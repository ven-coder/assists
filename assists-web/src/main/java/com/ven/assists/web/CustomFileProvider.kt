package com.ven.assists.web

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.LogUtils
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import com.ven.assists.mp.MPManager
import com.ven.assists.utils.CoroutineWrapper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

class CustomFileProvider : FileProvider() {


    override fun onCreate(): Boolean {
        val applicationContext = context?.applicationContext
        if (applicationContext is Application) {
            CoroutineWrapper.launch {
                val clearIds = arrayListOf<String>()
                while (true) {
                    runCatching {
                        clearIds.clear()
                        NodeCacheManager.cache.forEach { item ->
                            item.value.let {
                                val node = it.get()
                                if (node == null || !node.refresh()) {
                                    clearIds.add(item.key)
                                }
                            }
                        }

                        clearIds.forEach {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14 以下
                                runCatching { NodeCacheManager.cache[it]?.get()?.recycle() }
                            }
                            NodeCacheManager.cache.remove(it)
                        }
                    }

                    delay(1000)
                }
            }

            applicationContext.unregisterActivityLifecycleCallbacks(activityLifecycleCallbacks)
            applicationContext.registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
        }
        return super.onCreate()
    }

    private val activityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (activity is ComponentActivity && requestLaunchers[activity] == null) {
                requestLaunchers[activity] = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    currentCompletableDeferred?.complete(result)
                }
            }
            if (activity is ComponentActivity && requestLaunchersScan[activity] == null) {
                requestLaunchersScan[activity] = activity.registerForActivityResult(ScanContract()) { result ->
                    currentCompletableDeferredScan?.complete(result)
                }
            }
        }

        override fun onActivityStarted(activity: Activity) {
        }

        override fun onActivityResumed(activity: Activity) {
        }

        override fun onActivityPaused(activity: Activity) {
        }

        override fun onActivityStopped(activity: Activity) {
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        }

        override fun onActivityDestroyed(activity: Activity) {
            requestLaunchers.remove(activity)
            requestLaunchersScan.remove(activity)
        }
    }

    companion object {
        private val requestLaunchers = hashMapOf<Activity, ActivityResultLauncher<Intent>>()
        private var currentCompletableDeferred: CompletableDeferred<ActivityResult>? = null
        private val requestLaunchersScan = hashMapOf<Activity, ActivityResultLauncher<ScanOptions>>()
        private var currentCompletableDeferredScan: CompletableDeferred<ScanIntentResult>? = null

        suspend fun requestLaunchers(intent: Intent): ActivityResult? {
            val result = runCatching {
                currentCompletableDeferred?.completeExceptionally(RuntimeException("reset"))
                currentCompletableDeferred = null
                currentCompletableDeferred = CompletableDeferred<ActivityResult>()
                ActivityUtils.getTopActivity()?.let {
                    requestLaunchers[it]?.launch(intent)
                } ?: return@runCatching null
                return@runCatching currentCompletableDeferred?.await()
            }
            return result.getOrNull()

        }

        suspend fun requestLaunchersScan(scanOptions: ScanOptions): ScanIntentResult? {
            val result = runCatching {
                currentCompletableDeferredScan?.completeExceptionally(RuntimeException("reset"))
                currentCompletableDeferredScan = null
                currentCompletableDeferredScan = CompletableDeferred<ScanIntentResult>()
                ActivityUtils.getTopActivity()?.let {
                    requestLaunchersScan[it]?.launch(scanOptions)
                } ?: return@runCatching null
                return@runCatching currentCompletableDeferredScan?.await()
            }
            return result.getOrNull()

        }
    }

}