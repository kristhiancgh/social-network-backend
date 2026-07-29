// =============================================================================
//  Root build script.
//
//  Deliberately thin: it only declares the plugins on the classpath and the
//  coordinates shared by every module. All real configuration lives in each
//  module's own build.gradle.kts, so a module can be read and understood
//  without jumping back up here.
// =============================================================================

plugins {
    alias(libs.plugins.springBoot) apply false
    alias(libs.plugins.springDepMgmt) apply false
}

allprojects {
    group = "dev.social"
    version = "1.0.0"
}

subprojects {
    tasks.withType<Test>().configureEach {
        if (System.getenv("DOCKER_HOST").isNullOrBlank()) {
            val colimaSocket = File(System.getProperty("user.home"), ".colima/default/docker.sock")
            if (colimaSocket.exists()) {
                environment("DOCKER_HOST", "unix://${colimaSocket.absolutePath}")
                environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock")
            }
        }
    }
}

// -----------------------------------------------------------------------------
//  Convenience: `./gradlew testAll` runs every module's tests and coverage.
// -----------------------------------------------------------------------------
tasks.register("testAll") {
    group = "verification"
    description = "Runs unit and integration tests for every module."
    dependsOn(subprojects.map { "${it.path}:test" })
}

tasks.register("coverageAll") {
    group = "verification"
    description = "Produces the JaCoCo HTML report for every module."
    dependsOn(subprojects.map { "${it.path}:jacocoTestReport" })
}
