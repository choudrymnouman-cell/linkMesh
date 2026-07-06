package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profile_table")
data class MyProfile(
    @PrimaryKey val id: Int = 1,
    val username: String = "",
    val uniqueId: String = "",
    val avatarColor: Int = 0xFF3F51B5.toInt(),
    val isRegistered: Boolean = false,
    val rescueMode: Boolean = false,
    val batteryTracking: Boolean = true,
    val autoCleanupHours: Int = 24,
    val appLanguage: String = "English", // English, Urdu, Arabic
    val photoUri: String? = null
)

@Entity(tableName = "devices_table")
data class MeshDevice(
    @PrimaryKey val macAddress: String,
    val username: String,
    val signalStrength: Int, // 0 to 100
    val isOnline: Boolean,
    val canRelay: Boolean,
    val batteryLevel: Int,
    val latitude: Double = 33.6844, // Islamabad default or some coordinates
    val longitude: Double = 73.0479,
    val isSOSActive: Boolean = false,
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val photoUri: String? = null
)

@Entity(tableName = "messages_table")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: String,
    val senderId: String,
    val senderName: String,
    val receiverId: String, // Can be user MAC or "group_id"
    val isGroup: Boolean = false,
    val textContent: String,
    val attachmentType: String = "NONE", // NONE, IMAGE, AUDIO, FILE
    val attachmentPath: String? = null,
    val attachmentSize: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "SENT", // SENT, DELIVERED, READ
    val hopsList: String = "Direct", // JSON or human readable list of hops (e.g., "Farhan -> You")
    val isIncoming: Boolean = false,
    val voiceDurationSec: Int = 0
)

@Entity(tableName = "broadcast_events_table")
data class CommunityPost(
    @PrimaryKey val postId: String,
    val authorUsername: String,
    val authorId: String,
    val messageContent: String,
    val postType: String = "ALERT", // ALERT, GENERAL, ANNOUNCEMENT, MARKETPLACE
    val timestamp: Long = System.currentTimeMillis(),
    val batteryLevel: Int = 85,
    val locationName: String = "Direct Link",
    val upvotesCount: Int = 0,
    val hasUpvoted: Boolean = false,
    val commentCount: Int = 0
)

@Entity(tableName = "comments_table")
data class PostComment(
    @PrimaryKey(autoGenerate = true) val commentId: Long = 0,
    val postId: String,
    val commenterName: String,
    val commentContent: String,
    val timestamp: Long = System.currentTimeMillis()
)
