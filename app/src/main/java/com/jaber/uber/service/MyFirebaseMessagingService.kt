package com.jaber.uber.service

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.jaber.uber.Common
import com.jaber.uber.utils.UserUtils
import kotlin.random.Random

class MyFirebaseMessagingService:FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if(FirebaseAuth.getInstance().currentUser != null){
            UserUtils.updateToken(this, token)
        }
    }

    override fun onMessageReceived(remotMessage: RemoteMessage) {
        super.onMessageReceived(remotMessage)
        val data = remotMessage.data
        if(data !=null){
            Common.showNotification(this, Random.nextInt(),data[Common.NOTIF_TITLE],data[Common.NOTIF_BODY],null)
        }
    }
}