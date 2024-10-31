plugins {
    alias(libs.plugins.androidApplication) apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // JitPack 저장소 추가
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.3.1")
        classpath("com.google.ar.sceneform:plugin:1.17.1")

    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // JitPack 저장소 추가
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}
