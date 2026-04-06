# Ripple (XRP) Integration cho iOS

Hướng dẫn tích hợp Ripple (XRP) vào ứng dụng iOS qua XCFramework, bao gồm gửi/nhận XRP, destination tag, memo, và service fee.

> **Tài liệu kỹ thuật chi tiết:** [Ripple spec](../chains/ripple.md)

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

### 3.2 RippleManager trực tiếp (advanced)

Dùng khi cần truy cập API chi tiết hơn (fee estimation, reliable submission, pagination).

```swift
let rippleManager = RippleManager(mnemonic: "your mnemonic words ...")
```

---

## 4. Lấy địa chỉ ví

XRP là account-based — chỉ cần **1 địa chỉ** duy nhất (không có change address như Bitcoin).

```swift
// Qua CommonCoinsManager
let address = manager.getAddress(coin: .ripple)
// Trả về: r... (Base58Ripple, 25-35 ký tự)

// Qua RippleManager
let address = rippleManager.getAddress()
// Trả về: "rHb9CJAWyB4rj91VRWn96DkukG4bwdtyTh" (ví dụ)
```

> **Derivation path:** `m/44'/144'/0'/0/0` (BIP-44, coin_type = 144, secp256k1)
>
> **Lưu ý:** Địa chỉ XRP giống nhau trên Mainnet và Testnet — chỉ khác RPC endpoint.

---

## 5. Lấy số dư

Kotlin `suspend` functions tự động convert thành Swift `async` functions.

```swift
func fetchBalance() async {
    do {
        // Qua CommonCoinsManager — trả về XRP (Double)
        let balance = try await manager.getBalance(coin: .ripple, address: nil)
        print("XRP Balance: \(balance)")
    } catch {
        print("Error: \(error)")
    }
}

// Hoặc qua RippleManager
func fetchBalanceDirect() async {
    do {
        let balance = try await rippleManager.getBalance()
        print("XRP Balance: \(balance)")
    } catch {
        print("Error: \(error)")
    }
}
```

> **Quy đổi:** 1 XRP = 1,000,000 drops. API trả về đã quy đổi sang XRP.
>
> **Reserve:** Account cần tối thiểu **10 XRP** để kích hoạt trên ledger. Số dư khả dụng = balance - 10 XRP.

---

## 6. Lịch sử giao dịch

```swift
func fetchTransactions() async {
    do {
        let history = try await manager.getTransactionHistory(coin: .ripple, address: nil)
        print("Transactions: \(history ?? [])")
    } catch {
        print("Error: \(error)")
    }
}
```

### Phân trang (qua RippleManager)

```swift
func fetchPaginatedHistory() async {
    do {
        // Trang đầu
        let result = try await rippleManager.getTransactionHistoryPaginated(
            address: address,
            limit: 100,
            marker: nil
        )
        let transactions = result.first  // [RippleTransactionEntry]
        let nextMarker = result.second   // RippleMarker?

        // Trang tiếp theo
        if let marker = nextMarker {
            let page2 = try await rippleManager.getTransactionHistoryPaginated(
                address: address,
                limit: 100,
                marker: marker
            )
        }
    } catch {
        print("Error: \(error)")
    }
}
```

---

## 7. Ước lượng phí (Fee Estimation)

```swift
// Cách 1: Fee mặc định
let defaultFee = ACTCoin.ripple.feeDefault() // 0.000012 XRP (12 drops)

// Cách 2: Dynamic fee từ network
func fetchFee() async {
    do {
        let feeDynamic = try await rippleManager.estimateFeeDynamic() // Int64, đơn vị drops
        print("Network fee: \(feeDynamic) drops")
    } catch {
        print("Error: \(error)")
    }
}
```

> **Lưu ý:** Khi có service fee, tổng phí = `fee * 2` (vì gửi 2 transaction).

---

## 8. Gửi XRP

### 8.1 Cơ bản

