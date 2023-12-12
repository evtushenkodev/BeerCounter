package com.example.beercounter

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "beer_data.db"
        private const val DATABASE_VERSION = 2
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Создаем таблицу с новыми столбцами
        val createTableSQL = """
            CREATE TABLE IF NOT EXISTS beer_table (
                name TEXT PRIMARY KEY,
                count REAL,
                received REAL DEFAULT 0,
                sold REAL DEFAULT 0
            )
        """.trimIndent()
        db.execSQL(createTableSQL)
        Log.d("MyDatabaseHelper", "Database table created.")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Обновляем таблицу, добавляя новые столбцы, если это необходимо
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE beer_table ADD COLUMN received REAL DEFAULT 0")
            db.execSQL("ALTER TABLE beer_table ADD COLUMN sold REAL DEFAULT 0")
        }
    }
}
