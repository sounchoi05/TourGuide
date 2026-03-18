package com.ckchoi.tourguide

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.PI
import kotlin.math.atan2
import com.ckchoi.tourguide.ui.theme.TourGuideTheme

// =========================================================================================
// [0] 전역 설정 (Global Settings & SharedPreferences)
// =========================================================================================

data class AppFontSize(
    val title: TextUnit = 22.sp,
    val menu: TextUnit = 18.sp,
    val body: TextUnit = 14.sp,
    val small: TextUnit = 12.sp
)

val LocalAppTypography = compositionLocalOf { AppFontSize() }

object FontPrefs {
    private const val PREFS_NAME = "font_settings"
    fun getSizes(ctx: Context): AppFontSize {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AppFontSize(
            title = prefs.getFloat("font_title", 22f).sp,
            menu = prefs.getFloat("font_menu", 18f).sp,
            body = prefs.getFloat("font_body", 14f).sp,
            small = prefs.getFloat("font_small", 12f).sp
        )
    }
    fun setSizes(ctx: Context, title: Float, menu: Float, body: Float, small: Float) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putFloat("font_title", title).putFloat("font_menu", menu)
            .putFloat("font_body", body).putFloat("font_small", small).apply()
    }
}

object TtsPrefs {
    private const val PREFS_NAME = "tts_settings"
    fun getSpeed(ctx: Context) = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getFloat("speed", 1.0f)
    fun setSpeed(ctx: Context, speed: Float) = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putFloat("speed", speed).apply()
    fun getVoice(ctx: Context, langTag: String): Pair<String, String>? {
        val saved = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("voice_$langTag", null) ?: return null
        val parts = saved.split("|")
        return if (parts.size == 2) Pair(parts[0], parts[1]) else null
    }
    fun setVoice(ctx: Context, langTag: String, enginePkg: String, voiceName: String) = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("voice_$langTag", "$enginePkg|$voiceName").apply()
}


// =========================================================================================
// [1] 데이터 모델 (Data Models)
// - 주차장, 식당, 스팟 모델에 images, isMustVisit, isVisited 필드가 추가되었습니다.
// =========================================================================================

data class BasicInfoData(var headerMainTitle: String="", var headerSubTitle: String="", var headerBadge1: String="", var headerBadge2: String="", var summaries: List<SummaryBox> = emptyList(), var tipsSectionTitle: String="", var tipsSubDesc: String="", var tipBoxes: List<TipBox> = listOf(TipBox(), TipBox(), TipBox(), TipBox()), var routeSectionTitle: String="", var routeImageUri1: String="", var routeImageUri2: String="", var budgetLodging: String="0", var budgetTransport: String="0", var budgetFood: String="0", var budgetOther: String="0", var distanceLabels: String="", var distances: String="", var languages: List<String> = listOf("", "", ""), var currencies: List<String> = listOf("", "", ""), var exchangeRate1: String="1.0", var exchangeRate2: String="1.0", var exchangeRate3: String="1.0", var accountItems: List<AccountItem> = emptyList())
data class SummaryBox(var title: String="", var value: String="", var desc: String="")
data class TipBox(var title: String="", var content: String="")
data class AccountItem(val id: String=UUID.randomUUID().toString(), var date: String="", var category: String="", var content: String="", var amount: String="", var currency: String="", var details: String="")
data class RegionDetailItem(val id: String=UUID.randomUUID().toString(), var travelDates: String="", var stayDuration: String="", var summary: String="", var tips: String="")
data class AccommodationItem(val id: String=UUID.randomUUID().toString(), var name: String="", var address: String="", var contact: String="", var homepage: String="", var googleMapLink: String="", var parkingAvailable: String="", var roomType: String="", var price: String="", var roomDetails: String="", var checkInOutTime: String="", var otherInfo: String="", var attachedFiles: List<String> = emptyList())
data class ScheduleItem(val id: String=UUID.randomUUID().toString(), var date: String="", var time: String="", var icon: String="", var content: String="", var details: String="", var precautions: String="")
data class MapItem(val id: String=UUID.randomUUID().toString(), var routeDetails: String="", var googleMapLink: String="", var googleMapEmbedLink: String="")
data class ParkingItem(val id: String=UUID.randomUUID().toString(), var name: String="", var address: String="", var googleMapLink: String="", var details: String="", var images: List<String> = emptyList())
data class SimpleItem(val id: String=UUID.randomUUID().toString(), var name: String="", var desc: String="", var googleMapLink: String="", var images: List<String> = emptyList(), var isMustVisit: Boolean = false, var isVisited: Boolean = false)
data class RestaurantItem(val id: String=UUID.randomUUID().toString(), var name: String="", var desc: String="", var menu: String="", var googleMapLink: String="", var images: List<String> = emptyList(), var isMustVisit: Boolean = false, var isVisited: Boolean = false)
data class GalleryItem(val id: String=UUID.randomUUID().toString(), var imageUri: String="", var desc: String="")
data class AudioGuideItem(val id: String=UUID.randomUUID().toString(), var sequence: Int=0, var attraction: String="", var title: String="", var details: String="", var imageUri: String="")
data class WeatherCacheData(var syncTime: String="", var currentTemp: String="", var weatherCode: Int=0, var rainProb: String="", var pm10: String="", var pm25: String="", var uvIndex: String="", var hourly: List<HourlyWeather> = emptyList(), var daily: List<DailyWeather> = emptyList())
data class HourlyWeather(var time: String="", var temp: String="", var code: Int=0, var rainProb: String="")
data class DailyWeather(var date: String="", var fullDate: String="", var minTemp: String="", var maxTemp: String="", var code: Int=0, var sunrise: String="", var sunset: String="", var moonrise: String="", var moonset: String="")
data class RegionData(var detail: RegionDetailItem=RegionDetailItem(), var accommodation: AccommodationItem=AccommodationItem(), var schedules: List<ScheduleItem> = emptyList(), var routes: List<MapItem> = emptyList(), var attractions: List<MapItem> = emptyList(), var restaurantMaps: List<MapItem> = emptyList(), var parkings: List<ParkingItem> = emptyList(), var foods: List<SimpleItem> = emptyList(), var restaurants: List<RestaurantItem> = emptyList(), var cheapRestaurants: List<RestaurantItem> = emptyList(), var spots: List<SimpleItem> = emptyList(), var galleries: List<GalleryItem> = emptyList(), var audioAttractions: List<String> = emptyList(), var audioGuides: List<AudioGuideItem> = emptyList(), var weatherCache: String="", var usefulInfo: String="")
val gson = Gson()

// =========================================================================================
// [2] 통신 및 파싱 유틸리티 (API Helpers & Parsers)
// =========================================================================================

suspend fun translateText(text: String, targetLangCode: String): String = withContext(Dispatchers.IO) {
    if (text.isBlank() || targetLangCode.isBlank()) return@withContext ""
    try {
        val encodedText = java.net.URLEncoder.encode(text, "UTF-8")
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLangCode&dt=t&q=$encodedText"
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        val response = conn.inputStream.bufferedReader().readText()

        val jsonArray = org.json.JSONArray(response)
        var translated = ""
        val chunks = jsonArray.getJSONArray(0)
        for (i in 0 until chunks.length()) {
            translated += chunks.getJSONArray(i).getString(0)
        }
        translated
    } catch(e: Exception) {
        e.printStackTrace()
        ""
    }
}

fun generateRestaurantHtml(items: List<RestaurantItem>): String {
    if (items.isEmpty()) return ""
    return items.joinToString("\n") { item ->
        "<li><strong>${item.name}</strong> - 메뉴: ${item.menu} - ${item.desc} <a href=\"${item.googleMapLink}\">지도</a></li>"
    }
}

/** HTML 파싱 시 기존에 설정된 이미지와 방문 상태(MustVisit, Visited)를 유지하도록 oldItems를 받아와 병합합니다. */
fun parseRestaurantHtml(html: String, oldItems: List<RestaurantItem>): List<RestaurantItem> {
    val items = mutableListOf<RestaurantItem>()
    val liRegex = Regex("<li>(.*?)</li>", RegexOption.IGNORE_CASE)
    val nameRegex = Regex("<strong>(.*?)</strong>|<b[^>]*>(.*?)</b>", RegexOption.IGNORE_CASE)
    val linkRegex = Regex("href=[\"'](.*?)[\"']", RegexOption.IGNORE_CASE)
    val menuRegex = Regex("메뉴:\\s*(.*?)(?=\\s*-|\\s*<|$)", RegexOption.IGNORE_CASE)

    liRegex.findAll(html).forEach { matchResult ->
        val innerHtml = matchResult.groupValues[1]
        val nameMatch = nameRegex.find(innerHtml)
        val name = nameMatch?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }?.trim() ?: "이름 없음"
        val link = linkRegex.find(innerHtml)?.groupValues?.get(1)?.trim() ?: ""
        val menu = menuRegex.find(innerHtml)?.groupValues?.get(1)?.trim() ?: ""

        var desc = innerHtml.replace(nameRegex, "")
            .replace(Regex("<a[^>]*>.*?</a>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("메뉴:\\s*.*?(?=\\s*-|\\s*<|$)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<[^>]*>"), "")
            .replace("-", "")
            .trim()

        val oldItem = oldItems.find { it.name == name }
        if (oldItem != null) {
            items.add(RestaurantItem(id = oldItem.id, name = name, desc = desc, menu = menu, googleMapLink = link, images = oldItem.images, isMustVisit = oldItem.isMustVisit, isVisited = oldItem.isVisited))
        } else {
            items.add(RestaurantItem(id = UUID.randomUUID().toString(), name = name, desc = desc, menu = menu, googleMapLink = link))
        }
    }
    return items
}

fun generateSimpleHtml(items: List<SimpleItem>): String {
    if (items.isEmpty()) return ""
    return items.joinToString("\n") { item ->
        "<li><strong>${item.name}</strong> - ${item.desc} <a href=\"${item.googleMapLink}\">지도</a></li>"
    }
}

/** HTML 파싱 시 기존에 설정된 이미지와 방문 상태(MustVisit, Visited)를 유지하도록 oldItems를 받아와 병합합니다. */
fun parseSimpleHtml(html: String, oldItems: List<SimpleItem>): List<SimpleItem> {
    val items = mutableListOf<SimpleItem>()
    val liRegex = Regex("<li>(.*?)</li>", RegexOption.IGNORE_CASE)
    val nameRegex = Regex("<strong>(.*?)</strong>|<b[^>]*>(.*?)</b>", RegexOption.IGNORE_CASE)
    val linkRegex = Regex("href=[\"'](.*?)[\"']", RegexOption.IGNORE_CASE)

    liRegex.findAll(html).forEach { matchResult ->
        val innerHtml = matchResult.groupValues[1]
        val nameMatch = nameRegex.find(innerHtml)
        val name = nameMatch?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }?.trim() ?: "이름 없음"
        val link = linkRegex.find(innerHtml)?.groupValues?.get(1)?.trim() ?: ""

        var desc = innerHtml.replace(nameRegex, "")
            .replace(Regex("<a[^>]*>.*?</a>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<[^>]*>"), "")
            .replace("-", "")
            .trim()

        val oldItem = oldItems.find { it.name == name }
        if (oldItem != null) {
            items.add(SimpleItem(id = oldItem.id, name = name, desc = desc, googleMapLink = link, images = oldItem.images, isMustVisit = oldItem.isMustVisit, isVisited = oldItem.isVisited))
        } else {
            items.add(SimpleItem(id = UUID.randomUUID().toString(), name = name, desc = desc, googleMapLink = link))
        }
    }
    return items
}
// =========================================================================================
// [3] 일반 유틸리티 (General Utilities)
// - 숫자 변환, 파일 저장, 날씨/위상 계산 등의 공통 함수 모음
// =========================================================================================

fun parseSafeFloat(input: String): Float = input.replace(Regex("[^0-9.\\-]"), "").toFloatOrNull() ?: 0f
fun formatCurrency(amount: Float): String = java.text.DecimalFormat("#,##0.##").format(amount)
fun cleanForTts(text: String): String = text.replace(Regex("[^\\p{L}\\p{Nd}\\s.,!?]"), " ")

/** 내부 저장소에 이미지를 복사하여 저장하고 절대 경로를 반환합니다. */
fun saveImageToInternalStorage(context: Context, uri: Uri): String {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
        val imageDir = File(context.filesDir, "tour_images").apply { if(!exists()) mkdirs() }
        val outFile = File(imageDir, "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
        inputStream.use { input -> FileOutputStream(outFile).use { output -> input.copyTo(output) } }
        outFile.absolutePath
    } catch (e: Exception) { "" }
}

/** 내부 저장소에 일반 파일(PDF 등)을 복사하여 저장하고 절대 경로를 반환합니다. */
fun saveFileToInternalStorage(context: Context, uri: Uri): String {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
        val fileDir = File(context.filesDir, "tour_files").apply { if(!exists()) mkdirs() }
        var originalName = "file_${System.currentTimeMillis()}"
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) { val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (idx >= 0) originalName = cursor.getString(idx) }
            }
        }
        val safeName = UUID.randomUUID().toString().take(6) + "_" + originalName.replace(Regex("[^a-zA-Z0-9.\\-_]"), "_")
        val outFile = File(fileDir, safeName)
        inputStream.use { input -> FileOutputStream(outFile).use { output -> input.copyTo(output) } }
        outFile.absolutePath
    } catch (e: Exception) { "" }
}

fun isImageFile(path: String): Boolean = path.lowercase().let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".gif") || it.endsWith(".webp") }

/** 앱 내부 저장소에 있는 첨부파일을 사용자의 '다운로드' 폴더로 내보냅니다. */
fun exportFileToDownloads(context: Context, sourcePath: String) {
    try {
        val sourceFile = File(sourcePath)
        if (!sourceFile.exists()) { Toast.makeText(context, "파일을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show(); return }
        val destDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val originalName = if (sourceFile.name.contains("_")) sourceFile.name.substringAfter("_") else sourceFile.name
        File(destDir, "TourGuide_$originalName").outputStream().use { out -> sourceFile.inputStream().use { it.copyTo(out) } }
        Toast.makeText(context, "다운로드 폴더에 저장되었습니다.\n(TourGuide_$originalName)", Toast.LENGTH_LONG).show()
    } catch (e: Exception) { Toast.makeText(context, "파일 다운로드 실패", Toast.LENGTH_SHORT).show() }
}

/** Open-Meteo 날씨 코드를 시각적인 이모지로 변환합니다. */
fun getWeatherEmoji(code: Int): String = when (code) {
    0 -> "☀️"; 1, 2, 3 -> "⛅"; 45, 48 -> "🌫️"; 51, 53, 55, 56, 57 -> "🌧️"
    61, 63, 65, 66, 67 -> "☔"; 71, 73, 75, 77 -> "❄️"; 80, 81, 82 -> "🌧️"
    85, 86 -> "🌨️"; 95, 96, 99 -> "⛈️"; else -> "☁️"
}

/** 날짜를 기반으로 달의 위상(초승달, 보름달 등)을 계산하여 이모지로 반환합니다. */
fun getMoonPhaseEmoji(dateStr: String): String {
    return try {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr) ?: return "🌕"
        val refDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse("2000-01-06") ?: return "🌕"
        val phase = ((date.time - refDate.time) / (1000.0 * 60 * 60 * 24)) % 29.53058867
        val nPhase = if (phase < 0) phase + 29.53058867 else phase
        when { nPhase < 1.84 -> "🌑"; nPhase < 5.53 -> "🌒"; nPhase < 9.22 -> "🌓"; nPhase < 12.91 -> "🌔"; nPhase < 16.61 -> "🌕"; nPhase < 20.30 -> "🌖"; nPhase < 23.99 -> "🌗"; nPhase < 27.68 -> "🌘"; else -> "🌑" }
    } catch(e: Exception) { "🌕" }
}

/** 일출/일몰 시간과 달의 위상을 조합하여 근사치로 월출/월몰 시간을 계산합니다. */
fun getApproxMoonTime(sunTime: String, dateStr: String): String {
    if (sunTime.isBlank()) return ""
    return try {
        val parts = sunTime.split(":")
        if (parts.size < 2) return ""
        val sunH = parts[0].toInt(); val sunM = parts[1].toInt()
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr) ?: return ""
        val refDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse("2000-01-06") ?: return ""
        val phase = ((date.time - refDate.time) / (1000.0 * 60 * 60 * 24)) % 29.53058867
        val nPhase = if (phase < 0) phase + 29.53058867 else phase
        val delayMinutes = ((nPhase / 29.53058867) * 24 * 60).toInt()
        var totalM = sunH * 60 + sunM + delayMinutes
        totalM %= (24 * 60)
        val outH = totalM / 60; val outM = totalM % 60
        String.format(Locale.US, "%02d:%02d", outH, outM)
    } catch(e: Exception) { "" }
}

/** BCP-47 언어 태그를 안드로이드 TTS가 이해할 수 있는 Locale 객체로 변환합니다. */
fun getTtsLocale(lang: String): Locale {
    val l = lang.lowercase()
    return when {
        l.contains("영어") || l.contains("english") -> Locale.US
        l.contains("스페인") || l.contains("spanish") -> Locale.Builder().setLanguage("es").setRegion("ES").build()
        l.contains("포르투갈") || l.contains("portuguese") -> Locale.Builder().setLanguage("pt").setRegion("PT").build()
        l.contains("프랑스") || l.contains("french") -> Locale.FRANCE
        l.contains("독일") || l.contains("german") -> Locale.GERMAN
        l.contains("이탈리아") || l.contains("italian") -> Locale.ITALY
        l.contains("일본") || l.contains("japanese") -> Locale.JAPAN
        l.contains("중국") || l.contains("chinese") -> Locale.CHINA
        else -> Locale.getDefault()
    }
}

/** 언어별로 TTS 미리듣기에 사용할 샘플 문장을 반환합니다. */
fun getSampleTextForTts(langTag: String): String {
    val l = langTag.lowercase()
    return when {
        l.contains("ko") || l.contains("kr") -> "안녕하세요, 가이드 음성 테스트입니다."
        l.contains("es") -> "Hola, esto es una prueba de voz."
        l.contains("pt") -> "Olá, este é um teste de voz."
        l.contains("fr") -> "Bonjour, c'est un test vocal."
        l.contains("de") -> "Hallo, dies ist ein Sprachtest."
        l.contains("it") -> "Ciao, questa è una prova vocale."
        l.contains("ja") -> "こんにちは、音声テストです。"
        l.contains("zh") -> "你好, 这是语音测试。"
        else -> "Hello, this is a voice test."
    }
}

// =========================================================================================
// [4] 핵심 매니저 (TTS Manager & Image Viewers)
// =========================================================================================

data class VoiceWrapper(val voice: Voice, val enginePackage: String, val displayName: String)

/** 다중 TTS 엔진(구글, 삼성 등)을 로드하고 음성 가이드 출력을 관리하는 클래스 */
class TtsManager(val context: Context) {
    var onStartCallback: ((String) -> Unit)? = null
    var onDoneCallback: ((String) -> Unit)? = null
    val ttsEngines = mutableMapOf<String, TextToSpeech>()
    val tts: TextToSpeech? get() = ttsEngines["com.google.android.tts"] ?: ttsEngines.values.firstOrNull()

    init { initEngine("com.google.android.tts"); initEngine("com.samsung.SMT") }

    private fun initEngine(pkg: String) {
        try {
            context.packageManager.getPackageInfo(pkg, 0)
            val engine = TextToSpeech(context, { _ -> }, pkg)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { utteranceId?.let { onStartCallback?.invoke(it) } }
                override fun onDone(utteranceId: String?) { utteranceId?.let { onDoneCallback?.invoke(it) } }
                @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) {}
            })
            ttsEngines[pkg] = engine
        } catch(e: Exception){}
    }

    /** 특정 로케일에 맞는 오프라인 보이스 목록을 필터링하여 가져옵니다. */
    fun getAvailableVoices(locale: Locale): List<VoiceWrapper> {
        val list = mutableListOf<VoiceWrapper>()
        ttsEngines.forEach { (pkg, engine) ->
            try {
                engine.voices?.filter { it.locale.language == locale.language && !it.isNetworkConnectionRequired }?.forEach {
                    list.add(VoiceWrapper(it, pkg, getVoiceDisplayName(it, pkg)))
                }
            } catch(e: Exception){}
        }
        return list.sortedBy { it.displayName }
    }

    private fun getVoiceDisplayName(voice: Voice, enginePkg: String): String {
        val localeName = try { voice.locale.getDisplayName(Locale.KOREAN) } catch (e: Exception) { voice.locale.toString() }
        val prefix = when { enginePkg.contains("samsung", true) -> "S."; enginePkg.contains("google", true) -> "G."; else -> "" }
        val features = voice.features?.toString()?.lowercase() ?: ""
        val vName = voice.name.lowercase()
        val gender = when { features.contains("female") || vName.contains("female") || vName.contains("-f") -> "여성"; features.contains("male") || vName.contains("male") || vName.contains("-m") -> "남성"; else -> "" }
        val genderStr = if (gender.isNotEmpty()) " $gender" else ""
        var id = voice.name.substringAfterLast("-").uppercase()
        if (vName.contains("-x-")) id = vName.substringAfter("-x-").substringBefore("-").uppercase()
        if (id.length > 5) id = id.take(5)
        return "$prefix$localeName - $id$genderStr".trim()
    }

    fun stop() { ttsEngines.values.forEach { it.stop() } }
    fun shutdown() { ttsEngines.values.forEach { it.stop(); it.shutdown() }; ttsEngines.clear() }
}

/** 내부 저장소나 Content URI에 있는 이미지를 비동기로 안전하게 로드하는 컴포저블 */
@Composable
fun UriImage(uriString: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val context = LocalContext.current
    var bitmap by remember(uriString) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uriString) {
        if (uriString.isNotBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val bmp = if (uriString.startsWith("content://")) {
                        val uri = Uri.parse(uriString)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                        else @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    } else {
                        val file = File(uriString)
                        if (file.exists()) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ImageDecoder.decodeBitmap(ImageDecoder.createSource(file))
                            else BitmapFactory.decodeFile(uriString)
                        } else null
                    }
                    bitmap = bmp?.asImageBitmap()
                } catch (e: Exception) {}
            }
        }
    }
    if (bitmap != null) Image(bitmap = bitmap!!, contentDescription = null, modifier = modifier, contentScale = contentScale)
    else Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Text("이미지 없음", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = LocalAppTypography.current.small) }
}

/** * [수정됨] 갤러리처럼 여러 장의 이미지를 스와이프하며 넘겨보고 핀치 줌을 지원하는 뷰어입니다.
 * 줌 인 상태에서는 드래그 시 패닝(이동)이 작동하고 빈 화면으로 밀리지 않습니다.
 * 줌 아웃(1배율) 상태에서는 제스처를 패스하여 자연스럽게 다음/이전 사진으로 넘어갑니다.
 */
