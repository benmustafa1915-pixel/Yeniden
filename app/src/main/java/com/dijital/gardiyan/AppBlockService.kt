package com.dijital.gardiyan

import android.accessibilityservice.AccessibilityService
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class AppBlockService : AccessibilityService() {
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var strikeCount = 0
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, AdminReceiver::class.java)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        // Kendini ve Launcher'ı asla engelleme (Telefon kilitlenmesin diye)
        if (pkg == "com.dijital.gardiyan" || isLauncher(pkg)) return

        val prefs = getSharedPreferences("GardiyanPrefs", Context.MODE_PRIVATE)
        val blockedApps = prefs.getStringSet("blockedApps", emptySet()) ?: emptySet()

        if (blockedApps.contains(pkg)) {
            // Zamanlayıcı (Sıkı Mod) kontrolü
            val unlockTime = prefs.getLong("unlockTime", 0)
            if (System.currentTimeMillis() < unlockTime || unlockTime == 0L) {
                executeGhostMode(pkg)
            }
        }
    }

    private fun executeGhostMode(pkg: String) {
        // 1. Uygulamayı anında öldür
        val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        am.killBackgroundProcesses(pkg)

        // 2. 3 Saniyelik Siyah Perde
        showOverlay()

        // 3. 3 Strike Kuralı (Üst üste 3 kez girilirse telefonu kilitle)
        strikeCount++
        if (strikeCount >= 3) {
            strikeCount = 0
            if (dpm.isAdminActive(adminComponent)) {
                dpm.lockNow() // Telefonu kilitle
            }
        }

        // 4. Geri tuşuna bas ve Ana Ekrana dön
        performGlobalAction(GLOBAL_ACTION_BACK)
        Handler(Looper.getMainLooper()).postDelayed({
            performGlobalAction(GLOBAL_ACTION_HOME)
        }, 100)
    }

    private fun showOverlay() {
        if (overlayView != null) return
        overlayView = View(this).apply { setBackgroundColor(android.graphics.Color.parseColor("#E6000000")) }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(overlayView, params)
        object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                if (overlayView != null) {
                    windowManager.removeView(overlayView)
                    overlayView = null
                }
            }
        }.start()
    }

    private fun isLauncher(packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val res = packageManager.resolveActivity(intent, 0)
        return res?.activityInfo?.packageName == packageName
    }
    override fun onInterrupt() {}
}
