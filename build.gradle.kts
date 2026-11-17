// Top-level build file: plugin versions are declared in gradle/libs.versions.toml.
// 所有子模块用到的插件必须先在这里 alias apply false 声明版本，否则子模块解析会失败。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
