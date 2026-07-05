plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.0")
    implementation("org.jetbrains.kotlin:kotlin-serialization:2.4.0")
    implementation("dev.detekt:dev.detekt.gradle.plugin:2.0.0-alpha.5")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:14.2.0")
    implementation("info.solidsoft.gradle.pitest:gradle-pitest-plugin:1.19.0")
}
