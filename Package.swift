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
         url: "https://github.com/innfocus/cryptowallet/releases/download/v1.2.6/crypto_wallet_lib.xcframework.zip",
         checksum: "60f65816786b044b57a5002a96ed138eb0e7951cccb1c0c23794d84631d55cc3"
      )
   ]
)
