# Kế hoạch Triển khai: Hỗ trợ Phí Dịch Vụ (Service Fee) cho CommonCoinsManager

## Tổng quan

Triển khai hỗ trợ phí dịch vụ (service fee) vào `CommonCoinsManager` trong `commonMain`, bao gồm: helper functions phân loại chain, mở rộng `estimateFee` và `sendCoin`, hàm `validateSufficientBalance` mới, mở rộng BitcoinManager và CardanoManager cho UTXO service fee output, và property-based tests + unit tests.

## Tasks

- [ ] 1. Thêm helper functions và hằng số cho service fee
  - [-] 1.1 Thêm hằng số `FEE_MULTIPLIER`, `UTXO_CHAINS`, `ACCOUNT_CHAINS` vào companion object của `CommonCoinsManager`
    - Thêm `const val FEE_MULTIPLIER = 2`
    - Thêm `val UTXO_CHAINS = setOf(NetworkName.BTC, NetworkName.CARDANO)`
    - Thêm `val ACCOUNT_CHAINS = setOf(NetworkName.ETHEREUM, NetworkName.ARBITRUM, NetworkName.XRP, NetworkName.TON)`
    - _Requirements: 1.2, 1.3, 1.4_

  - [~] 1.2 Thêm helper function `isUtxoChain(coin: NetworkName): Boolean`
    - Trả về `coin in UTXO_CHAINS`
    - _Requirements: 1.4, 2.2, 4.4_

  - [~] 1.3 Thêm helper function `hasServiceFee(serviceAddress: String?, serviceFee: Double): Boolean`
    - Trả về `!serviceAddress.isNullOrBlank() && serviceFee > 0.0`
    - _Requirements: 5.1, 5.2, 5.3, 2.3, 2.4_

  - [~] 1.4 Viết property test cho `hasServiceFee`
    - **Property 1: Phát hiện hasServiceFee**
    - **Validates: Requirements 5.1, 5.2, 5.3, 2.3, 2.4**

- [ ] 2. Thêm data class `BalanceValidationResult` và hàm `validateSufficientBalance`
  - [~] 2.1 Tạo data class `BalanceValidationResult(sufficient, totalRequired, deficit)`
    - Đặt trong file `CommonCoinsManager.kt` cùng với các data class hiện có (`BalanceResult`, `SendResult`)
    - _Requirements: 4.1_

  - [~] 2.2 Implement hàm `validateSufficientBalance(coin, amount, networkFee, serviceFee, balance): BalanceValidationResult`
    - UTXO chain: `totalRequired = amount + networkFee + serviceFee`
    - Account chain: `totalRequired = amount + networkFee + serviceFee` (networkFee đã bao gồm phí cho cả 2 giao dịch từ `estimateFee`)
    - `deficit = max(0.0, totalRequired - balance)`
    - `sufficient = totalRequired <= balance`
    - _Requirements: 4.1, 4.2, 4.3, 4.4_

  - [~] 2.3 Viết property test cho `validateSufficientBalance`
    - **Property 8: Kiểm tra đủ số dư**
    - **Validates: Requirements 4.2, 4.3, 4.4**

  - [~] 2.4 Viết unit tests cho `validateSufficientBalance`
    - Test edge cases: balance vừa đủ, balance thiếu 1 đơn vị nhỏ nhất, serviceFee = 0, tất cả giá trị = 0
    - Test UTXO chain vs Account chain tính toán khác nhau
    - _Requirements: 4.1, 4.2, 4.3, 4.4_

- [~] 3. Checkpoint - Đảm bảo tất cả tests pass
  - Đảm bảo tất cả tests pass, hỏi người dùng nếu có thắc mắc.

