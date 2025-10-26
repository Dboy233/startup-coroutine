package com.dboy.startup_coroutine

import android.content.Context
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.reflect.KClass

/**
 * =====================================================================================
 *
 *                             任务流程拓扑图 (Task Topology)
 *
 * =====================================================================================
 *
 * 该图展示了所有初始化任务的依赖关系和执行顺序。
 *
 * - `[S]` 代表串行任务 (Serial)，在主线程调度，按顺序执行。
 * - `[P]` 代表并行任务 (Parallel)，在后台线程池调度，可以并发执行。
 * - `-->` 代表依赖关系 (depends on)。
 * - `(Fails)` 标记了会故意执行失败的任务。
 *
 *
 *                                     +---------------------------+
 *                                     | [S] PrivacyConsent        |  (阶段 1: 串行根任务)
 *                                     +-------------+-------------+
 *                                                   |
 *                           +-----------------------+-------------------------+
 *                           |                                                 |
 *              +---------------------------+                     +---------------------------+
 *              | [P] NetworkInitializer    |                     | [P] LoggingInitializer    |  (阶段 2: 基础服务并行)
 *              +-------------+-------------+                     +------------+--------------+
 *                            |                                                |
 *       +--------------------+------------------+                             |
 *       |                                       |                             |
 * +-----+-------------+            +--------------------------------+         |
 * | [S] Config        |            | [P] UnnecessaryAnalytics (Fails) |       |
 * +-----+-------------+            +--------------------------------+         |
 *       |                                                                     |
 *       | (阶段 3: 关键点,他们虽然都是并发任务,但是逻辑上是串行)                      |
 * +-----+-------------+                                                       |
 * | [S] UserAuth      |                                                       |
 * +-----+-------------+                                                       |
 *       |                                                                     |
 *       +-------------------------+-------------------------+                 |
 *       |                                                   |                 |
 * +-----+-------------+                       +-------------+-----+           |
 * | [P] Database      |                       | [S] UITheme       |           |  (阶段 4: 业务与UI并行)
 * +-------------------+                       +-------------+-----+           |
 *       |                                                   |                 |
 *       |                                                   |                 |
 *       +---------------------------------------------------+-----------------+
 *                                     |
 *                       +-------------+-----------------------------+
 *                       |  [P] ThirdPartySDK (等待所有上游任务完成)    | (阶段 5: 收尾任务)
 *                       +-------------------------------------------+
 *
 *
 * 执行流程解读:
 * 1.  **启动**: `PrivacyConsent` 首先在主线程串行执行。
 * 2.  **并行分支**: `PrivacyConsent` 完成后，`Network` 和 `Logging` 任务会立即在后台并行开始。
 * 3.  **主干并发的串行路径**: `Network` 完成后，关键的串行任务 `Config` 开始执行（其内部网络请求在IO线程），完成后 `UserAuth` 接着执行。
 * 4.  **新的并行分支**: `UserAuth` 完成后，`Database` (并行) 和 `UITheme` (串行) 开始执行。`UITheme` 会等待 `UserAuth` 完成，但 `Database` 可以在后台独立进行。
 * 5.  **异常任务**: `UnnecessaryAnalytics` 在 `Network` 准备好后就开始并行尝试，它的失败不会阻塞任何其他任务。
 * 6.  **最终汇集**: `ThirdPartySDK` 是一个收尾任务，它会等待 `UITheme`, `Database`, `Logging` 全部完成后才开始执行。
 *
 */

/**
 * =====================================================================================
 *
 *                                 模拟数据类 (Data Models)
 *
 * =====================================================================================
 */

/**
 * 模拟一个App的远程配置。
 */
data class AppConfig(
    val apiEndpoint: String,
    val featureFlags: Set<String>
)

/**
 * 模拟一个简化的用户信息。
 */
data class UserProfile(
    val userId: String,
    val nickname: String,
    val isVip: Boolean
)


/**
 * =====================================================================================
 *
 *                              初始化任务实现 (Initializers)
 *
 * =====================================================================================
 */


// --- 阶段一：基础且必要的串行任务 ---

/**
 * **任务 1 (串行): 隐私合规检查**
 *
 * 这是启动流程的绝对第一步。在用户同意隐私协议之前，任何其他任务都不应执行。
 * 它必须是串行的，并且是所有其他任务的根依赖（隐式或显式）。
 */
class PrivacyConsentInitializer : Initializer<Boolean>() {
    // 默认就是 InitMode.SERIAL
    override suspend fun init(context: Context, provider: DependenciesProvider): Boolean {
        Log.d("AppStartup", "1. [Privacy] (${Thread.currentThread().name}) 检查隐私协议同意状态...")
        delay(50) // 模拟从 SharedPreferences 读取状态
        val agreed = true // 模拟用户已同意
        if (agreed) {
            Log.d("AppStartup", "1. [Privacy] (${Thread.currentThread().name}) ✅ 用户已同意隐私协议。")
        } else {
            Log.d("AppStartup", "1. [Privacy] (${Thread.currentThread().name}) ⚠️ 用户未同意，启动流程可能需要暂停或引导。")
            // 在真实应用中，这里可能会阻塞UI线程，弹窗让用户同意
        }
        return agreed
    }
}


