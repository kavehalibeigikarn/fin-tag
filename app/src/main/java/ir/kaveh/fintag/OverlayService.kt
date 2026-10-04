package ir.kaveh.fintag

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.ViewTreeSavedStateRegistryOwner

/** مالک چرخه‌عمر برای ComposeView ای که داخل Activity نیست (پنجره‌ی روی سایر برنامه‌ها) */
class OverlayOwner : SavedStateRegistryOwner {
    private val reg = LifecycleRegistry(this)
    private val ctl = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = reg
    override val savedStateRegistry: SavedStateRegistry get() = ctl.savedStateRegistry

    fun create() {
        ctl.performRestore(null)
        reg.currentState = Lifecycle.State.CREATED
    }
    fun resume() { reg.currentState = Lifecycle.State.RESUMED }
    fun destroy() { reg.currentState = Lifecycle.State.DESTROYED }
}

class OverlayService : Service() {
    private class Holder(val view: ComposeView, val owner: OverlayOwner)

    private val holders = HashMap<Long, Holder>()
    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra("id", -1L) ?: -1L
        if (id > 0 && !holders.containsKey(id) && Settings.canDrawOverlays(this)) show(id)
        if (holders.isEmpty()) stopSelf()
        return START_NOT_STICKY
    }

    private fun show(id: Long) {
        val owner = OverlayOwner()
        owner.create()
        owner.resume()

        val view = ComposeView(this)
        ViewTreeLifecycleOwner.set(view, owner)
        ViewTreeSavedStateRegistryOwner.set(view, owner)
        view.setContent {
            AppTheme { TxnEditor(id) { handler.post { close(id) } } }
        }

        val dm = resources.displayMetrics
        val lp = WindowManager.LayoutParams(
            (dm.widthPixels * 0.94f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (dm.heightPixels * 0.08f).toInt()
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        }

        try {
            wm.addView(view, lp)
            holders[id] = Holder(view, owner)
        } catch (e: Exception) {
            owner.destroy()
        }
    }

    private fun close(id: Long) {
        holders.remove(id)?.let { h ->
            h.view.disposeComposition()
            try { wm.removeView(h.view) } catch (_: Exception) {}
            h.owner.destroy()
        }
        if (holders.isEmpty()) stopSelf()
    }

    override fun onDestroy() {
        holders.keys.toList().forEach { close(it) }
        super.onDestroy()
    }
}
