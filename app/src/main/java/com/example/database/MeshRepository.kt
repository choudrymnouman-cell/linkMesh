package com.example.database

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class MeshRepository private constructor(
    private val db: MeshDatabase,
    private val appScope: CoroutineScope
) {
    companion object {
        @Volatile
        private var instance: MeshRepository? = null
        
        // App-wide supervisor scope that stays active when the app is in the background or closed
        private val globalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun getInstance(context: Context): MeshRepository {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val db = MeshDatabase.getDatabase(context.applicationContext)
                    val repo = MeshRepository(db, globalScope)
                    repo.initializeRealNetwork(context.applicationContext)
                    instance = repo
                    repo
                }
            }
        }
    }
    private val profileDao = db.profileDao()
    private val deviceDao = db.deviceDao()
    private val messageDao = db.messageDao()
    private val communityDao = db.communityDao()

    private var networkManager: MeshLocalNetworkManager? = null

    fun initializeRealNetwork(context: Context) {
        if (networkManager == null) {
            networkManager = MeshLocalNetworkManager(context, this, appScope)
        }
    }

    fun getNetworkManager(): MeshLocalNetworkManager? = networkManager

    suspend fun insertOrUpdateMeshDevice(device: MeshDevice) {
        deviceDao.insertOrUpdateDevice(device)
    }

    // Flow for real-time notification events in UI (e.g. call overlays, transmission updates)
    private val _networkEvents = MutableSharedFlow<NetworkEvent>(extraBufferCapacity = 10)
    val networkEvents: SharedFlow<NetworkEvent> = _networkEvents

    // 1. Profile Data
    fun getProfileFlow(): Flow<MyProfile?> = profileDao.getProfileFlow()
    suspend fun getProfile(): MyProfile? = profileDao.getProfile()
    suspend fun saveProfile(profile: MyProfile) = profileDao.insertProfile(profile)

    // 2. Mesh Device Operations
    fun getAllDevices(): Flow<List<MeshDevice>> = deviceDao.getAllDevices()
    
    suspend fun toggleDeviceOnline(mac: String, setOnline: Boolean) {
        val list = deviceDao.getAllDevices().first()
        val device = list.find { it.macAddress == mac } ?: return
        val updated = device.copy(
            isOnline = setOnline,
            lastSeenTimestamp = System.currentTimeMillis()
        )
        deviceDao.insertOrUpdateDevice(updated)
        
        // Push an event about the route update
        triggerNetworkEvent(NetworkEvent.NetworkReconfigured("Network link changed: ${device.username} is ${if (setOnline) "ONLINE" else "OFFLINE"}"))
    }

    suspend fun toggleSOSForDevice(mac: String, sosActive: Boolean) {
        val list = deviceDao.getAllDevices().first()
        val device = list.find { it.macAddress == mac } ?: return
        val updated = device.copy(isSOSActive = sosActive)
        deviceDao.insertOrUpdateDevice(updated)
    }

    suspend fun insertMockDevicesIfEmpty() {
        // Clear mock/default data specifically to ensure there is no default data in the app as requested
        listOf(
            "E1:12:34:56:AB:01",
            "E1:12:34:56:AB:02",
            "E1:12:34:56:AB:03",
            "E1:12:34:56:AB:04"
        ).forEach { deviceDao.deleteDevice(it) }

        communityDao.deleteMockPosts()
        communityDao.deleteMockComments()
    }

    // 3. One-to-One and Group Messaging with Mash Routing Optimization
    fun getChatHistory(userId: String, otherId: String): Flow<List<ChatMessage>> = messageDao.getChatHistory(userId, otherId)
    fun getGroupChatHistory(groupId: String): Flow<List<ChatMessage>> = messageDao.getGroupChatHistory(groupId)
    fun getConversationsList(userId: String): Flow<List<ChatMessage>> = messageDao.getConversationsList(userId)

    suspend fun clearChat(userId: String, otherId: String) {
        messageDao.clearChat(userId, otherId)
    }

    suspend fun deleteMessage(message: ChatMessage) {
        messageDao.deleteMessage(message)
    }

    fun getAllMessagesWithAttachments(): Flow<List<ChatMessage>> = messageDao.getAllMessagesWithAttachments()

    suspend fun saveMessageDirect(msg: ChatMessage) {
        messageDao.insertMessage(msg)
    }

    // Calculates mesh hopping route dynamically based on active node state
    suspend fun calculateRoute(receiverMac: String): List<String> {
        val userMac = getProfile()?.uniqueId ?: "You"
        val activeNodes = deviceDao.getActiveDevices()
        
        // Find targeted device
        val target = activeNodes.find { it.macAddress == receiverMac }
        
        if (target == null) {
            return listOf("Destination Offline (unreachable)")
        }

        // Direct check: high signal strength or target directly in range
        if (target.signalStrength > 70) {
            return listOf("Direct WiFi-P2P link")
        }

        // Let's model a hop route
        // If CAPTAIN (AB:01) and SARAH (AB:02) are online, we can hop!
        val farhan = activeNodes.find { it.macAddress == "E1:12:34:56:AB:01" }
        val sarah = activeNodes.find { it.macAddress == "E1:12:34:56:AB:02" }
        val amna = activeNodes.find { it.macAddress == "E1:12:34:56:AB:04" }

        val hops = mutableListOf<String>()

        when (receiverMac) {
            "E1:12:34:56:AB:01" -> hops.add("Direct BLE/WiFi Link") // Farhan
            "E1:12:34:56:AB:02" -> { // Sarah
                if (farhan != null && farhan.isOnline) {
                    hops.addAll(listOf("Ref-Hub 1 (${farhan.username})", "Sarah"))
                } else {
                    hops.add("Direct BLE (Low signal strength)")
                }
            }
            "E1:12:34:56:AB:03" -> { // Dr. Tariq (unreachable directly, must hop!)
                // Check routing paths (self-healing!)
                if (sarah != null && sarah.isOnline && farhan != null && farhan.isOnline) {
                    hops.addAll(listOf("Ref-Hub 1 (${farhan.username})", "Relay 2 (${sarah.username})", "Dr. Tariq"))
                } else if (amna != null && amna.isOnline) {
                    hops.addAll(listOf("Direct Link (${amna.username}) [Self-Healed Route]", "Dr. Tariq"))
                } else if (sarah != null && sarah.isOnline) {
                    hops.addAll(listOf("Relay (${sarah.username}) [Auto Route Optimizer]", "Dr. Tariq"))
                } else if (farhan != null && farhan.isOnline) {
                    hops.addAll(listOf("Relay (${farhan.username}) [Auto Route Optimizer]", "Dr. Tariq"))
                } else {
                    hops.add("No Relay Path found! Scanning for passive BLE beacon...")
                }
            }
            "E1:12:34:56:AB:04" -> hops.add("Direct BLE (Amna)")
            else -> hops.add("Direct Connection")
        }
        return hops
    }

    suspend fun sendMessage(
        receiverMac: String,
        receiverName: String,
        textContent: String,
        isGroup: Boolean = false,
        attachmentType: String = "NONE",
        attachmentPath: String? = null,
        attachmentSize: String? = null,
        voiceDurationSec: Int = 0
    ) : ChatMessage {
        val profile = getProfile() ?: MyProfile(username = "Self-User", uniqueId = "AA:BB:CC:DD:EE:00")
        val hops = calculateRoute(receiverMac)
        val hopsString = if (isGroup) "Group Broadcast" else hops.joinToString(" ➔ ")

        val msgId = UUID.randomUUID().toString()
        val newMessage = ChatMessage(
            messageId = msgId,
            senderId = profile.uniqueId,
            senderName = profile.username,
            receiverId = receiverMac,
            isGroup = isGroup,
            textContent = textContent,
            attachmentType = attachmentType,
            attachmentPath = attachmentPath,
            attachmentSize = attachmentSize,
            timestamp = System.currentTimeMillis(),
            status = "SENT",
            hopsList = hopsString,
            isIncoming = false,
            voiceDurationSec = voiceDurationSec
        )

        saveMessageDirect(newMessage)

        // Transmit over real local connection
        networkManager?.transmitMessage(newMessage)

        // Simulate async propagation over mesh protocol
        appScope.launch(Dispatchers.IO) {
            triggerNetworkEvent(NetworkEvent.TransmissionProgress(msgId, 10))
            delay(500)
            triggerNetworkEvent(NetworkEvent.TransmissionProgress(msgId, 50))
            delay(500)
            
            // Mark DELIVERED
            messageDao.updateMessageStatus(msgId, "DELIVERED")
            triggerNetworkEvent(NetworkEvent.TransmissionProgress(msgId, 100))
            
            delay(1000)
            // Mark READ
            messageDao.updateMessageStatus(msgId, "READ")

            // Simulate Peer Automatic AI-comp compression or direct answers
            handleMockReplies(newMessage, targetMac = receiverMac, targetName = receiverName)
        }

        return newMessage
    }

    // Handles smart offline AI bots / replies simulating real other endpoints
    private suspend fun handleMockReplies(sentMsg: ChatMessage, targetMac: String, targetName: String) {
        // Only trigger mock replies if the target ID is one of the mock nodes
        if (!targetMac.startsWith("E1:12:34:56:AB:")) {
            // Real physical peer connected! No bot simulation.
            return
        }
        // If there's an active peer, they can answer
        if (sentMsg.isGroup) {
            val replyText = when {
                sentMsg.textContent.contains("help", ignoreCase = true) -> 
                    "Captain Farhan here. Regional group is listening. State your exact supply or medical coordinates."
                else -> "Understood. Received by broadcast mesh."
            }
            delay(2000)
            val incoming = ChatMessage(
                messageId = UUID.randomUUID().toString(),
                senderId = "E1:12:34:56:AB:01",
                senderName = "Captain Farhan",
                receiverId = sentMsg.receiverId,
                isGroup = true,
                textContent = replyText,
                timestamp = System.currentTimeMillis(),
                status = "READ",
                hopsList = "Group Relay",
                isIncoming = true
            )
            saveMessageDirect(incoming)
        } else {
            // Personal reply
            val replyText = when (targetMac) {
                "E1:12:34:56:AB:01" -> { // Farhan
                    when {
                        sentMsg.textContent.contains("route", ignoreCase = true) || sentMsg.textContent.contains("road", ignoreCase = true) -> 
                            "Yes, Route G-10 is totally blocked from landslide. Safe corridor G-9 is operational."
                        sentMsg.textContent.contains("coordinate", ignoreCase = true) || sentMsg.textContent.contains("coordinate", ignoreCase = true) -> 
                            "I see your GPS coordinates! Standard grid search activated."
                        else -> "Captain Farhan here. Mesh signal locked. Running static telemetry logs in sector 4. Keep your battery saved."
                    }
                }
                "E1:12:34:56:AB:02" -> { // Sarah
                    when {
                        sentMsg.textContent.contains("camp", ignoreCase = true) || sentMsg.textContent.contains("play", ignoreCase = true) -> 
                            "That's right, we are near G-8 markaz park. Water and fever tablets are available."
                        sentMsg.textContent.contains("help", ignoreCase = true) || sentMsg.textContent.contains("injured", ignoreCase = true) -> 
                            "Copy that. I am instructing the nearest volunteer team with a medical crate to head to your direct link sector."
                        else -> "Paramedic coordination station active. Copy that. Checking emergency inventory."
                    }
                }
                "E1:12:34:56:AB:03" -> { // Dr Tariq
                    when {
                        sentMsg.textContent.contains("doctor", ignoreCase = true) || sentMsg.textContent.contains("hurt", ignoreCase = true) -> 
                            "Keep calm. Apply direct pressure to any bleeding. Keep limbs elevated. Sarah's dispatcher team is on the way."
                        else -> "Doctor Tariq here. Route hopping takes slightly higher latency, but my grid status remains live. Stay safe."
                    }
                }
                "E1:12:34:56:AB:04" -> {
                    "Understood. Mesh connection looks steady on my civil defense terminal."
                }
                else -> {
                    "Broadcasting confirmation. Node ping successful."
                }
            }
            delay(2200)
            
            // Simulating a typing indicator!
            triggerNetworkEvent(NetworkEvent.PeerTyping(targetMac, isTyping = true))
            delay(1500)
            triggerNetworkEvent(NetworkEvent.PeerTyping(targetMac, isTyping = false))

            val incomingMsg = ChatMessage(
                messageId = UUID.randomUUID().toString(),
                senderId = targetMac,
                senderName = targetName,
                receiverId = getProfile()?.uniqueId ?: "You",
                isGroup = false,
                textContent = replyText,
                timestamp = System.currentTimeMillis(),
                status = "READ",
                hopsList = calculateRoute(targetMac).reversed().joinToString(" ➔ "),
                isIncoming = true
            )
            saveMessageDirect(incomingMsg)
        }
    }

    // 4. Community Announcement Board (Offline Feed)
    fun getAllPosts(): Flow<List<CommunityPost>> = communityDao.getAllPosts()
    fun getCommentsForPost(postId: String): Flow<List<PostComment>> = communityDao.getCommentsForPost(postId)

    suspend fun insertPostDirect(post: CommunityPost) {
        communityDao.insertPost(post)
    }

    suspend fun createPost(content: String, postType: String, location: String) {
        val user = getProfile() ?: MyProfile(username = "You (Offline Nom)")
        val realBattery = networkManager?.getBatteryLevel() ?: 85
        val post = CommunityPost(
            postId = UUID.randomUUID().toString(),
            authorUsername = user.username,
            authorId = user.uniqueId,
            messageContent = content,
            postType = postType,
            timestamp = System.currentTimeMillis(),
            batteryLevel = realBattery,
            locationName = location,
            upvotesCount = 0,
            hasUpvoted = false,
            commentCount = 0
        )
        communityDao.insertPost(post)

        // Transmit post over real local connection
        networkManager?.transmitPost(post)
    }

    suspend fun upvotePost(post: CommunityPost) {
        val updatedVotes = if (post.hasUpvoted) post.upvotesCount - 1 else post.upvotesCount + 1
        val updatedHasUpvoted = !post.hasUpvoted
        communityDao.updatePostUpvote(post.postId, updatedVotes, updatedHasUpvoted)
    }

    suspend fun addComment(postId: String, commenter: String, commentContent: String) {
        val comment = PostComment(
            postId = postId,
            commenterName = commenter,
            commentContent = commentContent,
            timestamp = System.currentTimeMillis()
        )
        communityDao.insertComment(comment)
    }

    var activeBackgroundCall: NetworkEvent.DirectCallReceived? = null

    // Trigger visual/logical calls sim, transmission logs
    suspend fun triggerNetworkEvent(event: NetworkEvent) {
        if (event is NetworkEvent.DirectCallReceived) {
            activeBackgroundCall = event
        } else if (event is NetworkEvent.CallTransitionReceived && event.status == "ENDED") {
            activeBackgroundCall = null
        }
        _networkEvents.emit(event)
    }

    suspend fun clearAllData() {
        db.withTransaction {
            deviceDao.deleteAllDevices()
            db.messageDao().cleanOldMessages(System.currentTimeMillis() + 1000 * 3600) // delete all
        }
    }
}

// Sealed class representing network notifications or low-latency simulation logs
sealed class NetworkEvent {
    data class TransmissionProgress(val messageId: String, val progress: Int) : NetworkEvent()
    data class PeerTyping(val peerMac: String, val isTyping: Boolean) : NetworkEvent()
    data class DirectCallReceived(val callerName: String, val callerMac: String, val isVideo: Boolean) : NetworkEvent()
    data class NetworkReconfigured(val description: String) : NetworkEvent()
    data class CallTransitionReceived(val peerMac: String, val status: String) : NetworkEvent()
    data class CallVideoFrameReceived(val peerMac: String, val frameBase64: String) : NetworkEvent()
}
