package ai.metabind.data.home.room

import androidx.core.net.toUri
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import javax.annotation.concurrent.Immutable

class RoomConstants {
    companion object {
        const val RECENTS_TABLE_NAME = "recents"
    }
}

@Immutable
@Entity(tableName = RoomConstants.RECENTS_TABLE_NAME)
data class RecentItem(
    @PrimaryKey(autoGenerate = true) val uid: Long = 0,
    @ColumnInfo(name = "url") val url: String,
    @ColumnInfo(name = "lastVisited") val lastVisited: Long,
    @ColumnInfo(name = "name") val name: String? = null,
) {
    val token: String
        get() = url.toUri().lastPathSegment ?: ""
}