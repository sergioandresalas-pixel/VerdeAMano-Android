package com.verdeamano.core.auth

import android.app.NotificationManager
import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.verdeamano.contracts.PushRegistration
import com.verdeamano.core.network.VerdeApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

interface PushSessionCleanup { suspend fun clear() }
object NoPushSessionCleanup:PushSessionCleanup {override suspend fun clear()=Unit}
class FirebasePushSessionCleanup @Inject constructor(@ApplicationContext private val context:Context,private val api:VerdeApi):PushSessionCleanup {
    override suspend fun clear() {
        context.getSystemService(NotificationManager::class.java).cancelAll()
        val messaging=FirebaseMessaging.getInstance()
        if(!messaging.isAutoInitEnabled)return
        try{withTimeout(10000){val token=messaging.token.await();try{api.unregisterPush(PushRegistration(token))}finally{messaging.deleteToken().await()}}}
        finally{messaging.isAutoInitEnabled=false}
    }
}
