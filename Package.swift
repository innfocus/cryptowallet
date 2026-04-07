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
         url: "https://github.com/innfocus/cryptowallet/releases/download/v1.2.3/crypto_wallet_lib.xcframework.zip",
         checksum: "5401200de42a0866a8da618dd73f30c8f1af267b6e0a8b5d03b6dfe5364d0b1d"
      )
   ]
)
