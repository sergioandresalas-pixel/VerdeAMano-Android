package com.verdeamano.core.auth

import com.verdeamano.contracts.*
import com.verdeamano.core.network.VerdeApi
import com.verdeamano.core.session.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class AuthRepository @Inject constructor(private val identity: IdentityClient, private val api: VerdeApi, private val session: SessionStore,private val pushCleanup:PushSessionCleanup=NoPushSessionCleanup,private val recent:com.verdeamano.core.data.RecentCartStore=com.verdeamano.core.data.NoRecentCartStore,private val localGate:LocalUnlockGate=NoLocalUnlockGate) {
    private val operations = Mutex()
    private var observedUid=identity.current?.uid
    private val observer = identity.observeIdentity { uid ->
        if(uid!=observedUid){observedUid?.let{localGate.lock(it);try{recent.clear(it)}catch(_:Exception){ /* Cache inaccessible to another UID even when deletion fails. */ }};observedUid=uid}
        if(uid == null || (session.firebaseUid != null && session.firebaseUid != uid)) session.clear()
    }
    val currentUid get()=identity.current?.uid
    val biometricEnabled get()=currentUid?.let(localGate::enabled)==true
    val requiresLocalUnlock get()=currentUid?.let(localGate::requiresUnlock)==true
    fun recentCart()=currentUid?.takeUnless(localGate::requiresUnlock)?.let(recent::read)
    fun lockLocalSession():Boolean {val uid=currentUid?:return false;if(!localGate.enabled(uid))return false;localGate.lock(uid);session.clear();return true}
    fun disableBiometrics(){currentUid?.let(localGate::disable)}
    val hasIdentity get() = identity.current != null
    val providers get() = identity.current?.providers.orEmpty()
    val emailVerified get() = identity.current?.emailVerified == true
    suspend fun login(email: String, password: String) = operations.withLock {
        session.clear()
        identity.signOut()
        identity.signIn(email.trim(),password)
        currentUid?.let(localGate::freshLogin)
        complete(ProfileInput())
    }
    suspend fun register(name: String, email: String, password: String) = operations.withLock {
        session.clear()
        identity.signOut()
        identity.register(name.trim(),email.trim(),password)
        currentUid?.let(localGate::freshLogin)
        complete(ProfileInput(name.trim(),true))
    }
    suspend fun google(token: String, acceptedTerms: Boolean) = operations.withLock {
        session.clear()
        identity.signOut()
        identity.google(token)
        currentUid?.let(localGate::freshLogin)
        complete(ProfileInput(acceptedTerms=acceptedTerms))
    }
    suspend fun linkGoogle(token: String) = operations.withLock {
        identity.linkGoogle(token)
        restoreProfile()
    }
    suspend fun restore() = operations.withLock {
        if(currentUid?.let(localGate::requiresUnlock)==true)throw AuthFailure("local_unlock_required","Desbloquea la sesión local o accede con correo o Google.")
        if (identity.current != null) restoreProfile() else session.clear()
    }
    suspend fun finishProfile(name: String?, acceptedTerms: Boolean) = operations.withLock { complete(ProfileInput(name,acceptedTerms)) }
    private suspend fun complete(input: ProfileInput) = profileRequest { api.firebaseProfile(input) }
    private suspend fun restoreProfile() = profileRequest { api.me() }
    private suspend fun profileRequest(fetch: suspend () -> User) {
        val expectedUid=identity.current?.uid
        val token=try { identity.token(forceRefresh=true) ?: throw AuthFailure("session_invalid","Inicia sesión de nuevo.") }
        catch(failure:AuthFailure) {
            if(failure.code == "session_invalid" && identity.current?.uid == expectedUid) { identity.signOut();session.clear() }
            throw failure
        }
        val requestVersion=session.version
        try {
            val profile = fetch()
            if (!profile.active) throw AuthFailure("session_invalid","Esta cuenta está desactivada.")
            if (identity.current?.uid != token.uid) throw CancellationException("Identity changed")
            if(localGate.requiresUnlock(token.uid))throw AuthFailure("local_unlock_required","Desbloquea la sesión local para continuar.")
            localGate.verifiedProfile(token.uid)
            session.set(Session(token.value,profile,token.expiresAt,development=false),token.uid)
        } catch (failure: HttpException) {
            val code = runCatching { Json.decodeFromString<ApiError>(failure.response()?.errorBody()?.string().orEmpty()).code }.getOrNull()
            if (code in setOf("profile_required","terms_required")) throw AuthFailure("terms_required","Completa tu perfil y acepta las condiciones para continuar.")
            if (code == "identity_conflict") throw AuthFailure("identity_conflict","Este correo ya tiene un perfil. Utiliza su método original; no vinculamos cuentas solo por correo.")
            if ((failure.code() == 401 || code == "account_disabled") && identity.current?.uid == token.uid && session.version == requestVersion) { identity.signOut(); session.clear() }
            throw failure
        } catch(failure:AuthFailure) {
            if(failure.code == "session_invalid" && identity.current?.uid == token.uid && session.version == requestVersion) { identity.signOut();session.clear() }
            throw failure
        }
    }
    suspend fun reset(email: String) {
        try { identity.resetPassword(email.trim()) }
        catch (failure: AuthFailure) { if (failure.code != "session_invalid") throw failure }
    }
    suspend fun sendVerification() = operations.withLock {
        requireCurrentProfile()
        identity.sendEmailVerification()
    }
    suspend fun refreshVerification() = operations.withLock {
        requireCurrentProfile()
        identity.reloadIdentity()
        restoreProfile()
    }
    private fun requireCurrentProfile() {
        if (session.firebaseUid == null || identity.current?.uid != session.firebaseUid) throw AuthFailure("session_invalid","Inicia sesión de nuevo.")
    }
    suspend fun logout() = operations.withLock {
        try { if (identity.current!=null || session.session.value != null) {pushCleanup.clear();api.logout()} }
        finally { identity.signOut(); session.clear() }
    }
}
