plugins {
    id("kanban.kotlin-common")
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":usecases"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("org.postgresql:postgresql:42.7.5")
    implementation("ch.qos.logback:logback-classic:1.5.18")

    testImplementation("org.jetbrains.kotlin:kotlin-test:2.1.20")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    testImplementation("io.mockk:mockk:1.14.2")
    testImplementation("io.zonky.test:embedded-postgres:2.0.7")
}

// Exclude Kotlin-generated coroutine continuation classes from JaCoCo coverage.
// These are state-machine classes (e.g. JdbcBoardRepository$query$2) produced by the
// Kotlin compiler for suspend functions and contain unreachable exception-path branches
// that cannot be exercised without deliberately crashing the JDBC driver.
tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    classDirectories.setFrom(
        sourceSets.main.get().output.asFileTree.matching {
            exclude("**/*\$*\$*.class")
        },
    )
}
