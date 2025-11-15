import kotlin.reflect.KClass


/**
 * 标记一个类是启动任务，并可以为其指定一个全局唯一的别名。
 * @param alias 此任务的别名，用于被其他任务精确依赖。
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class StartupInitializer(
    val alias: String = "",
    val depend: Array<Depend> = []
)

/**
 * 声明当前任务的一个依赖。
 * @param type 依赖的数据类型。
 * @param fromAlias 依赖来源任务的别名。如果该类型的依赖是唯一的，则可以省略。
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Depend(
    val type: KClass<*>,
    val fromAlias: String = ""
)
    