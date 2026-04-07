# TON Blockchain Integration

Tài liệu kỹ thuật cho TON (The Open Network) trong CryptoWallet library.

## 1. Tổng quan kiến trúc

```
commonMain/
├── wallets/ton/TonManager.kt       # Core: sign, broadcast, Jetton, NFT, DNS, Staking
├── services/TonApiService.kt       # Toncenter v2 JSON-RPC + v3 REST
├── services/TonService.kt          # Callback bridge cho Android (TokenService + NFTService)
└── models/ton/TonApiModel.kt       # Response models: Balance, Tx, Fee, NFT, Staking, Jetton
```

### Dependency
- `org.ton.kotlin:ton-kotlin:0.5.0` — Address, Cell/BOC, Contract, Signing
- Ktor Client — HTTP calls
- Kermit — Logging

---

## 2. Wallet Versions

| Version | Class | Mặc định | Ghi chú |
|---------|-------|----------|---------|
| **W4** (V4R2) | `WalletV4R2Contract` | Không | Legacy, address không đổi giữa mainnet/testnet |
| **W5** (V5R1) | Custom implementation | **Có** | Network-aware wallet_id, signature at tail |

### W5R1 wallet_id
```
context = 0x80000000  (workchain=0, subwallet=0)
networkGlobalId = -239 (mainnet) / -3 (testnet)
wallet_id = context XOR networkGlobalId
```

> **Lưu ý:** W5 address **khác nhau** giữa mainnet và testnet do wallet_id encode networkGlobalId.

---

## 3. Mnemonic & Key Derivation

### TON Native (24 từ)
```kotlin
val seed = Mnemonic(mnemonicList).toSeed()
val privateKey = PrivateKeyEd25519(seed.sliceArray(0 until 32))
```

### BIP39 (12 từ, tương thích Tonkeeper)
```kotlin
// SLIP-0010 ED25519, path: m/44'/607'/0'
val bip39Seed = MnemonicCode.toSeed(mnemonicList, "")
val privateKeyBytes = slip10DeriveEd25519(bip39Seed, intArrayOf(
    0x80000000.toInt() or 44,
    0x80000000.toInt() or 607,
    0x80000000.toInt() or 0
))
```

---

## 4. Address Format

| Loại | bounceable | Dùng khi |
|------|-----------|----------|
| Non-bounceable | `false` | Hiển thị cho user, nhận TON |
| Bounceable | `true` | Gửi đến smart contract (Jetton wallet, NFT, Pool) |
| testOnly | `true` nếu testnet | Tránh gửi nhầm mainnet ↔ testnet |

```kotlin
// User-friendly non-bounceable
address.toString(userFriendly = true, bounceable = false, testOnly = isTestnet)
```

---

## 5. Transaction Signing Flow

### TON Native Transfer
```
1. getSeqno(coinNetwork)          → lấy sequence number hiện tại
2. signTransaction(to, amount, seqno, memo)  → tạo BOC base64
3. transfer(boc, coinNetwork)     → broadcast qua sendBoc RPC
```

### Jetton (Token) Transfer — TEP-74
```
1. getSeqno(coinNetwork)
2. signJettonTransaction(masterAddr, to, amountNano, seqno, coinNetwork, memo)
   - Derives Jetton Wallet address via get_wallet_address on master
   - Builds body: opcode=0x0f8a7ea5, query_id, amount, recipient, response_dest
   - Forward TON: 0.01 TON, Total: 0.05 TON (configurable)
3. TransferToken(boc, coinNetwork)  → broadcast
```

### NFT Transfer — TEP-62
```
1. getSeqno(coinNetwork)
2. signNFTTransfer(nftAddr, to, seqno, memo)
   - Sends directly TO the NFT contract address
   - Body: opcode=0x5fcc3d14, query_id, new_owner, response_dest
   - Forward TON: 0.05 TON, Total: 0.1 TON (configurable)
3. transfer(boc, coinNetwork)  → broadcast
```

### Jetton Burn (Unstake) — TEP-74
```
1. getSeqno(coinNetwork)
2. signJettonBurn(masterAddr, amountNano, seqno, coinNetwork)
   - Sends to user's Jetton Wallet address
   - Body: opcode=0x595f07bc, query_id, amount, response_dest
   - Total: 0.05 TON
3. transfer(boc, coinNetwork)  → broadcast
```

