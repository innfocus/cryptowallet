# Local Development Guide

To build and use this library locally without publishing to Maven Central (Sonatype), follow these steps. This is the fastest way to test changes in another project on the same machine.

## 1. Publish to Local Maven Repository

In the root directory of the `cryptowallet` project, run:

```bash
./gradlew publishToMavenLocal
```

This command builds the library (Android, iOS, JVM, Common) and copies the artifacts to your local `.m2` repository (usually located at `~/.m2/repository/`).

Based on the current configuration, your library coordinates are:
- **Group ID:** `io.github.innfocus`
- **Artifact ID:** `crypto-wallet-lib`
- **Version:** `1.2.2.5` (or whatever is currently in `crypto-wallet-lib/build.gradle.kts`)

## 2. Use the Local Library in Another Project

There are two main ways to do this:

### Option A: Maven Local (Better for occasional updates)
...
```kotlin
dependencies {
    // Replace the version if you changed it in the library project
    implementation("io.github.innfocus:crypto-wallet-lib:1.2.2.5")
}
```

### Option B: Composite Builds (Best for active development)
This method links the library source directly to your app. You don't need to run `publishToMavenLocal` every time you change code.

1. In your **consumer project's** `settings.gradle.kts`:
```kotlin
includeBuild("../path/to/cryptowallet") {
    dependencySubstitution {
        substitute(module("io.github.innfocus:crypto-wallet-lib")).using(project(":crypto-wallet-lib"))
    }
}
```

2. In your **consumer project's** module `build.gradle.kts`, keep the dependency as usual:
```kotlin
dependencies {
    implementation("io.github.innfocus:crypto-wallet-lib:1.2.2.5")
}
```
Gradle will now replace the external dependency with your local source code automatically.

## Tips for Faster Iteration

- **Use SNAPSHOT versions:** If you are making frequent changes, update the version in `crypto-wallet-lib/build.gradle.kts` to `1.2.2.5-SNAPSHOT`. Gradle handles snapshot updates more aggressively than fixed versions.
- **Force Refresh:** If your consumer project isn't picking up the latest local changes, run:
  ```bash
  ./gradlew build --refresh-dependencies
  ```
- **Check Local Files:** You can verify the published files by looking into your local repository:
  ```bash
  ls -R ~/.m2/repository/io/github/innfocus/crypto-wallet-lib/
  ```

## iOS Specifics
If you are working on iOS, you might prefer using the XCFramework directly or via Swift Package Manager (if configured for local paths). However, `mavenLocal` is the standard approach for the Kotlin/Android side of KMP.
