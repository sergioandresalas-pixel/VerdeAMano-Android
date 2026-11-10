package com.verdeamano.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verdeamano.contracts.*
import com.verdeamano.core.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AccountData(val profile:ProfileDetails,val addresses:List<Address>,val preferences:Preferences,val requests:List<PrivacyRequest>,val consents:List<ConsentRecord>)
@HiltViewModel class AccountViewModel @Inject constructor(private val repository:AccountRepository):ViewModel(){
    private val mutable=MutableStateFlow(UiState<AccountData>())
    val state=mutable.asStateFlow()
    private val notices=MutableStateFlow<String?>(null)
    val message=notices.asStateFlow()
    private var pendingKey:String?=null
    private var pendingOperation:String?=null
    private fun key(operation:String):String {if(pendingOperation!=operation){pendingOperation=operation;pendingKey=UUID.randomUUID().toString()};return requireNotNull(pendingKey)}
    init{load()}
    private suspend fun fetch()=AccountData(repository.profile(),repository.addresses(),repository.preferences(),repository.requests(),repository.consents())
    fun load(){if(mutable.value.loading)return;mutable.value=mutable.value.copy(loading=true,error=null);viewModelScope.launch{try{mutable.value=UiState(fetch())}catch(e:Exception){mutable.value=mutable.value.copy(error=e.userMessage())}finally{mutable.value=mutable.value.copy(loading=false)}}}
    private fun mutate(action:suspend()->Unit){if(mutable.value.loading)return;mutable.value=mutable.value.copy(loading=true,error=null);notices.value=null;viewModelScope.launch{try{action();pendingKey=null;pendingOperation=null;mutable.value=UiState(fetch());notices.value="Cambios guardados."}catch(e:Exception){mutable.value=mutable.value.copy(error=e.userMessage())}finally{mutable.value=mutable.value.copy(loading=false)}}}
    fun profile(name:String,phone:String){AccountValidation.profile(name,phone)?.let{mutable.value=mutable.value.copy(error=it);return};val p=state.value.data?.profile?:return;val k=key("profile:$name:$phone:${p.version}");mutate{repository.saveProfile(ProfileUpdate(name.trim(),phone.takeIf{it.isNotBlank()},p.version,k))}}
    fun address(id:String?,input:AddressInput){AccountValidation.address(input)?.let{mutable.value=mutable.value.copy(error=it);return};val k=key("address:$id:${input.copy(requestId="")}");mutate{repository.saveAddress(id,input.copy(requestId=k))}}
    fun delete(address:Address){val k=key("delete:${address.id}:${address.version}");mutate{repository.deleteAddress(address,k)}}
    fun default(address:Address){val k=key("default:${address.id}:${address.version}");mutate{repository.defaultAddress(address,k)}}
    fun preferences(email:Boolean,promotions:Boolean){val p=state.value.data?.preferences?:return;val k=key("prefs:$email:$promotions:${p.version}");mutate{repository.savePreferences(PreferencesUpdate(email,promotions,p.version,k))}}
    fun request(type:PrivacyRequestType,note:String){val k=key("privacy:$type:$note");mutate{repository.request(PrivacyRequestInput(type,note,k))}}
    fun export(done:(PersonalDataExport)->Unit){if(mutable.value.loading)return;mutable.value=mutable.value.copy(loading=true,error=null);viewModelScope.launch{try{done(repository.exportData());notices.value="Archivo de datos personales generado."}catch(e:Exception){mutable.value=mutable.value.copy(error=e.userMessage())}finally{mutable.value=mutable.value.copy(loading=false)}}}
}
