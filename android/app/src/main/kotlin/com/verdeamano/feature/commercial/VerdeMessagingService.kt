package com.verdeamano.feature.commercial

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.auth.FirebaseAuth
import com.verdeamano.MainActivity
import com.verdeamano.R
import kotlinx.coroutines.launch

@dagger.hilt.android.AndroidEntryPoint
class VerdeMessagingService:FirebaseMessagingService() {
    @javax.inject.Inject lateinit var repository:com.verdeamano.core.data.CommercialRepository
    @javax.inject.Inject lateinit var session:com.verdeamano.core.session.SessionStore
    private val scope=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()+kotlinx.coroutines.Dispatchers.IO)
    override fun onNewToken(token:String){
        val uid=FirebaseAuth.getInstance().currentUser?.uid?:return
        if(!com.google.firebase.messaging.FirebaseMessaging.getInstance().isAutoInitEnabled||session.firebaseUid!=uid||session.session.value==null)return
        scope.launchSafe{repository.push(token,uid)}
    }
    private fun kotlinx.coroutines.CoroutineScope.launchSafe(action:suspend()->Unit){
        launch {
            try{kotlinx.coroutines.withTimeout(10000){action()}}
            catch(_:kotlinx.coroutines.CancellationException){throw kotlinx.coroutines.CancellationException("Push registration cancelled")}
            catch(_:Exception){ /* No token logging or resend of historical notifications. Inbox remains available. */ }
        }
    }
    override fun onDestroy(){scope.coroutineContext[kotlinx.coroutines.Job]?.cancel();super.onDestroy()}
    override fun onMessageReceived(message:RemoteMessage) {
        val uid=FirebaseAuth.getInstance().currentUser?.uid?:return
        if(!com.google.firebase.messaging.FirebaseMessaging.getInstance().isAutoInitEnabled)return
        if(message.data["recipient_uid"]!=uid)return
        val id=message.data["notification_id"]?:return
        if(!id.matches(Regex("[a-f0-9-]{36}")))return
        val receipts=getSharedPreferences("commercial-push-receipts",MODE_PRIVATE)
        val receiptKey=uid+":"+id
        if(receipts.contains(receiptKey))return // Legacy uncertain events are not converted into new acknowledgements.
        try{repository.recordDeviceReceipt(uid,id)}catch(_:Exception){return}
        if(!receipts.edit().putLong(receiptKey,System.currentTimeMillis()).commit())return
        if(session.firebaseUid==uid&&session.session.value!=null)scope.launchSafe{repository.reconcileDeviceReceipts()}
        val manager=getSystemService(NotificationManager::class.java)
        if(!manager.areNotificationsEnabled())return // SDK receipt and permission to display are distinct facts.
        manager.createNotificationChannel(NotificationChannel("commercial","Pedidos y reclamaciones",NotificationManager.IMPORTANCE_DEFAULT))
        val intent=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification=NotificationCompat.Builder(this,"commercial").setSmallIcon(R.drawable.ic_leaf).setContentTitle("Verde a Mano").setContentText("Hay una actualización en tu bandeja de notificaciones.").setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(intent).setAutoCancel(true).setOnlyAlertOnce(true).build()
        try{manager.notify(id,0,notification)}catch(_:SecurityException){ /* Inbox remains available when notification permission is denied. */ }
    }
}
