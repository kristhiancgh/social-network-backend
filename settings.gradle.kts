// =============================================================================
//  Multi-project build for the social network backend.
//
//  Layout mirrors the deployment topology: one Gradle module per container.
//  `shared-kernel` is the only library module - it is never deployed on its
//  own, it is compiled into each service that depends on it.
// =============================================================================

rootProject.name = "social-network-backend"

include(
    "shared-kernel",
    "api-gateway",
    "auth-service",
    "profile-service",
    "post-service",
    "like-service",
)

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