```swift
func sendXRP() async {
    do {
        let result = try await rippleManager.sendXrp(
            toAddress: "rRecipient...",
            amountDrops: 5_000_000,        // 5 XRP
            feeDrops: 0,                   // 0 = tự estimate
            destinationTag: nil,           // tùy chọn
            memoText: nil,                 // tùy chọn
            serviceAddress: nil,
            serviceFeeDrops: 0,
            awaitValidated: false
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

### 8.2 Với Destination Tag

**Destination Tag** là số nguyên 32-bit dùng để phân biệt người nhận khi nhiều user dùng chung 1 address (thường gặp ở sàn giao dịch).

```swift
let result = try await rippleManager.sendXrp(
    toAddress: "rExchangeAddress...",
    amountDrops: 10_000_000,           // 10 XRP
    feeDrops: 0,
    destinationTag: 987654321,         // BẮT BUỘC khi sàn yêu cầu
    memoText: nil,
    serviceAddress: nil,
    serviceFeeDrops: 0,
    awaitValidated: false
)
```

> **Cảnh báo:** Gửi XRP đến sàn mà **thiếu Destination Tag** có thể dẫn đến **mất tiền**.

### 8.3 Với Memo

```swift
let result = try await rippleManager.sendXrp(
    toAddress: "rRecipient...",
    amountDrops: 5_000_000,
    feeDrops: 0,
    destinationTag: nil,
    memoText: "Invoice #42",           // UTF-8 text memo
    serviceAddress: nil,
    serviceFeeDrops: 0,
    awaitValidated: false
)
```

### 8.4 Với Service Fee

Khi ứng dụng thu phí dịch vụ, library tự động gửi 2 transaction:

```swift
let result = try await rippleManager.sendXrp(
    toAddress: "rRecipient...",
    amountDrops: 5_000_000,              // 5 XRP cho người nhận
    feeDrops: 0,
    destinationTag: nil,
    memoText: nil,
    serviceAddress: "rServiceAddr...",   // Địa chỉ nhận phí dịch vụ
    serviceFeeDrops: 100_000,            // 0.1 XRP service fee
    awaitValidated: false
)
// Nội bộ:
// TX 1: gửi 5 XRP cho rRecipient (sequence = N)
// TX 2: gửi 0.1 XRP cho rServiceAddr (sequence = N+1, fire-and-forget)
```

### 8.5 Reliable Submission (đợi confirmed)

```swift
let result = try await rippleManager.sendXrp(
    toAddress: "rRecipient...",
    amountDrops: 5_000_000,
    feeDrops: 0,
    destinationTag: nil,
    memoText: nil,
    serviceAddress: nil,
    serviceFeeDrops: 0,
    awaitValidated: true    // Poll cho đến khi TX confirmed hoặc expired
)
// result.success = true khi TX đã validated trên ledger
// Timeout: ~80 giây (20 lần poll * 4 giây/lần)
```

---

## 9. Swift Concurrency

Tất cả API blockchain là `async`. Gọi từ SwiftUI hoặc UIKit:

### SwiftUI

```swift
struct XRPWalletView: View {
    @State private var balance: Double = 0
    @State private var address: String = ""

    var body: some View {
        VStack {
            Text("Address: \(address)")
            Text("XRP: \(balance)")
        }
        .task {
            address = rippleManager.getAddress()
            balance = (try? await rippleManager.getBalance()) ?? 0
        }
    }
}
```

### UIKit

```swift
class XRPViewController: UIViewController {
    override func viewDidLoad() {
        super.viewDidLoad()
        Task {
            let balance = try? await rippleManager.getBalance()
            // Update UI — Task trên MainActor mặc định từ ViewController
            balanceLabel.text = "\(balance ?? 0) XRP"
        }
    }
}
```

---

## 10. Quy tắc giao dịch (XRP Ledger Protocol)

| Quy tắc | Giá trị |
|---|---|
| Account reserve | 10 XRP (bắt buộc giữ để kích hoạt account) |
| Minimum amount | 1 XRP (`ACTCoin.ripple.minimumAmount()`) |
| Default fee | 0.000012 XRP (12 drops) |
| Destination Tag range | 0 — 4,294,967,295 (UInt32) |
| TX finality | ~3-5 giây |
| LastLedgerSequence offset | +75 ledger (~5 phút timeout) |

---

## 11. Network & Explorer

| Mục | Mainnet | Testnet |
|---|---|---|
| RPC Endpoint | `s1.ripple.com:51234` | `s.altnet.rippletest.net:51234` |
| Explorer | [bithomp.com](https://bithomp.com) | [test.bithomp.com](https://test.bithomp.com) |
| Xem TX | `bithomp.com/explorer/{txHash}` | `test.bithomp.com/explorer/{txHash}` |

Chuyển đổi network:
```swift
Config.shared.setNetwork(network: .testnet)
// Tất cả RPC call tự động đổi sang testnet endpoint
```

---

## 12. Lỗi thường gặp

| Lỗi | Nguyên nhân | Cách fix |
|---|---|---|
| `Missing Framework` | Chưa embed XCFramework | Add vào Frameworks & chọn Embed & Sign |
| `Architecture Mismatch` | Simulator M1/M2 | Đảm bảo framework hỗ trợ `iosArm64Sim` |
| Balance = 0 dù đã gửi XRP | Account chưa kích hoạt (< 10 XRP) | Gửi >= 10 XRP lần đầu để activate |
| `tecUNFUNDED_PAYMENT` | Không đủ số dư (balance - reserve < amount + fee) | Kiểm tra balance khả dụng trước khi gửi |
| `tecNO_DST` | Địa chỉ đích chưa tồn tại trên ledger | Gửi >= 10 XRP để kích hoạt account đích |
| `tecNO_DST_INSUF_XRP` | Gửi < 10 XRP đến account mới | Gửi >= 10 XRP |
| `tefPAST_SEQ` | Sequence number đã dùng | Retry — sequence sẽ tự refresh |
| `tefMAX_LEDGER` | TX hết hạn (LastLedgerSequence đã qua) | Tạo và gửi lại TX mới |
| Mất tiền khi gửi sàn | Thiếu Destination Tag | Luôn hỏi user nhập tag khi gửi đến sàn |
