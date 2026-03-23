package com.jwoglom.controlx2.db.nightscout

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.jwoglom.controlx2.db.util.Converters

@Database(
    entities = [NightscoutSyncState::class, NightscoutProcessorState::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class NightscoutSyncStateDatabase : RoomDatabase() {
    abstract fun nightscoutSyncStateDao(): NightscoutSyncStateDao
    abstract fun nightscoutProcessorStateDao(): NightscoutProcessorStateDao

    companion object {
        @Volatile
        private var INSTANCE: NightscoutSyncStateDatabase? = null

        fun getDatabase(context: Context): NightscoutSyncStateDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NightscoutSyncStateDatabase::class.java,
                    NightscoutSyncStateTable
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                return instance
            }
        }
    }
}
