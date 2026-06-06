plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

rootProject.name = "kanban-vision-api"

include(
    ":domain",
    ":usecases",
    ":sql_persistence",
    ":http_api",
)
