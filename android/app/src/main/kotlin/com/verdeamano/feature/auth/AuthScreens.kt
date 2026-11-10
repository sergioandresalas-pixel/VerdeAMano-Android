package com.verdeamano.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verdeamano.core.design.*

@Composable fun WelcomeScreen(login: () -> Unit, register: () -> Unit, browse: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BotanicalMark(Modifier.size(56.dp), decorative = true)
            Text("Verde a Mano", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(8.dp))
        Text("Tu rincón verde comienza aquí", Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Text("Macetas, herramientas y riego para transformar balcones y espacios pequeños.", Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Button(login, Modifier.fillMaxWidth()) { Text("Iniciar sesión") }
        OutlinedButton(register, Modifier.fillMaxWidth()) { Text("Crear cuenta") }
        TextButton(browse) { Text("Explorar catálogo") }
        BotanicalIllustration(BotanicalMotif.GARDEN, Modifier.fillMaxWidth().height(92.dp))
        Text("Versión de desarrollo · Sin cobros reales", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable fun AuthScreen(register: Boolean, viewModel: AuthViewModel, switchMode: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    // Password is deliberately excluded from saved instance state.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var acceptedTerms by remember { mutableStateOf(false) }
    var recovery by remember { mutableStateOf(false) }
    BotanicalBackground(if(register) BotanicalPattern.BRANCH else BotanicalPattern.TREE, Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            BotanicalMark(Modifier.size(48.dp), decorative = true)
            Text("Verde a Mano", style = MaterialTheme.typography.titleMedium)
        }
        Text(if (register) "Cultivemos tu nueva cuenta" else "¡Bienvenido de nuevo!", style = MaterialTheme.typography.headlineMedium)
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (register) OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !state.loading)
        OutlinedTextField(email, { email = it }, label = { Text("Correo electrónico") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !state.loading)
        OutlinedTextField(password, { password = it }, label = { Text("Contraseña") }, supportingText = if(register) ({ Text("16 a 128 caracteres") }) else null,
            trailingIcon = { IconButton({ visible = !visible }) { Icon(if(visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,if(visible) "Ocultar contraseña" else "Mostrar contraseña") } },
            visualTransformation = if(visible) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !state.loading)
        if(register) {
            OutlinedTextField(confirmation,{ confirmation = it },label={ Text("Confirmar contraseña") },visualTransformation=if(visible) VisualTransformation.None else PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!state.loading)
            TermsConsent(acceptedTerms,{ acceptedTerms = it },!state.loading)
        }
        if(!recovery) {
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.message?.let { Text(it) }
            if (state.loading) LoadingState()
        }
        Button({ focus.clearFocus(); keyboard?.hide(); viewModel.authenticate(if (register) name else null, email, password,confirmation,acceptedTerms) }, Modifier.fillMaxWidth(), enabled = !state.loading) { Text(if (register) "Crear cuenta de comprador" else "Entrar con correo") }
        if (!register) TextButton({ viewModel.clearError(); recovery = true },enabled=!state.loading) { Text("Olvidé mi contraseña") }
        Text("o continúa con",Modifier.align(Alignment.CenterHorizontally),style=MaterialTheme.typography.labelLarge)
        OutlinedButton({ focus.clearFocus(); keyboard?.hide(); viewModel.google(context,acceptedTerms) },Modifier.fillMaxWidth(),enabled=!state.loading && (!register || acceptedTerms)) { Text("Continuar con Google") }
        TextButton({ viewModel.clearError(); switchMode() }, enabled = !state.loading) { Text(if (register) "Ya tengo una cuenta" else "Crear cuenta") }
        } }
        Text("Versión de desarrollo · Sin cobros reales", style = MaterialTheme.typography.labelSmall)
    } }
    if(recovery) AlertDialog(onDismissRequest={ if(!state.loading) recovery=false },title={Text("Recuperar acceso")},
        text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(email,{email=it},label={Text("Correo electrónico")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),enabled=!state.loading)
            Text("Si accedes solo con Google, utiliza Continuar con Google. No necesitas una contraseña de Verde a Mano.")
            state.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            state.message?.let {Text(it)}
            if(state.loading) LoadingState()
        }},confirmButton={TextButton({viewModel.reset(email)},enabled=!state.loading){Text("Enviar instrucciones")}},dismissButton={TextButton({recovery=false},enabled=!state.loading){Text("Volver")}})
}

@Composable private fun TermsConsent(accepted: Boolean, change: (Boolean) -> Unit, enabled: Boolean) {
    Row(verticalAlignment=Alignment.CenterVertically) {
        Checkbox(accepted,change,enabled=enabled)
        Text("Acepto las condiciones de uso de esta versión de prueba.",Modifier.weight(1f))
    }
    Text("La aplicación está en desarrollo: permite gestionar catálogo y carrito, sin generar compras ni pagos. Firebase administra tu acceso y Verde a Mano conserva tu perfil y tus productos.",style=MaterialTheme.typography.bodySmall)
}

@Composable fun CompleteProfileScreen(viewModel: AuthViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var accepted by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        BrandHeader("Completa tu perfil","Tu identidad ya fue autenticada")
        Text("Falta crear tu perfil de comprador en Verde a Mano. No necesitas registrar otra cuenta.")
        OutlinedTextField(name,{name=it},label={Text("Nombre")},modifier=Modifier.fillMaxWidth(),singleLine=true,enabled=!state.loading)
        TermsConsent(accepted,{accepted=it},!state.loading)
        state.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        if(state.loading) LoadingState()
        Button({viewModel.completeProfile(name,accepted)},Modifier.fillMaxWidth(),enabled=!state.loading){Text("Completar perfil de comprador")}
        TextButton(viewModel::logout,enabled=!state.loading){Text("Cerrar sesión")}
    }
}

@Composable fun RestoreSessionScreen(viewModel: AuthViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        BrandHeader("Recuperando tu cuenta","Validamos tu perfil y permisos")
        if(state.loading) LoadingState() else {
            ErrorState(state.error ?: "No pudimos recuperar tu perfil. Intenta de nuevo.",viewModel::restore)
            if(viewModel.hasRecentCart())OutlinedButton(viewModel::viewRecentCart){Text("Ver carrito reciente sin conexión")}
            TextButton(viewModel::logout){Text("Cerrar sesión")}
        }
    }
}

@Composable fun LocalUnlockScreen(viewModel:AuthViewModel){
    val state by viewModel.state.collectAsStateWithLifecycle();val context=LocalContext.current
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
        BrandHeader("Sesión protegida","Desbloqueo local de este dispositivo")
        Text("Android comprueba la biometría. Después consultaremos nuevamente tu perfil y permisos con Verde a Mano.")
        state.error?.let{Text(it,color=MaterialTheme.colorScheme.error)};if(state.loading)LoadingState()
        Button({viewModel.biometric(context)},enabled=!state.loading){Text("Desbloquear con huella o biometría")}
        OutlinedButton(viewModel::logout,enabled=!state.loading){Text("Usar correo o Google")}
    }
}
