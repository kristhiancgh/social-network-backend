// =============================================================================
//  shared-kernel
// =============================================================================
//  A plain library, NOT a Spring Boot application: it has no main class and
//  must never be packaged as an executable jar. That is why the Spring Boot
//  plugin is absent here and only the dependency-management plugin is applied,
//  so the module still resolves versions from the Boot BOM.
// =============================================================================

plugins {
    `java-library`
    jacoco
    alias(libs.plugins.springDepMgmt)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}")
    }
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-security")
    api("org.springframework.boot:spring-boot-starter-validation")
    api(libs.springdoc.webmvc)
    api(libs.jjwt.api)
    api("org.springframework:spring-tx")
    api("org.springframework.data:spring-data-commons")
    compileOnly("org.springframework:spring-messaging")
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
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
