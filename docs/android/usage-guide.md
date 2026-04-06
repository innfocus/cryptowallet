# Hướng dẫn sử dụng cho Android (Usage Guide)

Tài liệu này hướng dẫn các Junior Android Developer cách tích hợp các chức năng Blockchain vào ứng dụng bằng `CoinsManager`.

## 1. Entry Point Duy nhất
Mọi thao tác với blockchain trên Android PHẢI thông qua singleton:
`com.lybia.cryptowallet.coinkits.CoinsManager.shared`

## 2. Các chức năng chính

### 2.1. Khởi tạo và Lấy địa chỉ ví
Trước khi thực hiện bất kỳ thao tác nào, bạn cần cung cấp Mnemonic cho Manager.

```kotlin
val mnemonic = "your twelve words mnemonic phrase..."
val coinsManager = CoinsManager.shared
coinsManager.init(mnemonic)

// Lấy địa chỉ ví
val btcAddress = coinsManager.getAddress(ACTCoin.Bitcoin)
val ethAddress = coinsManager.getAddress(ACTCoin.Ethereum)
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
