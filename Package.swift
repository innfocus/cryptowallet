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
         url: "https://github.com/innfocus/cryptowallet/releases/download/v1.2.16/crypto_wallet_lib.xcframework.zip",
         checksum: "1769d9bffc0776a525d8383c15e9b59857b27dfc3807e142bc145dc08f45e2bb"
      )
   ]
)
