import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    id("com.vanniktech.maven.publish") version "0.36.0"
    alias(libs.plugins.kotlin.serialization)
}

kotlin {


    android {
        namespace = "com.lybia.cryptowallet"
        compileSdk = 36
        minSdk = 26

        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_21)
                }
            }
        }

    }

    jvm()

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
        val commonMain by getting {
            dependencies {
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
                implementation(libs.kermit)
                implementation(libs.bignum)

                implementation(libs.ton.kotlin.crypto)
                implementation(libs.ton.kotlin.contract)
                implementation(libs.ton.kotlin.tvm)
                implementation(libs.ton.kotlin.tlb)
                implementation(libs.ton.kotlin.block.tlb)

                api(libs.secp256k1.kmp)

            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.mock)
                implementation(libs.kotest.property)
                api(libs.secp256k1.kmp)
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(libs.web3j.core.android)
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.ktor.client.okhttp)
//                implementation(fileTree(kotlin.collections.mapOf("dir" to "libs", "include" to kotlin.collections.listOf("*.jar"))))
//                implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

                implementation(fileTree("libs") {
                    include("*.jar")
                })


                implementation(libs.kotlin.unsigned)
                implementation(libs.kotlin.stdlib.jdk8)

                implementation(libs.gson)
                implementation(libs.retrofit)
                implementation(libs.converter.gson)
                implementation(libs.okhttp)

                implementation(libs.mockito.core)

                implementation(libs.secp256k1.kmp.jni.android)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.secp256k1.kmp.jni.jvm)
            }
        }

        val iosMain by creating {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
        // Link các target iOS vào iosMain
        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting

        iosMain.dependsOn(commonMain)
        iosX64Main.dependsOn(iosMain)
        iosArm64Main.dependsOn(iosMain)
        iosSimulatorArm64Main.dependsOn(iosMain)
    }
}


mavenPublishing {
    // Các thông tin Group, ArtifactId, Version và POM sẽ được plugin 
    // tự động lấy từ gradle.properties (GROUP, POM_ARTIFACT_ID, VERSION_NAME, POM_*)

    publishToMavenCentral()

    signAllPublications()
}
