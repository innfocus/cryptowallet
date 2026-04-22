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
         url: "https://github.com/innfocus/cryptowallet/releases/download/v1.2.11/crypto_wallet_lib.xcframework.zip",
         checksum: "3d55d43c22b211066b22ea7e7909a292b5f3e456e8694d96654e9939a70a28c9"
      )
   ]
)
