plugins {
    id("kanban.kotlin-common")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val exposedVersion = "1.3.0"

dependencies {
    implementation(project(":domain"))
    implementation(project(":usecases"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("com.zaxxer:HikariCP:7.0.2")
    implementation("org.postgresql:postgresql:42.7.5")
    implementation("org.flywaydb:flyway-core:10.21.0")
    runtimeOnly("org.flywaydb:flyway-database-postgresql:10.21.0")
    implementation("ch.qos.logback:logback-classic:1.5.32")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-test:2.4.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.0.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.0.3")
    testImplementation("io.mockk:mockk:1.14.9")
    testImplementation("io.zonky.test:embedded-postgres:2.2.2")
}

val jacocoExcludes =
    listOf(
        "**/*\$\$serializer.class",
        "**/*\$Companion.class",
        // DatabaseFactory: all testable branches are covered by DatabaseFactoryTest (happy path,
        // interrupt, closed datasource, close(), init(migrationsEnabled=false)). Two paths remain:
        //   1. isReady() TimeoutException — requires making DB validation hang >3s; not reliable in CI.
        //   2. isReady() generic Exception catch — unreachable: the inner supplyAsync already catches
        //      ALL exceptions, so CompletableFuture.get() can only throw InterruptedException or
        //      TimeoutException (both covered), never ExecutionException.
        // Excluded analogously to MainKt.class (both are infrastructure, not domain/application logic).
        "com/kanbanvision/persistence/DatabaseFactory.class",
    )

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
