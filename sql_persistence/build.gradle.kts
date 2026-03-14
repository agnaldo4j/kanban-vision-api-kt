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
    testImplementation("com.h2database:h2:2.3.232")
}
