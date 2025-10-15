package com.ven.assists.stepper2

import com.blankj.utilcode.util.LogUtils
import com.ven.assists.stepper2.Step.Companion.repeatCountInfinite
import com.ven.assists.stepper2.Step.Companion.showLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.*

/**
 * 步骤执行控制类
 * 用于管理和执行自动化步骤，提供步骤的生命周期管理、状态控制和界面操作功能
 */
class Step(
    val stepId: String,
    val impl: suspend (Step) -> Step?,
    val tag: String? = null,
    val data: Any? = null,
    val delayMs: Long = delayMsDefault,
    val repeatCountMax: Int = repeatCountMaxDefault
) {
    companion object {
        const val delayMsDefault: Long = 1000L
        const val repeatCountInfinite: Int = -1
        var repeatCountMaxDefault: Int = repeatCountInfinite
        var showLog: Boolean = false
        private var coroutine: CoroutineScope = CoroutineScope(Dispatchers.IO)

        private val jobs = hashMapOf<String, Job>()

        fun run(impl: suspend (Step) -> Step?) {
            launch { this.run(impl = impl) }
        }

        fun launch(jobID: String = UUID.randomUUID().toString(), run: suspend StepScope.() -> Unit): Job {
            val job = coroutine.launch(block = { StepScope.run() }, context = Dispatchers.IO)
            jobs[jobID] = job
            return job
        }

        fun stop(jobID: String? = null) {
            jobID?.let {
                val job = jobs[jobID]
                job?.cancel()
                jobs.remove(jobID)
            } ?: let {
                jobs.values.forEach { job ->
                    job.cancel()
                }
                jobs.clear()
            }
        }
    }

    /**
     * 步骤重复执行次数
     */
    var repeatCount: Int = 0
        private set

    /**
     * 延迟执行
     * @param ms 延迟时间(毫秒)
     */
    suspend fun delay(ms: Long) {
        StepScope.assert(stepId)
        kotlinx.coroutines.delay(ms)
        StepScope.assert(stepId)
    }

    /**
     * 创建下一个步骤
     * @param impl 下一步骤实现函数
     * @param config 步骤配置
     * @return 新的步骤实例
     */
    fun next(
        impl: suspend (Step) -> Step?,
        config: StepConfig = StepConfig()
    ): Step {
        StepScope.assert(stepId)
        return Step(
            stepId = stepId,
            impl = impl,
            tag = config.tag ?: tag,
            data = config.data ?: data,
            delayMs = config.delayMs,
            repeatCountMax = config.repeatCountMax
        )
    }

    /**
     * 重复当前步骤
     * @param config 步骤配置
     * @return 当前步骤实例
     */
    fun repeat(config: StepConfig = StepConfig()): Step {
        StepScope.assert(stepId)
        repeatCount++
        return this
    }
}

/**
 * 步骤配置数据类
 */
data class StepConfig(
    val tag: String? = null,
    val data: Any? = null,
    val delayMs: Long = Step.delayMsDefault,
    val repeatCountMax: Int = Step.repeatCountMaxDefault
)

/**
 * 步骤错误异常类
 */
class StepError(
    message: String,
    val impl: String,
    val tag: String?,
    val data: Any?,
    val originalError: Exception,
    val currentStep: Step?
) : RuntimeException(message, originalError)


object StepScope {
    /**
     * 当前执行步骤的ID
     */
    var stepId: String = ""
        private set

    /**
     * 验证步骤ID是否匹配，如果不匹配则表示停止
     * @param stepId 要验证的步骤ID
     */
    fun assert(stepId: String) {
        if (this.stepId.isEmpty() || this.stepId != stepId) {
            throw RuntimeException("StepId mismatch")
        }
    }

    fun discontinue() {
        stepId = ""
    }

    /**
     * 生成UUID
     */
    private fun generateUUID(): String = UUID.randomUUID().toString()

    suspend fun run(
        config: StepConfig = StepConfig(),
        impl: suspend (Step) -> Step?,
    ): Step {
        var implName = impl.javaClass.simpleName
        var currentStep = Step(
            stepId = stepId,
            impl = impl,
            tag = config.tag,
            data = config.data,
            delayMs = config.delayMs,
            repeatCountMax = config.repeatCountMax
        )
        try {
            // 步骤开始
            stepId = generateUUID()

            while (true) {
                if (currentStep.delayMs > 0) {
                    if (showLog) {
                        LogUtils.d("Step", "延迟${currentStep.delayMs}毫秒")
                    }
                    currentStep.delay(currentStep.delayMs)
                    assert(currentStep.stepId)
                }

                // 执行步骤
                implName = currentStep.impl.javaClass.simpleName
                if (showLog) {
                    LogUtils.d("Step", "执行步骤$implName，重复次数${currentStep.repeatCount}")
                }

                val nextStep = currentStep.impl(currentStep)

                if (currentStep.repeatCountMax > repeatCountInfinite &&
                    currentStep.repeatCount > currentStep.repeatCountMax
                ) {
                    if (showLog) {
                        LogUtils.d("Step", "重复次数${currentStep.repeatCount}超过最大次数${currentStep.repeatCountMax}，停止执行")
                    }
                    break
                }

                assert(currentStep.stepId)
                if (nextStep != null) {
                    currentStep = nextStep
                } else {
                    break
                }
            }
        } catch (e: Exception) {
            if (showLog) {
                LogUtils.e("Step", "步骤${implName}执行出错", e)
            }

            // 步骤执行出错
            val errorMsg = """
                    {
                        "impl": "$implName",
                        "tag": "${config.tag}",
                        "data": "${config.data}",
                        "error": "${e.message ?: e.toString()}"
                    }
                """.trimIndent()

            throw StepError(
                errorMsg,
                implName,
                config.tag,
                config.data,
                e,
                currentStep
            )
        }

        // 步骤执行结束
        return currentStep
    }

}