### Transaction Expiry (validUntil)
- **seqno == 0** (deployment): `validUntil = 0xFFFFFFFF` (W5) hoặc `Int.MAX_VALUE` (W4)
- **seqno > 0** (normal): `validUntil = currentTime + 60s`
- Ngăn signed message bị tái sử dụng sau thời hạn

---

## 6. Jetton (Token) Support

### Balance
```kotlin
// decimals: lấy từ metadata hoặc truyền vào (USDT=6, USDC=6, Jetton mặc định=9)
val balance = tonManager.getBalanceToken(address, contractAddress, coinNetwork, decimals = 6)
```

> **Quan trọng:** Jetton mỗi loại có `decimals` khác nhau. USDT trên TON dùng 6 decimals. Luôn lấy decimals từ metadata trước khi hiển thị balance.

### Metadata
```kotlin
val metadata = tonManager.getJettonMetadata(contractAddress, coinNetwork)
// metadata.name, metadata.symbol, metadata.decimals, metadata.image
```

Hỗ trợ: Layout `0x01` (off-chain URL, bao gồm IPFS gateway). Layout `0x00` (on-chain dictionary) chưa hỗ trợ.

### Transaction History (Parsed)
```kotlin
val txs = tonManager.getJettonTransactionsParsed(
    address, contractAddress, coinNetwork,
    limit = 20,
    lt = lastTx?.transactionId?.lt,    // pagination cursor
    hash = lastTx?.transactionId?.hash
)
// Returns List<JettonTransactionParsed> with type, amountNano, sender, recipient, memo
```

Parsed opcodes:
| Opcode | Type | Ý nghĩa |
|--------|------|---------|
| `0x0f8a7ea5` | `send` | User gửi Jetton |
| `0x7362d09c` | `receive` | User nhận Jetton (transfer_notification) |
| `0x595f07bc` | `burn` | Đốt Jetton (unstake) |
| `0xd53276db` | _(skipped)_ | Excess refund |

---

## 7. NFT Support — TEP-62

### Lấy danh sách NFT
```kotlin
val nfts: List<NFTItem>? = tonManager.getNFTs(address, coinNetwork)
// Sử dụng Toncenter v3 REST: GET /nfts?owner_address=...
```

### Transfer NFT
```kotlin
val result = tonManager.transferNFT(nftAddress, toAddress, memo, coinNetwork)
```

---

## 8. Staking

### Pool Types
| Pool | Detect method | Deposit | Unstake |
|------|--------------|---------|---------|
| **Nominator** | `get_nominator_data` | `signDepositToNominatorPool` (opcode `0x4e73746b`) | Tự động bởi pool |
| **Tonstakers** | `get_pool_full_data` | `signTonstakersDeposit` (transfer TON) | `signJettonBurn` (đốt tsTON) |
| **Bemo** | `get_full_data` | `signBemoDeposit` (transfer TON) | `signJettonBurn` (đốt stTON) |

### Auto-detect & Stake
```kotlin
val result = tonManager.stake(amountNano, poolAddress, coinNetwork)
// Tự động detect pool type và gọi đúng method
```

### Unstake (Liquid Staking)
```kotlin
val result = tonManager.unstake(amountNano, poolAddress, coinNetwork)
// Chỉ hỗ trợ TONSTAKERS và BEMO (burn staking tokens)
// NOMINATOR: rút tự động bởi pool contract
```

### Staking Balance
```kotlin
val balance: TonStakingBalance = tonManager.getStakingBalance(address, poolAddress, coinNetwork)
// balance.amount      → tổng (gốc + lãi) quy về TON
// balance.rewards     → phần lãi
// balance.pendingDeposit / pendingWithdrawal (Nominator only)
```

Liquid staking rate: `amountInTon = tokenBalance × (totalTonLocked / totalTokenSupply)`

---

## 9. DNS — TEP-81

```kotlin
// Forward: domain.ton → address
val address = tonManager.resolveDns("example.ton", coinNetwork)

// Reverse: address → domain
val domain = tonManager.reverseResolveDns(address, coinNetwork)
```

