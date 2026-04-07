# Contributing Guide: CryptoWallet

Hướng dẫn đóng góp code cho dự án CryptoWallet KMP library. Tài liệu này đảm bảo mọi thành viên team tuân theo cùng tiêu chuẩn chất lượng.

> **Tài liệu liên quan:**
> - [Developer Guide](DEVELOPER_GUIDE.md) — Setup, build, test commands
> - [Architecture Overview](architecture/overview.md) — Kiến trúc 5 tầng
> - [CommonCoinsManager API](api/common-coins-manager.md) — Design patterns (Section 11)

---

## 1. Kiến trúc tổng quan

```
┌──────────────────────────────────────────────────────────────┐
│  Layer 5: Platform Bridge                                     │
│  CoinsManager (Android), XCFramework (iOS)                    │
├──────────────────────────────────────────────────────────────┤
│  Layer 4: CommonCoinsManager (Facade)                         │
│  Unified API — dispatches to chain managers                   │
├──────────────────────────────────────────────────────────────┤
│  Layer 3: Chain Managers (TonManager, BitcoinManager, ...)    │
│  Implements interfaces: IWalletManager, ITokenManager, ...    │
├──────────────────────────────────────────────────────────────┤
│  Layer 2: Service Layer (TonApiService, InfuraRpcService, ...)│
│  Ktor HTTP clients — API calls, response parsing              │
├──────────────────────────────────────────────────────────────┤
│  Layer 1: Config & Models                                     │
│  Config singleton, CoinNetwork, data models                   │
└──────────────────────────────────────────────────────────────┘
```

**Nguyên tắc:** Code chảy từ trên xuống. Layer trên gọi layer dưới, không bao giờ ngược lại.

---

## 2. Interface Hierarchy (BẮT BUỘC)

Mỗi chain manager **phải** implement đúng bộ interfaces tương ứng với feature set:

| Interface | Methods | Khi nào cần |
|-----------|---------|-------------|
| `BaseCoinManager` / `IWalletManager` | `getAddress`, `getBalance`, `getTransactionHistory`, `transfer`, `getChainId` | **Luôn luôn** |
| `ITokenManager` | `getTokenBalance`, `getTokenTransactionHistory`, `transferToken` | Chain hỗ trợ token (ERC-20, Jetton, Native Token) |
| `ITokenAndNFT` | `getBalanceToken`, `getTransactionHistoryToken`, `TransferToken`, `getNFT` | Legacy interface — implement **cùng với** ITokenManager |
| `INFTManager` | `getNFTs`, `transferNFT` | Chain hỗ trợ NFT |
| `IStakingManager` | `stake`, `unstake`, `getStakingRewards`, `getStakingBalance` | Chain hỗ trợ staking |
| `IFeeEstimator` | `estimateFee` | Chain hỗ trợ dynamic fee |
| `IBridgeManager` | `bridgeAsset`, `getBridgeStatus` | Chain hỗ trợ bridge |

### Ví dụ khai báo đúng:
```kotlin
// TON: hỗ trợ token + NFT + staking
class TonManager(...) : BaseCoinManager(), ITokenAndNFT, ITokenManager, IStakingManager, INFTManager

// Ethereum: hỗ trợ token + NFT + fee estimation
class EthereumManager(...) : BaseCoinManager(), ITokenManager, INFTManager, IFeeEstimator, ITokenAndNFT

// Bitcoin: chỉ coin cơ bản
class BitcoinManager(...) : BaseCoinManager()
```

> **Sai lầm thường gặp:** Implement `ITokenAndNFT` nhưng quên `ITokenManager`. `CommonCoinsManager` cast sang `ITokenManager`, không phải `ITokenAndNFT` — sẽ gây `ClassCastException` at runtime.

---

## 3. CommonCoinsManager Routing Rules

Khi chain manager có logic riêng, **phải** thêm `when` branch vào các method tương ứng trong `CommonCoinsManager`:

| Method | Cần routing khi |
|--------|----------------|
| `sendCoin()` | Chain có flow signing riêng (seqno, UTXO selection, ...) |
| `sendCoinExact()` | Chain dùng smallest unit khác |
| `getTransactionHistoryPaginated()` | Chain hỗ trợ pagination |
| `getTokenTransactionHistoryPaginated()` | Chain hỗ trợ token tx pagination |
| `estimateFee()` | Chain có fee estimation logic riêng |

### Smallest Unit Convention

| Chain | Unit | Factor | Ví dụ |
|-------|------|--------|-------|
| Bitcoin | satoshi | 100,000,000 | 1 BTC = 100M sat |
| Ethereum | wei | 1e18 | 1 ETH = 1e18 wei |
| TON | nanoTON | 1,000,000,000 | 1 TON = 1e9 nano |
| Cardano | lovelace | 1,000,000 | 1 ADA = 1M lovelace |
| Ripple | drops | 1,000,000 | 1 XRP = 1M drops |
| Centrality | unit | 10,000 | 1 CENNZ = 10K unit |

### Capability Registration

Khi chain hỗ trợ feature mới, cập nhật các hàm check:
```kotlin
fun supportsTokens(coin)    // → setOf(ETHEREUM, ARBITRUM, CARDANO, TON)
fun supportsNFTs(coin)      // → setOf(ETHEREUM, ARBITRUM, TON)
fun supportsStaking(coin)   // → setOf(CARDANO, TON)
fun supportsBridge(from,to) // → Cardano↔Midnight, Ethereum↔Arbitrum
```

---

## 4. Result Wrapper Pattern (BẮT BUỘC)

**Mọi** public method trong `CommonCoinsManager` phải:

