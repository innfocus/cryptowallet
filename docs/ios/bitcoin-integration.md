# Bitcoin (BTC) Integration cho iOS

Hướng dẫn tích hợp Bitcoin (BTC) vào ứng dụng iOS qua XCFramework, bao gồm 3 loại địa chỉ (Legacy, Nested SegWit, Native SegWit), gửi/nhận BTC, fee estimation, và service fee.

> **Tài liệu kỹ thuật chi tiết:** [Bitcoin spec](../chains/bitcoin.md)

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

// Khởi tạo với mnemonic (BIP-39, 12 hoặc 24 từ)
let mnemonic = "your mnemonic words ..."
let manager = CommonCoinsManager(mnemonic: mnemonic)
```

### 3.2 BitcoinManager trực tiếp (advanced)

Dùng khi cần truy cập API chi tiết hơn (chọn loại địa chỉ, local TX building, fee rate).

```swift
let bitcoinManager = BitcoinManager(mnemonics: "your mnemonic words ...")
```

---

## 4. Loại địa chỉ (Address Types)

Bitcoin hỗ trợ **3 loại địa chỉ**, mỗi loại có đặc điểm riêng:

| Loại | BIP | Prefix (Mainnet) | Prefix (Testnet) | Phí | Mô tả |
|------|-----|-------------------|-------------------|-----|-------|
| **Native SegWit** | BIP-84 | `bc1q...` | `tb1q...` | Thấp nhất | Mặc định, khuyên dùng |
| **Nested SegWit** | BIP-49 | `3...` | `2...` | Trung bình | Tương thích ngược |
| **Legacy** | BIP-44 | `1...` | `m.../n...` | Cao nhất | Cũ, hỗ trợ mọi nơi |

---

## 5. Lấy địa chỉ ví

### 5.1 Qua CommonCoinsManager (Native SegWit mặc định)

```swift
let address = manager.getAddress(coin: .bitcoin)
// Trả về: bc1q... (Native SegWit, mặc định)
```

### 5.2 Qua BitcoinManager (chọn loại)

```swift
// Native SegWit (mặc định, phí thấp nhất)
let nativeSegWit = bitcoinManager.getNativeSegWitAddress(numberAccount: 0)
// → "bc1q..." (mainnet) / "tb1q..." (testnet)

// Nested SegWit (tương thích ngược)
let nestedSegWit = bitcoinManager.getNestedSegWitAddress(accountIndex: 0)
// → "3..." (mainnet) / "2..." (testnet)

// Legacy
let legacy = bitcoinManager.getLegacyAddress(accountIndex: 0)
// → "1..." (mainnet) / "m..." hoặc "n..." (testnet)

// Hoặc dùng hàm chung
let address = bitcoinManager.getAddressByType(
    addressType: .nativeSegwit,
    accountIndex: 0
)
```

### 5.3 Derivation Paths

```
Native SegWit:  m/84'/0'/account'/0/0   (mainnet)
                m/84'/1'/account'/0/0   (testnet)

Nested SegWit:  m/49'/0'/account'/0/0   (mainnet)
                m/49'/1'/account'/0/0   (testnet)

Legacy:         m/44'/0'/account'/0/0   (mainnet)
                m/44'/1'/account'/0/0   (testnet)
```

> **Lưu ý:** `accountIndex` cho phép derive nhiều ví con từ cùng 1 mnemonic. Thường dùng `0`.

---

## 6. Lấy số dư

Kotlin `suspend` functions tự động convert thành Swift `async` functions.

```swift
func fetchBalance() async {
    do {
        // Qua CommonCoinsManager — trả về BTC (Double)
        let balance = try await manager.getBalance(coin: .bitcoin, address: nil)
        print("BTC Balance: \(balance)")
    } catch {
        print("Error: \(error)")
    }
}

// Hoặc qua BitcoinManager
func fetchBalanceDirect() async {
    do {
        let balance = try await bitcoinManager.getBalance()
        print("BTC Balance: \(balance)")
    } catch {
        print("Error: \(error)")
    }
}
```

> **Quy đổi:** 1 BTC = 100,000,000 satoshi. API trả về đã quy đổi sang BTC.

---

## 7. Lịch sử giao dịch

```swift
func fetchTransactions() async {
    do {
        let history = try await manager.getTransactionHistory(coin: .bitcoin, address: nil)
        print("Transactions: \(history ?? [])")
    } catch {
        print("Error: \(error)")
    }
}

