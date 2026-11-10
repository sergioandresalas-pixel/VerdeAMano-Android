package com.verdeamano.core.auth

data class IdentityUser(val uid: String, val email: String?, val name: String?, val providers: Set<String>, val emailVerified: Boolean = false)
data class IdentityToken(val uid: String, val value: String, val expiresAt: String)
class AuthFailure(val code: String, override val message: String) : Exception(message)

/** Firebase owns credentials/persistence; this boundary makes orchestration testable without fake production identities. */
interface IdentityClient {
    val current: IdentityUser?
    suspend fun signIn(email: String, password: String)
    suspend fun register(name: String, email: String, password: String)
    suspend fun google(token: String)
    suspend fun linkGoogle(token: String)
    suspend fun token(forceRefresh: Boolean = false): IdentityToken?
    fun blockingToken(forceRefresh: Boolean = false): IdentityToken?
    suspend fun resetPassword(email: String)
    suspend fun sendEmailVerification()
    suspend fun reloadIdentity()
    fun signOut()
    fun observeIdentity(listener: (String?) -> Unit): AutoCloseable
}
