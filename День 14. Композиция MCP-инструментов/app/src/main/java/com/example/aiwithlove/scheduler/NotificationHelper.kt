package com.example.aiwithlove.scheduler

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.aiwithlove.MainActivity
import com.example.aiwithlove.R

class NotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE,
                "Joke Scheduler Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when joke scheduler is running"
                setShowBadge(false)
            }

            val jokeChannel = NotificationChannel(
                CHANNEL_JOKES,
                "Joke Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Periodic joke notifications"
                enableVibration(true)
            }

            val errorChannel = NotificationChannel(
                CHANNEL_ERRORS,
                "Error Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Error notifications when something goes wrong"
            }

            notificationManager.createNotificationChannels(
                listOf(serviceChannel, jokeChannel, errorChannel)
            )
        }
    }

    fun buildForegroundNotification(): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setContentTitle("🎭 Планировщик шуток активен")
            .setContentText("Получаю шутки каждые 30 секунд...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun showJokeNotification(joke: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_JOKES)
            .setContentTitle("😄 Шутка дня")
            .setContentText(joke)
            .setStyle(NotificationCompat.BigTextStyle().bigText(joke))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_JOKE_ID, notification)
    }

    fun showErrorNotification(errorMessage: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ERRORS)
            .setContentTitle("❌ Ошибка получения шутки")
            .setContentText(errorMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(errorMessage))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ERROR_ID, notification)
    }

    companion object {
        const val CHANNEL_SERVICE = "joke_scheduler_service"
        const val CHANNEL_JOKES = "joke_notifications"
        const val CHANNEL_ERRORS = "error_notifications"
        const val NOTIFICATION_SERVICE_ID = 1001
        const val NOTIFICATION_JOKE_ID = 1002
        const val NOTIFICATION_ERROR_ID = 1003
    }
}
