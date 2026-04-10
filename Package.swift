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
         url: "https://github.com/innfocus/cryptowallet/releases/download/v1.2.9/crypto_wallet_lib.xcframework.zip",
         checksum: "b1cc923dd7fc95a73c555a00517f608a192f3fb4b6552dc68dac4272688cedc1"
      )
   ]
)
