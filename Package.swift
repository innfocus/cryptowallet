// swift-tools-version:5.3
import PackageDescription

let package = Package(
   name: "crypto-wallet-lib",
   platforms: [
     .iOS(.v13),
   ],
   products: [
      .library(name: "crypto-wallet-lib", targets: ["crypto-wallet-lib"])
   ],
   targets: [
      .binaryTarget(
         name: "crypto-wallet-lib",
         url: "https://github.com/innfocus/cryptowallet/releases/download/v1.0.1/crypto_wallet_lib.xcframework.zip",
         checksum:"5987ea2b57eff2d0275d3cf505c23a01ad5b66a16692ee5f7dc97ba44e89791d"
         )
   ]
)