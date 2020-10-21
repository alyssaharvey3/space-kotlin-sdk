@file:Suppress("PropertyName")

import org.jetbrains.kotlin.gradle.plugin.LanguageSettingsBuilder

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("maven-publish")
}

repositories {
    mavenCentral()
    maven { url = uri("https://kotlin.bintray.com/kotlinx") }
    maven { url = uri("https://cache-redirector.jetbrains.com/plugins.gradle.org/m2") }
}

val this_version: String by rootProject.extra
val mavenForPublishing: groovy.lang.Closure<PublishingExtension> by rootProject.extra
val ktor_version: String by rootProject.extra
val kotlinx_coroutines_version: String by rootProject.extra
val kotlin_version: String by rootProject.extra
val jackson_version: String by rootProject.extra

fun setGroupAndVersion(publication: MavenPublication) {
    publication.groupId = "org.jetbrains"
    publication.version = this_version
}

kotlin {
    explicitApi()
    jvm {
        compilations.configureEach {
            tasks.named(compileKotlinTaskName).configure {
                kotlinOptions {
                    jvmTarget = "1.6"
                }
            }
        }
        mavenPublication {
            setGroupAndVersion(this)
            artifactId = "space-api-client-runtime-jvm"
            pom {
                name.set("Space API client runtime")
                description.set("Runtime for JetBrains Space API client")
            }
        }
    }

    js {
        browser {
        }
        nodejs {
        }
        mavenPublication {
            setGroupAndVersion(this)
            artifactId = "space-api-client-runtime-js"
            pom {
                name.set("Space API client runtime")
                description.set("Runtime for JetBrains Space API client")
            }
        }

        compilations.configureEach {
            tasks.named(compileKotlinTaskName).configure {
                kotlinOptions {
                    metaInfo = true
                    sourceMap = true
                    sourceMapEmbedSources = "always"
                    moduleKind = "commonjs"
                    main = "call"
                }
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api("io.ktor:ktor-client-core:$ktor_version")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinx_coroutines_version")
                api("org.jetbrains.kotlinx:kotlinx-datetime:0.1.0")
            }
        }

        val jvmMain by getting {
            dependencies {
                api("org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlin_version")

                api("com.fasterxml.jackson.core:jackson-core:$jackson_version")
                api("com.fasterxml.jackson.core:jackson-databind:$jackson_version")
            }
        }

        val jsMain by getting {
            dependencies {
                api("io.ktor:ktor-client-js:$ktor_version")
            }
        }
        all {
            languageSettings(closureOf<LanguageSettingsBuilder> {
                useExperimentalAnnotation("kotlin.time.ExperimentalTime")
                useExperimentalAnnotation("kotlin.ExperimentalStdlibApi")
            })
        }
    }
}

publishing {
    mavenForPublishing(this)
    publications {
        val kotlinMultiplatform by getting(MavenPublication::class) {
            setGroupAndVersion(this)
            artifactId = "space-api-client-runtime"
            pom {
                name.set("Space API client runtime")
                description.set("Runtime for JetBrains Space API client")
            }
        }
        val metadata by getting(MavenPublication::class) {
            setGroupAndVersion(this)
            artifactId = "space-api-client-runtime-metadata"
            pom {
                name.set("Space API client runtime")
                description.set("Runtime for JetBrains Space API client")
            }
        }
    }
}
