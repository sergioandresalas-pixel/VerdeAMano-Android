package com.verdeamano.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.verdeamano.contracts.Validation
import com.verdeamano.core.auth.*
import com.verdeamano.core.data.*
import com.verdeamano.core.session.SessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

data class AuthState(val loading: Boolean = false, val error: String? = null, val message: String? = null,
    val restoring: Boolean = false, val needsTerms: Boolean = false, val showLogin: Boolean = false, val providers: Set<String> = emptySet(), val emailVerified: Boolean = false,val degradedCart:RecentCartSnapshot?=null,val localUnlock:Boolean=false,val biometricEnabled:Boolean=false)

@HiltViewModel class AuthViewModel @Inject constructor(private val repository: AuthRepository, private val google: GoogleCredentialClient, private val store: SessionStore,private val biometrics:LocalBiometricPrompt?=null) : ViewModel() {
    val session = store.session
    private val mutable = MutableStateFlow(AuthState())
    val state = mutable.asStateFlow()
    private var biometricRunning=false
    init {
        restore()
        viewModelScope.launch {
            var hadSession = false
            session.collect { current ->
                if (current != null) hadSession = true
                else if (hadSession) { hadSession = false; mutable.value = mutable.value.copy(showLogin=true,providers=emptySet(),localUnlock=repository.requiresLocalUnlock,degradedCart=null) }
            }
        }
    }
    fun authenticate(name: String?, email: String, password: String, confirmation: String = "", acceptedTerms: Boolean = false) {
        AuthValidation.error(name,email,password,confirmation,acceptedTerms)?.let { mutable.value = mutable.value.copy(error=it); return }
        execute { if (name == null) repository.login(email,password) else repository.register(name,email,password) }
    }
    fun google(context: Context, acceptedTerms: Boolean = false) = execute { repository.google(google.select(context),acceptedTerms) }
    fun linkGoogle(context: Context) = execute { repository.linkGoogle(google.select(context)) }
    fun restore() = execute(restoring=repository.hasIdentity) { repository.restore() }
    fun viewRecentCart(){repository.recentCart()?.let{mutable.value=mutable.value.copy(degradedCart=it)}}
    fun hasRecentCart()=repository.recentCart()!=null
    fun biometric(context:Context,enable:Boolean=false){
        val uid=repository.currentUid?:return
        if(state.value.loading || (enable&&session.value==null))return
        mutable.value=mutable.value.copy(loading=true,error=null,message=null)
        val prompt=biometrics
        if(prompt==null){mutable.value=mutable.value.copy(loading=false,error="Biometría no disponible; usa correo o Google.");return}
        biometricRunning=true
        prompt.authenticate(context,uid,enable){failure->
            biometricRunning=false
            if(repository.currentUid!=uid)return@authenticate
            mutable.value=mutable.value.copy(loading=false,error=failure,biometricEnabled=repository.biometricEnabled)
            if(failure==null){if(enable)mutable.value=mutable.value.copy(message="Bloqueo biométrico activado para esta cuenta en este dispositivo.")else restore()}
        }
    }
    fun disableBiometrics(){if(session.value==null||state.value.loading)return;try{repository.disableBiometrics();mutable.value=mutable.value.copy(biometricEnabled=false,message="Bloqueo biométrico desactivado.")}catch(_:Exception){mutable.value=mutable.value.copy(error="No se pudo cambiar la protección local.")}}
    fun cancelBiometricAttempt(){if(biometricRunning){biometricRunning=false;biometrics?.cancel();mutable.value=mutable.value.copy(loading=false)}}
    fun lockForBackground(){if(repository.lockLocalSession()){cancelBiometricAttempt();mutable.value=AuthState(localUnlock=true,biometricEnabled=true)}}
    override fun onCleared(){biometrics?.cancel();super.onCleared()}
    fun completeProfile(name: String, acceptedTerms: Boolean) {
        if (!acceptedTerms || name.trim().length !in 2..120) { mutable.value = mutable.value.copy(error="Indica tu nombre y acepta las condiciones."); return }
        execute { repository.finishProfile(name.trim(),true) }
    }
    fun reset(email: String) {
        if (!Validation.email(email.trim())) { mutable.value = mutable.value.copy(error="Indica un correo válido."); return }
        execute(message="Si el correo tiene acceso con contraseña, recibirás instrucciones. Si utilizas Google, continúa con Google.") { repository.reset(email) }
    }
    fun sendVerification() = execute(message="Firebase aceptó el envío del enlace. Comprueba tu correo y completa la verificación.") { repository.sendVerification() }
    fun refreshVerification() = execute { repository.refreshVerification() }
    private fun execute(restoring: Boolean = false, message: String? = null, action: suspend () -> Unit) {
        if (mutable.value.loading) return
        mutable.value = mutable.value.copy(loading=true,error=null,message=null,restoring=restoring,needsTerms=if(restoring) false else mutable.value.needsTerms,degradedCart=null)
        viewModelScope.launch {
            // Keep the guest navigation graph when recovery succeeds after logout, so its dialog can show the result.
            try { action(); mutable.value = AuthState(showLogin=mutable.value.showLogin,message=message,providers=repository.providers,emailVerified=repository.emailVerified,biometricEnabled=repository.biometricEnabled,localUnlock=repository.requiresLocalUnlock) }
            catch (failure: CancellationException) {
                if(!repository.hasIdentity && session.value == null) mutable.value=AuthState(showLogin=true)
                throw failure
            }
            catch (failure: Exception) {
                val needsTerms = failure is AuthFailure && failure.code == "terms_required"
                val cancelled = failure is AuthFailure && failure.code == "cancelled"
                val locked=repository.requiresLocalUnlock || (failure is AuthFailure && failure.code=="local_unlock_required")
                mutable.value = mutable.value.copy(error=if(cancelled) null else failure.authMessage(),message=if(cancelled) failure.message else null,
                    restoring=repository.hasIdentity && session.value == null && !needsTerms&&!locked,needsTerms=needsTerms,providers=repository.providers,emailVerified=repository.emailVerified,localUnlock=locked,biometricEnabled=repository.biometricEnabled)
            } finally { mutable.value = mutable.value.copy(loading=false) }
        }
    }
    fun logout() {
        if (mutable.value.loading) return
        mutable.value = mutable.value.copy(loading=true,error=null,message=null)
        viewModelScope.launch {
            var cleanupWarning: String? = null
            try { repository.logout() }
            catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { cleanupWarning = "La sesión local se cerró; el servidor no respondió al cierre." }
            finally {
                try { google.clear() }
                catch (failure: CancellationException) { throw failure }
                catch (_: Exception) { cleanupWarning = "La sesión se cerró. Selecciona tu cuenta al volver a entrar con Google." }
                mutable.value = AuthState(showLogin=true,message=cleanupWarning)
            }
        }
    }
    fun clearError() { mutable.value = mutable.value.copy(error=null,message=null) }
}

private fun Exception.authMessage() = if (this is AuthFailure) message else userMessage()
