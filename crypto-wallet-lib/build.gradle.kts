import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    id("com.vanniktech.maven.publish") version "0.28.0"
    kotlin("plugin.serialization").version("1.9.10")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
        publishLibraryVariants("release", "debug")
    }
    
    val xcf = XCFramework()
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "crypto-wallet-lib"
            xcf.add(this)
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.serialization)
            implementation(libs.ktor.client.json)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation("com.squareup.okio:okio:3.9.0")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        androidMain.dependencies {
            val ktor_version  : String by project
            implementation ("org.web3j:core:4.8.9")
            implementation("org.web3j:core:4.8.9-android")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
            implementation("io.ktor:ktor-client-okhttp:$ktor_version")
        }

        iosMain.dependencies {
            val ktor_version  : String by project
            implementation("io.ktor:ktor-client-darwin:$ktor_version")
        }
    }
}

android {
    namespace = "com.lybia.cryptowallet"
    compileSdk = 34
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}


mavenPublishing {
    // Define coordinates for the published artifact
    coordinates(
        groupId = "io.github.innfocus",
        artifactId = "crypto-wallet-lib",
        version = "1.0.1"
    )

    // Configure POM metadata for the published artifact
    pom {
        name.set("Crypto Wallet Libirary")
        description.set("A library for blockchain crypto wallet")
        inceptionYear.set("2024")
        url.set("https://github.com/innfocus/cryptowallet")

        licenses {
            license {
                name.set("MIT")
                url.set("https://opensource.org/licenses/MIT")
            }
        }

        // Specify developers information
        developers {
            developer {
                id.set("nqhuy2509")
                name.set("quanhuy")
                email.set("nqhuy250901@gmail.com")
            }
        }

        // Specify SCM information
        scm {
            url.set("https://github.com/innfocus/cryptowallet")
        }
    }

    // Configure publishing to Maven Central
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    // Enable GPG signing for all publications
    signAllPublications()
}

task("testClasses") {
    doLast {
        println("Hello from testClasses")
    }
}

