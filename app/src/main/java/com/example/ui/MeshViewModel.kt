package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class MeshViewModel(application: Application) : AndroidViewModel(application) {
    val repository = MeshRepository.getInstance(application)

    // Current navigation state (Screens: onboarding, splash, nearby, personal_chat, group_chat, community, calls, profile, settings, emergency)
    private val _currentScreen = MutableStateFlow("splash")
    val currentScreen: StateFlow<String> = _currentScreen

    private val _isDarkTheme = MutableStateFlow(false) // default light theme
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
    }

    fun createMeshNetwork() {
        val context = getApplication<Application>()
        val wifiManager = context.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
        
        try {
            android.widget.Toast.makeText(context, "Hotspot starting... Please ensure Portable Hotspot is turned ON.", android.widget.Toast.LENGTH_LONG).show()
        } catch (e: Exception) {}

        try {
            if (wifiManager != null && !wifiManager.isWifiEnabled) {
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = true
            }
        } catch (e: Exception) {
            // ignore
        }

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val hotspotPermission =
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        android.Manifest.permission.NEARBY_WIFI_DEVICES
                    } else {
                        android.Manifest.permission.ACCESS_FINE_LOCATION
                    }
                val hasHotspotPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    hotspotPermission
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (hasHotspotPermission) {
                    wifiManager?.startLocalOnlyHotspot(object : android.net.wifi.WifiManager.LocalOnlyHotspotCallback() {
                        override fun onStarted(reservation: android.net.wifi.WifiManager.LocalOnlyHotspotReservation?) {
                            super.onStarted(reservation)
                            addLog("Local hotspot started successfully! Mesh SSID is active.")
                        }
                        override fun onFailed(reason: Int) {
                            super.onFailed(reason)
                            addLog("Local hotspot start failed (reason $reason). Please enable it manually.")
                        }
                    }, null)
                } else {
                    addLog("Nearby Wi-Fi permission is required to start a local hotspot.")
                }
            }
        } catch (e: SecurityException) {
            addLog("Local hotspot permission was denied.")
        } catch (e: Exception) {
            addLog("Local hotspot request failed: ${e.message}")
        }

        try {
            val intent = android.content.Intent().apply {
                action = "android.settings.TETHER_SETTINGS"
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            addLog("Please ensure Portable Wi-Fi Hotspot is enabled.")
        } catch (e: Exception) {
            try {
                val intent = android.content.Intent().apply {
                    action = android.provider.Settings.ACTION_WIRELESS_SETTINGS
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (ex: Exception) {
                // fallback
            }
        }

        addLog("Mesh Network creation started: Hotspot requested & WiFi state configured.")
    }

    fun joinMeshNetwork() {
        val context = getApplication<Application>()
        val wifiManager = context.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
        
        try {
            android.widget.Toast.makeText(context, "Wi-Fi starting... Please select and connect to LinkMesh Wi-Fi.", android.widget.Toast.LENGTH_LONG).show()
        } catch (e: Exception) {}

        try {
            if (wifiManager != null && !wifiManager.isWifiEnabled) {
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = true
                addLog("Enabling Wi-Fi...")
            }
        } catch (e: Exception) {
            // ignore
        }

        try {
            val intent = android.content.Intent().apply {
                action = android.provider.Settings.ACTION_WIFI_SETTINGS
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            addLog("Scanning for nearby Mesh Network links...")
        } catch (e: Exception) {
            // fallback
        }

        try {
            repository.getNetworkManager()?.forceBroadcastProfile()
        } catch (e: Exception) {
            // ignore
        }

        addLog("Searching for nearby Mesh networks. Wi-Fi activated.")
    }

    val profile: StateFlow<MyProfile?> = repository.getProfileFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val devices: StateFlow<List<MeshDevice>> = repository.getAllDevices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val communityPosts: StateFlow<List<CommunityPost>> = repository.getAllPosts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedDeviceMac = MutableStateFlow<String?>(null)
    val selectedDeviceMac: StateFlow<String?> = _selectedDeviceMac

    val activeDirectChat: StateFlow<List<ChatMessage>> = _selectedDeviceMac.flatMapLatest { mac ->
        val userMac = profile.value?.uniqueId ?: "You"
        if (mac != null) {
            repository.getChatHistory(userMac, mac)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeGroupChat: StateFlow<List<ChatMessage>> = repository.getGroupChatHistory("COMMUNITY_GROUP_M_01")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Conversations state
    val conversations: StateFlow<List<ChatMessage>> = profile.flatMapLatest { currentProfile ->
        if (currentProfile != null) {
            repository.getConversationsList(currentProfile.uniqueId)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Transmission and Dynamic states
    private val _transmissionProgressMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val transmissionProgressMap: StateFlow<Map<String, Int>> = _transmissionProgressMap

    private val _typingPeers = MutableStateFlow<Set<String>>(emptySet())
    val typingPeers: StateFlow<Set<String>> = _typingPeers

    private val _systemAlertLog = MutableStateFlow<List<String>>(listOf("System initialized: Mesh network operating via BLE & WiFi-Direct."))
    val systemAlertLog: StateFlow<List<String>> = _systemAlertLog

    // Call Simulation state
    private val _callState = MutableStateFlow<CallSession?>(null)
    val callState: StateFlow<CallSession?> = _callState

    private val _isRecordingVoice = MutableStateFlow(false)
    val isRecordingVoice: StateFlow<Boolean> = _isRecordingVoice

    private val _voiceRecordingDuration = MutableStateFlow(0)
    val voiceRecordingDuration: StateFlow<Int> = _voiceRecordingDuration

    private val _selectedCommentPostId = MutableStateFlow<String?>(null)
    val selectedCommentPostId: StateFlow<String?> = _selectedCommentPostId

    val postComments: StateFlow<List<PostComment>> = _selectedCommentPostId.flatMapLatest { postId ->
        if (postId != null) repository.getCommentsForPost(postId) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // File Sharing simulations state
    private val _currentFileJobState = MutableStateFlow<FileShareState?>(null)
    val currentFileJobState: StateFlow<FileShareState?> = _currentFileJobState

    init {
        viewModelScope.launch(Dispatchers.IO) {
            // Seed profile and mesh devices if needed
            ensureProfileSeeded()
            repository.insertMockDevicesIfEmpty()
            // Boot real-world socket & NSD discovering engine
            repository.initializeRealNetwork(application)

            // If there's an active background call, restore it immediately
            repository.activeBackgroundCall?.let { event ->
                withContext(Dispatchers.Main) {
                    _callState.value = CallSession(
                        peerName = event.callerName,
                        peerMac = event.callerMac,
                        isVideo = event.isVideo,
                        isIncoming = true,
                        status = "RINGING"
                    )
                    _currentScreen.value = "calls"
                }
            }
        }

        // Gather real-time network events from the simulation layer
        viewModelScope.launch {
            repository.networkEvents.collect { event ->
                when (event) {
                    is NetworkEvent.TransmissionProgress -> {
                        val current = _transmissionProgressMap.value.toMutableMap()
                        current[event.messageId] = event.progress
                        _transmissionProgressMap.value = current
                    }
                    is NetworkEvent.PeerTyping -> {
                        val current = _typingPeers.value.toMutableSet()
                        if (event.isTyping) {
                            current.add(event.peerMac)
                        } else {
                            current.remove(event.peerMac)
                        }
                        _typingPeers.value = current
                    }
                    is NetworkEvent.DirectCallReceived -> {
                        _callState.value = CallSession(
                            peerName = event.callerName,
                            peerMac = event.callerMac,
                            isVideo = event.isVideo,
                            isIncoming = true,
                            status = "RINGING"
                        )
                        _currentScreen.value = "calls"
                    }
                    is NetworkEvent.CallTransitionReceived -> {
                        if (event.status == "ACTIVE") {
                            val current = _callState.value
                            if (current != null && current.peerMac == event.peerMac) {
                                _callState.value = current.copy(status = "ACTIVE", activeSeconds = 0)
                                startActiveCallTimer()
                                startActiveCallAudioStreaming(event.peerMac)
                            }
                        } else if (event.status == "ENDED") {
                            callTimerJob?.cancel()
                            activeCallRecordingJob?.cancel()
                            try {
                                activeCallRecord?.stop()
                                activeCallRecord?.release()
                            } catch (e: Exception) {}
                            activeCallRecord = null
                            MeshSoundPlayer.stopCallAudioStream()
                            _callState.value = null
                            _currentScreen.value = "nearby"
                        }
                    }
                    is NetworkEvent.CallVideoFrameReceived -> {
                        val current = _callState.value
                        if (current != null && current.peerMac == event.peerMac) {
                            _callState.value = current.copy(peerVideoFrameBase64 = event.frameBase64)
                        }
                    }
                    is NetworkEvent.NetworkReconfigured -> {
                        addLog(event.description)
                    }
                }
            }
        }
    }

    private fun addLog(text: String) {
        val updated = _systemAlertLog.value.toMutableList()
        updated.add(0, "[LOG] $text")
        _systemAlertLog.value = updated
    }

    private suspend fun ensureProfileSeeded() {
        val p = repository.getProfile()
        if (p == null) {
            val personalId = "U-${UUID.randomUUID().toString().substring(0, 8).uppercase()}"
            repository.saveProfile(
                MyProfile(
                    username = "Mesh User",
                    uniqueId = personalId,
                    avatarColor = 0xFF1565C0.toInt(),
                    isRegistered = false,
                    rescueMode = false,
                    batteryTracking = true,
                    autoCleanupHours = 24,
                    appLanguage = "English"
                )
            )
        } else if (p.username.startsWith("User-") || p.username.isEmpty() || p.username == "nomi developer" || p.username == "Rescue-Node") {
            repository.saveProfile(p.copy(username = "Mesh User"))
        }
    }

    // Navigation and screen navigation functions
    fun navigateTo(screen: String) {
        _currentScreen.value = screen
    }

    fun selectDirectChat(peerMac: String?) {
        _selectedDeviceMac.value = peerMac
        if (peerMac != null) {
            _currentScreen.value = "personal_chat"
        } else {
            _currentScreen.value = "nearby"
        }
    }

    fun selectPostComments(postId: String?) {
        _selectedCommentPostId.value = postId
    }

    // Profile updates
    fun updateProfile(name: String, language: String, rescueMode: Boolean, colors: Int, photoUri: String? = null) {
        val current = profile.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveProfile(
                current.copy(
                    username = name,
                    appLanguage = language,
                    rescueMode = rescueMode,
                    avatarColor = colors,
                    isRegistered = true,
                    photoUri = photoUri ?: current.photoUri
                )
            )
            addLog("Profile updated: $name, Lang: $language. Photo selection updated.")
            repository.getNetworkManager()?.forceBroadcastProfile()
        }
    }

    // Device toggling (simulate devices dropping off or entering mesh coverage)
    fun toggleDeviceRange(mac: String, setOnline: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.toggleDeviceOnline(mac, setOnline)
        }
    }

    fun updateDevicePhoto(mac: String, photoUri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = repository.getAllDevices().first()
            val found = list.find { it.macAddress == mac }
            if (found != null) {
                val updated = found.copy(photoUri = photoUri)
                repository.insertOrUpdateMeshDevice(updated)
            }
        }
    }

    // One-to-one and Group Messaging
    fun sendSirenSignal(signal: String) {
        val peerMac = _selectedDeviceMac.value ?: return
        val peerName = devices.value.find { it.macAddress == peerMac }?.username ?: "Peer Device"
        viewModelScope.launch(Dispatchers.IO) {
            repository.sendMessage(
                receiverMac = peerMac,
                receiverName = peerName,
                textContent = signal,
                isGroup = false
            )
        }
    }

    fun sendDirectText(text: String) {
        val peerMac = _selectedDeviceMac.value ?: return
        val peerName = devices.value.find { it.macAddress == peerMac }?.username ?: "Peer Device"
        if (text.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.sendMessage(
                receiverMac = peerMac,
                receiverName = peerName,
                textContent = text,
                isGroup = false
            )
        }
    }

    fun sendGroupText(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.sendMessage(
                receiverMac = "COMMUNITY_GROUP_M_01",
                receiverName = "Emergency Mesh Group",
                textContent = text,
                isGroup = true
            )
        }
    }

    // Dynamic Recording Simulation
    private var voiceTimerJob: Job? = null
    private var mediaRecorder: android.media.MediaRecorder? = null
    private var currentRecordingFile: File? = null

    fun startRecordingVoice() {
        val app = getApplication<Application>()
        
        // Ensure microphone record permission is available before continuing
        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            app,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        
        if (!hasPermission) {
            android.widget.Toast.makeText(app, "Microphone permission is required to record voice notes", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val f = File(app.filesDir, "audio_record_${System.currentTimeMillis()}.3gp")
        currentRecordingFile = f

        try {
            val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                android.media.MediaRecorder(app)
            } else {
                @Suppress("DEPRECATION")
                android.media.MediaRecorder()
            }
            recorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.THREE_GPP)
            recorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AMR_NB)
            recorder.setOutputFile(f.absolutePath)
            recorder.prepare()
            recorder.start()
            mediaRecorder = recorder

            // Successfully started! Update the recording state and visual timer
            _isRecordingVoice.value = true
            _voiceRecordingDuration.value = 0

            voiceTimerJob = viewModelScope.launch {
                while (_isRecordingVoice.value) {
                    delay(1000)
                    _voiceRecordingDuration.value += 1
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(app, "Could not start audio recorder. Microphone might be in use.", android.widget.Toast.LENGTH_SHORT).show()
            currentRecordingFile = null
        }
    }

    fun stopRecordingVoice(cancelled: Boolean = false) {
        _isRecordingVoice.value = false
        voiceTimerJob?.cancel()
        voiceTimerJob = null
        
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaRecorder = null
        }

        val duration = _voiceRecordingDuration.value
        _voiceRecordingDuration.value = 0

        if (cancelled || duration == 0) {
            try {
                currentRecordingFile?.delete()
            } catch (e: Exception) {
                // ignore
            }
            currentRecordingFile = null
            return
        }

        // Save simulated voice attachment to active chat
        val peerMac = _selectedDeviceMac.value ?: return
        val peerName = devices.value.find { it.macAddress == peerMac }?.username ?: "Peer Device"
        
        val recordedPath = currentRecordingFile?.absolutePath ?: "audio_record_${System.currentTimeMillis()}.3gp"
        currentRecordingFile = null

        viewModelScope.launch(Dispatchers.IO) {
            repository.sendMessage(
                receiverMac = peerMac,
                receiverName = peerName,
                textContent = "🎤 Voice message (${duration}s)",
                attachmentType = "AUDIO",
                attachmentPath = recordedPath,
                attachmentSize = "${(duration * 4.5).toInt()} KB compressed",
                voiceDurationSec = duration
            )
            addLog("Sent offline compressed voice note over Bluetooth BLE.")
        }
    }

    // Image & File attachment sharing simulation
    fun shareMeshFile(fileName: String, sizeStr: String, fileType: String, localPath: String? = null) {
        val peerMac = _selectedDeviceMac.value ?: return
        val peerName = devices.value.find { it.macAddress == peerMac }?.username ?: "Peer Device"

        val jobKey = UUID.randomUUID().toString()
        _currentFileJobState.value = FileShareState(
            jobId = jobKey,
            fileName = fileName,
            fileSize = sizeStr,
            fileType = fileType,
            progress = 0,
            status = "SENDING",
            peerMac = peerMac,
            peerName = peerName,
            localPath = localPath
        )

        val resolvedPath = localPath ?: "mesh_received_$fileName"

        // Increment file download progress task (simulated Bluetooth classic/Wi-Fi Direct high throughput pipe)
        viewModelScope.launch {
            var progress = 0
            while (progress < 100) {
                val state = _currentFileJobState.value ?: break
                if (state.status == "PAUSED" || state.status == "INTERRUPTED") {
                    break
                }
                delay(200)
                progress += 25
                _currentFileJobState.value = state.copy(progress = progress)
            }

            if (progress >= 100) {
                _currentFileJobState.value = _currentFileJobState.value?.copy(status = "COMPLETED", progress = 100)
                repository.sendMessage(
                    receiverMac = peerMac,
                    receiverName = peerName,
                    textContent = " Shared: $fileName ($sizeStr)",
                    attachmentType = fileType,
                    attachmentPath = resolvedPath,
                    attachmentSize = sizeStr
                )
                addLog("File direct transfer finished: $fileName ($sizeStr)")
            }
        }
    }

    fun interruptFileShare() {
        val current = _currentFileJobState.value ?: return
        _currentFileJobState.value = current.copy(status = "INTERRUPTED")
        addLog("Mesh file transfer interrupted (Simulated range drop). Ready to resume.")
    }

    fun resumeFileShare() {
        val current = _currentFileJobState.value ?: return
        _currentFileJobState.value = current.copy(status = "RESUMING")
        addLog("Resuming Wi-Fi Direct file download block from index offset.")
        
        viewModelScope.launch {
            delay(400)
            var progress = current.progress
            while (progress < 100) {
                delay(200)
                progress += 25
                if (progress > 100) progress = 100
                _currentFileJobState.value = _currentFileJobState.value?.copy(status = "RESUMING", progress = progress)
            }
            _currentFileJobState.value = _currentFileJobState.value?.copy(status = "COMPLETED", progress = 100)
            repository.sendMessage(
                receiverMac = current.peerMac,
                receiverName = current.peerName,
                textContent = " Shared: ${current.fileName} (${current.fileSize})",
                attachmentType = current.fileType,
                attachmentPath = current.localPath ?: "mesh_received_${current.fileName}",
                attachmentSize = current.fileSize
            )
            addLog("Resumed file transfer succeeded: ${current.fileName}")
        }
    }

    // Call Simulation functions
    fun startCall(peerMac: String, video: Boolean) {
        val peer = devices.value.find { it.macAddress == peerMac } ?: return
        _callState.value = CallSession(
            peerName = peer.username,
            peerMac = peerMac,
            isVideo = video,
            isIncoming = false,
            status = "DIALING"
        )
        _currentScreen.value = "calls"

        viewModelScope.launch(Dispatchers.IO) {
            val myProfile = repository.getProfile()
            if (myProfile != null) {
                val payload = org.json.JSONObject().apply {
                    put("type", "CALL_INITIATE")
                    put("callerMac", myProfile.uniqueId)
                    put("callerName", myProfile.username)
                    put("isVideo", video)
                }
                repository.getNetworkManager()?.let { net ->
                    net.activePeers[peerMac]?.let { resolved ->
                        net.sendJsonToPeer(resolved.hostAddress, resolved.port, payload)
                    }
                }
            }
        }
    }

    fun setIncomingCallSession(peerMac: String, peerName: String, isVideo: Boolean) {
        _callState.value = CallSession(
            peerName = peerName,
            peerMac = peerMac,
            isVideo = isVideo,
            isIncoming = true,
            status = "RINGING"
        )
    }

    fun acceptIncomingCall() {
        val current = _callState.value ?: return
        _callState.value = current.copy(status = "ACTIVE", activeSeconds = 0)
        startActiveCallTimer()
        startActiveCallAudioStreaming(current.peerMac)

        viewModelScope.launch(Dispatchers.IO) {
            val myProfile = repository.getProfile()
            if (myProfile != null) {
                val payload = org.json.JSONObject().apply {
                    put("type", "CALL_ACCEPT")
                    put("senderMac", myProfile.uniqueId)
                }
                repository.getNetworkManager()?.let { net ->
                    net.activePeers[current.peerMac]?.let { resolved ->
                        net.sendJsonToPeer(resolved.hostAddress, resolved.port, payload)
                    }
                }
            }
        }
    }

    fun rejectOrEndCall() {
        val current = _callState.value
        callTimerJob?.cancel()
        activeCallRecordingJob?.cancel()
        try {
            activeCallRecord?.stop()
            activeCallRecord?.release()
        } catch (e: Exception) {}
        activeCallRecord = null
        MeshSoundPlayer.stopCallAudioStream()
        
        _callState.value = null
        _currentScreen.value = "nearby"
        addLog("Offline peer call session terminated safely.")

        if (current != null) {
            val durationMin = current.activeSeconds / 60
            val durationSec = current.activeSeconds % 60
            val durationStr = if (current.status == "ACTIVE") {
                String.format("%02d:%02d", durationMin, durationSec)
            } else {
                "--"
            }
            val statusStr = if (current.isIncoming) {
                if (current.status == "ACTIVE") "Incoming Call" else "Missed Voice Call"
            } else {
                "Outgoing Call"
            }
            addCallHistoryLog(
                name = current.peerName,
                status = statusStr,
                duration = durationStr,
                isVideo = current.isVideo,
                isMissed = (current.isIncoming && current.status != "ACTIVE")
            )

            viewModelScope.launch(Dispatchers.IO) {
                val myProfile = repository.getProfile()
                if (myProfile != null) {
                    val payload = org.json.JSONObject().apply {
                        put("type", "CALL_END")
                        put("senderMac", myProfile.uniqueId)
                    }
                    repository.getNetworkManager()?.let { net ->
                        net.activePeers[current.peerMac]?.let { resolved ->
                            net.sendJsonToPeer(resolved.hostAddress, resolved.port, payload)
                        }
                    }
                }
            }
        }
    }

    private var activeCallRecord: android.media.AudioRecord? = null
    private var activeCallRecordingJob: Job? = null

    private fun startActiveCallAudioStreaming(peerMac: String) {
        activeCallRecordingJob?.cancel()
        activeCallRecordingJob = viewModelScope.launch(Dispatchers.IO) {
            val sampleRate = 8000
            val channelConfig = android.media.AudioFormat.CHANNEL_IN_MONO
            val audioFormat = android.media.AudioFormat.ENCODING_PCM_16BIT
            val minBufSize = android.media.AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = maxOf(minBufSize, 1024)

            try {
                @android.annotation.SuppressLint("MissingPermission")
                val record = android.media.AudioRecord(
                    android.media.MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
                if (record.state != android.media.AudioRecord.STATE_INITIALIZED) {
                    addLog("Mic record initialization failed.")
                    return@launch
                }
                activeCallRecord = record
                record.startRecording()

                val buffer = ByteArray(512) // Smaller buffer sizes reduce latency!
                val net = repository.getNetworkManager()

                while (activeCallRecordingJob?.isActive == true) {
                    val currentCall = _callState.value
                    if (currentCall == null || currentCall.status != "ACTIVE") {
                        break
                    }
                    if (currentCall.isMuted) {
                        delay(200)
                        continue
                    }

                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val base64 = android.util.Base64.encodeToString(buffer, 0, read, android.util.Base64.DEFAULT or android.util.Base64.NO_WRAP)
                        val payload = org.json.JSONObject().apply {
                            put("type", "CALL_AUDIO")
                            put("senderMac", repository.getProfile()?.uniqueId ?: "")
                            put("audioData", base64)
                        }
                        
                        net?.let { manager ->
                            manager.activePeers[peerMac]?.let { resolved ->
                                manager.sendJsonToPeer(resolved.hostAddress, resolved.port, payload)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    activeCallRecord?.stop()
                    activeCallRecord?.release()
                } catch (e: Exception) {}
                activeCallRecord = null
            }
        }
    }

    private var callTimerJob: Job? = null
    private fun startActiveCallTimer() {
        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch {
            addLog("P2P Audio/Video connection settled. Streaming local voice packets.")
            while (true) {
                delay(1000)
                val current = _callState.value ?: break
                _callState.value = current.copy(activeSeconds = current.activeSeconds + 1)
            }
        }
    }

    fun toggleCallMute() {
        val current = _callState.value ?: return
        _callState.value = current.copy(isMuted = !current.isMuted)
    }

    fun toggleCallCamera() {
        val current = _callState.value ?: return
        _callState.value = current.copy(cameraEnabled = !current.cameraEnabled)
    }

    fun sendLocalVideoFrame(peerMac: String, base64Frame: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val payload = org.json.JSONObject().apply {
                put("type", "CALL_VIDEO_FRAME")
                put("senderMac", repository.getProfile()?.uniqueId ?: "")
                put("frameData", base64Frame)
            }
            repository.getNetworkManager()?.let { net ->
                net.activePeers[peerMac]?.let { resolved ->
                    net.sendJsonToPeer(resolved.hostAddress, resolved.port, payload)
                }
            }
        }
    }

    // Community Broadcast and comments
    fun addCommunityBroad(content: String, type: String, locationStr: String) {
        if (content.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.createPost(content, type, locationStr)
            addLog("Broadcasting alert post locally. Mesh gossip routing activated!")
        }
    }

    fun upvotePostFlow(post: CommunityPost) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.upvotePost(post)
        }
    }

    fun addCommentToPost(postId: String, text: String) {
        if (text.isBlank()) return
        val currentUsername = profile.value?.username ?: "Anonymous User"
        viewModelScope.launch(Dispatchers.IO) {
            repository.addComment(postId, currentUsername, text)
        }
    }

    // Emergency Center Actions
    fun triggerSOSAlert(latitude: Double, longitude: Double) {
        val prof = profile.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            // Play continuous siren sound
            MeshSoundPlayer.startSiren()

            // Mark SOS as activated in profile
            val updatedProfile = prof.copy(rescueMode = true)
            repository.saveProfile(updatedProfile)

            // Dynamic broad creation
            val currentBattery = repository.getNetworkManager()?.getBatteryLevel() ?: 85
            val emergencyContent = "[🚨 SOS EMERGENCY ALERT] Please help! Beacon active. User: ${prof.username}. Battery: $currentBattery%. Coordinates: $latitude, $longitude. Operating in disaster-relief mesh node mode."
            repository.createPost(
                content = emergencyContent,
                postType = "ALERT",
                location = "GPS: $latitude, $longitude"
            )

            // Send standard SOS group messages to alerts channel
            repository.sendMessage(
                receiverMac = "COMMUNITY_GROUP_M_01",
                receiverName = "Emergency Mesh Group",
                textContent = "🚨 SOS PANIC BUTTON PINNED! Current GPS: $latitude, $longitude. Requesting help, battery solid at $currentBattery%.",
                isGroup = true
            )

            // Trigger local logs
            addLog("🚨 panic beacon broadcasted! Multi-hop gossiping on nearby devices started.")
        }
    }

    fun turnOffSOS() {
        val prof = profile.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            // Stop sound
            MeshSoundPlayer.stopSound()

            repository.saveProfile(prof.copy(rescueMode = false))
            addLog("SOS deactivated. Returning node state back to low-power classic BLE.")
        }
    }

    fun wipeLocalNodeData() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAllData()
            _currentScreen.value = "onboarding"
            addLog("Node cleared. Database reset complete.")
        }
    }

    // Missing Person alert generator
    fun postMissingPersonAlert(name: String, age: String, lastSeenLocation: String, clothingDetails: String) {
        val alertText = "🚨 [MISSING PERSON PILOT] Help locate: $name ($age years old). Last seen near $lastSeenLocation wearing $clothingDetails. Missing person data is auto-synced across local node caches. Reach out if spotted."
        addCommunityBroad(
            content = alertText,
            type = "ALERT",
            locationStr = lastSeenLocation
        )
    }

    // Interactive custom state extensions for groups, calls history, files, and recent activity
    private val _groups = MutableStateFlow<List<MeshGroup>>(emptyList())
    val groups: StateFlow<List<MeshGroup>> = _groups.asStateFlow()

    fun createGroup(name: String, description: String, membersCount: Int = 1) {
        val current = _groups.value.toMutableList()
        val newId = "g_${UUID.randomUUID().toString().substring(0, 4)}"
        current.add(0, MeshGroup(newId, name, membersCount, description, isCustom = true))
        _groups.value = current
        addLog("Group Created: $name")
        addRecentActivity(name, "Created Group", "Just Now", "group")
    }

    val sharedFiles: StateFlow<List<SharedFile>> = repository.getAllMessagesWithAttachments()
        .map { messages ->
            messages.map { msg ->
                val name = msg.attachmentPath?.substringAfterLast('/') ?: "File"
                val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                val timeStr = sdf.format(msg.timestamp)
                SharedFile(
                    id = msg.messageId,
                    name = name,
                    size = msg.attachmentSize ?: "Unknown size",
                    senderOrReceiver = if (msg.isIncoming) msg.senderName else "You",
                    isIncoming = msg.isIncoming,
                    timestamp = timeStr,
                    fileType = when (msg.attachmentType) {
                        "IMAGE" -> "jpg"
                        "AUDIO" -> "3gp"
                        else -> "bin"
                    }
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun simulateShareFile(name: String, size: String, recipient: String, fileType: String, isIncoming: Boolean) {
        // Obsolete static file sharing simulator
    }

    private val _blockedPeers = MutableStateFlow<Set<String>>(emptySet())
    val blockedPeers: StateFlow<Set<String>> = _blockedPeers.asStateFlow()

    fun toggleBlockPeer(macAddress: String) {
        val current = _blockedPeers.value.toMutableSet()
        if (current.contains(macAddress)) {
            current.remove(macAddress)
        } else {
            current.add(macAddress)
        }
        _blockedPeers.value = current
    }

    fun isPeerBlocked(macAddress: String): Boolean {
        return _blockedPeers.value.contains(macAddress)
    }

    fun clearChat(peerMac: String) {
        val myMac = profile.value?.uniqueId ?: "You"
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearChat(myMac, peerMac)
        }
    }

    fun deleteMessage(message: ChatMessage) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteMessage(message)
        }
    }

    private val _callHistory = MutableStateFlow<List<CallHistoryLog>>(emptyList())
    val callHistory: StateFlow<List<CallHistoryLog>> = _callHistory.asStateFlow()

    fun addCallHistoryLog(name: String, status: String, duration: String, isVideo: Boolean, isMissed: Boolean) {
        val current = _callHistory.value.toMutableList()
        val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
        val timeStr = sdf.format(System.currentTimeMillis())
        current.add(0, CallHistoryLog(
            id = UUID.randomUUID().toString().substring(0, 4),
            name = name,
            status = status,
            timestamp = timeStr,
            duration = duration,
            isVideo = isVideo,
            isMissed = isMissed
        ))
        _callHistory.value = current
    }

    fun simulateAddCallLog(name: String, isIncoming: Boolean, isVideo: Boolean, isMissed: Boolean, duration: String) {
        val current = _callHistory.value.toMutableList()
        val statusStr = when {
            isMissed -> "Missed Voice Call"
            isVideo -> if (isIncoming) "Incoming Video Call" else "Outgoing Video Call"
            else -> if (isIncoming) "Incoming Voice Call" else "Outgoing Voice Call"
        }
        val newId = "c_${UUID.randomUUID().toString().substring(0, 4)}"
        current.add(0, CallHistoryLog(newId, name, statusStr, "Just Now", duration, isVideo, isMissed))
        _callHistory.value = current
        addRecentActivity(name, if (isVideo) "Video Call" else "Voice Call", "Just Now", "phone")
    }

    private val _recentActivities = MutableStateFlow<List<RecentActivity>>(emptyList())
    val recentActivities: StateFlow<List<RecentActivity>> = _recentActivities.asStateFlow()

    fun addRecentActivity(name: String, type: String, time: String, iconType: String) {
        val current = _recentActivities.value.toMutableList()
        val newId = "r_${UUID.randomUUID().toString().substring(0, 4)}"
        current.add(0, RecentActivity(newId, name, type, time, iconType))
        _recentActivities.value = current
    }

    // Multi-Language Translations Dictionary Map helper
    fun getTranslation(key: String, currentLang: String): String {
        return languageTranslations[currentLang]?.get(key) ?: key
    }

    private val languageTranslations = mapOf(
        "English" to mapOf(
            "nearby_tab" to "Nearby Nodes",
            "chats_tab" to "Personal Chats",
            "groups_tab" to "Emergency Groups",
            "feed_tab" to "Community Feed",
            "emergency_tab" to "Emergency Center",
            "settings_tab" to "Settings",
            "sos_btn" to "TAP SOS PANIC ALERT",
            "sos_active" to "SOS EMBEDDED ACCELERATION ACTIVE",
            "missing_alert" to "Missing Person Alert",
            "battery_label" to "Node Battery Power",
            "search_nearby" to "Discover Devices",
            "registered_status" to "Local Mesh Badge"
        ),
        "Urdu" to mapOf(
            "nearby_tab" to "قریبی آلات",
            "chats_tab" to "ذاتی پیغامات",
            "groups_tab" to "امدادی گروپ",
            "feed_tab" to "عوامی فیڈ",
            "emergency_tab" to "ایمرجنسی سینٹر",
            "settings_tab" to "ترتیبات",
            "sos_btn" to "فوری ایمرجنسی الرٹ دبائیں",
            "sos_active" to "ایمرجنسی الرٹ چالو کر دیا گیا ہے۔",
            "missing_alert" to "گمشدہ شخص کا الرٹ",
            "battery_label" to "آلہ کی بیٹری پاور",
            "search_nearby" to "آلات دریافت کریں",
            "registered_status" to "مقامی میش بیج"
        ),
        "Arabic" to mapOf(
            "nearby_tab" to "الأجهزة القريبة",
            "chats_tab" to "الدردشات الخاصة",
            "groups_tab" to "مجموعات الطوارئ",
            "feed_tab" to "الموجز المحلي",
            "emergency_tab" to "مركز الطوارئ",
            "settings_tab" to "الإعدادات",
            "sos_btn" to "اضغط على تنبيه الطوارئ SOS",
            "sos_active" to "تنبيه الطوارئ نشط الآن",
            "missing_alert" to "بلاغ عن شخص مفقود",
            "battery_label" to "طاقة بطارية العقدة",
            "search_nearby" to "اكتشاف الأجهزة",
            "registered_status" to "شعار الشبكة المحلية"
        )
    )
}

