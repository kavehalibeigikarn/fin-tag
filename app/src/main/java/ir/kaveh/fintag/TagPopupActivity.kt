package ir.kaveh.fintag

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** پاپ‌آپ جایگزین (وقتی از روی اعلان باز می‌شود یا مجوز نمایش روی سایر برنامه‌ها داده نشده) */
class TagPopupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getLongExtra("id", -1L)
        if (id <= 0) { finish(); return }
        setContent {
            AppTheme {
                Box(
                    Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    TxnEditor(id) { finish() }
                }
            }
        }
    }
}
