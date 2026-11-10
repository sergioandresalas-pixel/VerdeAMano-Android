package com.verdeamano.feature.profile

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verdeamano.contracts.*
import com.verdeamano.core.design.*
import com.verdeamano.feature.admin.label
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Composable fun ProfileEditorScreen(viewModel:AccountViewModel){
    val state by viewModel.state.collectAsStateWithLifecycle();val message by viewModel.message.collectAsStateWithLifecycle()
    BotanicalBackground(BotanicalPattern.SPROUT,Modifier.fillMaxSize()){Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        BrandHeader("Editar perfil","Actualiza tus datos de contacto")
        if(state.loading)LoadingState();state.error?.let{ErrorState(it,viewModel::load)};message?.let{Text(it)}
        state.data?.profile?.let{profile->
            var name by remember(profile.user.id){mutableStateOf(profile.user.name)};var phone by remember(profile.user.id){mutableStateOf(profile.phone.orEmpty())}
            Text(profile.user.email);Text("Rol: ${profile.user.role.label()}")
            OutlinedTextField(name,{name=it},label={Text("Nombre")},modifier=Modifier.fillMaxWidth(),enabled=!state.loading)
            OutlinedTextField(phone,{phone=it},label={Text("Teléfono")},modifier=Modifier.fillMaxWidth(),enabled=!state.loading)
            Button({viewModel.profile(name,phone)},Modifier.fillMaxWidth(),enabled=!state.loading){Text("Guardar perfil")}
        }
    }}
}

@Composable fun AddressesScreen(viewModel:AccountViewModel,edit:(String)->Unit){
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }
    val state by viewModel.state.collectAsStateWithLifecycle();var removing by remember{mutableStateOf<Address?>(null)}
    LazyColumn(Modifier.fillMaxSize().testTag("addresses-list"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{BrandHeader("Mis direcciones","Solo las direcciones de tu cuenta")}
        item{Button({edit("new")},Modifier.fillMaxWidth(),enabled=!state.loading){Text("Agregar dirección")}}
        if(state.loading)item{LoadingState()};state.error?.let{item{ErrorState(it,viewModel::load)}}
        if(!state.loading&&state.error==null&&state.data!=null&&state.data?.addresses.isNullOrEmpty())item{EmptyState("No tienes direcciones registradas.")}
        items(state.data?.addresses.orEmpty(),key={it.id}){address->OutlinedCard(Modifier.fillMaxWidth()){
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(address.recipient);Text("${address.line}, ${address.city}");if(address.isDefault)Text("Dirección predeterminada")
                OutlinedButton({edit(address.id)},enabled=!state.loading){Text("Editar ${address.recipient}")}
                if(!address.isDefault)TextButton({viewModel.default(address)},enabled=!state.loading){Text("Predeterminada ${address.recipient}")}
                TextButton({removing=address},enabled=!state.loading){Text("Eliminar ${address.recipient}")}
            }
        }}
    }
    removing?.let{address->AlertDialog(onDismissRequest={removing=null},title={Text("Desactivar dirección")},text={Text("Se conservará el registro para trazabilidad y dejará de estar disponible.")},confirmButton={TextButton({removing=null;viewModel.delete(address)}){Text("Eliminar dirección")}},dismissButton={TextButton({removing=null}){Text("Cancelar")}})}
}

