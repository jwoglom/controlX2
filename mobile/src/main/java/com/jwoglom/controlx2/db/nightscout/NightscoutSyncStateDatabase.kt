package com.jwoglom.controlx2.db.nightscout

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jwoglom.controlx2.db.util.Converters

@Database(entities = [NightscoutSyncState::class, NightscoutProcessorState::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class NightscoutSyncStateDatabase : RoomDatabase() {
    abstract fun nightscoutSyncStateDao(): NightscoutSyncStateDao
    abstract fun nightscoutProcessorStateDao(): NightscoutProcessorStateDao

    companion object {
        @Volatile
        private var INSTANCE: NightscoutSyncStateDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `nightscout_processor_state` (`processorType` TEXT NOT NULL, `lastProcessedSeqId` INTEGER NOT NULL, `lastSuccessTime` INTEGER, PRIMARY KEY(`processorType`))")
            }
        }

        fun getDatabase(context: Context): NightscoutSyncStateDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NightscoutSyncStateDatabase::class.java,
                    NightscoutSyncStateTable
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                return instance
            }
        }
    }
}
