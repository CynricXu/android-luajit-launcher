package org.koreader.launcher.device.lights

import android.app.Activity
import android.net.Uri
import android.util.Log
import org.koreader.launcher.device.LightsInterface
import java.io.File

/**
 * iReader Neo 3 Ultra frontlight.
 *
 * Cool channel is sysfs B, warm channel is sysfs A (0-255).
 * SystemUI on/off is driven via the reading-light ContentProvider;
 * Settings.System keys are not writable by third-party apps.
 */
class IReaderNeo3LightsController : LightsInterface {
    companion object {
        private const val TAG = "Lights"
        private const val SYS_A = "/sys/devices/platform/11009000.i2c/i2c-2/2-0036/a_brightness"
        private const val SYS_B = "/sys/devices/platform/11009000.i2c/i2c-2/2-0036/b_brightness"
        private const val HW_MAX = 255
        private const val PROVIDER = "content://com.szzy.ireader.systemui.provider"
        private const val MIN = 0
        private const val MAX = 100
    }

    @Volatile private var lastIntensity = 40
    @Volatile private var lastWarmth = 50

    override fun getPlatform(): String = "ireader"
    override fun hasFallback(): Boolean = false
    override fun hasWarmth(): Boolean = true
    override fun needsPermission(): Boolean = false
    override fun hasStandaloneWarmth(): Boolean = false
    override fun getMinWarmth(): Int = MIN
    override fun getMaxWarmth(): Int = MAX
    override fun getMinBrightness(): Int = MIN
    override fun getMaxBrightness(): Int = MAX

    override fun enableFrontlightSwitch(activity: Activity): Int {
        providerCall(activity, "call_reading_light_custom")
        return 1
    }

    override fun getBrightness(activity: Activity): Int {
        val (cool, warm) = readAb()
        val intensity = fromHw(cool, warm).first
        if (intensity > 0) lastIntensity = intensity
        return intensity
    }

    override fun getWarmth(activity: Activity): Int {
        val (cool, warm) = readAb()
        val warmth = fromHw(cool, warm).second
        lastWarmth = warmth
        return warmth
    }

    override fun setBrightness(activity: Activity, brightness: Int) {
        val intensity = brightness.coerceIn(MIN, MAX)
        lastIntensity = intensity
        apply(activity, intensity, lastWarmth)
        Log.i(TAG, "setBrightness $intensity")
    }

    override fun setWarmth(activity: Activity, warmth: Int) {
        lastWarmth = warmth.coerceIn(MIN, MAX)
        apply(activity, lastIntensity, lastWarmth)
        Log.i(TAG, "setWarmth $lastWarmth")
    }

    private fun apply(activity: Activity, intensity: Int, warmth: Int) {
        val (cool, warm) = mix(intensity, warmth)
        writeAb(cool, warm)
        if (cool + warm > 0) {
            providerCall(activity, "call_reading_light_custom")
            writeAb(cool, warm)
        } else {
            providerCall(activity, "call_reading_light_off")
        }
    }

    private fun readSys(path: String): Int {
        return try {
            File(path).readText().trim().toInt()
        } catch (_: Exception) {
            0
        }
    }

    private fun writeSys(path: String, value: Int) {
        try {
            File(path).writeText(value.coerceIn(0, HW_MAX).toString())
        } catch (e: Exception) {
            Log.w(TAG, "cannot write $path: $e")
        }
    }

    private fun readAb(): Pair<Int, Int> = readSys(SYS_B) to readSys(SYS_A)

    private fun writeAb(cool: Int, warm: Int) {
        writeSys(SYS_A, warm)
        writeSys(SYS_B, cool)
    }

    private fun mix(intensity: Int, warmth: Int): Pair<Int, Int> {
        if (intensity <= 0) return 0 to 0
        val coolW = 100 - warmth
        val warmW = warmth
        val peak = maxOf(coolW, warmW, 1)
        val cool = intensity * coolW / peak * HW_MAX / 100
        val warm = intensity * warmW / peak * HW_MAX / 100
        return cool to warm
    }

    private fun fromHw(cool: Int, warm: Int): Pair<Int, Int> {
        val intensity = (maxOf(cool, warm) * 100 + HW_MAX / 2) / HW_MAX
        if (cool + warm <= 0) return intensity to lastWarmth
        val warmth = (warm * 100 + (cool + warm) / 2) / (cool + warm)
        return intensity to warmth
    }

    private fun providerCall(activity: Activity, method: String) {
        try {
            activity.contentResolver.call(Uri.parse(PROVIDER), method, null, null)
        } catch (e: Exception) {
            Log.w(TAG, "provider $method: $e")
        }
    }
}
