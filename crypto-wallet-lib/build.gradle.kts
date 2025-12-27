import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    id("com.vanniktech.maven.publish") version "0.35.0"
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
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
        }

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

                api(libs.secp256k1.kmp)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.mock)
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

                implementation(libs.secp256k1.kmp.jni.android)
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
    coordinates(
        groupId = "io.github.innfocus",
        artifactId = "crypto-wallet-lib",
        version = "1.1.6"
    )

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

        developers {
            developer {
                id.set("nqhuy2509")
                name.set("quanhuy")
                email.set("nqhuy250901@gmail.com")
            }
        }

        scm {
            url.set("https://github.com/innfocus/cryptowallet")
        }
    }

    publishToMavenCentral()

    signAllPublications()
}
