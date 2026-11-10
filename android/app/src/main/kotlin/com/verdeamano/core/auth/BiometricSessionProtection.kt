package com.verdeamano.core.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** No biometric data or Firebase credential is stored here. The Android Keystore authorizes each cryptographic use. */
@Singleton class BiometricSessionVault @Inject constructor(@ApplicationContext context:Context):LocalUnlockGate {
    private val preferences=context.getSharedPreferences("local-biometric-session",Context.MODE_PRIVATE)
    @Volatile private var allowedUid:String?=null
    private fun key(uid:String)=MessageDigest.getInstance("SHA-256").digest(uid.toByteArray()).joinToString(""){"%02x".format(it)}
    private fun alias(uid:String)="vam_local_bio_"+key(uid)
    private fun keys()=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
    override fun enabled(uid:String)=preferences.getBoolean(key(uid)+".enabled",false)
    override fun requiresUnlock(uid:String)=enabled(uid)&&allowedUid!=uid
    override fun freshLogin(uid:String){allowedUid=uid}
    internal fun enrollmentAuthorized(uid:String)=allowedUid==uid
    override fun lock(uid:String){if(allowedUid==uid)allowedUid=null}
    override fun disable(uid:String){
        check(allowedUid==uid){"A locally authenticated session is required"}
        val prefix=key(uid);check(preferences.edit().remove("$prefix.enabled").remove("$prefix.iv").remove("$prefix.sealed").commit())
        keys().deleteEntry(alias(uid))
    }
    internal fun prepare(uid:String,enable:Boolean):Cipher {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        if(enable){
            check(allowedUid==uid){"Sign in before enabling biometric protection"}
            val specification=KeyGenParameterSpec.Builder(alias(uid),KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true).setInvalidatedByBiometricEnrollment(true)
            if(Build.VERSION.SDK_INT>=30)specification.setUserAuthenticationParameters(0,KeyProperties.AUTH_BIOMETRIC_STRONG)
            else @Suppress("DEPRECATION") specification.setUserAuthenticationValidityDurationSeconds(-1)
            val generated=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{init(specification.build())}.generateKey()
            cipher.init(Cipher.ENCRYPT_MODE,generated)
        }else{
            check(enabled(uid))
            val stored=keys().getKey(alias(uid),null) as SecretKey
            val iv=Base64.decode(requireNotNull(preferences.getString(key(uid)+".iv",null)),Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE,stored,GCMParameterSpec(128,iv))
        }
        return cipher
    }
    internal fun complete(uid:String,enable:Boolean,cipher:Cipher){
        val marker=("VerdeAMano-local-session-v1:"+uid).toByteArray(Charsets.UTF_8)
        if(enable){
            val sealed=cipher.doFinal(marker);val prefix=key(uid)
            check(preferences.edit().putString("$prefix.iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP)).putString("$prefix.sealed",Base64.encodeToString(sealed,Base64.NO_WRAP)).putBoolean("$prefix.enabled",true).commit())
        }else{
            val sealed=Base64.decode(requireNotNull(preferences.getString(key(uid)+".sealed",null)),Base64.NO_WRAP)
            check(MessageDigest.isEqual(marker,cipher.doFinal(sealed))){"The cryptographic marker does not match this identity"}
        }
        allowedUid=uid
    }
}

@Singleton class LocalBiometricPrompt @Inject constructor(private val vault:BiometricSessionVault,private val identity:IdentityClient){
    private var cancellation:CancellationSignal?=null
    fun authenticate(context:Context,uid:String,enable:Boolean,result:(String?)->Unit){
        cancel()
        var base=context
        while(base is ContextWrapper&&base !is Activity)base=base.baseContext
        val activity=base as? Activity
        if(activity==null||activity.isFinishing||identity.current?.uid!=uid){result("Vuelve a iniciar sesión para continuar.");return}
        val manager=activity.getSystemService(BiometricManager::class.java)
        val capability=if(Build.VERSION.SDK_INT>=30)manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)else @Suppress("DEPRECATION") manager.canAuthenticate()
        if(capability!=BiometricManager.BIOMETRIC_SUCCESS){result("No hay biometría compatible disponible. Puedes usar correo y contraseña o Google.");return}
        val cipher=try{vault.prepare(uid,enable)}catch(error:Exception){android.util.Log.w("VamLocalBiometric","prepare_failed type="+error.javaClass.simpleName);result("No pudimos preparar la protección local. Accede con correo o Google y vuelve a activarla desde Perfil.");return}
        val signal=CancellationSignal();cancellation=signal
        val builder=BiometricPrompt.Builder(activity).setTitle(if(enable)"Activar bloqueo biométrico"else"Desbloquear Verde a Mano")
            .setSubtitle("Android verifica tu biometría. No enviamos ni guardamos huellas.")
            .setNegativeButton("Cancelar",activity.mainExecutor){_,_->result("Biometría cancelada. Puedes usar correo o Google.")}
        if(Build.VERSION.SDK_INT>=30)builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        builder.build().authenticate(BiometricPrompt.CryptoObject(cipher),signal,activity.mainExecutor,object:BiometricPrompt.AuthenticationCallback(){
            override fun onAuthenticationSucceeded(authenticated:BiometricPrompt.AuthenticationResult){
                if(cancellation!==signal||identity.current?.uid!=uid||activity.isFinishing||activity.isDestroyed||(activity as? androidx.lifecycle.LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)!=true)return
                try{vault.complete(uid,enable,requireNotNull(authenticated.cryptoObject?.cipher));result(null)}catch(_:Exception){result("No pudimos verificar la protección local. Usa tu otro método de acceso.")}
                cancellation=null
            }
            override fun onAuthenticationError(code:Int,message:CharSequence){if(cancellation===signal){cancellation=null;result("No se completó la biometría. Puedes usar correo o Google sin bloquear tu cuenta.")}}
        })
    }
    fun cancel(){cancellation?.cancel();cancellation=null}
}
@Module @InstallIn(SingletonComponent::class) abstract class LocalUnlockModule {
    @Binds @Singleton abstract fun gate(vault:BiometricSessionVault):LocalUnlockGate
}
