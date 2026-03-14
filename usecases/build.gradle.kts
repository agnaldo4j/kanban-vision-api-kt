plugins {
    id("kanban.kotlin-common")
}

dependencies {
    api(project(":domain"))

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
}
