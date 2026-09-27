pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// Declaracao de versoes apenas: os plugins sao aplicados em build.gradle.kts.
plugins {
    kotlin("jvm") version "2.0.21" apply false
    kotlin("plugin.spring") version "2.0.21" apply false
    id("org.springframework.boot") version "3.4.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

rootProject.name = "noctua-backend"
