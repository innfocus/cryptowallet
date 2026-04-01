# Tài liệu Yêu cầu — Hỗ trợ Phí Dịch Vụ (Service Fee) cho CommonCoinsManager

## Giới thiệu

Tính năng này bổ sung hỗ trợ phí dịch vụ (service fee) vào `CommonCoinsManager` trong `commonMain`, tương đương với chức năng đã có trong `CoinsManager` (androidMain). Phí dịch vụ là khoản phí bổ sung thu từ người dùng trên mỗi giao dịch chuyển tiền, được cấu hình động từ Backend. Cơ chế xử lý khác nhau tùy theo kiến trúc blockchain: UTXO-based (thêm output) và Account-based (giao dịch riêng biệt).

## Thuật ngữ

- **CommonCoinsManager**: Lớp facade thống nhất trong `commonMain` quản lý tất cả thao tác coin trên Kotlin Multiplatform.
- **Service_Fee**: Khoản phí bổ sung thu từ người dùng, được gửi đến địa chỉ ví dịch vụ (Service_Address) trên mỗi giao dịch chuyển tiền.
- **Service_Address**: Địa chỉ ví nhận phí dịch vụ, được cấu hình từ Backend. Chuỗi rỗng nghĩa là miễn phí dịch vụ.
- **Network_Fee**: Phí mạng lưới blockchain (gas fee, miner fee) cần thiết để xác nhận giao dịch.
- **UTXO_Chain**: Blockchain dựa trên mô hình Unspent Transaction Output (Bitcoin, Cardano, BCH).
- **Account_Chain**: Blockchain dựa trên mô hình tài khoản (Ethereum, ERC20, Ripple, TON).
- **Fee_Multiplier**: Hệ số nhân phí mạng lưới cho Account_Chain khi có Service_Fee (giá trị = 2), vì cần phí cho cả giao dịch chính và giao dịch phí dịch vụ.
- **FeeEstimateResult**: Data class chứa kết quả ước tính phí, bao gồm fee, gasLimit, gasPrice, unit, success, error.
- **SendResult**: Data class chứa kết quả gửi coin, bao gồm txHash, success, error.

## Yêu cầu

### Yêu cầu 1: Mở rộng hàm estimateFee hỗ trợ Service Fee

**User Story:** Là một nhà phát triển ứng dụng ví, tôi muốn hàm `estimateFee` trong CommonCoinsManager hỗ trợ tham số phí dịch vụ, để tôi có thể hiển thị tổng phí chính xác cho người dùng trước khi thực hiện giao dịch.

#### Tiêu chí chấp nhận

1. WHEN Service_Address là chuỗi rỗng hoặc null, THE CommonCoinsManager SHALL ước tính phí mạng lưới cho một giao dịch duy nhất (hành vi hiện tại, không thay đổi).
2. WHEN Service_Address hợp lệ và coin thuộc Account_Chain (Ethereum, Arbitrum, Ripple), THE CommonCoinsManager SHALL nhân kết quả ước tính phí mạng lưới với Fee_Multiplier (giá trị 2) để dự trù phí cho cả giao dịch chính và giao dịch phí dịch vụ.
3. WHEN Service_Address hợp lệ và coin là TON, THE CommonCoinsManager SHALL nhân kết quả ước tính phí mạng lưới với Fee_Multiplier (giá trị 2).
4. WHEN Service_Address hợp lệ và coin thuộc UTXO_Chain (Bitcoin, Cardano), THE CommonCoinsManager SHALL truyền Service_Address vào quá trình ước tính phí để tính toán thêm output bổ sung trong giao dịch.
5. THE CommonCoinsManager SHALL giữ nguyên chữ ký hàm `estimateFee` hiện tại tương thích ngược, bổ sung tham số `serviceAddress` và `serviceFee` với giá trị mặc định (null và 0.0).
6. THE CommonCoinsManager SHALL trả về FeeEstimateResult chứa tổng phí mạng lưới ước tính (bao gồm phí cho giao dịch phí dịch vụ nếu có).

### Yêu cầu 2: Mở rộng hàm sendCoin hỗ trợ gửi Service Fee

**User Story:** Là một nhà phát triển ứng dụng ví, tôi muốn hàm `sendCoin` trong CommonCoinsManager tự động gửi phí dịch vụ khi được cấu hình, để quy trình thu phí được tự động hóa.

#### Tiêu chí chấp nhận

1. WHEN Service_Address hợp lệ và Service_Fee lớn hơn 0 và coin thuộc Account_Chain, THE CommonCoinsManager SHALL thực hiện giao dịch phí dịch vụ riêng biệt gửi Service_Fee đến Service_Address sau khi giao dịch chính thành công.
2. WHEN Service_Address hợp lệ và Service_Fee lớn hơn 0 và coin thuộc UTXO_Chain, THE CommonCoinsManager SHALL bao gồm Service_Fee như một output bổ sung đến Service_Address trong cùng giao dịch chính.
3. WHEN Service_Address là chuỗi rỗng hoặc null, THE CommonCoinsManager SHALL chỉ thực hiện giao dịch chính mà không gửi phí dịch vụ.
4. WHEN Service_Fee bằng 0, THE CommonCoinsManager SHALL chỉ thực hiện giao dịch chính mà không gửi phí dịch vụ.
5. IF giao dịch chính thất bại, THEN THE CommonCoinsManager SHALL trả về SendResult với success=false và không thực hiện giao dịch phí dịch vụ.
6. THE CommonCoinsManager SHALL giữ nguyên chữ ký hàm `sendCoin` hiện tại tương thích ngược (tham số `serviceFee` và `serviceAddress` đã tồn tại với giá trị mặc định).

