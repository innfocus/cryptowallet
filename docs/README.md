# Tài liệu Dự án CryptoWallet

Chào mừng bạn đến với hệ thống tài liệu kỹ thuật của dự án `crypto-wallet-lib`. Hệ thống này được thiết kế để hỗ trợ cả Android và iOS Member dễ dàng tích hợp và hiểu sâu về kiến trúc Blockchain.

## 1. Quy tắc quản lý và đặt tên (Convention)

Để giữ cho tài liệu luôn ngăn nắp và dễ tìm kiếm, chúng ta thống nhất các quy tắc sau:

### Quy tắc đặt tên (Naming Convention)
- **Thư mục:** Luôn dùng **chữ thường (lowercase)**. Ví dụ: `android`, `ios`, `chains`.
- **File tài liệu:** Dùng **kebab-case** (chữ thường, nối bằng dấu gạch ngang `-`). Ví dụ: `usage-guide.md`, `cardano-midnight.md`.
- **Ngoại lệ:** Các file Entry-point quan trọng (như `README.md`, `DEVELOPER_GUIDE.md`) dùng **CHỮ HOA** để luôn hiển thị ở trên cùng của danh sách.

### Cấu trúc thư mục (Directory Structure)
- `architecture/`: Chứa các tài liệu về kiến trúc hệ thống, sơ đồ Sequence, phân tích Migration.
- `android/`: Hướng dẫn tích hợp riêng cho Android (Java/Kotlin).
- `ios/`: Hướng dẫn tích hợp riêng cho iOS (Swift/XCFramework).
- `api/`: Chi tiết tham số, kiểu dữ liệu (Inputs/Outputs) của các module.
- `chains/`: Tài liệu chi tiết kỹ thuật cho từng Blockchain cụ thể (BTC, ETH, ADA, TON...).

## 2. Bản đồ Tài liệu (Documentation Map)

### 🏗️ Kiến trúc & Tổng quan
- [Tổng quan Kiến trúc 5 tầng](architecture/overview.md) - **Nên đọc đầu tiên.**
- [Hướng dẫn cho Developer (KMP)](DEVELOPER_GUIDE.md)
- [Phân tích Migration (Android -> KMP)](architecture/migration-analysis.md)
- [Thiết lập môi trường Local](architecture/local-development.md)

### 📱 Hướng dẫn theo Nền tảng
- [**Dành cho Android:** Cách dùng CoinsManager](android/usage-guide.md)
- [**Dành cho Android:** Tích hợp Cardano (Byron + Shelley)](android/cardano-integration.md)
- [**Dành cho iOS:** Cách dùng Swift async/await](ios/swift-integration.md)
- [**Dành cho iOS:** Tích hợp Cardano (Byron + Shelley)](ios/cardano-integration.md)
- [**Dành cho Android:** Tích hợp Ripple (XRP)](android/ripple-integration.md)
- [**Dành cho iOS:** Tích hợp Ripple (XRP)](ios/ripple-integration.md)
- [Hướng dẫn cho User cuối](architecture/user-guide.md)

### 🔌 API Reference
- [CommonCoinsManager API](api/common-coins-manager.md)
- [Phân tích Service Fee](api/service-fee-analysis.md)

### ⛓️ Blockchain Details
- [Đặc tả Bitcoin (BTC)](chains/bitcoin.md) — BIP-32/39/44/84, SegWit, UTXO, fee estimation
- [Đặc tả Ripple (XRP)](chains/ripple.md) — Base58Ripple, SHA-512Half, JSON-RPC, Destination Tag
- [Tích hợp TON (The Open Network)](chains/ton.md)
- [Đặc tả Cardano Byron](chains/cardano-byron.md) — Icarus V2, Base58, CBOR, ed25519-bip32
- [Đặc tả Cardano Shelley](chains/cardano-shelley.md) — CIP-1852, Bech32, staking, native token
- [Tích hợp Cardano & Midnight](chains/cardano-midnight.md)
- [Đặc tả Centrality (CENNZ)](chains/centrality.md) — SS58, SCALE codec, Sr25519, Substrate extrinsic

---

**Lưu ý:** Khi tạo tài liệu mới, hãy đảm bảo bạn tuân thủ đúng quy tắc đặt tên và sơ đồ thư mục trên để giữ cho hệ thống luôn sạch sẽ.
