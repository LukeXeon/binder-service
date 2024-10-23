package binderservice

import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log

@SuppressLint("PrivateApi")
object AppGlobals {

    private const val TAG = "AppGlobals"

    lateinit var application: Application
        internal set

    init {
        try {
            val activityThreadClazz = Class.forName("android.app.ActivityThread")
            application = activityThreadClazz.getDeclaredMethod("currentApplication").apply {
                isAccessible = true
            }.invoke(null) as Application
        } catch (e: Exception) {
            Log.e(TAG, "init", e)
        }
    }

    val processName: String = run {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            val activityThreadClazz = Class.forName("android.app.ActivityThread")
            activityThreadClazz.getDeclaredMethod("currentProcessName")
                .apply {
                    isAccessible = true
                }.invoke(null) as String
        }
    }

    val isDebuggable: Boolean
        get() {
            return application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        }
}