// Qua BitcoinManager — trả về chi tiết hơn
func fetchTransactionsDirect() async {
    do {
        let history = try await bitcoinManager.getTransactionHistory()
        history?.forEach { tx in
            print("Hash: \(tx.hash), Fee: \(tx.fees)")
        }
    } catch {
        print("Error: \(error)")
    }
}
```

---

## 8. Ước lượng phí (Fee Estimation)

Bitcoin phí biến động theo tình trạng mempool. Có 2 cách ước lượng:

### 8.1 BlockCypher (API-assisted)

```swift
func estimateFee() async {
    do {
        let feeSatoshi = try await bitcoinManager.estimateFee(
            toAddress: "bc1q...",
            amountSatoshi: 50_000         // 0.0005 BTC
        )
        print("Estimated fee: \(feeSatoshi ?? 0) sat")
    } catch {
        print("Error: \(error)")
    }
}
```

### 8.2 Local (Mempool.space fee rate)

```swift
func estimateFeeDynamic() async {
    do {
        // Lấy fee rate từ Mempool.space
        let feeRates = try await EsploraApiService.shared.getRecommendedFeeRates()
        // feeRates.fastestFee     — xác nhận block tiếp theo (sat/vB)
        // feeRates.halfHourFee    — ~30 phút (mặc định)
        // feeRates.hourFee        — ~1 giờ
        // feeRates.economyFee     — tiết kiệm
        // feeRates.minimumFee     — tối thiểu

        // Ước lượng fee cho 1 giao dịch cụ thể
        let feeEstimate = try await bitcoinManager.estimateFeeLocal(
            toAddress: "bc1q...",
            amountSatoshi: 50_000,
            feeRateSatPerVbyte: feeRates?.halfHourFee  // nil = tự lấy, fallback 10 sat/vB
        )
        print("Local fee estimate: \(feeEstimate ?? 0) sat")
    } catch {
        print("Error: \(error)")
    }
}
```

### 8.3 Bảng tham chiếu vsize

| Loại địa chỉ | Input (vB) | Output (vB) | Base (vB) |
|---------------|-----------|-------------|-----------|
| Native SegWit (P2WPKH) | 68 | 31 | 11 |
| Nested SegWit (P2SH-P2WPKH) | 91 | 32 | 11 |
| Legacy (P2PKH) | 148 | 34 | 11 |

> **Công thức:** `fee = vsize * feeRate (sat/vB)`
>
> **Ví dụ:** 1 input + 2 output (Native SegWit) = 11 + 68 + 31*2 = 141 vB × 10 sat/vB = 1,410 sat

---

## 9. Gửi BTC

### 9.1 API-Assisted (BlockCypher)

BlockCypher xử lý UTXO selection trên server, client chỉ cần ký:

```swift
func sendBTC() async {
    do {
        let result = try await bitcoinManager.sendBtc(
            toAddress: "bc1q...",
            amountSatoshi: 100_000,                         // 0.001 BTC
            addressType: .nativeSegwit,                     // mặc định
            accountIndex: 0
        )

        if result.success {
            print("TX hash: \(result.hash ?? "")")
        } else {
            print("Error: \(result.error ?? "")")
        }
    } catch {
        print("Error: \(error)")
    }
}
```

### 9.2 Local (Client-Side, khuyên dùng)

Full client-side: fetch UTXOs, build TX, ký, và broadcast — không phụ thuộc BlockCypher:

```swift
func sendBTCLocal() async {
    do {
        let result = try await bitcoinManager.sendBtcLocal(
            toAddress: "bc1q...",
            amountSatoshi: 100_000,                         // 0.001 BTC
            addressType: .nativeSegwit,
            accountIndex: 0,
            feeRateSatPerVbyte: nil                         // nil = tự lấy từ Mempool.space
        )

        if result.success {
            print("TX ID: \(result.hash ?? "")")
        } else {
            print("Error: \(result.error ?? "")")
        }
    } catch {
        print("Error: \(error)")
    }
}
```

**Flow nội bộ:**
1. Fetch UTXOs từ Esplora (`blockstream.info`)
2. Lấy fee rate từ Mempool.space (hoặc dùng giá trị truyền vào)
3. UTXO selection (largest-first) + fee estimation
4. Build & sign TX hoàn toàn trên client
5. Broadcast qua Esplora `/tx`

### 9.3 Với Service Fee

Service fee được thêm như output phụ trong cùng 1 transaction (khác XRP cần 2 TX):

```swift
// API-Assisted
let result = try await bitcoinManager.sendBtc(
    toAddress: "bc1qRecipient...",
    amountSatoshi: 100_000,                    // 0.001 BTC cho người nhận
    addressType: .nativeSegwit,
    accountIndex: 0,
    serviceAddress: "bc1qService...",          // Địa chỉ nhận phí dịch vụ
    serviceFeeAmount: 5_000                    // 5,000 sat service fee
)
// TX có 3 outputs: recipient + service fee + change

