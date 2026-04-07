# Hướng dẫn tích hợp cho iOS (Swift Integration)

Thư viện `crypto-wallet-lib` được build dưới dạng **XCFramework** từ Kotlin Multiplatform. Tài liệu này hướng dẫn **từ đầu** cách cài đặt, cấu hình, và sử dụng trên iOS.

---

## 1. Cài đặt XCFramework

### Cách 1: Swift Package Manager (SPM) — Khuyến nghị

1. Trong Xcode: **File → Add Package Dependencies...**
2. Nhập URL: `https://github.com/innfocus/cryptowallet`
3. Chọn version rule (ví dụ: **Up to Next Major Version** từ `1.2.5`)
4. Xcode sẽ tự tải `crypto_wallet_lib.xcframework`

### Cách 2: Thêm thủ công

1. Tải file `crypto_wallet_lib.xcframework.zip` từ [GitHub Releases](https://github.com/innfocus/cryptowallet/releases)
2. Giải nén và kéo `crypto_wallet_lib.xcframework` vào project
3. Vào **Target → General → Frameworks, Libraries, and Embedded Content**
4. Chọn `crypto_wallet_lib.xcframework` → **Embed & Sign**

---

## 2. Import và xử lý xung đột tên (Config Conflict)

### ⚠️ Vấn đề quan trọng: `Config` bị xung đột

Khi import `crypto_wallet_lib`, class `Config` của thư viện **có thể xung đột** với các type `Config` khác trong iOS (ví dụ từ các framework khác hoặc type tự định nghĩa). Lỗi thường gặp:

```
Type 'Config' has no member 'shared'
```

### ✅ Giải pháp: Dùng module-qualified name

**Luôn luôn** sử dụng tên đầy đủ kèm module cho `Config`:

```swift
import crypto_wallet_lib

// ❌ SAI — có thể xung đột với Config type khác
// Config.shared.setNetwork(network: .mainnet)

// ✅ ĐÚNG — dùng module-qualified name
crypto_wallet_lib.Config.shared.setNetwork(network: .mainnet)
```

### Cách khác: Dùng typealias

Để code gọn hơn, bạn có thể tạo typealias ngay đầu file hoặc trong một file riêng:

```swift
import crypto_wallet_lib

// Tạo typealias để tránh xung đột
typealias WalletConfig = crypto_wallet_lib.Config
typealias WalletNetwork = crypto_wallet_lib.Network
typealias WalletNetworkName = crypto_wallet_lib.NetworkName

// Dùng typealias — ngắn gọn và rõ ràng
WalletConfig.shared.setNetwork(network: .mainnet)
```

> **Lưu ý:** Các class khác như `CommonCoinsManager`, `NetworkName`, `BalanceResult`... thường không bị xung đột, nhưng nếu gặp lỗi tương tự, hãy áp dụng cùng cách: `crypto_wallet_lib.ClassName`.

---

## 3. Cấu hình ban đầu (Initial Setup)

### 3.1. Thiết lập Network (Mainnet/Testnet)

Gọi trước khi khởi tạo `CommonCoinsManager`. Thường đặt trong `AppDelegate` hoặc `@main App`:

```swift
import crypto_wallet_lib

// Trong AppDelegate.swift
func application(_ application: UIApplication,
                 didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {

    // Bước 1: Chọn network
    crypto_wallet_lib.Config.shared.setNetwork(network: .mainnet)
    // Hoặc: crypto_wallet_lib.Config.shared.setNetwork(network: .testnet)

    // Bước 2: Cấu hình API keys (xem mục 3.2)
    setupApiKeys()

    // Bước 3: Khởi tạo CommonCoinsManager (xem mục 4)
    // ...

    return true
}
```

Với **SwiftUI App**:

```swift
import SwiftUI
import crypto_wallet_lib

@main
struct MyWalletApp: App {
    init() {
        // Bước 1: Chọn network
        crypto_wallet_lib.Config.shared.setNetwork(network: .mainnet)

        // Bước 2: Cấu hình API keys
        setupApiKeys()

        // Bước 3: Khởi tạo CommonCoinsManager
        let mnemonic = KeychainHelper.getMnemonic() // lấy từ Keychain
        CommonCoinsManager.companion.initialize(mnemonic: mnemonic, configs: [:])
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
```

### 3.2. Cấu hình API Keys

Mỗi blockchain cần API key riêng. Bảng dưới đây liệt kê các key cần thiết:

| Property | Blockchain | Bắt buộc? | Đăng ký tại |
|----------|-----------|-----------|-------------|
| `apiKeyInfura` | Ethereum, Arbitrum | ✅ Có | [infura.io](https://infura.io) |
| `apiKeyExplorer` | Ethereum (Etherscan), Arbitrum (Arbiscan) | ✅ Có | [etherscan.io/apis](https://etherscan.io/apis) |
| `apiKeyOwlRacle` | Ethereum, Arbitrum (gas estimation) | Khuyến nghị | [owlracle.info](https://owlracle.info) |
| `apiKeyToncenter` | TON | ✅ Có | [toncenter.com](https://toncenter.com) |

> **Cardano (ADA):** Sử dụng Blockfrost (url mặc định được cấu hình sẵn qua `ChainConfig`). Nếu cần custom, truyền qua `configs` parameter khi khởi tạo `CommonCoinsManager`.
> 
> **Bitcoin, XRP, Centrality:** Không cần API key — dùng public endpoint mặc định.

```swift
func setupApiKeys() {
    let config = crypto_wallet_lib.Config.shared

    // Ethereum & Arbitrum
    config.apiKeyInfura = "YOUR_INFURA_PROJECT_ID"
    config.apiKeyExplorer = "YOUR_ETHERSCAN_API_KEY"   // dùng chung cho Etherscan + Arbiscan
    config.apiKeyOwlRacle = "YOUR_OWLRACLE_API_KEY"     // gas estimation

    // TON
    config.apiKeyToncenter = "YOUR_TONCENTER_API_KEY"
}
```

### 3.3. Cấu hình nâng cao với ChainConfig (tuỳ chọn)

Nếu muốn custom API endpoint cho từng chain (ví dụ: dùng Blockfrost riêng cho Cardano):

```swift
import crypto_wallet_lib

// Tạo ChainConfig cho Cardano với Blockfrost API key
let cardanoConfig = ChainConfig(
    apiBaseUrl: "https://cardano-mainnet.blockfrost.io/api/v0",
    apiKey: "YOUR_BLOCKFROST_PROJECT_ID",
    isTestnet: false,
    fallbackApiBaseUrl: "https://api.koios.rest/api/v1"  // Koios fallback (free, no key)
)

// Truyền configs khi khởi tạo CommonCoinsManager
let configs: [NetworkName: ChainConfig] = [
    .cardano: cardanoConfig
]

CommonCoinsManager.companion.initialize(mnemonic: mnemonic, configs: configs)
```

---

## 4. Khởi tạo CommonCoinsManager

`CommonCoinsManager` hỗ trợ 2 cách sử dụng:

### Cách 1: Singleton (Khuyến nghị cho app thực tế)

```swift
import crypto_wallet_lib

// Khởi tạo 1 lần duy nhất (AppDelegate / @main App init)
CommonCoinsManager.companion.initialize(mnemonic: mnemonic, configs: [:])

// Sử dụng ở bất kỳ đâu
let manager = CommonCoinsManager.companion.shared

let btcAddress = manager.getAddress(coin: .btc)
let ethAddress = manager.getAddress(coin: .ethereum)
```

**Kiểm tra trạng thái:**
```swift
if CommonCoinsManager.companion.isInitialized {
    let manager = CommonCoinsManager.companion.shared
    // ...
}
```

**Reset khi đổi ví (mnemonic mới):**
```swift
CommonCoinsManager.companion.reset()
CommonCoinsManager.companion.initialize(mnemonic: newMnemonic, configs: [:])
```

### Cách 2: Instance trực tiếp

```swift
// Tạo instance mới — không dùng singleton
let manager = CommonCoinsManager(mnemonic: mnemonic, configs: [:])
```

> **Khi nào dùng cách nào?**
> - **Singleton** (`companion.initialize`): App chỉ quản lý 1 ví tại 1 thời điểm. Truy cập từ nhiều màn hình.
> - **Instance trực tiếp**: Cần quản lý nhiều ví cùng lúc, hoặc dùng trong test.

---

## 5. Sử dụng cơ bản

### 5.1. Lấy địa chỉ (Synchronous)

```swift
let manager = CommonCoinsManager.companion.shared

let btcAddress  = manager.getAddress(coin: .btc)
let ethAddress  = manager.getAddress(coin: .ethereum)
let tonAddress  = manager.getAddress(coin: .ton)
let adaAddress  = manager.getAddress(coin: .cardano)
let xrpAddress  = manager.getAddress(coin: .xrp)
```

### 5.2. Lấy số dư (Async)

Kotlin `suspend fun` tự động thành `async` trong Swift:

```swift
func fetchBalances() async {
    let manager = CommonCoinsManager.companion.shared

    // Bitcoin
    let btcResult = try? await manager.getBalance(coin: .btc, address: nil)
    if let result = btcResult, result.success {
        print("BTC: \(result.balance)")  // đơn vị BTC
    }

    // Ethereum
    let ethResult = try? await manager.getBalance(coin: .ethereum, address: nil)
    if let result = ethResult, result.success {
        print("ETH: \(result.balance)")  // đơn vị ETH
    }

    // TON
    let tonResult = try? await manager.getBalance(coin: .ton, address: nil)
    if let result = tonResult, result.success {
        print("TON: \(result.balance)")  // đơn vị TON
    }
}
```

### 5.3. Gửi coin

```swift
// Gửi BTC
let sendResult = try await manager.sendBtc(
    toAddress: "bc1q...",
    amountSatoshi: 50000,       // 0.0005 BTC
    memoStr: nil
)

if sendResult.success {
    print("TX Hash: \(sendResult.txHash)")
}

// Gửi ETH
let ethResult = try await manager.sendEth(
    toAddress: "0x...",
    amountEth: 0.01,
    coin: .ethereum              // hoặc .arbitrum cho Arbitrum
)

// Gửi TON
let tonResult = try await manager.sendCoin(
    coin: .ton,
    toAddress: "UQ...",
    amount: 1.5,
    networkFee: 0.0,
    serviceFee: 0.0,
    serviceAddress: nil,
    memo: nil
)

// Gửi ADA
let adaResult = try await manager.sendCardano(
    toAddress: "addr1q...",
    amountLovelace: 2_000_000,   // 2 ADA
    fee: 200_000,                // ~0.2 ADA fee
    serviceAddress: nil,
    serviceFeeLovelace: 0
)

// Gửi XRP
let xrpResult = try await manager.sendXrp(
    toAddress: "r...",
    amountXrp: 10.0,
    feeXrp: 0.000012,
    destinationTag: nil           // KotlinLong nếu cần tag
)
```

### 5.4. Lịch sử giao dịch (Paginated)

```swift
let historyResult = try await manager.getTransactionHistoryPaginated(
    coin: .btc,
    address: nil,
    limit: 20,
    pageParam: nil               // nil cho trang đầu
)

if historyResult.success {
    // historyResult.transactions chứa danh sách giao dịch
    // historyResult.hasMore cho biết còn trang tiếp
    // historyResult.nextPageParam dùng cho lần gọi tiếp
}

// Trang tiếp theo
if historyResult.hasMore {
    let page2 = try await manager.getTransactionHistoryPaginated(
        coin: .btc,
        address: nil,
        limit: 20,
        pageParam: historyResult.nextPageParam
    )
}
```

### 5.5. Ước tính phí

```swift
let feeResult = try await manager.estimateFee(
    coin: .ethereum,
    amount: 0.1,
    fromAddress: nil,
    toAddress: "0x...",
    serviceAddress: nil,
    serviceFee: 0.0
)

if feeResult.success {
    print("Fee: \(feeResult.fee) \(feeResult.unit)")
    // gasLimit, gasPrice có sẵn cho EVM chains
}
```

---

## 6. Token & NFT

### 6.1. Kiểm tra chain hỗ trợ

```swift
let manager = CommonCoinsManager.companion.shared

manager.supportsTokens(coin: .ethereum)  // true (ERC-20)
manager.supportsTokens(coin: .ton)       // true (Jetton)
manager.supportsTokens(coin: .cardano)   // true (Native Token)
manager.supportsNFTs(coin: .ton)         // true
manager.supportsStaking(coin: .cardano)  // true
manager.supportsStaking(coin: .ton)      // true
```

### 6.2. Token balance

```swift
// ERC-20 token
let tokenResult = try await manager.getTokenBalance(
    coin: .ethereum,
    address: ethAddress,
    contractAddress: "0xdAC17F958D2ee523a2206206994597C13D831ec7" // USDT
)

// TON Jetton
let jettonResult = try await manager.getTokenBalance(
    coin: .ton,
    address: tonAddress,
    contractAddress: "EQBynBO23ywHy_CgarY9NK9FTz0yDsG82PtcbSTQgGoXwiuA" // jUSDT
)
```

### 6.3. NFT

```swift
// Lấy danh sách NFT
let nfts = try await manager.getNFTs(coin: .ton, address: tonAddress)

// Transfer NFT
let nftResult = try await manager.transferNFT(
    coin: .ton,
    nftAddress: "EQ...",
    toAddress: "UQ...",
    memo: "Gift"
)
```

---

## 7. Staking

```swift
// TON staking
let stakeResult = try await manager.stake(
    coin: .ton,
    amount: 10_000_000_000,   // 10 TON (nanoTON)
    poolAddress: "EQ..."
)

// Cardano staking
let adaStakeResult = try await manager.stake(
    coin: .cardano,
    amount: 5_000_000,         // 5 ADA (lovelace)
    poolAddress: "pool1..."
)

// Xem rewards
let rewards = try await manager.getStakingRewards(
    coin: .cardano,
    address: nil
)
```

---

## 8. Xử lý kiểu dữ liệu Kotlin ↔ Swift

| Kotlin | Swift | Ghi chú |
|--------|-------|---------|
| `String` | `String` | Trực tiếp |
| `Int` | `Int32` | ⚠️ Kotlin Int = 32-bit |
| `Long` | `Int64` | Dùng `KotlinLong` nếu cần wrap |
| `Double` | `Double` | Trực tiếp |
| `Boolean` | `Bool` / `KotlinBoolean` | |
| `List<T>` | `[T]` (Array) | Tự động convert |
| `Map<K,V>` | `[K:V]` (Dictionary) | Tự động convert |
| `suspend fun` | `async throws` | Tự động bởi KMP |
| `enum class` | Swift enum | Truy cập qua `.btc`, `.ethereum`... |
| `data class` | Swift class | Property access bình thường |
| `companion object` | `.companion` | Ví dụ: `CommonCoinsManager.companion.shared` |
| `null` | `nil` | Kotlin nullable `?` → Swift Optional |

### Lưu ý quan trọng về Int/Long

```swift
// ⚠️ amount > 9.2 * 10^18 sẽ overflow Int64
// Đối với amount lớn (ETH wei, ADA lovelace), kiểm tra giới hạn:
let maxSafeAmount: Int64 = Int64.max  // 9,223,372,036,854,775,807

// Nếu cần truyền Long parameter optional:
let tag = KotlinLong(value: 12345)  // cho destinationTag
```

---

## 9. Sử dụng trong SwiftUI

```swift
import SwiftUI
import crypto_wallet_lib

struct WalletView: View {
    @State private var btcBalance: String = "Loading..."
    @State private var btcAddress: String = ""

    var body: some View {
        VStack(spacing: 16) {
            Text("BTC Address: \(btcAddress)")
                .font(.caption)
            Text("Balance: \(btcBalance)")
                .font(.title2)
        }
        .task {
            await loadWallet()
        }
    }

    func loadWallet() async {
        let manager = CommonCoinsManager.companion.shared
        btcAddress = manager.getAddress(coin: .btc)

        if let result = try? await manager.getBalance(coin: .btc, address: nil),
           result.success {
            btcBalance = "\(result.balance) BTC"
        } else {
            btcBalance = "Error"
        }
    }
}
```

---

## 10. Sử dụng trong UIKit

```swift
import UIKit
import crypto_wallet_lib

class WalletViewController: UIViewController {

    override func viewDidLoad() {
        super.viewDidLoad()

        Task {
            await fetchBalance()
        }
    }

    func fetchBalance() async {
        let manager = CommonCoinsManager.companion.shared
        let address = manager.getAddress(coin: .ethereum)

        do {
            let result = try await manager.getBalance(coin: .ethereum, address: nil)
            await MainActor.run {
                // Update UI
                self.balanceLabel.text = "\(result.balance) ETH"
            }
        } catch {
            print("Error: \(error)")
        }
    }
}
```

---

## 11. Ví dụ hoàn chỉnh: Setup từ đầu

Dưới đây là ví dụ đầy đủ cho `AppDelegate.swift`:

```swift
import UIKit
import crypto_wallet_lib

// Typealias để tránh xung đột
typealias WalletConfig = crypto_wallet_lib.Config

@main
class AppDelegate: UIResponder, UIApplicationDelegate {

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {

        setupCryptoWallet()
        return true
    }

    private func setupCryptoWallet() {
        // ═══════════════════════════════════════════
        // BƯỚC 1: Chọn network
        // ═══════════════════════════════════════════
        WalletConfig.shared.setNetwork(network: .mainnet)

        // ═══════════════════════════════════════════
        // BƯỚC 2: Cấu hình API keys
        // ═══════════════════════════════════════════
        let config = WalletConfig.shared

        // Ethereum & Arbitrum
        config.apiKeyInfura   = "YOUR_INFURA_PROJECT_ID"
        config.apiKeyExplorer = "YOUR_ETHERSCAN_API_KEY"
        config.apiKeyOwlRacle = "YOUR_OWLRACLE_API_KEY"

        // TON
        config.apiKeyToncenter = "YOUR_TONCENTER_API_KEY"

        // ═══════════════════════════════════════════
        // BƯỚC 3: (Tuỳ chọn) Custom config cho chain cụ thể
        // ═══════════════════════════════════════════
        let cardanoConfig = ChainConfig(
            apiBaseUrl: "https://cardano-mainnet.blockfrost.io/api/v0",
            apiKey: "YOUR_BLOCKFROST_PROJECT_ID",
            isTestnet: false,
            fallbackApiBaseUrl: "https://api.koios.rest/api/v1"
        )

        let configs: [NetworkName: ChainConfig] = [
            .cardano: cardanoConfig
        ]

        // ═══════════════════════════════════════════
        // BƯỚC 4: Khởi tạo CommonCoinsManager
        // ═══════════════════════════════════════════
        let mnemonic = KeychainHelper.getMnemonic() ?? ""
        CommonCoinsManager.companion.initialize(mnemonic: mnemonic, configs: configs)

        // ═══════════════════════════════════════════
        // BƯỚC 5: Verify — lấy thử địa chỉ
        // ═══════════════════════════════════════════
        if CommonCoinsManager.companion.isInitialized {
            let manager = CommonCoinsManager.companion.shared
            print("BTC: \(manager.getAddress(coin: .btc))")
            print("ETH: \(manager.getAddress(coin: .ethereum))")
            print("TON: \(manager.getAddress(coin: .ton))")
            print("ADA: \(manager.getAddress(coin: .cardano))")
        }
    }
}
```

---

## 12. Troubleshooting

### `Type 'Config' has no member 'shared'`
**Nguyên nhân:** `Config` bị xung đột với type khác trong project.  
**Fix:** Dùng `crypto_wallet_lib.Config.shared` hoặc tạo typealias (xem mục 2).

### `Missing Framework` hoặc `No such module 'crypto_wallet_lib'`
**Fix:**
1. Kiểm tra SPM đã resolve thành công (File → Packages → Resolve)
2. Hoặc kiểm tra xcframework đã add vào "Frameworks, Libraries, and Embedded Content" với **Embed & Sign**
3. Clean Build Folder: `Cmd + Shift + K`

### `Architecture Mismatch` trên Simulator M1/M2
**Fix:** Đảm bảo xcframework chứa slice `ios-arm64_x86_64-simulator`. Nếu thiếu, build lại xcframework:
```bash
./gradlew :crypto-wallet-lib:assembleXCFramework
```

### Kotlin `suspend fun` không thấy `async` trong Swift
**Fix:** Đảm bảo dùng Swift 5.5+ và Xcode 13+. KMP auto-generate async bridge cho suspend functions.

### `KotlinException` khi gọi `CommonCoinsManager.companion.shared`
**Nguyên nhân:** Chưa gọi `initialize()` trước.  
**Fix:** Luôn gọi `CommonCoinsManager.companion.initialize(mnemonic:configs:)` trước khi truy cập `.shared`.

### Lỗi network hoặc timeout
**Kiểm tra:**
1. API keys đã set đúng chưa?
2. Network đã set đúng (mainnet/testnet)?
3. App có internet permission? (iOS mặc định có, nhưng kiểm tra ATS settings nếu dùng HTTP)

---

## 13. Tham khảo thêm

- [Tích hợp Bitcoin (iOS)](bitcoin-integration.md)
- [Tích hợp Ethereum & Arbitrum (iOS)](ethereum-integration.md)
- [Tích hợp TON (iOS)](ton-integration.md)
- [Tích hợp Cardano (iOS)](cardano-integration.md)
- [Tích hợp Ripple/XRP (iOS)](ripple-integration.md)
- [Tích hợp Centrality (iOS)](centrality-integration.md)
- [API Reference: CommonCoinsManager](../api/common-coins-manager.md)
