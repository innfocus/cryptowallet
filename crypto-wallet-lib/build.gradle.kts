import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    id("com.vanniktech.maven.publish") version "0.28.0"
    kotlin("plugin.serialization").version("2.0.0")
}


kotlin {

    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions{
                    jvmTarget.set(JvmTarget.JVM_11)
                }
            }
        }
        publishLibraryVariants("release", "debug")
    }

    val xcframeworkName = "crypto-wallet-lib"
    val xcf = XCFramework(xcframeworkName)
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = xcframeworkName
            binaryOption("bundleId", "org.lybia.${xcframeworkName}")
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
            implementation(libs.okio)

            implementation(libs.krypto)
            implementation(libs.bitcoin.kmp)

            api(libs.secp256k1.kmp)

        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }

        androidMain.dependencies {
            implementation(libs.web3j.core.android)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
            implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

            implementation(libs.kotlin.unsigned)
            implementation(libs.kotlin.stdlib.jdk8)
            implementation(libs.cbor)
            implementation(libs.spongycastle.core)
            implementation(libs.prov)
            implementation(libs.curve25519.android)
            implementation(libs.guava)

            implementation(libs.rxjava)
            implementation(libs.rxandroid)
            implementation(libs.rxkotlin)

            implementation(libs.gson)
            implementation(libs.retrofit)
            implementation(libs.converter.gson)
            implementation(libs.okhttp)
            implementation(libs.adapter.rxjava2)

            implementation(libs.mockito.core)

            implementation(kotlin("stdlib"))
            implementation(libs.secp256k1.kmp.jni.android)
            implementation(libs.secp256k1.kmp.jni.jvm)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)

        }

    }
}

android {
    namespace = "com.lybia.cryptowallet"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}


mavenPublishing {
    // Define coordinates for the published artifact
    coordinates(
        groupId = "io.github.innfocus",
        artifactId = "crypto-wallet-lib",
        version = "1.0.3-alpha.7"
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
dependencies {
    testImplementation(libs.testng)
}

