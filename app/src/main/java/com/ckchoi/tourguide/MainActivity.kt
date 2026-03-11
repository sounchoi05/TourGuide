package com.ckchoi.tourguide

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import kotlin.math.PI
import kotlin.math.atan2
import java.util.regex.Pattern
import com.ckchoi.tourguide.ui.theme.TourGuideTheme

// ==========================================
// 1. 데이터 모델
// ==========================================
data class BasicInfoData(
    var headerMainTitle: String = "",
    var headerSubTitle: String = "",
    var headerBadge1: String = "",
    var headerBadge2: String = "",
    var summaries: List<SummaryBox> = emptyList(),
    var tipsSectionTitle: String = "",
    var tipsSubDesc: String = "",
    var tipBoxes: List<TipBox> = listOf(TipBox(), TipBox(), TipBox(), TipBox()),
    var routeSectionTitle: String = "",
    var routeImageUri1: String = "",
    var routeImageUri2: String = "",
    var budgetLodging: String = "0",
    var budgetTransport: String = "0",
    var budgetFood: String = "0",
    var budgetOther: String = "0",
    var distanceLabels: String = "",
    var distances: String = "",
    var languages: List<String> = listOf("", "", ""),
    var currencies: List<String> = listOf("", "", ""),
    var exchangeRate1: String = "1.0",
    var exchangeRate2: String = "1.0",
    var exchangeRate3: String = "1.0",
    var accountItems: List<AccountItem> = emptyList()
)

data class SummaryBox(var title: String = "", var value: String = "", var desc: String = "")
data class TipBox(var title: String = "", var content: String = "")
data class AccountItem(val id: String = UUID.randomUUID().toString(), var date: String = "", var category: String = "", var content: String = "", var amount: String = "", var currency: String = "")

// 상세/숙소는 단일 데이터로 처리
data class RegionDetailItem(val id: String = UUID.randomUUID().toString(), var travelDates: String = "", var stayDuration: String = "", var summary: String = "", var tips: String = "")
data class AccommodationItem(val id: String = UUID.randomUUID().toString(), var name: String = "", var address: String = "", var contact: String = "", var homepage: String = "", var googleMapLink: String = "", var parkingAvailable: String = "", var roomType: String = "", var price: String = "", var roomDetails: String = "", var checkInOutTime: String = "", var otherInfo: String = "")

data class ScheduleItem(val id: String = UUID.randomUUID().toString(), var date: String = "", var time: String = "", var icon: String = "", var content: String = "", var details: String = "", var precautions: String = "")
data class MapItem(val id: String = UUID.randomUUID().toString(), var routeDetails: String = "", var googleMapLink: String = "", var googleMapEmbedLink: String = "")
data class ParkingItem(val id: String = UUID.randomUUID().toString(), var name: String = "", var address: String = "", var googleMapLink: String = "", var details: String = "")
data class SimpleItem(val id: String = UUID.randomUUID().toString(), var name: String = "", var desc: String = "", var googleMapLink: String = "")
data class RestaurantItem(val id: String = UUID.randomUUID().toString(), var name: String = "", var desc: String = "", var menu: String = "", var googleMapLink: String = "")
data class GalleryItem(val id: String = UUID.randomUUID().toString(), var imageUri: String = "", var desc: String = "")
data class AudioGuideItem(val id: String = UUID.randomUUID().toString(), var sequence: Int = 0, var title: String = "", var details: String = "")

data class RegionData(
    var detail: RegionDetailItem = RegionDetailItem(),
    var accommodation: AccommodationItem = AccommodationItem(),
    var schedules: List<ScheduleItem> = emptyList(),
    var routes: List<MapItem> = emptyList(),
    var attractions: List<MapItem> = emptyList(),
    var restaurantMaps: List<MapItem> = emptyList(),
    var parkings: List<ParkingItem> = emptyList(),
    var foods: List<SimpleItem> = emptyList(),
    var restaurants: List<RestaurantItem> = emptyList(),
    var cheapRestaurants: List<RestaurantItem> = emptyList(),
    var spots: List<SimpleItem> = emptyList(),
    var galleries: List<GalleryItem> = emptyList(),
    var audioGuides: List<AudioGuideItem> = emptyList()
)

val gson = Gson()

// ==========================================
// 유틸리티 함수 & 글로벌 TTS 매니저
// ==========================================
fun parseSafeFloat(input: String): Float = input.replace(Regex("[^0-9.]"), "").toFloatOrNull() ?: 0f

fun formatCurrency(amount: Float): String = java.text.DecimalFormat("#,##0.##").format(amount)

fun cleanForTts(text: String): String {
    return text.replace(Regex("[^\\p{L}\\p{Nd}\\s.,!?]"), " ")
}

fun saveImageToInternalStorage(context: Context, uri: Uri): String {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
        val imageDir = File(context.filesDir, "tour_images")
        if (!imageDir.exists()) imageDir.mkdirs()

        val fileName = "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"
        val outFile = File(imageDir, fileName)
        val outputStream = FileOutputStream(outFile)

        inputStream.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
        outFile.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        ""
    }
}

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

class TtsManager(context: Context) {
    var onStartCallback: ((String) -> Unit)? = null
    var onDoneCallback: ((String) -> Unit)? = null

    val tts: TextToSpeech = TextToSpeech(context.applicationContext) {}.apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                utteranceId?.let { onStartCallback?.invoke(it) }
            }
            override fun onDone(utteranceId: String?) {
                utteranceId?.let { onDoneCallback?.invoke(it) }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {}
        })
    }

    fun stop() { tts.stop() }
    fun shutdown() { tts.shutdown() }
}

// ==========================================
// 공용 이미지 뷰어 컴포넌트
// ==========================================
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
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                        } else {
                            @Suppress("DEPRECATION")
                            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                        }
                    } else {
                        val file = File(uriString)
                        if (file.exists()) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                ImageDecoder.decodeBitmap(ImageDecoder.createSource(file))
                            } else {
                                BitmapFactory.decodeFile(uriString)
                            }
                        } else null
                    }
                    bitmap = bmp?.asImageBitmap()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    if (bitmap != null) {
        Image(bitmap = bitmap!!, contentDescription = null, modifier = modifier, contentScale = contentScale)
    } else {
        Box(modifier = modifier.background(Color.LightGray), contentAlignment = Alignment.Center) {
            Text("이미지 없음", color = Color.Gray)
        }
    }
}

