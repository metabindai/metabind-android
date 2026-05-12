package ai.metabind.data.home.room

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class RecentsRepository(private val recentsDao: RecentsDao) {
    private val _allRecents: MutableSharedFlow<List<RecentItem>> = MutableSharedFlow()
    val allRecents: Flow<List<RecentItem>> = _allRecents.asSharedFlow()

    suspend fun load() {
        _allRecents.emit(recentsDao.getAll())
    }

    suspend fun getById(id: Long): RecentItem? {
        return recentsDao.getById(id = id)
    }

    suspend fun insert(item: RecentItem): Long {
        recentsDao.deleteByUrl(item.url)
        return recentsDao.insert(item)
    }

    suspend fun delete(itemId: Long) {
        recentsDao.deleteById(itemId)
    }

    suspend fun updateLastVisited(itemId: Long, timestamp: Long) {
        recentsDao.updateLastVisited(itemId, timestamp)
    }

    suspend fun updateName(itemId: Long, name: String) {
        recentsDao.updateName(itemId, name)
    }
}