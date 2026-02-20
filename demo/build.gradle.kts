plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

group = "com.anthropic"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":agent"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

application {
    mainClass.set("com.anthropic.sdk.demo.MainKt")
}
