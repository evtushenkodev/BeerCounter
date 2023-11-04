package com.example.beercounter

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class MyDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "beer_data.db"
        private const val DATABASE_VERSION = 1
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Создание таблицы при первом запуске
        val createTableSQL = "CREATE TABLE IF NOT EXISTS beer_table " +
                "(name TEXT PRIMARY KEY, count REAL)"
        db.execSQL(createTableSQL)
        Log.d("MyDatabaseHelper", "Database table created.")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Обработка обновления базы данных
    }
}
