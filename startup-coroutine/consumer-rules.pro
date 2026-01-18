# ============================================================
# Startup Coroutine
# ============================================================

# 1. 保护 Initializer 接口本身不被移除或混淆
-keep class com.dboy.startup.coroutine.api.Initializer

# 2. 关键规则：
# 保持所有实现了 Initializer 接口的类的类名不被混淆。
# 这可以防止 R8 将不同的 Initializer 类合并。
-keep class * implements com.dboy.startup.coroutine.api.Initializer {
    <init>();
}