@Composable
fun FullScreenImageViewer(imageUris: List<String>, initialIndex: Int, onClose: () -> Unit) {
    val pagerState = rememberPagerState(initialPage = initialIndex) { imageUris.size }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .zIndex(100f)
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }

            // 페이지 전환 시 확대 상태와 위치를 1배율 정중앙으로 초기화
            LaunchedEffect(pagerState.currentPage) {
                if (pagerState.currentPage != page) {
                    scale = 1f
                    offset = Offset.Zero
                }
            }

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                UriImage(
                    uriString = imageUris[page],
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            // 더블 탭 시 1배율 초기화 기능
                            detectTapGestures(
                                onDoubleTap = {
                                    scale = 1f
                                    offset = Offset.Zero
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            // 커스텀 제스처: 줌 인(확대) 상태에서만 패닝 이벤트를 소비하고, 1배율일 때는 Pager가 스와이프되도록 무시합니다.
                            awaitEachGesture {
                                awaitFirstDown()
                                do {
                                    val event = awaitPointerEvent()
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()

                                    // 확대 축소 시도 또는 이미 확대된 상태인 경우
                                    if (zoomChange != 1f || scale > 1f) {
                                        scale = (scale * zoomChange).coerceIn(1f, 5f)

                                        // 빈 화면으로 과도하게 이미지가 밀리는 현상 방지 (경계값 계산)
                                        val maxX = (size.width * (scale - 1)) / 2
                                        val maxY = (size.height * (scale - 1)) / 2

                                        if (scale > 1f) {
                                            offset = Offset(
                                                x = (offset.x + panChange.x).coerceIn(-maxX, maxX),
                                                y = (offset.y + panChange.y).coerceIn(-maxY, maxY)
                                            )
                                        } else {
                                            offset = Offset.Zero
                                        }

                                        // 이벤트 소비 (Pager로 스와이프 이벤트가 넘어가지 못하게 막음)
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        ),
                    contentScale = ContentScale.Fit
                )
            }
        }

        // 닫기 버튼 및 상단 이미지 인디케이터 (현재 / 전체)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${pagerState.currentPage + 1} / ${imageUris.size}", color = Color.White, fontSize = LocalAppTypography.current.menu, fontWeight = FontWeight.Bold)
            IconButton(
                onClick = onClose,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
            ) {
                Text("X", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// =========================================================================================
// [5] 앱 진입점 및 메인 라우팅 (App Entry Point & Navigation Routing)
// =========================================================================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TourGuideTheme { TourGuideApp() } }
    }
}

/** 앱의 전체 화면 이동(라우팅) 상태와 상단 TopAppBar를 관리하는 최상위 컴포저블 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TourGuideApp() {
    val context = LocalContext.current
    var appTypography by remember { mutableStateOf(FontPrefs.getSizes(context)) }
    var currentScreen by remember { mutableStateOf("홈메인") }
    var selectedCountryId by remember { mutableStateOf<Int?>(null) }
    var selectedCountryName by remember { mutableStateOf("") }
    var selectedRegionId by remember { mutableStateOf<Int?>(null) }
    var selectedRegionName by remember { mutableStateOf("") }
    var selectedAudioGuideId by remember { mutableStateOf<String?>(null) }
    var userCountryTabIndex by remember { mutableIntStateOf(0) }
    var adminCountryTabIndex by remember { mutableIntStateOf(0) }
    var userRegionTabIndex by remember { mutableIntStateOf(0) }
    var adminRegionTabIndex by remember { mutableIntStateOf(0) }
    var userSelectedAttraction by remember { mutableStateOf<String?>(null) }

    // 다중 이미지를 보여주기 위한 갤러리 라우팅 상태
    var fullScreenImageUris by remember { mutableStateOf<List<String>?>(null) }
    var fullScreenImageIndex by remember { mutableIntStateOf(0) }

    val dbHelper = remember { DatabaseHelper(context) }
    val ttsManager = remember { TtsManager(context) }
    var backPressedTime by remember { mutableLongStateOf(0L) }

    // 안드로이드 물리적 뒤로가기 버튼에 대한 커스텀 네비게이션 제어
    BackHandler {
        if (fullScreenImageUris != null) fullScreenImageUris = null
        else if (currentScreen == "지역 사용자 화면" && userRegionTabIndex == 12 && userSelectedAttraction != null) userSelectedAttraction = null
        else if (currentScreen == "홈메인") {
            if (System.currentTimeMillis() - backPressedTime < 2000) { ttsManager.shutdown(); (context as? Activity)?.finishAffinity() }
            else { Toast.makeText(context, "뒤로가기를 두 번 클릭하여 종료합니다.", Toast.LENGTH_SHORT).show(); backPressedTime = System.currentTimeMillis() }
        } else {
            currentScreen = when (currentScreen) { "앱설정" -> "홈메인"; "여행지 설정" -> "앱설정"; "국가 상세" -> "여행지 설정"; "지역 상세" -> "국가 상세"; "국가 사용자 화면" -> "홈메인"; "지역 사용자 화면" -> "국가 사용자 화면"; "오디오 가이드 상세" -> "지역 사용자 화면"; else -> "홈메인" }
            if (currentScreen != "오디오 가이드 상세") ttsManager.stop()
        }
    }

    CompositionLocalProvider(LocalAppTypography provides appTypography) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(when(currentScreen){"홈메인"->"여행 가이드";"국가 사용자 화면"->selectedCountryName;"국가 상세"->"$selectedCountryName 관리";"지역 사용자 화면"->"$selectedCountryName > $selectedRegionName";"오디오 가이드 상세"->"오디오 가이드";"지역 상세"->"$selectedCountryName > $selectedRegionName 관리";"앱설정"->"앱 설정";"여행지 설정"->"여행지 관리";else->currentScreen}, fontSize = LocalAppTypography.current.title) },
                        navigationIcon = {
                            if (currentScreen != "홈메인") IconButton(onClick = {
                                if (currentScreen == "지역 사용자 화면" && userRegionTabIndex == 12 && userSelectedAttraction != null) userSelectedAttraction = null
                                else {
                                    currentScreen = when (currentScreen) { "앱설정" -> "홈메인"; "여행지 설정" -> "앱설정"; "국가 상세" -> "여행지 설정"; "지역 상세" -> "국가 상세"; "국가 사용자 화면" -> "홈메인"; "지역 사용자 화면" -> "국가 사용자 화면"; "오디오 가이드 상세" -> "지역 사용자 화면"; else -> "홈메인" }
                                    if (currentScreen != "오디오 가이드 상세") ttsManager.stop()
                                }
                            }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로 가기") }
                        },
                        actions = { if (currentScreen == "홈메인") IconButton(onClick = { currentScreen = "앱설정" }) { Icon(Icons.Default.Settings, contentDescription = "앱 설정") } },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer, titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                    )
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                    // currentScreen 상태에 맞추어 각각의 컴포저블 화면을 매핑
                    when (currentScreen) {
                        "홈메인" -> HomeScreen(dbHelper) { id, name -> selectedCountryId=id; selectedCountryName=name; userCountryTabIndex=0; currentScreen="국가 사용자 화면" }
                        "국가 사용자 화면" -> CountryUserScreen(dbHelper, selectedCountryId?:0, userCountryTabIndex, {userCountryTabIndex=it}, { id, name -> selectedRegionId=id; selectedRegionName=name; userRegionTabIndex=0; userSelectedAttraction=null; currentScreen="지역 사용자 화면" }, { uri -> fullScreenImageUris = listOf(uri); fullScreenImageIndex = 0 }, ttsManager)
                        "지역 사용자 화면" -> RegionUserScreen(dbHelper, selectedRegionId?:0, userRegionTabIndex, {userRegionTabIndex=it}, userSelectedAttraction, {userSelectedAttraction=it}, { uri -> fullScreenImageUris = listOf(uri); fullScreenImageIndex = 0 }, { uris, idx -> fullScreenImageUris = uris; fullScreenImageIndex = idx }, { currentScreen="오디오 가이드 상세"; selectedAudioGuideId=it })
                        "오디오 가이드 상세" -> selectedAudioGuideId?.let { AudioGuideDetailScreen(dbHelper, selectedCountryId?:0, selectedRegionId?:0, it, ttsManager, { uri -> fullScreenImageUris = listOf(uri); fullScreenImageIndex = 0 }, { newId -> selectedAudioGuideId=newId }) }
                        "앱설정" -> SettingsScreen(dbHelper, ttsManager, { route -> currentScreen=route }, {appTypography=FontPrefs.getSizes(context)})
                        "여행지 설정" -> CountrySettingScreen(dbHelper, { id, name -> selectedCountryId=id; selectedCountryName=name; adminCountryTabIndex=0; currentScreen="국가 상세" }, {currentScreen="홈메인"})
                        "국가 상세" -> CountryDetailScreen(dbHelper, selectedCountryId?:0, adminCountryTabIndex, {adminCountryTabIndex=it}, { id, name -> selectedRegionId=id; selectedRegionName=name; adminRegionTabIndex=0; currentScreen="지역 상세" })
                        "지역 상세" -> RegionDetailScreen(dbHelper, countryId=selectedCountryId?:0, regionId=selectedRegionId?:0, selectedTabIndex=adminRegionTabIndex, onTabSelected={adminRegionTabIndex=it}, onShowImage={ uri -> fullScreenImageUris = listOf(uri); fullScreenImageIndex = 0 }, onShowMultiImage={ uris, idx -> fullScreenImageUris = uris; fullScreenImageIndex = idx })
                    }
                }
            }
            // 전체 화면 갤러리 이미지 뷰어 호출 (리스트 형태로 전달하여 스와이프를 지원)
            fullScreenImageUris?.let { uris ->
                FullScreenImageViewer(imageUris = uris, initialIndex = fullScreenImageIndex, onClose = { fullScreenImageUris = null })
            }
        }
    }
}
// =========================================================================================
// [5] 사용자 화면 (User Screens) - 국가 레벨
// - 사용자가 앱을 실행하고 보게 되는 실제 가이드 화면들입니다.
// =========================================================================================

/** * 앱의 첫 화면입니다.
 * DatabaseHelper를 통해 저장된 국가 목록을 불러와 리스트 형태로 보여줍니다.
 */
@Composable
fun HomeScreen(
    dbHelper: DatabaseHelper,
    onCountryClick: (Int, String) -> Unit
) {
    var countries by remember { mutableStateOf<List<Triple<Int, String, String>>>(emptyList()) }

    // 화면이 로드될 때 백그라운드 스레드에서 국가 목록을 가져옵니다.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            countries = dbHelper.getAllCountriesWithId()
        }
    }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (countries.isEmpty()) {
            Text(
                "우측 상단의 톱니바퀴(⚙️) 아이콘을 눌러\n여행지를 추가하세요.",
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
                fontSize = LocalAppTypography.current.body,
                lineHeight = LocalAppTypography.current.menu
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(countries) { (id, name, flag) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable { onCountryClick(id, name) },
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            text = "$flag $name",
                            modifier = Modifier.padding(20.dp),
                            fontSize = LocalAppTypography.current.title,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 특정 국가를 선택했을 때 나타나는 메인 탭 화면입니다.
 * 가로 스와이프가 가능한 HorizontalPager를 사용하여 각 하위 탭 화면을 연결합니다.
 */
@Composable
fun CountryUserScreen(
    dbHelper: DatabaseHelper,
    countryId: Int,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onRegionClick: (Int, String) -> Unit,
    onShowImage: (String) -> Unit,
    ttsManager: TtsManager
) {
    val tabs = listOf("기본정보", "날씨/환율", "지역", "회화표현", "유용한정보", "가계부")
    val pagerState = rememberPagerState(initialPage = selectedTabIndex) { tabs.size }
    val coroutineScope = rememberCoroutineScope()

    // 탭 선택 상태와 Pager의 스크롤 상태를 동기화합니다.
    LaunchedEffect(selectedTabIndex) {
        if (pagerState.currentPage != selectedTabIndex) {
            pagerState.scrollToPage(selectedTabIndex)
        }
    }
    // [수정됨] 애니메이션 도중 멈추는 버그 방지를 위해 settledPage 사용
    LaunchedEffect(pagerState.settledPage) {
        onTabSelected(pagerState.settledPage)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = pagerState.currentPage, edgePadding = 8.dp) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(title, fontSize = LocalAppTypography.current.body) }
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.Top) { page ->
            when (page) {
                0 -> UserBasicInfoTab(dbHelper, countryId, onShowImage)
                1 -> UserWeatherExchangeTab(dbHelper, countryId)
                2 -> UserRegionTab(dbHelper, countryId, onRegionClick)
                3 -> UserPhraseTab(dbHelper, countryId, ttsManager)
                4 -> UserUsefulInfoTab(dbHelper, countryId)
                5 -> UserAccountBookTab(dbHelper, countryId)
            }
        }
    }
}

/**
 * [국가 탭 1] 기본정보
 * 국가의 핵심 요약, 팁, 여행 루트 이미지, 예산(파이차트), 거리(막대차트)를 보여줍니다.
 */
@Composable
fun UserBasicInfoTab(dbHelper: DatabaseHelper, countryId: Int, onShowImage: (String) -> Unit) {
    val savedJson = dbHelper.getCountryInfo(countryId, true)
    val data = try {
        if (savedJson.isNotEmpty()) gson.fromJson(savedJson, BasicInfoData::class.java) ?: BasicInfoData()
        else BasicInfoData()
    } catch(e: Exception) { BasicInfoData() }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {

        // 상단 헤더 영역 (타이틀 및 배지)
        Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
            Column {
                if (data.headerMainTitle.isNotBlank()) {
                    Text(data.headerMainTitle, fontSize = LocalAppTypography.current.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                if (data.headerSubTitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(data.headerSubTitle, fontSize = LocalAppTypography.current.menu, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    if (data.headerBadge1.isNotBlank()) {
                        Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                            Text(data.headerBadge1, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(4.dp), fontSize = LocalAppTypography.current.small)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (data.headerBadge2.isNotBlank()) {
                        Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(data.headerBadge2, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(4.dp), fontSize = LocalAppTypography.current.small)
                        }
                    }
                }
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            // 핵심 요약 영역
            val validSummaries = data.summaries.filter { it.title.isNotBlank() || it.value.isNotBlank() }
            if (validSummaries.isNotEmpty()) {
                Text("📌 핵심 요약", fontSize = LocalAppTypography.current.menu, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.height(8.dp))
                validSummaries.forEach { summary ->
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(summary.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = LocalAppTypography.current.body)
                                Text(summary.value, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.body)
                            }
                            if (summary.desc.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(summary.desc, fontSize = LocalAppTypography.current.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 주의사항 및 팁 영역
            val validTips = data.tipBoxes.filter { it.title.isNotBlank() || it.content.isNotBlank() }
            if (data.tipsSectionTitle.isNotBlank() || validTips.isNotEmpty()) {
                Text("💡 ${data.tipsSectionTitle.ifBlank { "주의사항 & 팁" }}", fontSize = LocalAppTypography.current.menu, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                if (data.tipsSubDesc.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(data.tipsSubDesc, fontSize = LocalAppTypography.current.body, color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.7f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                validTips.forEach { box ->
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            if (box.title.isNotBlank()) {
                                Text(box.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = LocalAppTypography.current.body)
                            }
                            if (box.content.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(box.content, fontSize = LocalAppTypography.current.small, lineHeight = LocalAppTypography.current.menu, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 여행 루트 이미지 갤러리 영역
            if (data.routeSectionTitle.isNotBlank() || data.routeImageUri1.isNotBlank() || data.routeImageUri2.isNotBlank()) {
                Text("🗺️ ${data.routeSectionTitle.ifBlank { "여행 루트" }}", fontSize = LocalAppTypography.current.menu, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (data.routeImageUri1.isNotBlank()) {
                        UriImage(
                            uriString = data.routeImageUri1,
                            modifier = Modifier.weight(1f).height(120.dp).clip(RoundedCornerShape(8.dp)).clickable { onShowImage(data.routeImageUri1) }
                        )
                    }
                    if (data.routeImageUri2.isNotBlank()) {
                        UriImage(
                            uriString = data.routeImageUri2,
                            modifier = Modifier.weight(1f).height(120.dp).clip(RoundedCornerShape(8.dp)).clickable { onShowImage(data.routeImageUri2) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 예산 파이 차트 및 운전 거리 막대 차트 호출
            BudgetVisualizerSection(data)
            Spacer(modifier = Modifier.height(24.dp))
            DistanceVisualizerSection(data)
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

/** * 여행 예산을 원형 차트(파이 차트)로 시각화하여 보여주는 컴포저블
 * Compose Canvas를 사용하여 직접 호를 그립니다. 터치 시 해당 영역의 금액을 Toast로 띄웁니다.
 */
@Composable
fun BudgetVisualizerSection(data: BasicInfoData) {
    val context = LocalContext.current
    val lodging = parseSafeFloat(data.budgetLodging)
    val transport = parseSafeFloat(data.budgetTransport)
    val food = parseSafeFloat(data.budgetFood)
    val other = parseSafeFloat(data.budgetOther)

    val total = lodging + transport + food + other
    val colors = listOf(Color(0xFFF2A65A), Color(0xFF5D9CEC), Color(0xFF66CDAA), Color(0xFFAAAEB6))
    val values = listOf(lodging, transport, food, other)
    val sliceLabels = listOf("숙박", "교통", "식비", "기타")

    Text("💰 예상 비용 분석", fontSize = LocalAppTypography.current.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
    Spacer(modifier = Modifier.height(16.dp))

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("가족 총 현지 비용", fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.menu, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))

            // 차트 범례 표시
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                LegendItem(colors[0], "숙박")
                Spacer(modifier = Modifier.width(8.dp))
                LegendItem(colors[1], "교통")
                Spacer(modifier = Modifier.width(8.dp))
                LegendItem(colors[2], "식비")
                Spacer(modifier = Modifier.width(8.dp))
                LegendItem(colors[3], "기타")
            }
            Spacer(modifier = Modifier.height(24.dp))

            // 파이 차트 드로잉 영역
            if (total > 0) {
                Canvas(modifier = Modifier.size(200.dp).pointerInput(values, total) {
                    detectTapGestures { offset ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dx = offset.x - cx
                        val dy = offset.y - cy
                        val angle = ((atan2(dy.toDouble(), dx.toDouble()) * 180f / PI) + 90f + 360f) % 360f

                        var currentAngle = 0f
                        for (i in values.indices) {
                            if (values[i] > 0f) {
                                val sweepAngle = (values[i] / total) * 360f
                                if (angle >= currentAngle && angle < currentAngle + sweepAngle) {
                                    Toast.makeText(context, "${sliceLabels[i]}: 약 ${formatCurrency(values[i])}", Toast.LENGTH_SHORT).show()
                                    break
                                }
                                currentAngle += sweepAngle
                            }
                        }
                    }
                }) {
                    var startAngle = -90f
                    val strokeWidth = 40.dp.toPx()
                    values.forEachIndexed { index, value ->
                        if (value > 0) {
                            val sweepAngle = (value / total) * 360f
                            drawArc(
                                color = colors[index],
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                                size = Size(size.width, size.height)
                            )
                            startAngle += sweepAngle
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.size(200.dp).clip(RoundedCornerShape(100.dp)).background(Color.LightGray), contentAlignment = Alignment.Center) {
                    Text("데이터 없음", color = Color.DarkGray, fontSize = LocalAppTypography.current.body)
                }
            }
            Spacer(modifier = Modifier.height(32.dp))

            // 각 예산 항목별 텍스트 리스트
            BudgetListItem("🏨", "숙박", data.budgetLodging)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            BudgetListItem("🚗", "교통", data.budgetTransport)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            BudgetListItem("🍳", "식비", data.budgetFood)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            BudgetListItem("🎟️", "기타", data.budgetOther)
            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("총 예상 비용", fontSize = LocalAppTypography.current.body, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha=0.7f))
                    Text("약 ${formatCurrency(total)}", fontSize = LocalAppTypography.current.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }
    }
}

/** 차트 상단 범례 아이템 UI */
@Composable
fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(16.dp, 8.dp).background(color))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, fontSize = LocalAppTypography.current.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 예산 리스트 아이템 UI */
@Composable
fun BudgetListItem(icon: String, title: String, amount: String) {
    val displayAmount = if(amount.isBlank() || amount == "0") "0" else amount
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("$icon $title", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = LocalAppTypography.current.body)
        Text("약 $displayAmount", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = LocalAppTypography.current.body)
    }
}

/** * 일자별 운전 거리를 막대 그래프로 시각화하여 보여주는 컴포저블
 * 데이터가 없으면 전체 영역이 화면에 렌더링되지 않도록 조기 종료합니다.
 */
@Composable
fun DistanceVisualizerSection(data: BasicInfoData) {
    val context = LocalContext.current
    val labels = data.distanceLabels.split(Regex("[,，]")).map { it.trim() }.filter { it.isNotBlank() }
    val values = data.distances.split(Regex("[,，]")).map { parseSafeFloat(it) }

    // [수정됨] 데이터가 하나도 입력되지 않았다면 차트 영역 전체를 숨김
    if (labels.isEmpty() || values.isEmpty()) {
        return
    }

    Text("📏 일자별 운전 거리", fontSize = LocalAppTypography.current.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
    Spacer(modifier = Modifier.height(16.dp))

    val zipCount = minOf(labels.size, values.size)
    val safeLabels = labels.take(zipCount)
    val safeValues = values.take(zipCount)
    val maxVal = safeValues.maxOrNull()?.coerceAtLeast(10f) ?: 10f
    val yAxisMax = (maxVal * 1.15f).toInt()

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(modifier = Modifier.padding(16.dp).height(280.dp)) {

            // Y축 라벨 (최대값 기준 분할)
            Column(modifier = Modifier.width(32.dp).fillMaxHeight(0.75f).padding(end = 4.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                Text("$yAxisMax", fontSize = LocalAppTypography.current.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${(yAxisMax * 0.75).toInt()}", fontSize = LocalAppTypography.current.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${(yAxisMax * 0.5).toInt()}", fontSize = LocalAppTypography.current.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${(yAxisMax * 0.25).toInt()}", fontSize = LocalAppTypography.current.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("0", fontSize = LocalAppTypography.current.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // 가로 기준선 그리기
                Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.75f)) {
                    for (i in 0..4) {
                        val y = size.height * (i / 4f)
                        drawLine(color = Color.LightGray.copy(alpha = 0.5f), start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1f)
                    }
                }

                // 막대 그래프 매핑
                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    safeLabels.zip(safeValues).forEach { (label, value) ->
                        Column(modifier = Modifier.fillMaxHeight().weight(1f).clickable {
                            Toast.makeText(context, "$label: ${value}km", Toast.LENGTH_SHORT).show()
                        }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(modifier = Modifier.weight(0.75f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                                val heightRatio = if (yAxisMax > 0) (value / yAxisMax).coerceIn(0.01f, 1f) else 0f
                                val barColor = if (value >= 350) Color(0xFFF26B6B) else Color(0xFF4CB172) // 350km 이상이면 경고성 빨간색 표시
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight(heightRatio)
                                        .width(26.dp)
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(barColor)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            // X축 라벨 비스듬히 출력
                            Box(modifier = Modifier.weight(0.25f), contentAlignment = Alignment.TopCenter) {
                                Text(
                                    text = label,
                                    fontSize = LocalAppTypography.current.small,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.rotate(-45f).offset(x = (-8).dp, y = 8.dp),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 다중 화폐 금액을 한 줄에 나란히 보여주는 UI 유틸리티 (가계부 등에서 사용) */
@Composable
fun MultiCurrencyDisplay(
    amtInC1: Float, c1: String, r1: Float, c2: String, r2: Float, c3: String, r3: Float,
    mainSize: TextUnit, subSize: TextUnit
) {
    Column(horizontalAlignment = Alignment.End) {
        Text("${formatCurrency(amtInC1)} $c1", fontWeight = FontWeight.Bold, fontSize = mainSize, color = MaterialTheme.colorScheme.primary)
        Row {
            if (c2.isNotBlank()) Text("${formatCurrency(amtInC1 * (r2 / r1))} $c2", fontSize = subSize, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (c2.isNotBlank() && c3.isNotBlank()) Text(" | ", fontSize = subSize, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (c3.isNotBlank()) Text("${formatCurrency(amtInC1 * (r3 / r1))} $c3", fontSize = subSize, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** * [국가 탭 2] 날씨/환율
 * 실시간 Open-Meteo 날씨 정보(온도, 비, 일출/일몰, 달 위상 등) 조회 및
 * 실시간 ER-API 환율 자동 계산 기능을 제공합니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserWeatherExchangeTab(dbHelper: DatabaseHelper, countryId: Int) {
    val context = LocalContext.current
    val savedCountryJson = dbHelper.getCountryInfo(countryId, true)
    var countryData by remember { mutableStateOf(try { if (savedCountryJson.isNotEmpty()) gson.fromJson(savedCountryJson, BasicInfoData::class.java) ?: BasicInfoData() else BasicInfoData() } catch(e: Exception) { BasicInfoData() }) }
    val regions = remember { dbHelper.getRegionsByCountry(countryId) }

    var selectedRegion by remember { mutableStateOf<DatabaseHelper.RegionItem?>(regions.firstOrNull()) }
    var expandedRegion by remember { mutableStateOf(false) }
    var isFetchingWeather by remember { mutableStateOf(false) }
    var isFetchingRate by remember { mutableStateOf(false) }
    var weatherSyncTrigger by remember { mutableIntStateOf(0) }

    val c1 = countryData.currencies.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "KRW"
    val c2 = countryData.currencies.getOrNull(1)?.takeIf { it.isNotBlank() } ?: ""
    val c3 = countryData.currencies.getOrNull(2)?.takeIf { it.isNotBlank() } ?: ""

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(16.dp)) {

        // --- 날씨 섹션 ---
        Text("🌤️ 날씨 정보", fontSize = LocalAppTypography.current.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(8.dp))

        if (regions.isEmpty()) {
            Text("등록된 지역이 없어 날씨를 조회할 수 없습니다.", color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.6f), fontSize = LocalAppTypography.current.body)
        } else {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ExposedDropdownMenuBox(expanded = expandedRegion, onExpandedChange = { expandedRegion = !expandedRegion }, modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = selectedRegion?.name ?: "지역 선택", onValueChange = {}, readOnly = true, label = { Text("조회할 지역") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedRegion) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = expandedRegion, onDismissRequest = { expandedRegion = false }) {
                        regions.forEach { reg ->
                            DropdownMenuItem(text = { Text(reg.name) }, onClick = { selectedRegion = reg; expandedRegion = false })
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                // 날씨 동기화 버튼 (백그라운드 통신)
                IconButton(
                    onClick = {
                        if (selectedRegion != null) {
                            isFetchingWeather = true
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val queryName = selectedRegion!!.weatherName.takeIf { it.isNotBlank() } ?: selectedRegion!!.name
                                    val geoUrl = java.net.URL("https://geocoding-api.open-meteo.com/v1/search?name=$queryName&count=1")
                                    val geoConn = geoUrl.openConnection() as java.net.HttpURLConnection
                                    val geoResp = geoConn.inputStream.bufferedReader().readText()
                                    val results = org.json.JSONObject(geoResp).optJSONArray("results")

                                    if (results != null && results.length() > 0) {
                                        val lat = results.getJSONObject(0).getDouble("latitude")
                                        val lon = results.getJSONObject(0).getDouble("longitude")

                                        val weatherUrl = java.net.URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,precipitation_probability&hourly=temperature_2m,precipitation_probability,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min,uv_index_max,sunrise,sunset&timezone=auto")
                                        val wConn = weatherUrl.openConnection() as java.net.HttpURLConnection
                                        val wResp = wConn.inputStream.bufferedReader().readText()
                                        val wJson = org.json.JSONObject(wResp)

                                        val aqiUrl = java.net.URL("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon&current=pm10,pm2_5")
                                        val aConn = aqiUrl.openConnection() as java.net.HttpURLConnection
                                        val aResp = aConn.inputStream.bufferedReader().readText()
                                        val aJson = org.json.JSONObject(aResp)

                                        val curW = wJson.getJSONObject("current")
                                        val curA = aJson.getJSONObject("current")
                                        val hourlyW = wJson.getJSONObject("hourly")
                                        val dailyW = wJson.getJSONObject("daily")

                                        val newCache = WeatherCacheData(
                                            syncTime = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date()),
                                            currentTemp = curW.optDouble("temperature_2m").toString(),
                                            weatherCode = curW.optInt("weather_code"),
                                            rainProb = curW.optInt("precipitation_probability", 0).toString(),
                                            pm10 = curA.optDouble("pm10", 0.0).toString(),
                                            pm25 = curA.optDouble("pm2_5", 0.0).toString(),
                                            uvIndex = dailyW.getJSONArray("uv_index_max").optDouble(0, 0.0).toString()
                                        )

                                        val hList = mutableListOf<HourlyWeather>()
                                        val hTimes = hourlyW.getJSONArray("time")
                                        val hTemps = hourlyW.getJSONArray("temperature_2m")
                                        val hCodes = hourlyW.getJSONArray("weather_code")
                                        val hRains = hourlyW.getJSONArray("precipitation_probability")
                                        for (i in 0 until minOf(72, hTimes.length())) {
                                            hList.add(HourlyWeather(hTimes.getString(i).substringAfter("T"), hTemps.getString(i), hCodes.getInt(i), hRains.getString(i)))
                                        }
                                        newCache.hourly = hList

                                        val dList = mutableListOf<DailyWeather>()
                                        val dDates = dailyW.getJSONArray("time")
                                        val dMax = dailyW.getJSONArray("temperature_2m_max")
                                        val dMin = dailyW.getJSONArray("temperature_2m_min")
                                        val dCodes = dailyW.getJSONArray("weather_code")
                                        val dSunrise = dailyW.optJSONArray("sunrise")
                                        val dSunset = dailyW.optJSONArray("sunset")

                                        for (i in 0 until dDates.length()) {
                                            val fullD = dDates.getString(i)
                                            val dDate = fullD.substring(5)
                                            val sr = dSunrise?.optString(i, "")?.substringAfter("T") ?: ""
                                            val ss = dSunset?.optString(i, "")?.substringAfter("T") ?: ""

                                            // 달 위상을 바탕으로 월출/월몰 시간을 추정하여 채움 (API 400 에러 방지)
                                            val mr = getApproxMoonTime(sr, fullD)
                                            val ms = getApproxMoonTime(ss, fullD)

                                            dList.add(DailyWeather(dDate, fullD, dMin.getString(i), dMax.getString(i), dCodes.getInt(i), sr, ss, mr, ms))
                                        }
                                        newCache.daily = dList

                                        val rJsonStr = dbHelper.getRegionData(selectedRegion!!.id)
                                        val rData = try { if (rJsonStr.isNotEmpty()) gson.fromJson(rJsonStr, RegionData::class.java) ?: RegionData() else RegionData() } catch(e:Exception){ RegionData() }
                                        rData.weatherCache = gson.toJson(newCache)
                                        dbHelper.updateRegionData(selectedRegion!!.id, gson.toJson(rData))

                                        withContext(Dispatchers.Main) {
                                            isFetchingWeather = false
                                            weatherSyncTrigger++
                                            Toast.makeText(context, "날씨 정보 업데이트 완료", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        withContext(Dispatchers.Main) { isFetchingWeather=false; Toast.makeText(context, "위치를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show() }
                                    }
                                } catch(e: Exception) {
                                    withContext(Dispatchers.Main) { isFetchingWeather=false; Toast.makeText(context, "날씨 업데이트 실패", Toast.LENGTH_SHORT).show() }
                                }
                            }
                        }
                    },
                    modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50))
                ) {
                    if (isFetchingWeather) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    else Icon(Icons.Default.Sync, contentDescription = "동기화", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // 날씨 정보 캐시 파싱 및 렌더링
            if (selectedRegion != null) {
                val rJsonStr = remember(selectedRegion!!.id, weatherSyncTrigger) { dbHelper.getRegionData(selectedRegion!!.id) }
                val rData = try { if (rJsonStr.isNotEmpty()) gson.fromJson(rJsonStr, RegionData::class.java) ?: RegionData() else RegionData() } catch(e:Exception){ RegionData() }

                if (rData.weatherCache.isBlank()) {
                    Text("우측의 동기화 버튼을 눌러 날씨를 가져오세요.", color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.6f), fontSize = LocalAppTypography.current.body)
                } else {
                    val weather = try { gson.fromJson(rData.weatherCache, WeatherCacheData::class.java) } catch(e:Exception){ WeatherCacheData() }

                    Text("마지막 업데이트: ${weather.syncTime}", fontSize = LocalAppTypography.current.small, color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.6f), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${getWeatherEmoji(weather.weatherCode)} ${weather.currentTemp}°C", fontSize = LocalAppTypography.current.title * 1.5f, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("☔ 강수", fontSize = LocalAppTypography.current.small); Text("${weather.rainProb}%", fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.body) }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("☀️ UV", fontSize = LocalAppTypography.current.small); Text(weather.uvIndex, fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.body) }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("😷 PM10", fontSize = LocalAppTypography.current.small); Text(weather.pm10, fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.body) }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("😷 PM2.5", fontSize = LocalAppTypography.current.small); Text(weather.pm25, fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.body) }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("⏳ 시간대별 예보 (3일)", fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.body)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(modifier = Modifier.fillMaxWidth()) {
                        items(weather.hourly) { h ->
                            Card(modifier = Modifier.padding(end=8.dp).width(60.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(h.time, fontSize = LocalAppTypography.current.small)
                                    Text(getWeatherEmoji(h.code), fontSize = LocalAppTypography.current.title)
                                    Text("${h.temp}°C", fontSize = LocalAppTypography.current.body, fontWeight = FontWeight.Bold)
                                    Text("☔${h.rainProb}%", fontSize = LocalAppTypography.current.small, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("📅 주간 예보 (7일)", fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.body)
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            weather.daily.forEach { d ->
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(d.date, modifier = Modifier.weight(1f), fontSize = LocalAppTypography.current.body)
                                    Text(getWeatherEmoji(d.code), fontSize = LocalAppTypography.current.menu, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                    Text("${d.minTemp}°C / ${d.maxTemp}°C", modifier = Modifier.weight(1f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.body)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("🌅 일출/일몰 & 달의 위상 (7일)", fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.body)
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            weather.daily.forEach { d ->
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(d.date, modifier = Modifier.weight(1f), fontSize = LocalAppTypography.current.body, fontWeight = FontWeight.Bold)
                                    Column(modifier = Modifier.weight(2.5f)) {
                                        Text("☀️ 일출 ${d.sunrise} | 일몰 ${d.sunset}", fontSize = LocalAppTypography.current.small)
                                        val mr = if(d.moonrise.isNotBlank()) "월출 ${d.moonrise}" else "월출 없음"
                                        val ms = if(d.moonset.isNotBlank()) "월몰 ${d.moonset}" else "월몰 없음"
                                        Text("${getMoonPhaseEmoji(d.fullDate)} $mr | $ms", fontSize = LocalAppTypography.current.small)
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(thickness = 2.dp)
        Spacer(modifier = Modifier.height(24.dp))

        // --- 환율 섹션 ---
        Text("💱 환율 정보", fontSize = LocalAppTypography.current.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("환율을 수동으로 입력하거나 온라인에서 가져옵니다.", fontSize = LocalAppTypography.current.body, color = MaterialTheme.colorScheme.onSurfaceVariant)

                var focusedIndex by remember { mutableIntStateOf(0) }
                Spacer(modifier = Modifier.height(16.dp))

                Row {
                    OutlinedTextField(value = countryData.exchangeRate1, onValueChange = { countryData = countryData.copy(exchangeRate1 = it) }, label = { Text(c1, fontSize = LocalAppTypography.current.small) }, modifier = Modifier.weight(1f).onFocusChanged { if (it.isFocused) focusedIndex = 0 }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Spacer(modifier = Modifier.width(4.dp))
                    OutlinedTextField(value = countryData.exchangeRate2, onValueChange = { countryData = countryData.copy(exchangeRate2 = it) }, label = { Text(c2, fontSize = LocalAppTypography.current.small) }, modifier = Modifier.weight(1f).onFocusChanged { if (it.isFocused) focusedIndex = 1 }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Spacer(modifier = Modifier.width(4.dp))
                    OutlinedTextField(value = countryData.exchangeRate3, onValueChange = { countryData = countryData.copy(exchangeRate3 = it) }, label = { Text(c3, fontSize = LocalAppTypography.current.small) }, modifier = Modifier.weight(1f).onFocusChanged { if (it.isFocused) focusedIndex = 2 }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    // 입력 포커스가 있는 화폐를 기준으로 환율 자동 계산 통신
                    Button(
                        onClick = {
                            val r1 = countryData.exchangeRate1.toFloatOrNull() ?: 0f
                            val r2 = countryData.exchangeRate2.toFloatOrNull() ?: 0f
                            val r3 = countryData.exchangeRate3.toFloatOrNull() ?: 0f

                            var baseCur: String? = null
                            var baseVal = 0f

                            when (focusedIndex) {
                                0 -> if (c1.isNotBlank()) { baseCur = c1; baseVal = r1 }
                                1 -> if (c2.isNotBlank()) { baseCur = c2; baseVal = r2 }
                                2 -> if (c3.isNotBlank()) { baseCur = c3; baseVal = r3 }
                            }

                            if (baseCur != null && baseVal > 0f) {
                                isFetchingRate = true
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        val url = java.net.URL("https://open.er-api.com/v6/latest/$baseCur")
                                        val connection = url.openConnection() as java.net.HttpURLConnection
                                        val response = connection.inputStream.bufferedReader().readText()
                                        val rates = org.json.JSONObject(response).getJSONObject("rates")
                                        val df = java.text.DecimalFormat("#.####", java.text.DecimalFormatSymbols(java.util.Locale.US))

                                        var newR1 = countryData.exchangeRate1
                                        var newR2 = countryData.exchangeRate2
                                        var newR3 = countryData.exchangeRate3

                                        if (c1.isNotBlank() && baseCur != c1 && rates.has(c1)) newR1 = df.format(baseVal * rates.getDouble(c1))
                                        if (c2.isNotBlank() && baseCur != c2 && rates.has(c2)) newR2 = df.format(baseVal * rates.getDouble(c2))
                                        if (c3.isNotBlank() && baseCur != c3 && rates.has(c3)) newR3 = df.format(baseVal * rates.getDouble(c3))

                                        withContext(Dispatchers.Main) {
                                            countryData = countryData.copy(exchangeRate1 = newR1, exchangeRate2 = newR2, exchangeRate3 = newR3)
                                            dbHelper.updateCountryInfo(countryId, true, gson.toJson(countryData))
                                            isFetchingRate = false
                                            Toast.makeText(context, "$baseCur 기준으로 자동 계산 완료!", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch(e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            isFetchingRate = false
                                            Toast.makeText(context, "환율 가져오기 실패.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            } else {
                                Toast.makeText(context, "현재 선택된 기준 화폐에 0이 아닌 금액을 입력하세요.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !isFetchingRate,
                        modifier = Modifier.weight(1f)
                    ) { Text("온라인 갱신", fontSize = LocalAppTypography.current.body) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            dbHelper.updateCountryInfo(countryId, true, gson.toJson(countryData))
                            Toast.makeText(context, "환율 정보가 저장되었습니다.", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("수동 저장", fontSize = LocalAppTypography.current.body) }
                }
            }
        }
    }
}

/**
 * [국가 탭 6] 가계부
 * 여행 중 지출한 금액을 일자별, 카테고리별로 모아보는 탭입니다.
 */
@Composable
fun UserAccountBookTab(dbHelper: DatabaseHelper, countryId: Int) {
    val savedJson = dbHelper.getCountryInfo(countryId, true)
    val basicInfo = try { if (savedJson.isNotEmpty()) gson.fromJson(savedJson, BasicInfoData::class.java) ?: BasicInfoData() else BasicInfoData() } catch(e: Exception) { BasicInfoData() }
    val items = basicInfo.accountItems

    val c1 = basicInfo.currencies.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "KRW"
    val c2 = basicInfo.currencies.getOrNull(1)?.takeIf { it.isNotBlank() } ?: ""
    val c3 = basicInfo.currencies.getOrNull(2)?.takeIf { it.isNotBlank() } ?: ""

    val r1 = (basicInfo.exchangeRate1).toFloatOrNull()?.takeIf { it > 0f } ?: 1f
    val r2 = (basicInfo.exchangeRate2).toFloatOrNull()?.takeIf { it > 0f } ?: 1f
    val r3 = (basicInfo.exchangeRate3).toFloatOrNull()?.takeIf { it > 0f } ?: 1f

    // 환율 변환 헬퍼 함수
    fun getRate(curr: String): Float = when(curr) { c1 -> r1; c2 -> r2; c3 -> r3; else -> 1f }
    fun toC1(amt: Float, fromCurr: String): Float = amt * (r1 / getRate(fromCurr))

    val totalInC1 = items.sumOf { toC1(parseSafeFloat(it.amount), it.currency).toDouble() }.toFloat()

    var viewMode by remember { mutableStateOf("DATE") } // "DATE" or "CATEGORY"

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (items.isEmpty()) {
            Text("등록된 가계부 내역이 없습니다.", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.6f), fontSize = LocalAppTypography.current.body)
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // 총 지출 합계 헤더
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.cardElevation(4.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("총 지출 합계", fontSize = LocalAppTypography.current.menu, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        MultiCurrencyDisplay(totalInC1, c1, r1, c2, r2, c3, r3, LocalAppTypography.current.title * 1.2f, LocalAppTypography.current.body)
                    }
                }

                // 보기 모드 전환 버튼
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
                    Button(
                        onClick = { viewMode = "DATE" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (viewMode == "DATE") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = if (viewMode == "DATE") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) { Text("일자별 보기", fontSize = LocalAppTypography.current.body) }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = { viewMode = "CATEGORY" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (viewMode == "CATEGORY") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = if (viewMode == "CATEGORY") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) { Text("분류별 보기", fontSize = LocalAppTypography.current.body) }
                }

                // 일자별/분류별 리스트 렌더링
                if (viewMode == "DATE") {
                    val groupedItems = items.groupBy { it.date }.toSortedMap()
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        groupedItems.forEach { (date, dailyList) ->
                            item {
                                Text(date, fontSize = LocalAppTypography.current.menu, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                            }
                            items(dailyList) { item ->
                                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(item.category, fontSize = LocalAppTypography.current.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(item.content, fontSize = LocalAppTypography.current.body, fontWeight = FontWeight.Bold)
                                            }
                                            val amtInC1 = toC1(parseSafeFloat(item.amount), item.currency)
                                            MultiCurrencyDisplay(amtInC1, c1, r1, c2, r2, c3, r3, LocalAppTypography.current.menu, LocalAppTypography.current.small)
                                        }
                                        if (item.details.isNotBlank()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(item.details, fontSize = LocalAppTypography.current.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                            item {
                                val dailyTotalC1 = dailyList.sumOf { toC1(parseSafeFloat(it.amount), it.currency).toDouble() }.toFloat()
                                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp, top = 4.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                    Text("일일 합계: ", fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.body)
                                    MultiCurrencyDisplay(dailyTotalC1, c1, r1, c2, r2, c3, r3, LocalAppTypography.current.body, LocalAppTypography.current.small)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                } else {
                    val groupedItems = items.groupBy { it.category.ifBlank { "미분류" } }.toSortedMap()
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        groupedItems.forEach { (cat, catList) ->
                            item {
                                Text("🏷️ $cat", fontSize = LocalAppTypography.current.menu, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                            }
                            val sortedCatList = catList.sortedWith(compareBy({ it.date }, { it.content }))
                            items(sortedCatList) { item ->
                                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(item.date, fontSize = LocalAppTypography.current.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(item.content, fontSize = LocalAppTypography.current.body, fontWeight = FontWeight.Bold)
                                            }
                                            val amtInC1 = toC1(parseSafeFloat(item.amount), item.currency)
                                            MultiCurrencyDisplay(amtInC1, c1, r1, c2, r2, c3, r3, LocalAppTypography.current.menu, LocalAppTypography.current.small)
                                        }
                                        if (item.details.isNotBlank()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(item.details, fontSize = LocalAppTypography.current.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                            item {
                                val catTotalC1 = catList.sumOf { toC1(parseSafeFloat(it.amount), it.currency).toDouble() }.toFloat()
                                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp, top = 4.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                    Text("분류 합계: ", fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.body)
                                    MultiCurrencyDisplay(catTotalC1, c1, r1, c2, r2, c3, r3, LocalAppTypography.current.body, LocalAppTypography.current.small)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
/**
 * [국가 탭 3] 지역
 * 해당 국가에 등록된 하위 지역(도시 등) 목록을 보여주고, 클릭 시 지역 상세 화면으로 이동합니다.
 */
@Composable
fun UserRegionTab(dbHelper: DatabaseHelper, countryId: Int, onRegionClick: (Int, String) -> Unit) {
    val regions = remember { dbHelper.getRegionsByCountry(countryId) }
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (regions.isEmpty()) {
            Text(
                "등록된 지역이 없습니다.",
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.6f),
                fontSize = LocalAppTypography.current.body
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(regions) { region ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onRegionClick(region.id, region.name) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            region.name,
                            modifier = Modifier.padding(16.dp),
                            fontSize = LocalAppTypography.current.menu,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * [국가 탭 4] 회화표현
 * 다국어 회화표현을 리스트로 제공하며, 클릭 시 TTS 엔진으로 텍스트를 읽어줍니다.
 */
@Composable
fun UserPhraseTab(dbHelper: DatabaseHelper, countryId: Int, ttsManager: TtsManager) {
    val context = LocalContext.current
    val savedJson = dbHelper.getCountryInfo(countryId, true)
    val basicInfo = try { if (savedJson.isNotEmpty()) gson.fromJson(savedJson, BasicInfoData::class.java) ?: BasicInfoData() else BasicInfoData() } catch(e: Exception) { BasicInfoData() }

    val lang1Tag = basicInfo.languages.getOrNull(0) ?: ""
    val lang2Tag = basicInfo.languages.getOrNull(1) ?: ""
    val lang3Tag = basicInfo.languages.getOrNull(2) ?: ""

    var categories by remember { mutableStateOf(dbHelper.getPhraseCategories(countryId)) }
    var selectedCategoryId by remember { mutableStateOf<Int?>(categories.firstOrNull()?.first) }
    var phrases by remember { mutableStateOf(selectedCategoryId?.let { dbHelper.getPhrasesByCategory(it) } ?: emptyList()) }

    var selectedLang1Voice by remember { mutableStateOf<VoiceWrapper?>(null) }
    var selectedLang2Voice by remember { mutableStateOf<VoiceWrapper?>(null) }
    var selectedLang3Voice by remember { mutableStateOf<VoiceWrapper?>(null) }

    LaunchedEffect(ttsManager.ttsEngines, lang1Tag, lang2Tag, lang3Tag) {
        withContext(Dispatchers.IO) {
            var waitCount = 0
            while (ttsManager.ttsEngines.isEmpty() && waitCount < 20) { delay(100); waitCount++ }
            val v1 = ttsManager.getAvailableVoices(Locale.forLanguageTag(lang1Tag))
            val v2 = ttsManager.getAvailableVoices(Locale.forLanguageTag(lang2Tag))
            val v3 = ttsManager.getAvailableVoices(Locale.forLanguageTag(lang3Tag))

            val savedV1 = TtsPrefs.getVoice(context, lang1Tag)
            val savedV2 = TtsPrefs.getVoice(context, lang2Tag)
            val savedV3 = TtsPrefs.getVoice(context, lang3Tag)

            withContext(Dispatchers.Main) {
                selectedLang1Voice = v1.find { it.enginePackage == savedV1?.first && it.voice.name == savedV1?.second } ?: v1.firstOrNull()
                selectedLang2Voice = v2.find { it.enginePackage == savedV2?.first && it.voice.name == savedV2?.second } ?: v2.firstOrNull()
                selectedLang3Voice = v3.find { it.enginePackage == savedV3?.first && it.voice.name == savedV3?.second } ?: v3.firstOrNull()
            }
        }
    }

    val playTts = { text: String, langIdx: Int ->
        val speed = TtsPrefs.getSpeed(context)
        when(langIdx) {
            1 -> {
                val tts = ttsManager.ttsEngines[selectedLang1Voice?.enginePackage] ?: ttsManager.tts
                tts?.setSpeechRate(speed)
                if (selectedLang1Voice != null) tts?.voice = selectedLang1Voice!!.voice else tts?.language = getTtsLocale(lang1Tag)
                tts?.speak(cleanForTts(text), TextToSpeech.QUEUE_FLUSH, null, null)
            }
            2 -> {
                val tts = ttsManager.ttsEngines[selectedLang2Voice?.enginePackage] ?: ttsManager.tts
                tts?.setSpeechRate(speed)
                if (selectedLang2Voice != null) tts?.voice = selectedLang2Voice!!.voice else tts?.language = getTtsLocale(lang2Tag)
                tts?.speak(cleanForTts(text), TextToSpeech.QUEUE_FLUSH, null, null)
            }
            3 -> {
                val loc = getTtsLocale(lang3Tag)
                val tts = ttsManager.ttsEngines[selectedLang3Voice?.enginePackage] ?: ttsManager.tts
                tts?.setSpeechRate(speed)
                if (selectedLang3Voice != null) {
                    tts?.voice = selectedLang3Voice!!.voice
                } else {
                    val availableVoices = try { tts?.voices } catch(e: Exception) { null }
                    val targetVoice = availableVoices?.find { it.locale.language == loc.language && !it.isNetworkConnectionRequired } ?: availableVoices?.find { it.locale.language == loc.language } ?: availableVoices?.find { it.locale.language.contains(loc.language) }
                    if (targetVoice != null) tts?.voice = targetVoice else tts?.language = loc
                }
                tts?.speak(cleanForTts(text), TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
    }

    LaunchedEffect(selectedCategoryId, categories) {
        phrases = selectedCategoryId?.let { dbHelper.getPhrasesByCategory(it) } ?: emptyList()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        LazyRow(modifier = Modifier.fillMaxWidth()) {
            items(categories) { (id, name) ->
                val isSelected = selectedCategoryId == id
                Button(
                    onClick = { selectedCategoryId = id },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer, contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer),
                    modifier = Modifier.padding(end = 8.dp)
                ) { Text(name, fontSize = LocalAppTypography.current.body) }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (categories.isEmpty()) {
                Text("등록된 회화 카테고리가 없습니다.", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.6f), fontSize = LocalAppTypography.current.body)
            } else if (phrases.isEmpty()) {
                Text("등록된 회화표현이 없습니다.", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.6f), fontSize = LocalAppTypography.current.body)
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(phrases) { phrase ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                if (phrase.expr1.isNotBlank()) {
                                    Text(phrase.expr1, fontSize = LocalAppTypography.current.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { playTts(phrase.expr1, 1) })
                                }
                                if (phrase.meaning.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(phrase.meaning, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.body)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                if (phrase.expr2.isNotBlank()) {
                                    val label1 = try { Locale.forLanguageTag(lang2Tag).displayName } catch(e:Exception){lang2Tag}
                                    Text("${label1}: ${phrase.expr2}", fontSize = LocalAppTypography.current.menu, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.clickable { playTts(phrase.expr2, 2) }.padding(vertical=4.dp))
                                }
                                if (phrase.expr3.isNotBlank()) {
                                    val label2 = try { Locale.forLanguageTag(lang3Tag).displayName } catch(e:Exception){lang3Tag}
                                    Text("${label2}: ${phrase.expr3}", fontSize = LocalAppTypography.current.menu, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.clickable { playTts(phrase.expr3, 3) }.padding(vertical=4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * [국가 탭 5] 유용한 정보
 * SelectionContainer를 통해 자유롭게 텍스트를 선택 및 복사할 수 있습니다.
 */
@Composable
fun UserUsefulInfoTab(dbHelper: DatabaseHelper, countryId: Int) {
    val text = remember { dbHelper.getCountryInfo(countryId, false) }
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (text.isBlank()) {
            Text("등록된 유용한 정보가 없습니다.", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.6f), fontSize = LocalAppTypography.current.body)
        } else {
            SelectionContainer {
                Text(text, fontSize = LocalAppTypography.current.body, modifier = Modifier.verticalScroll(rememberScrollState()), color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

/**
 * 다중 이미지를 가로 스크롤로 보여주는 공통 썸네일 컴포저블
 * 클릭 시 선택한 이미지의 전체 목록과 시작 인덱스를 콜백으로 전달합니다.
 */
@Composable
fun ThumbnailRow(images: List<String>, onShowMultiImage: (List<String>, Int) -> Unit) {
    if (images.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            images.forEachIndexed { index, uri ->
                UriImage(
                    uriString = uri,
                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)).clickable { onShowMultiImage(images, index) }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}

/**
 * 특정 지역(도시 등)을 선택했을 때 나타나는 하위 탭 화면 메인 컨테이너입니다.
 * 14개의 하위 탭을 HorizontalPager로 제공합니다.
 */
@Composable
fun RegionUserScreen(
    dbHelper: DatabaseHelper,
    regionId: Int,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    selectedAttraction: String?,
    onAttractionSelected: (String?) -> Unit,
    onShowImage: (String) -> Unit,
    onShowMultiImage: (List<String>, Int) -> Unit,
    onAudioGuideClick: (String) -> Unit
) {
    val tabs = listOf("상세정보", "주요일정", "숙소정보", "이동경로/지도", "여행지/지도", "맛집/지도", "주차장정보", "먹거리/기념품", "맛집정보", "가성비맛집", "추천스팟", "갤러리", "오디오가이드", "유용한정보")
    val pagerState = rememberPagerState(initialPage = selectedTabIndex) { tabs.size }
    val coroutineScope = rememberCoroutineScope()
    val savedJson = dbHelper.getRegionData(regionId)
    val regionData = try { if (savedJson.isNotEmpty()) gson.fromJson(savedJson, RegionData::class.java) ?: RegionData() else RegionData() } catch (e: Exception) { RegionData() }

    LaunchedEffect(selectedTabIndex) { if (pagerState.currentPage != selectedTabIndex) pagerState.scrollToPage(selectedTabIndex) }
    LaunchedEffect(pagerState.settledPage) { if (selectedTabIndex != pagerState.settledPage) onTabSelected(pagerState.settledPage) }

    // [다크모드 수정] 고정 색상 대신 MaterialTheme.colorScheme.background 사용
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScrollableTabRow(selectedTabIndex = pagerState.currentPage, edgePadding = 8.dp) {
            tabs.forEachIndexed { index, title -> Tab(selected = pagerState.currentPage == index, onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } }, text = { Text(title, fontSize = LocalAppTypography.current.body) }) }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.Top) { page ->
            when (page) {
                0 -> {
                    val item = regionData.detail
                    if (item.travelDates.isBlank() && item.stayDuration.isBlank() && item.summary.isBlank() && item.tips.isBlank()) {
                        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) { Text("등록된 상세정보가 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = LocalAppTypography.current.body) }
                    } else {
                        Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                if(item.travelDates.isNotBlank()) Text("일정: ${item.travelDates}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = LocalAppTypography.current.body)
                                if(item.stayDuration.isNotBlank()) Text("숙박: ${item.stayDuration}", fontSize = LocalAppTypography.current.body)
                                if(item.summary.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text("요약: ${item.summary}", fontSize = LocalAppTypography.current.body) }
                                if(item.tips.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text("팁: ${item.tips}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = LocalAppTypography.current.body) }
                            }
                        }
                    }
                }
                1 -> GenericUserList(regionData.schedules.sortedWith(compareBy({ it.date }, { it.time }, { it.content })), "등록된 일정이 없습니다.") { item ->
                    Text("${item.icon} ${item.date} ${item.time} - ${item.content}", fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.menu, color = MaterialTheme.colorScheme.primary)
                    if(item.details.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text(item.details, fontSize = LocalAppTypography.current.body) }
                    if(item.precautions.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text("⚠️ ${item.precautions}", color = MaterialTheme.colorScheme.error, fontSize = LocalAppTypography.current.small) }
                }
                2 -> {
                    val item = regionData.accommodation
                    if (item.name.isBlank() && item.address.isBlank()) {
                        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) { Text("등록된 숙소가 없습니다.", color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.6f), fontSize = LocalAppTypography.current.body) }
                    } else {
                        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(item.name, fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.menu, color = MaterialTheme.colorScheme.primary)
                                    val context = LocalContext.current
                                    Row(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                                        if(item.googleMapLink.isNotBlank()) {
                                            Text("📍 구글맵 실행", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.body, modifier = Modifier.clickable { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.googleMapLink))) } catch (e:Exception) {} })
                                            Spacer(modifier = Modifier.width(16.dp))
                                        }
                                        if(item.homepage.isNotBlank()) {
                                            Text("🌐 홈페이지 접속", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.body, modifier = Modifier.clickable { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.homepage))) } catch (e:Exception) {} })
                                        }
                                    }
                                    if(item.roomType.isNotBlank() || item.price.isNotBlank()) Text("${item.roomType} | ${item.price}", fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.body)
                                    if(item.address.isNotBlank()) Text("주소: ${item.address}", fontSize = LocalAppTypography.current.body)
                                    if(item.contact.isNotBlank()) Text("연락처: ${item.contact}", fontSize = LocalAppTypography.current.body)
                                    if(item.checkInOutTime.isNotBlank()) Text("체크인/아웃: ${item.checkInOutTime}", fontSize = LocalAppTypography.current.body)
                                    if(item.parkingAvailable.isNotBlank()) Text("주차여부: ${item.parkingAvailable}", fontSize = LocalAppTypography.current.body)
                                    if(item.roomDetails.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text("룸 상세: ${item.roomDetails}", fontSize = LocalAppTypography.current.body) }
                                    if(item.otherInfo.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text("기타정보: ${item.otherInfo}", fontSize = LocalAppTypography.current.body) }

                                    if (item.attachedFiles.isNotEmpty()) {
                                        val imageFiles = item.attachedFiles.filter { isImageFile(it) }
                                        Spacer(Modifier.height(16.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        Spacer(Modifier.height(8.dp))
                                        Text("첨부파일", fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.small, color = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.height(8.dp))
                                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                            item.attachedFiles.forEach { file ->
                                                if (isImageFile(file)) {
                                                    UriImage(
                                                        uriString = file,
                                                        modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)).clickable {
                                                            onShowMultiImage(imageFiles, imageFiles.indexOf(file).coerceAtLeast(0))
                                                        }
                                                    )
                                                } else {
                                                    val fileName = if (file.contains("_")) file.substringAfter("_") else file.substringAfterLast("/")
                                                    Card(modifier = Modifier.width(120.dp).height(80.dp).clickable { exportFileToDownloads(context, file) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                                                        Column(modifier = Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                                            Icon(Icons.Default.AttachFile, contentDescription = "파일", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Text(fileName, fontSize = LocalAppTypography.current.small, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                                        }
                                                    }
                                                }
                                                Spacer(Modifier.width(8.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> MapUserList(regionData.routes)
                4 -> MapUserList(regionData.attractions)
                5 -> MapUserList(regionData.restaurantMaps)
                6 -> ParkingUserList(regionData.parkings, onShowMultiImage)
                7 -> SimpleUserList(regionData.foods.sortedWith(compareByDescending<SimpleItem>{it.isVisited}.thenByDescending{it.isMustVisit}.thenBy{it.name}), onShowMultiImage)
                8 -> RestaurantUserList(regionData.restaurants.sortedWith(compareByDescending<RestaurantItem>{it.isVisited}.thenByDescending{it.isMustVisit}.thenBy{it.name}), onShowMultiImage)
                9 -> RestaurantUserList(regionData.cheapRestaurants.sortedWith(compareByDescending<RestaurantItem>{it.isVisited}.thenByDescending{it.isMustVisit}.thenBy{it.name}), onShowMultiImage)
                10 -> SimpleUserList(regionData.spots.sortedWith(compareByDescending<SimpleItem>{it.isVisited}.thenByDescending{it.isMustVisit}.thenBy{it.name}), onShowMultiImage)
                11 -> {
                    val allGalleryUris = regionData.galleries.map { it.imageUri }.filter { it.isNotBlank() }
                    GenericUserList(regionData.galleries, "등록된 사진이 없습니다.") { item ->
                        if(item.imageUri.isNotBlank()) {
                            UriImage(
                                uriString = item.imageUri,
                                modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(8.dp)).clickable {
                                    onShowMultiImage(allGalleryUris, allGalleryUris.indexOf(item.imageUri).coerceAtLeast(0))
                                }
                            )
                        }
                        if(item.desc.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(item.desc, fontSize = LocalAppTypography.current.body, fontWeight = FontWeight.Bold) }
                    }
                }
                12 -> AudioGuideUserList(items = regionData.audioGuides, selectedAttraction = selectedAttraction, onAttractionSelected = onAttractionSelected, onAudioGuideClick = onAudioGuideClick, onShowImage = onShowImage)
                13 -> {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        if (regionData.usefulInfo.isBlank()) {
                            Text("등록된 유용한 정보가 없습니다.", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.6f), fontSize = LocalAppTypography.current.body)
                        } else {
                            SelectionContainer {
                                Text(regionData.usefulInfo, fontSize = LocalAppTypography.current.body, modifier = Modifier.verticalScroll(rememberScrollState()), color = MaterialTheme.colorScheme.onBackground)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 지도 및 외부 링크를 연결하는 뷰를 포함한 항목을 렌더링합니다. (구글맵 웹뷰 연동) */
@Composable
fun MapUserList(items: List<MapItem>) {
    val context = LocalContext.current
    GenericUserList(items, "등록된 항목이 없습니다.") { item ->
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(item.routeDetails.ifBlank { "이름 없음" }, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = LocalAppTypography.current.menu)
            if(item.googleMapLink.isNotBlank()) {
                Text(
                    "📍 구글맵 실행", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.body,
                    modifier = Modifier.clickable { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.googleMapLink))) } catch (e:Exception) { Toast.makeText(context,"링크 오류",Toast.LENGTH_SHORT).show() } }.padding(top=4.dp)
                )
            }
        }
        if(item.googleMapEmbedLink.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            AndroidView(
                factory = { ctx ->
                    object : WebView(ctx) {
                        override fun onTouchEvent(event: android.view.MotionEvent?): Boolean {
                            when (event?.actionMasked) {
                                android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_POINTER_DOWN, android.view.MotionEvent.ACTION_MOVE -> parent?.requestDisallowInterceptTouchEvent(true)
                                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_POINTER_UP, android.view.MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
                            }
                            return super.onTouchEvent(event)
                        }
                    }.apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        setBackgroundColor(0)
                    }
                },
                update = { webView ->
                    var embedLink = item.googleMapEmbedLink.replace("http://", "https://")
                    val modifiedLink = if (embedLink.contains("<iframe", ignoreCase = true)) embedLink.replace(Regex("width=\"[^\"]*\""), "width=\"100%\"").replace(Regex("height=\"[^\"]*\""), "height=\"800\"") else "<iframe width=\"100%\" height=\"800\" frameborder=\"0\" style=\"border:0;\" src=\"$embedLink\" allowfullscreen></iframe>"
                    webView.loadDataWithBaseURL("https://www.google.com", "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\" /><style>body, html { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; } iframe { width: 100% !important; height: 800px !important; border: none !important; }</style></head><body>$modifiedLink</body></html>", "text/html", "utf-8", null)
                },
                modifier = Modifier.fillMaxWidth().height(450.dp).clip(RoundedCornerShape(8.dp))
            )
        }
    }
}

/** 주차장 리스트 UI (다중 썸네일 지원) */
@Composable
fun ParkingUserList(items: List<ParkingItem>, onShowMultiImage: (List<String>, Int) -> Unit) {
    val context = LocalContext.current
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) { Text("등록된 주차장이 없습니다.", color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.6f), fontSize = LocalAppTypography.current.body) }
    } else {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
            items(items) { item ->
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(item.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = LocalAppTypography.current.menu)
                        ThumbnailRow(item.images, onShowMultiImage)
                        if(item.googleMapLink.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("📍 구글맵 실행", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.body, modifier = Modifier.clickable { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.googleMapLink))) } catch (e:Exception) {} })
                        }
                        Text(item.address, fontSize = LocalAppTypography.current.body)
                        if(item.details.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text(item.details, fontSize = LocalAppTypography.current.body) }
                    }
                }
            }
        }
    }
}

/** 특정 관광지에 속한 오디오 가이드 리스트를 그룹핑하여 보여줍니다. */
@Composable
fun AudioGuideUserList(items: List<AudioGuideItem>, selectedAttraction: String?, onAttractionSelected: (String?) -> Unit, onAudioGuideClick: (String) -> Unit, onShowImage: (String) -> Unit) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) { Text("등록된 오디오 가이드가 없습니다.", color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.6f), fontSize = LocalAppTypography.current.body) }
        return
    }
    if (selectedAttraction == null) {
        val grouped = items.groupBy { it.attraction.ifBlank { "기타 (관광지 미지정)" } }
        val attractions = grouped.keys.sorted()
        LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
            items(attractions) { attr ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable { onAttractionSelected(attr) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(attr, fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.menu, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Text("오디오 가이드 ${grouped[attr]?.size ?: 0}개", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = LocalAppTypography.current.body)
                    }
                }
            }
        }
    } else {
        val filteredItems = items.filter { (it.attraction.ifBlank { "기타 (관광지 미지정)" }) == selectedAttraction }.sortedBy { it.sequence }
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp).clickable { onAttractionSelected(null) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                Spacer(Modifier.width(8.dp))
                Text(selectedAttraction, fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.title)
            }
            LazyColumn {
                items(filteredItems) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable { onAudioGuideClick(item.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (item.imageUri.isNotBlank()) {
                                UriImage(uriString = item.imageUri, modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)).clickable { onShowImage(item.imageUri) })
                                Spacer(modifier = Modifier.width(16.dp))
                            }
                            Text("[${item.sequence}] ${item.title}", fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.menu, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

/** * 오디오 가이드 재생 화면
 * 문장을 쪼개어 다국어 TTS를 순차적으로 재생하고, 현재 읽고 있는 문장을 노란색으로 강조합니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioGuideDetailScreen(dbHelper: DatabaseHelper, countryId: Int, regionId: Int, guideId: String, ttsManager: TtsManager, onShowImage: (String) -> Unit, onNavigateGuide: (String) -> Unit) {
    val context = LocalContext.current
    val savedJsonCountry = dbHelper.getCountryInfo(countryId, true)
    val basicInfo = try { if (savedJsonCountry.isNotEmpty()) gson.fromJson(savedJsonCountry, BasicInfoData::class.java) ?: BasicInfoData() else BasicInfoData() } catch(e: Exception) { BasicInfoData() }
    val savedJsonRegion = dbHelper.getRegionData(regionId)
    val regionData = try { if (savedJsonRegion.isNotEmpty()) gson.fromJson(savedJsonRegion, RegionData::class.java) ?: RegionData() else RegionData() } catch (e: Exception) { RegionData() }
    val item = regionData.audioGuides.find { it.id == guideId } ?: return

    val currentAttraction = item.attraction
    val sortedGuides = regionData.audioGuides.filter { it.attraction == currentAttraction }.sortedBy { it.sequence }
    val currentIndex = sortedGuides.indexOfFirst { it.id == guideId }

    val lang1Tag = basicInfo.languages.getOrNull(0)?.takeIf { it.isNotBlank() } ?: Locale.getDefault().toLanguageTag()
    val lang2Tag = basicInfo.languages.getOrNull(1)?.takeIf { it.isNotBlank() } ?: Locale.US.toLanguageTag()

    val paragraphs = remember(guideId) { item.details.split("\n") }
    val flatSentences = remember(guideId) {
        val list = mutableListOf<String>()
        paragraphs.forEach { p -> list.addAll(p.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }) }
        list
    }

    var playingIndex by remember(guideId) { mutableIntStateOf(-1) }
    var isPlaying by remember(guideId) { mutableStateOf(false) }
    var isPaused by remember(guideId) { mutableStateOf(false) }
    var ttsSpeed by remember(guideId) { mutableFloatStateOf(TtsPrefs.getSpeed(context)) }

    var lang1Voices by remember { mutableStateOf<List<VoiceWrapper>>(emptyList()) }
    var lang2Voices by remember { mutableStateOf<List<VoiceWrapper>>(emptyList()) }
    var selectedLang1Voice by remember { mutableStateOf<VoiceWrapper?>(null) }
    var selectedLang2Voice by remember { mutableStateOf<VoiceWrapper?>(null) }
    var expandedLang1 by remember { mutableStateOf(false) }
    var expandedLang2 by remember { mutableStateOf(false) }

    val currentPlayingIndex by rememberUpdatedState(playingIndex)

    LaunchedEffect(ttsManager.ttsEngines) {
        withContext(Dispatchers.IO) {
            var waitCount = 0
            while (ttsManager.ttsEngines.isEmpty() && waitCount < 20) { delay(100); waitCount++ }
            val v1 = ttsManager.getAvailableVoices(Locale.forLanguageTag(lang1Tag))
            val v2 = ttsManager.getAvailableVoices(Locale.forLanguageTag(lang2Tag))
            val savedV1 = TtsPrefs.getVoice(context, lang1Tag)
            val savedV2 = TtsPrefs.getVoice(context, lang2Tag)

            withContext(Dispatchers.Main) {
                lang1Voices = v1
                lang2Voices = v2
                selectedLang1Voice = v1.find { it.enginePackage == savedV1?.first && it.voice.name == savedV1?.second } ?: v1.firstOrNull()
                selectedLang2Voice = v2.find { it.enginePackage == savedV2?.first && it.voice.name == savedV2?.second } ?: v2.firstOrNull()
            }
        }
    }

    val speakSentenceMixed = { sentenceIdx: Int ->
        if (sentenceIdx < flatSentences.size) {
            val sentence = flatSentences[sentenceIdx]
            val parts = mutableListOf<Pair<String, Boolean>>()
            val matcher = Pattern.compile("[\\(（](.*?)[\\)）]").matcher(sentence)
            var lastEnd = 0
            while (matcher.find()) {
                if (matcher.start() > lastEnd) parts.add(Pair(sentence.substring(lastEnd, matcher.start()), false))
                parts.add(Pair(matcher.group(1) ?: "", true))
                lastEnd = matcher.end()
            }
            if (lastEnd < sentence.length) parts.add(Pair(sentence.substring(lastEnd), false))

            parts.forEachIndexed { pIdx, part ->
                val cleanText = cleanForTts(part.first).trim()
                if (cleanText.isNotEmpty()) {
                    var isSecondLang = false
                    if (part.second) {
                        val l1 = lang1Tag.lowercase()
                        val isFirstLang = when {
                            l1.contains("ko") || l1.contains("kr") -> Regex("[가-힣]").containsMatchIn(part.first)
                            l1.contains("ja") || l1.contains("jp") -> Regex("[ぁ-んァ-ン]").containsMatchIn(part.first)
                            l1.contains("zh") || l1.contains("cn") || l1.contains("tw") -> Regex("[\\u4e00-\\u9fa5]").containsMatchIn(part.first)
                            else -> !(Regex("[가-힣]").containsMatchIn(part.first) || Regex("[ぁ-んァ-ン]").containsMatchIn(part.first) || Regex("[\\u4e00-\\u9fa5]").containsMatchIn(part.first))
                        }
                        if (!isFirstLang && part.first.any { it.isLetter() }) isSecondLang = true
                    }
                    val targetTts = if(isSecondLang) ttsManager.ttsEngines[selectedLang2Voice?.enginePackage] ?: ttsManager.ttsEngines.values.firstOrNull() else ttsManager.ttsEngines[selectedLang1Voice?.enginePackage] ?: ttsManager.ttsEngines.values.firstOrNull()
                    targetTts?.setSpeechRate(ttsSpeed)

                    if(isSecondLang) {
                        if(selectedLang2Voice != null) targetTts?.voice = selectedLang2Voice!!.voice else targetTts?.language = Locale.forLanguageTag(lang2Tag)
                    } else {
                        if(selectedLang1Voice != null) targetTts?.voice = selectedLang1Voice!!.voice else targetTts?.language = Locale.forLanguageTag(lang1Tag)
                    }
                    targetTts?.speak(cleanText, TextToSpeech.QUEUE_ADD, null, "${sentenceIdx}_${pIdx}_${parts.size}")
                } else if (pIdx == parts.size - 1) {
                    ttsManager.ttsEngines.values.firstOrNull()?.speak(" ", TextToSpeech.QUEUE_ADD, null, "${sentenceIdx}_${pIdx}_${parts.size}")
                }
            }
        }
    }

    DisposableEffect(guideId) {
        ttsManager.onStartCallback = { utteranceId ->
            if (utteranceId != "preview1" && utteranceId != "preview2") {
                utteranceId.split("_").getOrNull(0)?.toIntOrNull()?.let {
                    if(it>=0) CoroutineScope(Dispatchers.Main).launch { playingIndex = it }
                }
            }
        }
        ttsManager.onDoneCallback = { utteranceId ->
            if (utteranceId != "preview1" && utteranceId != "preview2") {
                CoroutineScope(Dispatchers.Main).launch {
                    val chunks = utteranceId.split("_")
                    val sIdx = chunks.getOrNull(0)?.toIntOrNull() ?: -1
                    val pIdx = chunks.getOrNull(1)?.toIntOrNull() ?: -1
                    val totalParts = chunks.getOrNull(2)?.toIntOrNull() ?: -1

                    if (pIdx == totalParts - 1) {
                        if (isPlaying && sIdx + 1 < flatSentences.size) speakSentenceMixed(sIdx + 1)
                        else { isPlaying = false; isPaused = false; playingIndex = -1 }
                    }
                }
            }
        }
        onDispose { ttsManager.stop(); ttsManager.onStartCallback = null; ttsManager.onDoneCallback = null }
    }

    val playFrom = { index: Int ->
        if (flatSentences.isNotEmpty() && index < flatSentences.size) {
            ttsManager.stop(); isPlaying = true; isPaused = false; playingIndex = index; speakSentenceMixed(index)
        }
    }

    val togglePlayPause = {
        if (isPlaying) { ttsManager.stop(); isPlaying = false; isPaused = true }
        else { if (isPaused && playingIndex >= 0) playFrom(playingIndex) else playFrom(0) }
    }

    val stopPlaying = { isPlaying = false; isPaused = false; playingIndex = -1; ttsManager.stop() }
    val scrollState = rememberScrollState()
    var viewportHeight by remember { mutableFloatStateOf(0f) }
    var textTopOffset by remember { mutableFloatStateOf(0f) }
    var textLayoutResult by remember(guideId) { mutableStateOf<TextLayoutResult?>(null) }

    val currentAnnotatedText by rememberUpdatedState(
        buildAnnotatedString {
            var globalIdx = 0
            paragraphs.forEach { para ->
                para.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }.forEach { sentence ->
                    val start = length
                    append(sentence)
                    val end = length
                    addStringAnnotation("SENTENCE", globalIdx.toString(), start, end)

                    // [다크모드 수정] 테마 기반의 하이라이트 색상 적용
                    if (globalIdx == playingIndex && (isPlaying || isPaused)) {
                        addStyle(SpanStyle(background = MaterialTheme.colorScheme.primary, color = MaterialTheme.colorScheme.onPrimary), start, end)
                    }
                    append(" ")
                    globalIdx++
                }
                append("\n")
            }
        }
    )

    LaunchedEffect(playingIndex, textLayoutResult, viewportHeight) {
        if (playingIndex >= 0 && textLayoutResult != null && viewportHeight > 0) {
            currentAnnotatedText.getStringAnnotations("SENTENCE", 0, currentAnnotatedText.length).find { it.item.toInt() == playingIndex }?.let { annotation ->
                val relativeY = textTopOffset + textLayoutResult!!.getLineTop(textLayoutResult!!.getLineForOffset(annotation.start)) - scrollState.value
                if (relativeY > viewportHeight * 0.7f || relativeY < viewportHeight * 0.1f) {
                    scrollState.animateScrollTo((textTopOffset + textLayoutResult!!.getLineTop(textLayoutResult!!.getLineForOffset(annotation.start)) - viewportHeight * 0.1f).toInt().coerceAtLeast(0))
                }
            }
        }
    }

    // [다크모드 수정] 고정 색상 대신 MaterialTheme.colorScheme 사용
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).clickable { togglePlayPause() }) {
        Column(modifier = Modifier.fillMaxSize()) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(4.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), elevation = CardDefaults.cardElevation(2.dp)) { IconButton(onClick = { ttsSpeed = (ttsSpeed - 0.1f).coerceAtLeast(0.5f); TtsPrefs.setSpeed(context, ttsSpeed) }) { Icon(Icons.Default.FastRewind, "느리게", tint = MaterialTheme.colorScheme.onSecondaryContainer) } }
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), elevation = CardDefaults.cardElevation(2.dp)) { if (isPlaying) IconButton(onClick = togglePlayPause) { Icon(Icons.Default.Pause, "일시정지", tint = MaterialTheme.colorScheme.onSecondaryContainer) } else IconButton(onClick = togglePlayPause) { Icon(Icons.Default.PlayArrow, "재생", tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(28.dp)) } }
                        Text("속도: ${String.format(Locale.US, "%.1f", ttsSpeed)}x", fontSize = LocalAppTypography.current.body, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), elevation = CardDefaults.cardElevation(2.dp)) { IconButton(onClick = stopPlaying) { Icon(Icons.Default.Stop, "정지", tint = MaterialTheme.colorScheme.onSecondaryContainer) } }
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), elevation = CardDefaults.cardElevation(2.dp)) { IconButton(onClick = { ttsSpeed = (ttsSpeed + 0.1f).coerceAtMost(2.0f); TtsPrefs.setSpeed(context, ttsSpeed) }) { Icon(Icons.Default.FastForward, "빠르게", tint = MaterialTheme.colorScheme.onSecondaryContainer) } }
                    }
                    if (lang1Voices.size > 1 || lang2Voices.size > 1) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            if (lang1Voices.size > 1) {
                                ExposedDropdownMenuBox(expanded = expandedLang1, onExpandedChange = { expandedLang1 = !expandedLang1 }, modifier = Modifier.weight(1f)) {
                                    OutlinedTextField(
                                        value = selectedLang1Voice?.displayName ?: "기본",
                                        onValueChange = {}, readOnly = true, label = { Text("언어1", fontSize = LocalAppTypography.current.small) },
                                        textStyle = LocalTextStyle.current.copy(fontSize = LocalAppTypography.current.small),
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedLang1) },
                                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                        maxLines = 1
                                    )
                                    ExposedDropdownMenu(expanded = expandedLang1, onDismissRequest = { expandedLang1 = false }) {
                                        lang1Voices.forEach { voiceWrap ->
                                            DropdownMenuItem(text = { Text(voiceWrap.displayName, fontSize = LocalAppTypography.current.small) }, onClick = { selectedLang1Voice = voiceWrap; TtsPrefs.setVoice(context, lang1Tag, voiceWrap.enginePackage, voiceWrap.voice.name); expandedLang1 = false })
                                        }
                                    }
                                }
                            }
                            if (lang1Voices.size > 1 && lang2Voices.size > 1) Spacer(modifier = Modifier.width(8.dp))
                            if (lang2Voices.size > 1) {
                                ExposedDropdownMenuBox(expanded = expandedLang2, onExpandedChange = { expandedLang2 = !expandedLang2 }, modifier = Modifier.weight(1f)) {
                                    OutlinedTextField(
                                        value = selectedLang2Voice?.displayName ?: "기본",
                                        onValueChange = {}, readOnly = true, label = { Text("언어2(괄호)", fontSize = LocalAppTypography.current.small) },
                                        textStyle = LocalTextStyle.current.copy(fontSize = LocalAppTypography.current.small),
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedLang2) },
                                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                        maxLines = 1
                                    )
                                    ExposedDropdownMenu(expanded = expandedLang2, onDismissRequest = { expandedLang2 = false }) {
                                        lang2Voices.forEach { voiceWrap ->
                                            DropdownMenuItem(text = { Text(voiceWrap.displayName, fontSize = LocalAppTypography.current.small) }, onClick = { selectedLang2Voice = voiceWrap; TtsPrefs.setVoice(context, lang2Tag, voiceWrap.enginePackage, voiceWrap.voice.name); expandedLang2 = false })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Card(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
                Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { viewportHeight = it.size.height.toFloat() }) {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp)) {
                        if (item.imageUri.isNotBlank()) {
                            UriImage(uriString = item.imageUri, modifier = Modifier.fillMaxWidth().wrapContentHeight().clip(RoundedCornerShape(8.dp)).clickable { onShowImage(item.imageUri) }, contentScale = ContentScale.FillWidth)
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        Text("[${item.sequence}] ${item.title}", fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.title, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = currentAnnotatedText,
                            style = LocalTextStyle.current.copy(fontSize = LocalAppTypography.current.menu, lineHeight = LocalAppTypography.current.title, color = MaterialTheme.colorScheme.onSurface),
                            onTextLayout = { textLayoutResult = it },
                            modifier = Modifier.onGloballyPositioned { textTopOffset = it.positionInParent().y }.pointerInput(guideId) {
                                detectTapGestures { pos ->
                                    textLayoutResult?.let { layoutResult ->
                                        currentAnnotatedText.getStringAnnotations("SENTENCE", layoutResult.getOffsetForPosition(pos), layoutResult.getOffsetForPosition(pos)).firstOrNull()?.let { annotation ->
                                            val clickedIdx = annotation.item.toInt()
                                            if (clickedIdx == currentPlayingIndex) togglePlayPause() else playFrom(clickedIdx)
                                        }
                                    }
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                if (currentIndex > 0) Button(onClick = { stopPlaying(); onNavigateGuide(sortedGuides[currentIndex - 1].id) }) { Text("이전 가이드", fontSize = LocalAppTypography.current.body) } else Spacer(modifier = Modifier.width(8.dp))
                if (currentIndex < sortedGuides.size - 1) Button(onClick = { stopPlaying(); onNavigateGuide(sortedGuides[currentIndex + 1].id) }) { Text("다음 가이드", fontSize = LocalAppTypography.current.body) } else Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}

/** 범용 리스트 UI 템플릿 */
@Composable
fun <T> GenericUserList(items: List<T>, emptyMessage: String, itemContent: @Composable (T) -> Unit) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(emptyMessage, color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.6f), fontSize = LocalAppTypography.current.body)
        }
    } else {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
            items(items) { item ->
                Card(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        itemContent(item)
                    }
                }
            }
        }
    }
}

/** * 단순 명칭/설명 리스트 UI
 * [방문 상태 반영] ✅, 🔥 아이콘과 배경색 변경 적용, 썸네일 가로 스크롤 적용
 */
@Composable
fun SimpleUserList(items: List<SimpleItem>, onShowMultiImage: (List<String>, Int) -> Unit) {
    val context = LocalContext.current
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) { Text("등록된 항목이 없습니다.", color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.6f), fontSize = LocalAppTypography.current.body) }
    } else {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
            items(items) { item ->
                // [다크모드 수정] 테마 기반의 컨테이너 색상 적용 (isVisited -> primaryContainer, isMustVisit -> tertiaryContainer)
                val bgColor = when {
                    item.isVisited -> MaterialTheme.colorScheme.primaryContainer
                    item.isMustVisit -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.surface
                }
                val contentColor = when {
                    item.isVisited -> MaterialTheme.colorScheme.onPrimaryContainer
                    item.isMustVisit -> MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.primary
                }

                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = bgColor), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (item.isVisited) Text("✅ ", fontSize = LocalAppTypography.current.menu)
                            else if (item.isMustVisit) Text("🔥 ", fontSize = LocalAppTypography.current.menu)
                            Text(item.name, fontWeight = FontWeight.Bold, color = contentColor, fontSize = LocalAppTypography.current.menu)
                        }
                        ThumbnailRow(item.images, onShowMultiImage)
                        if (item.googleMapLink.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("📍 구글맵 실행", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.body, modifier = Modifier.clickable { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.googleMapLink))) } catch (e:Exception) {} })
                        }
                        if (item.desc.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(item.desc, fontSize = LocalAppTypography.current.body)
                        }
                    }
                }
            }
        }
    }
}

/** * 식당/맛집 특화 리스트 UI
 * [방문 상태 반영] ✅, 🔥 아이콘과 배경색 변경 적용, 썸네일 가로 스크롤 적용
 */
@Composable
fun RestaurantUserList(items: List<RestaurantItem>, onShowMultiImage: (List<String>, Int) -> Unit) {
    val context = LocalContext.current
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) { Text("등록된 식당이 없습니다.", color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.6f), fontSize = LocalAppTypography.current.body) }
    } else {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
            items(items) { item ->
                // [다크모드 수정] 테마 기반의 컨테이너 색상 적용
                val bgColor = when {
                    item.isVisited -> MaterialTheme.colorScheme.primaryContainer
                    item.isMustVisit -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.surface
                }
                val contentColor = when {
                    item.isVisited -> MaterialTheme.colorScheme.onPrimaryContainer
                    item.isMustVisit -> MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.primary
                }

                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = bgColor), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (item.isVisited) Text("✅ ", fontSize = LocalAppTypography.current.menu)
                            else if (item.isMustVisit) Text("🔥 ", fontSize = LocalAppTypography.current.menu)
                            Text(item.name, fontWeight = FontWeight.Bold, color = contentColor, fontSize = LocalAppTypography.current.menu)
                        }
                        ThumbnailRow(item.images, onShowMultiImage)
                        if (item.googleMapLink.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("📍 구글맵 실행", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.body, modifier = Modifier.clickable { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.googleMapLink))) } catch (e:Exception) {} })
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("메뉴: ${item.menu}", fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.body)
                        if (item.desc.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(item.desc, fontSize = LocalAppTypography.current.body)
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================================
// [7] 관리자 화면 (Admin Screens)
// - 앱 설정, 여행지 추가, 상세 정보 입력, 회화 번역, HTML 연동 등 관리자 기능을 담당합니다.
// =========================================================================================

/** * 앱 전역 설정 화면입니다.
 * 글꼴 크기 변경, TTS 기본 목소리 설정, 데이터 백업/복원 및 캐시/데이터 초기화 기능을 제공합니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(dbHelper: DatabaseHelper, ttsManager: TtsManager, onNavigate: (String) -> Unit, onFontUpdated: () -> Unit = {}) {
    val context = LocalContext.current
    var showDataClearDialog by remember { mutableStateOf(false) }
    var showTtsDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }
    val packageInfo = try { context.packageManager.getPackageInfo(context.packageName, 0) } catch (e: Exception) { null }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    dbHelper.close()
                    val dbFile = context.getDatabasePath("tourguide.db")
                    val imageDir = File(context.filesDir, "tour_images")
                    val filesDir = File(context.filesDir, "tour_files")
                    context.contentResolver.openOutputStream(it)?.use { os ->
                        ZipOutputStream(os).use { zos ->
                            if (dbFile.exists()) {
                                zos.putNextEntry(ZipEntry("tourguide.db"))
                                dbFile.inputStream().use { input -> input.copyTo(zos) }
                                zos.closeEntry()
                            }
                            if (imageDir.exists() && imageDir.isDirectory) {
                                imageDir.listFiles()?.forEach { file ->
                                    zos.putNextEntry(ZipEntry("tour_images/${file.name}"))
                                    file.inputStream().use { input -> input.copyTo(zos) }
                                    zos.closeEntry()
                                }
                            }
                            if (filesDir.exists() && filesDir.isDirectory) {
                                filesDir.listFiles()?.forEach { file ->
                                    zos.putNextEntry(ZipEntry("tour_files/${file.name}"))
                                    file.inputStream().use { input -> input.copyTo(zos) }
                                    zos.closeEntry()
                                }
                            }
                        }
                    }
                    withContext(Dispatchers.Main) { Toast.makeText(context, "백업이 완료되었습니다.", Toast.LENGTH_LONG).show() }
                } catch(e: Exception) { e.printStackTrace() }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    dbHelper.close()
                    val dbFile = context.getDatabasePath("tourguide.db")
                    File(dbFile.path + "-wal").delete()
                    File(dbFile.path + "-shm").delete()

                    val imageDir = File(context.filesDir, "tour_images").apply { if(!exists()) mkdirs() }
                    val filesDir = File(context.filesDir, "tour_files").apply { if(!exists()) mkdirs() }

                    context.contentResolver.openInputStream(it)?.use { ins ->
                        ZipInputStream(ins).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                if (entry.name == "tourguide.db") {
                                    dbFile.outputStream().use { out -> zis.copyTo(out) }
                                } else if (entry.name.startsWith("tour_images/")) {
                                    val fileName = entry.name.substringAfterLast("/")
                                    val file = File(imageDir, fileName)
                                    file.outputStream().use { out -> zis.copyTo(out) }
                                } else if (entry.name.startsWith("tour_files/")) {
                                    val fileName = entry.name.substringAfterLast("/")
                                    val file = File(filesDir, fileName)
                                    file.outputStream().use { out -> zis.copyTo(out) }
                                }
                                zis.closeEntry()
                                entry = zis.nextEntry
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "복원이 완료되었습니다. 앱을 재시작합니다.", Toast.LENGTH_LONG).show()
                        val intent = Intent(context, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        context.startActivity(intent)
                        Runtime.getRuntime().exit(0)
                    }
                } catch(e: Exception) { e.printStackTrace() }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        ListItem(
            headlineContent = { Text("여행지 설정", fontSize = LocalAppTypography.current.menu) },
            supportingContent = { Text("국가 및 여행지를 추가, 수정, 삭제합니다.", fontSize = LocalAppTypography.current.body) },
            modifier = Modifier.clickable { onNavigate("여행지 설정") }
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text("글꼴 크기 설정", fontSize = LocalAppTypography.current.menu) },
            supportingContent = { Text("앱 전체의 제목, 메뉴, 본문 글자 크기를 조절합니다.", fontSize = LocalAppTypography.current.body) },
            modifier = Modifier.clickable { showFontDialog = true }
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text("TTS 기본 설정", fontSize = LocalAppTypography.current.menu) },
            supportingContent = { Text("오디오 가이드의 기본 목소리와 재생 속도를 설정합니다.", fontSize = LocalAppTypography.current.body) },
            modifier = Modifier.clickable { showTtsDialog = true }
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text("데이터 백업 (내보내기)", fontSize = LocalAppTypography.current.menu) },
            supportingContent = { Text("모든 데이터와 이미지를 하나의 압축파일로 저장합니다.", fontSize = LocalAppTypography.current.body) },
            modifier = Modifier.clickable {
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                exportLauncher.launch("tourguide_backup_$timeStamp.zip")
            }
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text("데이터 복원 (가져오기)", fontSize = LocalAppTypography.current.menu) },
            supportingContent = { Text("백업된 압축파일을 불러와 앱을 복원합니다.", fontSize = LocalAppTypography.current.body) },
            modifier = Modifier.clickable {
                importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
            }
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text("앱 캐시 삭제", fontSize = LocalAppTypography.current.menu) },
            supportingContent = { Text("임시로 저장된 데이터(이미지 캐시 등)를 비웁니다.", fontSize = LocalAppTypography.current.body) },
            modifier = Modifier.clickable {
                try {
                    context.cacheDir.deleteRecursively()
                    Toast.makeText(context, "캐시가 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "캐시 삭제 실패", Toast.LENGTH_SHORT).show()
                }
            }
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text("앱 데이터 & 캐시 모두 삭제", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.menu) },
            supportingContent = { Text("등록한 모든 여행 데이터가 영구적으로 초기화됩니다.", fontSize = LocalAppTypography.current.body) },
            modifier = Modifier.clickable { showDataClearDialog = true }
        )
        HorizontalDivider()

        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "버전 정보: ${packageInfo?.versionName ?: "1.0.0"}",
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.secondary,
            fontSize = LocalAppTypography.current.body
        )
    }

    if (showFontDialog) {
        val currentSizes = FontPrefs.getSizes(context)
        var tSize by remember { mutableFloatStateOf(currentSizes.title.value) }
        var mSize by remember { mutableFloatStateOf(currentSizes.menu.value) }
        var bSize by remember { mutableFloatStateOf(currentSizes.body.value) }
        var sSize by remember { mutableFloatStateOf(currentSizes.small.value) }

        AlertDialog(
            onDismissRequest = { showFontDialog = false },
            title = { Text("글꼴 크기 설정", fontSize = LocalAppTypography.current.title) },
            text = {
                Column {
                    Text("제목용 폰트 (${String.format(Locale.US, "%.1f", tSize)}sp)", fontSize = tSize.sp)
                    Slider(value = tSize, onValueChange = { tSize = it }, valueRange = 18f..32f, steps = 27)

                    Text("메뉴용 폰트 (${String.format(Locale.US, "%.1f", mSize)}sp)", fontSize = mSize.sp)
                    Slider(value = mSize, onValueChange = { mSize = it }, valueRange = 14f..24f, steps = 19)

                    Text("목록/본문용 폰트 (${String.format(Locale.US, "%.1f", bSize)}sp)", fontSize = bSize.sp)
                    Slider(value = bSize, onValueChange = { bSize = it }, valueRange = 12f..20f, steps = 15)

                    Text("작은 글자용 폰트 (${String.format(Locale.US, "%.1f", sSize)}sp)", fontSize = sSize.sp)
                    Slider(value = sSize, onValueChange = { sSize = it }, valueRange = 8f..16f, steps = 15)
                }
            },
            confirmButton = {
                Button(onClick = {
                    FontPrefs.setSizes(context, tSize, mSize, bSize, sSize)
                    onFontUpdated()
                    showFontDialog = false
                    Toast.makeText(context, "글꼴 크기가 적용되었습니다.", Toast.LENGTH_SHORT).show()
                }) { Text("적용", fontSize = LocalAppTypography.current.body) }
            },
            dismissButton = {
                TextButton(onClick = { showFontDialog = false }) { Text("취소", fontSize = LocalAppTypography.current.body) }
            }
        )
    }

    if (showTtsDialog) {
        val firstCountryId = dbHelper.getAllCountriesWithId().firstOrNull()?.first
        val basicInfo = firstCountryId?.let { dbHelper.getCountryInfo(it, true) }?.let {
            try { gson.fromJson(it, BasicInfoData::class.java) } catch(e:Exception){null}
        } ?: BasicInfoData()

        val lang1Tag = basicInfo.languages.getOrNull(0)?.takeIf { it.isNotBlank() } ?: Locale.getDefault().toLanguageTag()
        val lang2Tag = basicInfo.languages.getOrNull(1)?.takeIf { it.isNotBlank() } ?: Locale.US.toLanguageTag()
        val lang3Tag = basicInfo.languages.getOrNull(2)?.takeIf { it.isNotBlank() } ?: Locale.US.toLanguageTag()

        var dialogSpeed by remember { mutableFloatStateOf(TtsPrefs.getSpeed(context)) }
        var l1Voices by remember { mutableStateOf<List<VoiceWrapper>>(emptyList()) }
        var l2Voices by remember { mutableStateOf<List<VoiceWrapper>>(emptyList()) }
        var l3Voices by remember { mutableStateOf<List<VoiceWrapper>>(emptyList()) }

        var selV1 by remember { mutableStateOf<VoiceWrapper?>(null) }
        var selV2 by remember { mutableStateOf<VoiceWrapper?>(null) }
        var selV3 by remember { mutableStateOf<VoiceWrapper?>(null) }

        var exp1 by remember { mutableStateOf(false) }
        var exp2 by remember { mutableStateOf(false) }
        var exp3 by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                var waitCount = 0
                while (ttsManager.ttsEngines.isEmpty() && waitCount < 20) { delay(100); waitCount++ }
                val v1 = ttsManager.getAvailableVoices(Locale.forLanguageTag(lang1Tag))
                val v2 = ttsManager.getAvailableVoices(Locale.forLanguageTag(lang2Tag))
                val v3 = ttsManager.getAvailableVoices(Locale.forLanguageTag(lang3Tag))

                val savedV1 = TtsPrefs.getVoice(context, lang1Tag)
                val savedV2 = TtsPrefs.getVoice(context, lang2Tag)
                val savedV3 = TtsPrefs.getVoice(context, lang3Tag)

                withContext(Dispatchers.Main) {
                    l1Voices = v1
                    l2Voices = v2
                    l3Voices = v3
                    selV1 = v1.find { it.enginePackage == savedV1?.first && it.voice.name == savedV1?.second } ?: v1.firstOrNull()
                    selV2 = v2.find { it.enginePackage == savedV2?.first && it.voice.name == savedV2?.second } ?: v2.firstOrNull()
                    selV3 = v3.find { it.enginePackage == savedV3?.first && it.voice.name == savedV3?.second } ?: v3.firstOrNull()
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showTtsDialog = false; ttsManager.stop() },
            title = { Text("TTS 기본 설정", fontSize = LocalAppTypography.current.title) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("재생 속도: ${String.format(Locale.US, "%.1f", dialogSpeed)}x", fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.body)
                    Slider(value = dialogSpeed, onValueChange = { dialogSpeed = it }, valueRange = 0.5f..2.0f, steps = 14)
                    Spacer(modifier = Modifier.height(16.dp))

                    if (l1Voices.size > 1) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            ExposedDropdownMenuBox(expanded = exp1, onExpandedChange = { exp1 = !exp1 }, modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = selV1?.displayName ?: "기본",
                                    onValueChange = {}, readOnly = true, label = { Text("언어1 목소리", fontSize = LocalAppTypography.current.small) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = exp1) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                    maxLines = 1
                                )
                                ExposedDropdownMenu(expanded = exp1, onDismissRequest = { exp1 = false }) {
                                    l1Voices.forEach { voiceWrap -> DropdownMenuItem(text = { Text(voiceWrap.displayName, fontSize = LocalAppTypography.current.small) }, onClick = { selV1 = voiceWrap; exp1 = false }) }
                                }
                            }
                            IconButton(onClick = {
                                val tts = ttsManager.ttsEngines[selV1?.enginePackage] ?: ttsManager.tts
                                tts?.setSpeechRate(dialogSpeed)
                                selV1?.let { tts?.voice = it.voice }
                                tts?.speak(getSampleTextForTts(lang1Tag), TextToSpeech.QUEUE_FLUSH, null, "preview1")
                            }) { Icon(Icons.Default.PlayArrow, contentDescription = "미리듣기", tint = MaterialTheme.colorScheme.primary) }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (l2Voices.size > 1) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            ExposedDropdownMenuBox(expanded = exp2, onExpandedChange = { exp2 = !exp2 }, modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = selV2?.displayName ?: "기본",
                                    onValueChange = {}, readOnly = true, label = { Text("언어2 목소리", fontSize = LocalAppTypography.current.small) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = exp2) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                    maxLines = 1
                                )
                                ExposedDropdownMenu(expanded = exp2, onDismissRequest = { exp2 = false }) {
                                    l2Voices.forEach { voiceWrap -> DropdownMenuItem(text = { Text(voiceWrap.displayName, fontSize = LocalAppTypography.current.small) }, onClick = { selV2 = voiceWrap; exp2 = false }) }
                                }
                            }
                            IconButton(onClick = {
                                val tts = ttsManager.ttsEngines[selV2?.enginePackage] ?: ttsManager.tts
                                tts?.setSpeechRate(dialogSpeed)
                                selV2?.let { tts?.voice = it.voice }
                                tts?.speak(getSampleTextForTts(lang2Tag), TextToSpeech.QUEUE_FLUSH, null, "preview2")
                            }) { Icon(Icons.Default.PlayArrow, contentDescription = "미리듣기", tint = MaterialTheme.colorScheme.primary) }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (l3Voices.size > 1) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            ExposedDropdownMenuBox(expanded = exp3, onExpandedChange = { exp3 = !exp3 }, modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = selV3?.displayName ?: "기본",
                                    onValueChange = {}, readOnly = true, label = { Text("언어3 목소리", fontSize = LocalAppTypography.current.small) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = exp3) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                    maxLines = 1
                                )
                                ExposedDropdownMenu(expanded = exp3, onDismissRequest = { exp3 = false }) {
                                    l3Voices.forEach { voiceWrap -> DropdownMenuItem(text = { Text(voiceWrap.displayName, fontSize = LocalAppTypography.current.small) }, onClick = { selV3 = voiceWrap; exp3 = false }) }
                                }
                            }
                            IconButton(onClick = {
                                val tts = ttsManager.ttsEngines[selV3?.enginePackage] ?: ttsManager.tts
                                tts?.setSpeechRate(dialogSpeed)
                                selV3?.let { tts?.voice = it.voice }
                                tts?.speak(getSampleTextForTts(lang3Tag), TextToSpeech.QUEUE_FLUSH, null, "preview3")
                            }) { Icon(Icons.Default.PlayArrow, contentDescription = "미리듣기", tint = MaterialTheme.colorScheme.primary) }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    TtsPrefs.setSpeed(context, dialogSpeed)
                    selV1?.let { TtsPrefs.setVoice(context, lang1Tag, it.enginePackage, it.voice.name) }
                    selV2?.let { TtsPrefs.setVoice(context, lang2Tag, it.enginePackage, it.voice.name) }
                    selV3?.let { TtsPrefs.setVoice(context, lang3Tag, it.enginePackage, it.voice.name) }
                    Toast.makeText(context, "설정이 저장되었습니다.", Toast.LENGTH_SHORT).show()
                    ttsManager.stop()
                    showTtsDialog = false
                }) { Text("저장", fontSize = LocalAppTypography.current.body) }
            },
            dismissButton = {
                TextButton(onClick = { ttsManager.stop(); showTtsDialog = false }) { Text("취소", fontSize = LocalAppTypography.current.body) }
            }
        )
    }

    if (showDataClearDialog) {
        AlertDialog(
            onDismissRequest = { showDataClearDialog = false },
            title = { Text("모든 데이터 초기화", fontSize = LocalAppTypography.current.title) },
            text = { Text("정말 모든 여행 데이터와 캐시를 삭제하시겠습니까?\n앱이 즉시 종료됩니다.", fontSize = LocalAppTypography.current.body) },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        try {
                            dbHelper.close()
                            context.deleteDatabase("tourguide.db")
                            context.cacheDir.deleteRecursively()
                            File(context.filesDir, "tour_images").deleteRecursively()
                            File(context.filesDir, "tour_files").deleteRecursively()
                            Toast.makeText(context, "데이터 초기화 완료. 앱을 다시 실행해주세요.", Toast.LENGTH_LONG).show()
                            (context as? Activity)?.finishAffinity()
                        } catch (e: Exception) {}
                    }
                ) { Text("전체 삭제", fontSize = LocalAppTypography.current.body) }
            },
            dismissButton = { TextButton(onClick = { showDataClearDialog = false }) { Text("취소", fontSize = LocalAppTypography.current.body) } }
        )
    }
}

/** 국가 추가 및 수정 관리자 화면 */
@Composable
fun CountrySettingScreen(dbHelper: DatabaseHelper, onCountryClick: (Int, String) -> Unit, onGoHome: () -> Unit) {
    var savedCountries by remember { mutableStateOf(dbHelper.getAllCountriesWithId()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<Int?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (savedCountries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("추가된 나라가 없습니다.", fontSize = LocalAppTypography.current.body) }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(savedCountries) { (id, name, flag) ->
                        ListItem(
                            headlineContent = { Text("$flag $name", fontSize = LocalAppTypography.current.menu) },
                            modifier = Modifier.clickable { onCountryClick(id, name) },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { editingId = id; showEditDialog = true }) { Icon(Icons.Default.Edit, "수정", tint = MaterialTheme.colorScheme.primary) }
                                    IconButton(onClick = { dbHelper.deleteCountry(name); savedCountries = dbHelper.getAllCountriesWithId() }) { Icon(Icons.Default.Delete, "삭제", tint = MaterialTheme.colorScheme.error) }
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
            FloatingActionButton(onClick = { showAddDialog = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) { Icon(Icons.Default.Add, "추가") }
        }
        Button(onClick = onGoHome, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("홈으로 이동", fontSize = LocalAppTypography.current.menu) }
    }

    if (showAddDialog) {
        CountrySelectDialog(onDismiss = { showAddDialog = false }) { n, f, c ->
            dbHelper.insertCountry(n, f, c); savedCountries = dbHelper.getAllCountriesWithId(); showAddDialog = false
        }
    }
    if (showEditDialog && editingId != null) {
        CountrySelectDialog(onDismiss = { showEditDialog = false; editingId = null }) { n, f, c ->
            dbHelper.updateCountryById(editingId!!, n, f, c); savedCountries = dbHelper.getAllCountriesWithId(); showEditDialog = false; editingId = null
        }
    }
}

/** 국가 추가 시 외부 API를 통해 국가 목록을 불러와 제공하는 다이얼로그 */
@Composable
fun CountrySelectDialog(onDismiss: () -> Unit, onCountrySelected: (String, String, String) -> Unit) {
    var countryList by remember { mutableStateOf(listOf<Triple<String, String, String>>()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val jsonArray = org.json.JSONArray(java.net.URL("https://restcountries.com/v3.1/all?fields=name,flag,cca2").openConnection().inputStream.bufferedReader().readText())
                val result = mutableListOf<Triple<String, String, String>>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    result.add(Triple(obj.getJSONObject("name").getString("common"), obj.getString("flag"), obj.getString("cca2")))
                }
                countryList = result.sortedBy { it.first }
            } catch (e: Exception) {} finally { isLoading = false }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("나라 선택", fontSize = LocalAppTypography.current.title) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("취소", fontSize = LocalAppTypography.current.body) } },
        text = {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(modifier = Modifier.height(400.dp)) {
                    items(countryList) { (n, f, c) ->
                        Text(text = "$f $n", modifier = Modifier.fillMaxWidth().clickable { onCountrySelected(n, f, c) }.padding(12.dp), fontSize = LocalAppTypography.current.body)
                        HorizontalDivider()
                    }
                }
            }
        }
    )
}

/** 국가 정보 관리를 위한 5개의 탭을 보여주는 메인 관리자 화면 */
@Composable
fun CountryDetailScreen(dbHelper: DatabaseHelper, countryId: Int, selectedTabIndex: Int, onTabSelected: (Int) -> Unit, onRegionClick: (Int, String) -> Unit) {
    val tabs = listOf("기본정보", "지역", "회화표현", "유용한정보", "가계부")
    val pagerState = rememberPagerState(initialPage = selectedTabIndex) { tabs.size }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(selectedTabIndex) { if (pagerState.currentPage != selectedTabIndex) pagerState.scrollToPage(selectedTabIndex) }
    LaunchedEffect(pagerState.settledPage) { onTabSelected(pagerState.settledPage) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = pagerState.currentPage, edgePadding = 8.dp) {
            tabs.forEachIndexed { index, title -> Tab(selected = pagerState.currentPage == index, onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } }, text = { Text(title, fontSize = LocalAppTypography.current.body) }) }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.Top) { page ->
            when (page) {
                0 -> BasicInfoForm(dbHelper, countryId)
                1 -> RegionTab(dbHelper, countryId, onRegionClick)
                2 -> PhraseTab(dbHelper, countryId)
                3 -> InfoTab(dbHelper, countryId, false)
                4 -> AdminAccountBookTab(dbHelper, countryId)
            }
        }
    }
}

/** TTS 엔진 선택을 돕는 드롭다운 컴포저블 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsLanguageDropdown(label: String, selectedTag: String, availableLocales: List<Locale>, onLocaleSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = if (selectedTag.isNotBlank()) {
        try { Locale.forLanguageTag(selectedTag).displayName } catch(e:Exception) { selectedTag }
    } else "언어 선택"

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selectedName, onValueChange = {}, readOnly = true, label = { Text(label, fontSize = LocalAppTypography.current.small) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            availableLocales.forEach { locale -> DropdownMenuItem(text = { Text(locale.displayName, fontSize = LocalAppTypography.current.body) }, onClick = { onLocaleSelected(locale.toLanguageTag()); expanded = false }) }
        }
    }
}

/** * [국가 관리 탭 1] 기본정보 폼
 * 앱 상단의 핵심 요약, 이미지 업로드, 환율 설정, 사용 언어 설정 등을 입력받아 JSON 형태로 저장합니다.
 */
@Composable
fun BasicInfoForm(dbHelper: DatabaseHelper, countryId: Int) {
    val context = LocalContext.current
    val savedJson = dbHelper.getCountryInfo(countryId, true)
    var data by remember { mutableStateOf(try { if (savedJson.isNotEmpty()) gson.fromJson(savedJson, BasicInfoData::class.java) ?: BasicInfoData() else BasicInfoData() } catch (e: Exception) { BasicInfoData() }) }
    var isEditing by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val picker1 = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { coroutineScope.launch(Dispatchers.IO) { val savedPath = saveImageToInternalStorage(context, it); withContext(Dispatchers.Main) { if (savedPath.isNotBlank()) data = data.copy(routeImageUri1 = savedPath) } } } }
    val picker2 = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { coroutineScope.launch(Dispatchers.IO) { val savedPath = saveImageToInternalStorage(context, it); withContext(Dispatchers.Main) { if (savedPath.isNotBlank()) data = data.copy(routeImageUri2 = savedPath) } } } }

    var availableLocales by remember { mutableStateOf<List<Locale>>(emptyList()) }
    DisposableEffect(Unit) {
        var ttsInstance: TextToSpeech? = null
        ttsInstance = TextToSpeech(context) { status -> if (status == TextToSpeech.SUCCESS) availableLocales = ttsInstance?.availableLanguages?.toList()?.sortedBy { it.displayName } ?: emptyList() }
        onDispose { ttsInstance?.shutdown() }
    }

    if (isEditing) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("1. 메인 헤더", fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.menu)
                    OutlinedTextField(value = data.headerMainTitle, onValueChange = { data = data.copy(headerMainTitle = it) }, label = { Text("메인 타이틀") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = data.headerSubTitle, onValueChange = { data = data.copy(headerSubTitle = it) }, label = { Text("서브 타이틀") }, modifier = Modifier.fillMaxWidth())
                    Row {
                        OutlinedTextField(value = data.headerBadge1, onValueChange = { data = data.copy(headerBadge1 = it) }, label = { Text("강조 배지 1") }, modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(value = data.headerBadge2, onValueChange = { data = data.copy(headerBadge2 = it) }, label = { Text("강조 배지 2") }, modifier = Modifier.weight(1f))
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("2. 핵심 요약 (최대 4개)", fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.menu)
                    data.summaries.forEachIndexed { index, summary ->
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("요약 ${index + 1}", color = MaterialTheme.colorScheme.primary, fontSize = LocalAppTypography.current.body)
                                Icon(Icons.Default.Delete, "삭제", modifier = Modifier.clickable { val newList = data.summaries.toMutableList(); newList.removeAt(index); data = data.copy(summaries = newList) }, tint = MaterialTheme.colorScheme.error)
                            }
                            Row {
                                OutlinedTextField(value = summary.title, onValueChange = { v -> val lst = data.summaries.toMutableList(); lst[index] = summary.copy(title=v); data = data.copy(summaries=lst) }, label = { Text("박스 제목") }, modifier = Modifier.weight(1f))
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedTextField(value = summary.value, onValueChange = { v -> val lst = data.summaries.toMutableList(); lst[index] = summary.copy(value=v); data = data.copy(summaries=lst) }, label = { Text("박스 값") }, modifier = Modifier.weight(1f))
                            }
                            OutlinedTextField(value = summary.desc, onValueChange = { v -> val lst = data.summaries.toMutableList(); lst[index] = summary.copy(desc=v); data = data.copy(summaries=lst) }, label = { Text("박스 설명") }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (data.summaries.size < 4) Button(onClick = { data = data.copy(summaries = data.summaries + SummaryBox()) }) { Text("요약 추가") }
                }
            }

            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("3. 주의사항 & 팁", fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.menu)
                    OutlinedTextField(value = data.tipsSectionTitle, onValueChange = { data = data.copy(tipsSectionTitle = it) }, label = { Text("섹션 타이틀") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = data.tipsSubDesc, onValueChange = { data = data.copy(tipsSubDesc = it) }, label = { Text("서브 설명") }, modifier = Modifier.fillMaxWidth())
                    data.tipBoxes.forEachIndexed { index, box ->
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = box.title, onValueChange = { v -> val lst = data.tipBoxes.toMutableList(); lst[index] = box.copy(title=v); data = data.copy(tipBoxes=lst) }, label = { Text("박스 ${index + 1} 제목") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = box.content, onValueChange = { v -> val lst = data.tipBoxes.toMutableList(); lst[index] = box.copy(content=v); data = data.copy(tipBoxes=lst) }, label = { Text("박스 ${index + 1} 내용 (줄바꿈 가능)") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("4. 여행루트 시각화", fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.menu)
                    OutlinedTextField(value = data.routeSectionTitle, onValueChange = { data = data.copy(routeSectionTitle = it) }, label = { Text("섹션 타이틀") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { picker1.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth()) { Text(if (data.routeImageUri1.isBlank()) "첫 번째 이미지 업로드" else "첫 번째 이미지 (업로드 완료)") }
                    Button(onClick = { picker2.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth()) { Text(if (data.routeImageUri2.isBlank()) "두 번째 이미지 업로드" else "두 번째 이미지 (업로드 완료)") }
                }
            }

            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("5. 예산 및 운전 거리", fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.menu)
                    Row {
                        OutlinedTextField(value = data.budgetLodging, onValueChange = { data = data.copy(budgetLodging = it) }, label = { Text("숙박 예산 (숫자만)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(4.dp))
                        OutlinedTextField(value = data.budgetTransport, onValueChange = { data = data.copy(budgetTransport = it) }, label = { Text("교통 예산 (숫자만)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    }
                    Row {
                        OutlinedTextField(value = data.budgetFood, onValueChange = { data = data.copy(budgetFood = it) }, label = { Text("식비 예산 (숫자만)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(4.dp))
                        OutlinedTextField(value = data.budgetOther, onValueChange = { data = data.copy(budgetOther = it) }, label = { Text("기타 예산 (숫자만)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = data.distanceLabels, onValueChange = { data = data.copy(distanceLabels = it) }, label = { Text("운전거리 라벨 (콤마 구분)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = data.distances, onValueChange = { data = data.copy(distances = it) }, label = { Text("운전 거리 값 (콤마 구분)") }, modifier = Modifier.fillMaxWidth())
                }
            }

            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("6. 회화표현 사용 언어 설정", fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.menu)
                    Spacer(modifier = Modifier.height(8.dp))
                    TtsLanguageDropdown("메인 언어(음성 가이드 기준)", data.languages.getOrNull(0)?:"", availableLocales) { tag -> val lst = data.languages.toMutableList(); if(lst.isEmpty()) lst.add(tag) else lst[0] = tag; data = data.copy(languages = lst) }
                    Spacer(modifier = Modifier.height(4.dp))
                    TtsLanguageDropdown("서브 언어 1", data.languages.getOrNull(1)?:"", availableLocales) { tag -> val lst = data.languages.toMutableList(); if(lst.size<2) lst.add(tag) else lst[1] = tag; data = data.copy(languages = lst) }
                    Spacer(modifier = Modifier.height(4.dp))
                    TtsLanguageDropdown("서브 언어 2", data.languages.getOrNull(2)?:"", availableLocales) { tag -> val lst = data.languages.toMutableList(); if(lst.size<3) lst.add(tag) else lst[2] = tag; data = data.copy(languages = lst) }
                }
            }

            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("7. 환율/가계부 화폐단위 및 환율설정", fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.menu)
                    Row {
                        data.currencies.forEachIndexed { index, curr ->
                            OutlinedTextField(value = curr, onValueChange = { v -> val lst = data.currencies.toMutableList(); lst[index] = v; data = data.copy(currencies=lst) }, label = { Text("화폐 ${index+1}") }, modifier = Modifier.weight(1f))
                            if (index < 2) Spacer(modifier = Modifier.width(4.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("환율 설정 (동등 가치의 비율 입력)", fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.body)
                    Text("예: 100 EUR = 145000 KRW = 108 USD 인 경우 각각 100, 145000, 108 입력", fontSize = LocalAppTypography.current.small, color = Color.Gray)

                    var focusedIndex by remember { mutableIntStateOf(0) }

                    Row(modifier = Modifier.padding(top=8.dp)) {
                        val c1Label = data.currencies.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "화폐1"
                        val c2Label = data.currencies.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "화폐2"
                        val c3Label = data.currencies.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "화폐3"

                        OutlinedTextField(value = data.exchangeRate1, onValueChange = { data = data.copy(exchangeRate1 = it) }, label = { Text(c1Label) }, modifier = Modifier.weight(1f).onFocusChanged { if (it.isFocused) focusedIndex = 0 }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        Spacer(modifier = Modifier.width(4.dp))
                        OutlinedTextField(value = data.exchangeRate2, onValueChange = { data = data.copy(exchangeRate2 = it) }, label = { Text(c2Label) }, modifier = Modifier.weight(1f).onFocusChanged { if (it.isFocused) focusedIndex = 1 }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        Spacer(modifier = Modifier.width(4.dp))
                        OutlinedTextField(value = data.exchangeRate3, onValueChange = { data = data.copy(exchangeRate3 = it) }, label = { Text(c3Label) }, modifier = Modifier.weight(1f).onFocusChanged { if (it.isFocused) focusedIndex = 2 }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }

                    var isFetching by remember { mutableStateOf(false) }
                    Button(
                        onClick = {
                            val c1 = data.currencies.getOrNull(0)?.takeIf { it.isNotBlank() } ?: ""
                            val c2 = data.currencies.getOrNull(1)?.takeIf { it.isNotBlank() } ?: ""
                            val c3 = data.currencies.getOrNull(2)?.takeIf { it.isNotBlank() } ?: ""
                            val r1 = data.exchangeRate1.toFloatOrNull() ?: 0f
                            val r2 = data.exchangeRate2.toFloatOrNull() ?: 0f
                            val r3 = data.exchangeRate3.toFloatOrNull() ?: 0f

                            var baseCur: String? = null; var baseVal = 0f

                            when (focusedIndex) {
                                0 -> if (c1.isNotBlank()) { baseCur = c1; baseVal = r1 }
                                1 -> if (c2.isNotBlank()) { baseCur = c2; baseVal = r2 }
                                2 -> if (c3.isNotBlank()) { baseCur = c3; baseVal = r3 }
                            }

                            if (baseCur != null && baseVal > 0f) {
                                isFetching = true
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        val url = java.net.URL("https://open.er-api.com/v6/latest/$baseCur")
                                        val connection = url.openConnection() as java.net.HttpURLConnection
                                        val response = connection.inputStream.bufferedReader().readText()
                                        val rates = org.json.JSONObject(response).getJSONObject("rates")
                                        val df = java.text.DecimalFormat("#.####", java.text.DecimalFormatSymbols(java.util.Locale.US))

                                        var newR1 = data.exchangeRate1; var newR2 = data.exchangeRate2; var newR3 = data.exchangeRate3

                                        if (c1.isNotBlank() && baseCur != c1 && rates.has(c1)) newR1 = df.format(baseVal * rates.getDouble(c1))
                                        if (c2.isNotBlank() && baseCur != c2 && rates.has(c2)) newR2 = df.format(baseVal * rates.getDouble(c2))
                                        if (c3.isNotBlank() && baseCur != c3 && rates.has(c3)) newR3 = df.format(baseVal * rates.getDouble(c3))

                                        withContext(Dispatchers.Main) {
                                            data = data.copy(exchangeRate1 = newR1, exchangeRate2 = newR2, exchangeRate3 = newR3)
                                            isFetching = false
                                            Toast.makeText(context, "$baseCur 기준으로 자동 계산 완료!", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch(e: Exception) {
                                        withContext(Dispatchers.Main) { isFetching = false; Toast.makeText(context, "환율 가져오기 실패. 직접 입력해주세요.", Toast.LENGTH_SHORT).show() }
                                    }
                                }
                            } else Toast.makeText(context, "현재 선택된 기준 화폐에 0이 아닌 금액을 입력하세요.", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.fillMaxWidth().padding(top=8.dp),
                        enabled = !isFetching
                    ) { Text(if (isFetching) "가져오는 중..." else "현재 환율 자동 가져오기 (인터넷 필요)") }
                }
            }
            Button(onClick = { dbHelper.updateCountryInfo(countryId, true, gson.toJson(data)); isEditing = false }, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) { Text("저장 완료", fontSize = LocalAppTypography.current.menu) }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("기본정보가 설정되었습니다.", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.menu)
            Spacer(modifier = Modifier.height(8.dp))
            Text("메인 타이틀: ${data.headerMainTitle}", fontSize = LocalAppTypography.current.body)
            Text("등록된 요약: ${data.summaries.size}개", fontSize = LocalAppTypography.current.body)
            Text("숙박 예산: ${data.budgetLodging}", fontSize = LocalAppTypography.current.body)
            Text("설정된 언어: ${data.languages.filter { it.isNotBlank() }.joinToString(", ")}", fontSize = LocalAppTypography.current.body)
            Text("설정된 화폐: ${data.currencies.filter { it.isNotBlank() }.joinToString(", ")}", fontSize = LocalAppTypography.current.body)
            Button(onClick = { isEditing = true }, modifier = Modifier.fillMaxWidth().padding(top=24.dp)) { Text("기본정보 수정하기", fontSize = LocalAppTypography.current.body) }
        }
    }
}

/** * [국가 관리 탭 5] 가계부 관리
 * 지출 내역을 CRUD(추가, 수정, 삭제) 할 수 있습니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAccountBookTab(dbHelper: DatabaseHelper, countryId: Int) {
    val context = LocalContext.current
    val savedJson = dbHelper.getCountryInfo(countryId, true)
    val basicInfo = try { if (savedJson.isNotEmpty()) gson.fromJson(savedJson, BasicInfoData::class.java) ?: BasicInfoData() else BasicInfoData() } catch(e: Exception) { BasicInfoData() }

    var items by remember { mutableStateOf(basicInfo.accountItems) }
    var showDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<AccountItem?>(null) }

    val configuredCurrencies = basicInfo.currencies.filter { it.isNotBlank() }
    val currencies = if (configuredCurrencies.isEmpty()) listOf("KRW") else configuredCurrencies
    val categoriesList = listOf("식당", "마트", "간식", "주유/충전", "주차", "교통", "입장료", "세금", "숙박", "기념품", "선물", "렌터카", "항공", "기타")

    val c1 = basicInfo.currencies.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "KRW"
    val c2 = basicInfo.currencies.getOrNull(1)?.takeIf { it.isNotBlank() } ?: ""
    val c3 = basicInfo.currencies.getOrNull(2)?.takeIf { it.isNotBlank() } ?: ""
    val r1 = (basicInfo.exchangeRate1).toFloatOrNull()?.takeIf { it > 0f } ?: 1f
    val r2 = (basicInfo.exchangeRate2).toFloatOrNull()?.takeIf { it > 0f } ?: 1f
    val r3 = (basicInfo.exchangeRate3).toFloatOrNull()?.takeIf { it > 0f } ?: 1f

    fun getRate(curr: String): Float = when(curr) { c1 -> r1; c2 -> r2; c3 -> r3; else -> 1f }
    fun toC1(amt: Float, fromCurr: String): Float = amt * (r1 / getRate(fromCurr))

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("등록된 가계부 내역이 없습니다.", fontSize = LocalAppTypography.current.body) }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items) { item ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${item.date} [${item.category}]", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = LocalAppTypography.current.body)
                                Row {
                                    Icon(Icons.Default.Edit, "수정", modifier = Modifier.clickable { editingItem = item; showDialog = true }.padding(end=8.dp), tint = MaterialTheme.colorScheme.primary)
                                    Icon(Icons.Default.Delete, "삭제", modifier = Modifier.clickable {
                                        val newList = items.filter { it.id != item.id }; items = newList
                                        dbHelper.updateCountryInfo(countryId, true, gson.toJson(basicInfo.copy(accountItems = newList)))
                                    }, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.content, fontSize = LocalAppTypography.current.body)
                                    if (item.details.isNotBlank()) Text(item.details, fontSize = LocalAppTypography.current.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                val amtC1 = toC1(parseSafeFloat(item.amount), item.currency)
                                MultiCurrencyDisplay(amtC1, c1, r1, c2, r2, c3, r3, LocalAppTypography.current.body, LocalAppTypography.current.small)
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { editingItem = null; showDialog = true }, modifier = Modifier.align(Alignment.BottomEnd)) { Icon(Icons.Default.Add, "추가") }
    }

    if (showDialog) {
        var date by remember { mutableStateOf(editingItem?.date ?: "") }; var category by remember { mutableStateOf(editingItem?.category ?: categoriesList[0]) }
        var expandedCategory by remember { mutableStateOf(false) }; var content by remember { mutableStateOf(editingItem?.content ?: "") }
        var details by remember { mutableStateOf(editingItem?.details ?: "") }; var amount by remember { mutableStateOf(editingItem?.amount ?: "") }
        var currency by remember { mutableStateOf(editingItem?.currency ?: currencies[0]) }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("가계부 내역 편집", fontSize = LocalAppTypography.current.title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    val cal = Calendar.getInstance()
                    OutlinedTextField(
                        value = date, onValueChange = { date = it }, label = { Text("날짜 (예: 2026/04/15)") },
                        trailingIcon = { Text("📅", modifier = Modifier.clickable { DatePickerDialog(context, { _, y, m, d -> date = String.format("%04d/%02d/%02d", y, m + 1, d) }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show() }.padding(8.dp), fontSize = LocalAppTypography.current.menu) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ExposedDropdownMenuBox(expanded = expandedCategory, onExpandedChange = { expandedCategory = !expandedCategory }) {
                        OutlinedTextField(
                            value = category, onValueChange = {}, readOnly = true, label = { Text("분류") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategory) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(expanded = expandedCategory, onDismissRequest = { expandedCategory = false }) { categoriesList.forEach { cat -> DropdownMenuItem(text = { Text(cat) }, onClick = { category = cat; expandedCategory = false }) } }
                    }
                    OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("지출 내용") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = details, onValueChange = { details = it }, label = { Text("세부내역 (선택)") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("금액") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("화폐 단위 선택", fontSize = LocalAppTypography.current.body, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp)) {
                        currencies.forEach { curr ->
                            val isSelected = currency == curr
                            Button(onClick = { currency = curr }, colors = ButtonDefaults.buttonColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer, contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer), modifier = Modifier.padding(end = 4.dp)) { Text(curr, fontSize = LocalAppTypography.current.body) }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val newItem = AccountItem(editingItem?.id ?: UUID.randomUUID().toString(), date, category, content, amount, currency, details)
                    val newList = items.toMutableList()
                    val idx = newList.indexOfFirst { it.id == newItem.id }
                    if (idx >= 0) newList[idx] = newItem else newList.add(newItem)
                    items = newList
                    dbHelper.updateCountryInfo(countryId, true, gson.toJson(basicInfo.copy(accountItems = newList)))
                    showDialog = false
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("취소") } }
        )
    }
}

/** 단순 텍스트 정보(유용한 정보)를 편집하는 관리자 탭 */
@Composable
fun InfoTab(dbHelper: DatabaseHelper, countryId: Int, isBasic: Boolean) {
    if (isBasic) {
        BasicInfoForm(dbHelper, countryId)
    } else {
        var text by remember { mutableStateOf(dbHelper.getCountryInfo(countryId, false)) }
        var isEditing by remember { mutableStateOf(false) }

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            if (isEditing) {
                OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f).fillMaxWidth())
                Button(onClick = { dbHelper.updateCountryInfo(countryId, false, text); isEditing = false }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("저장") }
            } else {
                Text(text = text.ifEmpty { "내용이 없습니다." }, modifier = Modifier.weight(1f).fillMaxWidth(), fontSize = LocalAppTypography.current.body)
                Button(onClick = { isEditing = true }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("수정하기") }
            }
        }
    }
}

/** * [국가 관리 탭 3] 회화 탭 관리자
 * 자동 번역 API 연동을 통해 한 언어만 입력해도 3가지 언어로 자동 채워지는 기능을 포함합니다.
 */
@Composable
fun PhraseTab(dbHelper: DatabaseHelper, countryId: Int) {
    val context = LocalContext.current
    val savedJson = dbHelper.getCountryInfo(countryId, true)
    val basicInfo = try { if (savedJson.isNotEmpty()) gson.fromJson(savedJson, BasicInfoData::class.java) ?: BasicInfoData() else BasicInfoData() } catch(e: Exception) { BasicInfoData() }
    val langLabels = basicInfo.languages.mapIndexed { index, lang -> lang.ifBlank { "언어 ${index + 1} (미설정)" } }

    var categories by remember { mutableStateOf(dbHelper.getPhraseCategories(countryId)) }
    var selectedCategoryId by remember { mutableStateOf<Int?>(categories.firstOrNull()?.first) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var categoryName by remember { mutableStateOf("") }
    var editingCategoryId by remember { mutableStateOf<Int?>(null) }
    var phrases by remember { mutableStateOf(selectedCategoryId?.let { dbHelper.getPhrasesByCategory(it) } ?: emptyList()) }
    var showPhraseDialog by remember { mutableStateOf(false) }
    var editingPhraseId by remember { mutableStateOf<Int?>(null) }
    var expr1 by remember { mutableStateOf("") }
    var expr2 by remember { mutableStateOf("") }
    var expr3 by remember { mutableStateOf("") }

    LaunchedEffect(selectedCategoryId, categories) {
        phrases = selectedCategoryId?.let { dbHelper.getPhrasesByCategory(it) } ?: emptyList()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("카테고리:", fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.body)
            Spacer(modifier = Modifier.width(8.dp))
            LazyRow(modifier = Modifier.weight(1f)) {
                items(categories) { (id, name) ->
                    val isSelected = selectedCategoryId == id
                    Button(
                        onClick = { selectedCategoryId = id },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer, contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer),
                        modifier = Modifier.padding(end = 8.dp)
                    ) { Text(name, fontSize = LocalAppTypography.current.body) }
                }
            }
            IconButton(onClick = { editingCategoryId = null; categoryName = ""; showCategoryDialog = true }) { Icon(Icons.Default.Add, "추가", tint = MaterialTheme.colorScheme.primary) }
        }

        if (selectedCategoryId != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { editingCategoryId = selectedCategoryId; categoryName = categories.find { it.first == selectedCategoryId }?.second ?: ""; showCategoryDialog = true }) { Text("현재 카테고리 수정", fontSize = LocalAppTypography.current.body) }
                TextButton(onClick = { dbHelper.deletePhraseCategory(selectedCategoryId!!); categories = dbHelper.getPhraseCategories(countryId); selectedCategoryId = categories.firstOrNull()?.first }) { Text("삭제", color = MaterialTheme.colorScheme.error, fontSize = LocalAppTypography.current.body) }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (categories.isEmpty()) {
                Text("상단의 '+' 버튼을 눌러 카테고리를 추가해주세요.", modifier = Modifier.align(Alignment.Center), fontSize = LocalAppTypography.current.body)
            } else if (phrases.isEmpty()) {
                Text("등록된 회화표현이 없습니다.", modifier = Modifier.align(Alignment.Center), fontSize = LocalAppTypography.current.body)
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(phrases) { phrase ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    val displayTitle = phrase.expr1.ifBlank { phrase.meaning }
                                    Text(displayTitle, fontSize = LocalAppTypography.current.menu, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Row {
                                        Icon(Icons.Default.Edit, "수정", modifier = Modifier.clickable { editingPhraseId = phrase.id; expr1 = phrase.expr1.ifBlank { phrase.meaning }; expr2 = phrase.expr2; expr3 = phrase.expr3; showPhraseDialog = true }.padding(end = 8.dp), tint = MaterialTheme.colorScheme.primary)
                                        Icon(Icons.Default.Delete, "삭제", modifier = Modifier.clickable { dbHelper.deletePhrase(phrase.id); phrases = dbHelper.getPhrasesByCategory(selectedCategoryId!!) }, tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                if (phrase.expr2.isNotBlank()) Text("${langLabels[1]}: ${phrase.expr2}", fontSize = LocalAppTypography.current.body)
                                if (phrase.expr3.isNotBlank()) Text("${langLabels[2]}: ${phrase.expr3}", fontSize = LocalAppTypography.current.body)
                            }
                        }
                    }
                }
            }
            if (selectedCategoryId != null) {
                FloatingActionButton(onClick = { editingPhraseId = null; expr1 = ""; expr2 = ""; expr3 = ""; showPhraseDialog = true }, modifier = Modifier.align(Alignment.BottomEnd)) { Icon(Icons.Default.Add, "추가") }
            }
        }
    }

    if (showCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = { Text(if (editingCategoryId == null) "카테고리 추가" else "카테고리 수정", fontSize = LocalAppTypography.current.title) },
            text = { OutlinedTextField(value = categoryName, onValueChange = { categoryName = it }, label = { Text("카테고리명 (예: 공항, 식당)") }) },
            confirmButton = {
                Button(onClick = {
                    if (categoryName.isNotBlank()) {
                        if (editingCategoryId == null) dbHelper.insertPhraseCategory(countryId, categoryName)
                        else dbHelper.updatePhraseCategory(editingCategoryId!!, categoryName)
                        categories = dbHelper.getPhraseCategories(countryId)
                        if (editingCategoryId == null) selectedCategoryId = categories.lastOrNull()?.first
                        showCategoryDialog = false
                    }
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { showCategoryDialog = false }) { Text("취소") } }
        )
    }

    // 회화 추가/수정 (자동 번역 기능 포함)
    if (showPhraseDialog && selectedCategoryId != null) {
        var isTranslating by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()

        AlertDialog(
            onDismissRequest = { showPhraseDialog = false },
            title = { Text(if (editingPhraseId == null) "회화 추가" else "회화 수정", fontSize = LocalAppTypography.current.title) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("설정된 언어에 맞춰 표현을 입력하세요.", fontSize = LocalAppTypography.current.small, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = expr1, onValueChange = { expr1 = it }, label = { Text(langLabels[0]) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = expr2, onValueChange = { expr2 = it }, label = { Text(langLabels[1]) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = expr3, onValueChange = { expr3 = it }, label = { Text(langLabels[2]) }, modifier = Modifier.fillMaxWidth())

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            isTranslating = true
                            coroutineScope.launch {
                                val baseText = expr1.takeIf { it.isNotBlank() } ?: expr2.takeIf { it.isNotBlank() } ?: expr3
                                if (baseText.isNotBlank()) {
                                    val l1 = basicInfo.languages.getOrNull(0)?.split("-")?.get(0)?.takeIf { it.isNotBlank() } ?: "ko"
                                    val l2 = basicInfo.languages.getOrNull(1)?.split("-")?.get(0)?.takeIf { it.isNotBlank() } ?: "en"
                                    val l3 = basicInfo.languages.getOrNull(2)?.split("-")?.get(0)?.takeIf { it.isNotBlank() } ?: "es"

                                    val t1 = if (expr1.isBlank()) translateText(baseText, l1) else expr1
                                    val t2 = if (expr2.isBlank()) translateText(baseText, l2) else expr2
                                    val t3 = if (expr3.isBlank()) translateText(baseText, l3) else expr3

                                    withContext(Dispatchers.Main) {
                                        if (t1.isBlank() && t2.isBlank() && t3.isBlank()) {
                                            Toast.makeText(context, "번역을 실패했습니다. 인터넷 연결을 확인하세요.", Toast.LENGTH_SHORT).show()
                                        }
                                        expr1 = t1; expr2 = t2; expr3 = t3
                                    }
                                } else {
                                    withContext(Dispatchers.Main) { Toast.makeText(context, "최소 한 가지 언어는 입력해야 번역이 가능합니다.", Toast.LENGTH_SHORT).show() }
                                }
                                withContext(Dispatchers.Main) { isTranslating = false }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isTranslating
                    ) { Text(if (isTranslating) "번역 중..." else "빈칸 자동 번역 (인터넷 필요)") }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (expr1.isBlank()) {
                        Toast.makeText(context, "첫 번째 언어(${langLabels[0]})는 필수입니다.", Toast.LENGTH_SHORT).show()
                    } else {
                        if (editingPhraseId == null) dbHelper.insertPhrase(selectedCategoryId!!, expr1, expr1, expr2, expr3)
                        else dbHelper.updatePhrase(editingPhraseId!!, expr1, expr1, expr2, expr3)
                        phrases = dbHelper.getPhrasesByCategory(selectedCategoryId!!)
                        showPhraseDialog = false
                    }
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { showPhraseDialog = false }) { Text("취소") } }
        )
    }
}

/** [지역 관리 탭 2] 하위 지역(도시)을 관리하는 화면 */
@Composable
fun RegionTab(dbHelper: DatabaseHelper, countryId: Int, onRegionClick: (Int, String) -> Unit) {
    var regions by remember { mutableStateOf(dbHelper.getRegionsByCountry(countryId)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var regionCode by remember { mutableStateOf("") }
    var regionText by remember { mutableStateOf("") }
    var weatherRegionText by remember { mutableStateOf("") }
    var editingRegionId by remember { mutableStateOf<Int?>(null) }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (regions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("등록된 지역이 없습니다.", fontSize = LocalAppTypography.current.body) }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(regions) { regionItem ->
                    ListItem(
                        headlineContent = { Text(if (regionItem.code.isNotBlank()) "[${regionItem.code}] ${regionItem.name}" else regionItem.name, fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.menu) },
                        modifier = Modifier.clickable { onRegionClick(regionItem.id, regionItem.name) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = {
                                    editingRegionId = regionItem.id; regionText = regionItem.name; regionCode = regionItem.code; weatherRegionText = regionItem.weatherName; showAddDialog = true
                                }) { Icon(Icons.Default.Edit, "수정", tint=MaterialTheme.colorScheme.primary) }
                                IconButton(onClick = { dbHelper.deleteRegion(regionItem.id); regions = dbHelper.getRegionsByCountry(countryId) }) { Icon(Icons.Default.Delete, "삭제", tint=MaterialTheme.colorScheme.error) }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
        FloatingActionButton(onClick = { editingRegionId = null; regionText = ""; regionCode = ""; weatherRegionText = ""; showAddDialog = true }, modifier = Modifier.align(Alignment.BottomEnd)) { Icon(Icons.Default.Add, "지역 추가") }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(if (editingRegionId == null) "지역 추가" else "지역 수정", fontSize = LocalAppTypography.current.title) },
            text = {
                Column {
                    OutlinedTextField(value = regionCode, onValueChange = { regionCode = it }, label = { Text("지역ID (예: BCN)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = regionText, onValueChange = { regionText = it }, label = { Text("지역명 (예: 바르셀로나)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = weatherRegionText, onValueChange = { weatherRegionText = it }, label = { Text("날씨용 지역명 (영문, 예: Barcelona)") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (regionText.isNotBlank()) {
                        if (editingRegionId == null) dbHelper.insertRegion(regionText, regionCode, weatherRegionText, countryId)
                        else dbHelper.updateRegion(editingRegionId!!, regionText, regionCode, weatherRegionText)
                        regions = dbHelper.getRegionsByCountry(countryId)
                        showAddDialog = false
                    }
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("취소") } }
        )
    }
}

// ------------------------------------------
// [관리자용] 지역 상세 매니저
// ------------------------------------------

/** * 특정 지역(도시)의 모든 콘텐츠를 관리하는 메인 컨테이너 화면
 * 총 14개의 탭을 관리하며 모든 데이터는 하나의 RegionData JSON으로 통합 저장됩니다.
 */
@Composable
fun RegionDetailScreen(dbHelper: DatabaseHelper, countryId: Int, regionId: Int, selectedTabIndex: Int, onTabSelected: (Int) -> Unit, onShowImage: (String) -> Unit, onShowMultiImage: (List<String>, Int) -> Unit) {
    val tabs = listOf("상세정보", "주요일정", "숙소정보", "이동경로/지도", "여행지/지도", "맛집/지도", "주차장정보", "먹거리/기념품", "맛집정보", "가성비맛집", "추천스팟", "갤러리", "오디오가이드", "유용한정보")
    val pagerState = rememberPagerState(initialPage = selectedTabIndex) { tabs.size }
    val coroutineScope = rememberCoroutineScope()
    val savedJson = dbHelper.getRegionData(regionId)
    val regionData by remember { mutableStateOf(try { if (savedJson.isNotEmpty()) gson.fromJson(savedJson, RegionData::class.java) ?: RegionData() else RegionData() } catch (e: Exception) { RegionData() }) }
    var currentData by remember { mutableStateOf(regionData) }

    val saveRegionData = { newData: RegionData -> currentData = newData; dbHelper.updateRegionData(regionId, gson.toJson(newData)) }

    LaunchedEffect(selectedTabIndex) {
        if (pagerState.currentPage != selectedTabIndex) {
            pagerState.scrollToPage(selectedTabIndex)
        }
    }
    LaunchedEffect(pagerState.settledPage) {
        if (selectedTabIndex != pagerState.settledPage) onTabSelected(pagerState.settledPage)
    }

    // [다크모드 수정] 백그라운드 색상 테마 연동
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScrollableTabRow(selectedTabIndex = pagerState.currentPage, edgePadding = 8.dp) {
            tabs.forEachIndexed { index, title -> Tab(selected = pagerState.currentPage == index, onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } }, text = { Text(title, fontSize = LocalAppTypography.current.body) }) }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) { page ->
            when (page) {
                0 -> SingleDetailManager(currentData) { saveRegionData(it) }
                1 -> ScheduleManager(currentData.schedules) { saveRegionData(currentData.copy(schedules = it)) }
                2 -> SingleAccommodationManager(currentData.accommodation, onShowMultiImage) { saveRegionData(currentData.copy(accommodation = it)) }
                3 -> MapItemManager("이동경로상세", currentData.routes) { saveRegionData(currentData.copy(routes = it)) }
                4 -> MapItemManager("이동경로상세", currentData.attractions) { saveRegionData(currentData.copy(attractions = it)) }
                5 -> MapItemManager("이동경로상세", currentData.restaurantMaps) { saveRegionData(currentData.copy(restaurantMaps = it)) }
                6 -> ParkingManager(currentData.parkings, onShowMultiImage) { saveRegionData(currentData.copy(parkings = it)) }
                7 -> SimpleItemManager("명칭", currentData.foods, onShowMultiImage) { saveRegionData(currentData.copy(foods = it)) }
                8 -> RestaurantManager(currentData.restaurants, onShowMultiImage) { saveRegionData(currentData.copy(restaurants = it)) }
                9 -> RestaurantManager(currentData.cheapRestaurants, onShowMultiImage) { saveRegionData(currentData.copy(cheapRestaurants = it)) }
                10 -> SimpleItemManager("명칭", currentData.spots, onShowMultiImage) { saveRegionData(currentData.copy(spots = it)) }
                11 -> GalleryManager(currentData.galleries, onShowMultiImage) { saveRegionData(currentData.copy(galleries = it)) }
                12 -> AudioGuideManager(dbHelper, countryId, regionId, currentData, onShowImage) { saveRegionData(it) }
                13 -> RegionUsefulInfoManager(currentData.usefulInfo) { saveRegionData(currentData.copy(usefulInfo = it)) }
            }
        }
    }
}

/** [지역 관리 탭 14] 지역별 유용한 정보 텍스트 관리 */
@Composable
fun RegionUsefulInfoManager(usefulInfo: String, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(usefulInfo) }
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("유용한 정보 (사용자 화면에서 부분 복사 가능)") },
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
        Button(
            onClick = { onSave(text) },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) { Text("저장", fontSize = LocalAppTypography.current.menu) }
    }
}

/** * [지역 관리 탭 1] 상세정보
 * 기본 일정, 요약 정보와 더불어 '맛집', '추천스팟' 리스트를 HTML 태그 방식으로 양방향 동기화합니다.
 */
@Composable
fun SingleDetailManager(regionData: RegionData, onSave: (RegionData) -> Unit) {
    val context = LocalContext.current
    var travelDates by remember { mutableStateOf(regionData.detail.travelDates) }
    var stayDuration by remember { mutableStateOf(regionData.detail.stayDuration) }
    var summary by remember { mutableStateOf(regionData.detail.summary) }
    var tips by remember { mutableStateOf(regionData.detail.tips) }

    var restaurantsHtml by remember { mutableStateOf(generateRestaurantHtml(regionData.restaurants)) }
    var cheapRestaurantsHtml by remember { mutableStateOf(generateRestaurantHtml(regionData.cheapRestaurants)) }
    var spotsHtml by remember { mutableStateOf(generateSimpleHtml(regionData.spots)) }

    LaunchedEffect(regionData) {
        travelDates = regionData.detail.travelDates
        stayDuration = regionData.detail.stayDuration
        summary = regionData.detail.summary
        tips = regionData.detail.tips
        restaurantsHtml = generateRestaurantHtml(regionData.restaurants)
        cheapRestaurantsHtml = generateRestaurantHtml(regionData.cheapRestaurants)
        spotsHtml = generateSimpleHtml(regionData.spots)
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("기본 상세 정보", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = LocalAppTypography.current.menu)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = travelDates, onValueChange = { travelDates = it }, label = { Text("여행일정") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = stayDuration, onValueChange = { stayDuration = it }, label = { Text("숙박기간") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = summary, onValueChange = { summary = it }, label = { Text("일정요약") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = tips, onValueChange = { tips = it }, label = { Text("여행팁") }, minLines = 3, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(thickness = 2.dp)
        Spacer(modifier = Modifier.height(16.dp))

        Text("추가 정보 자동 연동 (HTML)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = LocalAppTypography.current.menu)
        // [다크모드 수정] 회색 텍스트도 테마 색상으로 대체
        Text("양식: <li><strong>이름</strong> - 메뉴: 메뉴내용 - 설명내용 <a href=\"링크\">지도</a></li>", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = LocalAppTypography.current.small)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(value = restaurantsHtml, onValueChange = { restaurantsHtml = it }, label = { Text("맛집 정보 (<li>...</li>)") }, minLines = 5, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(value = cheapRestaurantsHtml, onValueChange = { cheapRestaurantsHtml = it }, label = { Text("가성비 맛집 (<li>...</li>)") }, minLines = 5, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))

        Text("양식: <li><strong>이름</strong> - 설명내용 <a href=\"링크\">지도</a></li>", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = LocalAppTypography.current.small)
        OutlinedTextField(value = spotsHtml, onValueChange = { spotsHtml = it }, label = { Text("추천 스팟 (<li>...</li>)") }, minLines = 5, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val updatedDetail = regionData.detail.copy(travelDates = travelDates, stayDuration = stayDuration, summary = summary, tips = tips)
                val parsedRestaurants = parseRestaurantHtml(restaurantsHtml, regionData.restaurants)
                val parsedCheap = parseRestaurantHtml(cheapRestaurantsHtml, regionData.cheapRestaurants)
                val parsedSpots = parseSimpleHtml(spotsHtml, regionData.spots)

                onSave(regionData.copy(
                    detail = updatedDetail,
                    restaurants = parsedRestaurants,
                    cheapRestaurants = parsedCheap,
                    spots = parsedSpots
                ))
                Toast.makeText(context, "상세정보 및 연동 데이터가 저장되었습니다.", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("상세정보 및 HTML 연동 저장", fontSize = LocalAppTypography.current.menu) }
    }
}

/** [지역 관리 탭 3] 숙소정보 관리
 * 첨부파일 최대 5개까지 등록 허용
 */
@Composable
fun SingleAccommodationManager(item: AccommodationItem, onShowMultiImage: (List<String>, Int) -> Unit, onSave: (AccommodationItem) -> Unit) {
    val context = LocalContext.current
    var n by remember { mutableStateOf(item.name) }
    var a by remember { mutableStateOf(item.address) }
    var c by remember { mutableStateOf(item.contact) }
    var h by remember { mutableStateOf(item.homepage) }
    var g by remember { mutableStateOf(item.googleMapLink) }
    var pa by remember { mutableStateOf(item.parkingAvailable) }
    var rt by remember { mutableStateOf(item.roomType) }
    var p by remember { mutableStateOf(item.price) }
    var rd by remember { mutableStateOf(item.roomDetails) }
    var ci by remember { mutableStateOf(item.checkInOutTime) }
    var oi by remember { mutableStateOf(item.otherInfo) }
    var attachedFiles by remember { mutableStateOf(item.attachedFiles) }

    val coroutineScope = rememberCoroutineScope()
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                val savedPath = saveFileToInternalStorage(context, it)
                if (savedPath.isNotBlank()) {
                    withContext(Dispatchers.Main) {
                        val newList = attachedFiles.toMutableList()
                        if (newList.size < 5) {
                            newList.add(savedPath)
                            attachedFiles = newList
                        } else {
                            Toast.makeText(context, "최대 5개까지만 첨부 가능합니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        OutlinedTextField(value = n, onValueChange = { n = it }, label = { Text("숙소명") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = a, onValueChange = { a = it }, label = { Text("주소") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = c, onValueChange = { c = it }, label = { Text("연락처") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = h, onValueChange = { h = it }, label = { Text("홈페이지") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = g, onValueChange = { g = it }, label = { Text("구글맵 링크") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = pa, onValueChange = { pa = it }, label = { Text("주차장 여부") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = rt, onValueChange = { rt = it }, label = { Text("룸타입") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = p, onValueChange = { p = it }, label = { Text("가격") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = ci, onValueChange = { ci = it }, label = { Text("체크인/아웃") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = rd, onValueChange = { rd = it }, label = { Text("룸상세") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = oi, onValueChange = { oi = it }, label = { Text("기타정보") }, minLines = 3, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(16.dp))
        Text("첨부파일 (최대 5개)", fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.body)

        val imageFiles = attachedFiles.filter { isImageFile(it) }
        attachedFiles.forEachIndexed { index, file ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                if (isImageFile(file)) {
                    UriImage(
                        uriString = file,
                        modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)).clickable {
                            onShowMultiImage(imageFiles, imageFiles.indexOf(file).coerceAtLeast(0))
                        }
                    )
                } else {
                    val fileName = if (file.contains("_")) file.substringAfter("_") else file.substringAfterLast("/")
                    Text("📄 $fileName", modifier = Modifier.weight(1f).padding(end = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = LocalAppTypography.current.body)
                }
                IconButton(onClick = {
                    val newList = attachedFiles.toMutableList()
                    newList.removeAt(index)
                    attachedFiles = newList
                }) { Icon(Icons.Default.Delete, "삭제", tint = MaterialTheme.colorScheme.error) }
            }
        }
        if (attachedFiles.size < 5) {
            Button(onClick = { filePicker.launch("*/*") }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text("파일 첨부하기") }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                onSave(item.copy(name=n, address=a, contact=c, homepage=h, googleMapLink=g, parkingAvailable=pa, roomType=rt, price=p, roomDetails=rd, checkInOutTime=ci, otherInfo=oi, attachedFiles=attachedFiles))
                Toast.makeText(context, "숙소정보가 저장되었습니다.", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("숙소정보 저장", fontSize = LocalAppTypography.current.menu) }
    }
}

/** [지역 관리 탭 2] 주요일정 관리 */
@Composable
fun ScheduleManager(items: List<ScheduleItem>, onSave: (List<ScheduleItem>) -> Unit) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ScheduleItem?>(null) }
    val sortedItems = items.sortedWith(compareBy({ it.date }, { it.time }, { it.content }))

    Box(modifier = Modifier.fillMaxSize()) {
        if (sortedItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("등록된 일정이 없습니다.", fontSize = LocalAppTypography.current.body) }
        } else {
            LazyColumn {
                items(sortedItems) { item ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${item.icon} ${item.date} ${item.time} - ${item.content}", fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.body)
                                Row {
                                    Icon(Icons.Default.Edit, "수정", modifier = Modifier.clickable { editingItem = item; showDialog = true }.padding(end=8.dp), tint = MaterialTheme.colorScheme.primary)
                                    Icon(Icons.Default.Delete, "삭제", modifier = Modifier.clickable { onSave(items.filter { it.id != item.id }) }, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            if (item.details.isNotBlank()) Text(item.details, fontSize = LocalAppTypography.current.body, modifier = Modifier.padding(top=4.dp))
                            if (item.precautions.isNotBlank()) Text("주의: ${item.precautions}", fontSize = LocalAppTypography.current.small, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top=4.dp))
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { editingItem = null; showDialog = true }, modifier = Modifier.align(Alignment.BottomEnd)) { Icon(Icons.Default.Add, "추가") }
    }

    if (showDialog) {
        var date by remember { mutableStateOf(editingItem?.date ?: "") }
        var time by remember { mutableStateOf(editingItem?.time ?: "") }
        var icon by remember { mutableStateOf(editingItem?.icon ?: "") }
        var content by remember { mutableStateOf(editingItem?.content ?: "") }
        var details by remember { mutableStateOf(editingItem?.details ?: "") }
        var precautions by remember { mutableStateOf(editingItem?.precautions ?: "") }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("주요일정 편집", fontSize = LocalAppTypography.current.title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    val cal = Calendar.getInstance()
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("날짜 (예: 2026/04/15)") },
                        trailingIcon = {
                            Text("📅", modifier = Modifier.clickable {
                                DatePickerDialog(context, { _, y, m, d -> date = String.format("%04d/%02d/%02d", y, m + 1, d) }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                            }.padding(8.dp), fontSize = 20.sp)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = time,
                        onValueChange = { time = it },
                        label = { Text("시간 (예: 14:00)") },
                        trailingIcon = {
                            Text("⏰", modifier = Modifier.clickable {
                                TimePickerDialog(context, { _, h, m -> time = String.format("%02d:%02d", h, m) }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
                            }.padding(8.dp), fontSize = 20.sp)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(value = icon, onValueChange = { icon = it }, label = { Text("아이콘 (이모지 ✈️ 등)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("내용") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = details, onValueChange = { details = it }, label = { Text("상세설명") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = precautions, onValueChange = { precautions = it }, label = { Text("주의사항") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    val newItem = ScheduleItem(editingItem?.id ?: UUID.randomUUID().toString(), date, time, icon, content, details, precautions)
                    val newList = items.toMutableList()
                    val idx = newList.indexOfFirst { it.id == newItem.id }
                    if (idx >= 0) newList[idx] = newItem else newList.add(newItem)
                    onSave(newList); showDialog = false
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("취소") } }
        )
    }
}

/** * [지역 관리 탭 공용] 지도 기반 항목 (경로, 여행지 등) 관리
 * 구글맵 HTML 임베디드 코드를 입력받을 수 있습니다.
 */
@Composable
fun MapItemManager(detailLabel: String, items: List<MapItem>, onSave: (List<MapItem>) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<MapItem?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("등록된 항목이 없습니다.", fontSize = LocalAppTypography.current.body) }
        } else {
            LazyColumn {
                items(items) { item ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(item.routeDetails.ifBlank { "이름 없음" }, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), fontSize = LocalAppTypography.current.body)
                                Row {
                                    Icon(Icons.Default.Edit, "수정", modifier = Modifier.clickable { editingItem = item; showDialog = true }.padding(end=8.dp), tint = MaterialTheme.colorScheme.primary)
                                    Icon(Icons.Default.Delete, "삭제", modifier = Modifier.clickable { onSave(items.filter { it.id != item.id }) }, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { editingItem = null; showDialog = true }, modifier = Modifier.align(Alignment.BottomEnd)) { Icon(Icons.Default.Add, "추가") }
    }

    if (showDialog) {
        var rd by remember { mutableStateOf(editingItem?.routeDetails ?: "") }
        var gml by remember { mutableStateOf(editingItem?.googleMapLink ?: "") }
        var gmel by remember { mutableStateOf(editingItem?.googleMapEmbedLink ?: "") }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("지도/경로 편집", fontSize = LocalAppTypography.current.title) },
            text = {
                Column {
                    OutlinedTextField(value = rd, onValueChange = { rd = it }, label = { Text(detailLabel) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = gml, onValueChange = { gml = it }, label = { Text("구글맵 링크") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = gmel, onValueChange = { gmel = it }, label = { Text("구글맵 Embeded 링크") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    val newItem = MapItem(editingItem?.id ?: UUID.randomUUID().toString(), rd, gml, gmel)
                    val newList = items.toMutableList()
                    val idx = newList.indexOfFirst { it.id == newItem.id }
                    if (idx >= 0) newList[idx] = newItem else newList.add(newItem)
                    onSave(newList); showDialog = false
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("취소") } }
        )
    }
}

/** * [지역 관리 탭 7] 주차장 정보 관리
 * 다중 이미지 첨부 기능 추가
 */
@Composable
fun ParkingManager(items: List<ParkingItem>, onShowMultiImage: (List<String>, Int) -> Unit, onSave: (List<ParkingItem>) -> Unit) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ParkingItem?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("등록된 주차장이 없습니다.", fontSize = LocalAppTypography.current.body) }
        } else {
            LazyColumn {
                items(items) { item ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(item.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), fontSize = LocalAppTypography.current.body)
                                Row {
                                    Icon(Icons.Default.Edit, "수정", modifier = Modifier.clickable { editingItem = item; showDialog = true }.padding(end=8.dp), tint = MaterialTheme.colorScheme.primary)
                                    Icon(Icons.Default.Delete, "삭제", modifier = Modifier.clickable { onSave(items.filter { it.id != item.id }) }, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            if (item.images.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                    item.images.forEachIndexed { index, uri ->
                                        UriImage(uriString = uri, modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)).clickable { onShowMultiImage(item.images, index) })
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(item.address, fontSize = LocalAppTypography.current.body)
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { editingItem = null; showDialog = true }, modifier = Modifier.align(Alignment.BottomEnd)) { Icon(Icons.Default.Add, "추가") }
    }

    if (showDialog) {
        var n by remember { mutableStateOf(editingItem?.name ?: "") }
        var a by remember { mutableStateOf(editingItem?.address ?: "") }
        var gml by remember { mutableStateOf(editingItem?.googleMapLink ?: "") }
        var d by remember { mutableStateOf(editingItem?.details ?: "") }
        var images by remember { mutableStateOf(editingItem?.images ?: emptyList()) }

        val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let {
                coroutineScope.launch(Dispatchers.IO) {
                    val savedPath = saveImageToInternalStorage(context, it)
                    withContext(Dispatchers.Main) { if (savedPath.isNotBlank()) images = images + savedPath }
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("주차장 편집", fontSize = LocalAppTypography.current.title) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(value = n, onValueChange = { n = it }, label = { Text("주차장명") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = a, onValueChange = { a = it }, label = { Text("주소") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = gml, onValueChange = { gml = it }, label = { Text("구글맵 링크") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = d, onValueChange = { d = it }, label = { Text("상세정보") }, minLines = 3, modifier = Modifier.fillMaxWidth())

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("이미지 첨부 (${images.size}장)", fontSize = LocalAppTypography.current.small, color = Color.Gray)
                    if (images.isNotEmpty()) {
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp)) {
                            images.forEachIndexed { index, uri ->
                                Box {
                                    UriImage(uriString = uri, modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)))
                                    IconButton(onClick = { val mut = images.toMutableList(); mut.removeAt(index); images = mut }, modifier = Modifier.align(Alignment.TopEnd).size(20.dp)) {
                                        Icon(Icons.Default.Close, "삭제", tint = Color.Red)
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                        }
                    }
                    Button(onClick = { imagePicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("이미지 추가") }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val newItem = ParkingItem(editingItem?.id ?: UUID.randomUUID().toString(), n, a, gml, d, images)
                    val newList = items.toMutableList()
                    val idx = newList.indexOfFirst { it.id == newItem.id }
                    if (idx >= 0) newList[idx] = newItem else newList.add(newItem)
                    onSave(newList); showDialog = false
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("취소") } }
        )
    }
}

/** * [지역 관리 공통] 단순 아이템 관리 (먹거리/기념품, 스팟 등 HTML 동기화 가능 대상)
 * 다중 이미지 첨부 및 방문상태(isMustVisit, isVisited) 체크박스 추가
 */
@Composable
fun SimpleItemManager(nameLabel: String, items: List<SimpleItem>, onShowMultiImage: (List<String>, Int) -> Unit, onSave: (List<SimpleItem>) -> Unit) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<SimpleItem?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("등록된 항목이 없습니다.", fontSize = LocalAppTypography.current.body) }
        } else {
            LazyColumn {
                items(items) { item ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    if (item.isVisited) Text("✅ ", fontSize = LocalAppTypography.current.menu)
                                    else if (item.isMustVisit) Text("🔥 ", fontSize = LocalAppTypography.current.menu)
                                    Text(item.name, fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.body)
                                }
                                Row {
                                    Icon(Icons.Default.Edit, "수정", modifier = Modifier.clickable { editingItem = item; showDialog = true }.padding(end=8.dp), tint = MaterialTheme.colorScheme.primary)
                                    Icon(Icons.Default.Delete, "삭제", modifier = Modifier.clickable { onSave(items.filter { it.id != item.id }) }, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            if (item.images.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                    item.images.forEachIndexed { index, uri ->
                                        UriImage(uriString = uri, modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)).clickable { onShowMultiImage(item.images, index) })
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { editingItem = null; showDialog = true }, modifier = Modifier.align(Alignment.BottomEnd)) { Icon(Icons.Default.Add, "추가") }
    }

    if (showDialog) {
        var n by remember { mutableStateOf(editingItem?.name ?: "") }
        var d by remember { mutableStateOf(editingItem?.desc ?: "") }
        var gml by remember { mutableStateOf(editingItem?.googleMapLink ?: "") }
        var images by remember { mutableStateOf(editingItem?.images ?: emptyList()) }
        var isMustVisit by remember { mutableStateOf(editingItem?.isMustVisit ?: false) }
        var isVisited by remember { mutableStateOf(editingItem?.isVisited ?: false) }

        val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let {
                coroutineScope.launch(Dispatchers.IO) {
                    val savedPath = saveImageToInternalStorage(context, it)
                    withContext(Dispatchers.Main) { if (savedPath.isNotBlank()) images = images + savedPath }
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("항목 편집", fontSize = LocalAppTypography.current.title) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isMustVisit, onCheckedChange = { isMustVisit = it })
                        Text("🔥 꼭 가고싶은 곳", fontSize = LocalAppTypography.current.small)
                        Spacer(Modifier.width(8.dp))
                        Checkbox(checked = isVisited, onCheckedChange = { isVisited = it })
                        Text("✅ 방문 완료", fontSize = LocalAppTypography.current.small)
                    }
                    OutlinedTextField(value = n, onValueChange = { n = it }, label = { Text(nameLabel) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = gml, onValueChange = { gml = it }, label = { Text("구글맵 링크") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = d, onValueChange = { d = it }, label = { Text("설명") }, minLines = 3, modifier = Modifier.fillMaxWidth())

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("이미지 첨부 (${images.size}장)", fontSize = LocalAppTypography.current.small, color = Color.Gray)
                    if (images.isNotEmpty()) {
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp)) {
                            images.forEachIndexed { index, uri ->
                                Box {
                                    UriImage(uriString = uri, modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)))
                                    IconButton(onClick = { val mut = images.toMutableList(); mut.removeAt(index); images = mut }, modifier = Modifier.align(Alignment.TopEnd).size(20.dp)) {
                                        Icon(Icons.Default.Close, "삭제", tint = Color.Red)
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                        }
                    }
                    Button(onClick = { imagePicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("이미지 추가") }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val newItem = SimpleItem(editingItem?.id ?: UUID.randomUUID().toString(), n, d, gml, images, isMustVisit, isVisited)
                    val newList = items.toMutableList()
                    val idx = newList.indexOfFirst { it.id == newItem.id }
                    if (idx >= 0) newList[idx] = newItem else newList.add(newItem)
                    onSave(newList); showDialog = false
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("취소") } }
        )
    }
}

/** * [지역 관리 공통] 식당 관리 (맛집, 가성비 맛집)
 * 다중 이미지 첨부 및 방문상태(isMustVisit, isVisited) 체크박스 추가
 */
@Composable
fun RestaurantManager(items: List<RestaurantItem>, onShowMultiImage: (List<String>, Int) -> Unit, onSave: (List<RestaurantItem>) -> Unit) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<RestaurantItem?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("등록된 맛집이 없습니다.", fontSize = LocalAppTypography.current.body) }
        } else {
            LazyColumn {
                items(items) { item ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    if (item.isVisited) Text("✅ ", fontSize = LocalAppTypography.current.menu)
                                    else if (item.isMustVisit) Text("🔥 ", fontSize = LocalAppTypography.current.menu)
                                    Text(item.name, fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.body)
                                }
                                Row {
                                    Icon(Icons.Default.Edit, "수정", modifier = Modifier.clickable { editingItem = item; showDialog = true }.padding(end=8.dp), tint = MaterialTheme.colorScheme.primary)
                                    Icon(Icons.Default.Delete, "삭제", modifier = Modifier.clickable { onSave(items.filter { it.id != item.id }) }, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            if (item.images.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                    item.images.forEachIndexed { index, uri ->
                                        UriImage(uriString = uri, modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)).clickable { onShowMultiImage(item.images, index) })
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                }
                            }
                            Text("메뉴: ${item.menu}", fontSize = LocalAppTypography.current.body, modifier = Modifier.padding(top=4.dp))
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { editingItem = null; showDialog = true }, modifier = Modifier.align(Alignment.BottomEnd)) { Icon(Icons.Default.Add, "추가") }
    }

    if (showDialog) {
        var n by remember { mutableStateOf(editingItem?.name ?: "") }
        var d by remember { mutableStateOf(editingItem?.desc ?: "") }
        var m by remember { mutableStateOf(editingItem?.menu ?: "") }
        var gml by remember { mutableStateOf(editingItem?.googleMapLink ?: "") }
        var images by remember { mutableStateOf(editingItem?.images ?: emptyList()) }
        var isMustVisit by remember { mutableStateOf(editingItem?.isMustVisit ?: false) }
        var isVisited by remember { mutableStateOf(editingItem?.isVisited ?: false) }

        val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let {
                coroutineScope.launch(Dispatchers.IO) {
                    val savedPath = saveImageToInternalStorage(context, it)
                    withContext(Dispatchers.Main) { if (savedPath.isNotBlank()) images = images + savedPath }
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("맛집 편집", fontSize = LocalAppTypography.current.title) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isMustVisit, onCheckedChange = { isMustVisit = it })
                        Text("🔥 꼭 가고싶은 곳", fontSize = LocalAppTypography.current.small)
                        Spacer(Modifier.width(8.dp))
                        Checkbox(checked = isVisited, onCheckedChange = { isVisited = it })
                        Text("✅ 방문 완료", fontSize = LocalAppTypography.current.small)
                    }
                    OutlinedTextField(value = n, onValueChange = { n = it }, label = { Text("가게명") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = m, onValueChange = { m = it }, label = { Text("메뉴") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = gml, onValueChange = { gml = it }, label = { Text("구글맵 링크") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = d, onValueChange = { d = it }, label = { Text("설명") }, minLines = 3, modifier = Modifier.fillMaxWidth())

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("이미지 첨부 (${images.size}장)", fontSize = LocalAppTypography.current.small, color = Color.Gray)
                    if (images.isNotEmpty()) {
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp)) {
                            images.forEachIndexed { index, uri ->
                                Box {
                                    UriImage(uriString = uri, modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)))
                                    IconButton(onClick = { val mut = images.toMutableList(); mut.removeAt(index); images = mut }, modifier = Modifier.align(Alignment.TopEnd).size(20.dp)) {
                                        Icon(Icons.Default.Close, "삭제", tint = Color.Red)
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                        }
                    }
                    Button(onClick = { imagePicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("이미지 추가") }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val newItem = RestaurantItem(editingItem?.id ?: UUID.randomUUID().toString(), n, d, m, gml, images, isMustVisit, isVisited)
                    val newList = items.toMutableList()
                    val idx = newList.indexOfFirst { it.id == newItem.id }
                    if (idx >= 0) newList[idx] = newItem else newList.add(newItem)
                    onSave(newList); showDialog = false
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("취소") } }
        )
    }
}

/** [지역 관리 탭 12] 갤러리 이미지 관리 */
@Composable
fun GalleryManager(items: List<GalleryItem>, onShowMultiImage: (List<String>, Int) -> Unit, onSave: (List<GalleryItem>) -> Unit) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<GalleryItem?>(null) }
    var tempImageUri by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                val savedPath = saveImageToInternalStorage(context, it)
                withContext(Dispatchers.Main) { tempImageUri = savedPath }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("등록된 사진이 없습니다.", fontSize = LocalAppTypography.current.body) }
        } else {
            val allGalleryUris = items.map { it.imageUri }.filter { it.isNotBlank() }

            LazyColumn {
                items(items) { item ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (item.imageUri.isNotBlank()) {
                                        UriImage(
                                            uriString = item.imageUri,
                                            modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)).clickable {
                                                onShowMultiImage(allGalleryUris, allGalleryUris.indexOf(item.imageUri).coerceAtLeast(0))
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(if (item.imageUri.isNotBlank()) "이미지 첨부됨" else "이미지 없음", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = LocalAppTypography.current.body)
                                }
                                Row {
                                    Icon(Icons.Default.Edit, "수정", modifier = Modifier.clickable { editingItem = item; tempImageUri = item.imageUri; showDialog = true }.padding(end=8.dp), tint = MaterialTheme.colorScheme.primary)
                                    Icon(Icons.Default.Delete, "삭제", modifier = Modifier.clickable { onSave(items.filter { it.id != item.id }) }, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            if (item.desc.isNotBlank()) Text(item.desc, fontSize = LocalAppTypography.current.body, modifier = Modifier.padding(top=4.dp))
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { editingItem = null; tempImageUri = ""; showDialog = true }, modifier = Modifier.align(Alignment.BottomEnd)) { Icon(Icons.Default.Add, "추가") }
    }

    if (showDialog) {
        var d by remember { mutableStateOf(editingItem?.desc ?: "") }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("사진 편집", fontSize = LocalAppTypography.current.title) },
            text = {
                Column {
                    Button(onClick = { imagePicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (tempImageUri.isBlank()) "사진 업로드" else "사진 첨부됨", fontSize = LocalAppTypography.current.body)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = d, onValueChange = { d = it }, label = { Text("설명") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    val newItem = GalleryItem(editingItem?.id ?: UUID.randomUUID().toString(), tempImageUri, d)
                    val newList = items.toMutableList()
                    val idx = newList.indexOfFirst { it.id == newItem.id }
                    if (idx >= 0) newList[idx] = newItem else newList.add(newItem)
                    onSave(newList); showDialog = false
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("취소") } }
        )
    }
}

/** * [지역 관리 탭 13] 오디오 가이드 관리
 * 특정 관광지 단위로 하위 가이드를 생성/수정하며, 필요시 관광지 그룹 전체를 다른 지역(도시)로 이동시킬 수 있습니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioGuideManager(dbHelper: DatabaseHelper, countryId: Int, regionId: Int, regionData: RegionData, onShowImage: (String) -> Unit, onSave: (RegionData) -> Unit) {
    val context = LocalContext.current
    // 관광지 그룹 목록 추출
    var attractions by remember { mutableStateOf(regionData.audioAttractions.toMutableList().apply {
        regionData.audioGuides.forEach { if (it.attraction.isNotBlank() && !contains(it.attraction)) add(it.attraction) }
    }) }
    var selectedAttraction by remember { mutableStateOf<String?>(attractions.firstOrNull()) }

    var showAttrDialog by remember { mutableStateOf(false) }
    var attrName by remember { mutableStateOf("") }
    var editingAttr by remember { mutableStateOf<String?>(null) }

    var showGuideDialog by remember { mutableStateOf(false) }
    var editingGuide by remember { mutableStateOf<AudioGuideItem?>(null) }
    var seqString by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var tempImageUri by remember { mutableStateOf("") }
    var guideAttraction by remember { mutableStateOf("") }
    var expandedAttractionMenu by remember { mutableStateOf(false) }

    var showMoveDialog by remember { mutableStateOf(false) }
    var targetRegionId by remember { mutableStateOf<Int?>(null) }
    var regionsList by remember { mutableStateOf(listOf<DatabaseHelper.RegionItem>()) }

    val coroutineScope = rememberCoroutineScope()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                val savedPath = saveImageToInternalStorage(context, it)
                withContext(Dispatchers.Main) { tempImageUri = savedPath }
            }
        }
    }

    LaunchedEffect(regionData) {
        val newAttrs = regionData.audioAttractions.toMutableList()
        regionData.audioGuides.forEach { if (it.attraction.isNotBlank() && !newAttrs.contains(it.attraction)) newAttrs.add(it.attraction) }
        attractions = newAttrs
        if (selectedAttraction != null && !newAttrs.contains(selectedAttraction)) {
            selectedAttraction = newAttrs.firstOrNull()
        } else if (selectedAttraction == null) {
            selectedAttraction = newAttrs.firstOrNull()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("관광지:", fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.body)
            Spacer(modifier = Modifier.width(8.dp))
            LazyRow(modifier = Modifier.weight(1f)) {
                items(attractions) { attr ->
                    val isSelected = selectedAttraction == attr
                    Button(
                        onClick = { selectedAttraction = attr },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer, contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer),
                        modifier = Modifier.padding(end = 8.dp)
                    ) { Text(attr, fontSize = LocalAppTypography.current.body) }
                }
            }
            IconButton(onClick = { editingAttr = null; attrName = ""; showAttrDialog = true }) { Icon(Icons.Default.Add, "추가", tint = MaterialTheme.colorScheme.primary) }
        }

        if (selectedAttraction != null) {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    regionsList = dbHelper.getRegionsByCountry(countryId).filter { it.id != regionId }
                    showMoveDialog = true
                }) { Text("다른 지역으로 이동", color = MaterialTheme.colorScheme.primary, fontSize = LocalAppTypography.current.body) }
                TextButton(onClick = { editingAttr = selectedAttraction; attrName = selectedAttraction!!; showAttrDialog = true }) { Text("현재 관광지 수정", fontSize = LocalAppTypography.current.body) }
                TextButton(onClick = {
                    val newAttrs = attractions.filter { it != selectedAttraction }
                    val newGuides = regionData.audioGuides.filter { it.attraction != selectedAttraction }
                    onSave(regionData.copy(audioAttractions = newAttrs, audioGuides = newGuides))
                }) { Text("삭제", color = MaterialTheme.colorScheme.error, fontSize = LocalAppTypography.current.body) }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // 하위 가이드 리스트
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (attractions.isEmpty()) {
                // [다크모드 수정] Color.Gray -> MaterialTheme.colorScheme.onSurfaceVariant
                Text("상단의 '+' 버튼을 눌러 관광지를 추가해주세요.", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = LocalAppTypography.current.body)
            } else if (selectedAttraction != null) {
                val filteredGuides = regionData.audioGuides.filter { it.attraction == selectedAttraction }.sortedBy { it.sequence }
                if (filteredGuides.isEmpty()) {
                    // [다크모드 수정] Color.Gray -> MaterialTheme.colorScheme.onSurfaceVariant
                    Text("등록된 오디오 가이드가 없습니다.", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = LocalAppTypography.current.body)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filteredGuides) { item ->
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            if (item.imageUri.isNotBlank()) {
                                                UriImage(
                                                    uriString = item.imageUri,
                                                    modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)).clickable { onShowImage(item.imageUri) }
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                            }
                                            Text("[${item.sequence}] ${item.title}", fontWeight = FontWeight.Bold, fontSize = LocalAppTypography.current.body)
                                        }
                                        Row {
                                            Icon(Icons.Default.Edit, "수정", modifier = Modifier.clickable {
                                                editingGuide = item;
                                                seqString = item.sequence.toString();
                                                guideAttraction = item.attraction;
                                                title = item.title;
                                                details = item.details;
                                                tempImageUri = item.imageUri;
                                                showGuideDialog = true
                                            }.padding(end=8.dp), tint = MaterialTheme.colorScheme.primary)
                                            Icon(Icons.Default.Delete, "삭제", modifier = Modifier.clickable {
                                                onSave(regionData.copy(audioGuides = regionData.audioGuides.filter { it.id != item.id }))
                                            }, tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                    if (item.details.isNotBlank()) Text(item.details, fontSize = LocalAppTypography.current.small, modifier = Modifier.padding(top=4.dp), maxLines = 2)
                                }
                            }
                        }
                    }
                }
                FloatingActionButton(onClick = { editingGuide = null; seqString = ""; guideAttraction = selectedAttraction!!; title = ""; details = ""; tempImageUri = ""; showGuideDialog = true }, modifier = Modifier.align(Alignment.BottomEnd)) { Icon(Icons.Default.Add, "추가") }
            }
        }
    }

    if (showAttrDialog) {
        AlertDialog(
            onDismissRequest = { showAttrDialog = false },
            title = { Text(if (editingAttr == null) "관광지 추가" else "관광지 수정", fontSize = LocalAppTypography.current.title) },
            text = { OutlinedTextField(value = attrName, onValueChange = { attrName = it }, label = { Text("관광지명") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                Button(onClick = {
                    if (attrName.isNotBlank()) {
                        val newAttrs = attractions.toMutableList()
                        var newGuides = regionData.audioGuides
                        if (editingAttr == null) {
                            if (!newAttrs.contains(attrName)) newAttrs.add(attrName)
                        } else {
                            val idx = newAttrs.indexOf(editingAttr)
                            if (idx >= 0) newAttrs[idx] = attrName
                            newGuides = newGuides.map { if (it.attraction == editingAttr) it.copy(attraction = attrName) else it }
                        }
                        onSave(regionData.copy(audioAttractions = newAttrs, audioGuides = newGuides))
                        showAttrDialog = false
                    }
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { showAttrDialog = false }) { Text("취소") } }
        )
    }

    if (showGuideDialog && selectedAttraction != null) {
        AlertDialog(
            onDismissRequest = { showGuideDialog = false },
            title = { Text("오디오 가이드 편집", fontSize = LocalAppTypography.current.title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Button(onClick = { imagePicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (tempImageUri.isBlank()) "대표 이미지 업로드" else "이미지 첨부됨", fontSize = LocalAppTypography.current.body)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    ExposedDropdownMenuBox(expanded = expandedAttractionMenu, onExpandedChange = { expandedAttractionMenu = !expandedAttractionMenu }) {
                        OutlinedTextField(
                            value = guideAttraction,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("소속 관광지") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAttractionMenu) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(expanded = expandedAttractionMenu, onDismissRequest = { expandedAttractionMenu = false }) {
                            attractions.forEach { attr ->
                                DropdownMenuItem(
                                    text = { Text(attr) },
                                    onClick = { guideAttraction = attr; expandedAttractionMenu = false }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = seqString, onValueChange = { seqString = it }, label = { Text("순번 (숫자 입력)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("제목") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = details, onValueChange = { details = it }, label = { Text("상세설명") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    val seq = seqString.toIntOrNull() ?: 0
                    val newItem = AudioGuideItem(editingGuide?.id ?: UUID.randomUUID().toString(), seq, guideAttraction, title, details, tempImageUri)
                    val newGuides = regionData.audioGuides.toMutableList()
                    val idx = newGuides.indexOfFirst { it.id == newItem.id }
                    if (idx >= 0) newGuides[idx] = newItem else newGuides.add(newItem)
                    onSave(regionData.copy(audioGuides = newGuides))
                    showGuideDialog = false
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { showGuideDialog = false }) { Text("취소") } }
        )
    }

    if (showMoveDialog && selectedAttraction != null) {
        AlertDialog(
            onDismissRequest = { showMoveDialog = false; targetRegionId = null },
            title = { Text("관광지 타 지역 이동", fontSize = LocalAppTypography.current.title) },
            text = {
                Column {
                    Text("[$selectedAttraction] 관광지와 하위 오디오 가이드를 통째로 이동할 지역을 선택하세요.", fontSize = LocalAppTypography.current.body)
                    Spacer(modifier = Modifier.height(8.dp))
                    // [다크모드 수정] 이동 팝업의 바탕을 테마의 surfaceVariant로 변경
                    LazyColumn(modifier = Modifier.height(200.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))) {
                        items(regionsList) { regionItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { targetRegionId = regionItem.id }
                                    .background(if (targetRegionId == regionItem.id) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .padding(16.dp)
                            ) {
                                Text(regionItem.name, fontWeight = if (targetRegionId == regionItem.id) FontWeight.Bold else FontWeight.Normal, fontSize = LocalAppTypography.current.body)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (targetRegionId != null) {
                            val targetJson = dbHelper.getRegionData(targetRegionId!!)
                            val targetData = try { if (targetJson.isNotEmpty()) gson.fromJson(targetJson, RegionData::class.java) ?: RegionData() else RegionData() } catch (e: Exception) { RegionData() }

                            val guidesToMove = regionData.audioGuides.filter { it.attraction == selectedAttraction }
                            val newTargetAttrs = targetData.audioAttractions.toMutableList()
                            if (!newTargetAttrs.contains(selectedAttraction)) newTargetAttrs.add(selectedAttraction!!)
                            val newTargetGuides = targetData.audioGuides.toMutableList()
                            newTargetGuides.addAll(guidesToMove)

                            val updatedTargetData = targetData.copy(audioAttractions = newTargetAttrs, audioGuides = newTargetGuides)
                            dbHelper.updateRegionData(targetRegionId!!, gson.toJson(updatedTargetData))

                            val newCurrentAttrs = regionData.audioAttractions.filter { it != selectedAttraction }
                            val newCurrentGuides = regionData.audioGuides.filter { it.attraction != selectedAttraction }
                            onSave(regionData.copy(audioAttractions = newCurrentAttrs, audioGuides = newCurrentGuides))

                            Toast.makeText(context, "다른 지역으로 이동되었습니다.", Toast.LENGTH_SHORT).show()
                            showMoveDialog = false
                            targetRegionId = null
                        } else {
                            Toast.makeText(context, "이동할 지역을 선택해주세요.", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) { Text("이동하기", fontSize = LocalAppTypography.current.body) }
            },
            dismissButton = { TextButton(onClick = { showMoveDialog = false; targetRegionId = null }) { Text("취소", fontSize = LocalAppTypography.current.body) } }
        )
    }
}