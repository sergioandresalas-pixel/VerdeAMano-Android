package com.verdeamano.core.network

import com.verdeamano.contracts.ApiError
import com.verdeamano.core.auth.*
import com.verdeamano.core.session.SessionStore
import kotlinx.serialization.json.Json
import okhttp3.*
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import com.verdeamano.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrl

private data class RequestIdentity(val uid: String, val version: Long)

@Singleton class FirebaseHttpSession private constructor(private val identity: IdentityClient, private val session: SessionStore,private val trustedApi:HttpUrl,private val localGate:LocalUnlockGate) : Interceptor, Authenticator {
    @Inject constructor(identity:IdentityClient,session:SessionStore,localGate:LocalUnlockGate):this(identity,session,BuildConfig.API_BASE_URL.toHttpUrl(),localGate)
    internal constructor(identity:IdentityClient,session:SessionStore,apiOrigin:String,localGate:LocalUnlockGate=NoLocalUnlockGate):this(identity,session,apiOrigin.toHttpUrl(),localGate)
    private fun isApi(request:Request):Boolean {
        val base=trustedApi
        return request.url.host==base.host && request.url.port==base.port && request.url.scheme==base.scheme
    }
    private fun matches(tag: RequestIdentity) = identity.current?.uid == tag.uid && session.version == tag.version
    private fun invalidate(tag: RequestIdentity) { if (matches(tag)) { identity.signOut(); session.clear() } }
    override fun intercept(chain: Interceptor.Chain): Response {
        // Coil also uses this client. Never attach or refresh Firebase tokens for external images.
        if(!isApi(chain.request()))return chain.proceed(chain.request().newBuilder().removeHeader("Authorization").tag(RequestIdentity::class.java,null).build())
        val origin=chain.request().tag(RequestIdentity::class.java)
        if(origin != null && !matches(origin)) throw IOException("La sesión cambió. Repite la operación con tu cuenta actual.")
        val version = session.version
        val expectedUid = identity.current?.uid
        val cleanup=(chain.request().url.encodedPath=="/api/logout"&&chain.request().method=="POST") || (chain.request().url.encodedPath=="/api/notifications/push"&&chain.request().method=="DELETE")
        if(expectedUid!=null&&localGate.requiresUnlock(expectedUid)&&!cleanup)throw IOException("Desbloquea tu sesión local antes de consultar datos privados.")
        val token = try { identity.blockingToken() } catch (failure: AuthFailure) {
            if (failure.code == "session_invalid" && identity.current?.uid == expectedUid && session.version == version) { identity.signOut(); session.clear() }
            throw IOException(failure.message)
        }
        val tag = token?.let { RequestIdentity(it.uid,version) }
        if(origin != null && (token?.uid != origin.uid || !matches(origin))) throw IOException("La sesión cambió. Repite la operación con tu cuenta actual.")
        val request = chain.request().newBuilder().apply {
            if (token != null) { header("Authorization","Bearer ${token.value}"); tag(RequestIdentity::class.java,tag) }
        }.build()
        return chain.proceed(request).also { response ->
            if (tag != null) {
                val disabled = response.code == 403 && runCatching { Json.decodeFromString<ApiError>(response.peekBody(4096).string()).code == "account_disabled" }.getOrDefault(false)
                if (response.code == 401 || disabled) invalidate(tag)
            }
        }
    }
    override fun authenticate(route: Route?, response: Response): Request? {
        if(!isApi(response.request))return null
        val tag = response.request.tag(RequestIdentity::class.java) ?: return null
        if (!matches(tag) || response.priorResponse != null) return null
        val token = try { identity.blockingToken(forceRefresh=true) } catch (_: AuthFailure) { return null }
        if (token == null || token.uid != tag.uid || !matches(tag)) return null
        // The server rejects 401 before invoking protected business operations. Only one refresh/replay.
        return response.request.newBuilder().header("Authorization","Bearer ${token.value}").build()
    }
}

/** Binds requests when Retrofit creates the call, before queued work can switch to another account. */
class FirebaseCallFactory(private val client: OkHttpClient, private val identity: IdentityClient, private val session: SessionStore) : Call.Factory {
    override fun newCall(request: Request): Call {
        val origin=(session.firebaseUid ?: identity.current?.uid)?.let { RequestIdentity(it,session.version) }
        return client.newCall(request.newBuilder().tag(RequestIdentity::class.java,origin).build())
    }
}