@Composable fun AddressEditorScreen(viewModel:AccountViewModel,id:String,suggestion:com.verdeamano.feature.orders.AddressSuggestion?=null,saved:()->Unit){
    val state by viewModel.state.collectAsStateWithLifecycle();val message by viewModel.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) { if(message == "Cambios guardados.") saved() }
    val original=state.data?.addresses?.find{it.id==id}
    if(id!="new"&&original==null){Column(Modifier.padding(20.dp)){if(state.loading)LoadingState() else ErrorState("La dirección no está disponible.",viewModel::load)};return}
    var recipient by remember(id){mutableStateOf(original?.recipient.orEmpty())};var phone by remember(id){mutableStateOf(original?.phone.orEmpty())}
    var department by remember(id){mutableStateOf(original?.department?:suggestion?.department.orEmpty())};var city by remember(id){mutableStateOf(original?.city?:suggestion?.city.orEmpty())};var line by remember(id){mutableStateOf(original?.line?:suggestion?.line.orEmpty())}
    var neighborhood by remember(id){mutableStateOf(original?.neighborhood.orEmpty())};var complement by remember(id){mutableStateOf(original?.complement.orEmpty())};var instructions by remember(id){mutableStateOf(original?.instructions.orEmpty())}
    var latitude by remember(id){mutableStateOf(original?.latitude?.toString().orEmpty())};var longitude by remember(id){mutableStateOf(original?.longitude?.toString().orEmpty())};var primary by remember(id){mutableStateOf(original?.isDefault?:false)}
    var coordinateError by remember{mutableStateOf<String?>(null)}
    BotanicalBackground(BotanicalPattern.SPROUT,Modifier.fillMaxSize()){Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        BrandHeader(if(id=="new")"Crear dirección" else "Editar dirección","Datos para recibir tus pedidos")
        if(state.loading)LoadingState();state.error?.let{ErrorState(it,viewModel::load)};message?.let{Text(it)};coordinateError?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        AccountField("Destinatario",recipient,!state.loading){recipient=it};AccountField("Teléfono",phone,!state.loading){phone=it}
        AccountField("Departamento",department,!state.loading){department=it};AccountField("Municipio o ciudad",city,!state.loading){city=it};AccountField("Dirección",line,!state.loading){line=it}
        AccountField("Barrio",neighborhood,!state.loading){neighborhood=it};AccountField("Complemento",complement,!state.loading){complement=it};AccountField("Referencias de entrega",instructions,!state.loading){instructions=it}
        AccountField("Latitud opcional",latitude,!state.loading){latitude=it};AccountField("Longitud opcional",longitude,!state.loading){longitude=it}
        Row{Checkbox(primary,{primary=it},enabled=!state.loading&&original?.isDefault!=true);Text("Usar como predeterminada")};if(original?.isDefault==true)Text("Para cambiar la dirección principal, selecciona otra desde Mis direcciones.")
        Button({
            if((latitude.isNotBlank()&&latitude.toDoubleOrNull()==null)||(longitude.isNotBlank()&&longitude.toDoubleOrNull()==null)){coordinateError="Coordenadas inválidas."}
            else{coordinateError=null;viewModel.address(id.takeUnless{it=="new"},AddressInput(recipient,phone,department,city,line,neighborhood,complement,instructions,latitude.toDoubleOrNull(),longitude.toDoubleOrNull(),primary,original?.version,""))}
        },Modifier.fillMaxWidth(),enabled=!state.loading){Text("Guardar dirección")}
    }}
}

@Composable fun PreferencesScreen(viewModel:AccountViewModel){
    val state by viewModel.state.collectAsStateWithLifecycle();val message by viewModel.message.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        BrandHeader("Preferencias","El consentimiento comercial es independiente")
        if(state.loading)LoadingState();state.error?.let{ErrorState(it,viewModel::load)};message?.let{Text(it)}
        state.data?.preferences?.let{preferences->
            var email by remember(preferences.version){mutableStateOf(preferences.emailNotifications)};var promotions by remember(preferences.version){mutableStateOf(preferences.promotions)}
            Row{Checkbox(email,{email=it},enabled=!state.loading);Text("Comunicaciones sobre mi actividad")}
            Row{Checkbox(promotions,{promotions=it},enabled=!state.loading);Text("Acepto recibir promociones")}
            Text("Las instrucciones de seguridad y acceso se gestionan directamente mediante Firebase.")
            Button({viewModel.preferences(email,promotions)},Modifier.fillMaxWidth(),enabled=!state.loading){Text("Guardar preferencias")}
            Text("Historial de consentimiento comercial")
            state.data?.consents.orEmpty().forEach{record->Text("${if(record.accepted)"Aceptado" else "Retirado"} · ${record.createdAt} · ${record.policyVersion}")}
        }
    }
}

