package com.floatingai.assistant
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        val p = Prefs(this)
        setContent { MaterialTheme(colorScheme = if (p.dark) darkColorScheme() else lightColorScheme()) { Surface(Modifier.fillMaxSize()) {
            var key by remember { mutableStateOf(p.key) }
            Column(Modifier.padding(24.dp).statusBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Floating AI Assistant", style = MaterialTheme.typography.headlineSmall)
                Text("Asisten belajar melayang. Scan layar hanya berjalan setelah Anda menyetujui dialog sistem setiap kali, dan indikator aktif selama proses. Tidak ada data layar yang dikirim tanpa tindakan Anda. Untuk belajar & latihan; jangan dipakai pada ujian resmi yang melarang alat bantu.")
                OutlinedTextField(key, { key = it; p.key = it }, Modifier.fillMaxWidth(), label = { Text("Gemini API key") }, singleLine = true)
                Button({
                    if (!Settings.canDrawOverlays(this@MainActivity)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    else startForegroundService(Intent(this@MainActivity, FloatingService::class.java))
                }) { Text("Aktifkan floating assistant") }
                OutlinedButton({ stopService(Intent(this@MainActivity, FloatingService::class.java)) }) { Text("Matikan") }
            } } } }
    }
}
