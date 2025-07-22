plugins.apply("maven-publish")
plugins.apply("org.jetbrains.dokka")

val javadocJar by tasks.creating(Jar::class) {
    dependsOn(tasks.named("dokkaHtml"))
    from(tasks.named("dokkaHtml").get().outputs)
    archiveClassifier.set("javadoc")
}

afterEvaluate {
    // Use configure<PublishingExtension> for Kotlin DSL
    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("release") {
                when {
                    plugins.hasPlugin("com.android.library") -> {
                        from(components.getByName("release"))
                    }
                    plugins.hasPlugin("org.jetbrains.kotlin.jvm") -> {
                        from(components.getByName("java"))
                    }
                    plugins.hasPlugin("java-library") -> {
                        from(components.getByName("java"))
                    }
                }

                artifact(javadocJar)
            }
        }
    }
}