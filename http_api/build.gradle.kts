plugins {
    id("kanban.kotlin-common")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("io.ktor.plugin")
}

application {
    mainClass.set("com.kanbanvision.httpapi.MainKt")
}

ktor {
    fatJar {
        archiveFileName.set("kanban-vision-api.jar")
    }
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    violationRules {
        rule {
            limit {
                minimum = "0.95".toBigDecimal()
            }
        }
    }
    classDirectories.setFrom(
        sourceSets.main.get().output.asFileTree.matching {
            exclude(
                "com/kanbanvision/httpapi/MainKt.class",
                "com/kanbanvision/httpapi/di/**",
                "**/*\$\$inlined\$*",
                "**/*\$\$serializer.class",
                "**/*\$Companion.class",
            )
        },
    )
}

tasks.named<JacocoReport>("jacocoTestReport") {
    classDirectories.setFrom(
        sourceSets.main.get().output.asFileTree.matching {
            exclude(
                "com/kanbanvision/httpapi/MainKt.class",
                "com/kanbanvision/httpapi/di/**",
                "**/*\$\$inlined\$*",
                "**/*\$\$serializer.class",
                "**/*\$Companion.class",
            )
        },
    )
}

dependencies {
    implementation(project(":usecases"))
    implementation(project(":sql_persistence"))

    implementation("io.arrow-kt:arrow-core:2.0.1")

    implementation("io.ktor:ktor-server-core-jvm:3.1.2")
    implementation("io.ktor:ktor-server-netty-jvm:3.1.2")
    implementation("io.ktor:ktor-server-auth-jvm:3.1.2")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:3.1.2")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:3.1.2")
    implementation("io.ktor:ktor-server-status-pages-jvm:3.1.2")
    implementation("io.ktor:ktor-server-call-logging-jvm:3.1.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.1.2")

    implementation("io.insert-koin:koin-core:4.1.1")
    implementation("io.insert-koin:koin-ktor:4.1.1")
    implementation("io.insert-koin:koin-logger-slf4j:4.1.1")

    implementation("io.github.smiley4:ktor-openapi:5.6.0")
    implementation("io.github.smiley4:ktor-swagger-ui:5.6.0")

    implementation("io.ktor:ktor-server-rate-limit-jvm:3.1.2")
    implementation("io.ktor:ktor-server-metrics-micrometer-jvm:3.1.2")
    implementation("io.micrometer:micrometer-registry-prometheus:1.14.5")

    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")
    implementation("org.codehaus.janino:janino:3.1.12")

    testImplementation("io.ktor:ktor-server-test-host-jvm:3.1.2")
    testImplementation("io.ktor:ktor-client-content-negotiation-jvm:3.1.2")
    testImplementation("io.insert-koin:koin-test-junit5:4.1.1")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.1.20")
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.0.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.0.3")
    testImplementation("io.mockk:mockk:1.14.2")
    testImplementation("com.h2database:h2:2.3.232")
}
