package com.open.wuling

import android.app.Application
import android.os.Build
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import java.io.BufferedReader
import java.io.FileReader

@HiltAndroidApp
class WulingApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Set crash handler for non-recovery process
        if (!isRecoveryProcess()) {
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                Log.e("WulingApp", "Uncaught exception in thread ${thread.name}", throwable)
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun isRecoveryProcess(): Boolean {
        val processName = getProcessNameSafely()
        return processName.endsWith(":recovery")
    }

    /**
     * Safely get the current process name with multiple fallbacks.
     * NOTE: Direct call to Process.myProcessName() causes NoSuchMethodError on some
     * Android 9 ROMs. R8 eliminates the catch block as dead code because compileSdk 34
     * includes the method. Use reflection to bypass R8 optimization.
     */
    private fun getProcessNameSafely(): String {
        if (Build.VERSION.SDK_INT >= 28) {
            // Use reflection — R8 removes try-catch around direct API calls that exist in compileSdk
            try {
                val name = Class.forName("android.os.Process")
                    .getMethod("myProcessName")
                    .invoke(null) as? String
                if (!name.isNullOrEmpty()) return name
            } catch (_: Throwable) {
            }

            try {
                val name = getProcessName()
                if (!name.isNullOrEmpty()) return name
            } catch (_: Throwable) {
            }
        }

        try {
            BufferedReader(FileReader("/proc/self/cmdline")).use { reader ->
                val name = reader.readLine()?.trim()
                if (!name.isNullOrEmpty()) return name
            }
        } catch (_: Throwable) {
        }

        return packageName
    }
}
