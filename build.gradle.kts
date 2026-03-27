plugins {
    id("io.ktor.plugin") version "3.4.1" apply false
}

tasks.register("testAll") {
    description = "Runs tests, quality checks and coverage verification across all subprojects"
    group = "verification"
    dependsOn(subprojects.map { "${it.path}:check" })
}

tasks.register("pitestAll") {
    description = "Runs PITest mutation testing (domain module: SimulationEngine focus)"
    group = "verification"
    dependsOn(":domain:pitest")
}