// --- 阶段二：核心服务的并行初始化 (依赖隐私合规) ---

/**
 * **任务 2 (并行): 初始化网络库 (例如 OkHttp, Retrofit)**
 *
 * 一个独立的后台任务，可以在隐私合规后立即开始。
 * 它通常涉及一些轻量级的 I/O 操作来设置缓存。
 */
class NetworkInitializer : Initializer<Unit>() {
    override fun initMode(): InitMode = InitMode.PARALLEL
    override fun dependencies(): List<KClass<out Initializer<*>>> =
        listOf(PrivacyConsentInitializer::class)

    override suspend fun init(context: Context, provider: DependenciesProvider) {
        // 确保用户已同意隐私协议
        provider.result<Boolean>(PrivacyConsentInitializer::class)

        // 切换到 IO 线程执行实际工作
        withContext(Dispatchers.IO) {
            Log.d("AppStartup", "2.1 [Network] (${Thread.currentThread().name}) 开始初始化网络库...")
            delay(150) // 模拟设置证书、拦截器、缓存等
            Log.d("AppStartup", "2.1 [Network] (${Thread.currentThread().name}) ✅ 网络库初始化完成。")
        }
    }
}

/**
 * **任务 3 (并行): 初始化日志框架 (例如 Timber)**
 *
 * 同样是一个独立的后台任务，可以在后台与其他任务并行执行。
 *
 * 它甚至都可以不依赖任何其他任务
 */
class LoggingInitializer : Initializer<Unit>() {
    override fun initMode(): InitMode = InitMode.PARALLEL
    override fun dependencies(): List<KClass<out Initializer<*>>> =
        listOf(PrivacyConsentInitializer::class)//纯属多余

    override suspend fun init(context: Context, provider: DependenciesProvider) {
        withContext(Dispatchers.Default) { // CPU密集型可以使用Default
            Log.d("AppStartup", "2.2 [Logging] (${Thread.currentThread().name}) 开始初始化日志服务...")
            delay(50) // 模拟配置日志等级、输出目标等
            Log.d("AppStartup", "2.2 [Logging] (${Thread.currentThread().name}) ✅ 日志服务初始化完成。")
        }
    }
}

/**
 * **任务 4 (并行): 获取远程配置**
 *
 * 这是一个关键的网络请求，后续很多业务都依赖它。因为它依赖的任务是并行所以它必须是并行.
 *
 * 它的网络请求逻辑依然在后台线程执行。
 */
class ConfigInitializer : Initializer<AppConfig>() {
    // 【修改】将此任务改为串行，作为关键路径的一部分
    override fun initMode(): InitMode = InitMode.PARALLEL
    override fun dependencies(): List<KClass<out Initializer<*>>> =
        listOf(NetworkInitializer::class)

    override suspend fun init(context: Context, provider: DependenciesProvider): AppConfig {
        // 任务本身虽然是串行调度，但其内部实现可以通过 withContext 切换到后台线程执行耗时操作
        return withContext(Dispatchers.IO) {
            Log.d("AppStartup", "3. [Config] (${Thread.currentThread().name}) 开始从服务器获取配置...")
            delay(300) // 模拟网络延迟
            val config = AppConfig(
                apiEndpoint = "https://api.example.com",
                featureFlags = setOf("new_ui", "beta_feature")
            )
            Log.d("AppStartup", "3. [Config] (${Thread.currentThread().name}) ✅ 配置获取成功: $config")
            config
        }
    }
}


// --- 阶段三：依赖核心服务的业务逻辑初始化 ---

/**
 *  **任务 5 (并行): 交验用户**
 */
class UserAuthInitializer : Initializer<UserProfile?>() {
    override fun dependencies(): List<KClass<out Initializer<*>>> = listOf(ConfigInitializer::class)

    override fun initMode(): InitMode = InitMode.PARALLEL
    override suspend fun init(context: Context, provider: DependenciesProvider): UserProfile? {
        val config = provider.result<AppConfig>(ConfigInitializer::class)
        Log.d("AppStartup", "4. [UserAuth] (${Thread.currentThread().name}) 开始校验用户状态，API: ${config.apiEndpoint}")

        delay(100) // 模拟从本地存储读取Token
        val hasToken = true // 模拟用户已登录
        if (hasToken) {
            val user = UserProfile("uid-12345", "Dboy", true)
            Log.d("AppStartup", "4. [UserAuth] (${Thread.currentThread().name}) ✅ 用户已登录: ${user.nickname}")
            return user
        } else {
            Log.d("AppStartup", "4. [UserAuth] (${Thread.currentThread().name}) ⚠️ 用户未登录。")
            return null
        }
    }
}


