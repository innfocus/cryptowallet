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
         url: "https://github.com/innfocus/cryptowallet/releases/download/v1.2.10/crypto_wallet_lib.xcframework.zip",
         checksum: "fc3c117c6382bac311a128e9312894685dc149c9d8aaa3447eeaf5bac133285b"
      )
   ]
)
