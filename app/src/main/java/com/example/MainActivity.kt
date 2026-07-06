package com.example

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.database.MeshService
import com.example.database.MeshRepository
import com.example.ui.MeshRootView
import com.example.ui.MeshViewModel
import com.example.ui.theme.MyApplicationTheme

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
    private lateinit var meshViewModel: MeshViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup ViewModel
        meshViewModel = ViewModelProvider(this)[MeshViewModel::class.java]

        // Start offline mesh tracking/monitoring foreground service
        try {
            val serviceIntent = Intent(this, MeshService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Request notification permission if API level is 33+ (Tiramisu)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            try {
                val permissionCheck = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                if (permissionCheck != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Process incoming call intents
        handleIncomingIntent(intent)

        enableEdgeToEdge()
        setContent {
            val isDark by meshViewModel.isDarkTheme.collectAsState()
            MyApplicationTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MeshRootView(viewModel = meshViewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val action = intent?.getStringExtra("action")
        val peerMac = intent?.getStringExtra("peerMac")
        val callerName = intent?.getStringExtra("callerName") ?: "Peer"
        val isVideo = intent?.getBooleanExtra("isVideo", false) ?: false

        Log.d("MainActivity", "handleIncomingIntent: action=$action, peerMac=$peerMac")

        if (peerMac != null) {
            if (action == "answer" || action == "decline" || action == "ring") {
                // Ensure the call session is populated in our view model
                meshViewModel.setIncomingCallSession(peerMac, callerName, isVideo)
                meshViewModel.navigateTo("calls")
                
                if (action == "answer") {
                    meshViewModel.acceptIncomingCall()
                } else if (action == "decline") {
                    meshViewModel.rejectOrEndCall()
                }
            }
        }
        
        // Dismiss the notification programmatically
        try {
            val repo = MeshRepository.getInstance(applicationContext)
            repo.getNetworkManager()?.cancelCallNotification()
        } catch (e: Exception) {}
    }
}
