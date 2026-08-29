package com.example.linkmesh

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity: FlutterActivity() {
    private val systemChannel = "linkmesh/system"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        configureUrgentSirenChannel()
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, systemChannel).setMethodCallHandler { call, result ->
            if (call.method == "openNotificationPolicySettings") {
                try {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    result.success(null)
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                    result.success(null)
                }
            } else {
                result.notImplemented()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        configureUrgentSirenChannel()
    }

    private fun configureUrgentSirenChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val soundId = resources.getIdentifier("linkmesh_siren", "raw", packageName)
        val sound = Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://$packageName/$soundId")
        val audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        val channel = manager.getNotificationChannel("linkmesh_siren_v2")
            ?: NotificationChannel("linkmesh_siren_v2", "Urgent LinkMesh sirens", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Urgent sirens from trusted LinkMesh devices"
                enableVibration(true)
                setSound(sound, audioAttributes)
            }
        if (manager.isNotificationPolicyAccessGranted) channel.setBypassDnd(true)
        manager.createNotificationChannel(channel)
    }
}
