plugins {
    id("kanban.kotlin-common")
}

dependencies {
    api(project(":domain"))

    api("io.arrow-kt:arrow-core:2.0.1")
    implementation("org.slf4j:slf4j-api:2.0.16")

    testImplementation("org.jetbrains.kotlin:kotlin-test:2.3.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.0.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.0.3")
    testImplementation("io.mockk:mockk:1.14.9")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("io.kotest:kotest-property:5.9.1")
}
