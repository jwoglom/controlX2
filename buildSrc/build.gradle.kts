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
    implementation("com.android.tools.build:gradle-api:8.8.2")
    compileOnly("com.android.tools.build:gradle:8.8.2")
    implementation("org.ow2.asm:asm:9.7")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.10")
}
