package com.verdeamano.feature.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.verdeamano.contracts.Role
import com.verdeamano.contracts.User
import com.verdeamano.core.design.EmptyState
import com.verdeamano.core.design.LoadingState
import com.verdeamano.core.design.VerdeStatusChip
import com.verdeamano.core.design.BotanicalHero
import com.verdeamano.feature.admin.label
import com.verdeamano.feature.auth.AuthState

@Composable fun ProfileScreen(user: User?, logout: () -> Unit, login: () -> Unit, authentication: AuthState, sendVerification: () -> Unit, refreshVerification: () -> Unit, edit: () -> Unit, addresses: () -> Unit, preferences: () -> Unit, privacy: () -> Unit, biometric:(Context)->Unit={},disableBiometric:()->Unit={},invitation:()->Unit={},linkGoogle: (Context) -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        val seller = user?.role == Role.SELLER
        BotanicalHero(if (seller) "Perfil comercial" else "Mi perfil", if (seller) "Mi negocio" else "")
        if (user == null) {
            Text("Inicia sesión para sincronizar tu carrito y acceder a tu cuenta.")
            Button(login, Modifier.fillMaxWidth()) { Text("Iniciar sesión") }
        } else {
            if (seller) {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Identificación en el catálogo", style = MaterialTheme.typography.titleMedium)
                        if (user.name.isBlank()) EmptyState("No hay un nombre de vendedor disponible.")
                        else Text(user.name, style = MaterialTheme.typography.titleLarge)
                        VerdeStatusChip(if (user.active) "Vendedor activo" else "Vendedor inactivo", user.active)
                        Text("El catálogo muestra tu nombre junto a tus productos.")
                    }
                }
                Text("Cuenta y seguridad", style = MaterialTheme.typography.titleMedium)
                if (authentication.loading) LoadingState()
            } else {
                Text(user.name, style = MaterialTheme.typography.titleLarge)
                Text(user.email)
                Text("Rol: ${user.role.label()}")
            }
            if(user.role==com.verdeamano.contracts.Role.BUYER)OutlinedButton(invitation,Modifier.fillMaxWidth()){Text("Aceptar invitación a vendedor")}
            Text("Métodos vinculados: ${authentication.providers.filter { it != "firebase" }.joinToString { if(it == "google.com") "Google" else if(it == "password") "Correo y contraseña" else it }}")
            Text(if(authentication.emailVerified) "Correo verificado por Firebase" else "Correo pendiente de verificación")
            if(!authentication.emailVerified) {
                OutlinedButton(sendVerification,Modifier.fillMaxWidth(),enabled=!authentication.loading){Text("Enviar verificación de correo")}
                TextButton(refreshVerification,Modifier.fillMaxWidth(),enabled=!authentication.loading){Text("Ya verifiqué mi correo")}
            }
            if("google.com" !in authentication.providers) OutlinedButton({linkGoogle(context)},Modifier.fillMaxWidth(),enabled=!authentication.loading){Text("Vincular Google")}
            authentication.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            authentication.message?.let {Text(it)}
            OutlinedButton(edit, Modifier.fillMaxWidth()) { Text(if (seller) "Editar nombre y perfil" else "Editar perfil") }
            OutlinedButton(addresses, Modifier.fillMaxWidth()) { Text("Mis direcciones") }
            OutlinedButton(preferences, Modifier.fillMaxWidth()) { Text("Preferencias") }
            OutlinedButton(privacy, Modifier.fillMaxWidth()) { Text("Privacidad y datos") }
            Text("La biometría protege esta sesión local al salir de la aplicación; no sustituye tus permisos del servidor.")
            if(authentication.biometricEnabled)OutlinedButton(disableBiometric,Modifier.fillMaxWidth(),enabled=!authentication.loading){Text("Desactivar bloqueo biométrico")}
            else OutlinedButton({biometric(context)},Modifier.fillMaxWidth(),enabled=!authentication.loading){Text("Activar bloqueo biométrico")}
            OutlinedButton(logout, Modifier.fillMaxWidth(),enabled=!authentication.loading) { Text("Cerrar sesión") }
        }
    }
}