```kotlin
suspend fun someOperation(...): SomeResult {
    return try {
        // Business logic
        SomeResult(data = ..., success = true)
    } catch (e: Exception) {
        logger.e(e) { "Failed to ..." }
        SomeResult(success = false, error = e.message)
    }
}
```

**Quy tắc:**
- Return result wrapper (`BalanceResult`, `SendResult`, `TransactionHistoryResult`)
- **KHÔNG throw exception** ra khỏi CommonCoinsManager
- Log error trước khi return
- Chain managers bên trong **có thể** throw (sẽ được catch ở facade)

---

## 5. Config & Network Rules

### Config Singleton
```kotlin
Config.shared.setNetwork(Network.MAINNET)
Config.shared.apiKeyToncenter = "..." // Optional API keys
```

- Chain managers đọc `Config.shared` **at runtime**, không nhận network ở constructor
- `CoinNetwork(name)` chỉ wrap `NetworkName`, không mang state
- Switching network **tự động** thay đổi tất cả endpoints

### Coroutine Convention
- `CommonCoinsManager` methods: `suspend fun`
- Android bridge: `scope.launch { ... withContext(Dispatchers.Main) { callback } }`
- **KHÔNG** dùng `GlobalScope`
- `CoinsManager` là `CoroutineScope` (`SupervisorJob + Dispatchers.IO`)

---

## 6. Dependency Management

- **Tất cả** versions trong `gradle/libs.versions.toml`
- **KHÔNG** hardcode version trong `build.gradle.kts`
- Dùng `version.ref` trong `[libraries]`
- Tên: `camelCase` cho versions, `kebab-case` cho library aliases

---

## 7. Testing Standards

### Build & Verify
```bash
# Primary: compile check (nhanh nhất)
./gradlew :crypto-wallet-lib:compileAndroidMain

# Full Android build
./gradlew :crypto-wallet-lib:assembleAndroidMain
```

> **KHÔNG chạy:** `jvmTest`, `allTests`, `build` — JVM/iOS missing actual declarations.

### Test Guidelines
- Mock HTTP calls với Ktor `MockEngine` — **KHÔNG** gọi live API trong unit test
- TON tests dùng 24-word mnemonic (TON format, không phải BIP-39)
- Mỗi chain manager mới **phải** có test cho address derivation
- Integration test cho API calls dùng `MockEngine`

### Error Path Testing
- Test case khi API trả về error
- Test case khi network timeout
- Test case khi dữ liệu không hợp lệ (malformed JSON, empty response)

---

## 8. Documentation Standards

### Khi thêm chain mới, phải tạo:

| Tài liệu | Đường dẫn | Nội dung |
|-----------|-----------|----------|
| Chain spec | `docs/chains/{chain}.md` | Kỹ thuật chi tiết: address, signing, API, standards |
| Android guide | `docs/android/{chain}-integration.md` | Hướng dẫn tích hợp Android với code examples |
| iOS guide | `docs/ios/{chain}-integration.md` | Hướng dẫn tích hợp iOS với Swift code |
| API update | `docs/api/common-coins-manager.md` | Cập nhật bảng pagination, capability, TON-specific methods |
| README update | `docs/README.md` | Thêm link vào Documentation Map |

### Naming Convention (từ `docs/README.md`)
- **Thư mục:** chữ thường (`android`, `ios`, `chains`)
- **File:** kebab-case (`ton-integration.md`, `cardano-byron.md`)
- **Ngoại lệ:** Entry-point files dùng CHỮ HOA (`README.md`, `DEVELOPER_GUIDE.md`)

---

## 9. Spec-First Development (Kiro Specs)

Mỗi feature lớn **nên** có spec trong `.kiro/specs/{feature-name}/`:

```
.kiro/specs/{feature-name}/
├── requirements.md   # Yêu cầu chức năng, acceptance criteria
├── design.md         # Thiết kế kỹ thuật, API changes, data models
└── tasks.md          # Danh sách task với checkbox [ ] / [x]
```

### Khi nào cần spec:
- Thêm chain mới
- Thêm feature lớn (staking, bridge, NFT support)
- Refactor ảnh hưởng nhiều file (migration)
- Audit & fix (ví dụ: `ton-audit-fixes`)

### Khi nào KHÔNG cần spec:
- Bug fix nhỏ (1-2 file)
- Update dependency version
- Documentation-only changes

---

## 10. Security Guidelines

### API Keys
- **KHÔNG** commit API keys vào source code
- Dùng `Config.shared.apiKeyXxx` — set ở app layer, không hardcode trong library
- Kiểm tra `.gitignore` cho `.env`, `credentials.json`, `local.properties`

### Transaction Signing
- `validUntil` phải có expiry (TON: now + 60s) — tránh signed message bị tái sử dụng
- Kiểm tra `seqno` trước khi sign — return error nếu API fail, không dùng default 0
- Bounceable address chỉ dùng cho smart contract, non-bounceable cho user wallet

### Input Validation
- Validate address format trước khi gửi transaction
- Validate amount > 0 và không vượt quá balance
- Validate contract address khi gọi token operations

---

## 11. Git Workflow

### Branch Naming
```
feature/{chain}-{feature}   # feature/ton-jetton-burn
fix/{chain}-{issue}          # fix/ton-seqno-error
docs/{topic}                 # docs/ton-integration
refactor/{scope}             # refactor/staking-interface
```

### Commit Message
```
{type}: {short description}

{optional body}
```

Types: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`

### PR Requirements
- Build thành công (`compileAndroidMain`)
- Không có error mới (warnings OK nếu pre-existing)
- Cập nhật tài liệu nếu thay đổi API
- Review checklist (xem Section 12)
