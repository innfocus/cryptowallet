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
         url: "https://github.com/innfocus/cryptowallet/releases/download/v1.2.12/crypto_wallet_lib.xcframework.zip",
         checksum: "7a3947afad17b06e035db2fdc13b02e58194b70b7951b8c5f4b59e8636435303"
      )
   ]
)