- [ ] 4. Mở rộng hàm `estimateFee` hỗ trợ service fee
  - [~] 4.1 Bổ sung tham số `serviceAddress: String? = null` và `serviceFee: Double = 0.0` vào hàm `estimateFee` trong `CommonCoinsManager`
    - Giữ nguyên chữ ký hiện tại tương thích ngược (tham số mới có giá trị mặc định)
    - _Requirements: 1.5_

  - [~] 4.2 Implement logic service fee cho Account Chain trong `estimateFee`
    - Khi `hasServiceFee == true` và coin thuộc Account Chain (ETH, Arbitrum, XRP, TON): nhân kết quả fee × `FEE_MULTIPLIER`
    - Khi `hasServiceFee == false`: giữ nguyên hành vi hiện tại
    - _Requirements: 1.1, 1.2, 1.3_

  - [~] 4.3 Implement logic service fee cho UTXO Chain trong `estimateFee`
    - Khi `hasServiceFee == true` và coin thuộc UTXO Chain (BTC, Cardano): truyền `serviceAddress` vào chain manager để tính thêm output
    - Cần mở rộng `BitcoinManager.estimateFee` và `BitcoinManager.estimateFeeLocal` để nhận `serviceAddress`
    - _Requirements: 1.4_

  - [~] 4.4 Viết property test cho hệ số nhân phí Account Chain
    - **Property 2: Hệ số nhân phí cho Account Chain**
    - **Validates: Requirements 1.1, 1.2, 1.3**

  - [~] 4.5 Viết property test cho UTXO Chain fee estimation
    - **Property 3: UTXO Chain fee estimation với service address**
    - **Validates: Requirements 1.4**

  - [~] 4.6 Viết property test cho tương thích ngược
    - **Property 10: Tương thích ngược**
    - **Validates: Requirements 1.5, 2.6**

- [ ] 5. Mở rộng hàm `sendCoin` hỗ trợ gửi service fee
  - [~] 5.1 Implement logic service fee cho Account Chain trong `sendCoin`
    - Khi `hasServiceFee == true` và coin thuộc Account Chain:
      - Chia đôi `networkFee` cho mỗi giao dịch
      - Gửi giao dịch chính với `networkFee / 2`
      - Nếu thành công: gửi giao dịch phí dịch vụ đến `serviceAddress` với `serviceFee` và `networkFee / 2`
      - Nếu giao dịch phí dịch vụ thất bại: log warning, trả về `SendResult(txHash=mainTxHash, success=true, error="Service fee failed: ...")`
    - Xử lý riêng cho từng chain: ETH/Arbitrum gọi `sendEthBigInt`, XRP gọi `sendXrp`, TON gọi `signTransaction` + `transfer`
    - _Requirements: 2.1, 2.5, 3.1, 3.2, 3.3, 3.4, 6.1, 6.2, 6.3_

  - [~] 5.2 Implement logic service fee cho UTXO Chain trong `sendCoin`
    - Khi `hasServiceFee == true` và coin thuộc UTXO Chain: truyền `serviceAddress` và `serviceFee` vào chain manager
    - Mở rộng `BitcoinManager.sendBtc` / `sendBtcLocal` để nhận `serviceAddress` và `serviceFeeAmount`
    - Mở rộng `CardanoManager.buildAndSignTransaction` để nhận `serviceAddress` và `serviceFeeLovelace`
    - _Requirements: 2.2_

  - [~] 5.3 Implement logic bỏ qua service fee khi không cần thiết
    - Khi `hasServiceFee == false` (serviceAddress rỗng/null hoặc serviceFee = 0): chỉ giao dịch chính, không thay đổi hành vi
    - _Requirements: 2.3, 2.4, 5.4_

  - [~] 5.4 Viết property test cho Account Chain gửi hai giao dịch
    - **Property 4: Account Chain gửi hai giao dịch**
    - **Validates: Requirements 2.1**

  - [~] 5.5 Viết property test cho UTXO Chain bao gồm service fee output
    - **Property 5: UTXO Chain bao gồm service fee output**
    - **Validates: Requirements 2.2**

  - [~] 5.6 Viết property test cho giao dịch chính thất bại
    - **Property 6: Giao dịch chính thất bại ngăn giao dịch phí dịch vụ**
    - **Validates: Requirements 2.5**

  - [~] 5.7 Viết property test cho chia đôi phí mạng lưới
    - **Property 7: Chia đôi phí mạng lưới cho Account Chain**
    - **Validates: Requirements 3.1, 3.2**

  - [~] 5.8 Viết property test cho giao dịch phí dịch vụ thất bại
    - **Property 9: Giao dịch phí dịch vụ thất bại bảo toàn giao dịch chính**
    - **Validates: Requirements 6.1**

