# Hướng dẫn tích hợp cho iOS (Swift Integration)

Thư viện `crypto-wallet-lib` được build dưới dạng **XCFramework**. Tài liệu này hướng dẫn cách gọi các hàm từ Kotlin trong code Swift.

## 1. Khởi tạo CommonCoinsManager
Trên iOS, chúng ta sử dụng `CommonCoinsManager` trực tiếp từ bộ lõi KMP.

```swift
import CryptoWalletLib

let mnemonic = "your twelve words..."
let manager = CommonCoinsManager(mnemonic: mnemonic, configs: [:])
```

## 2. Sử dụng Async/Await (Swift 5.5+)
Kotlin `suspend` functions sẽ tự động được convert thành `async` functions trong Swift.

### 2.1. Lấy số dư
```swift
func fetchBalance() async {
    do {
        let balance = try await manager.getBalance(coin: .bitcoin, address: nil)
        print("BTC Balance: \(balance)")
    } catch {
        print("Error: \(error)")
    }
}
```

### 2.2. Lấy địa chỉ
Hàm này là hàm đồng bộ (synchronous) trong Kotlin nên gọi trực tiếp:
```swift
let btcAddress = manager.getAddress(coin: .bitcoin)
```

## 3. Xử lý các kiểu dữ liệu đặc biệt

### 3.1. Enum ACTCoin
Trong Swift, các enum Kotlin sẽ có tiền tố hoặc viết thường tùy theo cách mapping của KMP. Thông thường là:
- `.bitcoin`
- `.ethereum`
- `.cardano`
- `.ton`

### 3.2. List và Models
Kotlin `List<TransactionData>` sẽ trở thành `Array<TransactionData>` trong Swift. Bạn có thể truy cập các property bình thường:
```swift
let firstTx = transactions[0]
print(firstTx.iD) // TxHash
print(firstTx.amount)
```

## 4. Troubleshooting cho iOS
- **Missing Framework:** Đảm bảo bạn đã add `CryptoWalletLib.xcframework` vào "Frameworks, Libraries, and Embedded Content" và chọn "Embed & Sign".
- **Architecture Mismatch:** Nếu chạy trên Simulator (M1/M2), hãy đảm bảo thư viện hỗ trợ `iosArm64Sim`.
