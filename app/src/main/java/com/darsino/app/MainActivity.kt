package com.darsino.app

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

// ================= رنگ‌ها، فونت، تنظیمات =================
val Purple = Color(0xFF5E2CA5)
val PurpleSoft = Color(0xFF8E5BD0)
val Orange = Color(0xFFFF9F1C)
val OrangeLight = Color(0xFFFFA94D)
val DarsinoFont = FontFamily.SansSerif
private val DOT_DX = (-38).dp
private val DOT_DY = (-17).dp
const val API_BASE = "https://YOUR-SERVER.com/api" // آدرس سرور خودت

data class Profile(val name: String, val role: String, val info: String)

// ================= ارتباط با سرور (کلاود) =================
suspend fun postJson(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
    val conn = (URL(API_BASE + path).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        doOutput = true
        connectTimeout = 20000
        readTimeout = 90000
    }
    conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
    val text = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream)
        .bufferedReader().use { it.readText() }
    JSONObject(text)
}

suspend fun login(code: String, role: String): Profile {
    val r = postJson("/login", JSONObject().put("national_code", code).put("role", role))
    if (r.has("error")) throw Exception(r.getString("error"))
    return Profile(r.optString("name"), role, r.optString("info"))
}

suspend fun ask(mode: String, message: String): String {
    val r = postJson("/ask", JSONObject().put("mode", mode).put("message", message))
    return r.optString("answer", "پاسخی دریافت نشد")
}

suspend fun grade(studentCode: String, questions: List<Pair<String, String>>): String {
    val arr = JSONArray()
    questions.forEach { (img, score) ->
        arr.put(JSONObject().put("image", img).put("score", score))
    }
    val r = postJson("/grade", JSONObject().put("student_code", studentCode).put("questions", arr))
    return r.optString("result", "نتیجه‌ای دریافت نشد")
}

fun Bitmap.toBase64(): String {
    val out = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, 70, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

// ================= Activity =================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Purple, secondary = Orange,
                    background = Color.White, surface = Color.White
                )
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    var profile by remember { mutableStateOf<Profile?>(null) }
                    if (profile == null) {
                        EntryScreen { profile = it }
                    } else {
                        MainScreen(profile!!) { profile = null }
                    }
                }
            }
        }
    }
}

// ================= صفحه ورود (اسپلش + کادر) =================
@Composable
fun EntryScreen(onLogin: (Profile) -> Unit) {
    val logoAlpha = remember { Animatable(0f) }
    val logoOffset = remember { Animatable(28f) }
    val expand = remember { Animatable(0f) }
    var showForm by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        logoAlpha.animateTo(1f, tween(400))
        logoOffset.animateTo(0f, tween(500, easing = FastOutSlowInEasing))
        delay(200)
        expand.animateTo(1f, tween(1200, easing = LinearEasing))
        delay(200)
        showForm = true
    }

    fun doLogin(role: String) {
        if (code.isBlank()) { error = "کد ملی را وارد کنید"; return }
        loading = true
        error = null
        scope.launch {
            try {
                onLogin(login(code.trim(), role))
            } catch (e: Exception) {
                error = "ورود ناموفق: ${e.message}"
            }
            loading = false
        }
    }

    Box(Modifier.fillMaxSize().background(Color.White)) {
        Text(
            "درسینو",
            fontFamily = DarsinoFont,
            fontSize = 64.sp, fontWeight = FontWeight.Bold,
            color = Purple,
            modifier = Modifier.align(Alignment.Center).offset(y = logoOffset.value.dp).alpha(logoAlpha.value)
        )

        // دایره نارنجی: از نقطه «ن» بزرگ می‌شود و کل صفحه را می‌گیرد
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f + DOT_DX.toPx(), size.height / 2f + DOT_DY.toPx() + logoOffset.value.dp.toPx())
            val corners = listOf(
                Offset(0f, 0f), Offset(size.width, 0f),
                Offset(0f, size.height), Offset(size.width, size.height)
            )
            val maxR = corners.maxOf { (it - center).getDistance() }
            val startR = 6.dp.toPx()
            val r = startR + (maxR - startR) * expand.value
            drawCircle(OrangeLight, radius = r, center = center, alpha = logoAlpha.value)
        }

        AnimatedVisibility(
            visible = showForm,
            enter = fadeIn(tween(1000)) + slideInVertically(tween(1000)) { it / 3 },
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                shadowElevation = 8.dp,
                modifier = Modifier.padding(24.dp).fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("ورود دانش‌آموز", color = Purple, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.filter { c -> c.isDigit() } },
                        placeholder = {
                            Text("کد ملی خود را وارد کنید", color = Color.Gray.copy(alpha = 0.5f))
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    error?.let { Text(it, color = Color.Red, fontSize = 13.sp) }
                    Button(
                        onClick = { doLogin("student") },
                        enabled = !loading,
                        colors = ButtonDefaults.buttonColors(containerColor = Purple),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text(if (loading) "در حال ورود..." else "ورود")
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showForm,
            enter = fadeIn(tween(1000)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            /*Button(
                onClick = { doLogin("teacher") },
                enabled = !loading,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                shape= RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Purple),
                modifier = Modifier.padding(24.dp).fillMaxWidth().height(64.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ورود دبیران", color = Color.Black, fontWeight = FontWeight.Bold)
                    Text("کد ملی خود را وارد کنید", color = Color.Gray, fontSize = 12.sp)
                }
            }
            */
            Row(
    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 120.dp),

    horizontalArrangement = Arrangement.spacedBy(12.dp)
) {
    Button(
        onClick = { },
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.weight(1f).height(50.dp)
    ) {
        Text("ورود دبیران", fontWeight = FontWeight.Bold)
    }
    Button(
        onClick = { onLogin(Profile("مهمان", "student", ""))},
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.weight(1f).height(50.dp)
    ) {
        Text("ورود بدون شناسه", fontWeight = FontWeight.Bold)
    }
}
        }
    }
}

