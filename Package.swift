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
         url: "https://github.com/innfocus/cryptowallet/releases/download/v1.2.15/crypto_wallet_lib.xcframework.zip",
         checksum: "0fb42e11800cf5f370fbfe08b3842dd22c45bd24a4aa8b81fad1b28d89caaab2"
      )
   ]
)
