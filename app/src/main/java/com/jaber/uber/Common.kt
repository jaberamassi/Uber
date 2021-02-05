package com.jaber.uber

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import com.jaber.uber.model.DriverInfoModel
import java.lang.StringBuilder

object Common {

    var currentDriver: DriverInfoModel? = null

    const val TOKEN_REFERENCE: String = "Token"
    const val DRIVER_INFO_REFERENCE = "driverInfo"
    const val DRIVERS_LOCATION_REFERENCE: String = "driversLocation"

    const val NOTIF_TITLE: String = "title"
    const val NOTIF_BODY: String = "body"

    fun buildWelcomeMessage(): String {
        return StringBuilder("Welcome, ")
            .append(currentDriver!!.firstName)
            .append(" ")
            .append(currentDriver!!.lastName)
            .toString()
    }

    fun showNotification(context: Context, id: Int, title: String?, body: String?, intent: Intent?) {
        var pendingIntent:PendingIntent? = null
        if (intent != null){
            pendingIntent = PendingIntent.getActivity(context,id, intent,PendingIntent.FLAG_UPDATE_CURRENT)
            val NOTIFECATION_CHANNEL_ID = "jaber_uber"
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                val notificationChannel = NotificationChannel(NOTIFECATION_CHANNEL_ID,"Uber",NotificationManager.IMPORTANCE_HIGH)
                notificationChannel.description = "Uber"
                notificationChannel.enableLights(true)
                notificationChannel.lightColor = Color.RED
                notificationChannel.enableVibration(true)
                notificationChannel.vibrationPattern = longArrayOf(0,1000,500,100)

                notificationManager.createNotificationChannel(notificationChannel)
            }

            val builder = NotificationCompat.Builder(context,NOTIFECATION_CHANNEL_ID)
            builder.setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(false)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(Notification.DEFAULT_VIBRATE)
                .setSmallIcon(R.drawable.ic_car)
                .setLargeIcon(BitmapFactory.decodeResource(context.resources,R.drawable.ic_car))

            if (pendingIntent !=null){
                builder.setContentIntent(pendingIntent)
            }

            val notification = builder.build()
            notificationManager.notify(id,notification)
        }

    }



}