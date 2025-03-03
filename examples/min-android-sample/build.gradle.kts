// This project can only build against local deployed artifacts
buildscript {
    extra["realmVersion"] = file("${rootProject.rootDir.absolutePath}/../../buildSrc/src/main/kotlin/Config.kt")
        .readLines()
        .first { it.contains("const val version") }
        .let {
            it.substringAfter("\"").substringBefore("\"")
        }

    repositories {
        maven(url = "file://${rootProject.rootDir.absolutePath}/../../packages/build/m2-buildrepo")
        gradlePluginPortal()
        google()
        mavenCentral()
        maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.7.3")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")
        classpath("io.github.xilinjia.krdb:gradle-plugin:${rootProject.extra["realmVersion"]}")
    }
}

tasks.create("clean", Delete::class) {
    delete.add(rootProject.buildDir)
}

allprojects {
    repositories {
        maven(url = "file://${rootProject.rootDir.absolutePath}/../../packages/build/m2-buildrepo")
        google()
        mavenCentral()
        maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
    }
}
