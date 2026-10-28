package com.ming.videowallpaper

import android.Manifest
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = WallpaperSettings(this)
        setContent {
            AppTheme {
                SettingsScreen(settings)
            }
        }
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(settings: WallpaperSettings) {
    val context = LocalContext.current

    var playlist by remember { mutableStateOf(settings.playlist) }
    var switchMode by remember { mutableStateOf(settings.switchMode) }
    var intervalMinutes by remember { mutableIntStateOf(settings.intervalMinutes) }
    var shuffle by remember { mutableStateOf(settings.shuffle) }
    var soundOn by remember { mutableStateOf(settings.soundOn) }
    var scaleMode by remember { mutableStateOf(settings.scaleMode) }
    var isActive by remember { mutableStateOf(false) }
    var carrierInput by remember { mutableStateOf(settings.carrierCode) }
    var carrierNotify by remember { mutableStateOf(settings.carrierNotify) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        isActive = isOurWallpaperActive(context)
        // Notification permission may have been revoked in system settings meanwhile.
        carrierNotify = settings.carrierNotify && CarrierNotification.canPost(context)
        // Re-post in case the notification was swiped away.
        CarrierNotification.update(context)
    }

    fun applyCarrierNotify(on: Boolean) {
        carrierNotify = on
        settings.carrierNotify = on
        CarrierNotification.update(context)
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        applyCarrierNotify(granted)
        if (!granted) {
            Toast.makeText(context, "需要通知權限才能在鎖定畫面顯示條碼", Toast.LENGTH_LONG).show()
        }
    }

    fun updatePlaylist(newList: List<Uri>) {
        playlist = newList
        settings.playlist = newList
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        val added = uris.distinct().filter { it !in playlist }
        for (uri in added) {
            // Keep read access after the app restarts; without it the wallpaper loses the video.
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        if (added.isNotEmpty()) updatePlaylist(playlist + added)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("影片桌布") }) }) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (isActive) "目前桌布：使用中" else "目前桌布：尚未套用",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Button(onClick = { openWallpaperPicker(context) }) {
                            Text(if (isActive) "重新套用" else "設為桌布")
                        }
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionTitle("播放清單（${playlist.size} 部）", Modifier.weight(1f))
                    FilledTonalButton(onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("新增影片")
                    }
                }
            }

            if (playlist.isEmpty()) {
                item {
                    Text(
                        "還沒有影片。按「新增影片」從相簿挑選，可以一次選多部。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            itemsIndexed(playlist, key = { _, uri -> uri.toString() }) { i, uri ->
                PlaylistRow(
                    uri = uri,
                    canMoveUp = i > 0,
                    canMoveDown = i < playlist.lastIndex,
                    onMoveUp = { updatePlaylist(playlist.swap(i, i - 1)) },
                    onMoveDown = { updatePlaylist(playlist.swap(i, i + 1)) },
                    onRemove = {
                        runCatching {
                            context.contentResolver.releasePersistableUriPermission(
                                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                            )
                        }
                        updatePlaylist(playlist - uri)
                    },
                )
            }

            item { SectionDivider() }
            item { SectionTitle("輪播") }
            item {
                RadioRow("每部播完就換下一部", switchMode == SwitchMode.AFTER_EACH) {
                    switchMode = SwitchMode.AFTER_EACH
                    settings.switchMode = SwitchMode.AFTER_EACH
                }
            }
            item {
                RadioRow("每隔一段時間換", switchMode == SwitchMode.INTERVAL) {
                    switchMode = SwitchMode.INTERVAL
                    settings.switchMode = SwitchMode.INTERVAL
                }
            }
            if (switchMode == SwitchMode.INTERVAL) {
                item {
                    IntervalStepper(intervalMinutes) {
                        intervalMinutes = it
                        settings.intervalMinutes = it
                    }
                }
            }
            item {
                RadioRow("每次回到桌面時換", switchMode == SwitchMode.ON_RETURN) {
                    switchMode = SwitchMode.ON_RETURN
                    settings.switchMode = SwitchMode.ON_RETURN
                }
            }
            item {
                SwitchRow("隨機順序", shuffle) {
                    shuffle = it
                    settings.shuffle = it
                }
            }
            if (playlist.size == 1) {
                item { HintText("清單只有一部影片時，會一直循環播放這部。") }
            }

            item { SectionDivider() }
            item { SectionTitle("聲音") }
            item {
                SwitchRow("播放影片聲音", soundOn) {
                    soundOn = it
                    settings.soundOn = it
                }
            }

            item { SectionDivider() }
            item { SectionTitle("畫面") }
            item {
                RadioRow("填滿螢幕（裁掉超出的邊緣）", scaleMode == ScaleMode.FILL) {
                    scaleMode = ScaleMode.FILL
                    settings.scaleMode = ScaleMode.FILL
                }
            }
            item {
                RadioRow("完整顯示（上下或左右留黑邊）", scaleMode == ScaleMode.FIT) {
                    scaleMode = ScaleMode.FIT
                    settings.scaleMode = ScaleMode.FIT
                }
            }

            item { SectionDivider() }
            item { SectionTitle("發票載具") }
            item {
                OutlinedTextField(
                    value = carrierInput,
                    onValueChange = { input ->
                        val code = input.uppercase().filterNot(Char::isWhitespace).take(8)
                        carrierInput = code
                        if (code.isEmpty() || Code39.isValidCarrier(code)) {
                            settings.carrierCode = code
                            CarrierNotification.update(context)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("手機條碼") },
                    placeholder = { Text("/ABC1234") },
                    singleLine = true,
                    isError = carrierInput.isNotEmpty() && !Code39.isValidCarrier(carrierInput),
                    supportingText = {
                        if (carrierInput.isNotEmpty() && !Code39.isValidCarrier(carrierInput)) {
                            Text("格式是「/」加 7 個英數字，例如 /ABC1234")
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                    ),
                )
            }
            if (Code39.isValidCarrier(carrierInput)) {
                item {
                    val preview = remember(carrierInput) {
                        Code39.render(carrierInput, moduleWidth = 3, height = 120).asImageBitmap()
                    }
                    Image(
                        preview,
                        contentDescription = "手機條碼預覽",
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.FillWidth,
                        filterQuality = FilterQuality.None,
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { context.startActivity(Intent(context, CarrierActivity::class.java)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("全螢幕顯示條碼")
                    }
                }
            }
            item {
                SwitchRow(
                    "在鎖定畫面顯示（常駐通知）",
                    carrierNotify,
                    enabled = Code39.isValidCarrier(carrierInput),
                ) { on ->
                    val needsPermission = on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    if (needsPermission) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        applyCarrierNotify(on)
                    }
                }
            }
            item {
                HintText(
                    "在鎖定畫面點一下這則通知，不用解鎖就會放大條碼，亮度也會自動調到最亮。" +
                        "如果鎖定畫面看不到通知，請到 設定 → 通知 → 鎖定畫面，允許顯示通知內容。",
                )
            }

            item { SectionDivider() }
            item {
                HintText(
                    "如果桌布過一陣子變成靜止或黑畫面，請到 設定 → 應用程式 → 影片桌布 → 省電策略，改成「無限制」。",
                )
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    uri: Uri,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val name by produceState<String?>(null, uri) {
        value = withContext(Dispatchers.IO) { queryDisplayName(context, uri) }
    }
    val thumbnail by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { loadThumbnail(context, uri) }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            thumbnail?.let {
                Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            name ?: "（讀取中或無法讀取）",
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "往上移")
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "往下移")
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = "移除")
        }
    }
}

@Composable
private fun IntervalStepper(minutes: Int, onChange: (Int) -> Unit) {
    val choices = WallpaperSettings.INTERVAL_CHOICES
    // Saved values always come from the list, but fall back to the nearest one just in case.
    val i = choices.indexOf(minutes).takeIf { it >= 0 }
        ?: choices.indices.minBy { kotlin.math.abs(choices[it] - minutes) }
    Row(
        Modifier.fillMaxWidth().padding(start = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedIconButton(onClick = { onChange(choices[i - 1]) }, enabled = i > 0) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            "每 ${choices[i]} 分鐘",
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        OutlinedIconButton(onClick = { onChange(choices[i + 1]) }, enabled = i < choices.lastIndex) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, onValueChange = onChange, role = Role.Switch)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
}

@Composable
private fun HintText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun <T> List<T>.swap(a: Int, b: Int): List<T> =
    toMutableList().apply { this[a] = this[b].also { this[b] = this[a] } }

private fun isOurWallpaperActive(context: Context): Boolean = runCatching {
    WallpaperManager.getInstance(context).wallpaperInfo?.packageName == context.packageName
}.getOrDefault(false)

private fun openWallpaperPicker(context: Context) {
    val component = ComponentName(context, VideoWallpaperService::class.java)
    val intents = listOf(
        // Opens the preview of this wallpaper directly.
        Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component),
        // Fallback for systems that block the direct preview: the list of live wallpapers.
        Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER),
    )
    for (intent in intents) {
        try {
            context.startActivity(intent)
            return
        } catch (e: ActivityNotFoundException) {
            // Try the next one.
        } catch (e: SecurityException) {
            // Try the next one.
        }
    }
    Toast.makeText(context, "找不到系統的動態桌布設定畫面", Toast.LENGTH_LONG).show()
}

private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }
}.getOrNull()

private fun loadThumbnail(context: Context, uri: Uri): ImageBitmap? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    return runCatching {
        context.contentResolver.loadThumbnail(uri, Size(256, 256), null).asImageBitmap()
    }.getOrNull()
}
