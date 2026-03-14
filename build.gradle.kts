// Root build file — plugins are declared in submodule build files and buildSrc convention plugin.
// The Ktor plugin is declared here so subprojects can reference it without specifying the version.
plugins {
    alias(libs.plugins.ktor) apply false
}

tasks.register("testAll") {
    description = "Runs tests in all subprojects"
    group = "verification"
    dependsOn(subprojects.map { "${it.path}:test" })
}
