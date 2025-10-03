plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(17)
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    compileOnly("com.android.tools.build:gradle:8.5.2")
    implementation("com.android.tools.build:gradle-api:8.5.2")
    implementation("org.ow2.asm:asm:9.7")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.compose.ui:ui-tooling:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.8")
    implementation("androidx.compose.runtime:runtime:1.7.8")
    implementation("app.cash.paparazzi:paparazzi:1.3.5")
    implementation(kotlin("reflect"))
}
