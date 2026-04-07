# Cardano Integration cho iOS

Hướng dẫn tích hợp Cardano (ADA) vào ứng dụng iOS qua XCFramework, bao gồm Byron (legacy) và Shelley (hiện tại).

> **Tài liệu kỹ thuật chi tiết:** [Byron spec](../chains/cardano-byron.md) | [Shelley spec](../chains/cardano-shelley.md)

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

### 3.2 CardanoManager trực tiếp (advanced)

```swift
let cardanoManager = CardanoManager(mnemonicPhrase: "your mnemonic words ...")

// Key derivation được cache tự động — PBKDF2-4096 chỉ chạy 1 lần.
// Gọi clearCachedKeys() khi wallet bị lock:
// cardanoManager.clearCachedKeys()
```

---

## 4. Lấy địa chỉ ví

### 4.1 Shelley address (mặc định)

```swift
// Qua CommonCoinsManager
let address = manager.getAddress(coin: .cardano)
// Trả về: addr1q... (Bech32, base address)

// Qua CardanoManager
let address = cardanoManager.getAddress()              // = getShelleyAddress(0, 0)
let address2 = cardanoManager.getShelleyAddress(account: 0, index: 1)
```

### 4.2 Byron address (legacy)

```swift
let byronAddress = cardanoManager.getByronAddress(index: 0)
// Trả về: Ae2tdPwUPEZ... (Base58)
```

### 4.3 Staking address

```swift
let stakingAddress = cardanoManager.getStakingAddress(account: 0)
// Trả về: stake1u... (Bech32)
```

> **Lưu ý:** Shelley và Byron dùng chung Icarus master key (CIP-0003).
> - Shelley path: `m/1852'/1815'/account'/0/index` (CIP-1852)
> - Byron path: `m/44'/1815'/0'/0/index` (BIP-44)

---

## 5. Lấy số dư

Kotlin `suspend` functions tự động convert thành Swift `async` functions.

```swift
func fetchBalance() async {
    do {
        // Qua CommonCoinsManager — trả về ADA (Double)
        let balance = try await manager.getBalance(coin: .cardano, address: nil)
        print("ADA Balance: \(balance)")
    } catch {
        print("Error: \(error)")
    }
}

// Hoặc qua CardanoManager
func fetchBalanceDirect() async {
    do {
        let balance = try await cardanoManager.getBalance(address: nil, coinNetwork: coinNetwork)
        print("ADA Balance: \(balance)")
    } catch {
        print("Error: \(error)")
    }
}
```

> **Quy đổi:** 1 ADA = 1,000,000 lovelace. API trả về đã quy đổi sang ADA.

---

## 6. Lịch sử giao dịch

### 6.1 Không phân trang

```swift
func fetchTransactions() async {
    do {
        let history = try await manager.getTransactionHistory(coin: .cardano, address: nil)
        // history là Array — cast sang kiểu phù hợp
        print("Transactions: \(history ?? [])")
    } catch {
        print("Error: \(error)")
    }
}
```

### 6.2 Có phân trang (Pagination)

```swift
func fetchTransactionsPaginated() async {
    do {
        // Trang đầu tiên
        let result = try await manager.getTransactionHistoryPaginated(
            coin: .cardano,
            address: nil,
            limit: 20,
            pageParam: nil  // nil = trang đầu
        )
        
        if result.success {
            print("Transactions: \(result.transactions ?? [])")
            
            // Load trang tiếp theo nếu còn
            if result.hasMore, let nextParam = result.nextPageParam {
                let page2 = try await manager.getTransactionHistoryPaginated(
                    coin: .cardano,
                    address: nil,
                    limit: 20,
                    pageParam: nextParam  // {"page": 2}
                )
            }
        }
    } catch {
        print("Error: \(error)")
    }
}
```

> **Lưu ý:** `pageParam` là `{"page": Int}` (1-based). Mặc định sắp xếp mới nhất trước (`order=desc`).

---

## 7. Chuyển ADA

### 7.1 Shelley transaction (mặc định)

```swift
func sendADA() async {
    do {
        // Qua CommonCoinsManager — amount và fee đơn vị lovelace
        let result = try await manager.sendCardano(
            toAddress: "addr1q...",
            amountLovelace: 5_000_000,   // 5 ADA
            fee: 170_000                  // ~0.17 ADA
        )
        print("TX submitted: \(result)")
    } catch {
        print("Error: \(error)")
    }
}

// Hoặc qua CardanoManager (chi tiết hơn)
func sendADADirect() async {
    do {
        let signedTx = try await cardanoManager.buildAndSignTransaction(
            toAddress: "addr1q...",
            amount: 5_000_000,
            fee: 170_000
        )
        let result = try await cardanoManager.transfer(
            dataSigned: signedTx.cborHex,
            coinNetwork: coinNetwork
        )
        print("TX hash: \(result)")
    } catch {
        print("Error: \(error)")
    }
}
```

