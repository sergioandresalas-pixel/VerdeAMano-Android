package com.verdeamano.feature.commercial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verdeamano.contracts.*
import com.verdeamano.core.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import java.util.UUID
import javax.inject.Inject

data class CommercialState(val loading:Boolean=false,val error:String?=null,val payment:PaymentCheckout?=null,val claims:List<ClaimCase> = emptyList(),val claim:ClaimCase?=null,val notifications:List<NotificationItem> = emptyList(),val created:String?=null,val refund:SandboxRefund?=null,val attachment:ByteArray?=null,val responsibles:List<User> = emptyList(),val adminPolicies:List<CommercialPolicyDefinition> = emptyList(),val pushRegistered:Boolean=false)
@HiltViewModel class CommercialViewModel @Inject constructor(private val repository:CommercialRepository):ViewModel() {
    private val mutable=MutableStateFlow(CommercialState());val state=mutable.asStateFlow()
    private var pendingClaim:ClaimInput?=repository.pendingClaim()
    private var scopeStamp=repository.scopeStamp()
    private var generation=0L
    private var activeJob:Job?=null
    init{viewModelScope.launch{repository.scopeChanges().collect{stamp->if(stamp!=scopeStamp){generation++;activeJob?.cancel();scopeStamp=stamp;mutable.value=CommercialState();pendingClaim=repository.pendingClaim()}}}}
    fun currentUid()=repository.scopeStamp().first
    fun pendingClaim()=pendingClaim
    fun run(block:suspend()->CommercialState){
        if(scopeStamp!=repository.scopeStamp()){generation++;activeJob?.cancel();scopeStamp=repository.scopeStamp();mutable.value=CommercialState();pendingClaim=repository.pendingClaim()}
        if(state.value.loading)return
        val expected=scopeStamp;val current=++generation
        mutable.value=state.value.copy(loading=true,error=null)
        activeJob=viewModelScope.launch{try{val value=block();ensureActive();if(expected!=repository.scopeStamp())throw CancellationException("Account changed");if(current==generation)mutable.value=value.copy(loading=false)}
        catch(e:CancellationException){if(current==generation)mutable.value=if(expected!=repository.scopeStamp())CommercialState()else state.value.copy(loading=false);throw e}
        catch(e:Exception){if(current==generation)mutable.value=if(expected!=repository.scopeStamp())CommercialState()else state.value.copy(loading=false,error=e.userMessage())}}
    }
    fun payment(id:String)=run{state.value.copy(payment=repository.payment(id))}
    fun checkout(id:String)=run{state.value.copy(payment=repository.checkout(id))}
    fun reconcile(id:String,transaction:String)=run{state.value.copy(payment=repository.reconcile(id,transaction.trim()))}
    fun claims()=run{state.value.copy(claims=repository.claims())}
    fun claim(id:String,admin:Boolean=false)=run{state.value.copy(claim=repository.claim(id),responsibles=if(admin)repository.responsibles(id)else emptyList())}
    fun adminPolicies()=run{state.value.copy(adminPolicies=repository.adminPolicies())}
    fun create(order:String,kind:ClaimKind,reason:String,description:String){
        val input=ClaimInput(order,kind,reason.trim(),description.trim(),pendingClaim?.requestId?:UUID.randomUUID().toString())
        if(pendingClaim!=null && pendingClaim!=input){mutable.value=state.value.copy(error="Reintenta primero la solicitud pendiente con los mismos datos.");return}
        pendingClaim=input;run{val created=repository.create(input);pendingClaim=null;state.value.copy(claim=created,created=created.id)}
    }
    fun update(status:CaseStatus,note:String,assigned:String,due:String){val current=state.value.claim?:return;run{state.value.copy(claim=repository.update(current.id,ClaimUpdate(status,note,current.version,assigned.ifBlank{null},due.ifBlank{null})))}}
    fun attach(bytes:ByteArray){val current=state.value.claim?:return;run{state.value.copy(claim=repository.attach(current.id,UUID.randomUUID().toString(),bytes))}}
    fun notifications()=run{state.value.copy(notifications=repository.notifications())}
    fun reconcileReceipts()=run{repository.reconcileDeviceReceipts();state.value.copy(notifications=repository.notifications())}
    fun read(id:String)=run{repository.read(id);state.value.copy(notifications=repository.notifications())}
    fun push(token:String,expectedUid:String?=currentUid()){
        if(expectedUid==null||expectedUid!=currentUid()||state.value.loading)return
        mutable.value=state.value.copy(pushRegistered=false)
        run{repository.push(token,expectedUid);state.value.copy(pushRegistered=true)}
    }
    fun refund() {val c=state.value.claim?:return;run{state.value.copy(refund=repository.refund(c.id))}}
    fun authorizeRefund(note:String){val c=state.value.claim?:return;run{state.value.copy(refund=repository.authorizeRefund(c.id,RefundAuthorization(c.version,note)))}}
    fun reconcileRefund(){val c=state.value.claim?:return;run{state.value.copy(refund=repository.reconcileRefund(c.id))}}
    fun photo(id:String){val c=state.value.claim?:return;run{state.value.copy(attachment=repository.attachment(c.id,id))}}
}
