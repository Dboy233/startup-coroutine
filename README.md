<div align="center">
  <h1 align="center">Startup-Coroutine</h1>
  <p align="center">
    一个基于 Kotlin 协程的 Android 启动框架，优雅地管理您的应用初始化流程。
    <br>
    <a href="#-核心特性"><strong>探索特性 »</strong></a>
    <br>
    <br>
    <a href="README_EN.md">English</a>
    ·
    <a href="https://github.com/Dboy233/startup-coroutine/issues">报告 Bug</a>
    ·
    <a href="https://github.com/Dboy233/startup-coroutine/issues">提出新特性</a>
  </p>
</div>

***

`startup-coroutine` 是一个为 Android 设计的、基于 Kotlin 协程的异步启动框架。它通过拓扑排序智能地管理复杂的初始化依赖关系，并利用协程的强大能力实现任务的并行化，从而显著缩短应用的启动时间。框架设计精良，具备高级错误处理和生命周期管理能力，让您的应用初始化流程更健壮、更高效、更易于维护。

## 📖 目录

*   [✨ 核心特性](#-核心特性)
*   [📥 下载与集成](#-下载与集成)
    *   [第一步：添加 JitPack 仓库](#第一步添加-jitpack-仓库)
    *   [第二步：添加依赖](#第二步添加依赖)
*   [🚀 快速上手](#-快速上手)
    *   [第一步：定义初始化任务](#第一步定义初始化任务)
    *   [第二步：配置并启动框架](#第二步配置并启动框架)
    *   [第三步：监听启动结果](#第三步监听启动结果)
*   [🧩 核心 API 解析](#-核心-api-解析)
    *   [`Initializer<T>`](#initializert)
    *   [`Startup.Builder`](#startupbuilder)
    *   [`StartupDispatchers`](#startupdispatchers)
    *   [`DependenciesProvider`](#dependenciesprovider)
*   [🔧 高级用法](#-高级用法)
    *   [异常处理机制](#异常处理机制)
    *   [循环依赖检测](#循环依赖检测)
*   [🆚 与 Jetpack App Startup 对比](#-与-Jetpack-App-Startup-对比)
*   [🤝 贡献指南](#-贡献指南)
*   [📄 许可证](#-许可证)

## ✨ 核心特性

*   **🔗 依赖管理**: 自动解析并按拓扑顺序执行任务，精确处理任务间的依赖关系。
*   **⚡ 协程优先**: 原生支持 `suspend` 函数，轻松处理异步初始化（如网络请求、数据库迁移）。
*   **🧵 灵活调度**: 提供多种线程调度策略（全主线程、全IO线程、IO执行主线程回调等），适应不同场景。
*   **🚀 极致性能**: 框架自身的拓扑排序与调度逻辑在后台执行，对主线程几乎零干扰。
*   **🛡️ 异常隔离**: 采用 `supervisorScope` 隔离并行任务，确保单个任务的失败不会导致整个启动流程崩溃。
*   **👀 生命周期感知**: 通过 `LiveData` 观察启动结果，完美适配 Activity/Fragment 生命周期。
*   **🤚 可取消**: 返回标准协程 `Job`，支持随时安全地取消整个启动流程。

## 📥 下载与集成

### 第一步：添加 JitPack 仓库

在您的根项目 `settings.gradle.kts` (或 `settings.gradle`) 文件中，添加 JitPack 仓库地址。

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") } // <-- 添加这一行
    }
}
```

### 第二步：添加依赖

<a href="https://jitpack.io/#Dboy233/startup-coroutine"><img src="https://jitpack.io/v/Dboy233/startup-coroutine.svg"></a>

在您需要使用此框架的模块（通常是 `app` 模块）的 `build.gradle.kts` 文件中，添加依赖项。请将 `Tag` 替换为最新的版本号。

```kotlin
// app/build.gradle.kts
dependencies {
    // ... 其他依赖
    implementation("com.github.Dboy233:startup-coroutine:Tag")
}
```

您可以通过上方的版本角标查看最新的版本号。

## 🚀 快速上手

### 第一步：定义初始化任务

每个初始化单元都需要实现 `Initializer<T>` 接口。

*   **`init(application, provider)`**: 包含实际的初始化逻辑。这是一个 `suspend` 函数。
*   **`dependencies()`**: (可选) 声明当前任务所依赖的其他 `Initializer` 类。

**示例：定义两个任务**

一个用于初始化网络库的任务（`NetworkInitializer`），以及一个依赖于它的 API 服务初始化任务（`ApiServiceInitializer`）。

```kotlin
// 1. 定义一个产出 Retrofit 实例的任务
class NetworkInitializer : Initializer<Retrofit> {

    override suspend fun init(application: Application, provider: DependenciesProvider): Retrofit {
        // 这是一个 suspend 函数，适合执行耗时操作
        // 注意：默认情况下 init 在主线程被调用(取决于 Dispatchers 配置)，
        // 如果有繁重 I/O，建议使用 withContext(Dispatchers.IO) 或配置 StartupDispatchers.ExecuteOnIO
        return Retrofit.Builder()
            .baseUrl("https://api.example.com")
            .build()
    }
}

// 2. 定义一个依赖于 NetworkInitializer 的任务
class ApiServiceInitializer : Initializer<MyApiService> {

    // 声明依赖关系
    override fun dependencies(): List<KClass<out Initializer<*>>> {
        return listOf(NetworkInitializer::class)
    }

    override suspend fun init(application: Application, provider: DependenciesProvider): MyApiService {
        // 获取依赖项的结果。如果依赖项失败，ApiServiceInitializer 不会执行init
        val retrofit = provider.result<Retrofit>(NetworkInitializer::class)
        
        return retrofit.create(MyApiService::class.java)
    }
}
```

### 第二步：配置并启动框架

在您的 `Application` 类中，使用 `Startup.Builder` 构建并启动框架。

```kotlin
class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val startup = Startup.Builder(this)
            .add(NetworkInitializer())       // 添加任务
            .add(ApiServiceInitializer())    // 添加任务
            .setDispatchers(StartupDispatchers.ExecuteOnIO) // 设置线程策略：在IO线程初始化执行，主线程创建框架
            .setDebug(true)                  // 开启调试模式，输出详细日志
            .build()

        // 启动初始化流程。此调用是非阻塞的，会立即返回一个 Job。
        startup.start()
    }
}
```

### 第三步：监听启动结果

框架提供了基于 `LiveData` 的结果观察机制。您可以在 `SplashActivity` 或 `MainActivity` 中监听启动是否完成。

```kotlin
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 观察启动结果
        Startup.observe(this) { result ->
            when (result) {
                is StartupResult.Success -> {
                    Log.d("Startup", "🎉 所有任务完成！")
                    goToMainActivity()
                }
                is StartupResult.Failure -> {
                    Log.e("Startup", "🔥 启动失败: ${result.exceptions.size} 个错误")
                    // 处理错误，例如弹窗提示或重试
                }
                StartupResult.Idle -> {
                    // 尚未开始或已重置
                }
            }
        }
    }
}
```

## 🧩 核心 API 解析

### `Initializer<T>`

核心接口，定义一个初始化单元。

*   `suspend fun init(application: Application, provider: DependenciesProvider): T`: 执行初始化逻辑。
*   `fun dependencies(): List<KClass<out Initializer<*>>>`: 返回依赖的任务列表。

### `Startup.Builder`

用于构建 `Startup` 实例的建造者。

*   `add(Initializer<*>)`: 添加单个或多个任务。
*   `setDispatchers(StartupDispatchers)`: 配置线程调度策略。
*   `setDebug(Boolean)`: 是否输出调试日志（包含耗时统计和依赖图）。
*   `build()`: 创建 `Startup` 实例。

### `StartupDispatchers`

预置的线程调度策略，控制任务在哪个线程执行以及在哪个线程启动。

| 策略                | 说明                                      | 适用场景                  |
| :---------------- | :-------------------------------------- | :-------------------- |
| **`Default`**     | **(推荐)** 启动/排序在 IO 线程，`init` 在 **主线程**。 | 大多数包含 UI 初始化的场景。      |
| **`ExecuteOnIO`** | 启动/排序在主线程，`init` 在 **IO 线程**。           | 任务主要是耗时 I/O (数据库、网络)。 |
| **`AllIO`**       | 全流程都在 IO 线程。                            | 完全无 UI 操作的后台初始化。      |
| **`AllMain`**     | 全流程都在主线程。                               | 仅适用于极轻量级的任务集合。        |

### `DependenciesProvider`

传递给 `init` 方法的参数，用于获取上游依赖的结果。

*   `result<T>(class)`: 获取结果，如果不存在或类型不匹配抛出异常。
*   `resultOrNull<T>(class)`: 安全获取结果，失败返回 null。

## 🔧 高级用法

### 异常处理机制

框架会收集所有并行任务中的异常。

*   如果发生异常，`Startup.observe` 会收到 `StartupResult.Failure`。
*   `Failure` 对象包含一个 `exceptions` 列表，您可以遍历它查看具体的错误原因。
*   使用 `setDebug(true)` 可以在 Logcat 中看到详细的错误堆栈和对应的任务名称。

### 循环依赖检测

在 `build()` 之后，调用 `start()` 时，框架会自动执行拓扑排序。如果检测到循环依赖（例如 A 依赖 B，B 依赖 A），会立即抛出 `IllegalStateException`，帮助您在开发阶段发现结构问题。

## 🆚 与 Jetpack App Startup 对比

| 特性       | Jetpack App Startup    | startup-coroutine        | 优势说明                                                                                         |
| :------- | :--------------------- | :----------------------- | :------------------------------------------------------------------------------------------- |
| **异步能力** | 不支持 (同步阻塞)             | **原生支持协程**               | **startup-coroutine** 的 `init` 是 `suspend` 函数，天然适配现代 Android 开发 (Room, Retrofit, DataStore)。 |
| **线程控制** | 默认主线程                  | **高度可配置**                | 可一键切换全 IO 线程执行，避免 ANR。                                                                       |
| **依赖参数** | 无 (通过 ContentProvider) | **DependenciesProvider** | 支持上游任务向下游传递初始化结果（如 OkHttp 实例传给 Retrofit）。                                                    |
| **结果监听** | 较弱                     | **LiveData 观察**          | 方便与 UI 生命周期绑定，制作启动页更简单。                                                                      |
| **手动控制** | 支持                     | **Builder 模式**           | 更符合直觉的配置方式，支持懒加载和按需启动。                                                                       |

## 🤝 贡献指南

欢迎各种形式的贡献！

1.  Fork 本仓库
2.  创建您的特性分支 (`git checkout -b feature/AmazingFeature`)
3.  提交您的更改 (`git commit -m 'Add some AmazingFeature'`)
4.  推送到分支 (`git push origin feature/AmazingFeature`)
5.  创建一个 Pull Request

## 📄 许可证

本项目采用 Apache 2.0 许可证。详情请参阅 [LICENSE](LICENSE) 文件。
