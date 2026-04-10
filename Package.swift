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
         url: "https://github.com/innfocus/cryptowallet/releases/download/v1.2.8/crypto_wallet_lib.xcframework.zip",
         checksum: "f948308dc4eec00df7adb959c1bd052f498136c5b3e54dfafab01697a57b31ea"
      )
   ]
)
