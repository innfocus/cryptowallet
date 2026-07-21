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
         url: "https://github.com/innfocus/cryptowallet/releases/download/v1.2.13/crypto_wallet_lib.xcframework.zip",
         checksum: "65cd999c47a7ddff4a9c83a23fc8936592b9327d16b0f7cf6632cde4e8809525"
      )
   ]
)
