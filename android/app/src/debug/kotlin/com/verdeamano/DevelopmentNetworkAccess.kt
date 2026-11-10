package com.verdeamano

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.verdeamano.core.design.BrandHeader

private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

/** Android 17 requires user consent even for the emulator's host bridge. Debug only. */
@Composable fun DevelopmentNetworkAccess(content: @Composable () -> Unit) {
    val needsPermission = Build.VERSION.SDK_INT >= 37 &&
        Uri.parse(BuildConfig.API_BASE_URL).host in setOf("10.0.2.2", "127.0.0.1", "localhost", "192.168.0.90")
    if (!needsPermission) { content(); return }
    val context = LocalContext.current
    fun permitted() = context.checkSelfPermission(LOCAL_NETWORK_PERMISSION) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(permitted()) }
    var denied by rememberSaveable { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        denied = !it
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = permitted()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (granted) content() else Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            BrandHeader("Conexión local", "Verde a Mano · desarrollo")
            Text("Esta versión de desarrollo necesita permiso para conectarse al servidor de pruebas en tu computadora.")
            if (denied) Text("Sin este permiso no podemos cargar el catálogo. Puedes volver a intentarlo o revisar los permisos de la aplicación.", color = MaterialTheme.colorScheme.error)
            Button({ permission.launch(LOCAL_NETWORK_PERMISSION) }, Modifier.fillMaxWidth()) { Text("Permitir conexión local") }
            TextButton({ context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("Revisar permisos") }
        }
    }
}
