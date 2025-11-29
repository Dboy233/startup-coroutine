
<div align="center">
  <h1 align="center">Startup-Coroutine</h1>
  <p align="center">
    A Kotlin Coroutines-based Android startup framework that elegantly manages your app's initialization process.
    <br>
    <a href="#-key-features"><strong>Explore Features »</strong></a>
    <br>
    <br>
    <a href="README.md">中文</a>
    ·
    <a href="https://github.com/Dboy233/startup-coroutine/issues">Report Bug</a>
    ·
    <a href="https://github.com/Dboy233/startup-coroutine/issues">Request Feature</a>
  </p>
</div>

***

`startup-coroutine` is an asynchronous startup framework designed for Android, built on Kotlin Coroutines. It intelligently manages complex initialization dependencies using topological sorting and leverages the power of coroutines to parallelize tasks, significantly reducing application startup time. The framework is well-designed with advanced error handling and lifecycle management capabilities, making your app initialization process more robust, efficient, and easier to maintain.

## 📖 Table of Contents

*   [✨ Key Features](#-key-features)
*   [📥 Download & Integration](#-download--integration)
    *   [Step 1: Add JitPack Repository](#step-1-add-jitpack-repository)
    *   [Step 2: Add Dependency](#step-2-add-dependency)
*   [🚀 Quick Start](#-quick-start)
    *   [Step 1: Define Initialization Tasks](#step-1-define-initialization-tasks)
    *   [Step 2: Configure and Start](#step-2-configure-and-start)
    *   [Step 3: Observe Startup Results](#step-3-observe-startup-results)
*   [🧩 Core API Analysis](#-core-api-analysis)
    *   [`Initializer<T>`](#initializert)
    *   [`Startup.Builder`](#startupbuilder)
    *   [`StartupDispatchers`](#startupdispatchers)
    *   [`DependenciesProvider`](#dependenciesprovider)
*   [🔧 Advanced Usage](#-advanced-usage)
    *   [Exception Handling Mechanism](#exception-handling-mechanism)
    *   [Circular Dependency Detection](#circular-dependency-detection)
*   [🆚 Comparison with Jetpack App Startup](#-comparison-with-jetpack-app-startup)
*   [🤝 Contribution](#-contribution)
*   [📄 License](#-license)

## ✨ Key Features

*   **🔗 Dependency Management**: Automatically resolves and executes tasks in topological order, precisely handling dependencies between tasks.
*   **⚡ Coroutine First**: Native support for `suspend` functions, making it easy to handle asynchronous initialization (e.g., network requests, database migrations).
*   **🧵 Flexible Scheduling**: Provides multiple threading strategies (All Main, All IO, IO Execution with Main Callback, etc.) to adapt to different scenarios.
*   **🚀 Extreme Performance**: The framework's own topological sorting and scheduling logic runs in the background, with almost zero interference to the main thread.
*   **🛡️ Exception Isolation**: Uses `supervisorScope` to isolate parallel tasks, ensuring that the failure of a single task does not crash the entire startup process.
*   **👀 Lifecycle Aware**: Observes startup results via `LiveData`, perfectly adapting to the Activity/Fragment lifecycle.
*   **🤚 Cancellable**: Returns a standard coroutine `Job`, allowing you to safely cancel the entire startup process at any time.

## 📥 Download & Integration

### Step 1: Add JitPack Repository

Add the JitPack repository URL to your root project's `settings.gradle.kts` (or `settings.gradle`) file.

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

Add the dependency to the `build.gradle.kts` file of the module where you need to use this framework (usually the `app` module). Please replace `Tag` with the latest version number.

```kotlin
// app/build.gradle.kts
dependencies {
    // ... other dependencies
    implementation("com.github.Dboy233:startup-coroutine:Tag")
}
```

You can check the latest version number via the badge above.

## 🚀 Quick Start

### Step 1: Define Initialization Tasks

Each initialization unit needs to implement the `Initializer<T>` interface.

*   **`init(application, provider)`**: Contains the actual initialization logic. This is a `suspend` function.
*   **`dependencies()`**: (Optional) Declares other `Initializer` classes that the current task depends on.

**Example: Defining Two Tasks**

One task for initializing the network library (`NetworkInitializer`), and another for initializing an API service that depends on it (`ApiServiceInitializer`).

```kotlin
// 1. Define a task that produces a Retrofit instance
class NetworkInitializer : Initializer<Retrofit> {

    override suspend fun init(application: Application, provider: DependenciesProvider): Retrofit {
        // This is a suspend function, suitable for heavy operations.
        // Note: By default, init is called on the Main thread (depends on Dispatchers config).
        // If there is heavy I/O, it is recommended to use withContext(Dispatchers.IO) or configure StartupDispatchers.ExecuteOnIO
        return Retrofit.Builder()
            .baseUrl("https://api.example.com")
            .build()
    }
}

// 2. Define a task that depends on NetworkInitializer
class ApiServiceInitializer : Initializer<MyApiService> {

    // Declare dependencies
    override fun dependencies(): List<KClass<out Initializer<*>>> {
        return listOf(NetworkInitializer::class)
    }

    override suspend fun init(application: Application, provider: DependenciesProvider): MyApiService {
        // Get the result of the dependency. If the dependency failed, ApiServiceInitializer's init will not be executed.
        val retrofit = provider.result<Retrofit>(NetworkInitializer::class)
        
        return retrofit.create(MyApiService::class.java)
    }
}
```

### Step 2: Configure and Start

In your `Application` class, use `Startup.Builder` to build and start the framework.

```kotlin
class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val startup = Startup.Builder(this)
            .add(NetworkInitializer())       // Add task
            .add(ApiServiceInitializer())    // Add task
            .setDispatchers(StartupDispatchers.ExecuteOnIO) // Set thread strategy: Execute on IO, create framework on Main
            .setDebug(true)                  // Enable debug mode for detailed logs
            .build()

        // Start the initialization process. This call is non-blocking and returns a Job immediately.
        startup.start()
    }
}
```

### Step 3: Observe Startup Results

The framework provides a `LiveData`-based observation mechanism. You can listen for startup completion in your `SplashActivity` or `MainActivity`.

```kotlin
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Observe startup results
        Startup.observe(this) { result ->
            when (result) {
                is StartupResult.Success -> {
                    Log.d("Startup", "🎉 All tasks completed!")
                    goToMainActivity()
                }
                is StartupResult.Failure -> {
                    Log.e("Startup", "🔥 Startup failed: ${result.exceptions.size} errors")
                    // Handle errors, e.g., show a dialog or retry
                }
                StartupResult.Idle -> {
                    // Not started yet or reset
                }
            }
        }
    }
}
```

## 🧩 Core API Analysis

### `Initializer<T>`

The core interface defining an initialization unit.

*   `suspend fun init(application: Application, provider: DependenciesProvider): T`: Executes the initialization logic.
*   `fun dependencies(): List<KClass<out Initializer<*>>>`: Returns a list of dependent tasks.

### `Startup.Builder`

The builder used to create a `Startup` instance.

*   `add(Initializer<*>)`: Adds a single or multiple tasks.
*   `setDispatchers(StartupDispatchers)`: Configures the thread scheduling strategy.
*   `setDebug(Boolean)`: Enables/disables debug logs (including timing stats and dependency graphs).
*   `build()`: Creates a `Startup` instance.

### `StartupDispatchers`

Pre-configured thread scheduling strategies that control where tasks are executed and where the framework starts.

| Strategy | Description | Suitable Scenario |
| :--- | :--- | :--- |
| **`Default`** | **(Recommended)** Startup/Sorting on IO, `init` on **Main**. | Most scenarios involving UI initialization. |
| **`ExecuteOnIO`** | Startup/Sorting on Main, `init` on **IO**. | Tasks are mainly heavy I/O (Database, Network). |
| **`AllIO`** | Entire process on IO. | Background initialization with absolutely no UI operations. |
| **`AllMain`** | Entire process on Main. | Only for very lightweight task collections. |

### `DependenciesProvider`

Passed to the `init` method to retrieve results from upstream dependencies.

*   `result<T>(class)`: Retrieves the result; throws an exception if missing or type mismatch.
*   `resultOrNull<T>(class)`: Safely retrieves the result; returns null on failure.

## 🔧 Advanced Usage

### Exception Handling Mechanism

The framework collects exceptions from all parallel tasks.

*   If an exception occurs, `Startup.observe` will receive `StartupResult.Failure`.
*   The `Failure` object contains an `exceptions` list, which you can iterate through to see specific error causes.
*   Using `setDebug(true)` allows you to see detailed error stacks and corresponding task names in Logcat.

### Circular Dependency Detection

After `build()`, when `start()` is called, the framework automatically performs topological sorting. If a circular dependency is detected (e.g., A depends on B, B depends on A), an `IllegalStateException` is thrown immediately to help you identify structural issues during development.

## 🆚 Comparison with Jetpack App Startup

| Feature | Jetpack App Startup | startup-coroutine | Advantage |
| :--- | :--- | :--- | :--- |
| **Async Capability** | Not Supported (Sync Blocking) | **Native Coroutine Support** | **startup-coroutine**'s `init` is a `suspend` function, naturally fitting modern Android development (Room, Retrofit, DataStore). |
| **Thread Control** | Default Main Thread | **Highly Configurable** | Switch to full IO execution with one line to avoid ANR. |
| **Dependency Args** | None (via ContentProvider) | **DependenciesProvider** | Supports passing initialization results from upstream to downstream tasks (e.g., OkHttp instance to Retrofit). |
| **Result Observation** | Weak | **LiveData Observation** | Easy to bind with UI lifecycle, making Splash screens simpler. |
| **Manual Control** | Supported | **Builder Pattern** | More intuitive configuration, supporting lazy loading and on-demand startup. |

## 🤝 Contribution

Contributions of all forms are welcome!

1.  Fork the repository
2.  Create your feature branch (`git checkout -b feature/AmazingFeature`)
3.  Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4.  Push to the branch (`git push origin feature/AmazingFeature`)
5.  Create a Pull Request

## 📄 License

This project is licensed under the Apache 2.0 License. See the [LICENSE](LICENSE) file for details.
