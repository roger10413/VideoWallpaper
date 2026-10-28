package com.ming.videowallpaper

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Full-screen carrier barcode at maximum brightness. Declared showWhenLocked, so tapping the
 * notification on the lock screen opens it without unlocking. Tap anywhere to close.
 */
class CarrierActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }

        val code = WallpaperSettings(this).carrierCode
        if (!Code39.isValidCarrier(code)) {
            finish()
            return
        }
        // Render at the exact pixel size shown, so bars stay sharp (no scaling blur).
        val screenWidth = resources.displayMetrics.widthPixels
        val moduleWidth = (screenWidth * 0.9f / Code39.totalModules(code)).toInt().coerceAtLeast(2)
        val barcode = Code39.render(code, moduleWidth, height = screenWidth / 4).asImageBitmap()

        setContent {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .clickable { finish() },
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("手機條碼", color = Color.Black, fontSize = 22.sp)
                Spacer(Modifier.height(24.dp))
                Image(
                    barcode,
                    contentDescription = "手機條碼 $code",
                    contentScale = ContentScale.None,
                    filterQuality = FilterQuality.None,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    code,
                    color = Color.Black,
                    fontSize = 36.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(48.dp))
                Text("點一下關閉", color = Color.Gray, fontSize = 14.sp)
            }
        }
    }
}
