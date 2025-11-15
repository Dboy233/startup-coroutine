plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
    id("com.google.devtools.ksp")
}
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

dependencies {
    // 1. ✨ 必须的依赖：依赖 API 模块来识别注解和接口
    implementation(project(":startup-coroutine-api"))

    // 2. KSP 的核心 API 库，让我们可以编写处理器逻辑
    // 使用 compileOnly 是因为这些库只在编译期需要，不应打包到最终的 jar 中
    compileOnly(libs.symbol.processing.api) // 请使用您项目配置的 KSP 版本

    // 3. Google AutoService：用于自动注册我们的处理器
    // 这样 KSP 就能在编译时自动发现并运行它
    compileOnly(libs.auto.service.annotations)
    // 注意：这里需要对自身使用 ksp，让 auto-service-annotations 生效
    ksp(libs.auto.service)
}

