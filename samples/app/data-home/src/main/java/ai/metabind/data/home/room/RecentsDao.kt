package ai.metabind.data.home.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RecentsDao {
    @Query("SELECT * FROM ${RoomConstants.RECENTS_TABLE_NAME} ORDER BY lastVisited DESC")
    suspend fun getAll(): List<RecentItem>

    @Query("DELETE FROM ${RoomConstants.RECENTS_TABLE_NAME} WHERE url = :value")
    suspend fun deleteByUrl(value: String)

    @Query("DELETE FROM ${RoomConstants.RECENTS_TABLE_NAME} WHERE uid = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM  ${RoomConstants.RECENTS_TABLE_NAME} WHERE uid = :id")
    suspend fun getById(id: Long): RecentItem?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: RecentItem): Long

    @Query("DELETE FROM ${RoomConstants.RECENTS_TABLE_NAME}")
    suspend fun deleteAll()

    @Query("UPDATE ${RoomConstants.RECENTS_TABLE_NAME} SET lastVisited = :timestamp WHERE uid = :id")
    suspend fun updateLastVisited(id: Long, timestamp: Long)

    @Query("UPDATE ${RoomConstants.RECENTS_TABLE_NAME} SET name = :name WHERE uid = :id")
    suspend fun updateName(id: Long, name: String)
}