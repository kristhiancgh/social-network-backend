// =============================================================================
//  api-gateway  (:8080)
// =============================================================================
//  The single entry point the browser talks to. It routes /api/** to the right
//  service and proxies the WebSocket upgrade to like-service.
// =============================================================================

plugins {
    java
    jacoco
    alias(libs.plugins.springBoot)
    alias(libs.plugins.springDepMgmt)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${libs.versions.springCloud.get()}")
    }
}

dependencies {
    implementation(libs.gateway.server.webflux)
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(libs.springdoc.webflux)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.bootJar {
    archiveFileName = "app.jar"
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}