Root DNS resolver: `Ef_SByTMM97KVRlaEFIqX_67pYI67FPRu_YBaAs7_pS48_p6`

---

## 10. Fee Estimation

```kotlin
val feeTon = tonManager.estimateFee(coinNetwork, address, bocBase64)
// Returns Double in TON (nanoTON / 1e9)
// Fee = inFwdFee + storageFee + gasFee + fwdFee
```

---

## 11. API Endpoints

| Network | v2 JSON-RPC | v3 REST |
|---------|------------|---------|
| Mainnet | `https://toncenter.com/api/v2/jsonRPC` | `https://toncenter.com/api/v3` |
| Testnet | `https://testnet.toncenter.com/api/v2/jsonRPC` | `https://testnet.toncenter.com/api/v3` |

API key (optional, for rate limits): `Config.shared.apiKeyToncenter`

---

## 12. Error Handling

| Error | Khi nào | Xử lý |
|-------|---------|-------|
| `WalletError.NetworkError` | API fail khi lấy seqno | Không cho ký transaction, thông báo user retry |
| `exitCode=-13/-14` on `seqno` | Wallet chưa deploy | `getSeqno` trả về 0 → `signTransaction` include stateInit |
| `WalletError.UnsupportedOperation` | Unstake Nominator / Unknown pool | Thông báo user |
| `Exception("Could not find Jetton Wallet")` | Jetton wallet chưa deploy | Token chưa có balance |

### Lưu ý quan trọng: `runGetMethod` exitCode

Toncenter `runGetMethod` trả về `ok=true` ngay cả khi get method thất bại (exitCode != 0).
Stack có thể chứa **garbage value** khi exitCode != 0.

**Quy tắc:** Luôn check `exitCode == 0` trước khi parse stack value.

```
// API response khi wallet chưa deploy:
{ "ok": true, "result": { "exit_code": -13, "stack": [["num", "0x14c97"]] } }
//                                    ^^^                        ^^^^^^^^
//                          method failed!              garbage — KHÔNG dùng!
```

| exitCode | Ý nghĩa |
|----------|---------|
| `0` | Thành công — parse stack bình thường |
| `-13` | Account not initialized (chưa deploy) |
| `-14` | Account not found |
| Khác | Get method error — không parse stack |

---

## 13. Tích hợp qua CommonCoinsManager (Recommended)

`CommonCoinsManager` là facade thống nhất — nên dùng cho cả Android và iOS.

```kotlin
val ccm = CommonCoinsManager.shared

// ── Balance ──
val balance = ccm.getBalance(NetworkName.TON)
val tokenBalance = ccm.getTokenBalance(NetworkName.TON, address, jettonMasterAddr)

// ── Send TON ──
val result = ccm.sendCoin(NetworkName.TON, toAddress, 1.5, memo = MemoData("Hello"))

// ── Send Jetton (convenience) ──
val result = ccm.sendJetton(
    toAddress = "UQ...",
    jettonMasterAddress = "EQ...",
    amount = 10.5,
    decimals = 6,  // USDT
    memo = "Payment"
)

// ── Transaction History (paginated) ──
val page1 = ccm.getTransactionHistoryPaginated(NetworkName.TON, limit = 20)
val page2 = ccm.getTransactionHistoryPaginated(
    NetworkName.TON, limit = 20,
    pageParam = page1.nextPageParam  // {"lt": "...", "hash": "..."}
)

// ── Token Transaction History (paginated) ──
val tokenTxs = ccm.getTokenTransactionHistoryPaginated(
    NetworkName.TON,
    policyId = "EQ...jettonMaster",  // Jetton Master address
    assetName = "",
    limit = 20
)

// ── Jetton Metadata ──
val metadata = ccm.getJettonMetadata("EQ...jettonMaster")
// metadata.name, metadata.symbol, metadata.decimals

// ── NFT ──
val nfts = ccm.getNFTs(NetworkName.TON, address)
val nftResult = ccm.transferNFT(NetworkName.TON, nftAddress, toAddress, "Gift")

// ── Staking ──
val stakeResult = ccm.stake(NetworkName.TON, 10_000_000_000L, poolAddress)
val unstakeResult = ccm.unstake(NetworkName.TON, 5_000_000_000L, poolAddress)  // poolAddress required!
val stakingBalance = ccm.getStakingBalance(NetworkName.TON, poolAddress = poolAddress)

// ── DNS ──
val resolved = ccm.resolveTonDns("alice.ton")
val domain = ccm.reverseResolveTonDns("UQ...")

// ── Pool Detection ──
val poolType = ccm.detectTonPoolType(poolAddress)  // NOMINATOR, TONSTAKERS, BEMO
```