@Composable
fun FullScreenImageViewer(uri: String, onClose: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .zIndex(100f)
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onClose() })
            }
    ) {
        UriImage(
            uriString = uri,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offset += pan
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
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
        ) {
            Text("X", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

// ==========================================
// 메인 액티비티
// ==========================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TourGuideTheme {
                TourGuideApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TourGuideApp() {
    var currentScreen by remember { mutableStateOf("홈메인") }
    var selectedCountryId by remember { mutableStateOf<Int?>(null) }
    var selectedCountryName by remember { mutableStateOf("") }
    var selectedRegionId by remember { mutableStateOf<Int?>(null) }
    var selectedRegionName by remember { mutableStateOf("") }

    var selectedAudioGuideId by remember { mutableStateOf<String?>(null) }

    var userCountryTabIndex by remember { mutableIntStateOf(0) }
    var adminCountryTabIndex by remember { mutableIntStateOf(0) }
    var fullScreenImageUri by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val dbHelper = remember { DatabaseHelper(context) }
    val ttsManager = remember { TtsManager(context) }

    var backPressedTime by remember { mutableLongStateOf(0L) }

    BackHandler {
        if (fullScreenImageUri != null) {
            fullScreenImageUri = null
        } else if (currentScreen == "홈메인") {
            if (System.currentTimeMillis() - backPressedTime < 2000) {
                ttsManager.shutdown()
                (context as? Activity)?.finishAffinity()
            } else {
                Toast.makeText(context, "뒤로가기를 두 번 클릭하여 종료합니다.", Toast.LENGTH_SHORT).show()
                backPressedTime = System.currentTimeMillis()
            }
        } else {
            currentScreen = when (currentScreen) {
                "앱설정" -> "홈메인"
                "여행지 설정" -> "앱설정"
                "국가 상세" -> "여행지 설정"
                "지역 상세" -> "국가 상세"
                "국가 사용자 화면" -> "홈메인"
                "지역 사용자 화면" -> "국가 사용자 화면"
                "오디오 가이드 상세" -> "지역 사용자 화면"
                else -> "홈메인"
            }
            if (currentScreen != "오디오 가이드 상세") {
                ttsManager.stop()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        val titleText = when(currentScreen) {
                            "홈메인" -> "여행 가이드"
                            "국가 사용자 화면" -> selectedCountryName
                            "국가 상세" -> "$selectedCountryName 관리"
                            "지역 사용자 화면" -> "$selectedCountryName > $selectedRegionName"
                            "오디오 가이드 상세" -> "오디오 가이드"
                            "지역 상세" -> "$selectedCountryName > $selectedRegionName 관리"
                            "앱설정" -> "앱 설정"
                            "여행지 설정" -> "여행지 관리"
                            else -> currentScreen
                        }
                        Text(titleText)
                    },
                    navigationIcon = {
                        if (currentScreen != "홈메인") {
                            IconButton(onClick = {
                                currentScreen = when (currentScreen) {
                                    "앱설정" -> "홈메인"
                                    "여행지 설정" -> "앱설정"
                                    "국가 상세" -> "여행지 설정"
                                    "지역 상세" -> "국가 상세"
                                    "국가 사용자 화면" -> "홈메인"
                                    "지역 사용자 화면" -> "국가 사용자 화면"
                                    "오디오 가이드 상세" -> "지역 사용자 화면"
                                    else -> "홈메인"
                                }
                                if (currentScreen != "오디오 가이드 상세") {
                                    ttsManager.stop()
                                }
                            }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로 가기") }
                        }
                    },
                    actions = {
                        if (currentScreen == "홈메인") {
                            IconButton(onClick = { currentScreen = "앱설정" }) {
                                Icon(Icons.Default.Settings, contentDescription = "앱 설정")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                when (currentScreen) {
                    "홈메인" -> HomeScreen(
                        countries = dbHelper.getAllCountriesWithId(),
                        onCountryClick = { id, name ->
                            selectedCountryId = id
                            selectedCountryName = name
                            userCountryTabIndex = 0
                            currentScreen = "국가 사용자 화면"
                        }
                    )
                    "국가 사용자 화면" -> CountryUserScreen(
                        dbHelper = dbHelper,
                        countryId = selectedCountryId ?: 0,
                        selectedTabIndex = userCountryTabIndex,
                        onTabSelected = { userCountryTabIndex = it },
                        onRegionClick = { id, name ->
                            selectedRegionId = id
                            selectedRegionName = name
                            currentScreen = "지역 사용자 화면"
                        },
                        onShowImage = { fullScreenImageUri = it },
                        ttsManager = ttsManager
                    )
                    "지역 사용자 화면" -> RegionUserScreen(
                        dbHelper = dbHelper,
                        regionId = selectedRegionId ?: 0,
                        onShowImage = { fullScreenImageUri = it },
                        onAudioGuideClick = { guideId ->
                            selectedAudioGuideId = guideId
                            currentScreen = "오디오 가이드 상세"
                        }
                    )
                    "오디오 가이드 상세" -> {
                        selectedAudioGuideId?.let { guideId ->
                            AudioGuideDetailScreen(
                                dbHelper = dbHelper,
                                countryId = selectedCountryId ?: 0,
                                regionId = selectedRegionId ?: 0,
                                guideId = guideId,
                                ttsManager = ttsManager,
                                onNavigateGuide = { newGuideId ->
                                    selectedAudioGuideId = newGuideId
                                }
                            )
                        }
                    }

                    // 관리자 화면 연동
                    "앱설정" -> SettingsScreen(onNavigate = { currentScreen = it })
                    "여행지 설정" -> CountrySettingScreen(
                        dbHelper,
                        onCountryClick = { id, name ->
                            selectedCountryId = id
                            selectedCountryName = name
                            adminCountryTabIndex = 0
                            currentScreen = "국가 상세"
                        },
                        onGoHome = { currentScreen = "홈메인" }
                    )
                    "국가 상세" -> CountryDetailScreen(
                        dbHelper,
                        selectedCountryId ?: 0,
                        selectedTabIndex = adminCountryTabIndex,
                        onTabSelected = { adminCountryTabIndex = it },
                        onRegionClick = { id, name ->
                            selectedRegionId = id
                            selectedRegionName = name
                            currentScreen = "지역 상세"
                        }
                    )
                    "지역 상세" -> RegionDetailScreen(dbHelper, selectedRegionId ?: 0)
                }
            }
        }

        fullScreenImageUri?.let { uri ->
            FullScreenImageViewer(uri = uri, onClose = { fullScreenImageUri = null })
        }
    }
}

// ==========================================
// [사용자용] 홈 & 국가 상세 탭
// ==========================================
@Composable
fun HomeScreen(countries: List<Triple<Int, String, String>>, onCountryClick: (Int, String) -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (countries.isEmpty()) {
            Text(
                "우측 상단의 톱니바퀴(⚙️) 아이콘을 눌러\n여행지를 추가하세요.",
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(countries) { (id, name, flag) ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onCountryClick(id, name) },
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(text = "$flag $name", modifier = Modifier.padding(20.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

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
    val tabs = listOf("기본정보", "가계부", "지역", "회화표현", "유용한정보")
    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = selectedTabIndex, edgePadding = 8.dp) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = selectedTabIndex == index, onClick = { onTabSelected(index) }, text = { Text(title) })
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTabIndex) {
                0 -> UserBasicInfoTab(dbHelper, countryId, onShowImage)
                1 -> UserAccountBookTab(dbHelper, countryId)
                2 -> UserRegionTab(dbHelper, countryId, onRegionClick)
                3 -> UserPhraseTab(dbHelper, countryId, ttsManager)
                4 -> UserUsefulInfoTab(dbHelper, countryId)
            }
        }
    }
}

// ------------------------------------------
// [사용자용] 기본정보 탭 (차트 포함)
// ------------------------------------------
@Composable
fun UserBasicInfoTab(dbHelper: DatabaseHelper, countryId: Int, onShowImage: (String) -> Unit) {
    val savedJson = dbHelper.getCountryInfo(countryId, true)
    val data = try {
        if (savedJson.isNotEmpty()) gson.fromJson(savedJson, BasicInfoData::class.java) ?: BasicInfoData()
        else BasicInfoData()
    } catch(e: Exception) { BasicInfoData() }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFFCF9F2)).verticalScroll(rememberScrollState())) {
        Box(modifier = Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) {
            Column {
                if (data.headerMainTitle.isNotBlank()) {
                    Text(data.headerMainTitle, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                if (data.headerSubTitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(data.headerSubTitle, fontSize = 16.sp, color = Color.Gray)
                }
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    if (data.headerBadge1.isNotBlank()) {
                        Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                            Text(data.headerBadge1, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(4.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (data.headerBadge2.isNotBlank()) {
                        Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(data.headerBadge2, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(4.dp))
                        }
                    }
                }
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            val validSummaries = data.summaries.filter { it.title.isNotBlank() || it.value.isNotBlank() }
            if (validSummaries.isNotEmpty()) {
                Text("📌 핵심 요약", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B4F4F))
                Spacer(modifier = Modifier.height(8.dp))
                validSummaries.forEach { summary ->
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(summary.title, fontWeight = FontWeight.Bold)
                                Text(summary.value, color = Color(0xFFD66A2C), fontWeight = FontWeight.Bold)
                            }
                            if (summary.desc.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(summary.desc, fontSize = 14.sp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            val validTips = data.tipBoxes.filter { it.title.isNotBlank() || it.content.isNotBlank() }
            if (data.tipsSectionTitle.isNotBlank() || validTips.isNotEmpty()) {
                Text("💡 ${data.tipsSectionTitle.ifBlank { "주의사항 & 팁" }}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B4F4F))
                if (data.tipsSubDesc.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(data.tipsSubDesc, fontSize = 14.sp, color = Color.Gray)
                }
                Spacer(modifier = Modifier.height(8.dp))
                validTips.forEach { box ->
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            if (box.title.isNotBlank()) {
                                Text(box.title, fontWeight = FontWeight.Bold, color = Color(0xFFD66A2C))
                            }
                            if (box.content.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(box.content, fontSize = 14.sp, lineHeight = 20.sp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (data.routeSectionTitle.isNotBlank() || data.routeImageUri1.isNotBlank() || data.routeImageUri2.isNotBlank()) {
                Text("🗺️ ${data.routeSectionTitle.ifBlank { "여행 루트" }}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B4F4F))
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

            BudgetVisualizerSection(data)
            Spacer(modifier = Modifier.height(24.dp))
            DistanceVisualizerSection(data)
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

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

    Text("💰 예상 비용 분석", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8B4513))
    Spacer(modifier = Modifier.height(16.dp))

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("가족 총 현지 비용", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(16.dp))

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
                    Text("데이터 없음")
                }
            }
            Spacer(modifier = Modifier.height(32.dp))

            BudgetListItem("🏨", "숙박", data.budgetLodging)
            HorizontalDivider(color = Color(0xFFF2E6D9))
            BudgetListItem("🚗", "교통", data.budgetTransport)
            HorizontalDivider(color = Color(0xFFF2E6D9))
            BudgetListItem("🍳", "식비", data.budgetFood)
            HorizontalDivider(color = Color(0xFFF2E6D9))
            BudgetListItem("🎟️", "기타", data.budgetOther)
            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8F0)), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEDDCC))) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("총 예상 비용", fontSize = 14.sp, color = Color.Gray)
                    Text("약 ${formatCurrency(total)}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE85D04))
                }
            }
        }
    }
}

@Composable
fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(16.dp, 8.dp).background(color))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, fontSize = 12.sp, color = Color.DarkGray)
    }
}

@Composable
fun BudgetListItem(icon: String, title: String, amount: String) {
    val displayAmount = if(amount.isBlank() || amount == "0") "0" else amount
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("$icon $title", color = Color.DarkGray)
        Text("약 $displayAmount", fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DistanceVisualizerSection(data: BasicInfoData) {
    val context = LocalContext.current

    val labels = data.distanceLabels.split(Regex("[,，]")).map { it.trim() }.filter { it.isNotBlank() }
    val values = data.distances.split(Regex("[,，]")).map { parseSafeFloat(it) }

    Text("📏 일자별 운전 거리", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))
    Spacer(modifier = Modifier.height(16.dp))

    if (labels.isEmpty() || values.isEmpty()) {
        Text("거리 데이터가 없습니다.", color = Color.Gray)
        return
    }

    val zipCount = minOf(labels.size, values.size)
    val safeLabels = labels.take(zipCount)
    val safeValues = values.take(zipCount)
    val maxVal = safeValues.maxOrNull()?.coerceAtLeast(10f) ?: 10f
    val yAxisMax = (maxVal * 1.15f).toInt()

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(modifier = Modifier.padding(16.dp).height(280.dp)) {
            Column(modifier = Modifier.width(32.dp).fillMaxHeight(0.75f).padding(end = 4.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                Text("$yAxisMax", fontSize = 10.sp, color = Color.Gray)
                Text("${(yAxisMax * 0.75).toInt()}", fontSize = 10.sp, color = Color.Gray)
                Text("${(yAxisMax * 0.5).toInt()}", fontSize = 10.sp, color = Color.Gray)
                Text("${(yAxisMax * 0.25).toInt()}", fontSize = 10.sp, color = Color.Gray)
                Text("0", fontSize = 10.sp, color = Color.Gray)
            }

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.75f)) {
                    for (i in 0..4) {
                        val y = size.height * (i / 4f)
                        drawLine(color = Color.LightGray.copy(alpha = 0.5f), start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1f)
                    }
                }
                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    safeLabels.zip(safeValues).forEach { (label, value) ->
                        Column(modifier = Modifier.fillMaxHeight().weight(1f).clickable {
                            Toast.makeText(context, "$label: ${value}km", Toast.LENGTH_SHORT).show()
                        }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(modifier = Modifier.weight(0.75f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                                val heightRatio = if (yAxisMax > 0) (value / yAxisMax).coerceIn(0.01f, 1f) else 0f
                                val barColor = if (value >= 350) Color(0xFFF26B6B) else Color(0xFF4CB172)
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight(heightRatio)
                                        .width(26.dp)
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(barColor)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(modifier = Modifier.weight(0.25f), contentAlignment = Alignment.TopCenter) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    color = Color.DarkGray,
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

// ------------------------------------------
// [공용] 다중 화폐 변환 및 출력 컴포넌트
// ------------------------------------------
@Composable
fun MultiCurrencyDisplay(
    amtInC1: Float,
    c1: String, r1: Float,
    c2: String, r2: Float,
    c3: String, r3: Float,
    mainSize: TextUnit, subSize: TextUnit
) {
    Column(horizontalAlignment = Alignment.End) {
        Text("${formatCurrency(amtInC1)} $c1", fontWeight = FontWeight.Bold, fontSize = mainSize, color = MaterialTheme.colorScheme.primary)
        Row {
            if (c2.isNotBlank()) Text("${formatCurrency(amtInC1 * (r2 / r1))} $c2", fontSize = subSize, color = Color.Gray)
            if (c2.isNotBlank() && c3.isNotBlank()) Text(" | ", fontSize = subSize, color = Color.Gray)
            if (c3.isNotBlank()) Text("${formatCurrency(amtInC1 * (r3 / r1))} $c3", fontSize = subSize, color = Color.Gray)
        }
    }
}

// ------------------------------------------
// [사용자용] 가계부 탭
// ------------------------------------------
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

    fun getRate(curr: String): Float = when(curr) { c1 -> r1; c2 -> r2; c3 -> r3; else -> 1f }
    fun toC1(amt: Float, fromCurr: String): Float = amt * (r1 / getRate(fromCurr))

    val totalInC1 = items.sumOf { toC1(parseSafeFloat(it.amount), it.currency).toDouble() }.toFloat()

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFCF9F2))) {
        if (items.isEmpty()) {
            Text("등록된 가계부 내역이 없습니다.", modifier = Modifier.align(Alignment.Center), color = Color.Gray)
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(4.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("총 지출 합계", fontSize = 16.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        MultiCurrencyDisplay(totalInC1, c1, r1, c2, r2, c3, r3, 32.sp, 14.sp)
                    }
                }

                val groupedItems = items.groupBy { it.date }.toSortedMap()
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    groupedItems.forEach { (date, dailyList) ->
                        item {
                            Text(date, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                        }
                        items(dailyList) { item ->
                            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.category, fontSize = 12.sp, color = Color.Gray)
                                        Text(item.content, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    }
                                    val amtInC1 = toC1(parseSafeFloat(item.amount), item.currency)
                                    MultiCurrencyDisplay(amtInC1, c1, r1, c2, r2, c3, r3, 18.sp, 12.sp)
                                }
                            }
                        }
                        item {
                            val dailyTotalC1 = dailyList.sumOf { toC1(parseSafeFloat(it.amount), it.currency).toDouble() }.toFloat()
                            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp, top = 4.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                Text("일일 합계: ", fontWeight = FontWeight.Bold)
                                MultiCurrencyDisplay(dailyTotalC1, c1, r1, c2, r2, c3, r3, 16.sp, 12.sp)
                            }
                            HorizontalDivider(color = Color.LightGray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserRegionTab(dbHelper: DatabaseHelper, countryId: Int, onRegionClick: (Int, String) -> Unit) {
    val regions = remember { dbHelper.getRegionsByCountry(countryId) }
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (regions.isEmpty()) {
            Text("등록된 지역이 없습니다.", modifier = Modifier.align(Alignment.Center), color = Color.Gray)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(regions) { (id, name, code) ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onRegionClick(id, name) }) {
                        Text(name, modifier = Modifier.padding(16.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun UserPhraseTab(dbHelper: DatabaseHelper, countryId: Int, ttsManager: TtsManager) {
    val savedJson = dbHelper.getCountryInfo(countryId, true)
    val basicInfo = try { if (savedJson.isNotEmpty()) gson.fromJson(savedJson, BasicInfoData::class.java) ?: BasicInfoData() else BasicInfoData() } catch(e: Exception) { BasicInfoData() }

    val lang1Tag = basicInfo.languages.getOrNull(0) ?: ""
    val lang2Tag = basicInfo.languages.getOrNull(1) ?: ""
    val lang3Tag = basicInfo.languages.getOrNull(2) ?: ""

    var categories by remember { mutableStateOf(dbHelper.getPhraseCategories(countryId)) }
    var selectedCategoryId by remember { mutableStateOf<Int?>(categories.firstOrNull()?.first) }
    var phrases by remember { mutableStateOf(selectedCategoryId?.let { dbHelper.getPhrasesByCategory(it) } ?: emptyList()) }

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
                ) { Text(name) }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (categories.isEmpty()) {
                Text("등록된 회화 카테고리가 없습니다.", modifier = Modifier.align(Alignment.Center), color = Color.Gray)
            } else if (phrases.isEmpty()) {
                Text("등록된 회화표현이 없습니다.", modifier = Modifier.align(Alignment.Center), color = Color.Gray)
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(phrases) { phrase ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                if (phrase.expr1.isNotBlank()) {
                                    Text(
                                        phrase.expr1,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable {
                                            ttsManager.tts.language = getTtsLocale(lang1Tag)
                                            ttsManager.tts.speak(cleanForTts(phrase.expr1), TextToSpeech.QUEUE_FLUSH, null, null)
                                        }
                                    )
                                }
                                if (phrase.meaning.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(phrase.meaning, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(8.dp))

                                if (phrase.expr2.isNotBlank()) {
                                    val label1 = try { Locale.forLanguageTag(lang2Tag).displayName } catch(e:Exception){lang2Tag}
                                    Text(
                                        "${label1}: ${phrase.expr2}",
                                        fontSize = 18.sp,
                                        modifier = Modifier.clickable {
                                            ttsManager.tts.language = getTtsLocale(lang2Tag)
                                            ttsManager.tts.speak(cleanForTts(phrase.expr2), TextToSpeech.QUEUE_FLUSH, null, null)
                                        }.padding(vertical=4.dp)
                                    )
                                }
                                if (phrase.expr3.isNotBlank()) {
                                    val label2 = try { Locale.forLanguageTag(lang3Tag).displayName } catch(e:Exception){lang3Tag}
                                    Text(
                                        "${label2}: ${phrase.expr3}",
                                        fontSize = 18.sp,
                                        modifier = Modifier.clickable {
                                            ttsManager.tts.language = getTtsLocale(lang3Tag)
                                            ttsManager.tts.speak(cleanForTts(phrase.expr3), TextToSpeech.QUEUE_FLUSH, null, null)
                                        }.padding(vertical=4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserUsefulInfoTab(dbHelper: DatabaseHelper, countryId: Int) {
    val text = remember { dbHelper.getCountryInfo(countryId, false) }
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (text.isBlank()) Text("등록된 유용한 정보가 없습니다.", modifier = Modifier.align(Alignment.Center), color = Color.Gray)
        else Text(text, fontSize = 16.sp, modifier = Modifier.verticalScroll(rememberScrollState()))
    }
}

// ------------------------------------------
// [사용자용] 지역 상세 13개 탭 화면
// ------------------------------------------
@Composable
fun RegionUserScreen(dbHelper: DatabaseHelper, regionId: Int, onShowImage: (String) -> Unit, onAudioGuideClick: (String) -> Unit) {
    val tabs = listOf("상세정보", "주요일정", "숙소정보", "이동경로/지도", "여행지/지도", "맛집/지도", "주차장정보", "먹거리정보", "맛집정보", "가성비맛집", "추천스팟", "갤러리", "오디오가이드")
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val savedJson = dbHelper.getRegionData(regionId)
    val regionData = try { if (savedJson.isNotEmpty()) gson.fromJson(savedJson, RegionData::class.java) ?: RegionData() else RegionData() } catch (e: Exception) { RegionData() }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFFCF9F2))) {
        ScrollableTabRow(selectedTabIndex = selectedTabIndex, edgePadding = 8.dp) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = selectedTabIndex == index, onClick = { selectedTabIndex = index }, text = { Text(title, fontSize = 14.sp) })
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTabIndex) {
                0 -> {
                    val item = regionData.detail
                    if (item.travelDates.isBlank() && item.stayDuration.isBlank() && item.summary.isBlank() && item.tips.isBlank()) {
                        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) { Text("등록된 상세정보가 없습니다.", color = Color.Gray) }
                    } else {
                        Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                if(item.travelDates.isNotBlank()) Text("일정: ${item.travelDates}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                if(item.stayDuration.isNotBlank()) Text("숙박: ${item.stayDuration}", fontSize = 14.sp)
                                if(item.summary.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text("요약: ${item.summary}") }
                                if(item.tips.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text("팁: ${item.tips}", color = Color.Gray) }
                            }
                        }
                    }
                }
                1 -> GenericUserList(regionData.schedules.sortedBy { it.date + it.time }, "등록된 일정이 없습니다.") { item ->
                    Text("${item.icon} ${item.date} ${item.time} - ${item.content}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                    if(item.details.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text(item.details) }
                    if(item.precautions.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text("⚠️ ${item.precautions}", color = MaterialTheme.colorScheme.error, fontSize = 14.sp) }
                }
                2 -> {
                    val item = regionData.accommodation
                    if (item.name.isBlank() && item.address.isBlank()) {
                        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) { Text("등록된 숙소가 없습니다.", color = Color.Gray) }
                    } else {
                        Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                Text(item.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                                val context = LocalContext.current
                                Row(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                                    if(item.googleMapLink.isNotBlank()) {
                                        Text("📍 구글맵 실행", color = Color.Blue, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                                            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.googleMapLink))) } catch (e:Exception) {}
                                        })
                                        Spacer(modifier = Modifier.width(16.dp))
                                    }
                                    if(item.homepage.isNotBlank()) {
                                        Text("🌐 홈페이지 접속", color = Color.Blue, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                                            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.homepage))) } catch (e:Exception) {}
                                        })
                                    }
                                }
                                if(item.roomType.isNotBlank() || item.price.isNotBlank()) Text("${item.roomType} | ${item.price}", fontWeight = FontWeight.Bold)
                                if(item.address.isNotBlank()) Text("주소: ${item.address}", fontSize = 14.sp)
                                if(item.contact.isNotBlank()) Text("연락처: ${item.contact}", fontSize = 14.sp)
                                if(item.checkInOutTime.isNotBlank()) Text("체크인/아웃: ${item.checkInOutTime}", fontSize = 14.sp)
                                if(item.parkingAvailable.isNotBlank()) Text("주차여부: ${item.parkingAvailable}", fontSize = 14.sp)
                                if(item.roomDetails.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text("룸 상세: ${item.roomDetails}", fontSize = 14.sp) }
                                if(item.otherInfo.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text("기타정보: ${item.otherInfo}", fontSize = 14.sp) }
                            }
                        }
                    }
                }
                3 -> MapUserList(regionData.routes)
                4 -> MapUserList(regionData.attractions)
                5 -> MapUserList(regionData.restaurantMaps)
                6 -> GenericUserList(regionData.parkings, "등록된 주차장이 없습니다.") { item ->
                    val context = LocalContext.current
                    Text(item.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    if(item.googleMapLink.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("📍 구글맵 실행", color = Color.Blue, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.googleMapLink))) } catch (e:Exception) {}
                        })
                    }
                    Text(item.address, fontSize = 14.sp)
                    if(item.details.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text(item.details) }
                }
                7 -> SimpleUserList(regionData.foods)
                8 -> RestaurantUserList(regionData.restaurants)
                9 -> RestaurantUserList(regionData.cheapRestaurants)
                10 -> SimpleUserList(regionData.spots)
                11 -> GenericUserList(regionData.galleries, "등록된 사진이 없습니다.") { item ->
                    if(item.imageUri.isNotBlank()) {
                        UriImage(
                            uriString = item.imageUri,
                            modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(8.dp)).clickable { onShowImage(item.imageUri) }
                        )
                    }
                    if(item.desc.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(item.desc, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
                12 -> AudioGuideUserList(regionData.audioGuides, onAudioGuideClick)
            }
        }
    }
}