### 7.2 Byron transaction

```swift
func sendByron() async {
    do {
        let signedTx = try await cardanoManager.buildAndSignByronTransaction(
            toAddress: "Ae2tdPwUPEZ...",
            amount: 5_000_000,
            fee: 170_000,
            fromIndex: 0
        )
        let result = try await cardanoManager.transfer(
            dataSigned: signedTx.cborHex,
            coinNetwork: coinNetwork
        )
        print("TX hash: \(result)")
    } catch {
        print("Error: \(error)")
    }
}
```

### 7.3 Quy tắc transaction (IOHK protocol)

| Quy tắc | Giá trị |
|---|---|
| Minimum UTXO output | 1 ADA (1,000,000 lovelace) |
| Change < 1 ADA | Absorb vào fee |
| Transaction size limit | 16,384 bytes |
| Input deduplication | Tự động |

---

## 8. Native Token

### 8.1 Lấy balance token

```swift
let tokenBalance = try await cardanoManager.getTokenBalance(
    address: "addr1q...",
    policyId: "abcdef1234...",
    assetName: "4d79546f6b656e"  // hex-encoded asset name
)
```

### 8.2 Gửi token

```swift
let txHex = try await cardanoManager.sendToken(
    toAddress: "addr1q...",
    policyId: "abcdef1234...",
    assetName: "4d79546f6b656e",
    amount: 100,
    fee: 200_000
)
```

### 8.3 Lịch sử giao dịch token (Pagination)

```swift
func fetchTokenTransactions() async {
    do {
        // Qua CommonCoinsManager
        let result = try await manager.getTokenTransactionHistoryPaginated(
            coin: .cardano,
            policyId: "abcdef1234...",
            assetName: "4d79546f6b656e",
            limit: 20,
            pageParam: nil  // nil = trang đầu
        )
        
        if result.success {
            print("Token Txs: \(result.transactions ?? [])")
            
            // Load trang tiếp
            if result.hasMore, let nextParam = result.nextPageParam {
                let page2 = try await manager.getTokenTransactionHistoryPaginated(
                    coin: .cardano,
                    policyId: "abcdef1234...",
                    assetName: "4d79546f6b656e",
                    limit: 20,
                    pageParam: nextParam
                )
            }
        }
    } catch {
        print("Error: \(error)")
    }
}

// Qua CardanoManager trực tiếp
func fetchTokenTxsDirect() async {
    do {
        let (txs, hasMore) = try await cardanoManager.getTokenTransactionHistoryPaginated(
            policyId: "abcdef1234...",
            assetName: "4d79546f6b656e",
            count: 20,
            page: 1,
            order: "desc"
        )
    } catch {
        print("Error: \(error)")
    }
}
```

> **API:** Blockfrost `/assets/{policyId}{assetName}/transactions?count=N&page=P&order=desc`

---

## 9. Staking

```swift
// Delegate vào pool
let result = try await cardanoManager.stake(
    amount: 10_000_000,       // 10 ADA deposit
    poolAddress: "pool1...",
    coinNetwork: coinNetwork
)

// Lấy staking rewards
let rewards = try await cardanoManager.getStakingRewards(
    address: "stake1u...",
    coinNetwork: coinNetwork
)

// Undelegate
let result = try await cardanoManager.unstake(
    amount: 0,
    coinNetwork: coinNetwork
)
```

> **Lưu ý:** `stake()` và `unstake()` ký bằng cả payment key + staking key (2 witnesses).

---

## 10. Swift Concurrency

Tất cả API blockchain là `async`. Gọi từ SwiftUI hoặc UIKit:

### SwiftUI

```swift
struct WalletView: View {
    @State private var balance: Double = 0
    
    var body: some View {
        Text("ADA: \(balance)")
            .task {
                balance = (try? await manager.getBalance(coin: .cardano, address: nil)) ?? 0
            }
    }
}
```

### UIKit

```swift
class WalletViewController: UIViewController {
    override func viewDidLoad() {
        super.viewDidLoad()
        Task {
            let balance = try? await manager.getBalance(coin: .cardano, address: nil)
            // Update UI — Task trên MainActor mặc định từ ViewController
            balanceLabel.text = "\(balance ?? 0) ADA"
        }
    }
}
```

---

## 11. Lỗi thường gặp

| Lỗi | Nguyên nhân | Cách fix |
|---|---|---|
| `Missing Framework` | Chưa embed XCFramework | Add vào Frameworks & chọn Embed & Sign |
| `Architecture Mismatch` | Simulator M1/M2 | Đảm bảo framework hỗ trợ `iosArm64Sim` |
| Address prefix sai | Sai network config | Kiểm tra `Config.shared.setNetwork()` |
| Transaction reject | Amount < 1 ADA | Output >= 1,000,000 lovelace |
| `insufficient funds` | UTXO < amount + fee | Kiểm tra balance trước khi gửi |