---

## 14. Tích hợp Android (Low-level)

### Sử dụng qua TonService (Callback pattern)

```kotlin
// Khởi tạo
val tonService = TonService(
    mnemonicProvider = { coinsManager.currentMnemonic },
    scope = coinsManager  // CoroutineScope
)

// Lấy balance token
tonService.getTokenBalance(address, contractAddress) { tokenInfo, success, error ->
    if (success && tokenInfo != null) {
        // tokenInfo.balance (đã đúng decimals)
        // tokenInfo.symbol, tokenInfo.name, tokenInfo.decimals
    }
}

// Gửi token
tonService.sendToken(
    toAddress = "UQ...",
    contractAddress = "EQ...",  // Jetton Master
    amount = 10.5,
    decimals = 6,  // e.g., USDT
    memo = "Payment"
) { txHash, success, error -> ... }

// NFT
tonService.getNFTs(address) { nfts, error -> ... }
tonService.transferNFT(nftAddr, toAddr, memo) { txHash, success, error -> ... }
```

### Sử dụng trực tiếp TonManager (Coroutines, low-level)

```kotlin
val mgr = TonManager(mnemonic, WalletVersion.W5)

// Address
val address = mgr.getAddress()

// Balance
val tonBalance = mgr.getBalance(address, coinNetwork)
val tokenBalance = mgr.getBalanceToken(address, contractAddr, coinNetwork, decimals = 6)

// Transfer TON
val seqno = mgr.getSeqno(coinNetwork)
val boc = mgr.signTransaction(toAddress, amountNano, seqno, memo)
val result = mgr.transfer(boc, coinNetwork)

// Transfer Jetton
val boc = mgr.signJettonTransaction(masterAddr, toAddr, amountNano, seqno, coinNetwork)
val txHash = mgr.TransferToken(boc, coinNetwork)

// Staking
mgr.stake(amountNano, poolAddress, coinNetwork)
mgr.unstake(amountNano, poolAddress, coinNetwork)  // Tonstakers/Bemo only
val stakingBalance = mgr.getStakingBalance(address, poolAddress, coinNetwork)

// Transaction History (paginated)
val page1 = mgr.getTransactionHistory(address, coinNetwork, limit = 20)
val lastTx = page1?.lastOrNull()
val page2 = mgr.getTransactionHistory(
    address, coinNetwork, limit = 20,
    lt = lastTx?.transactionId?.lt,
    hash = lastTx?.transactionId?.hash
)
```

---

## 15. Tích hợp iOS (Swift)

TON được export qua XCFramework. Sử dụng trong Swift:

```swift
import crypto_wallet_lib

// Khởi tạo
let manager = TonManager(mnemonics: "word1 word2 ... word24", walletVersion: .w5)

// Address
let address = manager.getAddress()

// Balance (async/await via Kotlin coroutines → Swift concurrency)
let balance = try await manager.getBalance(address: address, coinNetwork: coinNetwork)

// Transfer
let seqno = try await manager.getSeqno(coinNetwork: coinNetwork)
let boc = try await manager.signTransaction(
    toAddress: "UQ...",
    amountNano: 1_000_000_000,  // 1 TON
    seqno: Int32(seqno),
    memo: "Hello"
)
let result = try await manager.transfer(dataSigned: boc, coinNetwork: coinNetwork)

// Token balance (chú ý decimals)
let usdtBalance = try await manager.getBalanceToken(
    address: address,
    contractAddress: "EQ...",   // USDT master
    coinNetwork: coinNetwork,
    decimals: 6                 // USDT = 6 decimals
)

// Staking
let stakeResult = try await manager.stake(
    amount: 10_000_000_000,     // 10 TON
    poolAddress: "EQ...",
    coinNetwork: coinNetwork
)

// NFTs
let nfts = try await manager.getNFTs(address: address, coinNetwork: coinNetwork)
```

