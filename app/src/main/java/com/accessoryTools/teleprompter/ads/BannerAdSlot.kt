package com.accessoryTools.teleprompter.ads

import android.view.Gravity
import android.widget.FrameLayout
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.accessoryTools.teleprompter.BuildConfig
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

private const val BANNER_MAX_HEIGHT_DP = 60

/**
 * A persistent, adaptive banner. A fresh test ad is requested every 30 seconds.
 * Passing [enabled] = false pauses rotation and removes the ad immediately, so
 * the slot is not active during saves, imports, backup/restore, editing, or
 * recording flows.
 */
@Composable
fun BannerAdSlot(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    rotationDeferred: Boolean = false,
) {
    // Screenshot flavors (agents) run ad-free; nothing renders at all.
    if (BuildConfig.AD_FREE) {
        return
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    // Handset landscape (screen height < 480dp) gets a smaller banner so it does
    // not swallow a third of the viewport. Still labelled ADVERTISEMENT.
    val config = LocalConfiguration.current
    val shortLandscape = config.screenWidthDp > config.screenHeightDp && config.screenHeightDp < 480
    val bannerMaxHeightDp = if (shortLandscape) 40 else BANNER_MAX_HEIGHT_DP
    val bannerMaxHeight = if (shortLandscape) 40.dp else 60.dp
    val bannerMinHeight = if (shortLandscape) 36.dp else 50.dp

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(top = 8.dp)
                .border(width = 2.dp, color = Color.Black),
    ) {
        Text(
            text = "ADVERTISEMENT",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 4.dp, bottom = 2.dp),
            color = Color.Black.copy(alpha = 0.65f),
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            letterSpacing = 1.sp,
        )
        if (enabled && AdsInitialization.ready && AdsInitialization.consentReady) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().heightIn(min = bannerMinHeight, max = bannerMaxHeight),
            ) {
                val widthDp = maxWidth.value.roundToInt().coerceAtLeast(1)
                val context = LocalContext.current
                // One reusable AdView per width; issue a fresh request every
                // 30 seconds instead of tearing down the WebView each rotation.
                val adView =
                    remember(widthDp) {
                        AdView(context).apply {
                            adUnitId = AdConfig.BANNER_ID
                            setAdSize(
                                AdSize.getInlineAdaptiveBannerAdSize(
                                    widthDp,
                                    bannerMaxHeightDp,
                                ),
                            )
                        }
                    }
                var loadInFlight by remember(adView) { mutableStateOf(false) }
                DisposableEffect(adView) {
                    val listener =
                        object : AdListener() {
                            override fun onAdLoaded() {
                                loadInFlight = false
                            }

                            override fun onAdFailedToLoad(error: LoadAdError) {
                                loadInFlight = false
                            }
                        }
                    adView.adListener = listener
                    onDispose { }
                }
                LaunchedEffect(enabled, adView, lifecycleOwner, rotationDeferred) {
                    // Request and rotate only while the host activity is
                    // resumed. This avoids ad requests while the app is
                    // backgrounded or covered. While the host list is scrolling
                    // we skip the request instead — reloading the WebView mid-
                    // scroll is a visible hitch.
                    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        while (isActive) {
                            if (!rotationDeferred && !loadInFlight) {
                                loadInFlight = true
                                adView.loadAd(AdRequest.Builder().build())
                            }
                            delay(AdConfig.BANNER_ROTATION_MS)
                        }
                    }
                }
                key(widthDp) {
                    AndroidView(
                        factory = { context ->
                            FrameLayout(context).apply {
                                setBackgroundColor(0xFFF5F5DC.toInt())
                                addView(
                                    adView,
                                    FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.WRAP_CONTENT,
                                        FrameLayout.LayoutParams.WRAP_CONTENT,
                                        Gravity.CENTER,
                                    ),
                                )
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = bannerMinHeight, max = bannerMaxHeight)
                                .clipToBounds(),
                        onRelease = { frame ->
                            (frame.getChildAt(0) as? AdView)?.destroy()
                            frame.removeAllViews()
                        },
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = bannerMinHeight + 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text =
                        if (AdsInitialization.failed) {
                            "Advertisement unavailable"
                        } else if (enabled) {
                            "Loading advertisement…"
                        } else {
                            "Ad paused while this screen finishes saving"
                        },
                    color = Color.Black.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}
