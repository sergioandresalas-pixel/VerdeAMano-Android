package com.verdeamano.core.data

import com.verdeamano.contracts.*
import com.verdeamano.core.network.VerdeApi
import com.verdeamano.core.session.SessionStore
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

@Singleton class AccountRepository @Inject constructor(private val api:VerdeApi,private val session:SessionStore) {
    internal fun scopeStamp()=session.firebaseUid to session.version
    internal fun identityChanges()=session.scopeChanges
    private suspend fun <T> scoped(action:suspend()->T):T {val uid=session.firebaseUid;val version=session.version;val result=action();currentCoroutineContext().ensureActive();if(uid!=session.firebaseUid||version!=session.version)throw kotlinx.coroutines.CancellationException("Account changed");return result}
    suspend fun profile()=scoped{api.profile()}.also{session.updateProfile(it.user)}
    suspend fun sellerInvitations()=scoped{api.sellerInvitations()}
    suspend fun revokeInvitation(id:String)=scoped{api.revokeInvitation(id)}
    suspend fun sendInvitation(id:String)=scoped{api.sendInvitation(id)}
    suspend fun acceptInvitation(id:String,token:String)=scoped{api.acceptInvitation(id,InvitationAcceptance(token))}.also{session.updateProfile(it)}
    suspend fun saveProfile(input:ProfileUpdate)=scoped{api.updateProfile(input)}.also{session.updateProfile(it.user)}
    suspend fun addresses()=scoped{api.addresses()}
    suspend fun address(id:String)=scoped{api.address(id)}
    suspend fun saveAddress(id:String?,input:AddressInput)=scoped{if(id==null)api.createAddress(input) else api.updateAddress(id,input)}
    suspend fun deleteAddress(address:Address,key:String)=scoped{api.deleteAddress(address.id,MutationInput(address.version,key))}
    suspend fun defaultAddress(address:Address,key:String)=scoped{api.defaultAddress(address.id,MutationInput(address.version,key))}
    suspend fun preferences()=scoped{api.preferences()}
    suspend fun consents()=scoped{api.consents()}
    suspend fun savePreferences(input:PreferencesUpdate)=scoped{api.updatePreferences(input)}
    suspend fun requests()=scoped{api.privacyRequests()}
    suspend fun privacyDetail(id:String)=scoped{api.privacyDetail(id)}
    suspend fun privacyPage(page:Int,status:PrivacyStatus?)=scoped{api.privacyPage(page,status=status)}
    suspend fun privacyResponsibles()=scoped{api.privacyResponsibles()}
    suspend fun reviewPrivacy(id:String,input:PrivacyReview)=scoped{api.reviewPrivacy(id,input)}
    suspend fun privacyRetentionEvaluation(id:String)=scoped{api.privacyRetentionEvaluation(id)}
    suspend fun request(input:PrivacyRequestInput)=scoped{api.privacyRequest(input)}
    suspend fun exportData()=scoped{api.exportData()}
    suspend fun users(page:Int,q:String?,role:String?,active:Boolean?)=scoped{api.userPage(page,q=q,role=role,active=active)}
    suspend fun user(id:String)=scoped{api.userDetails(id)}
    suspend fun access(id:String,input:AccessUpdate)=scoped{api.userAccess(id,input)}
    suspend fun create(input:UserCreation)=scoped{api.createUser(input)}
    suspend fun audits(page:Int,q:String?,result:String?)=scoped{api.auditPage(page,q=q,result=result)}
}
