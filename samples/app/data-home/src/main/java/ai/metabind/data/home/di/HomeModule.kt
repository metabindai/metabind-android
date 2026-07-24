package ai.metabind.data.home.di

import android.content.Context
import androidx.room.Room
import ai.metabind.data.home.room.MetabindDatabase
import ai.metabind.data.home.room.RecentsDao
import ai.metabind.data.home.room.RecentsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HomeModule {

    @Provides
    @Singleton
    fun metabindDatabase(
        @ApplicationContext context: Context,
    ): MetabindDatabase = Room.databaseBuilder(
        context,
        MetabindDatabase::class.java, "metabind-database"
    ).addMigrations(MetabindDatabase.MIGRATION_1_2).build()

    @Provides
    @Singleton
    fun recentsDao(
        metabindDatabase: MetabindDatabase,
    ): RecentsDao = metabindDatabase.recentItemDao()

    @Provides
    @Singleton
    fun recentsRepository(
        recentsDao: RecentsDao,
    ): RecentsRepository = RecentsRepository(recentsDao)

}

