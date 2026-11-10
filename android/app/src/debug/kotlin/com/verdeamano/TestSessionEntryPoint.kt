package com.verdeamano

import com.verdeamano.core.session.SessionStore
import com.verdeamano.core.network.VerdeApi
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// Included in the debug Hilt component only. Tests clear local state; roles still come from the API.
@EntryPoint @InstallIn(SingletonComponent::class)
interface TestSessionEntryPoint { fun sessionStore(): SessionStore; fun api(): VerdeApi; fun authRepository():com.verdeamano.core.auth.AuthRepository;fun mediaClient():okhttp3.OkHttpClient;fun orderRepository():com.verdeamano.core.data.OrderRepository;fun biometricVault():com.verdeamano.core.auth.BiometricSessionVault }