- [~] 6. Checkpoint - Đảm bảo tất cả tests pass
  - Đảm bảo tất cả tests pass, hỏi người dùng nếu có thắc mắc.

- [ ] 7. Mở rộng BitcoinManager và CardanoManager cho UTXO service fee output
  - [~] 7.1 Mở rộng `BitcoinManager.sendBtc` và `sendBtcLocal` để hỗ trợ service fee output
    - Bổ sung tham số `serviceAddress: String? = null` và `serviceFeeAmount: Long = 0` với giá trị mặc định
    - Khi có service fee: thêm output bổ sung đến `serviceAddress` với `serviceFeeAmount` trong UTXO transaction
    - _Requirements: 2.2_

  - [~] 7.2 Mở rộng `BitcoinManager.estimateFee` và `estimateFeeLocal` để tính phí với service address
    - Bổ sung tham số `serviceAddress: String? = null` với giá trị mặc định
    - Khi có service address: tính thêm output size trong ước tính phí
    - _Requirements: 1.4_

  - [~] 7.3 Mở rộng `CardanoManager.buildAndSignTransaction` để hỗ trợ service fee output
    - Bổ sung tham số `serviceAddress: String? = null` và `serviceFeeLovelace: Long = 0` với giá trị mặc định
    - Khi có service fee: thêm output bổ sung đến `serviceAddress` trong Cardano transaction
    - _Requirements: 2.2_

  - [~] 7.4 Viết unit tests cho BitcoinManager và CardanoManager service fee
    - Test BitcoinManager tạo transaction với service fee output
    - Test CardanoManager tạo transaction với service fee output
    - Test tương thích ngược khi không có service fee
    - _Requirements: 1.4, 2.2_

- [ ] 8. Kết nối toàn bộ: cập nhật `sendCoin` và `estimateFee` gọi chain managers với tham số service fee
  - [~] 8.1 Cập nhật `sendCoin` dispatch cho BTC để truyền `serviceAddress` và `serviceFee` vào `BitcoinManager`
    - Convert `serviceFee` từ BTC sang satoshi và truyền vào `sendBtc`/`sendBtcLocal`
    - _Requirements: 2.2_

  - [~] 8.2 Cập nhật `sendCoin` dispatch cho Cardano để truyền `serviceAddress` và `serviceFee` vào `CardanoManager`
    - Convert `serviceFee` từ ADA sang lovelace và truyền vào `buildAndSignTransaction`
    - _Requirements: 2.2_

  - [~] 8.3 Cập nhật `estimateFee` dispatch cho BTC để truyền `serviceAddress` vào `BitcoinManager.estimateFee`
    - _Requirements: 1.4_

  - [~] 8.4 Viết unit tests end-to-end cho service fee flow
    - Test `estimateFee` → `validateSufficientBalance` → `sendCoin` flow cho Account Chain (ETH, XRP, TON)
    - Test `estimateFee` → `validateSufficientBalance` → `sendCoin` flow cho UTXO Chain (BTC, Cardano)
    - Test flow khi không có service fee (tương thích ngược)
    - _Requirements: 1.1-1.5, 2.1-2.6, 3.1-3.4, 4.1-4.4, 5.1-5.4, 6.1-6.3_

- [~] 9. Final checkpoint - Đảm bảo tất cả tests pass
  - Đảm bảo tất cả tests pass, hỏi người dùng nếu có thắc mắc.

## Ghi chú

- Tasks đánh dấu `*` là tùy chọn và có thể bỏ qua để triển khai MVP nhanh hơn
- Mỗi task tham chiếu đến requirements cụ thể để đảm bảo traceability
- Checkpoints đảm bảo kiểm tra tăng dần
- Property tests kiểm tra các thuộc tính phổ quát (Kotest Property Testing)
- Unit tests kiểm tra ví dụ cụ thể và edge cases
- Tất cả test files đặt tại `crypto-wallet-lib/src/commonTest/kotlin/com/lybia/cryptowallet/coinkits/`
