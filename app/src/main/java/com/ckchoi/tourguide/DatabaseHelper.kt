package com.ckchoi.tourguide

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "tourguide.db"
        // [수정] 날씨용 지역명 추가로 인한 버전 7로 업그레이드
        private const val DATABASE_VERSION = 7

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
        const val COLUMN_REGION_CODE = "region_code"
        const val COLUMN_WEATHER_REGION = "weather_region_name" // [신규] API 조회용 날씨 영문 지역명
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

        // [수정] region_code 및 weather_region_name 컬럼 포함 생성
        db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_REGIONS ("
                + "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "$COLUMN_REGION_NAME TEXT, "
                + "$COLUMN_REGION_CODE TEXT, "
                + "$COLUMN_WEATHER_REGION TEXT DEFAULT '', "
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
        // [핵심] 기존 사용자의 데이터를 보존하면서 앱 크래시를 방지하는 마이그레이션 로직
        if (oldVersion < 6) {
            // 버전 6 이전에서 올라오는 경우 기존 로직 유지 (테이블 초기화)
            db.execSQL("DROP TABLE IF EXISTS $TABLE_PHRASES")
            db.execSQL("DROP TABLE IF EXISTS $TABLE_PHRASE_CATEGORIES")
            db.execSQL("DROP TABLE IF EXISTS $TABLE_REGIONS")
            db.execSQL("DROP TABLE IF EXISTS $TABLE_COUNTRIES")
            db.execSQL("DROP TABLE IF EXISTS $TABLE_DESTINATIONS")
            onCreate(db)
        } else if (oldVersion == 6) {
            // 버전 6 -> 7 업데이트 시: 기존 데이터 삭제 없이 새로운 컬럼만 추가 (앱 크래시 방지)
            try {
                db.execSQL("ALTER TABLE $TABLE_REGIONS ADD COLUMN $COLUMN_WEATHER_REGION TEXT DEFAULT ''")
                Log.d("DatabaseHelper", "Successfully upgraded DB to version 7 without data loss.")
            } catch (e: Exception) {
                Log.e("DatabaseHelper", "Failed to add column during upgrade", e)
            }
        }
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

    // --- Regions CRUD (지역ID 및 날씨용 지역명 추가됨) ---

    // [신규] 관리하기 쉬운 데이터 클래스 도입.
    // 하위 호환성(기존 MainActivity 소스에서 it.first 등의 호출을 지원)을 위해 프로퍼티 추가
    data class RegionItem(val id: Int, val name: String, val code: String, val weatherName: String) {
        val first: Int get() = id
        val second: String get() = name
        val third: String get() = code
    }

    fun insertRegion(name: String, code: String, weatherName: String, countryId: Int) {
        this.writableDatabase.insert(TABLE_REGIONS, null, ContentValues().apply {
            put(COLUMN_REGION_NAME, name)
            put(COLUMN_REGION_CODE, code)
            put(COLUMN_WEATHER_REGION, weatherName)
            put(COLUMN_COUNTRY_ID, countryId)
        })
    }

    fun getRegionsByCountry(countryId: Int): List<RegionItem> {
        val list = mutableListOf<RegionItem>()
        this.readableDatabase.rawQuery("SELECT $COLUMN_ID, $COLUMN_REGION_NAME, $COLUMN_REGION_CODE, $COLUMN_WEATHER_REGION FROM $TABLE_REGIONS WHERE $COLUMN_COUNTRY_ID = ?", arrayOf(countryId.toString())).use {
            if (it.moveToFirst()) do {
                list.add(RegionItem(it.getInt(0), it.getString(1), it.getString(2) ?: "", it.getString(3) ?: ""))
            } while (it.moveToNext())
        }
        return list
    }

    fun updateRegion(id: Int, newName: String, newCode: String, newWeatherName: String) {
        this.writableDatabase.update(TABLE_REGIONS, ContentValues().apply {
            put(COLUMN_REGION_NAME, newName)
            put(COLUMN_REGION_CODE, newCode)
            put(COLUMN_WEATHER_REGION, newWeatherName)
        }, "$COLUMN_ID = ?", arrayOf(id.toString()))
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