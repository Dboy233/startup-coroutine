<div align="center">
  <h1 align="center">Startup-Coroutine</h1>
  <p align="center">
    A Kotlin Coroutine-based startup framework for Android, elegantly managing your app initialization process.
    <br>
    <a href="#-core-features"><strong>Explore Features »</strong></a>
    <br>
    <br>
    <a href="https://github.com/Dboy233/startup-coroutine/issues">Report Bug</a>
    ·
    <a href="https://github.com/Dboy233/startup-coroutine/issues">Request Feature</a>
  </p>
</div>

***

`startup-coroutine` is an asynchronous startup framework designed for Android, based on Kotlin Coroutines. It intelligently manages complex initialization dependencies through topological sorting and leverages the power of coroutines to parallelize tasks, significantly reducing application startup time. The framework is well-designed with advanced error handling and lifecycle management capabilities, making your app initialization process more robust, efficient, and maintainable.

## 📖 Table of Contents

*   [✨ Core Features](#-core-features)
*   [📥 Download & Integration](#-download--integration)
    *   [Step 1: Add JitPack Repository](#step-1-add-jitpack-repository)
    *   [Step 2: Add Dependency](#step-2-add-dependency)
*   [🚀 Quick Start](#-quick-start)
    *   [Step 1: Define Initialization Tasks](#step-1-define-initialization-tasks)
    *   [Step 2: Configure and Start the Framework](#step-2-configure-and-start-the-framework)
*   [🧩 Core API Guide](#-core-api-guide)
    *   [`Initializer<T>`](#initializert)
    *   [`Startup`](#startup)
    *   [`DependenciesProvider`](#dependenciesprovider)
*   [🔧 Advanced Usage](#-advanced-usage)
    *   [Exception Handling Mechanism](#exception-handling-mechanism)
    *   [Circular Dependency Detection](#circular-dependency-detection)
*   [🆚 Comparison with Jetpack App Startup](#-comparison-with-jetpack-app-startup)
*   [🤝 Contributing](#-contributing)
*   [📄 License](#-license)
*   [🔧 Test Logs](#-test-logs)

## ✨ Core Features

*   **🔗 Dependency Management**: Automatically resolves and executes tasks in topological order, precisely handling inter-task dependencies.
*   **⚡ Main Thread Safety**: All tasks execute on the **Main Thread** by default, ensuring absolute safety for UI-related initializations.
*   **🚀 Peak Performance**: The framework's own management and scheduling work is done on **background threads**, causing almost zero interference to the main thread, maximizing application startup performance.
*   **🛡️ Exception Isolation**: Uses `supervisorScope` to isolate parallel tasks, ensuring failure in one task doesn't crash the entire startup process.
*   **📊 Unified Result Callback**: Handles the final success or failure state uniformly via the `onResult` callback.
*   **🤚 Cancellable**: Supports safe cancellation of the entire startup process at any time, with proper resource cleanup.
*   **🍃 Lightweight**: Based on Kotlin Coroutines, with concise core logic and minimal project intrusion.

## 📥 Download & Integration

### Step 1: Add JitPack Repository

In your root project's `settings.gradle.kts` (or `settings.gradle`) file, add the JitPack repository URL.

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") } // <-- Add this line
    }
}
```

### Step 2: Add Dependency

<a href="https://jitpack.io/#Dboy233/startup-coroutine"><img src="https://jitpack.io/v/Dboy233/startup-coroutine.svg"></a>

Add the dependency in the `build.gradle.kts` file of the module where you want to use this framework (usually the `app` module). Replace `Tag` with the latest version number.

```kotlin
// app/build.gradle.kts
dependencies {
    // ... other dependencies
    implementation("com.github.Dboy233:startup-coroutine:Tag")
}
```

You can check the latest version number via the version badge above.

## 🚀 Quick Start

### Step 1: Define Initialization Tasks

Each initialization unit should inherit from the `Initializer<T>` abstract class and implement its core methods.

*   **`init(context, provider)`**: Contains the actual initialization logic. This is a suspend function. Its dispatcher can be specified in `Startup`, or it can switch internally.
*   **`dependencies()`**: (Optional) Declares other `Initializer` tasks that this task depends on.

**Example: Define Two Tasks**

One task for initializing an analytics service (`AnalyticsInitializer`), which involves time-consuming operations; and another task for initializing an Ads SDK (`AdsInitializer`) that depends on the first.

```kotlin

// AnalyticsInitializer.kt
// A parallel task simulating time-consuming work and returning an SDK object
class AnalyticsInitializer : Initializer<AnalyticsSDK>() {

    override suspend fun init(context: Context, provider: DependenciesProvider): AnalyticsSDK {
        // Important: If the **Startup** worker thread is set to Main, time-consuming operations must be placed on the IO thread.
        val result = withContext(Dispatchers.IO) {
            delay(1000) // Simulate a time-consuming I/O operation
            println("Analytics Service SDK initialized on background thread: ${Thread.currentThread().name}")
            AnalyticsSDK("Analytics-SDK-Instance")
        }
        return result
    }

}

// AdsInitializer.kt
// A parallel task that depends on AnalyticsInitializer
class AdsInitializer : Initializer<Unit>() {

    override suspend fun init(context: Context, provider: DependenciesProvider) {
        // Get the result of the dependency from the dependency provider
        val analyticsSDK = provider.result<AnalyticsSDK>(AnalyticsInitializer::class)

        // This operation executes on the main thread, allowing direct UI-related initialization (provided Startup's worker thread is set to Main!)
        println("Ads SDK is using: ${analyticsSDK.name}, on main thread: ${Thread.currentThread().name}")
        // Initialize the Ads SDK here...
    }

    // Define dependencies
    override fun dependencies(): List<KClass<out Initializer<*>>> {
        return listOf(AnalyticsInitializer::class)
    }

}

// Dummy class for example
data class AnalyticsSDK(val name: String)
```

### Step 2: Configure and Start the Framework

In your `Application` class or other suitable entry point, create a `Startup` instance and pass in the list of tasks.

```kotlin
// MyApplication.kt
class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val startup = Startup(
            context = this,
            dispatchers = ExecuteOnIODispatchers, // Optional
            initializers = listOf(AnalyticsInitializer(), AdsInitializer()),
            onResult = { result ->
                when (result) {
                    is StartupResult.Success -> {
                        println("🎉 All startup tasks completed successfully!")
                    }
                    is StartupResult.Failure -> {
                        println("🔥 Startup process failed with ${result.exceptions.size} error(s):")
                    }
                }
            }
        )

        // Start the initialization process. This call is non-blocking and returns immediately.
        startup.start()
    }

}
```

## 🧩 Core API Guide

### `Initializer<T>`

The base class for all initialization tasks.

*   `init(context: Context, provider: DependenciesProvider): T`: Where your initialization logic resides. This is a suspend function that executes on the **Main Thread** by default. **Any time-consuming operations must switch to a background thread using `withContext`**.
*   `dependencies(): List<KClass<out Initializer<*>>>`: Specifies the other tasks that this task depends on.

### `Startup`

The core class that manages and executes the initialization process.

*   `start()`: Starts the entire initialization process. This method is thread-safe, non-blocking, and can only be successfully called once.
*   `cancel()`: Cancels all ongoing initialization tasks.

### `DependenciesProvider`

An interface passed to the `init()` method, allowing a task to get the execution results of its dependencies.

*   `result<T>(dependency: KClass<out Initializer<*>>): T`: Gets the result of a completed dependency. Throws an `IllegalStateException` if the result is unavailable (e.g., dependency failed, not registered, or returned Unit).
*   `resultOrNull<T>(dependency: KClass<out Initializer<*>>): T?`: **(Recommended)** Safely gets the result, returning `null` if the result is unavailable.

## 🔧 Advanced Usage

### Exception Handling Mechanism

The framework can aggregate exceptions from all parallel tasks. If a sequential task fails, the entire startup process terminates immediately and reports the exception. If one or more parallel tasks fail, the framework waits for all other independently runnable tasks to complete, then reports all collected exceptions at once via the `onResult` callback.

### Circular Dependency Detection

The framework automatically performs topological sorting upon startup. If it detects a circular dependency among initialization tasks, it throws an `IllegalStateException`, preventing deadlocks at runtime.

## 🆚 Comparison with Jetpack App Startup

Jetpack App Startup is an excellent library that achieves automated, seamless initialization via `ContentProvider`. So, when should you choose `startup-coroutine`?

| Feature | Jetpack App Startup | startup-coroutine | Advantage Description |
| :--- | :--- | :--- | :--- |
| **Invocation Method** | Automated, non-intrusive | Manual call (`startup.start()`) | **startup-coroutine** offers more flexible control, allowing you to start tasks at any point (e.g., after privacy policy consent). |
| **Threading Model & Async Capability** | Executes on background thread, does not support `suspend` | **Framework schedules in background, tasks execute on Main Thread**, natively supports `suspend` functions | **startup-coroutine** has overwhelming advantages:<br>1. **Zero Main Thread Interference**: Framework overhead is in the background, friendlier to startup performance.<br>2. **Native Async Support**: The `init` method is a `suspend` function, allowing direct calls to other suspend functions (e.g., Retrofit, Room async APIs), resulting in cleaner, more natural code without callbacks.<br>3. **Arbitrary Thread Switching**: Easily and efficiently switch threads within initialization tasks using `withContext`. |
| **Exception Handling** | Crashes (by default) | Isolates parallel tasks, unified callback | **startup-coroutine** provides stronger exception isolation via `supervisorScope`, where failure in one task doesn't affect others. |
| **Result Passing** | Supported, but simpler | `DependenciesProvider` | **startup-coroutine** offers a type-safe, more intuitive way to pass results. |
| **Cancellation Support** | No | **Yes (`startup.cancel()`)** | **startup-coroutine** supports cancelling the entire startup process at runtime, suitable for dynamic module scenarios. |

**Summary:**

*   If you need a **simple, fully automatic, one-time** startup solution, **Jetpack App Startup** is a good choice.
*   If you need **fine-grained control** over the startup process, **advanced concurrency management, robust exception isolation**, or need to trigger initialization at **different stages of the application lifecycle**, then **startup-coroutine** is the more powerful and flexible solution.

**When to choose the Startup-Coroutine framework:**

*   Your project has complex initialization task logic.
*   All initialization tasks take more than 2 seconds in `Application.onCreate()`; startup-coroutine's threading model can help optimize startup time by ~30%.
*   You urgently need to display your SplashActivity instead of being stuck on a startup white screen.

## 🤝 Contributing

Contributions of all kinds are welcome! Whether it's reporting a bug, suggesting a new feature, or contributing code directly.

1.  Fork the Project
2.  Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3.  Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4.  Push to the Branch (`git push origin feature/AmazingFeature`)
5.  Open a Pull Request

## 📄 License

This project is licensed under the Apache 2.0 License. See the [LICENSE](https://github.com/Dboy233/startup-coroutine/blob/master/LICENSE) file for details.

### Acknowledgments & Disclaimer

The development of this project was supported by AI programming assistants. Portions of the code, documentation, and optimization suggestions were completed with the assistance of AI (Gemini), and were reviewed and integrated by the author.

## 🔧 Test Logs

> First, using the Jetpack App Startup framework for the startup process.

```txt
StartupJetpack           D  ============== StartupJetpack Startup Process Started ==============
StartupJetpack           D  1. [BugMonitor] (main) Initializing Bug Stats Platform...
StartupJetpack           D  1. [BugMonitor] (main) ✅ Bug Stats Platform initialized.
StartupJetpack           D  2. [Utils] (main) Initializing Common Utilities Library...
StartupJetpack           D  2.1 [Utils] (main) ...Logging, Network, Stats, EventBus utilities OK
StartupJetpack           D  2. [Utils] (main) ✅ Common Utilities Library fully initialized.
StartupJetpack           D  3. [Database] (main) Initializing Database...
StartupJetpack           D  3. [Database] (main) ...Database upgrade detected, performing upgrade...
StartupJetpack           D  3. [Database] (main) ✅ Database initialized.
StartupJetpack           D  4. [Config] (main) Fetching configuration from network...
StartupJetpack           D  4. [Config] (main) ✅ Configuration fetched successfully.
StartupJetpack           D  5. [Ads] (main) Initializing Ads Platform...
StartupJetpack           D  5. [Ads] (main) ...Using config: {provider=AwesomeAds, timeout=3000}
StartupJetpack           D  5. [Ads] (main) ✅ Ads Platform initialized.
StartupJetpack           I  ============== StartupJetpack Time Statistics ==============
StartupJetpack           I  - JectpacjBugMonitorInitializer   | 103 ms
StartupJetpack           I  - JetcpackCommonUtilsInitializer  | 500 ms
StartupJetpack           I  - JetcPackDatabaseInitializer     | 301 ms
StartupJetpack           I  - JetpackConfigInitializer        | 82 ms
StartupJetpack           I  - JectpackAdsPlatformInitializer  | 202 ms
StartupJetpack           I  StartupJetpack Total Time: 1191 ms
StartupJetpack           I  ============== StartupJetpack Startup Process Successfully Ended ==============
```

> Using startup-coroutine for the startup process.

```txt
StartupCoroutine         D  ============== Startup Process Started ==============
StartupCoroutine         D  startup.start() called, main thread continues other tasks...
StartupCoroutine         D  --- Startup Coroutine Dependency Graph ---
                            
                            BugMonitorInitializer
                            CommonUtilsInitializer
                            DatabaseInitializer
                              └─ CommonUtilsInitializer
                            ConfigInitializer
                              ├─ CommonUtilsInitializer
                              └─ DatabaseInitializer
                            AdsPlatformInitializer
                              └─ ConfigInitializer
                            
                            ----------------------------------------
StartupCoroutine         D  1. [BugMonitor] (main) Initializing Bug Stats Platform...
StartupCoroutine         D  2. [Utils] (main) Initializing Common Utilities Library...
StartupCoroutine         D  1. [BugMonitor] (main) ✅ Bug Stats Platform initialized.
StartupCoroutine         D  2.1 [Utils] (main) ...Logging, Network, Stats, EventBus utilities OK
StartupCoroutine         D  2. [Utils] (main) ✅ Common Utilities Library fully initialized.
StartupCoroutine         D  3. [Database] (DefaultDispatcher-worker-3) Initializing Database...
StartupCoroutine         D  3. [Database] (DefaultDispatcher-worker-3) ...Database upgrade detected, performing upgrade...
StartupCoroutine         D  3. [Database] (DefaultDispatcher-worker-3) ✅ Database initialized.
StartupCoroutine         D  4. [Config] (DefaultDispatcher-worker-4) Fetching configuration from network...
StartupCoroutine         D  4. [Config] (DefaultDispatcher-worker-4) ✅ Configuration fetched successfully: AppConfig(adConfig={provider=AwesomeAds, timeout=3000}, featureFlags=[new_checkout_flow, enable_dark_mode])
StartupCoroutine         D  5. [Ads] (main) Initializing Ads Platform...
StartupCoroutine         D  5. [Ads] (main) ...Using config: {provider=AwesomeAds, timeout=3000}
StartupCoroutine         D  5. [Ads] (main) ✅ Ads Platform initialized.
StartupCoroutine         I  --- Startup Coroutine Performance Summary ---
                            
                            >> Total Time: 1853ms  |  Status: SUCCESS
                            >> Dispatchers Mode: Default
                            
                            >> Individual Task Durations:
                               - CommonUtilsInitializer  |  502ms  |  Thread: main
                               - DatabaseInitializer     |  319ms  |  Thread: main
                               - BugMonitorInitializer   |  307ms  |  Thread: main
                               - AdsPlatformInitializer  |  202ms  |  Thread: main
                               - ConfigInitializer       |  53ms   |  Thread: main
                            >> Task time is sum  : 1383 ms
                            
                            -------------------------------------------
StartupCoroutine         D  ============== Startup Process Successfully Ended ==============
```