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

## 5. Lịch sử giao dịch (phân trang)

```swift
// Trang đầu
let page1 = try await ccm.getTransactionHistoryPaginated(
    coin: .ethereum,
    limit: 20,
    pageParam: nil
)

// Trang tiếp
if page1.hasMore, let nextParam = page1.nextPageParam {
    let page2 = try await ccm.getTransactionHistoryPaginated(
        coin: .ethereum,
        limit: 20,
        pageParam: nextParam  // ["page": 2]
    )
}
```

---

## 6. Gửi ETH

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

## 7. ERC-20 Token

### 7.1 Balance

```swift
let result = try await ccm.getTokenBalance(
    coin: .ethereum,
    address: address,
    contractAddress: "0xUSDT..."
)
print("USDT: \(result.balance)")
```

### 7.2 Token Transaction History (phân trang)

```swift
let page1 = try await ccm.getTokenTransactionHistoryPaginated(
    coin: .ethereum,
    policyId: "0xUSDT_Contract...",  // ERC-20 contract address
    assetName: "",
    limit: 20
)
// page1.transactions: [TransactionToken]
// page1.nextPageParam = ["page": 2]
```

### 7.3 Transfer

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

### 7.4 Approve ERC-20

```swift
let coinNetwork = CoinNetwork(name: .ethereum)

// Approve spender to spend 100 USDT (6 decimals)
let result = try await ethManager.approveErc20Token(
    contractAddress: "0xUSDT...",
    spenderAddress: "0xDexRouter...",
    amount: KotlinBigInteger.fromLong(value: 100_000_000),  // 100 USDT
    coinNetwork: coinNetwork,
    gasLimit: nil
)
```

### 7.5 Check Allowance

```swift
let coinNetwork = CoinNetwork(name: .ethereum)

let allowance = try await ethManager.getAllowanceErc20Token(
    contractAddress: "0xUSDT...",
    ownerAddress: myAddress,
    spenderAddress: "0xDexRouter...",
    coinNetwork: coinNetwork
)
// allowance: KotlinBigInteger? — in smallest token unit
```

---

## 8. NFT (ERC-721)

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

## 9. Fee Estimation

```swift
let fee = try await ccm.estimateFee(
    coin: .ethereum,
    amount: 0.5,
    toAddress: "0xRecipient..."
)
print("Fee: \(fee.fee) ETH, Gas: \(fee.gasLimit), Price: \(fee.gasPrice) gwei")
```

---

## 10. Kotlin-Swift Type Mapping

| Kotlin | Swift | Ghi chú |
|--------|-------|---------|
| `suspend fun` | `async throws` | `try await` |
| `Long` (gasLimit, gasPrice) | `KotlinLong` | Wrap: `KotlinLong(value: 21000)` |
| `BigInteger` (amountWei) | `KotlinBigInteger` | Dùng factory methods |
| `Double` | `Double` | Balance, fee |
| `GasPrice` | `GasPrice` | `.safeGasPrice`, `.proposeGasPrice`, `.fastGasPrice` |

---

## 11. Ethereum vs Arbitrum

| Thuộc tính | Ethereum | Arbitrum |
|-----------|----------|---------|
| NetworkName | `.ethereum` | `.arbitrum` |
| Address | Cùng | Cùng |
| Gas cost | Cao (~20-50 gwei) | Thấp (~0.1-0.5 gwei) |
| Finality | ~12s | ~0.25s |
| Gas source | Etherscan | OwlRacle |

---

## 12. Network

| Mục | Ethereum | Arbitrum |
|---|---|---|
| Mainnet RPC | `mainnet.infura.io` | `arbitrum-mainnet.infura.io` |
| Testnet RPC | `sepolia.infura.io` | `arbitrum-sepolia.infura.io` |

```swift
Config.shared.setNetwork(network: .testnet)  // Đổi sang Sepolia
```

---

## 13. Lỗi thường gặp

| Lỗi | Nguyên nhân | Cách fix |
|---|---|---|
| Insufficient funds | Balance < amount + gas | Kiểm tra balance trước |
| Nonce too low | TX đã submit | Retry |
| Long overflow | Amount > 9.2 ETH | Dùng `sendEthBigInt()` |
| Token transfer fail | Thiếu ETH cho gas | Cần ETH để trả gas |
| API key missing | Thiếu Infura key | Set `Config.shared.apiKeyInfura` |
