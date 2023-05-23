package com.jwoglom.controlx2.db.historylog

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jwoglom.controlx2.db.util.Converters


@Database(entities = [HistoryLogItem::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
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
                    ).addMigrations(DbMigration1_2)
                    .build()
                INSTANCE = instance
                return instance
            }
        }
    }
}

val DbMigration1_2: Migration = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE $HistoryLogTable "
                + " ADD COLUMN pumpTime INTEGER NOT NULL DEFAULT(0)");
    }
}