// ================= صفحه اصلی =================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(profile: Profile, onLogout: () -> Unit) {
    var section by remember { mutableStateOf("home") }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val isTeacher = profile.role == "teacher"
    val menu = listOf(
        "home" to "خانه",
        "status" to "وضعیت من",
        "helper" to "کمک از همیار درس",
        "support" to "ارتباط با پشتیبانی",
        "help" to "راهنما"
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Purple) {
                Text(
                    "درسینو", fontFamily = DarsinoFont, fontSize = 28.sp,
                    color = Color.White, modifier = Modifier.padding(24.dp)
                )
                menu.forEach { (key, label) ->
                    NavigationDrawerItem(
                        label = { Text(label) },
                        selected = section == key,
                        onClick = {
                            section = key
                            scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = Color.White.copy(alpha = 0.25f),
                            unselectedContainerColor = Color.Transparent,
                            selectedTextColor = Color.White,
                            unselectedTextColor = Color.White.copy(alpha = 0.8f)
                        ),
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("درسینو", fontFamily = DarsinoFont, color = Purple, fontSize = 24.sp) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "منو", tint = Purple)
                        }
                    },
                    actions = {
                        if (isTeacher) {
                            IconButton(onClick = { section = "exam" }) { ExamLogo() }
                        }
                    }
                )
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (section) {
                    "home" -> HomeContent(profile) { section = it }
                    "status" -> StatusScreen(profile, onLogout)
                    "helper" -> ChatScreen("helper", "کمک از همیار درس")
                    "support" -> SupportScreen()
                    "help" -> ChatScreen("help", "راهنمای درسینو")
                    "exam" -> TeacherExamScreen()
                    "game" -> GameFlowScreen()
                    "news" -> NewsScreen()
                }
            }
        }
    }
}

// لوگوی ترکیبی دوربین + برگه آزمون
@Composable
fun ExamLogo() {
    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Assignment, contentDescription = "آزمون", tint = Orange, modifier = Modifier.size(28.dp))
        Icon(
            Icons.Default.CameraAlt, contentDescription = null, tint = Purple,
            modifier = Modifier.size(16.dp).align(Alignment.BottomEnd)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NewsTicker(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Orange.copy(alpha = 0.2f),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Text(
            "📢 تعطیلی مدارس فردا لغو شد  •  امتحانات میان‌ترم هفته آینده برگزار می‌شود  •  ",
            color = Color.Black,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(10.dp).basicMarquee()
        )
    }
}

@Composable
fun CircleButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(96.dp).clip(CircleShape).background(Purple).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun HomeContent(profile: Profile, onNav: (String) -> Unit) {
    val isTeacher = profile.role == "teacher"
    val circles = if (isTeacher) {
        listOf("کلاس‌ها" to "status", "آزمون" to "exam", "نمرات" to "status")
    } else {
        listOf("کارنامه" to "status", "مدرسه" to "status", "وضعیت" to "status")
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        NewsTicker { onNav("news") }
        Spacer(Modifier.height(20.dp))
        Text("سلام ${profile.name} 👋", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Purple)
        Text(profile.info, color = Color.Gray)
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            circles.forEach { (label, target) -> CircleButton(label) { onNav(target) } }
        }
        Spacer(Modifier.height(24.dp))
        Card(
            onClick = { onNav("game") },
            colors = CardDefaults.cardColors(containerColor = Orange.copy(alpha = 0.15f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("🎮 بازینو ", fontWeight = FontWeight.Bold, color = Purple, fontSize = 18.sp)
                Text("با بازی یاد بگیر و امتیاز بگیر!", color = Color.Gray)
            }
        }
    }
}

