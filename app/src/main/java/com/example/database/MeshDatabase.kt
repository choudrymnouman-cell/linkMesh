package com.example.database

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile_table WHERE id = 1 LIMIT 1")
    fun getProfileFlow(): Flow<MyProfile?>

    @Query("SELECT * FROM profile_table WHERE id = 1 LIMIT 1")
    suspend fun getProfile(): MyProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: MyProfile)
}

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices_table ORDER BY signalStrength DESC")
    fun getAllDevices(): Flow<List<MeshDevice>>

    @Query("SELECT * FROM devices_table WHERE isOnline = 1")
    suspend fun getActiveDevices(): List<MeshDevice>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDevice(device: MeshDevice)

    @Query("DELETE FROM devices_table WHERE macAddress = :macAddress")
    suspend fun deleteDevice(macAddress: String)

    @Query("DELETE FROM devices_table")
    suspend fun deleteAllDevices()
}

@Dao
interface MessageDao {
    @Query("""
        SELECT * FROM messages_table 
        WHERE (senderId = :userId AND receiverId = :otherId AND isGroup = 0) 
           OR (senderId = :otherId AND receiverId = :userId AND isGroup = 0)
        ORDER BY timestamp ASC
    """)
    fun getChatHistory(userId: String, otherId: String): Flow<List<ChatMessage>>

    @Query("SELECT * FROM messages_table WHERE receiverId = :groupId AND isGroup = 1 ORDER BY timestamp ASC")
    fun getGroupChatHistory(groupId: String): Flow<List<ChatMessage>>

    // Get unique conversations list with last message
    @Query("""
        SELECT m1.* FROM messages_table m1
        JOIN (
            SELECT MAX(timestamp) as max_time, 
                   CASE WHEN isGroup = 1 THEN receiverId 
                        WHEN senderId = :userId THEN receiverId 
                        ELSE senderId END as contact_id
            FROM messages_table
            GROUP BY contact_id
        ) m2 ON m1.timestamp = m2.max_time
        ORDER BY m1.timestamp DESC
    """)
    fun getConversationsList(userId: String): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)

    @Delete
    suspend fun deleteMessage(message: ChatMessage)

    @Query("DELETE FROM messages_table WHERE (senderId = :userId AND receiverId = :otherId) OR (senderId = :otherId AND receiverId = :userId)")
    suspend fun clearChat(userId: String, otherId: String)

    @Query("DELETE FROM messages_table")
    suspend fun deleteAllMessages()

    @Query("SELECT * FROM messages_table WHERE attachmentType != 'NONE' ORDER BY timestamp DESC")
    fun getAllMessagesWithAttachments(): Flow<List<ChatMessage>>

    @Query("UPDATE messages_table SET status = :status WHERE messageId = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String)

    @Query("DELETE FROM messages_table WHERE timestamp < :cutoffTime")
    suspend fun cleanOldMessages(cutoffTime: Long)
}

@Dao
interface CommunityDao {
    @Query("SELECT * FROM broadcast_events_table ORDER BY timestamp DESC")
    fun getAllPosts(): Flow<List<CommunityPost>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: CommunityPost)

    @Query("UPDATE broadcast_events_table SET upvotesCount = :count, hasUpvoted = :hasUpvoted WHERE postId = :postId")
    suspend fun updatePostUpvote(postId: String, count: Int, hasUpvoted: Boolean)

    @Query("SELECT * FROM comments_table WHERE postId = :postId ORDER BY timestamp ASC")
    fun getCommentsForPost(postId: String): Flow<List<PostComment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: PostComment)

    @Query("DELETE FROM broadcast_events_table WHERE postId IN ('p1', 'p2', 'p3')")
    suspend fun deleteMockPosts()

    @Query("DELETE FROM comments_table WHERE postId IN ('p1', 'p2', 'p3')")
    suspend fun deleteMockComments()
}

@Database(
    entities = [
        MyProfile::class,
        MeshDevice::class,
        ChatMessage::class,
        CommunityPost::class,
        PostComment::class
    ],
    version = 3,
    exportSchema = false
)
abstract class MeshDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun deviceDao(): DeviceDao
    abstract fun messageDao(): MessageDao
    abstract fun communityDao(): CommunityDao

    companion object {
        @Volatile
        private var INSTANCE: MeshDatabase? = null

        fun getDatabase(context: Context): MeshDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MeshDatabase::class.java,
                    "mesh_connect_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
