package com.coolapk.port.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coolapk.port.api.ApiClient
import com.coolapk.port.core.API_VERSION
import com.coolapk.port.core.DeviceInfo
import com.coolapk.port.core.TokenCreator
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 一条信息流（精简模型，字段名沿用酷安原始 key） */
data class FeedItem(
    val id: String,
    val user: String,
    val message: String,
    val likes: String,
    val replies: String,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApiClient.init(this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF2CAE6D))) {
                App()
            }
        }
    }
}

@Composable
fun App() {
    var tab by remember { mutableStateOf(0) }
    var detailId by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val deviceCode = remember(context) { DeviceInfo.obtain(context) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0; detailId = null },
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text("首页") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1; detailId = null },
                    icon = { Icon(Icons.Default.Search, null) },
                    label = { Text("搜索") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2; detailId = null },
                    icon = { Icon(Icons.Default.Person, null) },
                    label = { Text("我的") },
                )
            }
        },
    ) { padding ->
        Surface(Modifier.fillMaxSize().padding(padding)) {
            val current = detailId
            when {
                current != null -> FeedDetailScreen(current) { detailId = null }
                tab == 0 -> HomeScreen { detailId = it }
                tab == 1 -> SearchScreen { detailId = it }
                else -> MineScreen(deviceCode)
            }
        }
    }
}

@Composable
fun HomeScreen(onOpen: (String) -> Unit) {
    val cards = remember { mutableStateListOf<Pair<String, List<String>>>() }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        runCatching { ApiClient.api.indexV8() }
            .onSuccess { json ->
                json.arr().forEach { cardEl ->
                    val card = cardEl.obj() ?: return@forEach
                    val title = card.str("title").ifBlank { card.str("entityTemplate") }
                    val children: List<String> = card.get("entities").arr().mapNotNull { e ->
                        val o = e.obj() ?: return@mapNotNull null
                        o.str("title", "userInfo", "description").takeIf { it.isNotBlank() }
                    }
                    if (title.isNotBlank() || children.isNotEmpty()) cards.add(title to children.take(6))
                }
            }
            .onFailure { error = it.message ?: it.toString() }
        loading = false
    }

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("酷安 · Android 移植版", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("API 版本 $API_VERSION · 首页 indexV8", fontSize = 11.sp, color = Color.Gray)
        }
        if (loading) item { Text("加载中…") }
        if (error.isNotBlank()) item { Text("请求失败: $error", color = Color.Red) }
        items(cards) { (title, children) ->
            Column(
                Modifier.fillMaxWidth().background(Color(0xFFF6F6F6), RoundedCornerShape(12.dp)).padding(12.dp),
            ) {
                Text(title.ifBlank { "卡片" }, fontWeight = FontWeight.SemiBold)
                children.forEach { c -> Text("· ${J.stripHtml(c)}", fontSize = 13.sp, color = Color(0xFF333333)) }
            }
        }
    }
}

@Composable
fun SearchScreen(onOpen: (String) -> Unit) {
    var keyword by remember { mutableStateOf("酷安") }
    val results = remember { mutableStateListOf<FeedItem>() }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun doSearch() {
        loading = true
        error = ""
        scope.launch {
            runCatching { ApiClient.api.search("feed", keyword) }
                .onSuccess { json ->
                    results.clear()
                    results.addAll(parseFeeds(json))
                    if (results.isEmpty()) error = "没有结果"
                }
                .onFailure { error = it.message ?: it.toString() }
            loading = false
        }
    }

    LaunchedEffect(Unit) { doSearch() }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1f).background(Color(0xFFF1F1F1), RoundedCornerShape(20.dp)).padding(12.dp, 10.dp),
            ) {
                BasicTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = Color.Black),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { doSearch() }) { Text("搜索") }
        }
        Spacer(Modifier.height(8.dp))
        if (loading) Text("搜索中…")
        if (error.isNotBlank()) Text(error, color = Color.Red, fontSize = 13.sp)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results) { item -> FeedCard(item) { onOpen(item.id) } }
        }
    }
}

