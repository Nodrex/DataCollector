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
    // It needs to know about the annotation to find it
    implementation(project(":Annotations"))

    // It needs the KSP API to analyze code
    implementation(libs.ksp.api)
}

apply(from = "$rootDir/publish.gradle.kts")