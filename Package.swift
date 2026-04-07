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
         checksum: "5c1482480cc632544156acc7255be862aa852a36fccb05ca4106a872463ff94d"
      )
   ]
)