@Composable fun PrivacyScreen(viewModel:AccountViewModel,open:(String)->Unit={}){
    val state by viewModel.state.collectAsStateWithLifecycle();val message by viewModel.message.collectAsStateWithLifecycle();val context=LocalContext.current
    var type by remember{mutableStateOf(PrivacyRequestType.ACCESS)};var note by remember{mutableStateOf("")};var exportFile by remember{mutableStateOf<File?>(null)}
    DisposableEffect(Unit) { onDispose { exportFile?.delete() } }
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("privacy-list"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{BrandHeader("Privacidad y datos","Control de tu información")}
        item{Text("Firebase gestiona tu identidad y acceso. Verde a Mano almacena tu nombre, teléfono, direcciones, preferencias y actividad necesaria para operar el servicio. El consentimiento de promociones es opcional y puede retirarse en Preferencias.")}
        item{Text("Puedes exportar los datos guardados en estos módulos y presentar solicitudes de acceso, corrección o eliminación. Una solicitud queda pendiente de revisión: no elimina automáticamente usuarios ni registros históricos. Los pedidos se conservan para trazabilidad; esta versión de desarrollo no realiza cobros ni pagos bancarios.")}
        if(state.loading)item{LoadingState()};state.error?.let{item{ErrorState(it,viewModel::load)}};message?.let{item{Text(it)}}
        item{Button({viewModel.export{data->
            val directory=File(context.cacheDir,"exports").apply{mkdirs()}
            val created=File(directory,"mis-datos-${java.util.UUID.randomUUID()}.json")
            try{created.writeText(Json.encodeToString(data))}catch(error:Exception){created.delete();throw error}
            exportFile?.delete();exportFile=created
        }},enabled=!state.loading){Text("Exportar mis datos")}}
        exportFile?.let{file->item{OutlinedButton({val uri=FileProvider.getUriForFile(context,"${context.packageName}.exports",file);context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),"Compartir mis datos"))}){Text("Compartir archivo exportado")}}}
        item{PrivacyRequestType.entries.forEach{option->FilterChip(type==option,{type=option},label={Text(when(option){PrivacyRequestType.ACCESS->"Acceso";PrivacyRequestType.EXPORT->"Exportación";PrivacyRequestType.DELETION->"Eliminación";PrivacyRequestType.CORRECTION->"Corrección"})},enabled=!state.loading)}}
        item{AccountField("Descripción de la solicitud",note,!state.loading){note=it}}
        item{Button({viewModel.request(type,note)},enabled=!state.loading){Text("Enviar solicitud de privacidad")}}
        items(state.data?.requests.orEmpty(),key={it.id}){request->OutlinedCard{Column(Modifier.padding(16.dp)){Text("${request.type.privacyLabel()} · ${request.status.privacyStatusLabel()}");Text(request.createdAt);Text(request.note);if(request.response.isNotBlank())Text(request.response);OutlinedButton({open(request.id)},enabled=!state.loading){Text("Consultar seguimiento")}}}}
    }
}

@Composable private fun AccountField(label:String,value:String,enabled:Boolean,change:(String)->Unit){OutlinedTextField(value,change,label={Text(label)},modifier=Modifier.fillMaxWidth(),enabled=enabled,singleLine=label!="Referencias de entrega")}

private fun PrivacyRequestType.privacyLabel():String = when(this){PrivacyRequestType.ACCESS->"Acceso";PrivacyRequestType.EXPORT->"Exportación";PrivacyRequestType.DELETION->"Eliminación";PrivacyRequestType.CORRECTION->"Corrección"}

