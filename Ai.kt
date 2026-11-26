package com.floatingai.assistant
import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.*
import org.json.*
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

object Ai {
    const val MODEL = "gemini-2.0-flash" // ganti sesuai model Gemini yang tersedia
    suspend fun ask(key: String, text: String, img: Bitmap?, lang: String, cbt: Boolean): String = withContext(Dispatchers.IO) {
        require(key.isNotBlank()) { "API key belum diisi (Settings)." }
        val sys = (if (lang == "id") "Jawab dalam Bahasa Indonesia. " else "Answer in English. ") +
            "Kamu tutor semua mata pelajaran. Matematika: selesaikan bertahap. Pilihan ganda A-E: sebut huruf jawaban lalu alasan singkat. " +
            "Akhiri dengan baris 'Keyakinan: NN%'. " +
            (if (cbt) "Mode latihan CBT: jika pengguna belum memilih jawaban, minta ia memilih dulu dan beri petunjuk; bahas lengkap setelah ia menjawab." else "")
        val parts = JSONArray().put(JSONObject().put("text", text))
        img?.let {
            val o = ByteArrayOutputStream(); it.compress(Bitmap.CompressFormat.JPEG, 85, o)
            parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP))))
        }
        val body = JSONObject().put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
        val c = URL("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$key").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val s = (if (c.responseCode in 200..299) c.inputStream else c.errorStream).bufferedReader().readText()
        if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}: ${s.take(200)}")
        JSONObject(s).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
    }
}
