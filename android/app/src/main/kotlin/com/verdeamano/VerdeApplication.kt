package com.verdeamano

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.verdeamano.core.auth.IdentityClient
import com.verdeamano.core.network.FirebaseCallFactory
import com.verdeamano.core.session.SessionStore
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp class VerdeApplication : Application(), SingletonImageLoader.Factory {
    @Inject lateinit var client:OkHttpClient
    @Inject lateinit var identity:IdentityClient
    @Inject lateinit var session:SessionStore
    override fun newImageLoader(context:Context):ImageLoader=ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory={FirebaseCallFactory(client,identity,session)})) }.build()
}
