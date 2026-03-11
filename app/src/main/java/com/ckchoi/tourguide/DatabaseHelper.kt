package com.ckchoi.tourguide

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "tourguide.db"
        private const val DATABASE_VERSION = 6 // 지역ID 컬럼 추가로 인한 버전 업

        // 공통 컬럼
        const val COLUMN_ID = "id"

        // 1. Destinations 테이블
        const val TABLE_DESTINATIONS = "destinations"
        const val COLUMN_NAME = "name"

        // 2. Countries 테이블
        const val TABLE_COUNTRIES = "countries"
        const val COLUMN_COUNTRY_NAME = "country_name"
        const val COLUMN_FLAG = "flag_emoji"
        const val COLUMN_CODE = "country_code"
        const val COLUMN_BASIC_INFO = "basic_info"
        const val COLUMN_USEFUL_INFO = "useful_info"

        // 3. Regions 테이블
        const val TABLE_REGIONS = "regions"
        const val COLUMN_REGION_NAME = "region_name"
        const val COLUMN_REGION_CODE = "region_code" // [신규] 지역ID
        const val COLUMN_COUNTRY_ID = "country_id"
        const val COLUMN_REGION_DATA = "region_data"

        // 4. Phrase Categories 테이블
        const val TABLE_PHRASE_CATEGORIES = "phrase_categories"
        const val COLUMN_CATEGORY_NAME = "category_name"

        // 5. Phrases 테이블
        const val TABLE_PHRASES = "phrases"
        const val COLUMN_CATEGORY_ID = "category_id"
        const val COLUMN_MEANING = "meaning"
        const val COLUMN_EXPR1 = "expr1"
        const val COLUMN_EXPR2 = "expr2"
        const val COLUMN_EXPR3 = "expr3"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_DESTINATIONS ($COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_NAME TEXT)")

        db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_COUNTRIES ("
                + "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "$COLUMN_COUNTRY_NAME TEXT, $COLUMN_FLAG TEXT, $COLUMN_CODE TEXT, "
                + "$COLUMN_BASIC_INFO TEXT DEFAULT '', $COLUMN_USEFUL_INFO TEXT DEFAULT '')")

        // region_code 컬럼 추가됨
        db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_REGIONS ("
                + "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "$COLUMN_REGION_NAME TEXT, "
                + "$COLUMN_REGION_CODE TEXT, "
                + "$COLUMN_REGION_DATA TEXT DEFAULT '', "
                + "$COLUMN_COUNTRY_ID INTEGER, "
                + "FOREIGN KEY($COLUMN_COUNTRY_ID) REFERENCES $TABLE_COUNTRIES($COLUMN_ID) ON DELETE CASCADE)")

        db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_PHRASE_CATEGORIES ("
                + "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "$COLUMN_COUNTRY_ID INTEGER, $COLUMN_CATEGORY_NAME TEXT, "
                + "FOREIGN KEY($COLUMN_COUNTRY_ID) REFERENCES $TABLE_COUNTRIES($COLUMN_ID) ON DELETE CASCADE)")

        db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_PHRASES ("
                + "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "$COLUMN_CATEGORY_ID INTEGER, $COLUMN_MEANING TEXT, "
                + "$COLUMN_EXPR1 TEXT, $COLUMN_EXPR2 TEXT, $COLUMN_EXPR3 TEXT, "
                + "FOREIGN KEY($COLUMN_CATEGORY_ID) REFERENCES $TABLE_PHRASE_CATEGORIES($COLUMN_ID) ON DELETE CASCADE)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PHRASES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PHRASE_CATEGORIES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_REGIONS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_COUNTRIES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_DESTINATIONS")
        onCreate(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (!db.isReadOnly) db.execSQL("PRAGMA foreign_keys=ON;")
    }

    // --- Countries CRUD ---
    fun insertCountry(name: String, flag: String, code: String): Long {
        return this.writableDatabase.insert(TABLE_COUNTRIES, null, ContentValues().apply { put(COLUMN_COUNTRY_NAME, name); put(COLUMN_FLAG, flag); put(COLUMN_CODE, code) })
    }
    fun getAllCountriesWithId(): List<Triple<Int, String, String>> {
        val list = mutableListOf<Triple<Int, String, String>>()
        this.readableDatabase.rawQuery("SELECT $COLUMN_ID, $COLUMN_COUNTRY_NAME, $COLUMN_FLAG FROM $TABLE_COUNTRIES", null).use {
            if (it.moveToFirst()) do { list.add(Triple(it.getInt(0), it.getString(1), it.getString(2))) } while (it.moveToNext())
        }
        return list
    }
    fun updateCountryById(id: Int, newName: String, newFlag: String, newCode: String) {
        this.writableDatabase.update(TABLE_COUNTRIES, ContentValues().apply { put(COLUMN_COUNTRY_NAME, newName); put(COLUMN_FLAG, newFlag); put(COLUMN_CODE, newCode) }, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }
    fun deleteCountry(name: String) {
        this.writableDatabase.delete(TABLE_COUNTRIES, "$COLUMN_COUNTRY_NAME = ?", arrayOf(name))
    }
    fun getCountryInfo(countryId: Int, isBasic: Boolean): String {
        val col = if (isBasic) COLUMN_BASIC_INFO else COLUMN_USEFUL_INFO
        var info = ""
        this.readableDatabase.rawQuery("SELECT $col FROM $TABLE_COUNTRIES WHERE $COLUMN_ID = ?", arrayOf(countryId.toString())).use {
            if (it.moveToFirst()) info = it.getString(0) ?: ""
        }
        return info
    }
    fun updateCountryInfo(countryId: Int, isBasic: Boolean, info: String) {
        this.writableDatabase.update(TABLE_COUNTRIES, ContentValues().apply { put(if (isBasic) COLUMN_BASIC_INFO else COLUMN_USEFUL_INFO, info) }, "$COLUMN_ID = ?", arrayOf(countryId.toString()))
    }

    // --- Regions CRUD (지역ID 추가됨) ---
    fun insertRegion(name: String, code: String, countryId: Int) {
        this.writableDatabase.insert(TABLE_REGIONS, null, ContentValues().apply { put(COLUMN_REGION_NAME, name); put(COLUMN_REGION_CODE, code); put(COLUMN_COUNTRY_ID, countryId) })
    }
    fun getRegionsByCountry(countryId: Int): List<Triple<Int, String, String>> {
        val list = mutableListOf<Triple<Int, String, String>>()
        // ID, 이름, 지역ID(코드) 3가지를 반환
        this.readableDatabase.rawQuery("SELECT $COLUMN_ID, $COLUMN_REGION_NAME, $COLUMN_REGION_CODE FROM $TABLE_REGIONS WHERE $COLUMN_COUNTRY_ID = ?", arrayOf(countryId.toString())).use {
            if (it.moveToFirst()) do { list.add(Triple(it.getInt(0), it.getString(1), it.getString(2) ?: "")) } while (it.moveToNext())
        }
        return list
    }
    fun updateRegion(id: Int, newName: String, newCode: String) {
        this.writableDatabase.update(TABLE_REGIONS, ContentValues().apply { put(COLUMN_REGION_NAME, newName); put(COLUMN_REGION_CODE, newCode) }, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }
    fun deleteRegion(id: Int) {
        this.writableDatabase.delete(TABLE_REGIONS, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    fun getRegionData(regionId: Int): String {
        var data = ""
        this.readableDatabase.rawQuery("SELECT $COLUMN_REGION_DATA FROM $TABLE_REGIONS WHERE $COLUMN_ID = ?", arrayOf(regionId.toString())).use {
            if (it.moveToFirst()) data = it.getString(0) ?: ""
        }
        return data
    }
    fun updateRegionData(regionId: Int, jsonData: String) {
        this.writableDatabase.update(TABLE_REGIONS, ContentValues().apply { put(COLUMN_REGION_DATA, jsonData) }, "$COLUMN_ID = ?", arrayOf(regionId.toString()))
    }

    // --- Phrase Categories CRUD ---
    fun insertPhraseCategory(countryId: Int, name: String) {
        this.writableDatabase.insert(TABLE_PHRASE_CATEGORIES, null, ContentValues().apply { put(COLUMN_COUNTRY_ID, countryId); put(COLUMN_CATEGORY_NAME, name) })
    }
    fun getPhraseCategories(countryId: Int): List<Pair<Int, String>> {
        val list = mutableListOf<Pair<Int, String>>()
        this.readableDatabase.rawQuery("SELECT $COLUMN_ID, $COLUMN_CATEGORY_NAME FROM $TABLE_PHRASE_CATEGORIES WHERE $COLUMN_COUNTRY_ID = ?", arrayOf(countryId.toString())).use {
            if (it.moveToFirst()) do { list.add(Pair(it.getInt(0), it.getString(1))) } while (it.moveToNext())
        }
        return list
    }
    fun updatePhraseCategory(id: Int, name: String) {
        this.writableDatabase.update(TABLE_PHRASE_CATEGORIES, ContentValues().apply { put(COLUMN_CATEGORY_NAME, name) }, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }
    fun deletePhraseCategory(id: Int) {
        this.writableDatabase.delete(TABLE_PHRASE_CATEGORIES, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    // --- Phrases CRUD ---
    data class PhraseItem(val id: Int, val categoryId: Int, val meaning: String, val expr1: String, val expr2: String, val expr3: String)
    fun insertPhrase(categoryId: Int, meaning: String, expr1: String, expr2: String, expr3: String) {
        this.writableDatabase.insert(TABLE_PHRASES, null, ContentValues().apply { put(COLUMN_CATEGORY_ID, categoryId); put(COLUMN_MEANING, meaning); put(COLUMN_EXPR1, expr1); put(COLUMN_EXPR2, expr2); put(COLUMN_EXPR3, expr3) })
    }
    fun getPhrasesByCategory(categoryId: Int): List<PhraseItem> {
        val list = mutableListOf<PhraseItem>()
        this.readableDatabase.rawQuery("SELECT * FROM $TABLE_PHRASES WHERE $COLUMN_CATEGORY_ID = ?", arrayOf(categoryId.toString())).use {
            if (it.moveToFirst()) do { list.add(PhraseItem(it.getInt(0), it.getInt(1), it.getString(2)?:"", it.getString(3)?:"", it.getString(4)?:"", it.getString(5)?:"")) } while (it.moveToNext())
        }
        return list
    }
    fun updatePhrase(id: Int, meaning: String, expr1: String, expr2: String, expr3: String) {
        this.writableDatabase.update(TABLE_PHRASES, ContentValues().apply { put(COLUMN_MEANING, meaning); put(COLUMN_EXPR1, expr1); put(COLUMN_EXPR2, expr2); put(COLUMN_EXPR3, expr3) }, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }
    fun deletePhrase(id: Int) {
        this.writableDatabase.delete(TABLE_PHRASES, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }
}