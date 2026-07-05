plugins {
    id("io.ktor.plugin") version "3.5.0" apply false
    // SBOM CycloneDX agregado dos módulos — gate de supply chain no CI (ADR-0025)
    id("org.cyclonedx.bom") version "3.2.4"
}

// O gate de SCA cobre o artefato publicado (ADR-0025): só o runtimeClasspath
// entra no SBOM — dependências de teste e de tooling de build ficam de fora.
allprojects {
    tasks.withType<org.cyclonedx.gradle.CyclonedxDirectTask>().configureEach {
        includeConfigs.set(listOf("runtimeClasspath"))
    }
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