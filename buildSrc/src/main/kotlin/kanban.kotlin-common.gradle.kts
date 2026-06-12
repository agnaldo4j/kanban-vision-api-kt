plugins {
    id("org.jetbrains.kotlin.jvm")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
    jacoco
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
        )
    }
}

repositories {
    mavenCentral()
}

detekt {
    config.setFrom("${rootDir}/config/detekt/detekt.yml")
    buildUponDefaultConfig = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    // Detekt 1.23.8 supports JVM targets up to 22; pin explicitly so it doesn't
    // inherit the toolchain version (25) which Detekt doesn't yet recognize.
    jvmTarget = "22"
    reports {
        sarif.required.set(true)
    }
}

ktlint {
    version.set("1.5.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.named("jacocoTestReport"))
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.withType<Test>())
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    violationRules {
        rule {
            limit {
                minimum = "0.97".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(
        tasks.named("jacocoTestCoverageVerification"),
        tasks.named("detektTest"),
    )
}
