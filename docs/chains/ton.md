# TON Blockchain Integration Plan (Android)

This document outlines the strategy, architecture, and task list for integrating TON (The Open Network) into the CryptoWallet library.

## 1. Technical Analysis

### Architecture
- **Common Layer**: Define `TonManager` extending `BaseCoinManager`.
- **Service Layer**: Implement `TonApiService` using Ktor for balance, transaction history, and broadcasting.
- **Android Layer**: Extend `CoinsManager` in `androidMain` to support `ACTCoin.TON`.
- **SDK**: Use `ton-kotlin:0.5.0` for address derivation, cell manipulation (BOC), and transaction signing.

### TON Specifics
- **Mnemonic**: 24 words (Standard TON mnemonic).
- **Wallet Version**: Default to `V4R2`.
- **Address Formats**: Support Bounceable (Base64) and Non-bounceable formats.
- **Provider**: Toncenter API (Mainnet/Testnet).

---

## 2. Task List & Progress Tracker

| Task ID | Description | Priority | Status |
| :--- | :--- | :--- | :--- |
| **PHASE 1** | **Research & Environment Setup** | | |
| T1.1 | Finalize TON SDK selection and add dependencies to `libs.versions.toml` | High | ✅ Completed |
| T1.2 | Define `NetworkName.TON` and update `CoinNetwork` config | High | ✅ Completed |
| **PHASE 2** | **Core Implementation (commonMain)** | | |
| T2.1 | Implement `TonManager` (Address derivation, Mnemonic to Seed) | High | ✅ Completed |
| T2.2 | Implement `TonApiService` (Ktor) for fetching balances | High | ✅ Completed |
| T2.3 | Implement `TonApiService` for transaction history | Medium | ✅ Completed |
| **PHASE 3** | **Transaction Logic** | | |
| T3.1 | Implement Cell/BOC serialization for TON transactions | High | ✅ Completed |
| T3.2 | Implement transaction signing logic | High | ✅ Completed |
| T3.3 | Implement broadcast transaction endpoint | High | ✅ Completed |
| T3.4 | Implement fee estimation (estimate_fee RPC) | Medium | ✅ Completed |
| **PHASE 4** | **Android Integration (androidMain)** | | |
| T4.1 | Add `ACTCoin.TON` to `coinkits` | Medium | ✅ Completed |
| T4.2 | Update `CoinsManager.kt` to handle TON requests | Medium | ✅ Completed |
| **PHASE 5** | **Testing & Validation** | | |
| T5.1 | Create unit tests for address derivation in `commonTest` | High | ✅ Completed |
| T5.2 | Create integration tests for API calls | Medium | ⏳ Pending |

---

## 3. Jetton Support (Tokens) - PHASE 6

TON tokens (Jettons) require interaction with specific smart contracts.

| Task ID | Description | Priority | Status |
| :--- | :--- | :--- | :--- |
| T6.1 | Implement Jetton Wallet address derivation | High | ✅ Completed |
| T6.2 | Fetch Jetton balances via `runGetMethod` (get_wallet_data) | High | ✅ Completed |
| T6.3 | Fetch Jetton metadata (Name, Symbol, Decimals) from Jetton Master | Medium | ✅ Completed |
| T6.4 | Implement Jetton transfer logic (Internal message to Jetton Wallet) | High | ✅ Completed |
| T6.5 | Implement Jetton transaction history | Medium | ✅ Completed |

---

## 3. Advanced Features (DNS, NFT, Staking)

### TON DNS (PHASE 7)
| Task ID | Description | Priority | Status |
| :--- | :--- | :--- | :--- |
| T7.1 | Implement DNS resolution (domain.ton -> address) | Medium | ✅ Completed |
| T7.2 | Implement Reverse DNS lookup (address -> domain) | Low | ✅ Completed |

### NFT Support (PHASE 8)
| Task ID | Description | Priority | Status |
| :--- | :--- | :--- | :--- |
| T8.1 | Fetch NFT items owned by address (via Indexer API) | High | ⏳ Pending |
| T8.2 | Implement NFT metadata parsing (TEP-64) | Medium | ⏳ Pending |
| T8.3 | Implement NFT transfer logic | High | ⏳ Pending |

### Staking Integration (PHASE 9)
| Task ID | Description | Priority | Status |
| :--- | :--- |:---------| :--- |
| T9.1 | Implement TON deposit to Nominator Pools | Mhedium  | ✅ Completed |
| T9.2 | Implement Liquid Staking support (Tonstakers/Bemo) | Medium   | ✅ Completed |
| T9.3 | Fetch staking rewards and balance | Medium   | ✅ Completed |

## 4. Integration Details

### Proposed `TonManager` Interface
```kotlin
class TonManager(mnemonics: String) : BaseCoinManager() {
    // Address derivation for V4R2
    fun getV4R2Address(): String
    
    // Implementation of BaseCoinManager methods
    override suspend fun getBalance(...)
    override suspend fun transfer(...)
}
```

### Dependencies added
- `org.ton:ton-kotlin:0.5.0`: For core TON logic, including address derivation and BOC serialization.
- `kotlinx-serialization`: For API responses.
