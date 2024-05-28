// swift-tools-version:5.3
import PackageDescription

let package = Package(
   name: "crypto-wallet-lib",
   platforms: [
     .iOS(.v13),
   ],
   products: [
      .library(name: "crypto-wallet-lib", targets: ["crypto-wallet-lib"])
   ],
   targets: [
      .binaryTarget(
         name: "crypto-wallet-lib",
         url: "https://github.com/innfocus/cryptowallet/releases/download/v1.0.2/crypto_wallet_lib.xcframework.zip",
         checksum:"a8fd5063f92aee79f8fe9dbaf7752208f9957f8df3061be244b0ce264f44581b"
         )
   ]
)