data class CallSession(
    val peerName: String,
    val peerMac: String,
    val isVideo: Boolean,
    val isIncoming: Boolean,
    val status: String, // DIALING, RINGING, ACTIVE
    val activeSeconds: Int = 0,
    val isMuted: Boolean = false,
    val cameraEnabled: Boolean = true,
    val peerVideoFrameBase64: String? = null
)

data class FileShareState(
    val jobId: String,
    val fileName: String,
    val fileSize: String,
    val fileType: String,
    val progress: Int,
    val status: String, // SENDING, PAUSED, INTERRUPTED, RESUMING, COMPLETED
    val peerMac: String,
    val peerName: String,
    val localPath: String? = null
)

data class MeshGroup(
    val id: String,
    val name: String,
    val membersCount: Int,
    val description: String,
    val isCustom: Boolean = false
)

data class SharedFile(
    val id: String,
    val name: String,
    val size: String,
    val senderOrReceiver: String,
    val isIncoming: Boolean,
    val timestamp: String,
    val fileType: String // "pdf", "jpg", "mp4", "docx", "zip"
)

data class CallHistoryLog(
    val id: String,
    val name: String,
    val status: String,
    val timestamp: String,
    val duration: String,
    val isVideo: Boolean,
    val isMissed: Boolean
)

data class RecentActivity(
    val id: String,
    val name: String,
    val type: String,
    val time: String,
    val iconType: String
)