@Composable
fun StatusScreen(profile: Profile, onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("وضعیت من", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Purple)
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Text(profile.info, modifier = Modifier.padding(16.dp))
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(containerColor = Purple),
            modifier = Modifier.fillMaxWidth()
        ) { Text("خروج") }
    }
}

@Composable
fun NewsScreen() {
    val news = listOf(
        "تعطیلی مدارس فردا لغو شد",
        "امتحانات میان‌ترم هفته آینده برگزار می‌شود",
        "ثبت‌نام کلاس‌های تابستانی آغاز شد"
    )
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("اخبار مدرسه", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Purple) }
        items(news) { n ->
            Card(Modifier.fillMaxWidth()) { Text("📢 $n", modifier = Modifier.padding(16.dp)) }
        }
    }
}

// ================= چت با کلاود (همیار، راهنما، پشتیبانی) =================
@Composable
fun ChatScreen(mode: String, title: String, modifier: Modifier = Modifier.fillMaxSize()) {
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<Pair<Boolean, String>>() }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    Column(modifier.padding(12.dp)) {
        Text(title, color = Purple, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(messages) { _, msg ->
                val isUser = msg.first
                Box(
                    Modifier.fillMaxWidth(),
                    contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Surface(
                        color = if (isUser) Purple else Orange.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            msg.second,
                            color = if (isUser) Color.White else Color.Black,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("سوالت را بنویس...") },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple)
            )
            IconButton(
                enabled = !loading && input.isNotBlank(),
                onClick = {
                    val q = input
                    messages.add(true to q)
                    input = ""
                    loading = true
                    scope.launch {
                        val answer = cannedAnswer(mode, q)
                        messages.add(false to answer)
                        loading = false
                    }
                }
            ) {
                Icon(Icons.Default.Send, contentDescription = "ارسال", tint = Purple)
            }
        }
    }
}

@Composable
fun SupportScreen() {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("09938925613", "09906045072").forEach { num ->
                Button(
                    onClick = { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num"))) },
                    colors = ButtonDefaults.buttonColors(containerColor = Purple),
                    modifier = Modifier.weight(1f)
                ) { Text(num, fontSize = 13.sp) }
            }
        }
        Spacer(Modifier.height(12.dp))
        ChatScreen("support", "پشتیبانی هوشمند", Modifier.weight(1f))
    }
}

// ================= بازی ریاضی =================
data class MathQ(val text: String, val answer: Int, val options: List<Int>)

fun newMathQ(): MathQ {
    val a = Random.nextInt(2, 13)
    val b = Random.nextInt(2, 13)
    val op = listOf('+', '-', '×').random()
    val (x, y) = if (op == '-' && a < b) b to a else a to b
    val ans = when (op) { '+' -> x + y; '-' -> x - y; else -> x * y }
    val set = mutableSetOf(ans)
    while (set.size < 4) set.add(ans + Random.nextInt(-6, 7).let { if (it == 0) 3 else it })
    return MathQ("$x $op $y = ?", ans, set.shuffled())
}

