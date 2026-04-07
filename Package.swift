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
         url: "https://github.com/innfocus/cryptowallet/releases/download/v1.2.5/crypto_wallet_lib.xcframework.zip",
         checksum: "8cb8a21cf14e4ef65d5c005f4517575f480b5db099c923eadffcc3c7b71a4b88"
      )
   ]
)
