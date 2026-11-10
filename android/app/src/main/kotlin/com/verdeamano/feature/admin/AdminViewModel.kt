package com.verdeamano.feature.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verdeamano.contracts.*
import com.verdeamano.core.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AdminData(val users:UserPage,val audits:AuditPage)
@HiltViewModel class AdminViewModel @Inject constructor(private val repository:AccountRepository):ViewModel(){
    private val mutable=MutableStateFlow(UiState<AdminData>())
    val state=mutable.asStateFlow()
    private val detail=MutableStateFlow(UiState<UserDetails>())
    val selected=detail.asStateFlow()
    private val notice=MutableStateFlow<String?>(null)
    val message=notice.asStateFlow()
    var query="";private set
    var role:String?=null;private set
    var active:Boolean?=null;private set
    var result:String?=null;private set
    private var page=1
    private var auditPage=1
    private var pendingPayload:String?=null
    private var pendingId:String?=null
    private fun key(payload:String):String {if(payload!=pendingPayload){pendingPayload=payload;pendingId=UUID.randomUUID().toString()};return requireNotNull(pendingId)}
    init{load()}
    fun filter(q:String,r:String?,a:Boolean?,outcome:String?){query=q;role=r;active=a;result=outcome;page=1;auditPage=1;load()}
    fun next(audit:Boolean,delta:Int){if(audit)auditPage=(auditPage+delta).coerceAtLeast(1) else page=(page+delta).coerceAtLeast(1);load()}
    fun load(){if(mutable.value.loading)return;mutable.value=mutable.value.copy(loading=true,error=null);viewModelScope.launch{try{mutable.value=UiState(AdminData(repository.users(page,query,role,active),repository.audits(auditPage,query,result)))}catch(e:Exception){mutable.value=mutable.value.copy(error=e.userMessage())}finally{mutable.value=mutable.value.copy(loading=false)}}}
    fun select(id:String){if(detail.value.loading)return;notice.value=null;detail.value=UiState(loading=true);viewModelScope.launch{try{detail.value=UiState(repository.user(id))}catch(e:Exception){detail.value=UiState(error=e.userMessage())}}}
    fun access(id:String,role:Role,active:Boolean){val current=detail.value.data?:return;if(detail.value.loading||current.protectedAccount||current.profile.user.id!=id)return;val p=current.profile;val k=key("$id:$role:$active:${p.version}");detail.value=detail.value.copy(loading=true,error=null);notice.value=null;viewModelScope.launch{try{val updated=repository.access(id,AccessUpdate(role,active,p.version,k));pendingId=null;pendingPayload=null;detail.value=UiState(current.copy(profile=updated),loading=true);notice.value="Permisos guardados.";load();detail.value=UiState(repository.user(id))}catch(e:Exception){detail.value=detail.value.copy(loading=false,error=e.userMessage())}}}
    fun create(name:String,email:String,role:Role){if(mutable.value.loading)return;if(name.trim().length !in 2..120||!Validation.email(email)||role==Role.ADMIN){mutable.value=mutable.value.copy(error="Revisa nombre, correo y rol.");return};val k=key("create:$name:$email:$role");mutable.value=mutable.value.copy(loading=true,error=null);notice.value=null;viewModelScope.launch{try{val created=repository.create(UserCreation(name.trim(),email.trim(),role,k));pendingId=null;pendingPayload=null;notice.value=if(created.invitationStatus=="INVITATION_PREPARED_LOCAL")"Invitación preparada localmente. Permisos de vendedor pendientes de aceptación; no se envió correo externo." else if(created.invitationStatus=="LOCAL_LINK_PREPARED")"Cuenta creada. Enlace local preparado; no se envió correo a .test." else "Cuenta creada. Firebase aceptó la solicitud de correo; la entrega debe confirmarse."}catch(e:Exception){mutable.value=mutable.value.copy(error=e.userMessage())}finally{mutable.value=mutable.value.copy(loading=false)}}}
}