@Composable
fun GradeSelectScreen(onGrade: (String) -> Unit) {
    val grades = listOf("اول", "دوم", "سوم", "چهارم", "پنجم", "ششم")
    val lastGrades = listOf("هفتم", "هشتم", "نهم")
    Column(
        modifier = Modifier.fillMaxSize().background(Color.White).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
Row(verticalAlignment = Alignment.Bottom) { Text("بازینو", fontWeight = FontWeight.Bold, color = Purple, fontSize = 32.sp); Box(Modifier.padding(bottom = 6.dp).size(8.dp).background(Orange, RoundedCornerShape(50))) }
Spacer(Modifier.height(24.dp))
grades.chunked(2).forEach { row ->            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { name ->
                    Button(
                        onClick = { onGrade(name) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Purple),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Purple),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) { Text("پایه $name", fontWeight = FontWeight.Bold) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            lastGrades.forEach { name ->
                Button(
                    onClick = { onGrade(name) },
                    colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).height(56.dp)
                ) { Text("پایه $name", fontWeight = FontWeight.Bold) }
            }
        }
    }
}
@Composable
fun MathGameScreen() {
    var q by remember { mutableStateOf(newMathQ()) }
    var score by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf("جواب درست رو انتخاب کن!") }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("امتیاز: $score ⭐", fontWeight = FontWeight.Bold, color = Purple, fontSize = 20.sp)
        Spacer(Modifier.height(24.dp))
        Text(q.text, fontSize = 44.sp, fontWeight = FontWeight.Black, color = Purple)
        Spacer(Modifier.height(16.dp))
        Text(msg, color = Orange, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        q.options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { opt ->
                    Button(
                        onClick = {
                            if (opt == q.answer) {
                                score += 10
                                msg = "خوشم اومد🎉"
                                q = newMathQ()
                            } else {
                                msg = "!یه سوال به این آسونی رو باید جواب بدی💪"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Purple),
                        modifier = Modifier.weight(1f).height(72.dp)
                    ) { Text("$opt", fontSize = 22.sp) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ================= تصحیح آزمون (معلم) =================
class ExamItem(val bitmap: Bitmap) {
    var score by mutableStateOf("")
}

@Composable
fun TeacherExamScreen() {
    val scope = rememberCoroutineScope()
    val examItems = remember { mutableStateListOf<ExamItem>() }
    var studentCode by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) examItems.add(ExamItem(bmp))
    }
    val allReady = examItems.isNotEmpty() &&
            examItems.all { it.score.isNotBlank() } &&
            studentCode.isNotBlank()

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("تصحیح آزمون", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Purple) }
        item {
            OutlinedTextField(
                value = studentCode,
                onValueChange = { studentCode = it.filter { c -> c.isDigit() } },
                label = { Text("کد ملی دانش‌آموز") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Button(
                onClick = { camera.launch(null) },
                colors = ButtonDefaults.buttonColors(containerColor = Orange),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("عکس از سوال")
            }
        }
        itemsIndexed(examItems) { index, entry ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Image(
                        bitmap = entry.bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                    )
                    OutlinedTextField(
                        value = entry.score,
                        onValueChange = { entry.score = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("نمره سوال ${index + 1} (اجباری)") },
                        singleLine = true,
                        isError = entry.score.isBlank(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        item {
            Button(
                enabled = allReady && !busy,
                onClick = {
                    busy = true
                    result = null
                    scope.launch {
                        result = try {
                            grade(studentCode, examItems.map { it.bitmap.toBase64() to it.score })
                        } catch (e: Exception) {
                            "خطا: ${e.message}"
                        }
                        busy = false
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Purple),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (busy) "در حال تصحیح..." else "محاسبه نمره نهایی")
            }
        }
        result?.let { r ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(r, modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

fun lessonsFor(grade: String): List<String> {
    val common = listOf("پیام های آسمانی", "قرآن", "نگارش", "املا")
    return when (grade) {
        "اول", "دوم", "سوم", "چهارم", "پنجم" -> listOf("ریاضی", "علوم", "فارسی") + common
        "ششم" -> listOf("ریاضی", "علوم", "فارسی", "انگلیسی") + common
        "هفتم", "هشتم", "نهم" -> listOf("ریاضی", "علوم", "فارسی", "انگلیسی", "عربی", "تفکر و سبک زندگی") + common
        else -> listOf("ریاضی", "علوم", "فارسی") + common
    }
}

@Composable
fun GameFlowScreen() {
    var grade by remember { mutableStateOf<String?>(null) }
    var selectedLesson by remember { mutableStateOf<String?>(null) }
    val g = grade
    if (g == null) {
        GradeSelectScreen { grade = it }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().background(Color.White).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("پایه $g", fontWeight = FontWeight.Bold, color = Purple, fontSize = 22.sp)
            selectedLesson?.let { sl -> questionsFor(g, sl).forEach { q -> Text(q, color = Color.Black, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)) } }
            Spacer(Modifier.height(24.dp))
            lessonsFor(g).forEach { lesson ->
                Button(
                    onClick = { selectedLesson = lesson },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Purple),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Purple),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) { Text(lesson, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { grade = null },
                colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("بازگشت", fontWeight = FontWeight.Bold) }
        }
    }
}

fun cannedAnswer(mode: String, q: String): String {
    return when (mode) {
        "help" -> "این بخش فقط دربارهٔ اپ درسینو راهنمایی می‌کند. سؤالت را دربارهٔ امکانات اپ بپرس."
        "support" -> "من اینجا هستم تا  مشکلات اپ اپلیکیشن رو برات حل کنم!"
        else -> "این بخش فقط به سؤال‌های درسی جواب می‌دهد. سؤال درسی‌ات را بپرس."
    }
}


fun questionsFor(grade: String, lesson: String): List<String> {
    return listOf(
        "سؤال ۱ $lesson پایه $grade",
        "سؤال ۲ $lesson پایه $grade",
        "سؤال ۳ $lesson پایه $grade"
    )
}
