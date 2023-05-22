package com.jwoglom.controlx2.db.historylog

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [HistoryLogItem::class], version = 1, exportSchema = false)
abstract class HistoryLogDatabase : RoomDatabase() {
    abstract fun historyLogDao(): HistoryLogDao

    companion object {
        @Volatile
        private var INSTANCE: HistoryLogDatabase? = null

        fun getDatabase(context: Context): HistoryLogDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HistoryLogDatabase::class.java,
                    HistoryLogTable
                ).build()
                INSTANCE = instance
                return instance
            }
        }
    }
}