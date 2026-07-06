package com.example.database

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

class MeshLocalNetworkManager(
    private val context: Context,
    private val repository: MeshRepository,
    private val scope: CoroutineScope
) {
    private val TAG = "MeshLocalNet"
    private val SERVICE_TYPE = "_meshconnect._tcp."

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var serverSocket: ServerSocket? = null
    private var localPort = 0
    private var isRunning = false
    
    // Locks multicast for reliable mDNS on all Android devices
    private var multicastLock: WifiManager.MulticastLock? = null

    // Track resolved peers: Unique ID (senderId/MAC) -> Peer details
    data class ResolvedPeer(
        val username: String,
        val hostAddress: String,
        val port: Int,
        val lastSeen: Long = System.currentTimeMillis()
    )
    val activePeers = ConcurrentHashMap<String, ResolvedPeer>()

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    init {
        acquireMulticastLock()
        startServerSocket()
        registerService()
        startDiscovery()
        startHeartbeatLoop()
    }

    private fun acquireMulticastLock() {
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifi.createMulticastLock("MeshConnectMulticastLock").apply {
                setReferenceCounted(true)
                acquire()
            }
            Log.d(TAG, "Multicast Lock Acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire multicast lock: ${e.message}")
        }
    }

    private fun startServerSocket() {
        try {
            // Bind to any available port
            serverSocket = ServerSocket(0)
            localPort = serverSocket!!.localPort
            isRunning = true
            Log.d(TAG, "Socket Server started on port $localPort")

            scope.launch(Dispatchers.IO) {
                while (isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        handleIncomingConnection(clientSocket)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error accepting client socket connection: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start socket server: ${e.message}")
        }
    }

    fun getBatteryLevel(): Int {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            val valCap = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (valCap > 0 && valCap <= 100) {
                return valCap
            }
            val batteryStatus: android.content.Intent? = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val level = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level != -1 && scale != -1) {
                ((level.toFloat() / scale.toFloat()) * 100).toInt()
            } else {
                88
            }
        } catch (e: Exception) {
            88
        }
    }

    private fun showSystemNotification(title: String, message: String) {
        try {
            val channelId = "mesh_messages"
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    "Mesh Communications",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notification channel for offline secure mesh communication"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show system notification: ${e.message}")
        }
    }

    private fun showCallNotification(callerName: String, callerMac: String, isVideo: Boolean) {
        try {
            val channelId = "mesh_calls"
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    "Mesh Calls",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Incoming offline calls"
                    setSound(null, null)
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val fullScreenIntent = android.content.Intent(context, com.example.MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("action", "ring")
                putExtra("peerMac", callerMac)
                putExtra("callerName", callerName)
                putExtra("isVideo", isVideo)
            }
            val fullScreenPendingIntent = android.app.PendingIntent.getActivity(
                context,
                1001,
                fullScreenIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val answerIntent = android.content.Intent(context, com.example.MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("action", "answer")
                putExtra("peerMac", callerMac)
                putExtra("callerName", callerName)
                putExtra("isVideo", isVideo)
            }
            val answerPendingIntent = android.app.PendingIntent.getActivity(
                context,
                1002,
                answerIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val declineIntent = android.content.Intent(context, com.example.MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("action", "decline")
                putExtra("peerMac", callerMac)
                putExtra("callerName", callerName)
                putExtra("isVideo", isVideo)
            }
            val declinePendingIntent = android.app.PendingIntent.getActivity(
                context,
                1003,
                declineIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_phone_call)
                .setContentTitle("Incoming Offline Call")
                .setContentText("From $callerName (Tap to talk)")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setOngoing(true)
                .setAutoCancel(true)
                .addAction(android.R.drawable.ic_menu_call, "ANSWER", answerPendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "DECLINE", declinePendingIntent)

            notificationManager.notify(2211, builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Failed showing call notification: ${e.message}")
        }
    }

    fun cancelCallNotification() {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.cancel(2211)
        } catch (e: Exception) {}
    }

    private fun handleIncomingConnection(socket: Socket) {
        scope.launch(Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val line = reader.readLine() ?: ""
                socket.close()

                if (line.isNotEmpty()) {
                    Log.d(TAG, "Received JSON: $line")
                    val json = JSONObject(line)
                    val type = json.optString("type", "")
                    
                    when (type) {
                        "DISCOVER" -> {
                            val peerId = json.getString("uniqueId")
                            val peerUsername = json.getString("username")
                            val pPort = json.getInt("port")
                            val isSOS = json.optBoolean("isSOS", false)
                            val battery = json.optInt("battery", 80)
                            val photoBase64 = json.optString("photoBase64", null)
                            
                            val hostIp = socket.inetAddress.hostAddress ?: ""
                            if (hostIp.isNotEmpty()) {
                                updatePeerDetails(peerId, peerUsername, hostIp, pPort, isSOS, battery, photoBase64)
                            }
                        }
                        "MESSAGE" -> {
                            val msgId = json.getString("messageId")
                            val senderId = json.getString("senderId")
                            val senderName = json.getString("senderName")
                            val receiverId = json.getString("receiverId")
                            val isGroup = json.optBoolean("isGroup", false)
                            val textContent = json.getString("textContent")
                            val timestamp = json.optLong("timestamp", System.currentTimeMillis())
                            val attachmentType = json.optString("attachmentType", "NONE")
                            val attachmentPath = json.optString("attachmentPath", null)
                            val attachmentSize = json.optString("attachmentSize", null)
                            val voiceDuration = json.optInt("voiceDuration", 0)

                             var resolvedPath = attachmentPath
                             val attachmentData = json.optString("attachmentData", null)
                             if (attachmentType != "NONE" && !attachmentData.isNullOrEmpty()) {
                                 try {
                                     val bytes = android.util.Base64.decode(attachmentData, android.util.Base64.DEFAULT)
                                     val safeFileName = if (!attachmentPath.isNullOrEmpty()) {
                                         attachmentPath.substringAfterLast("/")
                                     } else {
                                         val ext = when (attachmentType) {
                                             "IMAGE" -> "jpg"
                                             "AUDIO" -> "3gp"
                                             else -> "bin"
                                         }
                                         "received_${msgId}.$ext"
                                     }
                                     val localFile = java.io.File(context.filesDir, safeFileName)
                                     localFile.writeBytes(bytes)
                                     resolvedPath = localFile.absolutePath
                                     Log.d(TAG, "Successfully saved received attachment local file: $resolvedPath, size: ${localFile.length()} bytes")
                                 } catch (e: Exception) {
                                     Log.e(TAG, "Failed decoding received attachment: ${e.message}", e)
                                 }
                             }

                            val incomingMsg = ChatMessage(
                                messageId = msgId,
                                senderId = senderId,
                                senderName = senderName,
                                receiverId = receiverId,
                                isGroup = isGroup,
                                textContent = textContent,
                                attachmentType = attachmentType,
                                attachmentPath = resolvedPath,
                                attachmentSize = attachmentSize,
                                timestamp = timestamp,
                                status = "READ",
                                hopsList = "Direct Local Link",
                                isIncoming = true,
                                voiceDurationSec = voiceDuration
                            )
                            repository.saveMessageDirect(incomingMsg)
                            
                            if (textContent == "[SIREN_ALERT]") {
                                MeshSoundPlayer.playLoudSiren(context)
                            } else if (textContent == "[STOP_SIREN]") {
                                MeshSoundPlayer.stopSound()
                            } else {
                                // Play message arrive tone & trigger status-bar notification
                                MeshSoundPlayer.playNotification()
                                showSystemNotification("Incoming Message", "$senderName: $textContent")
                            }

                            repository.triggerNetworkEvent(NetworkEvent.NetworkReconfigured("Incoming message from $senderName"))
                        }
                        "POST" -> {
                            val postId = json.getString("postId")
                            val authorUsername = json.getString("authorUsername")
                            val authorId = json.getString("authorId")
                            val messageContent = json.getString("messageContent")
                            val postType = json.optString("postType", "ALERT")
                            val timestamp = json.optLong("timestamp", System.currentTimeMillis())
                            val battery = json.optInt("battery", 85)
                            val location = json.optString("location", "Direct Link")

                            val post = CommunityPost(
                                postId = postId,
                                authorUsername = authorUsername,
                                authorId = authorId,
                                messageContent = messageContent,
                                postType = postType,
                                timestamp = timestamp,
                                batteryLevel = battery,
                                locationName = location,
                                upvotesCount = 0,
                                commentCount = 0
                            )
                            // Save post locally with authentic battery, ID and details
                            repository.insertPostDirect(post)

                            // Play public post arrive alert sound
                            MeshSoundPlayer.playNotification()
                            showSystemNotification("New Alert Post", "$authorUsername: $messageContent")

                            repository.triggerNetworkEvent(NetworkEvent.NetworkReconfigured("Synced Broad Post from $authorUsername"))
                        }
                        "CALL_INITIATE" -> {
                            val callerMac = json.getString("callerMac")
                            val callerName = json.getString("callerName")
                            val isVideo = json.optBoolean("isVideo", false)

                            // Play continuous ringtone sound immediately
                            MeshSoundPlayer.stopSound()
                            MeshSoundPlayer.startRingtone()

                            showCallNotification(callerName, callerMac, isVideo)
                            repository.triggerNetworkEvent(NetworkEvent.DirectCallReceived(callerName, callerMac, isVideo))
                        }
                        "CALL_ACCEPT" -> {
                            val senderMac = json.getString("senderMac")
                            MeshSoundPlayer.stopSound() // Stop ringtone/dialing tone!
                            cancelCallNotification()
                            repository.triggerNetworkEvent(NetworkEvent.CallTransitionReceived(senderMac, "ACTIVE"))
                        }
                        "CALL_END" -> {
                            val senderMac = json.getString("senderMac")
                            MeshSoundPlayer.stopSound() // Stop ringtone/dialing tone!
                            MeshSoundPlayer.stopCallAudioStream() // Stop call audio session!
                            cancelCallNotification()
                            repository.triggerNetworkEvent(NetworkEvent.CallTransitionReceived(senderMac, "ENDED"))
                        }
                        "CALL_AUDIO" -> {
                            val audioData = json.optString("audioData", "")
                            if (audioData.isNotEmpty()) {
                                try {
                                    val bytes = android.util.Base64.decode(audioData, android.util.Base64.DEFAULT)
                                    MeshSoundPlayer.playCallAudioChunk(bytes)
                                } catch (e: Exception) {
                                    // suppress trace for sound chunks
                                }
                            }
                        }
                        "CALL_VIDEO_FRAME" -> {
                            val senderMac = json.getString("senderMac")
                            val frameData = json.optString("frameData", "")
                            if (frameData.isNotEmpty()) {
                                repository.triggerNetworkEvent(NetworkEvent.CallVideoFrameReceived(senderMac, frameData))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling incoming client payload: ${e.message}")
            }
        }
    }

    private fun getProfilePhotoBase64(photoUriString: String?): String? {
        if (photoUriString.isNullOrEmpty()) return null
        try {
            val uri = android.net.Uri.parse(photoUriString)
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (originalBitmap == null) return null

            val size = 120
            val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(originalBitmap, size, size, true)
            val out = java.io.ByteArrayOutputStream()
            scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, out)
            val bytes = out.toByteArray()
            scaledBitmap.recycle()
            if (scaledBitmap != originalBitmap) {
                originalBitmap.recycle()
            }
            return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating profile photo base64: ${e.message}")
            return null
        }
    }

    private fun savePeerPhoto(peerId: String, base64: String?): String? {
        if (base64.isNullOrEmpty()) return null
        try {
            val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
            val outputDir = java.io.File(context.cacheDir, "peer_photos")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }
            val file = java.io.File(outputDir, "peer_${peerId}.jpg")
            java.io.FileOutputStream(file).use { out ->
                out.write(bytes)
                out.flush()
            }
            return file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save peer photo: ${e.message}")
            return null
        }
    }

    private suspend fun updatePeerDetails(
        peerId: String,
        username: String,
        hostIp: String,
        port: Int,
        isSOS: Boolean,
        battery: Int,
        photoBase64: String? = null
    ) {
        val currentProfile = repository.getProfile()
        if (currentProfile != null && currentProfile.uniqueId == peerId) {
            // Don't add ourselves to active peers list
            return
        }

        activePeers[peerId] = ResolvedPeer(username, hostIp, port)
        
        var savedLocalPhotoUri: String? = null
        if (!photoBase64.isNullOrEmpty()) {
            val savedPath = savePeerPhoto(peerId, photoBase64)
            if (savedPath != null) {
                savedLocalPhotoUri = "file://$savedPath"
            }
        } else {
            try {
                val list = repository.getAllDevices().first()
                val existing = list.find { it.macAddress == peerId }
                savedLocalPhotoUri = existing?.photoUri
            } catch (e: Exception) {
                // ignore
            }
        }

        // Save to Database as a physical discovered node
        val meshDevice = MeshDevice(
            macAddress = peerId, // using uniqueId in place of MAC
            username = username,
            signalStrength = 95, // local connection high metric
            isOnline = true,
            canRelay = true,
            batteryLevel = battery,
            isSOSActive = isSOS,
            lastSeenTimestamp = System.currentTimeMillis(),
            photoUri = savedLocalPhotoUri
        )
        repository.insertOrUpdateMeshDevice(meshDevice)
        repository.triggerNetworkEvent(NetworkEvent.NetworkReconfigured("Peer node connected: $username"))
    }

    private fun registerService() {
        scope.launch(Dispatchers.IO) {
            try {
                // Ensure profile exists
                var profile = repository.getProfile()
                while (profile == null) {
                    delay(500)
                    profile = repository.getProfile()
                }

                // Append custom identifiers to unique name
                val name = "Mesh_${profile.username}_${profile.uniqueId}"
                val serviceInfo = NsdServiceInfo().apply {
                    serviceName = name
                    serviceType = SERVICE_TYPE
                    setPort(localPort)
                }

                registrationListener = object : NsdManager.RegistrationListener {
                    override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                        Log.d(TAG, "NSD service registered successfully: ${NsdServiceInfo.serviceName}")
                    }

                    override fun onRegistrationFailed(NsdServiceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.e(TAG, "NSD Registration failed: $errorCode")
                    }

                    override fun onServiceUnregistered(NsdServiceInfo: NsdServiceInfo) {
                        Log.d(TAG, "NSD Service unregistered successfully")
                    }

                    override fun onUnregistrationFailed(NsdServiceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.e(TAG, "NSD Unregistration failed: $errorCode")
                    }
                }

                nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
            } catch (e: Exception) {
                Log.e(TAG, "Failed registering NSD service: ${e.message}")
            }
        }
    }

    private fun startDiscovery() {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "NSD Discovery start failed: $errorCode")
                try { nsdManager.stopServiceDiscovery(this) } catch (e: Exception) {}
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "NSD Discovery stop failed: $errorCode")
                try { nsdManager.stopServiceDiscovery(this) } catch (e: Exception) {}
            }

            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "NSD Discovery started successfully")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "NSD Discovery stopped")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "NSD Service found: ${serviceInfo.serviceName}")
                if (serviceInfo.serviceType == SERVICE_TYPE) {
                    val myProfile = runBlocking { repository.getProfile() }
                    val myNamePrefix = myProfile?.let { "Mesh_${it.username}_${it.uniqueId}" } ?: ""
                    
                    if (serviceInfo.serviceName != myNamePrefix && serviceInfo.serviceName.startsWith("Mesh_")) {
                        nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                                Log.e(TAG, "NSD Resolve service failed: $errorCode")
                            }

                            override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                                Log.d(TAG, "NSD Service resolved: ${resolvedInfo.host}:${resolvedInfo.port}")
                                parseAndConnectResolvedService(resolvedInfo)
                            }
                        })
                    }
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "NSD Service lost: ${serviceInfo.serviceName}")
                // Remove lost peers
                val parts = serviceInfo.serviceName.split("_")
                if (parts.size >= 3) {
                    val peerId = parts.last()
                    activePeers.remove(peerId)
                    scope.launch(Dispatchers.IO) {
                        repository.toggleDeviceOnline(peerId, false)
                    }
                }
            }
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed starting service discovery: ${e.message}")
        }
    }

    private fun parseAndConnectResolvedService(resolvedInfo: NsdServiceInfo) {
        val nameParts = resolvedInfo.serviceName.split("_")
        if (nameParts.size < 3) return
        val username = nameParts[1]
        val uniqueId = nameParts.last()

        val hostIp = resolvedInfo.host.hostAddress ?: ""
        val port = resolvedInfo.port

        scope.launch(Dispatchers.IO) {
            val myProfile = repository.getProfile()
            val photoBase64 = getProfilePhotoBase64(myProfile?.photoUri)
            updatePeerDetails(uniqueId, username, hostIp, port, isSOS = false, battery = getBatteryLevel())
            // Send discovery greeting payload back to establish bidirectional identification
            if (myProfile != null) {
                val discoveryPayload = JSONObject().apply {
                    put("type", "DISCOVER")
                    put("uniqueId", myProfile.uniqueId)
                    put("username", myProfile.username)
                    put("port", localPort)
                    put("isSOS", myProfile.rescueMode)
                    put("battery", getBatteryLevel())
                    if (photoBase64 != null) {
                        put("photoBase64", photoBase64)
                    }
                }
                sendJsonToPeer(hostIp, port, discoveryPayload)
            }
        }
    }

    fun forceBroadcastProfile() {
        scope.launch(Dispatchers.IO) {
            val myProfile = repository.getProfile() ?: return@launch
            val photoBase64 = getProfilePhotoBase64(myProfile.photoUri)
            val payload = JSONObject().apply {
                put("type", "DISCOVER")
                put("uniqueId", myProfile.uniqueId)
                put("username", myProfile.username)
                put("port", localPort)
                put("isSOS", myProfile.rescueMode)
                put("battery", getBatteryLevel())
                if (photoBase64 != null) {
                    put("photoBase64", photoBase64)
                }
            }
            activePeers.forEach { (_, peer) ->
                sendJsonToPeer(peer.hostAddress, peer.port, payload)
            }
        }
    }

    private fun startHeartbeatLoop() {
        scope.launch(Dispatchers.IO) {
            while (isRunning) {
                delay(12000)
                // Periodic discovery refresh/exchange
                val myProfile = repository.getProfile() ?: continue
                val photoBase64 = getProfilePhotoBase64(myProfile.photoUri)
                val payload = JSONObject().apply {
                    put("type", "DISCOVER")
                    put("uniqueId", myProfile.uniqueId)
                    put("username", myProfile.username)
                    put("port", localPort)
                    put("isSOS", myProfile.rescueMode)
                    put("battery", getBatteryLevel())
                    if (photoBase64 != null) {
                        put("photoBase64", photoBase64)
                    }
                }
                // Send heartbeat discovery greeting to all active peers on network
                activePeers.forEach { (_, peer) ->
                    sendJsonToPeer(peer.hostAddress, peer.port, payload)
                }
            }
        }
    }

    fun sendJsonToPeer(host: String, port: Int, json: JSONObject) {
        scope.launch(Dispatchers.IO) {
            try {
                val socket = Socket(host, port)
                val out = PrintWriter(socket.getOutputStream(), true)
                out.println(json.toString())
                socket.close()
                Log.d(TAG, "Successfully sent JSON payload to $host:$port")
            } catch (e: Exception) {
                Log.e(TAG, "Failed sending socket packet to $host:$port: ${e.message}")
            }
        }
    }

    fun transmitMessage(msg: ChatMessage) {
        val payload = JSONObject().apply {
            put("type", "MESSAGE")
            put("messageId", msg.messageId)
            put("senderId", msg.senderId)
            put("senderName", msg.senderName)
            put("receiverId", msg.receiverId)
            put("isGroup", msg.isGroup)
            put("textContent", msg.textContent)
            put("timestamp", msg.timestamp)
            put("attachmentType", msg.attachmentType)
            put("attachmentPath", msg.attachmentPath)
            put("attachmentSize", msg.attachmentSize)
            put("voiceDuration", msg.voiceDurationSec)

            // Read the binary file directly and base64-encode it to transmit live attachment data over sockets!
            if (msg.attachmentType != "NONE" && !msg.attachmentPath.isNullOrEmpty()) {
                try {
                    val file = java.io.File(msg.attachmentPath)
                    if (file.exists() && file.length() > 0) {
                        val bytes = file.readBytes()
                        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT or android.util.Base64.NO_WRAP)
                        put("attachmentData", base64)
                        Log.d(TAG, "Encoded local attachment into packet payload: ${file.length()} bytes")
                    } else if (msg.attachmentPath.startsWith("content://") || msg.attachmentPath.startsWith("file://")) {
                        try {
                            val uri = android.net.Uri.parse(msg.attachmentPath)
                            val inputStream = context.contentResolver.openInputStream(uri)
                            if (inputStream != null) {
                                val bytes = inputStream.readBytes()
                                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT or android.util.Base64.NO_WRAP)
                                put("attachmentData", base64)
                                inputStream.close()
                                Log.d(TAG, "Encoded Uri attachment into packet payload: ${bytes.size} bytes")
                            }
                        } catch (ex: Exception) {
                            Log.e(TAG, "Failed reading content URI: ${ex.message}")
                        }
                    } else {
                        Log.w(TAG, "Attachment file does not exist or is empty: ${msg.attachmentPath}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed encoding attachment: ${e.message}", e)
                }
            }
        }

        if (msg.isGroup || msg.receiverId == "COMMUNITY_GROUP_M_01") {
            // Gossip routing: send to ALL resolved peers!
            activePeers.forEach { (_, peer) ->
                sendJsonToPeer(peer.hostAddress, peer.port, payload)
            }
        } else {
            // Direct message: lookup targeted peer in map
            val peer = activePeers[msg.receiverId]
            if (peer != null) {
                sendJsonToPeer(peer.hostAddress, peer.port, payload)
            } else {
                Log.w(TAG, "No direct peer resolved for destination address ID: ${msg.receiverId}")
            }
        }
    }

    fun transmitPost(post: CommunityPost) {
        val payload = JSONObject().apply {
            put("type", "POST")
            put("postId", post.postId)
            put("authorUsername", post.authorUsername)
            put("authorId", post.authorId)
            put("messageContent", post.messageContent)
            put("postType", post.postType)
            put("timestamp", post.timestamp)
            put("battery", post.batteryLevel)
            put("location", post.locationName)
        }
        // Sync community post over the local peer network
        activePeers.forEach { (_, peer) ->
            sendJsonToPeer(peer.hostAddress, peer.port, payload)
        }
    }

    fun close() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {}

        try {
            if (discoveryListener != null) {
                nsdManager.stopServiceDiscovery(discoveryListener)
            }
        } catch (e: Exception) {}

        try {
            if (registrationListener != null) {
                nsdManager.unregisterService(registrationListener)
            }
        } catch (e: Exception) {}

        try {
            multicastLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "Multicast Lock Released")
                }
            }
        } catch (e: Exception) {}
    }
}
