package com.verdeamano.core.data

import com.verdeamano.contracts.*
import com.verdeamano.core.network.VerdeApi
import com.verdeamano.core.session.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import javax.inject.Inject
import javax.inject.Singleton

interface ClaimPendingStore { fun read(uid:String):ClaimInput?;fun save(uid:String,input:ClaimInput);fun clear(uid:String) }
object NoClaimPendingStore:ClaimPendingStore {override fun read(uid:String):ClaimInput?=null;override fun save(uid:String,input:ClaimInput)=Unit;override fun clear(uid:String)=Unit}
interface DeviceReceiptStore { fun pending(uid:String):List<String>;fun record(uid:String,id:String);fun acknowledged(uid:String,id:String) }
object NoDeviceReceiptStore:DeviceReceiptStore {override fun pending(uid:String)=emptyList<String>();override fun record(uid:String,id:String)=Unit;override fun acknowledged(uid:String,id:String)=Unit}

@Singleton class CommercialRepository @Inject constructor(private val api:VerdeApi,private val session:SessionStore,private val pending:ClaimPendingStore=NoClaimPendingStore,private val receipts:DeviceReceiptStore=NoDeviceReceiptStore) {
    fun scopeStamp()=session.firebaseUid to session.version
    fun scopeChanges()=session.scopeChanges
    private suspend fun <T> scoped(block:suspend()->T):T {val uid=session.firebaseUid?:throw CancellationException("No identity");val version=session.version;val value=block();currentCoroutineContext().ensureActive();if(uid!=session.firebaseUid||version!=session.version)throw CancellationException("Account changed");return value}
    suspend fun payment(id:String)=scoped{api.payment(id)}
    suspend fun checkout(id:String)=scoped{api.paymentCheckout(id)}
    suspend fun reconcile(id:String,transaction:String)=scoped{api.paymentReconcile(id,PaymentQuery(transaction))}
    suspend fun claims()=scoped{api.claims()}
    suspend fun claim(id:String)=scoped{api.claim(id)}
    suspend fun responsibles(id:String)=scoped{api.claimResponsibles(id)}
    suspend fun adminPolicies()=scoped{api.adminClaimPolicies()}
    fun pendingClaim()=session.firebaseUid?.let{pending.read(it)}
    suspend fun create(input:ClaimInput):ClaimCase {
        val uid=session.firebaseUid?:throw CancellationException("No identity")
        val old=pending.read(uid)
        check(old==null||old==input){"Resolve the previous claim first"}
        pending.save(uid,input) // Save before POST; an uncertain response never gets a new logical request.
        return scoped{api.createClaim(input)}.also{pending.clear(uid)}
    }
    suspend fun update(id:String,input:ClaimUpdate)=scoped{api.updateClaim(id,input)}
    suspend fun attach(id:String,request:String,bytes:ByteArray)=scoped{api.claimAttachment(id,request,bytes.toRequestBody("application/octet-stream".toMediaType()))}
    suspend fun attachment(id:String,attachment:String)=scoped{api.claimPhoto(id,attachment).use{it.bytes()}}
    suspend fun notifications()=scoped{api.notifications()}
    suspend fun read(id:String)=scoped{api.readNotification(id)}
    suspend fun deviceReceipt(id:String,expectedUid:String){if(expectedUid!=session.firebaseUid)throw CancellationException("Receipt identity changed");scoped{api.deviceReceipt(id)}}
    fun recordDeviceReceipt(uid:String,id:String)=receipts.record(uid,id)
    suspend fun reconcileDeviceReceipts(){
        val uid=session.firebaseUid?:return
        for(id in receipts.pending(uid).take(20)){deviceReceipt(id,uid);receipts.acknowledged(uid,id)}
    }
    suspend fun push(token:String,expectedUid:String?=session.firebaseUid):Unit {
        if(expectedUid==null||expectedUid!=session.firebaseUid)throw CancellationException("Push identity changed")
        scoped{api.registerPush(PushRegistration(token))}
    }
    suspend fun refund(id:String)=scoped{api.refund(id)}
    suspend fun authorizeRefund(id:String,input:RefundAuthorization)=scoped{api.authorizeRefund(id,input)}
    suspend fun reconcileRefund(id:String)=scoped{api.reconcileRefund(id)}
}
