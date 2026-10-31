package app.videogir

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import okhttp3.Request as OkRequest

private val Bg = Color(0xFF0D1220)
private val CardBg = Color(0xFF161D31)
private val Card2 = Color(0xFF1F2842)
private val Accent = Color(0xFF4FD1C5)
private val OnAccent = Color(0xFF04201D)
private val TextMain = Color(0xFFEAF0FF)
private val TextDim = Color(0xFF9AA6C4)
private val Err = Color(0xFFFF7A8A)
private val Ok = Color(0xFF6EE7A8)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, onPrimary = OnAccent,
            background = Bg, onBackground = TextMain,
            surface = CardBg, onSurface = TextMain,
            surfaceVariant = Card2, onSurfaceVariant = TextDim,
            error = Err, outline = Color(0xFF3A4668)
        ),
        content = content
    )
}

private fun String.fa(): String =
    map { if (it in '0'..'9') '۰' + (it - '0') else it }.joinToString("")

private fun fmtSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val mb = bytes / 1048576.0
    val s = if (mb >= 1024) String.format(Locale.US, "%.1f گیگابایت", mb / 1024)
    else String.format(Locale.US, "%.1f مگابایت", mb)
    return s.replace('.', '٫').fa()
}

private fun fmtDuration(sec: Long): String {
    if (sec <= 0) return ""
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    val t = if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
    return t.fa()
}

@Composable
fun Root(vm: MainVm, onDownload: (VideoMeta, Option) -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(containerColor = Bg) { pad ->
            val dl by DownloadHub.state.collectAsStateWithLifecycle()
            val busy = dl is Dl.Running

            Column(Modifier.padding(pad).fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Header() }
                    item { InputCard(vm) }
                    when (val s = vm.ui) {
                        Ui.Idle -> item { HintCard() }
                        Ui.Loading -> item {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Accent)
                            }
                        }
                        is Ui.Error -> item {
                            Text(s.message, color = Err, modifier = Modifier.padding(8.dp))
                        }
                        is Ui.Ready -> {
                            item { MetaCard(s.data.meta) }
                            items(s.data.options) { opt ->
                                OptionRow(opt, enabled = !busy) { onDownload(s.data.meta, opt) }
                            }
                        }
                    }
                }
                DownloadBar(dl)
            }
        }
    }
}

@Composable
private fun Header() {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text("ویدیوگیر", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Accent)
        Spacer(Modifier.height(4.dp))
        Text("لینک یوتیوب را بده، کیفیت دلخواهت را دانلود کن", color = TextDim, fontSize = 14.sp)
    }
}

@Composable
private fun InputCard(vm: MainVm) {
    val clipboard = LocalClipboardManager.current
    Surface(shape = RoundedCornerShape(20.dp), color = CardBg) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = vm.input,
                onValueChange = { vm.input = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("https://youtu.be/…", color = TextDim) },
                textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { vm.load() }),
                shape = RoundedCornerShape(14.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        clipboard.getText()?.text?.let {
                            vm.input = Extractor.findUrl(it) ?: it
                            vm.load()
                        }
                    },
                    shape = RoundedCornerShape(14.dp)
                ) { Text("چسباندن") }
                Button(
                    onClick = { vm.load() },
                    enabled = vm.input.isNotBlank() && vm.ui != Ui.Loading,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("دریافت کیفیت‌ها", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun HintCard() {
    Surface(shape = RoundedCornerShape(20.dp), color = CardBg) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("چطور کار می‌کند؟", fontWeight = FontWeight.Bold)
            Text("۱. لینک ویدیو را بچسبان و «دریافت کیفیت‌ها» را بزن.", color = TextDim, fontSize = 14.sp)
            Text("۲. کیفیت دلخواه را انتخاب کن.", color = TextDim, fontSize = 14.sp)
            Text("۳. فایل در پوشه Movies/VideoGir ذخیره می‌شود.", color = TextDim, fontSize = 14.sp)
            Text(
                "نکته: در برنامه یوتیوب دکمه «اشتراک‌گذاری» را بزن و ویدیوگیر را انتخاب کن.",
                color = Accent, fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun MetaCard(meta: VideoMeta) {
    Surface(shape = RoundedCornerShape(20.dp), color = CardBg) {
        Column {
            val bmp by produceState<ImageBitmap?>(null, meta.thumb) {
                value = meta.thumb?.let { loadBitmap(it) }
            }
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Card2),
                contentAlignment = Alignment.Center
            ) {
                bmp?.let {
                    Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
            Column(Modifier.padding(16.dp)) {
                Text(
                    meta.title, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    maxLines = 3, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                val sub = listOf(meta.uploader, fmtDuration(meta.durationSec))
                    .filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, color = TextDim, fontSize = 13.sp)
            }
        }
    }
}

private suspend fun loadBitmap(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
    try {
        Http.client.newCall(OkRequest.Builder().url(url).build()).execute().use { r ->
            r.body?.byteStream()?.let { BitmapFactory.decodeStream(it)?.asImageBitmap() }
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun OptionRow(opt: Option, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(10.dp), color = if (enabled) Accent else Card2) {
                Text(
                    opt.label,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = if (enabled) OnAccent else TextDim,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(opt.detail, fontSize = 13.sp, color = TextMain)
                val size = fmtSize(opt.size)
                if (size.isNotEmpty()) Text(size, fontSize = 12.sp, color = TextDim)
            }
            Text("دانلود", color = if (enabled) Accent else TextDim, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DownloadBar(dl: Dl) {
    val ctx = LocalContext.current
    when (dl) {
        Dl.Idle -> Unit
        is Dl.Running -> Surface(color = Card2, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(dl.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                if (dl.progress >= 0f) {
                    LinearProgressIndicator(
                        progress = { dl.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = Accent
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Accent)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val pct = if (dl.progress >= 0f) " · ${(dl.progress * 100).toInt()}٪".fa() else ""
                    Text(dl.phase + pct, color = TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { ctx.startService(DownloadService.cancelIntent(ctx)) }) {
                        Text("لغو", color = Err)
                    }
                }
            }
        }
        is Dl.Done -> Surface(color = Card2, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("✓ ذخیره شد", color = Ok, fontWeight = FontWeight.Bold)
                    Text(dl.where, color = TextDim, fontSize = 12.sp)
                }
                TextButton(onClick = { DownloadHub.state.value = Dl.Idle }) { Text("باشه") }
            }
        }
        is Dl.Failed -> Surface(color = Card2, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(dl.message, color = Err, fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { DownloadHub.state.value = Dl.Idle }) { Text("باشه") }
            }
        }
    }
}
