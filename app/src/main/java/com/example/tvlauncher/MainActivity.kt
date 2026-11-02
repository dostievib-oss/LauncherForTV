package com.example.tvlauncher

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.LruCache
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppEntry(val label: String, val pkg: String, val component: ComponentName)

// Небольшой кэш иконок в памяти (на диск ничего не пишем)
private val iconCache = LruCache<String, ImageBitmap>(80)

class MainActivity : ComponentActivity() {
    private var refresh by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LauncherScreen(refresh) }
    }

    override fun onResume() {
        super.onResume()
        refresh++ // обновляем список, вдруг что-то установили или удалили
    }
}

private fun loadApps(pm: PackageManager, self: String): List<AppEntry> {
    val result = LinkedHashMap<String, AppEntry>()
    // Сначала TV-приложения, затем обычные: для одного пакета берётся первый найденный
    for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        for (ri in pm.queryIntentActivities(intent, 0)) {
            val info = ri.activityInfo
            if (info.packageName == self) continue
            result.getOrPut(info.packageName) {
                AppEntry(
                    label = ri.loadLabel(pm).toString(),
                    pkg = info.packageName,
                    component = ComponentName(info.packageName, info.name),
                )
            }
        }
    }
    return result.values.sortedBy { it.label.lowercase() }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LauncherScreen(refresh: Int) {
    val context = LocalContext.current
    val pm = context.packageManager
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }

    LaunchedEffect(refresh) {
        apps = withContext(Dispatchers.Default) { loadApps(pm, context.packageName) }
    }

    // В лаунчере кнопка «Назад» ничего не делает
    BackHandler {}

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101418))
            .padding(horizontal = 48.dp, vertical = 28.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Приложения", fontSize = 28.sp, color = Color.White)
            ClockText()
        }
        Spacer(Modifier.height(20.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
        ) {
            items(apps, key = { it.pkg }) { app -> AppTile(app) }
        }
    }
}

@Composable
fun ClockText() {
    val time by produceState(initialValue = "") {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            value = fmt.format(Date())
            delay(15_000)
        }
    }
    Text(time, fontSize = 28.sp, color = Color(0xFFB0BEC5))
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppTile(app: AppEntry) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(initialValue = iconCache.get(app.pkg), app.pkg) {
        if (value == null) {
            value = withContext(Dispatchers.Default) {
                runCatching {
                    context.packageManager.getActivityIcon(app.component)
                        .toBitmap(128, 128).asImageBitmap()
                }.getOrNull()
            }?.also { iconCache.put(app.pkg, it) }
        }
    }

    Surface(
        onClick = { launch(context, app) },
        onLongClick = { uninstall(context, app) },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF1B222A),
            contentColor = Color.White,
            focusedContainerColor = Color(0xFF2E3B49),
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            icon?.let {
                Image(bitmap = it, contentDescription = null, modifier = Modifier.size(72.dp))
            } ?: Spacer(Modifier.size(72.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                app.label,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun launch(context: android.content.Context, app: AppEntry) {
    val intent = Intent(Intent.ACTION_MAIN)
        .setComponent(app.component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    runCatching { context.startActivity(intent) }
}

// Долгое нажатие OK на приложении: предложить удалить (полезно, когда мало памяти)
private fun uninstall(context: android.content.Context, app: AppEntry) {
    val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}