# TON Integration cho iOS

Hướng dẫn tích hợp TON (The Open Network) vào ứng dụng iOS qua XCFramework, bao gồm gửi/nhận TON, Jetton (token), NFT, staking, và DNS.

> **Tài liệu kỹ thuật chi tiết:** [TON spec](../chains/ton.md)
> **CommonCoinsManager API:** [API Reference](../api/common-coins-manager.md)

---

## 1. Cài đặt XCFramework

### Swift Package Manager

**Xcode -> File -> Add Package Dependencies** -> nhập URL repository.

### Manual

1. Download `crypto_wallet_lib.xcframework.zip` từ GitHub Release
2. Giải nén, kéo vào Xcode project
3. Đảm bảo **Embed & Sign** trong "Frameworks, Libraries, and Embedded Content"

> **Minimum iOS version:** 13.0

---

## 2. Import

```swift
import crypto_wallet_lib
```

Tất cả class Kotlin trong `commonMain` được export sang Swift qua KMP framework.

---

## 3. Khởi tạo

### 3.1 CommonCoinsManager (recommended)

```swift
import crypto_wallet_lib

// Chọn network
Config.shared.setNetwork(network: .mainnet) // hoặc .testnet

// Khởi tạo singleton
CommonCoinsManager.companion.initialize(mnemonic: "your mnemonic words ...")
let ccm = CommonCoinsManager.companion.shared
```

### 3.2 TonManager trực tiếp (advanced)

```swift
let tonManager = TonManager(
    mnemonics: "your mnemonic words ...",
    walletVersion: .w5  // .w5 (default, V5R1) hoặc .w4 (legacy V4R2)
)
```

---

## 4. Lấy địa chỉ ví

```swift
// Qua CommonCoinsManager
let address = try ccm.getAddress(coin: .ton)
// "UQ..." hoặc "EQ..." (Base64 user-friendly, non-bounceable)

// Qua TonManager
let address = tonManager.getAddress()
```

> **W5 (V5R1):** Address khác nhau giữa mainnet/testnet (wallet_id encode networkGlobalId).
> **Mnemonic:** 24 từ TON native hoặc 12 từ BIP-39 (SLIP-0010 path `m/44'/607'/0'`).

---

## 5. Lấy số dư

```swift
// Kotlin suspend fun -> Swift async throws
let result = try await ccm.getBalance(coin: .ton)
print("TON Balance: \(result.balance)")
```

> **Quy đổi:** 1 TON = 1,000,000,000 nanoTON. API trả về Double (đã quy đổi).

---

## 6. Lịch sử giao dịch

### 6.1 Không phân trang

```swift
let txs = try await ccm.getTransactionHistory(coin: .ton)
```

### 6.2 Có phân trang

```swift
// Trang đầu
let page1 = try await ccm.getTransactionHistoryPaginated(
    coin: .ton,
    limit: 20,
    pageParam: nil
)

// Trang tiếp
if page1.hasMore, let nextParam = page1.nextPageParam {
    let page2 = try await ccm.getTransactionHistoryPaginated(
        coin: .ton,
        limit: 20,
        pageParam: nextParam  // ["lt": "...", "hash": "..."]
    )
}
```

---

## 7. Gửi TON

### 7.1 Cơ bản

```swift
let result = try await ccm.sendCoin(
    coin: .ton,
    toAddress: "UQRecipient...",
    amount: 1.5,   // 1.5 TON
    memo: MemoData(memo: "Payment")
)
if result.success {
    print("TX: \(result.txHash)")
}
```

### 7.2 Với smallest unit

```swift
let result = try await ccm.sendCoinExact(
    coin: .ton,
    toAddress: "UQRecipient...",
    amountSmallestUnit: 1_500_000_000  // 1.5 TON in nanoTON
)
```

### 7.3 Với Service Fee

```swift
let result = try await ccm.sendCoin(
    coin: .ton,
    toAddress: "UQRecipient...",
    amount: 5.0,
    serviceAddress: "UQServiceAddr...",
    serviceFee: 0.1  // 0.1 TON service fee
)
// 2 transaction riêng biệt: main + service fee
```

### 7.4 Nâng cao (qua TonManager)

```swift
let coinNetwork = CoinNetwork(name: .ton)
let seqno = try await tonManager.getSeqno(coinNetwork: coinNetwork)
let boc = try await tonManager.signTransaction(
    toAddress: "UQRecipient...",
    amountNano: 1_500_000_000,
    seqno: Int32(seqno),
    memo: "Hello"
)
let result = try await tonManager.transfer(dataSigned: boc, coinNetwork: coinNetwork)
```

---

## 8. Jetton (Token)

### 8.1 Lấy metadata

```swift
let metadata = try await ccm.getJettonMetadata(contractAddress: "EQJettonMaster...")
// metadata?.name = "Tether USD"
// metadata?.symbol = "USDT"
// metadata?.decimals = 6
```

### 8.2 Lấy số dư

```swift
let result = try await ccm.getTokenBalance(
    coin: .ton,
    address: address,
    contractAddress: "EQJettonMaster..."
)
print("Token balance: \(result.balance)")
```