// Local
let result = try await bitcoinManager.sendBtcLocal(
    toAddress: "bc1qRecipient...",
    amountSatoshi: 100_000,
    addressType: .nativeSegwit,
    accountIndex: 0,
    serviceAddress: "bc1qService...",
    serviceFeeAmount: 5_000
)
```

### 9.4 Chọn loại địa chỉ khi gửi

Loại địa chỉ **gửi từ** ảnh hưởng đến phí (vì script size khác nhau):

```swift
// Gửi từ Native SegWit (phí thấp nhất)
let result = try await bitcoinManager.sendBtcLocal(
    toAddress: "1LegacyAddr...",              // Có thể gửi đến BẤT KỲ loại nào
    amountSatoshi: 50_000,
    addressType: .nativeSegwit                // Địa chỉ GỬI
)

// Gửi từ Legacy (phí cao hơn ~2x)
let result = try await bitcoinManager.sendBtcLocal(
    toAddress: "bc1q...",
    amountSatoshi: 50_000,
    addressType: .legacy
)
```

---

## 10. UTXO Selection & Dust

### UTXO Selection

Library dùng thuật toán **largest-first** (tham lam):
1. Lọc chỉ UTXO đã confirmed
2. Sắp xếp theo giá trị giảm dần
3. Chọn đến khi tổng input >= amount + fee ước lượng
4. Tính change = tổng input - amount - fee

### Dust Threshold

Change quá nhỏ sẽ bị hấp thụ vào phí:

| Loại | Dust Threshold |
|------|---------------|
| P2WPKH (Native SegWit) | 294 sat |
| P2PKH (Legacy) | 546 sat |

> **Ví dụ:** Nếu change = 200 sat (P2WPKH) → change bị bỏ, cộng vào fee.

---

## 11. Swift Concurrency

Tất cả API blockchain là `async`. Gọi từ SwiftUI hoặc UIKit:

### SwiftUI

```swift
struct BTCWalletView: View {
    @State private var balance: Double = 0
    @State private var address: String = ""

    var body: some View {
        VStack {
            Text("Address: \(address)")
            Text("BTC: \(balance)")
        }
        .task {
            address = bitcoinManager.getAddress()
            balance = (try? await bitcoinManager.getBalance()) ?? 0
        }
    }
}
```

### UIKit

```swift
class BTCViewController: UIViewController {
    override func viewDidLoad() {
        super.viewDidLoad()
        Task {
            let balance = try? await bitcoinManager.getBalance()
            balanceLabel.text = "\(balance ?? 0) BTC"

            let address = bitcoinManager.getAddress()
            addressLabel.text = address
        }
    }
}
```

---

## 12. Network & Explorer

| Mục | Mainnet | Testnet |
|---|---|---|
| BlockCypher API | `api.blockcypher.com/v1/btc/main` | `api.blockcypher.com/v1/btc/test3` |
| Esplora API | `blockstream.info/api` | `blockstream.info/testnet/api` |
| Mempool.space | `mempool.space/api` | `mempool.space/testnet/api` |
| Explorer | [blockchain.com/btc](https://www.blockchain.com/btc) | [testnet.blockchain.info](https://testnet.blockchain.info) |

Chuyển đổi network:
```swift
Config.shared.setNetwork(network: .testnet)
// Tất cả API call + derivation path tự động đổi sang testnet
// coin_type: 0 (mainnet) → 1 (testnet)
// Address prefix: bc1q → tb1q, 1... → m.../n..., 3... → 2...
```

---

## 13. Lỗi thường gặp

| Lỗi | Nguyên nhân | Cách fix |
|---|---|---|
| `Missing Framework` | Chưa embed XCFramework | Add vào Frameworks & chọn Embed & Sign |
| `Architecture Mismatch` | Simulator M1/M2 | Đảm bảo framework hỗ trợ `iosArm64Sim` |
| Balance = 0 dù có BTC | Sai loại địa chỉ khi query | Dùng đúng address type đã nhận BTC |
| `insufficient funds` | Tổng UTXO < amount + fee | Kiểm tra balance trước khi gửi |
| Fee quá cao | Mempool đang congested | Dùng `economyFee` hoặc chờ phí giảm |
| Fee = 0 | API fee estimation fail | Fallback 10 sat/vB hoặc retry |
| TX không confirm | Fee quá thấp | Dùng `fastestFee` rate thay vì `economyFee` |
| Address sai prefix | Sai network config | Kiểm tra `Config.shared.setNetwork()` |
| Change "biến mất" | Change < dust threshold | Bình thường — change nhỏ bị cộng vào fee |
