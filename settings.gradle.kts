plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "kanban-vision-api"

include(
    ":domain",
    ":usecases",
    ":sql_persistence",
    ":http_api",
    ":architecture",
)
