package com.ven.assists.simple

import android.app.Application
import com.blankj.utilcode.util.Utils
import com.ven.assists.log.AssistsLogDiagnostics
import com.ven.assists.stepper.StepManager

class App : Application() {

    companion object {
        const val TARGET_PACKAGE_NAME = "com.tencent.mm"

        /** 与 assets 中 environment.env 文件名一致（该文件可本地创建并加入 gitignore） */
        private const val ASSETS_ENV_FILE_NAME = ".env"
    }

    override fun onCreate() {
        super.onCreate()
        Utils.init(this)
        applyUploadKeyFromAssetsEnv()
        //设置全局步骤默认间隔时长
        StepManager.DEFAULT_STEP_DELAY = 1000L
    }

    /**
     * 从 assets/environment.env 读取 UPLOAD_KEY 并注入 assists-log；文件缺失或解析失败时沿用库内默认值
     */
    private fun applyUploadKeyFromAssetsEnv() {
        runCatching {
            assets.open(ASSETS_ENV_FILE_NAME).bufferedReader().use { it.readText() }
        }.map { parseUploadKeyFromEnvText(it) }
            .onSuccess { key ->
                if (!key.isNullOrBlank()) {
                    AssistsLogDiagnostics.setUploadKey(key)
                }
            }
    }

    private fun parseUploadKeyFromEnvText(content: String): String? {
        for (raw in content.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val name = line.substring(0, eq).trim()
            if (name != "UPLOAD_KEY") continue
            return line.substring(eq + 1).trim().trim('"').trim('\'')
        }
        return null
    }
}