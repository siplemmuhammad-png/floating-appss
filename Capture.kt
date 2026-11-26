package com.floatingai.assistant
import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.*
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.*
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object Bus { val shots = MutableSharedFlow<Bitmap>(extraBufferCapacity = 1); val cancel = MutableSharedFlow<Unit>(extraBufferCapacity = 1) }

suspend fun ocr(b: Bitmap): String = suspendCancellableCoroutine { c ->
    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(InputImage.fromBitmap(b, 0))
        .addOnSuccessListener { c.resume(it.text) }.addOnFailureListener { c.resume("") }
}

/** Setiap scan meminta persetujuan sistem baru; hanya SATU frame diambil, lalu proyeksi dihentikan. */
class CaptureActivity : ComponentActivity() {
    private val l = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) startForegroundService(Intent(this, CaptureService::class.java).putExtra("code", r.resultCode).putExtra("data", r.data))
        else Bus.cancel.tryEmit(Unit)
        finish()
    }
    override fun onCreate(s: Bundle?) { super.onCreate(s); l.launch((getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent()) }
}

class CaptureService : Service() {
    override fun onBind(i: Intent?) = null
    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("cap", "Screen scan", NotificationManager.IMPORTANCE_LOW))
        startForeground(2, Notification.Builder(this, "cap").setContentTitle("Screen scanning aktif").setSmallIcon(android.R.drawable.ic_menu_camera).build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        val h = Handler(Looper.getMainLooper())
        @Suppress("DEPRECATION") val mp = (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).getMediaProjection(i!!.getIntExtra("code", 0), i.getParcelableExtra("data")!!)
        mp.registerCallback(object : MediaProjection.Callback() {}, h)
        val dm = resources.displayMetrics; val w = dm.widthPixels; val hh = dm.heightPixels
        val rd = ImageReader.newInstance(w, hh, PixelFormat.RGBA_8888, 2)
        val vd = mp.createVirtualDisplay("scan", w, hh, dm.densityDpi, DisplayManager.VIRTUAL_DISPLAY_AUTO_MIRROR, rd.surface, null, h)
        var done = false
        rd.setOnImageAvailableListener({ r ->
            val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            if (done) { img.close(); return@setOnImageAvailableListener }
            done = true
            val p = img.planes[0]; val bw = p.rowStride / p.pixelStride
            val full = Bitmap.createBitmap(bw, hh, Bitmap.Config.ARGB_8888).also { it.copyPixelsFromBuffer(p.buffer) }
            val bmp = Bitmap.createBitmap(full, 0, 0, w, hh); img.close()
            vd.release(); mp.stop(); rd.close(); Bus.shots.tryEmit(bmp); stopSelf()
        }, h)
        return START_NOT_STICKY
    }
}
