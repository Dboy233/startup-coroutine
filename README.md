<div align="center">
  <h1 align="center">Startup-Coroutine</h1>
  <p align="center">
    一个基于 Kotlin 协程的 Android 启动框架，优雅地管理您的应用初始化流程。
    <br>
    <a href="#-核心特性"><strong>探索特性 »</strong></a>
    <br>
    <br>
    <a href="https://github.com/Dboy233/startup-coroutine/issues">报告 Bug</a>
    ·
    <a href="https://github.com/Dboy233/startup-coroutine/issues">提出新特性</a>
  </p>
</div>

***

`startup-coroutine` 是一个为 Android 设计的、基于 Kotlin 协程的异步启动框架。它通过拓扑排序智能地管理复杂的初始化依赖关系，并利用协程的强大能力实现任务的并行化，从而显著缩短应用的启动时间。框架设计精良，具备高级错误处理和生命周期管理能力，让您的应用初始化流程更健壮、更高效、更易于维护。

## 📖 目录

*   [✨ 核心特性](#-核心特性)
*   [🚀 快速上手](#-快速上手)
    *   [第一步：定义初始化任务](#第一步定义初始化任务)
    *   [第二步：配置并启动框架](#第二步配置并启动框架)
*   [🧩 核心 API 解析](#-核心-api-解析)
    *   [`Initializer<T>`](#initializert)
    *   [`InitMode`](#initmode)
    *   [`Startup`](#startup)
    *   [`DependenciesProvider`](#dependenciesprovider)
*   [🔧 高级用法](#-高级用法)
    *   [异常处理机制](#异常处理机制)
    *   [循环依赖检测](#循环依赖检测)
*   [🤝 贡献指南](#-贡献指南)
*   [📄 许可证](#-许可证)

## ✨ 核心特性

*   **🔗 依赖管理**: 自动解析并按拓扑顺序执行任务，精确处理任务间的依赖关系。
*   **⚡ 并行执行**: 无依赖关系的任务将自动在后台线程池中并行执行，充分利用多核 CPU 资源，加速启动过程。
*   **🛡️ 异常隔离**: 采用 `supervisorScope` 隔离并行任务，确保单个任务的失败不会导致整个启动流程崩溃。
*   **📊 统一错误报告**: 通过 `onError` 回调聚合所有发生的异常，方便统一记录和处理。
*   **🤚 可取消**: 支持随时安全地取消整个启动流程，并正确处理相关的资源释放。
*   **🍃 轻量级**: 基于 Kotlin 协程，核心逻辑简洁，对项目侵入性小。

## 🚀 快速上手

### 第一步：定义初始化任务

每个初始化单元都应继承 `Initializer<T>` 抽象类，并实现其核心方法。

*   **`init(context, provider)`**: 包含实际的初始化逻辑。这是一个挂起函数，可执行耗时操作。
*   **`dependencies()`**: (可选) 声明当前任务所依赖的其他 `Initializer` 任务。
*   **`initMode()`**: (可选) 定义执行模式，默认为 `InitMode.SERIAL` (主线程串行)。

**示例：定义两个任务**

一个用于初始化分析服务的并行任务（`AnalyticsInitializer`），以及另一个依赖于它的广告 SDK 初始化任务（`AdsInitializer`）。

```kotlin

// AnalyticsInitializer.kt
// 一个模拟耗时并返回 SDK 对象的并行任务
class AnalyticsInitializer : Initializer<AnalyticsSDK>() {

    override suspend fun init(context: Context, provider: DependenciesProvider): AnalyticsSDK {
        // 模拟一个耗时的 I/O 操作
        delay(1000)
        println("分析服务 SDK 已在线程初始化: \${Thread.currentThread().name}")
        return AnalyticsSDK("Analytics-SDK-Instance")
    }

    // 在后台线程池中运行此任务
    override fun initMode(): InitMode = InitMode.PARALLEL

}

// AdsInitializer.kt
// 一个依赖于 AnalyticsInitializer 的串行任务
class AdsInitializer : Initializer<Unit>() {

    override suspend fun init(context: Context, provider: DependenciesProvider) {
        // 从依赖提供者处获取依赖项的结果
        val analyticsSDK = provider.result<AnalyticsSDK>(AnalyticsInitializer::class)
        println("广告 SDK 正在使用: ${analyticsSDK.name}，位于线程: ${Thread.currentThread().name}")
        // 在此处进行广告 SDK 的初始化...
    }

    // 定义依赖关系
    override fun dependencies(): List<KClass<out Initializer<*>>> {
        return listOf(AnalyticsInitializer::class)
    }

    // 在主线程上运行此任务 (默认行为)
    override fun initMode(): InitMode = InitMode.SERIAL

}

// 用于示例的虚拟类
data class AnalyticsSDK(val name: String)
```

### 第二步：配置并启动框架

在您的 `Application` 类或其他合适的入口点，创建 `Startup` 实例并传入任务列表。

```kotlin
// MyApplication.kt
class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val startup = Startup(
            context = this,
            initializers = listOf(AnalyticsInitializer(), AdsInitializer()),
            onCompletion = {
                // 此回调在主线程上调用
                println("🎉 所有启动任务已成功完成！")
            },
            onError = { errors ->
                // 此回调在后台线程上调用
                println("🔥 启动流程失败，共出现 ${errors.size} 个错误:")
                errors.forEach { error ->
                    println("   - ${error.message}")
                }
            }
        )

        // 启动初始化流程
        startup.start()
    }

}
```

## 🧩 核心 API 解析

### `Initializer<T>`

所有初始化任务的基类。

*   `init(context: Context, provider: DependenciesProvider): T`: 您的初始化逻辑所在地。这是一个挂起函数，可以安全地执行长时间运行的操作。
*   `dependencies(): List<KClass<out Initializer<*>>>`: 指定该任务所依赖的其他任务。
*   `initMode(): InitMode`: 决定任务是在主线程 (`SERIAL`) 还是后台线程 (`PARALLEL`) 执行。

### `InitMode`

一个定义 `Initializer` 执行模式的枚举。

*   `SERIAL`: 任务将在 **主线程** 上串行执行。适用于与 UI 相关或需要严格顺序的轻量级任务。
*   `PARALLEL`: 任务将在 **后台线程池** (`Dispatchers.Default`) 中并行执行。是 I/O 或 CPU 密集型工作的理想选择。

### `Startup`

管理和执行初始化流程的核心类。

*   `start()`: 启动整个初始化流程。此方法是线程安全的，且只能成功调用一次。
*   `cancel()`: 取消所有正在进行的初始化任务。

### `DependenciesProvider`

一个传递给 `init()` 方法的接口，允许任务获取其依赖项的执行结果。

*   `result<T>(dependency: KClass<out Initializer<*>>): T`: 获取一个已完成依赖项的结果。如果结果不可用（例如，依赖项失败、未注册或返回 Unit），则会抛出 `IllegalStateException`。
*   `resultOrNull<T>(dependency: KClass<out Initializer<*>>): T?`: **（推荐）** 安全地获取结果，如果结果不可用则返回 `null`。

## 🔧 高级用法

### 异常处理机制

框架能够聚合所有来自并行任务的异常。如果一个串行任务失败，整个启动流程将立即终止并报告异常。如果一个或多个并行任务失败，框架会等待所有其他并行任务完成后，通过 `onError` 回调一次性报告所有收集到的异常。

### 循环依赖检测

框架在启动时会自动进行拓扑排序，如果检测到初始化任务之间存在循环依赖，它会抛出 `IllegalStateException`，从而防止在运行时出现死锁。

此外，框架还包含一个重要的验证规则：**串行任务不能依赖于并行任务**。这是因为串行任务按序在主线程执行，而并行任务的完成时机不确定，这种依赖关系会破坏主线程的执行顺序并可能导致难以预料的行为。

## 🤝 贡献指南

欢迎各种形式的贡献！无论是提交 Bug、提出新功能建议还是直接贡献代码，我们都非常欢迎。

1.  Fork 本仓库
2.  创建您的特性分支 (`git checkout -b feature/AmazingFeature`)
3.  提交您的更改 (`git commit -m 'Add some AmazingFeature'`)
4.  推送到分支 (`git push origin feature/AmazingFeature`)
5.  创建一个 Pull Request

## 📄 许可证

本项目采用 Apache 2.0 许可证。详情请参阅 [LICENSE](https://github.com/Dboy233/startup-coroutine/blob/master/LICENSE) 文件。
