package com.verdeamano.core.auth

import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import java.time.Instant
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class FirebaseIdentityClient @Inject constructor(private val auth: FirebaseAuth) : IdentityClient {
    override val current get() = auth.currentUser?.let { IdentityUser(it.uid,it.email,it.displayName,it.providerData.map { provider -> provider.providerId }.toSet(),it.isEmailVerified) }
    private suspend fun <T> safely(action: suspend () -> T): T = try { withTimeout(35_000) { action() } }
    catch (_: TimeoutCancellationException) { throw AuthFailure("network_timeout","El acceso tardó demasiado. Revisa tu conexión e intenta de nuevo.") }
    catch (failure: Exception) { throw safeFailure(failure) }
    override suspend fun signIn(email: String, password: String) { safely { auth.signInWithEmailAndPassword(email,password).await() } }
    override suspend fun register(name: String, email: String, password: String) { safely {
        val user = auth.createUserWithEmailAndPassword(email,password).await().user ?: throw AuthFailure("session_invalid","Inicia sesión de nuevo.")
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build()).await()
    } }
    override suspend fun google(token: String) { safely { auth.signInWithCredential(GoogleAuthProvider.getCredential(token,null)).await() } }
    override suspend fun linkGoogle(token: String) { safely {
        val user = auth.currentUser ?: throw AuthFailure("session_invalid","Inicia sesión de nuevo.")
        val signedInAt = user.metadata?.lastSignInTimestamp ?: 0
        if (System.currentTimeMillis() - signedInAt > 5 * 60_000) throw AuthFailure("recent_login","Vuelve a iniciar sesión antes de vincular Google.")
        user.linkWithCredential(GoogleAuthProvider.getCredential(token,null)).await()
    } }
    override suspend fun token(forceRefresh: Boolean): IdentityToken? = safely {
        val user = auth.currentUser ?: return@safely null
        val result = user.getIdToken(forceRefresh).await()
        if (auth.currentUser?.uid != user.uid) return@safely null
        result.token?.let { IdentityToken(user.uid,it,Instant.ofEpochSecond(result.expirationTimestamp).toString()) }
    }
    override fun blockingToken(forceRefresh: Boolean): IdentityToken? {
        val user = auth.currentUser ?: return null
        return try {
            val result = Tasks.await(user.getIdToken(forceRefresh),15,TimeUnit.SECONDS)
            if (auth.currentUser?.uid != user.uid) null else result.token?.let { IdentityToken(user.uid,it,Instant.ofEpochSecond(result.expirationTimestamp).toString()) }
        } catch (failure: Exception) { throw safeFailure(failure) }
    }
    override suspend fun resetPassword(email: String) { safely { auth.sendPasswordResetEmail(email).await() } }
    override suspend fun sendEmailVerification() { safely {
        val user = auth.currentUser ?: throw AuthFailure("session_invalid","Inicia sesión de nuevo.")
        if (!user.isEmailVerified) user.sendEmailVerification().await()
    } }
    override suspend fun reloadIdentity() { safely {
        val user = auth.currentUser ?: throw AuthFailure("session_invalid","Inicia sesión de nuevo.")
        user.reload().await()
        if (auth.currentUser?.uid != user.uid) throw CancellationException("Identity changed")
    } }
    override fun signOut() = auth.signOut()
    override fun observeIdentity(listener: (String?) -> Unit): AutoCloseable {
        val observer = FirebaseAuth.AuthStateListener { listener(it.currentUser?.uid) }
        auth.addAuthStateListener(observer)
        return AutoCloseable { auth.removeAuthStateListener(observer) }
    }
    private fun safeFailure(original: Exception): Exception {
        val failure = if (original is ExecutionException) original.cause ?: original else original
        return when (failure) {
            is CancellationException -> failure
            is AuthFailure -> failure
            is FirebaseNetworkException -> AuthFailure("network","No pudimos conectar con Firebase. Revisa tu conexión e intenta de nuevo.")
            is FirebaseTooManyRequestsException -> AuthFailure("rate_limit","Demasiados intentos. Espera unos minutos.")
            is FirebaseAuthUserCollisionException -> AuthFailure("account_conflict","La cuenta ya existe. Accede con su método original y vincula Google desde tu perfil.")
            is FirebaseAuthWeakPasswordException -> AuthFailure("weak_password","La contraseña no cumple los requisitos de seguridad. Usa al menos 16 caracteres con letras, números y símbolos.")
            is FirebaseAuthRecentLoginRequiredException -> AuthFailure("recent_login","Vuelve a iniciar sesión para completar esta operación.")
            is FirebaseAuthInvalidUserException -> AuthFailure("session_invalid","La sesión o la cuenta no están disponibles. Inicia sesión de nuevo.")
            is FirebaseAuthInvalidCredentialsException -> AuthFailure("invalid_credentials","Revisa el correo, la contraseña o el método de acceso.")
            else -> AuthFailure("identity_error","No pudimos completar el acceso. Intenta de nuevo.")
        }
    }
}
