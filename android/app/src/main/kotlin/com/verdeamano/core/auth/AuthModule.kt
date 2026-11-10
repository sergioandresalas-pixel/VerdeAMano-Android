package com.verdeamano.core.auth

import com.google.firebase.auth.FirebaseAuth
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
abstract class AuthModule {
    @Binds @Singleton abstract fun identity(client: FirebaseIdentityClient): IdentityClient
    @Binds @Singleton abstract fun google(client: GoogleSignInService): GoogleCredentialClient
    @Binds @Singleton abstract fun pushCleanup(client:FirebasePushSessionCleanup):PushSessionCleanup
    companion object {
        @Provides @Singleton fun firebase(): FirebaseAuth = FirebaseAuth.getInstance()
    }
}
