pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven (url = "https://maven.pkg.jetbrains.space/public/p/ktor/eap")
        maven (url = "https://dl.bintray.com/kotlin/kotlin-eap" )
        maven (url = "https://api.xposed.info/" )
        maven (url = "https://jitpack.io" )
    }
}

buildscript {
    repositories {
        mavenCentral()
        maven {
            url = uri("https://storage.googleapis.com/r8-releases/raw")
        }
    }
    dependencies {}
}

rootProject.name = "Portal"
include(":app")
include(":xposed")
include(":system-api")
include(":nmea")
