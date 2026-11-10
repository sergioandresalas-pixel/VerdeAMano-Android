package com.verdeamano.feature.orders

import android.content.Context
import com.verdeamano.core.data.*
import com.verdeamano.contracts.PendingOrderConfirmation
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** App-private, backup-disabled, UID-scoped request recovery. Contains no token or password. */
@Singleton class AndroidOrderPendingStore @Inject constructor(@ApplicationContext context:Context):OrderPendingStore {
    private val preferences=context.getSharedPreferences("order-confirmations",Context.MODE_PRIVATE)
    override fun read(uid:String)=preferences.getString(uid,null)?.let{Json.decodeFromString<PendingOrderConfirmation>(it)}
    override fun save(uid:String,value:PendingOrderConfirmation){if(!preferences.edit().putString(uid,Json.encodeToString(value)).commit())throw IOException("Request persistence failed")}
    override fun clear(uid:String){if(!preferences.edit().remove(uid).commit())throw IOException("Request cleanup failed")}
}
@Module @InstallIn(SingletonComponent::class) abstract class OrderStorageModule {
    @Binds abstract fun pending(implementation:AndroidOrderPendingStore):OrderPendingStore
}