@Composable
fun MapUserList(items: List<MapItem>) {
    val context = LocalContext.current
    GenericUserList(items, "등록된 항목이 없습니다.") { item ->
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(item.routeDetails.ifBlank { "이름 없음" }, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
            if(item.googleMapLink.isNotBlank()) {
                Text(
                    "📍 구글맵 실행",
                    color = Color.Blue,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.googleMapLink)))
                        } catch (e:Exception) {
                            Toast.makeText(context,"링크 오류",Toast.LENGTH_SHORT).show()
                        }
                    }.padding(top=4.dp)
                )
            }
        }
        if(item.googleMapEmbedLink.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            AndroidView(
                factory = { ctx ->
                    // WebView를 상속받아 터치 이벤트를 강제로 고정
                    object : WebView(ctx) {
                        override fun onTouchEvent(event: android.view.MotionEvent?): Boolean {
                            when (event?.actionMasked) {
                                // 화면에 손가락이 닿거나 움직일 때 (핀치 줌 멀티터치 포함)
                                android.view.MotionEvent.ACTION_DOWN,
                                android.view.MotionEvent.ACTION_POINTER_DOWN,
                                android.view.MotionEvent.ACTION_MOVE -> {
                                    // 부모(스크롤 뷰)가 터치를 뺏어가지 못하게 강력 요청!
                                    parent?.requestDisallowInterceptTouchEvent(true)
                                }
                                // 손가락을 떼거나 취소되었을 때 권한 반환
                                android.view.MotionEvent.ACTION_UP,
                                android.view.MotionEvent.ACTION_POINTER_UP,
                                android.view.MotionEvent.ACTION_CANCEL -> {
                                    parent?.requestDisallowInterceptTouchEvent(false)
                                }
                            }
                            return super.onTouchEvent(event)
                        }
                    }.apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true

                        // 확대/축소 기능 활성화
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        }
                        setBackgroundColor(0)
                    }
                },
                update = { webView ->
                    var embedLink = item.googleMapEmbedLink
                    if (embedLink.contains("http://")) {
                        embedLink = embedLink.replace("http://", "https://")
                    }

                    val modifiedLink = if (embedLink.contains("<iframe", ignoreCase = true)) {
                        embedLink.replace(Regex("width=\"[^\"]*\""), "width=\"100%\"")
                            .replace(Regex("height=\"[^\"]*\""), "height=\"800\"")
                    } else {
                        "<iframe width=\"100%\" height=\"800\" frameborder=\"0\" style=\"border:0;\" src=\"$embedLink\" allowfullscreen></iframe>"
                    }

                    // viewport 줌 제한(user-scalable=no 등) 모두 해제
                    val fullHtml = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                            <style>
                                body, html { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; }
                                iframe { width: 100% !important; height: 800px !important; border: none !important; }
                            </style>
                        </head>
                        <body>
                            $modifiedLink
                        </body>
                        </html>
                    """.trimIndent()

                    webView.loadDataWithBaseURL("https://www.google.com", fullHtml, "text/html", "utf-8", null)
                },
                modifier = Modifier.fillMaxWidth().height(450.dp).clip(RoundedCornerShape(8.dp))
            )
        }
    }
}

@Composable
fun AudioGuideUserList(items: List<AudioGuideItem>, onAudioGuideClick: (String) -> Unit) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text("등록된 오디오 가이드가 없습니다.", color = Color.Gray)
        }
    } else {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
            items(items.sortedBy { it.sequence }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable { onAudioGuideClick(item.id) },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("[${item.sequence}] ${item.title}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// ------------------------------------------
// [사용자용] 오디오 가이드 상세 화면 (TTS 혼합 파싱 & 상태 캐싱 해결)
// ------------------------------------------
@Composable
fun AudioGuideDetailScreen(dbHelper: DatabaseHelper, countryId: Int, regionId: Int, guideId: String, ttsManager: TtsManager, onNavigateGuide: (String) -> Unit) {
    val savedJsonCountry = dbHelper.getCountryInfo(countryId, true)
    val basicInfo = try { if (savedJsonCountry.isNotEmpty()) gson.fromJson(savedJsonCountry, BasicInfoData::class.java) ?: BasicInfoData() else BasicInfoData() } catch(e: Exception) { BasicInfoData() }

    val savedJsonRegion = dbHelper.getRegionData(regionId)
    val regionData = try { if (savedJsonRegion.isNotEmpty()) gson.fromJson(savedJsonRegion, RegionData::class.java) ?: RegionData() else RegionData() } catch (e: Exception) { RegionData() }

    val sortedGuides = regionData.audioGuides.sortedBy { it.sequence }
    val currentIndex = sortedGuides.indexOfFirst { it.id == guideId }
    val item = sortedGuides.getOrNull(currentIndex) ?: return

    val lang1Tag = basicInfo.languages.getOrNull(0)?.takeIf { it.isNotBlank() } ?: Locale.getDefault().toLanguageTag()
    val lang2Tag = basicInfo.languages.getOrNull(1)?.takeIf { it.isNotBlank() } ?: Locale.US.toLanguageTag()

    val paragraphs = remember(guideId) { item.details.split("\n") }
    val flatSentences = remember(guideId) {
        val list = mutableListOf<String>()
        paragraphs.forEach { p ->
            list.addAll(p.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() })
        }
        list
    }

    var playingIndex by remember(guideId) { mutableIntStateOf(-1) }
    var isPlaying by remember(guideId) { mutableStateOf(false) }
    var isPaused by remember(guideId) { mutableStateOf(false) }

    val currentPlayingIndex by rememberUpdatedState(playingIndex)
    val currentIsPlaying by rememberUpdatedState(isPlaying)
    val currentIsPaused by rememberUpdatedState(isPaused)

    val speakSentenceMixed = { sentenceIdx: Int ->
        if (sentenceIdx < flatSentences.size) {
            val sentence = flatSentences[sentenceIdx]
            val parts = mutableListOf<Pair<String, Boolean>>()

            val matcher = Pattern.compile("[\\(（](.*?)[\\)）]").matcher(sentence)
            var lastEnd = 0

            while (matcher.find()) {
                if (matcher.start() > lastEnd) {
                    parts.add(Pair(sentence.substring(lastEnd, matcher.start()), false))
                }
                val insideText = matcher.group(1) ?: ""
                parts.add(Pair(insideText, true))
                lastEnd = matcher.end()
            }
            if (lastEnd < sentence.length) {
                parts.add(Pair(sentence.substring(lastEnd), false))
            }

            parts.forEachIndexed { pIdx, part ->
                val cleanText = cleanForTts(part.first).trim()
                val textToRead = if (cleanText.isEmpty()) " " else cleanText

                ttsManager.tts.language = getTtsLocale(if (part.second) lang2Tag else lang1Tag)
                val utteranceId = "${sentenceIdx}_${pIdx}_${parts.size}"
                ttsManager.tts.speak(textToRead, TextToSpeech.QUEUE_ADD, null, utteranceId)
            }
        }
    }

    DisposableEffect(guideId) {
        ttsManager.onStartCallback = { utteranceId ->
            val chunks = utteranceId.split("_")
            val sIdx = chunks.getOrNull(0)?.toIntOrNull() ?: -1
            if (sIdx >= 0) {
                CoroutineScope(Dispatchers.Main).launch { playingIndex = sIdx }
            }
        }
        ttsManager.onDoneCallback = { utteranceId ->
            CoroutineScope(Dispatchers.Main).launch {
                val chunks = utteranceId.split("_")
                val sIdx = chunks.getOrNull(0)?.toIntOrNull() ?: -1
                val pIdx = chunks.getOrNull(1)?.toIntOrNull() ?: -1
                val totalParts = chunks.getOrNull(2)?.toIntOrNull() ?: -1

                if (pIdx == totalParts - 1) {
                    if (isPlaying && sIdx + 1 < flatSentences.size) {
                        speakSentenceMixed(sIdx + 1)
                    } else {
                        isPlaying = false
                        isPaused = false
                        playingIndex = -1
                    }
                }
            }
        }

        onDispose {
            ttsManager.stop()
            ttsManager.onStartCallback = null
            ttsManager.onDoneCallback = null
        }
    }

    val playFrom = { index: Int ->
        if (flatSentences.isNotEmpty() && index < flatSentences.size) {
            ttsManager.stop()
            isPlaying = true
            isPaused = false
            playingIndex = index
            speakSentenceMixed(index)
        }
    }

    val togglePlayPause = {
        if (isPlaying) {
            ttsManager.stop()
            isPlaying = false
            isPaused = true
        } else {
            if (isPaused && playingIndex >= 0) playFrom(playingIndex) else playFrom(0)
        }
    }

    val stopPlaying = {
        isPlaying = false
        isPaused = false
        playingIndex = -1
        ttsManager.stop()
    }

    val currentPlayFrom by rememberUpdatedState(playFrom)
    val currentTogglePlayPause by rememberUpdatedState(togglePlayPause)
    var textLayoutResult by remember(guideId) { mutableStateOf<TextLayoutResult?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFCF9F2)).clickable { togglePlayPause() }) {
        Column(modifier = Modifier.fillMaxSize()) {

            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    if (isPlaying) {
                        IconButton(onClick = { togglePlayPause() }) { Text("⏸️", fontSize = 32.sp) }
                    } else {
                        IconButton(onClick = { togglePlayPause() }) {
                            Icon(Icons.Default.PlayArrow, "재생", tint = Color.Green, modifier = Modifier.size(40.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(32.dp))
                    IconButton(onClick = { stopPlaying() }) { Text("⏹️", fontSize = 32.sp) }
                }
            }

            Card(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("[${item.sequence}] ${item.title}", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFFEEDDCC))
                    Spacer(modifier = Modifier.height(16.dp))

                    var globalIdx = 0

                    val annotatedText = buildAnnotatedString {
                        paragraphs.forEach { para ->
                            val sentencesInPara = para.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
                            sentencesInPara.forEach { sentence ->
                                val start = length
                                append(sentence)
                                val end = length
                                addStringAnnotation(tag = "SENTENCE", annotation = globalIdx.toString(), start = start, end = end)

                                if (globalIdx == playingIndex && (isPlaying || isPaused)) {
                                    addStyle(style = SpanStyle(background = Color.Yellow, color = Color.Black), start = start, end = end)
                                }
                                append(" ")
                                globalIdx++
                            }
                            append("\n")
                        }
                    }
                    val currentAnnotatedText by rememberUpdatedState(annotatedText)

                    Text(
                        text = annotatedText,
                        style = LocalTextStyle.current.copy(fontSize = 18.sp, lineHeight = 28.sp, color = MaterialTheme.colorScheme.onSurface),
                        onTextLayout = { textLayoutResult = it },
                        modifier = Modifier.pointerInput(guideId) {
                            detectTapGestures { pos ->
                                textLayoutResult?.let { layoutResult ->
                                    val offset = layoutResult.getOffsetForPosition(pos)
                                    currentAnnotatedText.getStringAnnotations(tag = "SENTENCE", start = offset, end = offset).firstOrNull()?.let { annotation ->
                                        val clickedIdx = annotation.item.toInt()
                                        if (clickedIdx == currentPlayingIndex) {
                                            currentTogglePlayPause()
                                        } else {
                                            currentPlayFrom(clickedIdx)
                                        }
                                    }
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                if (currentIndex > 0) {
                    Button(onClick = { stopPlaying(); onNavigateGuide(sortedGuides[currentIndex - 1].id) }) { Text("이전 가이드") }
                } else { Spacer(modifier = Modifier.width(8.dp)) }

                if (currentIndex < sortedGuides.size - 1) {
                    Button(onClick = { stopPlaying(); onNavigateGuide(sortedGuides[currentIndex + 1].id) }) { Text("다음 가이드") }
                } else { Spacer(modifier = Modifier.width(8.dp)) }
            }
        }
    }
}

// ------------------------------------------
// [공용 리스트]
// ------------------------------------------
@Composable
fun <T> GenericUserList(items: List<T>, emptyMessage: String, itemContent: @Composable (T) -> Unit) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) { Text(emptyMessage, color = Color.Gray) }
    } else {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
            items(items) { item ->
                Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.padding(16.dp)) { itemContent(item) }
                }
            }
        }
    }
}

@Composable
fun SimpleUserList(items: List<SimpleItem>) {
    val context = LocalContext.current
    GenericUserList(items, "등록된 항목이 없습니다.") { item ->
        Text(item.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        if(item.googleMapLink.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text("📍 구글맵 실행", color = Color.Blue, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.googleMapLink))) } catch (e:Exception) {}
            })
        }
        if(item.desc.isNotBlank()) { Spacer(modifier = Modifier.height(4.dp)); Text(item.desc) }
    }
}

@Composable
fun RestaurantUserList(items: List<RestaurantItem>) {
    val context = LocalContext.current
    GenericUserList(items, "등록된 식당이 없습니다.") { item ->
        Text(item.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        if(item.googleMapLink.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text("📍 구글맵 실행", color = Color.Blue, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.googleMapLink))) } catch (e:Exception) {}
            })
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text("메뉴: ${item.menu}", fontWeight = FontWeight.Bold)
        if(item.desc.isNotBlank()) { Spacer(modifier = Modifier.height(4.dp)); Text(item.desc) }
    }
}

// ==========================================
// [관리자용] 화면 컴포넌트
// ==========================================
@Composable
fun SettingsScreen(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    var showDataClearDialog by remember { mutableStateOf(false) }
    val packageInfo = try { context.packageManager.getPackageInfo(context.packageName, 0) } catch (e: Exception) { null }
    val versionName = packageInfo?.versionName ?: "1.0.0"

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        ListItem(headlineContent = { Text("여행지 설정") }, supportingContent = { Text("국가 및 여행지를 추가, 수정, 삭제합니다.") }, modifier = Modifier.clickable { onNavigate("여행지 설정") })
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("앱 캐시 삭제") },
            supportingContent = { Text("임시로 저장된 데이터(이미지 캐시 등)를 비웁니다.") },
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
            headlineContent = { Text("앱 데이터 & 캐시 모두 삭제", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
            supportingContent = { Text("등록한 모든 여행 데이터가 영구적으로 초기화됩니다.") },
            modifier = Modifier.clickable { showDataClearDialog = true }
        )
        HorizontalDivider()
        Spacer(modifier = Modifier.weight(1f))
        Text("버전 정보: $versionName", modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.secondary)
    }

    if (showDataClearDialog) {
        AlertDialog(
            onDismissRequest = { showDataClearDialog = false },
            title = { Text("모든 데이터 초기화") },
            text = { Text("정말 모든 여행 데이터와 캐시를 삭제하시겠습니까?\n앱이 즉시 종료됩니다.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        try {
                            context.deleteDatabase("tourguide.db")
                            context.cacheDir.deleteRecursively()
                            File(context.filesDir, "tour_images").deleteRecursively()
                            Toast.makeText(context, "데이터 초기화 완료. 앱을 다시 실행해주세요.", Toast.LENGTH_LONG).show()
                            (context as? Activity)?.finishAffinity()
                        } catch (e: Exception) {}
                    }
                ) { Text("전체 삭제") }
            },
            dismissButton = { TextButton(onClick = { showDataClearDialog = false }) { Text("취소") } }
        )
    }
}

@Composable
fun CountrySettingScreen(dbHelper: DatabaseHelper, onCountryClick: (Int, String) -> Unit, onGoHome: () -> Unit) {
    var savedCountries by remember { mutableStateOf(dbHelper.getAllCountriesWithId()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<Int?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (savedCountries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("추가된 나라가 없습니다.") }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(savedCountries) { (id, name, flag) ->
                        ListItem(
                            headlineContent = { Text("$flag $name") },
                            modifier = Modifier.clickable { onCountryClick(id, name) },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { editingId = id; showEditDialog = true }) {
                                        Icon(Icons.Default.Edit, "수정", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = {
                                        dbHelper.deleteCountry(name)
                                        savedCountries = dbHelper.getAllCountriesWithId()
                                    }) {
                                        Icon(Icons.Default.Delete, "삭제", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
            FloatingActionButton(onClick = { showAddDialog = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) { Icon(Icons.Default.Add, "추가") }
        }
        Button(onClick = onGoHome, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("홈으로 이동", fontSize = 18.sp) }
    }

    if (showAddDialog) {
        CountrySelectDialog(
            onDismiss = { showAddDialog = false },
            onCountrySelected = { n, f, c ->
                dbHelper.insertCountry(n, f, c)
                savedCountries = dbHelper.getAllCountriesWithId()
                showAddDialog = false
            }
        )
    }

    if (showEditDialog && editingId != null) {
        CountrySelectDialog(
            onDismiss = { showEditDialog = false; editingId = null },
            onCountrySelected = { n, f, c ->
                dbHelper.updateCountryById(editingId!!, n, f, c)
                savedCountries = dbHelper.getAllCountriesWithId()
                showEditDialog = false
                editingId = null
            }
        )
    }
}

@Composable
fun CountrySelectDialog(onDismiss: () -> Unit, onCountrySelected: (String, String, String) -> Unit) {
    var countryList by remember { mutableStateOf(listOf<Triple<String, String, String>>()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val data = java.net.URL("https://restcountries.com/v3.1/all?fields=name,flag,cca2").openConnection().inputStream.bufferedReader().readText()
                val jsonArray = org.json.JSONArray(data)
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
        title = { Text("나라 선택") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("취소") } },
        text = {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(modifier = Modifier.height(400.dp)) {
                    items(countryList) { (n, f, c) ->
                        Text(text = "$f $n", modifier = Modifier.fillMaxWidth().clickable { onCountrySelected(n, f, c) }.padding(12.dp), fontSize = 16.sp)
                        HorizontalDivider()
                    }
                }
            }
        }
    )
}

@Composable
fun CountryDetailScreen(dbHelper: DatabaseHelper, countryId: Int, selectedTabIndex: Int, onTabSelected: (Int) -> Unit, onRegionClick: (Int, String) -> Unit) {
    val tabs = listOf("기본정보", "가계부", "지역", "회화표현", "유용한정보")
    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = selectedTabIndex, edgePadding = 8.dp) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = selectedTabIndex == index, onClick = { onTabSelected(index) }, text = { Text(title) })
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTabIndex) {
                0 -> BasicInfoForm(dbHelper, countryId)
                1 -> AdminAccountBookTab(dbHelper, countryId)
                2 -> RegionTab(dbHelper, countryId, onRegionClick)
                3 -> PhraseTab(dbHelper, countryId)
                4 -> InfoTab(dbHelper, countryId, false)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsLanguageDropdown(label: String, selectedTag: String, availableLocales: List<Locale>, onLocaleSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = if (selectedTag.isNotBlank()) {
        try { Locale.forLanguageTag(selectedTag).displayName } catch(e:Exception) { selectedTag }
    } else "언어 선택"

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selectedName, onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            availableLocales.forEach { locale ->
                DropdownMenuItem(
                    text = { Text(locale.displayName) },
                    onClick = { onLocaleSelected(locale.toLanguageTag()); expanded = false }
                )
            }
        }
    }
}

@Composable
fun BasicInfoForm(dbHelper: DatabaseHelper, countryId: Int) {
    val context = LocalContext.current
    val savedJson = dbHelper.getCountryInfo(countryId, true)
    val initialData = try { if (savedJson.isNotEmpty()) gson.fromJson(savedJson, BasicInfoData::class.java) ?: BasicInfoData() else BasicInfoData() } catch (e: Exception) { BasicInfoData() }
    var data by remember { mutableStateOf(initialData) }
    var isEditing by remember { mutableStateOf(false) }

    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    val coroutineScope = rememberCoroutineScope()

    val picker1 = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                val savedPath = saveImageToInternalStorage(context, it)
                withContext(Dispatchers.Main) { if (savedPath.isNotBlank()) data = data.copy(routeImageUri1 = savedPath) }
            }
        }
    }

    val picker2 = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                val savedPath = saveImageToInternalStorage(context, it)
                withContext(Dispatchers.Main) { if (savedPath.isNotBlank()) data = data.copy(routeImageUri2 = savedPath) }
            }
        }
    }

    var availableLocales by remember { mutableStateOf<List<Locale>>(emptyList()) }
    DisposableEffect(Unit) {
        var ttsInstance: TextToSpeech? = null
        ttsInstance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locales = ttsInstance?.availableLanguages?.toList()?.sortedBy { it.displayName } ?: emptyList()
                availableLocales = locales
            }
        }
        onDispose { ttsInstance?.shutdown() }
    }

    if (isEditing) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("1. 메인 헤더", fontWeight = FontWeight.Bold)
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
                    Text("2. 핵심 요약 (최대 4개)", fontWeight = FontWeight.Bold)
                    data.summaries.forEachIndexed { index, summary ->
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("요약 ${index + 1}", color = MaterialTheme.colorScheme.primary)
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
                    Text("3. 주의사항 & 팁", fontWeight = FontWeight.Bold)
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
                    Text("4. 여행루트 시각화", fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = data.routeSectionTitle, onValueChange = { data = data.copy(routeSectionTitle = it) }, label = { Text("섹션 타이틀") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { picker1.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth()) { Text(if (data.routeImageUri1.isBlank()) "첫 번째 이미지 업로드" else "첫 번째 이미지 (업로드 완료)") }
                    Button(onClick = { picker2.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth()) { Text(if (data.routeImageUri2.isBlank()) "두 번째 이미지 업로드" else "두 번째 이미지 (업로드 완료)") }
                }
            }

            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("5. 예산 및 운전 거리", fontWeight = FontWeight.Bold)
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

            // TTS 언어 설정
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("6. 회화표현 사용 언어 설정", fontWeight = FontWeight.Bold)
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
                    Text("7. 환율/가계부 화폐단위 및 환율설정", fontWeight = FontWeight.Bold)
                    Row {
                        data.currencies.forEachIndexed { index, curr ->
                            OutlinedTextField(value = curr, onValueChange = { v -> val lst = data.currencies.toMutableList(); lst[index] = v; data = data.copy(currencies=lst) }, label = { Text("화폐 ${index+1}") }, modifier = Modifier.weight(1f))
                            if (index < 2) Spacer(modifier = Modifier.width(4.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("환율 설정 (동등 가치의 비율 입력)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("예: 100 EUR = 145000 KRW = 108 USD 인 경우 각각 100, 145000, 108 입력", fontSize = 12.sp, color = Color.Gray)

                    Row(modifier = Modifier.padding(top=8.dp)) {
                        val c1Label = data.currencies.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "화폐1"
                        val c2Label = data.currencies.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "화폐2"
                        val c3Label = data.currencies.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "화폐3"

                        OutlinedTextField(value = data.exchangeRate1, onValueChange = { data = data.copy(exchangeRate1 = it) }, label = { Text(c1Label) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        Spacer(modifier = Modifier.width(4.dp))
                        OutlinedTextField(value = data.exchangeRate2, onValueChange = { data = data.copy(exchangeRate2 = it) }, label = { Text(c2Label) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        Spacer(modifier = Modifier.width(4.dp))
                        OutlinedTextField(value = data.exchangeRate3, onValueChange = { data = data.copy(exchangeRate3 = it) }, label = { Text(c3Label) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }

                    var isFetching by remember { mutableStateOf(false) }
                    Button(
                        onClick = {
                            val c1 = data.currencies.getOrNull(0)?.takeIf { it.isNotBlank() }
                            val c2 = data.currencies.getOrNull(1)?.takeIf { it.isNotBlank() }
                            val c3 = data.currencies.getOrNull(2)?.takeIf { it.isNotBlank() }
                            val r1 = data.exchangeRate1.toFloatOrNull() ?: 0f
                            val r2 = data.exchangeRate2.toFloatOrNull() ?: 0f
                            val r3 = data.exchangeRate3.toFloatOrNull() ?: 0f

                            var baseCur: String? = null
                            var baseVal = 0f

                            if (c1 != null && r1 > 0f) { baseCur = c1; baseVal = r1 }
                            else if (c2 != null && r2 > 0f) { baseCur = c2; baseVal = r2 }
                            else if (c3 != null && r3 > 0f) { baseCur = c3; baseVal = r3 }

                            if (baseCur != null) {
                                isFetching = true
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        val url = java.net.URL("https://open.er-api.com/v6/latest/$baseCur")
                                        val connection = url.openConnection() as java.net.HttpURLConnection
                                        val response = connection.inputStream.bufferedReader().readText()
                                        val rates = org.json.JSONObject(response).getJSONObject("rates")
                                        val df = java.text.DecimalFormat("#.####", java.text.DecimalFormatSymbols(java.util.Locale.US))

                                        var newR1 = data.exchangeRate1
                                        var newR2 = data.exchangeRate2
                                        var newR3 = data.exchangeRate3

                                        if (c1 != null && baseCur != c1 && rates.has(c1)) newR1 = df.format(baseVal * rates.getDouble(c1))
                                        if (c2 != null && baseCur != c2 && rates.has(c2)) newR2 = df.format(baseVal * rates.getDouble(c2))
                                        if (c3 != null && baseCur != c3 && rates.has(c3)) newR3 = df.format(baseVal * rates.getDouble(c3))

                                        withContext(Dispatchers.Main) {
                                            data = data.copy(exchangeRate1 = newR1, exchangeRate2 = newR2, exchangeRate3 = newR3)
                                            isFetching = false
                                            Toast.makeText(context, "$baseCur 기준으로 자동 계산 완료!", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch(e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            isFetching = false
                                            Toast.makeText(context, "환율 가져오기 실패. 직접 입력해주세요.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            } else {
                                Toast.makeText(context, "기준 화폐와 0이 아닌 금액을 한 개 이상 입력하세요.", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top=8.dp),
                        enabled = !isFetching
                    ) { Text(if (isFetching) "가져오는 중..." else "현재 환율 자동 가져오기 (인터넷 필요)") }
                }
            }
            Button(
                onClick = {
                    dbHelper.updateCountryInfo(countryId, true, gson.toJson(data))
                    isEditing = false
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
            ) { Text("저장 완료", fontSize = 18.sp) }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("기본정보가 설정되었습니다.", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("메인 타이틀: ${data.headerMainTitle}")
            Text("등록된 요약: ${data.summaries.size}개")
            Text("숙박 예산: ${data.budgetLodging}")
            Text("설정된 언어: ${data.languages.filter { it.isNotBlank() }.joinToString(", ")}")
            Text("설정된 화폐: ${data.currencies.filter { it.isNotBlank() }.joinToString(", ")}")
            Button(onClick = { isEditing = true }, modifier = Modifier.fillMaxWidth().padding(top=24.dp)) { Text("기본정보 수정하기") }
        }
    }
}

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
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("등록된 가계부 내역이 없습니다.") }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items) { item ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${item.date} [${item.category}]", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Row {
                                    Icon(Icons.Default.Edit, "수정", modifier = Modifier.clickable { editingItem = item; showDialog = true }.padding(end=8.dp), tint = MaterialTheme.colorScheme.primary)
                                    Icon(Icons.Default.Delete, "삭제", modifier = Modifier.clickable {
                                        val newList = items.filter { it.id != item.id }
                                        items = newList
                                        dbHelper.updateCountryInfo(countryId, true, gson.toJson(basicInfo.copy(accountItems = newList)))
                                    }, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(item.content, fontSize = 16.sp)
                                val amtC1 = toC1(parseSafeFloat(item.amount), item.currency)
                                MultiCurrencyDisplay(amtC1, c1, r1, c2, r2, c3, r3, 16.sp, 12.sp)
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { editingItem = null; showDialog = true }, modifier = Modifier.align(Alignment.BottomEnd)) { Icon(Icons.Default.Add, "추가") }
    }

    if (showDialog) {
        var date by remember { mutableStateOf(editingItem?.date ?: "") }
        var category by remember { mutableStateOf(editingItem?.category ?: "") }
        var content by remember { mutableStateOf(editingItem?.content ?: "") }
        var amount by remember { mutableStateOf(editingItem?.amount ?: "") }
        var currency by remember { mutableStateOf(editingItem?.currency ?: currencies[0]) }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("가계부 내역 편집") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("날짜 (예: 4/15)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("분류 (식비, 숙박, 교통 등)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("지출 내용") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("금액") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("화폐 단위 선택", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp)) {
                        currencies.forEach { curr ->
                            val isSelected = currency == curr
                            Button(
                                onClick = { currency = curr },
                                colors = ButtonDefaults.buttonColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer, contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer),
                                modifier = Modifier.padding(end = 4.dp)
                            ) { Text(curr) }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val newItem = AccountItem(editingItem?.id ?: UUID.randomUUID().toString(), date, category, content, amount, currency)
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
                Text(text = text.ifEmpty { "내용이 없습니다." }, modifier = Modifier.weight(1f).fillMaxWidth())
                Button(onClick = { isEditing = true }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("수정하기") }
            }
        }
    }
}

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
            Text("카테고리:", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            LazyRow(modifier = Modifier.weight(1f)) {
                items(categories) { (id, name) ->
                    val isSelected = selectedCategoryId == id
                    Button(
                        onClick = { selectedCategoryId = id },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer, contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer),
                        modifier = Modifier.padding(end = 8.dp)
                    ) { Text(name) }
                }
            }
            IconButton(onClick = { editingCategoryId = null; categoryName = ""; showCategoryDialog = true }) { Icon(Icons.Default.Add, "추가", tint = MaterialTheme.colorScheme.primary) }
        }

        if (selectedCategoryId != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { editingCategoryId = selectedCategoryId; categoryName = categories.find { it.first == selectedCategoryId }?.second ?: ""; showCategoryDialog = true }) { Text("현재 카테고리 수정") }
                TextButton(onClick = { dbHelper.deletePhraseCategory(selectedCategoryId!!); categories = dbHelper.getPhraseCategories(countryId); selectedCategoryId = categories.firstOrNull()?.first }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (categories.isEmpty()) {
                Text("상단의 '+' 버튼을 눌러 카테고리를 추가해주세요.", modifier = Modifier.align(Alignment.Center))
            } else if (phrases.isEmpty()) {
                Text("등록된 회화표현이 없습니다.", modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(phrases) { phrase ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    val displayTitle = phrase.expr1.ifBlank { phrase.meaning }
                                    Text(displayTitle, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Row {
                                        Icon(Icons.Default.Edit, "수정", modifier = Modifier.clickable { editingPhraseId = phrase.id; expr1 = phrase.expr1.ifBlank { phrase.meaning }; expr2 = phrase.expr2; expr3 = phrase.expr3; showPhraseDialog = true }.padding(end = 8.dp), tint = MaterialTheme.colorScheme.primary)
                                        Icon(Icons.Default.Delete, "삭제", modifier = Modifier.clickable { dbHelper.deletePhrase(phrase.id); phrases = dbHelper.getPhrasesByCategory(selectedCategoryId!!) }, tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                if (phrase.expr2.isNotBlank()) Text("${langLabels[1]}: ${phrase.expr2}", fontSize = 14.sp)
                                if (phrase.expr3.isNotBlank()) Text("${langLabels[2]}: ${phrase.expr3}", fontSize = 14.sp)
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
            title = { Text(if (editingCategoryId == null) "카테고리 추가" else "카테고리 수정") },
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

    if (showPhraseDialog && selectedCategoryId != null) {
        AlertDialog(
            onDismissRequest = { showPhraseDialog = false },
            title = { Text(if (editingPhraseId == null) "회화 추가" else "회화 수정") },
            text = {
                Column {
                    Text("설정된 언어에 맞춰 표현을 입력하세요.", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = expr1, onValueChange = { expr1 = it }, label = { Text(langLabels[0]) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = expr2, onValueChange = { expr2 = it }, label = { Text(langLabels[1]) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = expr3, onValueChange = { expr3 = it }, label = { Text(langLabels[2]) }, modifier = Modifier.fillMaxWidth())
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

@Composable
fun RegionTab(dbHelper: DatabaseHelper, countryId: Int, onRegionClick: (Int, String) -> Unit) {
    var regions by remember { mutableStateOf(dbHelper.getRegionsByCountry(countryId)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var regionCode by remember { mutableStateOf("") }
    var regionText by remember { mutableStateOf("") }
    var editingRegionId by remember { mutableStateOf<Int?>(null) }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (regions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("등록된 지역이 없습니다.") }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(regions) { (id, name, code) ->
                    val displayText = if (code.isNotBlank()) "[$code] $name" else name
                    ListItem(
                        headlineContent = { Text(displayText, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.clickable { onRegionClick(id, name) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { editingRegionId = id; regionText = name; regionCode = code; showAddDialog = true }) { Icon(Icons.Default.Edit, "수정", tint=MaterialTheme.colorScheme.primary) }
                                IconButton(onClick = { dbHelper.deleteRegion(id); regions = dbHelper.getRegionsByCountry(countryId) }) { Icon(Icons.Default.Delete, "삭제", tint=MaterialTheme.colorScheme.error) }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
        FloatingActionButton(onClick = { editingRegionId = null; regionText = ""; regionCode = ""; showAddDialog = true }, modifier = Modifier.align(Alignment.BottomEnd)) { Icon(Icons.Default.Add, "지역 추가") }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(if (editingRegionId == null) "지역 추가" else "지역 수정") },
            text = {
                Column {
                    OutlinedTextField(value = regionCode, onValueChange = { regionCode = it }, label = { Text("지역ID (예: BCN)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = regionText, onValueChange = { regionText = it }, label = { Text("지역명 (예: 바르셀로나)") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (regionText.isNotBlank()) {
                        if (editingRegionId == null) dbHelper.insertRegion(regionText, regionCode, countryId)
                        else dbHelper.updateRegion(editingRegionId!!, regionText, regionCode)
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
@Composable
fun RegionDetailScreen(dbHelper: DatabaseHelper, regionId: Int) {
    val tabs = listOf("상세정보", "주요일정", "숙소정보", "이동경로/지도", "여행지/지도", "맛집/지도", "주차장정보", "먹거리정보", "맛집정보", "가성비맛집", "추천스팟", "갤러리", "오디오가이드")
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val savedJson = dbHelper.getRegionData(regionId)
    val regionData by remember { mutableStateOf(try { if (savedJson.isNotEmpty()) gson.fromJson(savedJson, RegionData::class.java) ?: RegionData() else RegionData() } catch (e: Exception) { RegionData() }) }
    var currentData by remember { mutableStateOf(regionData) }

    val saveRegionData = { newData: RegionData -> currentData = newData; dbHelper.updateRegionData(regionId, gson.toJson(newData)) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = selectedTabIndex, edgePadding = 8.dp) {
            tabs.forEachIndexed { index, title -> Tab(selected = selectedTabIndex == index, onClick = { selectedTabIndex = index }, text = { Text(title, fontSize = 14.sp) }) }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
            when (selectedTabIndex) {
                0 -> SingleDetailManager(currentData.detail) { saveRegionData(currentData.copy(detail = it)) }
                1 -> ScheduleManager(currentData.schedules) { saveRegionData(currentData.copy(schedules = it)) }
                2 -> SingleAccommodationManager(currentData.accommodation) { saveRegionData(currentData.copy(accommodation = it)) }
                3 -> MapItemManager("이동경로상세", currentData.routes) { saveRegionData(currentData.copy(routes = it)) }
                4 -> MapItemManager("이동경로상세", currentData.attractions) { saveRegionData(currentData.copy(attractions = it)) }
                5 -> MapItemManager("이동경로상세", currentData.restaurantMaps) { saveRegionData(currentData.copy(restaurantMaps = it)) }
                6 -> ParkingManager(currentData.parkings) { saveRegionData(currentData.copy(parkings = it)) }
                7 -> SimpleItemManager("명칭", currentData.foods) { saveRegionData(currentData.copy(foods = it)) }
                8 -> RestaurantManager(currentData.restaurants) { saveRegionData(currentData.copy(restaurants = it)) }
                9 -> RestaurantManager(currentData.cheapRestaurants) { saveRegionData(currentData.copy(cheapRestaurants = it)) }
                10 -> SimpleItemManager("명칭", currentData.spots) { saveRegionData(currentData.copy(spots = it)) }
                11 -> GalleryManager(currentData.galleries) { saveRegionData(currentData.copy(galleries = it)) }
                12 -> AudioGuideManager(currentData.audioGuides) { saveRegionData(currentData.copy(audioGuides = it)) }
            }
        }
    }
}

@Composable
fun SingleDetailManager(item: RegionDetailItem, onSave: (RegionDetailItem) -> Unit) {
    val context = LocalContext.current
    var travelDates by remember { mutableStateOf(item.travelDates) }
    var stayDuration by remember { mutableStateOf(item.stayDuration) }
    var summary by remember { mutableStateOf(item.summary) }
    var tips by remember { mutableStateOf(item.tips) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        OutlinedTextField(value = travelDates, onValueChange = { travelDates = it }, label = { Text("여행일정") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = stayDuration, onValueChange = { stayDuration = it }, label = { Text("숙박기간") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = summary, onValueChange = { summary = it }, label = { Text("일정요약") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = tips, onValueChange = { tips = it }, label = { Text("여행팁") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                onSave(item.copy(travelDates = travelDates, stayDuration = stayDuration, summary = summary, tips = tips))
                Toast.makeText(context, "상세정보가 저장되었습니다.", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("상세정보 저장", fontSize = 18.sp)
        }
    }
}

@Composable
fun SingleAccommodationManager(item: AccommodationItem, onSave: (AccommodationItem) -> Unit) {
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
        Button(
            onClick = {
                onSave(item.copy(name=n, address=a, contact=c, homepage=h, googleMapLink=g, parkingAvailable=pa, roomType=rt, price=p, roomDetails=rd, checkInOutTime=ci, otherInfo=oi))
                Toast.makeText(context, "숙소정보가 저장되었습니다.", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("숙소정보 저장", fontSize = 18.sp)
        }
    }
}

@Composable
fun ScheduleManager(items: List<ScheduleItem>, onSave: (List<ScheduleItem>) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ScheduleItem?>(null) }
    Box(modifier = Modifier.fillMaxSize()) {
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("등록된 일정이 없습니다.") }
        } else {
            LazyColumn {
                items(items) { item ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${item.icon} ${item.date} ${item.time} - ${item.content}", fontWeight = FontWeight.Bold)
                                Row {
                                    Icon(Icons.Default.Edit, "수정", modifier = Modifier.clickable { editingItem = item; showDialog = true }.padding(end=8.dp), tint = MaterialTheme.colorScheme.primary)
                                    Icon(Icons.Default.Delete, "삭제", modifier = Modifier.clickable { onSave(items.filter { it.id != item.id }) }, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            if (item.details.isNotBlank()) Text(item.details, fontSize = 14.sp, modifier = Modifier.padding(top=4.dp))
                            if (item.precautions.isNotBlank()) Text("주의: ${item.precautions}", fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top=4.dp))
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
            title = { Text("주요일정 편집") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("날짜 (예: 4/15)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = time, onValueChange = { time = it }, label = { Text("시간 (예: 14:00)") }, modifier = Modifier.fillMaxWidth())
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

@Composable
fun MapItemManager(detailLabel: String, items: List<MapItem>, onSave: (List<MapItem>) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<MapItem?>(null) }
    Box(modifier = Modifier.fillMaxSize()) {
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("등록된 항목이 없습니다.") }
        } else {
            LazyColumn {
                items(items) { item ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(item.routeDetails.ifBlank { "이름 없음" }, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
            title = { Text("지도/경로 편집") },
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

@Composable
fun ParkingManager(items: List<ParkingItem>, onSave: (List<ParkingItem>) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ParkingItem?>(null) }
    Box(modifier = Modifier.fillMaxSize()) {
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("등록된 주차장이 없습니다.") }
        } else {
            LazyColumn {
                items(items) { item ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(item.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Row {
                                    Icon(Icons.Default.Edit, "수정", modifier = Modifier.clickable { editingItem = item; showDialog = true }.padding(end=8.dp), tint = MaterialTheme.colorScheme.primary)
                                    Icon(Icons.Default.Delete, "삭제", modifier = Modifier.clickable { onSave(items.filter { it.id != item.id }) }, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            Text(item.address, fontSize = 14.sp)
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
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("주차장 편집") },
            text = {
                Column {
                    OutlinedTextField(value = n, onValueChange = { n = it }, label = { Text("주차장명") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = a, onValueChange = { a = it }, label = { Text("주소") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = gml, onValueChange = { gml = it }, label = { Text("구글맵 링크") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = d, onValueChange = { d = it }, label = { Text("상세정보") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    val newItem = ParkingItem(editingItem?.id ?: UUID.randomUUID().toString(), n, a, gml, d)
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

@Composable
fun SimpleItemManager(nameLabel: String, items: List<SimpleItem>, onSave: (List<SimpleItem>) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<SimpleItem?>(null) }
    Box(modifier = Modifier.fillMaxSize()) {
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("등록된 항목이 없습니다.") }
        } else {
            LazyColumn {
                items(items) { item ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(item.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
        var n by remember { mutableStateOf(editingItem?.name ?: "") }
        var d by remember { mutableStateOf(editingItem?.desc ?: "") }
        var gml by remember { mutableStateOf(editingItem?.googleMapLink ?: "") }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("항목 편집") },
            text = {
                Column {
                    OutlinedTextField(value = n, onValueChange = { n = it }, label = { Text(nameLabel) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = gml, onValueChange = { gml = it }, label = { Text("구글맵 링크") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = d, onValueChange = { d = it }, label = { Text("설명") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    val newItem = SimpleItem(editingItem?.id ?: UUID.randomUUID().toString(), n, d, gml)
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

@Composable
fun RestaurantManager(items: List<RestaurantItem>, onSave: (List<RestaurantItem>) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<RestaurantItem?>(null) }
    Box(modifier = Modifier.fillMaxSize()) {
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("등록된 맛집이 없습니다.") }
        } else {
            LazyColumn {
                items(items) { item ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(item.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Row {
                                    Icon(Icons.Default.Edit, "수정", modifier = Modifier.clickable { editingItem = item; showDialog = true }.padding(end=8.dp), tint = MaterialTheme.colorScheme.primary)
                                    Icon(Icons.Default.Delete, "삭제", modifier = Modifier.clickable { onSave(items.filter { it.id != item.id }) }, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            Text("메뉴: ${item.menu}", fontSize = 14.sp)
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
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("맛집 편집") },
            text = {
                Column {
                    OutlinedTextField(value = n, onValueChange = { n = it }, label = { Text("가게명") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = m, onValueChange = { m = it }, label = { Text("메뉴") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = gml, onValueChange = { gml = it }, label = { Text("구글맵 링크") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = d, onValueChange = { d = it }, label = { Text("설명") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    val newItem = RestaurantItem(editingItem?.id ?: UUID.randomUUID().toString(), n, d, m, gml)
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

@Composable
fun GalleryManager(items: List<GalleryItem>, onSave: (List<GalleryItem>) -> Unit) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<GalleryItem?>(null) }
    var tempImageUri by remember { mutableStateOf("") }

    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
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
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("등록된 사진이 없습니다.") }
        } else {
            LazyColumn {
                items(items) { item ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(if (item.imageUri.isNotBlank()) "이미지 첨부됨" else "이미지 없음", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                                Row {
                                    Icon(Icons.Default.Edit, "수정", modifier = Modifier.clickable { editingItem = item; tempImageUri = item.imageUri; showDialog = true }.padding(end=8.dp), tint = MaterialTheme.colorScheme.primary)
                                    Icon(Icons.Default.Delete, "삭제", modifier = Modifier.clickable { onSave(items.filter { it.id != item.id }) }, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            if (item.desc.isNotBlank()) Text(item.desc, fontSize = 14.sp, modifier = Modifier.padding(top=4.dp))
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
            title = { Text("사진 편집") },
            text = {
                Column {
                    Button(onClick = { imagePicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (tempImageUri.isBlank()) "사진 업로드" else "사진 첨부됨")
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

@Composable
fun AudioGuideManager(items: List<AudioGuideItem>, onSave: (List<AudioGuideItem>) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<AudioGuideItem?>(null) }
    val sortedItems = items.sortedBy { it.sequence }

    Box(modifier = Modifier.fillMaxSize()) {
        if (sortedItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("등록된 오디오 가이드가 없습니다.") }
        } else {
            LazyColumn {
                items(sortedItems) { item ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("[${item.sequence}] ${item.title}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Row {
                                    Icon(Icons.Default.Edit, "수정", modifier = Modifier.clickable { editingItem = item; showDialog = true }.padding(end=8.dp), tint = MaterialTheme.colorScheme.primary)
                                    Icon(Icons.Default.Delete, "삭제", modifier = Modifier.clickable { onSave(items.filter { it.id != item.id }) }, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            if (item.details.isNotBlank()) Text(item.details, fontSize = 14.sp, modifier = Modifier.padding(top=4.dp), maxLines = 2)
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { editingItem = null; showDialog = true }, modifier = Modifier.align(Alignment.BottomEnd)) { Icon(Icons.Default.Add, "추가") }
    }

    if (showDialog) {
        var seqString by remember { mutableStateOf(editingItem?.sequence?.toString() ?: "") }
        var title by remember { mutableStateOf(editingItem?.title ?: "") }
        var details by remember { mutableStateOf(editingItem?.details ?: "") }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("오디오 가이드 편집") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(value = seqString, onValueChange = { seqString = it }, label = { Text("순번 (숫자 입력)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("제목") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = details, onValueChange = { details = it }, label = { Text("상세설명") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    val seq = seqString.toIntOrNull() ?: 0
                    val newItem = AudioGuideItem(editingItem?.id ?: UUID.randomUUID().toString(), seq, title, details)
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