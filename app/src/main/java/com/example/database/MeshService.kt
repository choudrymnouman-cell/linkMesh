package com.example.database

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class MeshService : Service() {

    private val TAG = "MeshService"
    private val SERVICE_CHANNEL_ID = "mesh_background_service_channel"
    private val SERVICE_NOTIF_ID = 9182

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MeshService onCreate called, promoting to foreground.")
        createNotificationChannel()
        startForegroundServiceCompat()

        // Access/instantiate the singleton MeshRepository
        // This triggers and keeps the MeshLocalNetworkManager, Socket listening,
        // and NSD discovery engine active under custom global scope.
        try {
            val repository = MeshRepository.getInstance(applicationContext)
            Log.d(TAG, "MeshRepository singleton successfully synchronized with MeshService.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MeshRepository in service: ${e.message}", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "MeshService onStartCommand received.")
        // Keep service running until explicitly stopped
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                "Mesh Network Active Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps secure offline mesh networking service running in background"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun startForegroundServiceCompat() {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setContentTitle("Mesh Connect Active")
            .setContentText("Offline direct communication channel is active in background")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    SERVICE_NOTIF_ID, 
                    notification, 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(SERVICE_NOTIF_ID, notification)
            }
            Log.d(TAG, "Foreground service started successfully with type connectedDevice")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service: ${e.message}", e)
            // Fallback
            startForeground(SERVICE_NOTIF_ID, notification)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "MeshService onDestroy called.")
        super.onDestroy()
    }
}
