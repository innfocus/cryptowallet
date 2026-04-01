# Phân tích chức năng Phí dịch vụ (Service Fee) - FGWallet iOS

Tài liệu này phân tích chi tiết cơ chế hoạt động và các kịch bản sử dụng (Use Cases) cho chức năng phí dịch vụ trong ứng dụng FGWallet.

---

## 1. Phân tích kỹ thuật

Chức năng **Service Fee** là khoản phí bổ sung thu từ người dùng trên mỗi giao dịch chuyển tiền, được quản lý động từ hệ thống Backend.

### Cơ chế tính toán
Phí được tính toán dựa trên đối tượng `MinerFee` nhận được từ `APIManager`:
- **Tham số:** `ratioPrice` (tỉ lệ %), `minPrice` (tối thiểu), `maxPrice` (tối đa).
- **Công thức:** `serviceFee = min(maxPrice, max(minPrice, amount * ratioPrice))`.
- **Đơn vị:** Toàn bộ giá trị cấu hình thường dựa trên tiền pháp định (Fiat) và được quy đổi sang đơn vị Coin tại thời điểm giao dịch.

### Cơ chế thực thi theo nền tảng Blockchain
Hệ thống xử lý phí dịch vụ khác nhau tùy thuộc vào kiến trúc của loại coin đó thông qua hàm `estimateFee`:

1. **Nền tảng UTXO (Bitcoin, Cardano, BCH):**
   - Phí dịch vụ được gộp vào làm **một Output bổ sung** trong cùng một giao dịch.
   - Ước tính phí (`estimateFee`) được tính toán dựa trên số lượng Input/Output của giao dịch thực tế (thêm 1 output sẽ làm tăng nhẹ kích thước transaction và phí).

2. **Nền tảng Account-based (Ethereum, ERC20, Ripple):**
   - Phí dịch vụ được thực hiện như một **giao dịch chuyển khoản riêng biệt**.
   - **Đặc điểm ước tính phí (`estimateFee`):** Hệ thống sẽ **nhân đôi** giá trị ước tính nếu có phí dịch vụ (`hasServiceFee ? 2 : 1`). Điều này là để dự trù phí mạng cho cả 2 giao dịch (Giao dịch chính + Giao dịch phí dịch vụ).
   - Khi thực hiện gửi (`sendCoin`), giá trị này được chia đôi lại để áp dụng làm gas price cho từng giao dịch đơn lẻ.

---

## 2. Phân tích hàm `estimateFee` trong `CoinsManager`

Hàm `estimateFee` đóng vai trò quan trọng nhất trong việc giúp người dùng biết trước tổng chi phí phải trả. Dưới đây là phân tích chi tiết các tham số và logic xử lý:

### Các tham số đầu vào:
- `toAddressStr`: Địa chỉ ví nhận tiền.
- `serAddressStr`: Địa chỉ ví nhận phí dịch vụ (lấy từ cấu hình `MinerFee`).
- `amount`: Số tiền người dùng muốn gửi.
- `paramFee`: Tham số đầu vào tùy chọn (ví dụ: `feePerK` cho Bitcoin hoặc `gasPrice` cho Ethereum).
- `serviceFee`: Giá trị phí dịch vụ đã được tính toán ở mức View Controller.

### Logic xử lý cho từng mạng lưới:

#### 1. Bitcoin & Bitcoin Cash
Hệ thống gọi đến các module bridge (`Gbtc` / `Gbch`). Việc ước tính phí cho UTXO rất phức tạp vì nó phụ thuộc vào số lượng "mảnh" tiền (Unspent Inputs) mà ví đang giữ. Việc thêm `serAddressStr` sẽ buộc thuật toán tính toán thêm phí cho 1 Output bổ sung.

#### 2. Ethereum & ERC20 Token
Công thức xử lý trực tiếp trong code:
- **Ethereum:** `estimateFee = gasPrice * 21,000 * (hasServiceFee ? 2 : 1)`
- **ERC20:** `estimateFee = gasPrice * 150,000 * (hasServiceFee ? 2 : 1)`
- **Lưu ý:** Việc nhân 2 giúp hiển thị cho người dùng tổng số ETH họ sẽ bị trừ khỏi tài khoản để hoàn thành cả 2 lệnh chuyển tiền (tiền chính và tiền phí).

#### 3. Ripple (XRP)
Tương tự Ethereum, phí dự toán cho 1 transaction chuẩn sẽ được nhân đôi nếu cấu hình yêu cầu thu phí dịch vụ: `fee * (hasServiceFee ? 2 : 1)`.

#### 4. Cardano (ADA)
Gọi bridge `Gada` với các tham số đặc thù như `networkMinFee` (thường là 0.17 ADA cộng thêm kích thước TX).

---

## 3. Các Use Case chi tiết

### **UC-01: Tính toán phí dịch vụ tự động**
- **Mục tiêu:** Hiển thị phí chính xác cho người dùng khi họ nhập số tiền gửi.
- **Kích hoạt:** Khi người dùng thay đổi giá trị trong ô nhập số tiền (`tfCoin` hoặc `tfCurrency`).
- **Luồng xử lý:**
    1. Kiểm tra cấu hình `MinerFee` từ bộ nhớ đệm (Cache).
    2. Nếu `serviceAddr` hợp lệ, thực hiện tính toán theo công thức phí.
    3. Cập nhật giao diện thông báo tổng phí (`totalFee = networkFee + serviceFee`).

### **UC-02: Kiểm tra khả năng chi trả (Insufficient Funds)**
- **Mục tiêu:** Ngăn chặn lỗi giao dịch do không đủ số dư để trả phí.
- **Luồng xử lý:**
    1. Người dùng nhấn "Continue".
    2. Hệ thống tính tổng: `Total = Số tiền gửi + Phí mạng lưới + Phí dịch vụ`.
    3. So sánh với `Balance` hiện có. Nếu không đủ, hiển thị cảnh báo và dừng giao dịch.

### **UC-04: Thực hiện giao dịch song song (Ethereum/XRP)**
- **Mục tiêu:** Tự động hóa việc thu phí sau khi chuyển tiền thành công.
- **Luồng xử lý:**
    1. Người dùng xác nhận gửi tiền cuối cùng.
    2. Gọi `sendCoin` trong `CoinsManager`.
    3. Thực hiện giao dịch chuyển tiền chính. 
    4. Ngay lập tức khởi tạo giao dịch thứ hai gửi `serviceFee` đến `serviceAddr` của hệ thống.
    5. Lưu thông tin ghi chú (Memo) "Service fee for xxx" vào cơ sở dữ liệu local để người dùng theo dõi lịch sử.

### **UC-05: Tùy biến phí theo mạng lưới (Miner Fee Type)**
- **Mục tiêu:** Điều chỉnh phí mạng lưới mà không ảnh hưởng đến phí dịch vụ.
- **Luồng xử lý:**
    1. Người dùng chọn mức độ ưu tiên (Low, Medium, High).
    2. `networkFee` thay đổi dựa trên tham số mạng lưới.
    3. `serviceFee` vẫn giữ nguyên theo tỉ lệ cấu hình ban đầu.

### **UC-06: Miễn phí dịch vụ (Promotional/Zero Fee)**
- **Mục tiêu:** Có thể cấu hình miễn phí cho người dùng trong các chiến dịch marketing.
- **Luồng xử lý:**
    1. Backend cấu hình `serviceAddr` là chuỗi rỗng.
    2. Hệ thống nhận diện `hasServiceFee = false`.
    3. Gán `serviceFee = 0` và chỉ hiển thị phí mạng lưới.

---
