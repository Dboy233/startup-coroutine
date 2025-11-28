package com.dboy.startup.coroutine

import android.app.Application
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.dboy.startup.coroutine.api.IStartup
import com.dboy.startup.coroutine.api.Initializer
import com.dboy.startup.coroutine.imp.StartupImpl
import com.dboy.startup.coroutine.model.StartupResult
import kotlinx.coroutines.Job


/**
 * 一个基于协程的、支持依赖关系、并行化的高级启动框架。
 */
open class Startup private constructor(
    private val implementation: IStartup
) : IStartup {
    companion object {

        private val _isInitialized = MutableLiveData<StartupResult>(StartupResult.Idle)

        fun observe(owner: LifecycleOwner, observer: Observer<StartupResult>) {
            _isInitialized.observe(owner, observer)
        }

        fun isInitialized() = _isInitialized.value != StartupResult.Idle

        fun initializedResult() = _isInitialized.value

        fun reset() {
            _isInitialized.postValue(StartupResult.Idle)
        }

        internal fun markInitializedResult(result: StartupResult) {
            _isInitialized.postValue(result)
        }
    }


    override fun start(): Job {
        return implementation.start()
    }

    override fun cancel() {
        implementation.cancel()
    }

    /**
     * 建造者类，用于配置和创建 [Startup] 实例。
     */
    class Builder(private val application: Application) {
        private var isDebug: Boolean = false
        private var dispatchers: StartupDispatchers = DefaultDispatchers
        private val initializers: MutableList<Initializer<*>> = mutableListOf()

        @Deprecated(
            message = "use `Startup.observe` subscribe to the results",
            replaceWith = ReplaceWith("Startup.observe"),
        )
        private var onResult: ((StartupResult) -> Unit)? = null

        fun setDebug(isDebug: Boolean): Builder =
            apply { this.isDebug = isDebug }

        fun setDispatchers(dispatchers: StartupDispatchers): Builder =
            apply { this.dispatchers = dispatchers }

        fun add(initializer: Initializer<*>): Builder =
            apply { this.initializers.add(initializer) }

        fun add(initializers: List<Initializer<*>>): Builder =
            apply { this.initializers.addAll(initializers) }

        fun add(vararg initializers: Initializer<*>): Builder =
            apply { this.initializers.addAll(initializers) }

        @Deprecated(
            message = "use `Startup.observe` subscribe to the results",
            replaceWith = ReplaceWith("Startup.observe"),
        )
        fun setOnResult(onResult: (StartupResult) -> Unit): Builder =
            apply { this.onResult = onResult }

        /**
         * 根据当前配置构建一个 [Startup] 实例。
         *
         * @return 配置完成的 Startup 实例。
         */
        fun build(): Startup {
            val impl = StartupImpl(
                application = application,
                isDebug = isDebug,
                dispatchers = dispatchers,
                initializers = initializers,
                onResult = onResult
            )
            return Startup(impl)
        }
    }
}