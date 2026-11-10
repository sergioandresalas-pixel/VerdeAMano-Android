package com.verdeamano.core.auth

import android.content.Context
import androidx.credentials.*
import androidx.credentials.exceptions.*
import com.google.android.libraries.identity.googleid.*
import dagger.hilt.android.qualifiers.ApplicationContext
import com.verdeamano.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

interface GoogleCredentialClient {
    suspend fun select(activityContext: Context): String
    suspend fun clear()
}

@Singleton class GoogleSignInService @Inject constructor(@ApplicationContext private val context: Context) : GoogleCredentialClient {
    private val manager = CredentialManager.create(context)
    override suspend fun select(activityContext: Context): String {
        val webClientId = context.getString(R.string.default_web_client_id)
        if (webClientId.isBlank()) throw AuthFailure("oauth_missing","Falta configurar el acceso con Google.")
        try {
            val option = GetSignInWithGoogleOption.Builder(webClientId).build()
            val result = manager.getCredential(activityContext,GetCredentialRequest.Builder().addCredentialOption(option).build())
            return googleToken(result.credential)
        } catch (_: GetCredentialCancellationException) {
            throw AuthFailure("cancelled","Acceso con Google cancelado.")
        } catch (_: NoCredentialException) {
            throw AuthFailure("no_google_account","No hay una cuenta Google disponible. Añade una cuenta al dispositivo e intenta de nuevo.")
        } catch (_: GetCredentialProviderConfigurationException) {
            throw AuthFailure("play_services","Actualiza Google Play Services para continuar con Google.")
        } catch (failure: CancellationException) { throw failure }
        catch (failure: AuthFailure) { throw failure }
        catch (_: GetCredentialException) { throw AuthFailure("google_error","No pudimos abrir el acceso con Google. Revisa tu conexión y la configuración OAuth.") }
    }
    override suspend fun clear() {
        try {withTimeout(15_000){manager.clearCredentialState(ClearCredentialStateRequest())}}
        catch(_:TimeoutCancellationException){throw AuthFailure("credential_cleanup","La sesión se cerró. Selecciona tu cuenta al volver a entrar con Google.")}
    }
}

internal fun googleToken(credential: Credential): String {
    if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) throw AuthFailure("invalid_google_credential","Google devolvió una credencial no compatible.")
    return try { GoogleIdTokenCredential.createFrom(credential.data).idToken }
    catch (_: GoogleIdTokenParsingException) { throw AuthFailure("invalid_google_credential","No pudimos leer la credencial de Google. Intenta de nuevo.") }
}
