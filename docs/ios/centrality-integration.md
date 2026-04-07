# Centrality (CENNZ/CPAY) Integration cho iOS

Hướng dẫn tích hợp Centrality (CENNZnet) vào ứng dụng iOS qua XCFramework, bao gồm gửi/nhận CENNZ và CPAY — 2 token trên cùng 1 chain, cùng 1 address.

> **Tài liệu kỹ thuật chi tiết:** [Centrality spec](../chains/centrality.md)
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

## 2. Import & Khởi tạo

```swift
import crypto_wallet_lib

Config.shared.setNetwork(network: .mainnet)

CommonCoinsManager.companion.initialize(mnemonic: "your mnemonic words ...")
let ccm = CommonCoinsManager.companion.shared
```

### CentralityManager trực tiếp (advanced)

```swift
let centralityManager = CentralityManager(mnemonic: "your mnemonic words ...")
```

---

## 3. Multi-Asset Pattern: CENNZ vs CPAY

Centrality có 2 token trên cùng 1 chain, phân biệt bằng `assetId`:

| Token | assetId | Mô tả |
|-------|---------|-------|
| **CENNZ** | 1 | Token chính của CENNZnet |
| **CPAY** | 2 | Token thanh toán dịch vụ |

```swift
// Constants
let ASSET_CENNZ: Int32 = 1
let ASSET_CPAY: Int32 = 2
```

> CENNZ và CPAY dùng **cùng 1 address** — không cần lấy address riêng.

---

## 4. Lấy địa chỉ ví

```swift
let address = try await ccm.getAddressAsync(coin: .centrality)
// "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY" (ví dụ)
```

- **Format:** SS58 (Base58 + Blake2b-512, network prefix = 42)
- **Bắt đầu bằng:** "5"
- **Dài:** ~48 ký tự
- **Address derivation qua external API** — cần kết nối mạng

---

## 5. Lấy số dư

```swift
// CENNZ balance
let cennzResult = try await ccm.getCentralityBalance(
    address: nil,           // nil = dùng address mặc định
    assetId: ASSET_CENNZ    // 1
)
print("CENNZ: \(cennzResult.balance)")

// CPAY balance
let cpayResult = try await ccm.getCentralityBalance(
    address: nil,
    assetId: ASSET_CPAY     // 2
)
print("CPAY: \(cpayResult.balance)")
```

> **Quy đổi:** 1 CENNZ/CPAY = 10,000 smallest units. API trả về đã quy đổi.

---

## 6. Lịch sử giao dịch

### 6.1 Không phân trang

```swift
let txs = try await ccm.getCentralityTransactions(
    address: nil,
    assetId: ASSET_CENNZ
)
```

### 6.2 Có phân trang

```swift
// Trang đầu — CENNZ
let page1 = try await ccm.getTransactionHistoryPaginated(
    coin: .centrality,
    limit: 100,
    pageParam: ["page": 0, "assetId": ASSET_CENNZ]
)

// Trang tiếp
if page1.hasMore, let nextParam = page1.nextPageParam {
    let page2 = try await ccm.getTransactionHistoryPaginated(
        coin: .centrality,
        limit: 100,
        pageParam: nextParam  // ["page": 1, "assetId": 1]
    )
}

// CPAY transactions
let cpayTxs = try await ccm.getTransactionHistoryPaginated(
    coin: .centrality,
    limit: 100,
    pageParam: ["page": 0, "assetId": ASSET_CPAY]
)
```

---

## 7. Gửi CENNZ / CPAY

```swift
// Gửi CENNZ
let result = try await ccm.sendCentrality(
    fromAddress: myAddress,
    toAddress: "5RecipientAddr...",
    amount: 100.0,          // 100 CENNZ
    assetId: ASSET_CENNZ
)
if result.success {
    print("TX: \(result.txHash)")
}

// Gửi CPAY
let cpayResult = try await ccm.sendCentrality(
    fromAddress: myAddress,
    toAddress: "5RecipientAddr...",
    amount: 50.0,
    assetId: ASSET_CPAY
)
```

### Transaction Flow (internal)

```
1. Chain state (runtime version, genesis hash, nonce)
2. Build extrinsic (callIndex=0x0401 + assetId + recipient + amount)
3. SCALE encode signing payload
4. Sr25519 signing (external API)
5. Submit extrinsic qua RPC
```

---

## 8. Fee Estimation

```swift
let fee = try await ccm.estimateFee(coin: .centrality, amount: 100.0)
print("Fee: \(fee.fee)")  // 15287.0 (smallest units) = 1.5287 CENNZ
```

> Hiện chỉ trả về fee mặc định (static). Dynamic fee chưa được expose.

---

## 9. Kotlin-Swift Type Mapping

| Kotlin | Swift | Ghi chú |
|--------|-------|---------|
| `suspend fun` | `async throws` | Gọi với `try await` |
| `Int` (assetId) | `Int32` | `ASSET_CENNZ = 1`, `ASSET_CPAY = 2` |
| `Double` | `Double` | Balance, amount |
| `String?` | `String?` | Address, error |

---

## 10. Network

| Mục | Endpoint |
|---|---|
| RPC Node | `cennznet.unfrastructure.io/public` |
| Explorer API | `service.eks.centralityapp.com/cennznet-explorer-api/api` |
| Signing Service | `fgwallet.srsfc.com` |

> Hiện chỉ hỗ trợ **mainnet**. Chưa có testnet config.

---

## 11. Lỗi thường gặp

| Lỗi | Nguyên nhân | Cách fix |
|---|---|---|
| Address rỗng | API derivation fail | Kiểm tra mạng, retry `getAddressAsync()` |
| `SigningFailed` | Signing API down | Kiểm tra kết nối `fgwallet.srsfc.com` |
| Balance = 0 | Sai `assetId` | CENNZ = 1, CPAY = 2 |
| `InvalidSS58Address` | Address sai format | Phải bắt đầu "5", ~48 ký tự |
| Fee không đủ | Thiếu CPAY | Cần CPAY để trả TX fee |
