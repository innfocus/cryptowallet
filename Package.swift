// swift-tools-version:5.3
import PackageDescription

let package = Package(
   name: "crypto-wallet-lib",
   products: [
      .library(name: "crypto-wallet-lib", targets: ["crypto-wallet-lib"])
   ],
   targets: [
      .binaryTarget(
         name: "crypto-wallet-lib",
         url: "https://github.com/innfocus/cryptowallet/releases/download/v1.2.4/crypto_wallet_lib.xcframework.zip",
         checksum: "144f7fdea1ecabd595e82712bf967d2369ead2f8eada35018c55e5d8adb1a1d9"
      )
   ]
)
