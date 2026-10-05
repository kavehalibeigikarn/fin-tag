package ir.kaveh.fintag

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.abs

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

/** تشخیص کشیدن/لمس با مختصات واقعی صفحه (چون خود پنجره هنگام کشیدن جابجا می‌شود) */
internal class DragTouch(
    ctx: Context,
    private val onMove: (Int, Int) -> Unit,
    private val onTap: (() -> Unit)?
) {
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var moved = false

    fun handle(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX; downY = ev.rawY
                lastX = downX; lastY = downY
                moved = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!moved && (abs(ev.rawX - downX) > slop || abs(ev.rawY - downY) > slop)) moved = true
                if (moved) {
                    val dx = (ev.rawX - lastX).toInt()
                    val dy = (ev.rawY - lastY).toInt()
                    if (dx != 0 || dy != 0) {
                        onMove(dx, dy)
                        lastX += dx
                        lastY += dy
                    }
                }
            }
            MotionEvent.ACTION_UP -> if (!moved) onTap?.invoke()
        }
        return true
    }
}

/** وقتی collapsed=true است همه‌ی لمس‌ها را خودش می‌گیرد (حالت آیکون) */
internal class DragLayout(ctx: Context, private val touch: DragTouch) : LinearLayout(ctx) {
    var collapsed = true
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = collapsed
    override fun onTouchEvent(ev: MotionEvent): Boolean =
        if (collapsed) touch.handle(ev) else super.onTouchEvent(ev)
}

@Composable
private fun Bubble(id: Long) {
    val ctx = LocalContext.current
    var isIn by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(id) { isIn = AppDb.get(ctx).dao().getTxn(id)?.isIn() }
    val color = when (isIn) {
        true -> Color(0xFF2E7D32)
        false -> Color(0xFFC62828)
        null -> Color(0xFF00695C)
    }
    val symbol = when (isIn) {
        true -> "↓"
        false -> "↑"
        null -> "؟"
    }
    Box(Modifier.padding(8.dp)) {
        Box(
            Modifier.size(56.dp).shadow(6.dp, CircleShape).background(color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(symbol, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
    }
}

class OverlayService : Service() {
    private class Holder(
        val root: DragLayout,
        val strip: View,
        val cv: ComposeView,
        val owner: OverlayOwner,
        val lp: WindowManager.LayoutParams,
        val expanded: MutableState<Boolean>,
        var savedX: Int,
        var savedY: Int
    )

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
        val dm = resources.displayMetrics
        val d = dm.density

        val owner = OverlayOwner()
        owner.create()
        owner.resume()

        val touch = DragTouch(
            this,
            onMove = { dx, dy -> holders[id]?.let { move(it, dx, dy) } },
            onTap = { holders[id]?.let { setExpanded(it, true) } }
        )
        val root = DragLayout(this, touch).apply { orientation = LinearLayout.VERTICAL }

        val stripTouch = DragTouch(
            this,
            onMove = { dx, dy -> holders[id]?.let { move(it, dx, dy) } },
            onTap = null
        )
        val strip = TextView(this).apply {
            text = "✥  جابجایی"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(0xFF00695C.toInt())
                cornerRadius = 48f
            }
            visibility = View.GONE
            setOnTouchListener { _, ev -> stripTouch.handle(ev) }
        }
        val cv = ComposeView(this)

        root.addView(
            strip,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (30 * d).toInt()).apply {
                bottomMargin = (4 * d).toInt()
            }
        )
        root.addView(
            cv,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )

        val expanded = mutableStateOf(false)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = dm.widthPixels - (72 * d).toInt()
            y = (dm.heightPixels * 0.30f).toInt() + holders.size * (72 * d).toInt()
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        }
        val h = Holder(root, strip, cv, owner, lp, expanded, lp.x, lp.y)

        root.setViewTreeLifecycleOwner(owner)
        root.setViewTreeSavedStateRegistryOwner(owner)
        cv.setViewTreeLifecycleOwner(owner)
        cv.setViewTreeSavedStateRegistryOwner(owner)

        cv.setContent {
            AppTheme {
                if (expanded.value) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 4.dp) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { handler.post { close(id) } }) { Text("✕ بستن") }
                                TextButton(onClick = { setExpanded(h, false) }) { Text("– کوچک‌کردن") }
                            }
                        }
                        TxnEditor(id, onLater = { setExpanded(h, false) }) {
                            handler.post { close(id) }
                        }
                    }
                } else {
                    Bubble(id)
                }
            }
        }

        try {
            wm.addView(root, lp)
            holders[id] = h
        } catch (e: Exception) {
            owner.destroy()
        }
    }

    private fun setExpanded(h: Holder, value: Boolean) {
        if (h.expanded.value == value) return
        val dm = resources.displayMetrics
        val lp = h.lp
        if (value) {
            h.savedX = lp.x
            h.savedY = lp.y
            lp.width = (dm.widthPixels * 0.92f).toInt()
            lp.x = (dm.widthPixels - lp.width) / 2
            lp.y = (dm.heightPixels * 0.06f).toInt()
            lp.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            lp.width = WindowManager.LayoutParams.WRAP_CONTENT
            lp.x = h.savedX
            lp.y = h.savedY
            lp.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        h.expanded.value = value
        h.root.collapsed = !value
        h.strip.visibility = if (value) View.VISIBLE else View.GONE
        try { wm.updateViewLayout(h.root, lp) } catch (_: Exception) {}
    }

    private fun move(h: Holder, dx: Int, dy: Int) {
        val dm = resources.displayMetrics
        h.lp.x = (h.lp.x + dx).coerceIn(0, (dm.widthPixels - h.root.width).coerceAtLeast(0))
        h.lp.y = (h.lp.y + dy).coerceIn(0, (dm.heightPixels - h.root.height).coerceAtLeast(0))
        try { wm.updateViewLayout(h.root, h.lp) } catch (_: Exception) {}
    }

    private fun close(id: Long) {
        holders.remove(id)?.let { h ->
            h.cv.disposeComposition()
            try { wm.removeView(h.root) } catch (_: Exception) {}
            h.owner.destroy()
        }
        if (holders.isEmpty()) stopSelf()
    }

    override fun onDestroy() {
        holders.keys.toList().forEach { close(it) }
        super.onDestroy()
    }
}
