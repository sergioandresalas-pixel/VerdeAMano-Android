package com.verdeamano.feature.orders

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import javax.inject.Inject
import javax.inject.Singleton

data class AddressSuggestion(val department:String,val city:String,val line:String,val approximate:Boolean)
interface CheckoutLocationClient {suspend fun suggest():AddressSuggestion}
@Singleton class DeviceCheckoutLocation @Inject constructor(@ApplicationContext private val context:Context):CheckoutLocationClient {
    override suspend fun suggest():AddressSuggestion {
        val coarse=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
        val fine=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
        if(!coarse&&!fine)throw IllegalStateException("Permiso de ubicación no concedido.")
        val manager=context.getSystemService(LocationManager::class.java)
        val provider=listOf(LocationManager.NETWORK_PROVIDER,LocationManager.GPS_PROVIDER).firstOrNull{(fine||it==LocationManager.NETWORK_PROVIDER)&&runCatching{manager.isProviderEnabled(it)}.getOrDefault(false)}
            ?:throw IllegalStateException("Activa la ubicación o escribe la dirección manualmente.")
        val location=withTimeout(15000){suspendCancellableCoroutine<Location?>{continuation->
            if(Build.VERSION.SDK_INT>=30){
                val signal=CancellationSignal();continuation.invokeOnCancellation{signal.cancel()}
                manager.getCurrentLocation(provider,signal,context.mainExecutor){if(continuation.isActive)continuation.resume(it)}
            }else{
                val listener=object:LocationListener{override fun onLocationChanged(location:Location){manager.removeUpdates(this);if(continuation.isActive)continuation.resume(location)}}
                continuation.invokeOnCancellation{manager.removeUpdates(listener)}
                @Suppress("DEPRECATION") manager.requestSingleUpdate(provider,listener,Looper.getMainLooper())
            }
        }}?:throw IllegalStateException("No pudimos obtener una ubicación puntual. Puedes escribir la dirección.")
        if(!Geocoder.isPresent())throw IllegalStateException("Este dispositivo no puede sugerir direcciones. Usa la entrada manual.")
        val address=withTimeout(5000){runInterruptible(Dispatchers.IO){@Suppress("DEPRECATION") Geocoder(context).getFromLocation(location.latitude,location.longitude,1)?.firstOrNull()}}
            ?:throw IllegalStateException("No encontramos una dirección para esa ubicación. Puedes escribirla manualmente.")
        val approximate=!fine||location.accuracy>100
        // Coordinates are ephemeral. An approximate fix suggests only region/city, never a precise street.
        return AddressSuggestion(address.adminArea.orEmpty(),address.locality?:address.subAdminArea.orEmpty(),if(approximate)"" else address.getAddressLine(0).orEmpty(),approximate)
    }
}
