import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

version = "1.0"

kotlin {
    jvm()

    androidLibrary {
        registerTarget() 
        namespace = "io.github.xilinjia.krdb.example.minandroidsample"
        compileSdk = 31
        minSdk = 16
    }

    sourceSets {
        commonMain.dependencies {
            implementation("io.github.xilinjia.krdb:library-base:${rootProject.ext["realmVersion"]}")
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}