> **Lưu ý iOS:**
> - Kotlin `suspend fun` → Swift `async throws`
> - Kotlin `Int` → Swift `Int32` (KotlinInt)
> - Kotlin `Long` → Swift `Int64` (KotlinLong)
> - Kotlin `Double` → Swift `Double`
> - Kotlin `String?` → Swift `String?`

---

## 16. Task Tracker

### Phase 1-5: Core (Completed)
| Task | Status |
|------|--------|
| T1.1 SDK & Dependencies | ✅ |
| T1.2 NetworkName.TON & CoinNetwork | ✅ |
| T2.1 TonManager (Address, Mnemonic) | ✅ |
| T2.2 TonApiService (Balance) | ✅ |
| T2.3 Transaction History | ✅ |
| T3.1 Cell/BOC Serialization | ✅ |
| T3.2 Transaction Signing | ✅ |
| T3.3 Broadcast | ✅ |
| T3.4 Fee Estimation | ✅ |
| T4.1 ACTCoin.TON | ✅ |
| T4.2 CoinsManager Integration | ✅ |
| T5.1 Unit Tests (Address) | ✅ |
| T5.2 Integration Tests (API) | ⏳ |

### Phase 6: Jetton (Completed)
| Task | Status |
|------|--------|
| T6.1 Jetton Wallet Address Derivation | ✅ |
| T6.2 Jetton Balance | ✅ |
| T6.3 Jetton Metadata | ✅ |
| T6.4 Jetton Transfer | ✅ |
| T6.5 Jetton Transaction History | ✅ |

### Phase 7: DNS (Completed)
| Task | Status |
|------|--------|
| T7.1 DNS Resolution | ✅ |
| T7.2 Reverse DNS | ✅ |

### Phase 8: NFT (Completed)
| Task | Status |
|------|--------|
| T8.1 Fetch NFTs (Toncenter v3) | ✅ |
| T8.2 NFT Metadata Parsing | ✅ |
| T8.3 NFT Transfer (TEP-62) | ✅ |

### Phase 9: Staking (Completed)
| Task | Status |
|------|--------|
| T9.1 Nominator Pool Deposit | ✅ |
| T9.2 Liquid Staking (Tonstakers/Bemo) | ✅ |
| T9.3 Staking Balance & Rewards | ✅ |

### Live Network Tests (Manual)

File: `commonTest/.../wallets/ton/TonManagerTest.kt`

| Test | Mô tả |
|------|--------|
| `testDiagnosticWalletVersion` | In W4/W5 address + balance + seqno — dùng để xác định wallet version nào đang giữ funds |
| `testSendTonMainnet` | Gửi 1 TON trên mainnet — tự detect W4/W5, chọn version có funds |

**Lệnh chạy** (bỏ `@Ignore` trước khi chạy):
```bash
# Diagnostic: xem wallet version nào có funds
./gradlew :crypto-wallet-lib:jvmTest --tests "*.TonManagerTest.testDiagnosticWalletVersion"

# Gửi 1 TON thực (⚠️ tốn TON thật!)
./gradlew :crypto-wallet-lib:jvmTest --tests "*.TonManagerTest.testSendTonMainnet"

# Debug: verify code hash & public key on-chain
./gradlew :crypto-wallet-lib:jvmTest --tests "*.TonManagerTest.testDebugW5CodeHash"
```

### Phase 10: Audit Fixes (v2)
| Task | Status |
|------|--------|
| BUG-1: Jetton balance decimal handling | ✅ Fixed |
| BUG-2: getSeqno error handling (nullable) | ✅ Fixed |
| BUG-3: Transaction expiry (validUntil = now+60s) | ✅ Fixed |
| IMPROVE-1: Transaction history pagination (lt/hash) | ✅ Done |
| IMPROVE-2: Unstake via Jetton burn | ✅ Done |
| IMPROVE-3: Parsed Jetton transaction history | ✅ Done |
| IMPROVE-4: W5R1 multi-transfer batching | ⏳ Planned |
| IMPROVE-5: Transaction confirmation polling | ⏳ Planned |
| IMPROVE-6: Pool detection hardening | ⏳ Planned |
| IMPROVE-7: On-chain Jetton metadata (layout 0x00) | ⏳ Planned |