### Yêu cầu 3: Xử lý phí mạng lưới cho giao dịch phí dịch vụ trên Account_Chain

**User Story:** Là một nhà phát triển ứng dụng ví, tôi muốn phí mạng lưới được phân bổ chính xác giữa giao dịch chính và giao dịch phí dịch vụ, để người dùng không bị tính phí vượt quá ước tính.

#### Tiêu chí chấp nhận

1. WHEN estimateFee trả về tổng phí đã nhân đôi cho Account_Chain, THE CommonCoinsManager SHALL chia đôi giá trị Network_Fee khi áp dụng cho từng giao dịch đơn lẻ trong hàm sendCoin.
2. WHEN coin là Ethereum hoặc Arbitrum, THE CommonCoinsManager SHALL sử dụng cùng gasPrice cho cả giao dịch chính và giao dịch phí dịch vụ.
3. WHEN coin là Ripple (XRP), THE CommonCoinsManager SHALL sử dụng phí mạng lưới chuẩn (12 drops hoặc phí động) cho mỗi giao dịch đơn lẻ.
4. WHEN coin là TON, THE CommonCoinsManager SHALL ước tính phí riêng cho giao dịch phí dịch vụ dựa trên BOC thực tế.

### Yêu cầu 4: Kiểm tra đủ số dư

**User Story:** Là một nhà phát triển ứng dụng ví, tôi muốn có hàm kiểm tra số dư đủ để thực hiện giao dịch bao gồm phí dịch vụ, để ngăn chặn giao dịch thất bại do thiếu số dư.

#### Tiêu chí chấp nhận

1. THE CommonCoinsManager SHALL cung cấp hàm `validateSufficientBalance` nhận vào coin, amount, networkFee, serviceFee và balance.
2. WHEN tổng (amount + networkFee + serviceFee) vượt quá balance, THE CommonCoinsManager SHALL trả về kết quả cho biết không đủ số dư kèm thông tin chi tiết (thiếu bao nhiêu).
3. WHEN tổng (amount + networkFee + serviceFee) nhỏ hơn hoặc bằng balance, THE CommonCoinsManager SHALL trả về kết quả cho biết đủ số dư.
4. THE CommonCoinsManager SHALL tính toán tổng chi phí chính xác theo từng loại chain: với UTXO_Chain tổng bao gồm amount + networkFee + serviceFee trong cùng giao dịch; với Account_Chain tổng bao gồm amount + networkFee cho giao dịch chính + serviceFee + networkFee cho giao dịch phí dịch vụ.

### Yêu cầu 5: Miễn phí dịch vụ (Zero/Promotional Fee)

**User Story:** Là một nhà phát triển ứng dụng ví, tôi muốn hệ thống tự động nhận diện khi phí dịch vụ được miễn, để không thực hiện giao dịch phí dịch vụ không cần thiết.

#### Tiêu chí chấp nhận

1. WHEN Service_Address là chuỗi rỗng, THE CommonCoinsManager SHALL gán hasServiceFee = false và bỏ qua toàn bộ logic phí dịch vụ.
2. WHEN Service_Address là null, THE CommonCoinsManager SHALL gán hasServiceFee = false và bỏ qua toàn bộ logic phí dịch vụ.
3. WHEN Service_Fee bằng 0.0, THE CommonCoinsManager SHALL gán hasServiceFee = false và bỏ qua toàn bộ logic phí dịch vụ.
4. WHILE hasServiceFee là false, THE CommonCoinsManager SHALL hoạt động giống hệt hành vi hiện tại (không thay đổi estimateFee và sendCoin).

### Yêu cầu 6: Xử lý lỗi giao dịch phí dịch vụ

**User Story:** Là một nhà phát triển ứng dụng ví, tôi muốn hệ thống xử lý đúng khi giao dịch phí dịch vụ thất bại, để giao dịch chính không bị ảnh hưởng và có thể retry.

#### Tiêu chí chấp nhận

1. IF giao dịch phí dịch vụ thất bại trên Account_Chain, THEN THE CommonCoinsManager SHALL trả về SendResult với txHash của giao dịch chính (đã thành công), success=true, và error chứa thông tin lỗi giao dịch phí dịch vụ.
2. IF giao dịch phí dịch vụ thất bại trên Account_Chain, THEN THE CommonCoinsManager SHALL ghi log cảnh báo chứa txHash giao dịch chính và thông tin lỗi phí dịch vụ.
3. THE CommonCoinsManager SHALL không rollback giao dịch chính khi giao dịch phí dịch vụ thất bại (vì blockchain transactions là không thể đảo ngược).
