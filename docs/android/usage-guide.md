# Hướng dẫn sử dụng cho Android (Usage Guide)

Tài liệu này hướng dẫn các Junior Android Developer cách tích hợp các chức năng Blockchain vào ứng dụng bằng `CoinsManager`.

## 1. Entry Point Duy nhất
Mọi thao tác với blockchain trên Android PHẢI thông qua singleton:
`com.lybia.cryptowallet.coinkits.CoinsManager.shared`

## 2. Các chức năng chính

### 2.1. Khởi tạo và Lấy địa chỉ ví
Trước khi thực hiện bất kỳ thao tác nào, bạn cần cung cấp Mnemonic cho Manager. Mnemonic có thể là **bất kỳ ngôn ngữ BIP-39 nào** (English, Japanese, Chinese Simplified/Traditional, French, Italian, Spanish, Korean, Czech, Portuguese) — ngôn ngữ được tự động phát hiện.

```kotlin
val mnemonic = "your twelve words mnemonic phrase..."
val coinsManager = CoinsManager.shared
coinsManager.init(mnemonic)

// Lấy địa chỉ ví
val btcAddress = coinsManager.getAddress(ACTCoin.Bitcoin)
val ethAddress = coinsManager.getAddress(ACTCoin.Ethereum)
```

#### Tạo seed phrase mới (đa ngôn ngữ)

```kotlin
import com.lybia.cryptowallet.wallets.bip39.Bip39Language
import com.lybia.cryptowallet.wallets.bip39.Mnemonics
import com.lybia.cryptowallet.wallets.bip39.MNEMONIC_SIZE

// Mặc định English
val words = Mnemonics.generateRandomSeed(MNEMONIC_SIZE.WORDS_24)

// Hoặc chọn ngôn ngữ
val ja = Mnemonics.generateRandomSeed(MNEMONIC_SIZE.WORDS_24, Bip39Language.JAPANESE)
val ko = Mnemonics.generateRandomSeed(MNEMONIC_SIZE.WORDS_12, Bip39Language.KOREAN)

// Validate (auto-detect ngôn ngữ + kiểm tra checksum BIP-39)
Mnemonics.validateSeedWord(ja.joinToString("\u3000"))   // JA conventionally dùng U+3000

// (Tuỳ chọn) Lock-down để chỉ auto-detect EN+JA — gọi 1 lần ở Application.onCreate
Bip39Language.setEnabledLanguages(listOf(Bip39Language.ENGLISH, Bip39Language.JAPANESE))
```

### 2.2. Lấy số dư (Balance)
Sử dụng Callback để nhận kết quả (đảm bảo tương thích với code cũ).

```kotlin
coinsManager.getBalance(ACTCoin.Bitcoin) { balance ->
    // balance là Double (ví dụ: 0.5 BTC)
    Log.d("Wallet", "Số dư BTC: $balance")
}
```

### 2.3. Lấy lịch sử giao dịch
```kotlin
coinsManager.getTransactions(ACTCoin.Ethereum) { transactions ->
    // transactions là List<TransactionData>
    transactions.forEach { tx ->
        Log.d("Wallet", "TxHash: ${tx.iD}, Amount: ${tx.amount}")
    }
}
```

### 2.4. Chuyển tiền (Send Coin)
```kotlin
coinsManager.sendCoin(
    coin = ACTCoin.Bitcoin,
    toAddress = "destination_address",
    amount = 0.001,
    fee = 0.0001
) { txHash, error ->
    if (error == null) {
        Log.d("Wallet", "Gửi thành công: $txHash")
    } else {
        Log.e("Wallet", "Lỗi: $error")
    }
}
```

## 3. Lưu ý quan trọng
- **Threading:** Các hàm callback của `CoinsManager` thường chạy trên Background Thread. Nếu cần update UI, hãy dùng `runOnUiThread` hoặc `lifecycleScope`.
- **Unit:** Số dư trả về đã được quy đổi sang đơn vị lớn nhất (BTC, ETH, ADA), không phải đơn vị nhỏ nhất (Satoshi, Wei, Lovelace).
