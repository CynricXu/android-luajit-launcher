package org.koreader.launcher.device.epd

import android.util.Log
import android.view.View
import org.koreader.launcher.device.EPDInterface

/**
 * iReader Neo 3 Ultra (RM06L) eink controller.
 *
 * The device compositor applies PAGE_H (water-ripple) when
 * `EPDCDevice.nativePostCommand("next-effect-type N")` is issued
 * before the next Surface commit / FBSurface invalidate.
 * N encodes rotation + direction; bit 6 (64) is medium speed.
 */
class IReaderNeo3EPDController : EPDInterface {
    companion object {
        private const val TAG = "EPD"
        private const val INVALIDATE_MODE = 16777218

        fun prepareRipple(effect: Int) {
            if (effect == 0) return
            try {
                val epdc = Class.forName("android.eink.EPDCDevice")
                val post = epdc.getMethod("nativePostCommand", String::class.java)
                post.invoke(null, "next-effect-type $effect")
                Log.i(TAG, "next-effect-type $effect")
            } catch (t: Throwable) {
                Log.e(TAG, "nativePostCommand failed", t)
            }
        }
    }

    override fun getPlatform(): String = "ireader"
    override fun getMode(): String = "all"
    override fun needsView(): Boolean = true
    override fun getWaveformFull(): Int = 1
    override fun getWaveformPartial(): Int = 2
    override fun getWaveformFullUi(): Int = 3
    override fun getWaveformPartialUi(): Int = 4
    override fun getWaveformFast(): Int = 5
    override fun getWaveformDelay(): Int = 0
    override fun getWaveformDelayUi(): Int = 0
    override fun getWaveformDelayFast(): Int = 0
    override fun resume() {}
    override fun pause() {}

    override fun setEpdMode(
        targetView: View,
        mode: Int,
        delay: Long,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        epdMode: String?
    ) {
        try {
            val fb = targetView.javaClass.getMethod("getFBSurface").invoke(targetView) ?: return
            try {
                fb.javaClass.getMethod(
                    "invalidate",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                ).invoke(fb, x, y, width, height, INVALIDATE_MODE)
            } catch (_: Throwable) {
                fb.javaClass.getMethod("invalidate").invoke(fb)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "FBSurface invalidate skipped: ${t.message}")
        }
    }
}
