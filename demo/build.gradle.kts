plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "com.anthropic"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":agent"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}

application {
    mainClass.set("com.anthropic.sdk.demo.MainKt")
}
