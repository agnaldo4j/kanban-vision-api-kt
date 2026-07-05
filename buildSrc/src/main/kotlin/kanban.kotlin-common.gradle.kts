plugins {
    id("org.jetbrains.kotlin.jvm")
    id("dev.detekt")
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

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    reports {
        // checkstyle is the renamed "xml" report from Detekt 1.x — CI parses it
        checkstyle.required.set(true)
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
