plugins {
    id("kanban.kotlin-common")
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":usecases"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("com.zaxxer:HikariCP:7.0.2")
    implementation("org.postgresql:postgresql:42.7.5")
    implementation("org.flywaydb:flyway-core:10.21.0")
    runtimeOnly("org.flywaydb:flyway-database-postgresql:10.21.0")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    testImplementation("org.jetbrains.kotlin:kotlin-test:2.1.20")
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.0.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.0.3")
    testImplementation("io.mockk:mockk:1.14.2")
    testImplementation("io.zonky.test:embedded-postgres:2.2.2")
}

// Exclude Kotlin-generated coroutine continuation classes for the `query` suspend function.
// These are state-machine classes (JdbcBoardRepository$query$2, JdbcStepRepository$query$2,
// JdbcCardRepository$query$2) produced by the Kotlin compiler and contain unreachable
// addSuppressed branches that cannot be exercised without deliberately crashing the JDBC driver.
// Applied to both report and verification so the two tasks stay in sync.
val jacocoExcludes = listOf("**/*\$query\$*.class", "**/*\$\$serializer.class", "**/*\$Companion.class")

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
