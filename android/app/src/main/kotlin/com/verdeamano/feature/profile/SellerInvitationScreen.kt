package com.verdeamano.feature.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verdeamano.contracts.*
import com.verdeamano.core.data.*
import com.verdeamano.core.design.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel class SellerInvitationViewModel @Inject constructor(private val repository:AccountRepository):ViewModel(){
    private val mutable=MutableStateFlow(UiState<List<SellerInvitation>>());val state=mutable.asStateFlow()
    private val notice=MutableStateFlow<String?>(null);val message=notice.asStateFlow()
    private var identity=repository.scopeStamp()
    private var generation=0L
    private var activeJob:Job?=null
    private fun refreshIdentity(){
        val scope=repository.scopeStamp()
        if(identity!=scope){generation++;activeJob?.cancel();identity=scope;mutable.value=UiState();notice.value=null}
    }
    fun currentUid()=repository.scopeStamp().first
    init{viewModelScope.launch{repository.identityChanges().collect{refreshIdentity()}}}
    fun load()=run{repository.sellerInvitations()}
    fun revoke(id:String)=run{repository.revokeInvitation(id);repository.sellerInvitations()}
    fun sendLocal(id:String)=run{repository.sendInvitation(id);repository.sellerInvitations()}
    fun accept(id:String,token:String){
        refreshIdentity()
        if(state.value.loading)return
        if(!Regex("^[a-f0-9-]{36}$").matches(id)||!Regex("^[A-Za-z0-9_-]{43}$").matches(token)){mutable.value=UiState(error="Revisa el identificador y el código de la invitación.");return}
        run(accepted=true){repository.acceptInvitation(id,token);emptyList()}
    }
    private fun run(accepted:Boolean=false,action:suspend()->List<SellerInvitation>){
        refreshIdentity();if(state.value.loading)return
        val scope=identity;val current=++generation
        mutable.value=UiState(loading=true);notice.value=null
        activeJob=viewModelScope.launch{
            try{
                val result=action();ensureActive()
                val latest=repository.scopeStamp()
                if(scope!=latest && (!accepted || scope.first!=latest.first))throw CancellationException("Identity changed")
                // Acceptance updates the verified role synchronously after the repository checks the original session.
                if(accepted && current==generation)identity=latest
                if(current==generation){mutable.value=UiState(result);if(accepted)notice.value="Invitación aceptada. Tus permisos de vendedor se verificaron con el servidor."}
            }catch(e:CancellationException){if(current==generation){mutable.value=UiState();notice.value=null};throw e}
            catch(e:Exception){if(current==generation){mutable.value=if(scope==repository.scopeStamp())UiState(error=e.userMessage())else UiState();notice.value=null}}
        }
    }
}
@Composable fun SellerInvitationScreen(vm:SellerInvitationViewModel,admin:Boolean){
    val state by vm.state.collectAsStateWithLifecycle();val message by vm.message.collectAsStateWithLifecycle()
    val uid=vm.currentUid()
    var id by remember(uid){mutableStateOf("")};var token by remember(uid){mutableStateOf("")};var revoke by remember(uid){mutableStateOf<String?>(null)};var send by remember(uid){mutableStateOf<String?>(null)}
    LaunchedEffect(admin,uid){if(admin)vm.load()}
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{BrandHeader(if(admin)"Invitaciones a vendedores"else"Aceptar invitación a vendedor","Identidad verificada y código de un solo uso")}
        if(state.loading)item{LoadingState()};state.error?.let{item{ErrorState(it,{if(admin)vm.load()else vm.accept(id,token)})}}
        message?.let{item{Text(it)}}
        if(admin){
            if(!state.loading&&state.error==null&&state.data.isNullOrEmpty())item{EmptyState("No hay invitaciones nuevas con verificador seguro.")}
            items(state.data.orEmpty(),key={it.id}){inv->OutlinedCard{Column(Modifier.padding(12.dp)){Text(inv.email);Text(invitationLabel(inv.status));Text("Vencimiento: ${inv.expiresAt?:"No disponible"}");if(inv.status=="INVITATION_PREPARED_LOCAL")OutlinedButton({send=inv.id}){Text("Enviar invitación")};if(inv.consumedAt==null&&inv.revokedAt==null)OutlinedButton({revoke=inv.id}){Text("Revocar invitación")}}}}
        }else{
            item{Text("Usa la misma identidad y el correo verificado del destinatario. No envíes contraseñas ni enlaces Firebase en estos campos.")}
            item{OutlinedTextField(id,{id=it},label={Text("Identificador de invitación")},singleLine=true)}
            item{OutlinedTextField(token,{token=it},label={Text("Código de un solo uso")},singleLine=true,visualTransformation=PasswordVisualTransformation())}
            item{Button({vm.accept(id.trim(),token.trim());token=""},enabled=!state.loading){Text("Aceptar invitación")}}
        }
    }
    revoke?.let{target->AlertDialog(onDismissRequest={revoke=null},title={Text("Revocar invitación")},text={Text("Solo invalida esta invitación. No elimina ni desactiva su cuenta.")},confirmButton={TextButton({vm.revoke(target);revoke=null}){Text("Confirmar revocación")}},dismissButton={TextButton({revoke=null}){Text("Cancelar")}})}
    send?.let{target->AlertDialog(onDismissRequest={send=null},title={Text("Enviar invitación")},text={Text("Solo se usará el canal configurado y autorizado. La entrega se confirmará por separado.")},confirmButton={TextButton({vm.sendLocal(target);send=null}){Text("Confirmar envío")}},dismissButton={TextButton({send=null}){Text("Cancelar")}})}
}
fun invitationLabel(status:String)=when(status){"INVITATION_PREPARED_LOCAL"->"Preparada localmente; sin entrega externa";"INVITATION_SMTP_ACCEPTED_PROVIDER"->"Aceptada por el proveedor SMTP; entrega por confirmar";"INVITATION_SMTP_ACCEPTED_LOCAL"->"Aceptada por SMTP local; sin entrega externa confirmada";"INVITATION_MAIL_SUBMITTING"->"Envío local en curso";"INVITATION_MAIL_RECONCILIATION"->"Resultado incierto; no se reenviará";"ACCEPTED"->"Aceptada por el destinatario";"REVOKED"->"Revocada";"EXPIRED"->"Vencida";else->"Pendiente de revisión"}
