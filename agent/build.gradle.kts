plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.anthropic"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Core
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // Test
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}

tasks.test {
    useJUnitPlatform()
}
