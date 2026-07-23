plugins {
    id("io.ktor.plugin") version "3.5.1" apply false
    // SBOM CycloneDX agregado dos módulos — gate de supply chain no CI (ADR-0025)
    id("org.cyclonedx.bom") version "3.3.0"
    // Native Image opt-in em :http_api — GAP-BA (ADR-0030 Fase 2); nunca roda no CI
    id("org.graalvm.buildtools.native") version "1.1.5" apply false
}

// O gate de SCA cobre os artefatos PUBLICADOS (ADR-0025): o `runtimeClasspath` (binário principal
// kanban-vision-api) E o `migrationRuntime` (binário de migração kanban-vision-migrate, ADR-0032) —
// GAP-DA. Sem `migrationRuntime` uma CVE que só o binário de migração alcança passava o gate (o SBOM
// era ponto cego dele, como o jackson do #329 mostrou). Dependências de teste/tooling ficam de fora.
// `migrationRuntime` só existe no http_api, então o nome casa exatamente aquele grafo.
allprojects {
    tasks.withType<org.cyclonedx.gradle.CyclonedxDirectTask>().configureEach {
        includeConfigs.set(listOf("runtimeClasspath", "migrationRuntime"))
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