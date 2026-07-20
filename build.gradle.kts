plugins {
    id("io.ktor.plugin") version "3.5.1" apply false
    // SBOM CycloneDX agregado dos módulos — gate de supply chain no CI (ADR-0025)
    id("org.cyclonedx.bom") version "3.3.0"
    // Native Image opt-in em :http_api — GAP-BA (ADR-0030 Fase 2); nunca roda no CI
    id("org.graalvm.buildtools.native") version "1.1.5" apply false
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
    description = "Runs PITest mutation testing on all modules (domain, usecases, sql_persistence, http_api)"
    group = "verification"
    dependsOn(
        ":domain-common:pitest",
        ":domain-kanban:pitest",
        ":domain-simulation:pitest",
        ":usecases:pitest",
        ":sql_persistence:pitest",
        ":http_api:pitest",
    )
}