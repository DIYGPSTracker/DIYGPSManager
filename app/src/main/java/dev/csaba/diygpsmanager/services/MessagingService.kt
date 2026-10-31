package dev.csaba.diygpsmanager.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dev.csaba.diygpsmanager.R
import dev.csaba.diygpsmanager.ui.MainActivity
import kotlin.random.Random
import timber.log.Timber


class MessagingService : FirebaseMessagingService() {
    companion object {
        private const val GEO_FENCE_CHANNEL_ID = "geo_fence_channel"
        private const val CHANNEL_NAME = "Geofence notification"
        private const val CHANNEL_DESCRIPTION = "Tracker to Manager notification"
    }

    /**
     * Called if the FCM registration token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the
     * FCM registration token is initially generated so this is where you would retrieve the token.
     */
    override fun onNewToken(token: String) {
        Timber.d("Refreshed token: $token")
        super.onNewToken(token)

        // If you want to send messages to this application instance or
        // manage this apps subscriptions on the server side, send the
        // FCM registration token to your app server.
        // sendRegistrationToServer(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val intent = Intent(this, MainActivity::class.java)
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationID = Random.nextInt(3000)

        setupChannels(notificationManager)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val largeIcon = BitmapFactory.decodeResource(
            resources,
            R.drawable.ic_warning
        )
        val notificationSoundUri =
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder =
            NotificationCompat.Builder(this, GEO_FENCE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_warning)
                .setLargeIcon(largeIcon)
                .setContentTitle(remoteMessage.data["title"])
                .setContentText(remoteMessage.data["message"])
                .setAutoCancel(true)
                .setSound(notificationSoundUri)
                .setContentIntent(pendingIntent)

        // Set notification color to match your app color template
        notificationBuilder.color = ContextCompat.getColor(this, R.color.colorAccent)
        notificationManager.notify(notificationID, notificationBuilder.build())
    }

    private fun setupChannels(notificationManager: NotificationManager?) {
        val adminChannel = NotificationChannel(
            GEO_FENCE_CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = CHANNEL_DESCRIPTION
            lightColor = Color.RED
        }
        adminChannel.enableLights(true)
        adminChannel.enableVibration(true)
        notificationManager?.createNotificationChannel(adminChannel)
    }

}
