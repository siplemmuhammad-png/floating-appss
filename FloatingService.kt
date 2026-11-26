package com.floatingai.assistant
import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.*
import android.os.IBinder
import android.view.*
import android.view.WindowManager.LayoutParams as LP
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.savedstate.*
import kotlinx.coroutines.*

class Owner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lr = LifecycleRegistry(this); private val ssc = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lr
    override val savedStateRegistry: SavedStateRegistry get() = ssc.savedStateRegistry
    override val viewModelStore = ViewModelStore()
    init { ssc.performRestore(null); lr.currentState = Lifecycle.State.RESUMED }
}

class FloatingService : Service() {
    private lateinit var wm: WindowManager; private lateinit var view: ComposeView
    private val owner = Owner()
    private val lp = LP(LP.WRAP_CONTENT, LP.WRAP_CONTENT, LP.TYPE_APPLICATION_OVERLAY, LP.FLAG_NOT_FOCUSABLE or LP.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT)
        .apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 300; softInputMode = LP.SOFT_INPUT_ADJUST_RESIZE }
    override fun onBind(i: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("fl", "Floating AI", NotificationManager.IMPORTANCE_LOW))
        startForeground(1, Notification.Builder(this, "fl").setContentTitle("Floating AI aktif").setSmallIcon(android.R.drawable.ic_dialog_info)
            .addAction(Notification.Action.Builder(null, "Tutup", PendingIntent.getService(this, 0, Intent(this, FloatingService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE)).build()).build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner); setViewTreeSavedStateRegistryOwner(owner); setViewTreeViewModelStoreOwner(owner)
            setContent { Root() }
        }
        wm.addView(view, lp)
    }
    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int { if (i?.action == "stop") stopSelf(); return START_STICKY }
    override fun onDestroy() { wm.removeView(view); super.onDestroy() }

    private fun Modifier.drag() = pointerInput(Unit) { detectDragGestures { c, d -> c.consume(); lp.x += d.x.toInt(); lp.y += d.y.toInt(); wm.updateViewLayout(view, lp) } }

    @Composable fun Root() {
        val ctx = this@FloatingService; val p = remember { Prefs(ctx) }; val dao = remember { Db.get(ctx).dao() }
        var open by remember { mutableStateOf(false) }; var big by remember { mutableStateOf(false) }
        var tab by remember { mutableStateOf(1) }; var hidden by remember { mutableStateOf(false) }; var busy by remember { mutableStateOf(false) }
        var input by remember { mutableStateOf("") }; var dark by remember { mutableStateOf(p.dark) }; var alpha by remember { mutableStateOf(p.alpha) }
        var lang by remember { mutableStateOf(p.lang) }; var cbt by remember { mutableStateOf(p.cbt) }; var key by remember { mutableStateOf(p.key) }
        val chat = remember { mutableStateListOf<Pair<Boolean, String>>() }; var hist by remember { mutableStateOf(listOf<Msg>()) }
        val scope = rememberCoroutineScope()
        fun send(q: String, img: Bitmap?) {
            chat.add(true to q); busy = true
            scope.launch {
                val a = try { Ai.ask(key, q, img, lang, cbt) } catch (e: Exception) { "Gagal: ${e.message}" }
                chat.add(false to a); dao.add(Msg(q = q, a = a)); busy = false
            }
        }
        fun scan() { hidden = true; startActivity(Intent(ctx, CaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        LaunchedEffect(Unit) {
            launch { Bus.shots.collect { b -> hidden = false; open = true; tab = 1; val t = ocr(b); send(t.ifBlank { "Jawab soal pada gambar." }, b) } }
            launch { Bus.cancel.collect { hidden = false } }
        }
        LaunchedEffect(open) { lp.flags = if (open) lp.flags and LP.FLAG_NOT_FOCUSABLE.inv() else lp.flags or LP.FLAG_NOT_FOCUSABLE; wm.updateViewLayout(view, lp) }
        LaunchedEffect(tab) { if (tab == 2) hist = dao.all() }
        MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
            if (hidden) return@MaterialTheme
            if (!open) Box(Modifier.size(56.dp).drag().clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable { open = true }, Alignment.Center) {
                Text(if (busy) "●" else "AI", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            } else Surface(Modifier.width(if (big) 340.dp else 300.dp).height(if (big) 560.dp else 420.dp).alpha(alpha), RoundedCornerShape(20.dp), tonalElevation = 6.dp) {
                Column(Modifier.padding(8.dp)) {
                    Row(Modifier.drag().fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Floating AI", Modifier.weight(1f).padding(start = 8.dp), fontWeight = FontWeight.Bold)
                        TextButton({ big = !big }) { Text("⤢") }; TextButton({ open = false }) { Text("—") }
                    }
                    Row { listOf("Scan", "Ask AI", "History", "Settings").forEachIndexed { i, t ->
                        TextButton({ if (i == 0) scan() else tab = i }, contentPadding = PaddingValues(6.dp)) { Text(t, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal) } } }
                    when (tab) {
                        1 -> Column(Modifier.weight(1f)) {
                            LazyColumn(Modifier.weight(1f)) { items(chat) { (u, t) -> Column(Modifier.padding(4.dp)) {
                                Text((if (u) "Anda: " else "AI: ") + t, color = if (u) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                if (!u) Row { TextButton({ (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("a", t)) }) { Text("Copy") }
                                    TextButton({ send("Jelaskan jawaban tadi dengan lebih sederhana.", null) }) { Text("Lebih sederhana") } } } } }
                            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("Tanya apa saja…") }, maxLines = 3)
                                Button({ if (input.isNotBlank()) { send(input, null); input = "" } }, enabled = !busy) { Text("Kirim") } } }
                        2 -> Column(Modifier.weight(1f)) {
                            TextButton({ scope.launch { dao.clear(); hist = emptyList() } }) { Text("Hapus semua riwayat") }
                            LazyColumn { items(hist) { Text("Q: ${it.q.take(120)}\nA: ${it.a.take(200)}", Modifier.padding(4.dp)) } } }
                        3 -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            Text("Transparansi"); Slider(alpha, { alpha = it; p.alpha = it }, valueRange = 0.4f..1f)
                            Row(verticalAlignment = Alignment.CenterVertically) { Text("Mode gelap", Modifier.weight(1f)); Switch(dark, { dark = it; p.dark = it }) }
                            Row(verticalAlignment = Alignment.CenterVertically) { Text("Mode latihan CBT", Modifier.weight(1f)); Switch(cbt, { cbt = it; p.cbt = it }) }
                            Row(verticalAlignment = Alignment.CenterVertically) { Text("English", Modifier.weight(1f)); Switch(lang == "en", { lang = if (it) "en" else "id"; p.lang = lang }) }
                            OutlinedTextField(key, { key = it; p.key = it }, Modifier.fillMaxWidth(), label = { Text("Gemini API key") }, singleLine = true) }
                    }
                }
            }
        }
    }
}
