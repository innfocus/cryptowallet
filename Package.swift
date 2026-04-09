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
         url: "https://github.com/innfocus/cryptowallet/releases/download/v1.2.7/crypto_wallet_lib.xcframework.zip",
         checksum: "397918cd0e9decaa1d8bbb3881e4d9cb790aac0f4a6f866309dcea6a38322b0e"
      )
   ]
)
