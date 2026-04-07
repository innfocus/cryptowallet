# Ethereum & Arbitrum Integration cho iOS

Hướng dẫn tích hợp Ethereum và Arbitrum vào ứng dụng iOS qua XCFramework. Cả 2 chain dùng chung `EthereumManager`.

> **Tài liệu kỹ thuật chi tiết:** [Ethereum spec](../chains/ethereum.md)
> **CommonCoinsManager API:** [API Reference](../api/common-coins-manager.md)

---

## 1. Cài đặt & Import

### Swift Package Manager

**Xcode -> File -> Add Package Dependencies** -> nhập URL repository.

```swift
import crypto_wallet_lib
```

> **Minimum iOS version:** 13.0

---

## 2. Khởi tạo

```swift
Config.shared.setNetwork(network: .mainnet)
Config.shared.apiKeyInfura = "your-infura-project-id"
Config.shared.apiKeyEtherscan = "your-etherscan-api-key"
Config.shared.apiKeyArbiscan = "your-arbiscan-api-key"      // Arbitrum
Config.shared.apiKeyOwlRacle = "your-owlracle-api-key"      // Arbitrum gas

CommonCoinsManager.companion.initialize(mnemonic: "your mnemonic words ...")
let ccm = CommonCoinsManager.companion.shared
```

### EthereumManager trực tiếp (advanced)

```swift
let ethManager = EthereumManager(mnemonic: "your mnemonic words ...")
```

---

## 3. Lấy địa chỉ ví

```swift
let address = try ccm.getAddress(coin: .ethereum)
// "0x..." (42 chars, EIP-55 checksummed)
// Cùng address cho Ethereum và Arbitrum
```

> **Derivation:** BIP-44 `m/44'/60'/0'/0/0` (secp256k1)

---

## 4. Lấy số dư

```swift
// ETH
let ethBalance = try await ccm.getBalance(coin: .ethereum)
print("ETH: \(ethBalance.balance)")

// Arbitrum ETH
let arbBalance = try await ccm.getBalance(coin: .arbitrum)
print("ARB ETH: \(arbBalance.balance)")
```

---

## 5. Gửi ETH

### 5.1 Cơ bản

```swift
let result = try await ccm.sendCoin(
    coin: .ethereum,
    toAddress: "0xRecipient...",
    amount: 0.5    // 0.5 ETH
)

// Arbitrum
let arbResult = try await ccm.sendCoin(
    coin: .arbitrum,
    toAddress: "0xRecipient...",
    amount: 0.1
)
```

### 5.2 Với Service Fee

```swift
let result = try await ccm.sendCoin(
    coin: .ethereum,
    toAddress: "0xRecipient...",
    amount: 1.0,
    serviceAddress: "0xServiceAddr...",
    serviceFee: 0.01
)
// 2 transactions: main + service fee
```

### 5.3 Nâng cao (EthereumManager)

```swift
let coinNetwork = CoinNetwork(name: .ethereum)

// EIP-1559 (recommended)
let result = try await ethManager.sendEthBigInt(
    toAddress: "0xRecipient...",
    amountWei: ethManager.ethToWei(ethAmount: 0.5),
    coinNetwork: coinNetwork,
    gasLimit: KotlinLong(value: 21000),
    maxPriorityFeeGwei: KotlinLong(value: 2),
    maxFeeGwei: KotlinLong(value: 30)
)
```

> **Long overflow:** `Long.MAX` ≈ 9.2 ETH. Dùng `sendEthBigInt()` + `ethToWei()` cho amount lớn.

---

## 6. ERC-20 Token

### 6.1 Balance

```swift
let result = try await ccm.getTokenBalance(
    coin: .ethereum,
    address: address,
    contractAddress: "0xUSDT..."
)
print("USDT: \(result.balance)")
```

### 6.2 Transfer

```swift
let coinNetwork = CoinNetwork(name: .ethereum)
let result = try await ethManager.sendErc20TokenBigInt(
    contractAddress: "0xUSDT...",
    toAddress: "0xRecipient...",
    amount: KotlinBigInteger.fromLong(value: 10_000_000),  // 10 USDT (6 decimals)
    coinNetwork: coinNetwork,
    gasLimit: KotlinLong(value: 65000)
)
```

---

## 7. NFT (ERC-721)

```swift
// List
let nfts = try await ccm.getNFTs(coin: .ethereum, address: address)

// Transfer
let result = try await ccm.transferNFT(
    coin: .ethereum,
    nftAddress: "0xNftContract...",
    toAddress: "0xRecipient..."
)
```

---

## 8. Fee Estimation

```swift
let fee = try await ccm.estimateFee(
    coin: .ethereum,
    amount: 0.5,
    toAddress: "0xRecipient..."
)
print("Fee: \(fee.fee) ETH, Gas: \(fee.gasLimit), Price: \(fee.gasPrice) gwei")
```

---

## 9. Kotlin-Swift Type Mapping

| Kotlin | Swift | Ghi chú |
|--------|-------|---------|
| `suspend fun` | `async throws` | `try await` |
| `Long` (gasLimit, gasPrice) | `KotlinLong` | Wrap: `KotlinLong(value: 21000)` |
| `BigInteger` (amountWei) | `KotlinBigInteger` | Dùng factory methods |
| `Double` | `Double` | Balance, fee |
| `GasPrice` | `GasPrice` | `.safeGasPrice`, `.proposeGasPrice`, `.fastGasPrice` |

---

## 10. Ethereum vs Arbitrum

| Thuộc tính | Ethereum | Arbitrum |
|-----------|----------|---------|
| NetworkName | `.ethereum` | `.arbitrum` |
| Address | Cùng | Cùng |
| Gas cost | Cao (~20-50 gwei) | Thấp (~0.1-0.5 gwei) |
| Finality | ~12s | ~0.25s |
| Gas source | Etherscan | OwlRacle |

---

## 11. Network

| Mục | Ethereum | Arbitrum |
|---|---|---|
| Mainnet RPC | `mainnet.infura.io` | `arbitrum-mainnet.infura.io` |
| Testnet RPC | `sepolia.infura.io` | `arbitrum-sepolia.infura.io` |

```swift
Config.shared.setNetwork(network: .testnet)  // Đổi sang Sepolia
```

---

## 12. Lỗi thường gặp

| Lỗi | Nguyên nhân | Cách fix |
|---|---|---|
| Insufficient funds | Balance < amount + gas | Kiểm tra balance trước |
| Nonce too low | TX đã submit | Retry |
| Long overflow | Amount > 9.2 ETH | Dùng `sendEthBigInt()` |
| Token transfer fail | Thiếu ETH cho gas | Cần ETH để trả gas |
| API key missing | Thiếu Infura key | Set `Config.shared.apiKeyInfura` |