@Composable
fun FeedCard(item: FeedItem, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .background(Color(0xFFFAFAFA), RoundedCornerShape(10.dp)).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(26.dp).background(Color(0xFFD8F3E5), RoundedCornerShape(13.dp)))
            Spacer(Modifier.width(8.dp))
            Text(item.user.ifBlank { "匿名" }, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(6.dp))
        Text(item.message.take(220), fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Text("赞 ${item.likes} · 评论 ${item.replies}", fontSize = 11.sp, color = Color.Gray)
    }
}

private fun parseFeeds(json: com.google.gson.JsonElement): List<FeedItem> =
    json.arr().mapNotNull { el ->
        val o: JsonObject = el.obj() ?: return@mapNotNull null
        val id = o.str("id", "feedId", "entityId")
        val user = o.str("username", "userName")
        val raw = o.str("message", "title", "description")
        if (id.isBlank() && raw.isBlank()) return@mapNotNull null
        FeedItem(
            id = id,
            user = user,
            message = J.stripHtml(raw),
            likes = o.str("likenum", "likeNum"),
            replies = o.str("replynum", "replyNum"),
        )
    }

@Composable
fun FeedDetailScreen(feedId: String, onBack: () -> Unit) {
    var title by remember { mutableStateOf("加载中…") }
    var body by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    LaunchedEffect(feedId) {
        runCatching { ApiClient.api.feedDetail(feedId) }
            .onSuccess { json ->
                val o = json.obj()
                val dataEl = o?.get("data")
                val feed = if (dataEl != null && dataEl.isJsonArray) {
                    dataEl.asJsonArray.firstOrNull()?.obj()
                } else dataEl?.obj()
                if (feed == null) {
                    note = o?.str("message") ?: "无数据（可能需要验证码）"
                } else {
                    title = feed.str("username", "title").ifBlank { "动态 $feedId" }
                    body = J.stripHtml(feed.str("message", "description"))
                }
            }
            .onFailure { note = it.message ?: it.toString() }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← 返回") }
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        if (note.isNotBlank()) Text(note, color = Color(0xFFB26A00), fontSize = 13.sp)
        Text(body, fontSize = 14.sp)
        Divider(Modifier.padding(vertical = 12.dp))
        Text("提示：feed/detail 在无登录 Cookie 时会返回 err_request_captcha_v2，需要过 NEC 验证码。", fontSize = 11.sp, color = Color.Gray)
    }
}

@Composable
fun MineScreen(deviceCode: String) {
    var cookieInput by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("未登录") }
    var loginInfo by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("设备与登录", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("X-App-Device", fontSize = 12.sp, color = Color.Gray)
        Text(deviceCode, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        Text("X-App-Token", fontSize = 12.sp, color = Color.Gray)
        Text(
            runCatching { TokenCreator.getToken(deviceCode).take(48) + "…" }.getOrElse { "生成失败" },
            fontSize = 11.sp,
        )
        Divider(Modifier.padding(vertical = 12.dp))
        Text("Cookie 登录（从网页版/抓包获取）", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Box(Modifier.fillMaxWidth().background(Color(0xFFF1F1F1), RoundedCornerShape(8.dp)).padding(10.dp)) {
            BasicTextField(
                value = cookieInput,
                onValueChange = { cookieInput = it },
                textStyle = TextStyle(fontSize = 12.sp),
                modifier = Modifier.fillMaxWidth().height(72.dp),
            )
        }
        Row {
            TextButton(onClick = {
                ApiClient.saveCookie(cookieInput)
                status = "已保存 Cookie"
            }) { Text("保存") }
            TextButton(onClick = {
                scope.launch {
                    val r = runCatching { ApiClient.api.checkLoginInfo() }
                    loginInfo = withContext(Dispatchers.IO) {
                        r.getOrNull()?.toString()?.take(300) ?: ("失败: " + r.exceptionOrNull()?.message)
                    }
                }
            }) { Text("检查登录态") }
            TextButton(onClick = { ApiClient.clearCookies(); status = "已清除" }) { Text("清除") }
        }
        Text(status, fontSize = 12.sp, color = Color(0xFF2CAE6D))
        Text(loginInfo, fontSize = 11.sp, color = Color(0xFF555555))
    }
}