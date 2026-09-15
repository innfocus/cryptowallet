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
         url: "https://github.com/innfocus/cryptowallet/releases/download/v1.2.14/crypto_wallet_lib.xcframework.zip",
         checksum: "37a8940686ec81957374b13c291c4d694fec6b0ad29ed9e3967485e27dc19144"
      )
   ]
)
