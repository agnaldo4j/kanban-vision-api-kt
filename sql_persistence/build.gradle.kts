plugins {
    id("kanban.kotlin-common")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val exposedVersion = "1.1.1"

dependencies {
    implementation(project(":domain"))
    implementation(project(":usecases"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("com.zaxxer:HikariCP:7.0.2")
    implementation("org.postgresql:postgresql:42.7.5")
    implementation("org.flywaydb:flyway-core:10.21.0")
    runtimeOnly("org.flywaydb:flyway-database-postgresql:10.21.0")
    implementation("ch.qos.logback:logback-classic:1.5.32")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-test:2.1.20")
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.0.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.0.3")
    testImplementation("io.mockk:mockk:1.14.9")
    testImplementation("io.zonky.test:embedded-postgres:2.2.2")
}

val jacocoExcludes = listOf("**/*\$\$serializer.class", "**/*\$Companion.class")

tasks.named<JacocoReport>("jacocoTestReport") {
    classDirectories.setFrom(
        sourceSets.main.get().output.asFileTree.matching {
            exclude(jacocoExcludes)
        },
    )
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    classDirectories.setFrom(
        sourceSets.main.get().output.asFileTree.matching {
            exclude(jacocoExcludes)
        },
    )
}
