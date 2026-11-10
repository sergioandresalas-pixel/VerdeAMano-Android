package com.verdeamano.core.network

import com.verdeamano.BuildConfig
import com.verdeamano.core.session.SessionStore
import com.verdeamano.core.auth.IdentityClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class) object NetworkModule {
    @Provides @Singleton fun json() = Json { ignoreUnknownKeys = true }
    @Provides @Singleton fun client(authentication: FirebaseHttpSession): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .addInterceptor(authentication).authenticator(authentication).build()
    @Provides @Singleton fun api(client: OkHttpClient, json: Json, identity: IdentityClient, session: SessionStore): VerdeApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL).callFactory(FirebaseCallFactory(client,identity,session)).addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build().create(VerdeApi::class.java)
}