/**
 * **任务 6 (并行): 初始化数据库 (例如 Room)**
 *
 * 这是一个耗时的 I/O 密集型任务，适合在后台并行执行。
 * 它依赖于用户状态，因为数据库可能以用户 ID 命名。
 */
class DatabaseInitializer : Initializer<Unit>() {
    override fun initMode(): InitMode = InitMode.PARALLEL
    override fun dependencies(): List<KClass<out Initializer<*>>> =
        listOf(UserAuthInitializer::class)

    override suspend fun init(context: Context, provider: DependenciesProvider) {
        val userProfile = provider.resultOrNull<UserProfile>(UserAuthInitializer::class)
        val dbName = userProfile?.userId ?: "default_user"

        withContext(Dispatchers.IO) {
            Log.d("AppStartup", "5.1 [Database] (${Thread.currentThread().name}) 准备为用户 '$dbName' 初始化数据库...")
            delay(500) // 模拟数据库打开和迁移
            Log.d("AppStartup", "5.1 [Database] (${Thread.currentThread().name}) ✅ 数据库初始化完成。")
        }
    }
}

/**
 * 一个会失败的非必要任务，用于测试容错性。
 * 它依赖网络，但没有其他任务依赖它。
 */
class UnnecessaryAnalyticsInitializer : Initializer<Unit>() {
    override fun initMode(): InitMode = InitMode.PARALLEL
    override fun dependencies(): List<KClass<out Initializer<*>>> =
        listOf(NetworkInitializer::class)

    override suspend fun init(context: Context, provider: DependenciesProvider) {
        withContext(Dispatchers.IO) {
            Log.d("AppStartup", "X. [Analytics] (${Thread.currentThread().name}) 开始上报一个不重要的分析事件...")
            delay(300) // 模拟网络请求
            Log.e("AppStartup", "X. [Analytics] (${Thread.currentThread().name}) ❌ 上报失败！网络超时。")
            throw IOException("Analytics report failed")
        }
    }
}


// --- 阶段四：UI 和高优先级第三方 SDK 初始化 ---

/**
 * **任务 7 (并行): 初始化核心 UI 组件 (例如主题、字体)**
 *
 * 这是一个必须在主线程执行的任务，用于在界面展示前设置好视觉元素。
 * 它依赖于用户认证（可能需要根据用户身份显示不同主题）。
 */
class UIThemeInitializer : Initializer<Unit>() {
    override fun dependencies(): List<KClass<out Initializer<*>>> =
        listOf(UserAuthInitializer::class)

    override fun initMode(): InitMode {
        return InitMode.PARALLEL
    }

    override suspend fun init(context: Context, provider: DependenciesProvider) {
        withContext(Dispatchers.Main){
            assert(Looper.myLooper() == Looper.getMainLooper()) { "UI任务必须在${Thread.currentThread().name}！" }
            Log.d("AppStartup", "5.2 [UITheme] (${Thread.currentThread().name}) 开始应用UI主题和字体...")
            delay(300)
            Log.d("AppStartup", "5.2 [UITheme] (${Thread.currentThread().name}) ✅ UI主题设置完毕。")
        }
    }
}


/**
 * **任务 8 (并行): 初始化第三方分析/广告 SDK**
 *
 * 许多第三方 SDK 要求在主线程初始化，但我们可以将其包装在并行任务中，
 * 只要它等待所有前置任务完成即可。
 * 这个任务依赖多个前置任务（如用户、配置、UI），确保在所有条件具备后才执行。
 */
class ThirdPartySDKInitializer : Initializer<Unit>() {
    override fun dependencies(): List<KClass<out Initializer<*>>> = listOf(
        UIThemeInitializer::class,
        DatabaseInitializer::class,
        LoggingInitializer::class
    )

    override fun initMode(): InitMode = InitMode.PARALLEL

    override suspend fun init(context: Context, provider: DependenciesProvider) {
        withContext(Dispatchers.Main){
            val user = provider.resultOrNull<UserProfile>(UserAuthInitializer::class) // 间接依赖
            assert(Looper.myLooper() == Looper.getMainLooper()) { "第三方SDK必须在主线程初始化！" }

            Log.d("AppStartup", "6. [3rdParty] (${Thread.currentThread().name}) 所有依赖已就绪，开始初始化分析和广告SDK...")

            if (user?.isVip == true) {
                Log.d("AppStartup", "6. [3rdParty] (${Thread.currentThread().name}) ✅ 用户是VIP，跳过广告SDK，只初始化分析SDK。")
                delay(300) // 模拟分析SDK初始化
            } else {
                Log.d("AppStartup", "6. [3rdParty] (${Thread.currentThread().name}) ✅ 初始化分析和广告SDK。")
                delay(600) // 模拟两个SDK的初始化
            }
            Log.d("AppStartup", "6. [3rdParty] (${Thread.currentThread().name}) ✅ 第三方SDK初始化完成。")
        }
    }
}

