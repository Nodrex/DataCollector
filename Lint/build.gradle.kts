plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
}
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

dependencies {
    compileOnly(libs.lint.api)
    compileOnly(libs.kotlin.stdlib.jdk8)

    // Optional: For testing lint rules
    testImplementation(libs.lint.tests)
    testImplementation(libs.junit)
}

// Create lint JAR with proper manifest
tasks.named<Jar>("jar") {
    manifest {
        attributes["Lint-Registry-v2"] = "com.nodrex.datacollector.lint.DataCollectorIssueRegistry"
    }
}