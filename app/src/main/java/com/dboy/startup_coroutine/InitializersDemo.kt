package com.dboy.startup_coroutine


import android.content.Context
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.delay
import kotlin.reflect.KClass

/**
 * 模拟一个App配置信息的数据类。
 */
data class AppConfig(
    val apiEndpoint: String,
    val featureFlags: Set<String>
)

/**
 * 模拟一个简化的用户信息数据类。
 */
data class UserProfile(
    val userId: String,
    val nickname: String,
    val isVip: Boolean
)

// --- 核心服务初始化 ---

/**
 * 任务一：获取远程配置 (修正为串行)
 *
 * 因为后续的主线程任务 AdsInitializer 依赖此任务的结果，
 * 为了遵守“串行不能依赖并行”的规则，我们将其修正为串行任务。
 */
class ConfigInitializer : Initializer<AppConfig>() {
    // 默认即为 InitMode.SERIAL
    override suspend fun init(context: Context, provider: DependenciesProvider): AppConfig {
        Log.d("AppStartup", "[ConfigInitializer] (主线程) 开始从服务器获取配置...")
        delay(300) // 模拟网络延迟
        val config = AppConfig(
            apiEndpoint = "https://api.example.com",
            featureFlags = setOf("new_ui", "beta_feature")
        )
        Log.d("AppStartup", "[ConfigInitializer] (主线程) ✅ 配置获取成功: $config")
        return config
    }
}

/**
 * 任务二：初始化日志服务 (并行)
 *
 * 一个独立的、轻量级的任务，适合并行执行。
 * 它不依赖任何其他任务，也没有返回值。
 */
class LogInitializer : Initializer<Unit>() {
    override fun initMode(): InitMode = InitMode.PARALLEL

    override suspend fun init(context: Context, provider: DependenciesProvider) {
        Log.d("AppStartup", "[LogInitializer] 开始初始化日志服务...")
        delay(100) // 模拟I/O操作
        // 这里可以执行类似 Timber.plant(Timber.DebugTree()) 的操作
        Log.d("AppStartup", "[LogInitializer] ✅ 日志服务初始化完成。")
    }
}

// --- 用户与数据相关初始化 (依赖于核心服务) ---

/**
 * 任务三：校验用户登录状态 (修正为串行)
 *
 * 依赖于 ConfigInitializer，并且被主线程任务 AdsInitializer 依赖，
 * 因此也必须是串行任务。
 */
class UserAuthInitializer : Initializer<UserProfile?>() {
    // 默认即为 InitMode.SERIAL
    override fun dependencies(): List<KClass<out Initializer<*>>> = listOf(ConfigInitializer::class)

    override suspend fun init(context: Context, provider: DependenciesProvider): UserProfile? {
        // 从依赖项获取App配置
        val config = provider.result<AppConfig>(ConfigInitializer::class)
        Log.d("AppStartup", "[UserAuthInitializer] (主线程) 开始校验用户状态，API地址: ${config.apiEndpoint}")

        delay(200) // 模拟读取本地Token和网络验证
        val hasToken = true // 模拟本地有Token
        if (hasToken) {
            val user = UserProfile("uid-12345", "Dboy", true)
            Log.d("AppStartup", "[UserAuthInitializer] (主线程) ✅ 用户已登录: ${user.nickname}")
            return user
        } else {
            Log.d("AppStartup", "[UserAuthInitializer] (主线程) ⚠️ 用户未登录。")
            return null
        }
    }
}

/**
 * 任务四：初始化数据库 (并行)
 *
 * 这是一个耗时的I/O密集型任务，适合在后台并行执行。
 * 它现在依赖于串行的 UserAuthInitializer，这是合法的（并行可以依赖串行）。
 */
class DatabaseInitializer : Initializer<Unit>() {
    override fun initMode(): InitMode = InitMode.PARALLEL

    override fun dependencies(): List<KClass<out Initializer<*>>> = listOf(UserAuthInitializer::class)

    override suspend fun init(context: Context, provider: DependenciesProvider) {
        val userProfile = provider.resultOrNull<UserProfile>(UserAuthInitializer::class)
        val dbName = userProfile?.userId ?: "default_user"
        Log.d("AppStartup", "[DatabaseInitializer] 准备为用户 '$dbName' 初始化数据库...")
        delay(500) // 模拟数据库打开和迁移
        // 这里可以执行 Room.databaseBuilder(...).build()
        Log.d("AppStartup", "[DatabaseInitializer] ✅ 数据库初始化完成。")
    }
}


// --- UI和第三方SDK初始化 (主线程或依赖所有后台任务) ---

/**
 * 任务五：初始化广告SDK (串行)
 *
 * 大多数广告SDK要求在主线程初始化。这是一个典型的串行任务。
 * 它现在依赖于同样是串行的 ConfigInitializer 和 UserAuthInitializer，完全符合规则。
 */
class AdsInitializer : Initializer<Unit>() {
    // 默认就是 InitMode.SERIAL，运行在主线程
    override fun dependencies(): List<KClass<out Initializer<*>>> = listOf(ConfigInitializer::class, UserAuthInitializer::class)

    override suspend fun init(context: Context, provider: DependenciesProvider) {
        val config = provider.result<AppConfig>(ConfigInitializer::class)
        val user = provider.resultOrNull<UserProfile>(UserAuthInitializer::class)

        // 验证我们是否在主线程
        assert(Looper.myLooper() == Looper.getMainLooper()) { "广告SDK必须在主线程初始化！" }

        Log.d("AppStartup", "[AdsInitializer] (主线程) 开始初始化广告SDK...")
        Log.d("AppStartup", "[AdsInitializer] (主线程) 使用配置: ${config.featureFlags}, 用户VIP状态: ${user?.isVip}")

        // VIP用户可能不需要初始化广告
        if (user?.isVip == true) {
            Log.d("AppStartup", "[AdsInitializer] (主线程) ✅ 用户是VIP，跳过广告初始化。")
            return
        }

        delay(150) // 模拟SDK初始化耗时
        Log.d("AppStartup", "[AdsInitializer] (主线程) ✅ 广告SDK初始化完成。")
    }
}


/**
 * 任务六：初始化UI主题和皮肤 (串行)
 *
 * 这是一个必须在主线程执行的任务，用于应用启动时的主题设置。
 * 它依赖于并行任务（DatabaseInitializer）和串行任务（AdsInitializer）。
 *
 * **重要：根据规则，这里也存在非法依赖！UIThemeInitializer(串行) 不能依赖 DatabaseInitializer(并行)。**
 *
 * **再次修正：** 让 UIThemeInitializer 只依赖串行任务。
 */
class UIThemeInitializer : Initializer<Unit>() {
    // 默认是串行
    // 修正：移除对并行任务 DatabaseInitializer 的依赖。
    override fun dependencies(): List<KClass<out Initializer<*>>> = listOf(
        // DatabaseInitializer::class, // 这是非法的，移除！
        AdsInitializer::class // 确保广告SDK已处理
    )

    override suspend fun init(context: Context, provider: DependenciesProvider) {
        // 由于并行任务（如DatabaseInitializer）与此任务没有依赖关系，
        // 它们可能会在此任务执行时仍在后台运行。
        assert(Looper.myLooper() == Looper.getMainLooper()) { "UI任务必须在主线程！" }
        Log.d("AppStartup", "[UIThemeInitializer] (主线程) 所有前置串行任务已完成，开始应用UI主题...")
        delay(50)
        Log.d("AppStartup", "[UIThemeInitializer] (主线程) ✅ UI主题设置完毕。")
    }
}
