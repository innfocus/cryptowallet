# Cardano Integration cho Android

Hướng dẫn tích hợp Cardano (ADA) vào ứng dụng Android, bao gồm Byron (legacy) và Shelley (hiện tại).

> **Tài liệu kỹ thuật chi tiết:** [Byron spec](../chains/cardano-byron.md) | [Shelley spec](../chains/cardano-shelley.md)

---

## 1. Dependency

```groovy
dependencies {
    implementation 'io.github.innfocus:crypto-wallet-lib-android:1.0.2'
}
```

---

## 2. Khởi tạo

### 2.1 Qua CoinsManager (recommended)

`CoinsManager` là entry point chính trên Android. Cardano được delegate sang `CommonCoinsManager` bên trong.

```kotlin
import com.lybia.cryptowallet.coinkits.CoinsManager
import com.lybia.cryptowallet.coinkits.hdwallet.bip32.ACTCoin
import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.enums.Network

// Chọn network
Config.shared.setNetwork(Network.MAINNET) // hoặc Network.TESTNET

// Set mnemonic (BIP-39, 12 hoặc 24 từ)
CoinsManager.shared.updateMnemonic("your mnemonic words ...")
```

### 2.2 Trực tiếp qua CardanoManager (advanced)

Dùng khi cần truy cập API chi tiết hơn (Byron address, staking, native token).

```kotlin
import com.lybia.cryptowallet.wallets.cardano.CardanoManager

val mnemonic = "your mnemonic words ..."
val cardanoManager = CardanoManager(mnemonic)
```

---

## 3. Lấy địa chỉ ví

### 3.1 Shelley address (mặc định)

```kotlin
// Qua CoinsManager
val address = CoinsManager.shared.addresses()[ACTCoin.Cardano]
// Trả về: addr1q... (Bech32, base address)

// Qua CardanoManager
val address = cardanoManager.getAddress()          // = getShelleyAddress(0, 0)
val address2 = cardanoManager.getShelleyAddress(0, 1) // account=0, index=1
```

### 3.2 Byron address (legacy)

```kotlin
val byronAddress = cardanoManager.getByronAddress(0)
// Trả về: Ae2tdPwUPEZ... (Base58)
```

### 3.3 Staking address

```kotlin
val stakingAddress = cardanoManager.getStakingAddress(0)
// Trả về: stake1u... (Bech32)
```

> **Lưu ý:** Shelley và Byron dùng chung Icarus master key (CIP-0003), chỉ khác derivation path.
> - Shelley: `m/1852'/1815'/account'/0/index` (CIP-1852)
> - Byron: `m/44'/1815'/0'/0/index` (BIP-44)

---

## 4. Lấy số dư

```kotlin
import com.lybia.cryptowallet.coinkits.BalanceHandle

// Qua CoinsManager — trả về ADA (Double), không phải lovelace
CoinsManager.shared.getBalance(ACTCoin.Cardano, object : BalanceHandle {
    override fun completionHandler(balance: Double, success: Boolean) {
        if (success) {
            Log.d("ADA", "Balance: $balance ADA")
        }
    }
})

// Qua CardanoManager — cũng trả về ADA (Double)
val balance = cardanoManager.getBalance(address = null, coinNetwork = coinNetwork)
```

> **Quy đổi:** 1 ADA = 1,000,000 lovelace. API trả về đã quy đổi sang ADA.

---

## 5. Lịch sử giao dịch

```kotlin
import com.lybia.cryptowallet.coinkits.TransactionsHandle
import com.lybia.cryptowallet.coinkits.TransationData

CoinsManager.shared.getTransactions(ACTCoin.Cardano, null, object : TransactionsHandle {
    override fun completionHandler(
        transactions: Array<TransationData>?,
        moreParam: JsonObject?,
        errStr: String
    ) {
        transactions?.forEach { tx ->
            Log.d("ADA", "Hash: ${tx.hash}, Amount: ${tx.amount}")
        }
    }
})
```

---

## 6. Chuyển ADA

### 6.1 Shelley transaction (mặc định)

```kotlin
// Qua CoinsManager — amount và fee đơn vị ADA
CoinsManager.shared.sendCoin(
    coin = ACTCoin.Cardano,
    toAddress = "addr1q...",
    amount = 5.0,        // 5 ADA
    fee = 0.17           // ~170,000 lovelace
) { txHash, error ->
    if (error == null) {
        Log.d("ADA", "TX submitted: $txHash")
    }
}

// Qua CardanoManager — amount và fee đơn vị lovelace
val signedTx = cardanoManager.buildAndSignTransaction(
    toAddress = "addr1q...",
    amount = 5_000_000,    // 5 ADA in lovelace
    fee = 170_000          // fee in lovelace
)
val result = cardanoManager.transfer(signedTx.cborHex, coinNetwork)
```

### 6.2 Byron transaction

```kotlin
val signedTx = cardanoManager.buildAndSignByronTransaction(
    toAddress = "Ae2tdPwUPEZ...",
    amount = 5_000_000,
    fee = 170_000,
    fromIndex = 0
)
val result = cardanoManager.transfer(signedTx.cborHex, coinNetwork)
```

### 6.3 Quy tắc transaction (IOHK protocol)

| Quy tắc | Giá trị |
|---|---|
| Minimum UTXO output | 1 ADA (1,000,000 lovelace) |
| Change < 1 ADA | Absorb vào fee |
| Transaction size limit | 16,384 bytes |
| Input deduplication | Tự động |

---

## 7. Native Token

```kotlin
// Lấy balance token
val tokenBalance = cardanoManager.getTokenBalance(
    address = "addr1q...",
    policyId = "abcdef1234...",
    assetName = "MyToken"
)

// Gửi token
val txHex = cardanoManager.sendToken(
    toAddress = "addr1q...",
    policyId = "abcdef1234...",
    assetName = "MyToken",
    amount = 100,
    fee = 200_000
)
```

---

## 8. Staking

```kotlin
// Delegate vào pool
val result = cardanoManager.stake(
    amount = 10_000_000,  // 10 ADA deposit (protocol param)
    poolAddress = "pool1...",
    coinNetwork = coinNetwork
)

// Lấy staking rewards
val rewards = cardanoManager.getStakingRewards(
    address = "stake1u...",
    coinNetwork = coinNetwork
)

// Undelegate (deregister staking key)
val result = cardanoManager.unstake(
    amount = 0,
    coinNetwork = coinNetwork
)
```

> **Lưu ý:** `stake()` và `unstake()` ký bằng cả payment key + staking key (2 witnesses).

---

## 9. Threading

- `CoinsManager` chạy trên `Dispatchers.IO`, callback trả về trên `Dispatchers.Main`.
- `CardanoManager` suspend functions — gọi trong `CoroutineScope` hoặc `viewModelScope`:

```kotlin
viewModelScope.launch {
    val balance = cardanoManager.getBalance(null, coinNetwork)
    // Update UI trực tiếp (đã ở Main thread nếu dùng viewModelScope)
}
```

---

## 10. Lỗi thường gặp

| Lỗi | Nguyên nhân | Cách fix |
|---|---|---|
| Address bắt đầu sai prefix | Sai network config | Kiểm tra `Config.shared.setNetwork()` |
| Balance = 0 dù có ADA | Dùng Byron address để query Shelley UTXO | Dùng đúng loại address |
| Transaction bị reject | Amount < 1 ADA (min UTXO) | Đảm bảo output >= 1,000,000 lovelace |
| `insufficient funds` | Tổng UTXO < amount + fee | Kiểm tra balance trước khi gửi |