> **Lưu ý:** `getTokenBalance` dùng default 9 decimals. Với USDT (6), dùng TonManager trực tiếp:
> ```swift
> let balance = try await tonManager.getBalanceToken(
>     address: address,
>     contractAddress: "EQ...",
>     coinNetwork: coinNetwork,
>     decimals: 6
> )
> ```

### 8.3 Gửi Jetton

```swift
let result = try await ccm.sendJetton(
    toAddress: "UQRecipient...",
    jettonMasterAddress: "EQJettonMaster...",
    amount: 10.5,    // 10.5 USDT
    decimals: 6,     // USDT = 6
    memo: "Payment"
)
```

### 8.4 Lịch sử giao dịch Jetton

```swift
let page = try await ccm.getTokenTransactionHistoryPaginated(
    coin: .ton,
    policyId: "EQJettonMaster...",  // Jetton Master address
    assetName: "",
    limit: 20
)
// page.transactions là [JettonTransactionParsed]
// type: "send", "receive", "burn"
```

---

## 9. NFT

### 9.1 Lấy danh sách

```swift
let nfts = try await ccm.getNFTs(coin: .ton, address: address)
for nft in nfts ?? [] {
    print("NFT: \(nft.name ?? "") - \(nft.imageUrl ?? "")")
}
```

### 9.2 Transfer

```swift
let result = try await ccm.transferNFT(
    coin: .ton,
    nftAddress: "EQNftContract...",
    toAddress: "UQRecipient...",
    memo: "Gift"
)
```

---

## 10. Staking

### 10.1 Detect pool type

```swift
let poolType = try await ccm.detectTonPoolType(poolAddress: "EQPool...")
// .nominator, .tonstakers, .bemo, .unknown
```

### 10.2 Stake

```swift
let result = try await ccm.stake(
    coin: .ton,
    amount: 10_000_000_000,  // 10 TON
    poolAddress: "EQPool..."
)
```

### 10.3 Unstake

```swift
// poolAddress BẮT BUỘC cho TON
let result = try await ccm.unstake(
    coin: .ton,
    amount: 5_000_000_000,
    poolAddress: "EQPool..."
)
```

> - **Tonstakers/Bemo:** Burn staking tokens
> - **Nominator:** Pool tự trả tiền, không cần gọi unstake

### 10.4 Staking balance

```swift
let result = try await ccm.getStakingBalance(
    coin: .ton,
    poolAddress: "EQPool..."
)
// result.balance = gốc + lãi (TON)
```

---

## 11. DNS

```swift
// Forward: domain -> address
let address = try await ccm.resolveTonDns(domain: "alice.ton")

// Reverse: address -> domain
let domain = try await ccm.reverseResolveTonDns(address: "UQ...")
```

---

## 12. Fee Estimation

```swift
let fee = try await ccm.estimateFee(coin: .ton, amount: 1.0)
print("Fee: \(fee.fee) \(fee.unit)")
```

---

## 13. Kotlin ↔ Swift Type Mapping

| Kotlin | Swift | Ghi chú |
|--------|-------|---------|
| `suspend fun` | `async throws` | Gọi với `try await` |
| `Int` | `Int32` / `KotlinInt` | Chú ý khi truyền param |
| `Long` | `Int64` / `KotlinLong` | nanoTON, amount |
| `Double` | `Double` | Balance, fee |
| `String?` | `String?` | Nullable tương thích |
| `List<T>` | `[T]` | Collection mapping tự động |
| `Map<String, Any?>` | `[String: Any?]` | pageParam, configs |
| `enum class` | Swift enum | `.w5`, `.ton`, `.mainnet` |

> **Lưu ý quan trọng:**
> - `seqno: Int` (Kotlin) -> truyền `Int32(seqno)` trong Swift
> - `amountNano: Long` (Kotlin) -> truyền `Int64(amount)` trong Swift
> - Callback-style API không có trên iOS — chỉ dùng `async/await`

---

## 14. Network & Explorer

| Mục | Mainnet | Testnet |
|---|---|---|
| RPC v2 | `toncenter.com/api/v2/jsonRPC` | `testnet.toncenter.com/api/v2/jsonRPC` |
| RPC v3 | `toncenter.com/api/v3` | `testnet.toncenter.com/api/v3` |
| Explorer | [tonscan.org](https://tonscan.org) | [testnet.tonscan.org](https://testnet.tonscan.org) |

```swift
Config.shared.setNetwork(network: .testnet)
Config.shared.apiKeyToncenter = "your-api-key"  // Optional
```

---

## 15. Lỗi thường gặp

| Lỗi | Nguyên nhân | Cách fix |
|---|---|---|
| `WalletError.NetworkError` | API fail khi lấy seqno | Retry — rate limit hoặc mất mạng |
| Balance token sai 1000x | Default decimals 9 cho USDT (6) | Truyền `decimals: 6` |
| TX fail | `validUntil` hết hạn (60s) | Sign + broadcast ngay |
| NFT transfer fail | Không đủ TON cho gas | Cần >= 0.1 TON |
| Unstake error | Thiếu `poolAddress` | Bắt buộc cho TON |
| Crash khi gọi suspend fun | Không dùng async/await | Wrap trong Task {} hoặc async context |
