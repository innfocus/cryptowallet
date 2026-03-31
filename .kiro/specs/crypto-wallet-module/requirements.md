# Tài liệu Yêu cầu Tổng hợp — Crypto Wallet Module

## Giới thiệu

`crypto-wallet-lib` là thư viện Kotlin Multiplatform (KMP) hỗ trợ đa blockchain cho ví tiền điện tử. Thư viện cung cấp khả năng quản lý ví HD (BIP32/39/44), tạo địa chỉ, truy vấn số dư, lịch sử giao dịch, gửi/nhận tiền, quản lý token/NFT, staking, và bridge cross-chain. Hỗ trợ các nền tảng: Android, iOS (iosX64, iosArm64, iosSimulatorArm64), và JVM.

### Phạm vi tổng thể

Tài liệu này tổng hợp toàn bộ yêu cầu của module `crypto-wallet-lib`, bao gồm:

1. **Kiến trúc lõi (Core Architecture)**: HD Wallet, interface hierarchy, factory pattern, facade pattern
2. **Blockchain Chains**: Bitcoin, Ethereum/Arbitrum, Cardano, TON, Ripple/XRP, Midnight, Centrality/CennzNet
3. **Tính năng nâng cao**: Token management, NFT, Staking, Cross-chain Bridge
4. **Hạ tầng kỹ thuật**: CBOR serialization, SCALE encoding, Ktor networking, kotlinx-serialization, error handling

### Trạng thái hiện tại

| Module | Trạng thái | Source Set | Ghi chú |
|---|---|---|---|
| HD Wallet (BIP32/39/44) | ✅ Đã migrate | commonMain | |
| Bitcoin | ✅ Đã migrate | commonMain | `transfer()` đã implement |
| Ethereum/Arbitrum | ✅ Đã migrate (JSON-RPC qua Ktor) | commonMain | NFT: `getNFTs()` + `transferNFT()` đã implement |
| Cardano (Byron + Shelley + Native Token) | ✅ Đã migrate | commonMain | |
| TON | ✅ Đã migrate | commonMain | `getNFTs()` + `transferNFT()` + INFTManager đã implement |
| Ripple/XRP | ✅ Đã migrate | commonMain | |
| Midnight Network | ✅ Đã migrate | commonMain | |
| Centrality/CennzNet | ✅ Đã migrate | commonMain | |
| Staking (Cardano + TON) | ✅ Đã implement | commonMain | |
| Bridge (Cardano↔Midnight, Ethereum↔Arbitrum) | ⚠️ Cấu trúc xong, simulated response | commonMain | Chưa kết nối API thật |
| CBOR Serializer | ✅ Custom implementation | commonMain | |
| CommonCoinsManager (Facade) | ✅ Đã implement | commonMain | |
| ChainManagerFactory | ✅ Đã implement | commonMain | |
| Testing (Property + Unit) | ✅ Đã implement | commonTest | P1-P13 property tests + unit tests |

## Glossary

- **Wallet_Library**: Module `crypto-wallet-lib`, thư viện KMP hỗ trợ đa blockchain
- **HD_Wallet**: Hierarchical Deterministic Wallet theo chuẩn BIP32/39/44
- **BIP39_Module**: Module xử lý mnemonic phrase (tạo, validate, chuyển thành seed)
- **BIP32_Module**: Module xử lý key derivation (private key, public key, chain code)
- **BIP44_Module**: Module xử lý address generation và HD wallet orchestration
- **CommonCoinsManager**: Facade class thống nhất trong commonMain quản lý tất cả coin operations
- **ChainManagerFactory**: Factory tạo chain manager theo NetworkName
- **IWalletManager**: Interface cơ bản cho tất cả chain manager (getAddress, getBalance, transfer, getTransactionHistory)
- **ITokenManager**: Interface cho token operations (ERC-20, Jetton, Cardano native token)
- **INFTManager**: Interface cho NFT operations (TEP-62, ERC-721)
- **IStakingManager**: Interface cho staking operations (stake, unstake, getStakingRewards, getStakingBalance)
- **IBridgeManager**: Interface cho cross-chain bridge operations (bridgeAsset, getBridgeStatus)
- **IFeeEstimator**: Interface cho fee estimation (estimateFee, getGasPrice)
- **BridgeManagerFactory**: Factory tạo bridge manager cho các cặp chain được hỗ trợ
- **CoinNetwork**: Class cấu hình network cho mỗi coin (API keys, endpoints, mainnet/testnet)
- **NetworkName**: Enum định nghĩa các blockchain network: BTC, ETHEREUM, ARBITRUM, TON, CARDANO, MIDNIGHT, XRP, CENTRALITY
- **ACTCoin**: Enum định nghĩa các loại coin với metadata (symbol, unit, algorithm, fee, regex)
- **Algorithm**: Enum cho thuật toán ký: Ed25519, Secp256k1, Sr25519
- **Cardano_Module**: Phần code xử lý Cardano blockchain (CardanoManager, CardanoAddress, CardanoTransaction)
- **Byron_Address**: Địa chỉ Cardano era Byron, mã hóa Base58 với CBOR tag và CRC32 checksum
- **Shelley_Address**: Địa chỉ Cardano era Shelley, mã hóa Bech32 với prefix `addr` hoặc `addr_test`
- **CIP_1852**: Cardano Improvement Proposal cho HD wallet derivation path: `m/1852'/1815'/account'/role/index`
- **Native_Token**: Token phát hành trên Cardano blockchain theo chuẩn multi-asset (policy ID + asset name)
- **CBOR_Serializer**: Component serialize/deserialize dữ liệu theo chuẩn CBOR (RFC 7049)
- **CBOR_Pretty_Printer**: Component format CBOR data thành dạng đọc được để debug
- **VKey_Witness**: Verification key witness trong Shelley transaction
- **Bootstrap_Witness**: Witness format cho Byron-era addresses trong Shelley transactions
- **Multi_Asset_Output**: Transaction output chứa ADA và một hoặc nhiều Native Tokens
- **Delegation_Certificate**: Shelley delegation certificate dùng để delegate ADA cho stake pool
- **Staking_Key**: Ed25519 key pair dẫn xuất từ path `m/1852'/1815'/account'/2/0`
- **Stake_Pool**: Node validator trên Cardano network nhận delegation từ người dùng
- **Midnight_Network**: Sidechain bảo mật dữ liệu của Cardano, sử dụng zero-knowledge proofs
- **Midnight_Module**: Phần code xử lý Midnight Network (MidnightManager, MidnightAddress)
- **tDUST**: Token native của Midnight Network
- **TON_Module**: Phần code xử lý TON blockchain (TonManager)
- **Nominator_Pool**: Smart contract trên TON cho phép người dùng stake TON vào validator
- **Tonstakers**: Liquid staking protocol trên TON, phát hành tsTON token
- **Bemo**: Liquid staking protocol trên TON, phát hành stTON token
- **Ethereum_Module**: Phần code xử lý Ethereum/Arbitrum blockchain (EthereumManager)
- **InfuraRpcService**: Service JSON-RPC cho Ethereum/Arbitrum qua Infura
- **ExplorerRpcService**: Service query Etherscan/Arbiscan API
- **Bitcoin_Module**: Phần code xử lý Bitcoin blockchain (BitcoinManager)
- **Ripple_Module**: Phần code xử lý Ripple/XRP blockchain (RippleManager)
- **Centrality_Module**: Phần code xử lý CennzNet blockchain (CentralityManager)
- **ScaleCodec**: Module mã hóa SCALE (Simple Concatenated Aggregate Little-Endian) cho Substrate
- **ExtrinsicBuilder**: Class xây dựng extrinsic Substrate (payload, ký, mã hóa)
- **SS58**: Định dạng địa chỉ của Substrate (tương tự Base58Check của Bitcoin)
- **Bridge_Status**: Trạng thái bridge transaction: `pending`, `confirming`, `completed`, `failed`
- **Bridge_Fee**: Phí cho việc bridge tài sản giữa hai chain
- **Ktor**: HTTP client multiplatform dùng cho tất cả networking
- **bitcoin_kmp**: Thư viện `fr.acinq.bitcoin:bitcoin-kmp` cung cấp MnemonicCode, DeterministicWallet, Crypto
- **TransationData**: Model giao dịch chung cho tất cả chain
- **TransferResponseModel**: Model kết quả gửi giao dịch (txHash, success, error)

## Requirements

---

### Requirement 1: Kiến trúc Kotlin Multiplatform

**User Story:** Là developer, tôi muốn toàn bộ thư viện hoạt động trên Android, iOS và JVM từ một codebase duy nhất trong commonMain, để giảm chi phí bảo trì và đảm bảo tính nhất quán.

#### Acceptance Criteria

1. THE Wallet_Library SHALL đặt toàn bộ business logic trong source set `commonMain` để hỗ trợ Android, iOS (iosX64, iosArm64, iosSimulatorArm64), và JVM
2. THE Wallet_Library SHALL sử dụng Ktor HTTP client cho tất cả networking thay vì Retrofit/OkHttp
3. THE Wallet_Library SHALL sử dụng `kotlinx-serialization-json` cho tất cả JSON parsing thay vì Gson
4. THE Wallet_Library SHALL sử dụng `fr.acinq.bitcoin:bitcoin-kmp` cho BIP32/39 key derivation thay vì Spongy Castle
5. THE Wallet_Library SHALL sử dụng `com.ionspin.kotlin:bignum` thay vì `java.math.BigInteger` cho số lớn
6. THE Wallet_Library SHALL sử dụng `co.touchlab:kermit` cho logging thay vì `android.util.Log`
7. WHEN Wallet_Library được build cho bất kỳ target nào (Android, iOS, JVM), THE Wallet_Library SHALL compile thành công mà không có lỗi platform-specific
8. THE Wallet_Library SHALL loại bỏ dependency vào `android.util.Base64`, `java.io.Serializable`, `java.util.Date` trong commonMain code

### Requirement 2: HD Wallet — BIP39 Mnemonic

**User Story:** Là người dùng ví, tôi muốn tạo và khôi phục ví từ mnemonic phrase, để quản lý tài sản an toàn trên nhiều blockchain.

#### Acceptance Criteria

1. THE BIP39_Module SHALL tạo mnemonic mới với các strength: 128, 160, 192, 224, 256 bit
2. THE BIP39_Module SHALL validate mnemonic và trả về entropy string
3. THE BIP39_Module SHALL tạo deterministic seed từ mnemonic và passphrase
4. THE BIP39_Module SHALL hỗ trợ detect ngôn ngữ mnemonic (ACTLanguages)
5. THE BIP39_Module SHALL hỗ trợ correct mnemonic (sửa checksum)
6. FOR ALL mnemonic hợp lệ, `deterministicSeedString(mnemonic)` SHALL tạo ra cùng seed hex string trên mọi platform (round-trip property)

### Requirement 3: HD Wallet — BIP32 Key Derivation

**User Story:** Là developer, tôi muốn derive private key và public key từ seed theo chuẩn BIP32, để tạo địa chỉ cho mọi blockchain.

#### Acceptance Criteria

1. THE BIP32_Module SHALL hỗ trợ key derivation cho algorithm Secp256k1 (Bitcoin, Ethereum, Ripple) sử dụng bitcoin-kmp
2. THE BIP32_Module SHALL hỗ trợ key derivation cho algorithm Ed25519 (Cardano, TON, Midnight)
3. THE BIP32_Module SHALL hỗ trợ key derivation cho algorithm Sr25519 (Centrality)
4. THE BIP32_Module SHALL tạo public key từ private key mà không dùng thư viện Android-only
5. THE BIP32_Module SHALL tạo Base58-encoded extended key mà không dùng java.nio.ByteBuffer
6. FOR ALL derivation path hợp lệ, private key và public key SHALL giống byte-for-byte trên mọi platform

### Requirement 4: HD Wallet — BIP44 Address Generation

**User Story:** Là developer, tôi muốn tạo địa chỉ cho mọi blockchain từ HD wallet, để hỗ trợ đa coin trong một ví duy nhất.

#### Acceptance Criteria

1. THE BIP44_Module SHALL tạo address cho Bitcoin (Base58Check, Native SegWit Bech32)
2. THE BIP44_Module SHALL tạo address cho Ethereum (EIP-55 checksum)
3. THE BIP44_Module SHALL tạo address cho Ripple (Base58 Ripple alphabet)
4. THE BIP44_Module SHALL tạo address cho Cardano (Bech32 Shelley, Base58 Byron)
5. THE BIP44_Module SHALL tạo address cho TON (Base64url)
6. THE BIP44_Module SHALL tạo address cho Midnight (Bech32 với prefix midnight1)
7. THE BIP44_Module SHALL tạo address cho Centrality (SS58)
8. FOR ALL coin type và mnemonic hợp lệ, address được tạo SHALL giống nhau trên mọi platform

### Requirement 5: Interface Hierarchy — Phân tách theo Capability

**User Story:** Là developer, tôi muốn mỗi blockchain chỉ implement interface phù hợp với capability của nó, để code sạch và dễ mở rộng.

#### Acceptance Criteria

1. THE Wallet_Library SHALL cung cấp interface `IWalletManager` với methods: getAddress, getBalance, getTransactionHistory, transfer, getChainId
2. THE Wallet_Library SHALL cung cấp interface `ITokenManager` với methods: getTokenBalance, getTokenTransactionHistory, transferToken
3. THE Wallet_Library SHALL cung cấp interface `INFTManager` với methods: getNFTs, transferNFT
4. THE Wallet_Library SHALL cung cấp interface `IFeeEstimator` với methods: estimateFee, getGasPrice
5. THE Wallet_Library SHALL cung cấp interface `IStakingManager` với methods: stake, unstake, getStakingRewards, getStakingBalance
6. THE Wallet_Library SHALL cung cấp interface `IBridgeManager` với methods: bridgeAsset, getBridgeStatus
7. THE Wallet_Library SHALL đảm bảo mỗi chain manager chỉ implement interface phù hợp theo bảng capability matrix

### Requirement 6: ChainManagerFactory — Factory Pattern

**User Story:** Là developer, tôi muốn tạo chain manager qua factory pattern, để thêm blockchain mới chỉ cần thêm case mà không sửa code hiện có.

#### Acceptance Criteria

1. THE ChainManagerFactory SHALL tạo IWalletManager cho tất cả NetworkName: BTC, ETHEREUM, ARBITRUM, TON, CARDANO, MIDNIGHT, XRP, CENTRALITY
2. THE ChainManagerFactory SHALL cung cấp method `createTokenManager` trả về ITokenManager cho chain hỗ trợ token (Ethereum, Cardano, TON)
3. THE ChainManagerFactory SHALL cung cấp method `createNFTManager` trả về INFTManager cho chain hỗ trợ NFT (Ethereum, TON)
4. THE ChainManagerFactory SHALL cung cấp method `createFeeEstimator` trả về IFeeEstimator cho chain hỗ trợ fee estimation (Ethereum, Arbitrum)
5. THE ChainManagerFactory SHALL cung cấp method `createStakingManager` trả về IStakingManager cho chain hỗ trợ staking (Cardano, TON)
6. THE ChainManagerFactory SHALL cung cấp method `createBridgeManager` delegate sang BridgeManagerFactory cho các cặp chain được hỗ trợ
7. WHEN `createStakingManager` được gọi cho chain không hỗ trợ staking, THE ChainManagerFactory SHALL trả về `null`
8. WHEN `createBridgeManager` được gọi cho cặp chain không hỗ trợ, THE ChainManagerFactory SHALL trả về `null`

### Requirement 7: CommonCoinsManager — Unified Facade

**User Story:** Là developer, tôi muốn một API duy nhất để tương tác với tất cả blockchain, để tích hợp dễ dàng vào ứng dụng.

#### Acceptance Criteria

1. THE CommonCoinsManager SHALL cung cấp suspend functions cho: getAddress, getBalance, getTransactionHistory, transfer cho mỗi coin
2. THE CommonCoinsManager SHALL nhận mnemonic làm constructor parameter và tạo wallet manager lazy cho mỗi coin
3. THE CommonCoinsManager SHALL hỗ trợ token operations (getTokenBalance, sendToken) cho các coin có token
4. THE CommonCoinsManager SHALL hỗ trợ NFT operations (getNFTs, transferNFT) cho các coin có NFT
5. THE CommonCoinsManager SHALL hỗ trợ staking operations (stake, unstake, getStakingRewards, getStakingBalance) cho Cardano và TON
6. THE CommonCoinsManager SHALL hỗ trợ bridge operations (bridgeAsset, getBridgeStatus) cho các cặp chain được hỗ trợ
7. THE CommonCoinsManager SHALL cung cấp capability checking: supportsTokens, supportsNFTs, supportsFeeEstimation, supportsStaking, supportsBridge
8. IF operation được gọi cho chain không hỗ trợ, THEN THE CommonCoinsManager SHALL trả về error `UnsupportedOperation`

### Requirement 8: ACTCoin Enum — Metadata cho mỗi Coin

**User Story:** Là developer, tôi muốn enum ACTCoin chứa đầy đủ metadata cho mỗi coin, để sử dụng thống nhất trên mọi platform.

#### Acceptance Criteria

1. THE ACTCoin enum SHALL chứa tất cả coin: Bitcoin, Ethereum, Cardano, XCoin, Ripple, Centrality, TON, Midnight
2. THE ACTCoin enum SHALL cung cấp methods: nameCoin, symbolName, minimumValue, unitValue, regex, algorithm, baseApiUrl, feeDefault, minimumAmount, supportMemo, allowNewAddress
3. THE ACTCoin enum SHALL sử dụng `kotlin.Double` cho unitValue thay vì `java.math.BigDecimal`
4. THE ACTCoin enum SHALL cung cấp enum `Algorithm` (Ed25519, Secp256k1, Sr25519) và enum `Change` (External, Internal)
5. FOR ALL giá trị ACTCoin, metadata SHALL nhất quán trên mọi platform

### Requirement 9: CBOR Serialization đa nền tảng

**User Story:** Là developer, tôi muốn CBOR serialization hoạt động trên mọi nền tảng, để transaction building cho Cardano không phụ thuộc Android.

#### Acceptance Criteria

1. THE CBOR_Serializer SHALL serialize các kiểu dữ liệu: unsigned integer, negative integer, byte string, text string, array, map, và tag theo RFC 7049
2. THE CBOR_Serializer SHALL deserialize CBOR byte array thành các kiểu dữ liệu tương ứng
3. THE CBOR_Pretty_Printer SHALL format CBOR data thành chuỗi text có cấu trúc để debug
4. FOR ALL giá trị CBOR hợp lệ, serialize rồi deserialize SHALL tạo ra giá trị tương đương với giá trị gốc (round-trip property)
5. THE CBOR_Serializer SHALL hỗ trợ CBOR tag 24 (embedded CBOR) cho Byron address encoding
6. THE CBOR_Serializer SHALL hỗ trợ indefinite-length encoding cho transaction arrays
7. THE CBOR_Serializer SHALL hỗ trợ canonical (deterministic) encoding

### Requirement 10: Bitcoin — Quản lý ví và giao dịch

**User Story:** Là người dùng ví, tôi muốn gửi/nhận Bitcoin, để quản lý tài sản BTC.

#### Acceptance Criteria

1. THE Bitcoin_Module SHALL tạo Bitcoin address (Base58Check và Native SegWit Bech32) từ HD wallet
2. THE Bitcoin_Module SHALL truy vấn balance cho một hoặc nhiều address qua BitcoinApiService (Ktor)
3. THE Bitcoin_Module SHALL truy vấn lịch sử giao dịch
4. THE Bitcoin_Module SHALL tạo và gửi Bitcoin transaction
5. THE Bitcoin_Module SHALL hỗ trợ cả mainnet và testnet thông qua Config
6. THE Bitcoin_Module SHALL implement IWalletManager interface

### Requirement 11: Ethereum/Arbitrum — Quản lý ví, Token và NFT

**User Story:** Là người dùng ví, tôi muốn tương tác với Ethereum và Arbitrum, để sử dụng DeFi và NFT.

#### Acceptance Criteria

1. THE Ethereum_Module SHALL implement IWalletManager, ITokenManager, INFTManager, IFeeEstimator
2. THE Ethereum_Module SHALL sử dụng InfuraRpcService cho JSON-RPC calls: eth_getBalance, eth_sendRawTransaction, eth_gasPrice, eth_estimateGas, eth_chainId
3. THE Ethereum_Module SHALL sử dụng ExplorerRpcService cho transaction history và token balance
4. THE Ethereum_Module SHALL hỗ trợ ERC-20 token operations (balance, transfer)
5. THE Ethereum_Module SHALL hỗ trợ ERC-721 NFT operations (list, transfer)
6. THE Ethereum_Module SHALL hỗ trợ fee estimation (gas price, gas limit)
7. THE Ethereum_Module SHALL hỗ trợ cả Ethereum mainnet/testnet và Arbitrum mainnet/testnet

### Requirement 12: Cardano — Byron-era Address

**User Story:** Là người dùng ví, tôi muốn tiếp tục sử dụng Byron-era addresses, để tương thích ngược với các ví cũ.

#### Acceptance Criteria

1. THE Cardano_Module SHALL tạo Byron_Address từ extended public key và chain code theo chuẩn Cardano Byron
2. WHEN một chuỗi ký tự được cung cấp, THE Cardano_Module SHALL xác định chuỗi đó có phải Byron_Address hợp lệ hay không bằng cách kiểm tra Base58 decode, CBOR structure và CRC32 checksum
3. THE Cardano_Module SHALL hỗ trợ Byron derivation scheme V1 và V2
4. IF một Byron_Address không hợp lệ được cung cấp, THEN THE Cardano_Module SHALL trả về lỗi mô tả rõ nguyên nhân

### Requirement 13: Cardano — Shelley-era Address

**User Story:** Là người dùng ví, tôi muốn sử dụng Shelley-era addresses, để tận dụng tính năng staking.

#### Acceptance Criteria

1. THE Cardano_Module SHALL tạo Shelley_Address theo chuẩn CIP_1852 với derivation path `m/1852'/1815'/account'/role/index`
2. THE Cardano_Module SHALL mã hóa Shelley_Address dưới dạng Bech32 với prefix `addr` cho mainnet và `addr_test` cho testnet
3. WHEN một chuỗi ký tự được cung cấp, THE Cardano_Module SHALL xác định chuỗi đó có phải Shelley_Address hợp lệ hay không
4. THE Cardano_Module SHALL hỗ trợ các loại Shelley address: base address, enterprise address, reward address
5. THE Cardano_Module SHALL tạo staking key từ derivation path `m/1852'/1815'/account'/2/0`
6. IF một Shelley_Address không hợp lệ được cung cấp, THEN THE Cardano_Module SHALL trả về lỗi mô tả rõ nguyên nhân

### Requirement 14: Cardano — Shelley Transaction và Signing

**User Story:** Là người dùng ví, tôi muốn gửi ADA bằng Shelley transactions, để giao dịch với phí thấp hơn và bảo mật tốt hơn.

#### Acceptance Criteria

1. THE Cardano_Module SHALL tạo Shelley transaction body với các trường: inputs, outputs, fee, ttl, optional metadata hash, optional certificates
2. THE Cardano_Module SHALL tạo VKey_Witness cho Shelley_Address bằng cách ký transaction hash với Ed25519 private key
3. THE Cardano_Module SHALL tạo Bootstrap_Witness cho Byron_Address trong cùng một transaction
4. WHEN transaction chứa cả Shelley và Byron inputs, THE Cardano_Module SHALL tạo witness set chứa cả VKey witnesses (key 0) và Bootstrap witnesses (key 2)
5. THE CBOR_Serializer SHALL serialize Shelley transaction theo đúng chuẩn Cardano CBOR encoding
6. FOR ALL Shelley transactions hợp lệ, serialize rồi deserialize SHALL tạo ra transaction tương đương với transaction gốc (round-trip property)

### Requirement 15: Cardano — Native Tokens

**User Story:** Là người dùng ví, tôi muốn gửi và nhận Cardano Native Tokens, để quản lý tài sản đa dạng trên Cardano.

#### Acceptance Criteria

1. THE Cardano_Module SHALL hỗ trợ Multi_Asset_Output chứa ADA và một hoặc nhiều Native_Token trong cùng một transaction output
2. THE Cardano_Module SHALL xác định Native_Token bằng policy ID (28 bytes) và asset name (tối đa 32 bytes)
3. WHEN tạo transaction chứa Native_Token, THE Cardano_Module SHALL tính minimum ADA required cho mỗi output theo quy tắc min-UTXO-value
4. THE Cardano_Module SHALL truy vấn danh sách Native_Token và metadata của một address từ Cardano backend
5. WHEN gửi Native_Token, THE Cardano_Module SHALL chọn UTXOs phù hợp chứa đủ token amount và ADA cho phí giao dịch
6. IF số lượng Native_Token không đủ trong UTXOs, THEN THE Cardano_Module SHALL trả về lỗi mô tả rõ số lượng thiếu

### Requirement 16: Cardano — API Service (Ktor-based)

**User Story:** Là developer, tôi muốn Cardano API service sử dụng Ktor, để hoạt động trên mọi nền tảng KMP.

#### Acceptance Criteria

1. THE Cardano_Module SHALL truy vấn UTXO set cho một hoặc nhiều addresses qua CardanoApiService
2. THE Cardano_Module SHALL truy vấn lịch sử giao dịch cho một hoặc nhiều addresses
3. THE Cardano_Module SHALL gửi signed transaction lên Cardano network
4. THE Cardano_Module SHALL truy vấn thông tin block mới nhất (epoch, slot, hash, height)
5. THE Cardano_Module SHALL truy vấn protocol parameters (min fee coefficients, min UTXO value)
6. THE Cardano_Module SHALL hỗ trợ cấu hình API endpoint (Blockfrost, Koios, hoặc custom backend)
7. IF Cardano backend trả về lỗi HTTP, THEN THE Cardano_Module SHALL trả về error object chứa HTTP status code và error message
8. IF kết nối đến Cardano backend thất bại, THEN THE Cardano_Module SHALL trả về error object mô tả lỗi kết nối

### Requirement 17: Cardano — Staking (Delegation)

**User Story:** Là người dùng ví, tôi muốn delegate ADA cho stake pool, để nhận staking rewards mà không cần chạy node.

#### Acceptance Criteria

1. WHEN người dùng cung cấp pool address và số lượng ADA, THE Cardano_Module SHALL tạo Shelley transaction chứa Delegation_Certificate
2. THE Cardano_Module SHALL ký Delegation_Certificate bằng cả payment key và Staking_Key
3. WHEN delegation transaction được submit thành công, THE Cardano_Module SHALL trả về transaction hash
4. IF Stake_Pool address không hợp lệ, THEN THE Cardano_Module SHALL trả về lỗi mô tả rõ pool không tồn tại
5. IF số dư ADA không đủ cho phí giao dịch và deposit (2 ADA), THEN THE Cardano_Module SHALL trả về lỗi mô tả rõ số dư hiện tại và số lượng yêu cầu

### Requirement 18: Cardano — Undelegation và Query Rewards

**User Story:** Là người dùng ví, tôi muốn rút delegation và xem staking rewards, để quản lý staking hiệu quả.

#### Acceptance Criteria

1. WHEN người dùng yêu cầu undelegate, THE Cardano_Module SHALL tạo Shelley transaction chứa deregistration certificate
2. THE Cardano_Module SHALL ký deregistration transaction bằng cả payment key và Staking_Key
3. WHEN undelegation hoàn tất, THE Cardano_Module SHALL hoàn trả 2 ADA deposit về payment address
4. WHEN người dùng cung cấp staking address, THE Cardano_Module SHALL truy vấn tổng staking rewards tích lũy và delegation status (pool ID, ADA đang stake, epoch bắt đầu)
5. THE Cardano_Module SHALL trả về staking rewards dưới dạng ADA (chia cho 1,000,000 từ lovelace)
6. IF wallet chưa delegate cho bất kỳ pool nào, THEN THE Cardano_Module SHALL trả về delegation status rỗng với rewards bằng 0

### Requirement 19: Midnight Network — Quản lý ví và giao dịch tDUST

**User Story:** Là người dùng ví, tôi muốn kết nối với Midnight Network, để sử dụng các tính năng bảo mật dữ liệu.

#### Acceptance Criteria

1. THE Midnight_Module SHALL tạo Midnight wallet address từ mnemonic seed phrase
2. THE Midnight_Module SHALL truy vấn số dư tDUST cho một address
3. THE Midnight_Module SHALL truy vấn lịch sử giao dịch tDUST cho một address
4. THE Midnight_Module SHALL tạo và ký transaction gửi tDUST từ address này sang address khác
5. WHEN gửi tDUST thành công, THE Midnight_Module SHALL trả về transaction hash
6. IF số dư tDUST không đủ, THEN THE Midnight_Module SHALL trả về lỗi mô tả rõ số dư hiện tại và số lượng yêu cầu
7. THE Midnight_Module SHALL kết nối với Midnight Network node qua API endpoint cấu hình được
8. WHEN Midnight Network node không khả dụng, THE Midnight_Module SHALL trả về lỗi kết nối với thông tin chi tiết

### Requirement 20: TON — Quản lý ví, Jetton Token và NFT

**User Story:** Là người dùng ví, tôi muốn gửi/nhận TON, Jetton tokens và NFT, để tương tác đầy đủ với TON blockchain.

#### Acceptance Criteria

1. THE TON_Module SHALL implement IWalletManager, ITokenManager, INFTManager
2. THE TON_Module SHALL tạo TON wallet address từ mnemonic (W4/W5 wallet contract)
3. THE TON_Module SHALL truy vấn balance, lịch sử giao dịch, và gửi TON
4. THE TON_Module SHALL hỗ trợ Jetton token operations (balance, transfer) theo TEP-74
5. THE TON_Module SHALL hỗ trợ NFT operations (list, transfer) theo TEP-62
6. THE TON_Module SHALL hỗ trợ memo/comment trong giao dịch

### Requirement 21: TON — Staking (Nominator Pool, Tonstakers, Bemo)

**User Story:** Là người dùng ví, tôi muốn stake TON qua nhiều protocol, để nhận staking rewards.

#### Acceptance Criteria

1. WHEN người dùng cung cấp Nominator_Pool address và số lượng TON, THE TON_Module SHALL tạo signed message với op-code `0x4e73746b` để deposit
2. WHEN người dùng cung cấp Tonstakers master address, THE TON_Module SHALL tạo signed transfer message để deposit
3. WHEN người dùng cung cấp Bemo master address, THE TON_Module SHALL tạo signed transfer message để deposit
4. THE TON_Module SHALL truy vấn staking balance cho mỗi loại pool (Nominator, Tonstakers, Bemo)
5. THE TON_Module SHALL implement IStakingManager interface
6. WHEN `stake` được gọi, THE TON_Module SHALL xác định loại pool và delegate sang method tương ứng
7. WHEN `unstake` được gọi, THE TON_Module SHALL trả về lỗi `UnsupportedOperation` vì TON staking protocols không hỗ trợ unstake trực tiếp
8. IF pool address không hợp lệ, THEN THE TON_Module SHALL trả về lỗi mô tả rõ pool không hợp lệ
9. IF số dư TON không đủ, THEN THE TON_Module SHALL trả về lỗi mô tả rõ số dư hiện tại và số lượng yêu cầu

### Requirement 22: Ripple/XRP — Quản lý ví và giao dịch

**User Story:** Là người dùng ví, tôi muốn gửi/nhận XRP, để quản lý tài sản Ripple.

#### Acceptance Criteria

1. THE Ripple_Module SHALL implement IWalletManager interface
2. THE Ripple_Module SHALL tạo XRP address (Base58 Ripple alphabet) từ HD wallet
3. THE Ripple_Module SHALL truy vấn XRP balance qua JSON-RPC (account_info)
4. THE Ripple_Module SHALL truy vấn lịch sử giao dịch với pagination (account_tx)
5. THE Ripple_Module SHALL submit signed transaction qua JSON-RPC (submit)
6. THE Ripple_Module SHALL sử dụng Ktor HTTP client và kotlinx-serialization
7. THE Ripple_Module SHALL hỗ trợ memo trong giao dịch

### Requirement 23: Centrality/CennzNet — Quản lý ví và giao dịch

**User Story:** Là người dùng ví, tôi muốn gửi/nhận CENNZ/CPAY trên CennzNet, để quản lý tài sản Centrality.

#### Acceptance Criteria

1. THE Centrality_Module SHALL implement IWalletManager interface
2. THE Centrality_Module SHALL tạo SS58 address từ mnemonic qua CentralityApiService
3. THE Centrality_Module SHALL truy vấn balance (chia cho BASE_UNIT = 10000) qua scanAccount API
4. THE Centrality_Module SHALL truy vấn lịch sử giao dịch qua scanTransfers API
5. THE Centrality_Module SHALL orchestrate sendCoin flow: getRuntimeVersion → chainGetBlockHash → chainGetFinalizedHead → chainGetHeader → systemAccountNextIndex → build extrinsic → sign → submit
6. THE Centrality_Module SHALL sử dụng Ktor HTTP client cho cả JSON-RPC và REST API
7. THE Centrality_Module SHALL sử dụng kotlinx.serialization thay vì Gson
8. THE Centrality_Module SHALL sử dụng suspend functions thay vì callback-based pattern

### Requirement 24: SCALE Encoding cho Centrality

**User Story:** Là developer, tôi muốn SCALE encoding hoạt động trên mọi nền tảng, để extrinsic builder cho Centrality không phụ thuộc Android.

#### Acceptance Criteria

1. THE ScaleCodec SHALL encode compact integers theo chuẩn SCALE (single-byte ≤ 63, two-byte ≤ 16383, four-byte ≤ 1073741823, big-integer mode cho giá trị lớn hơn)
2. THE ScaleCodec SHALL encode integers sang little-endian byte array với độ dài chỉ định
3. THE ScaleCodec SHALL prepend compact-encoded length trước byte array đầu vào
4. THE ScaleCodec SHALL sử dụng `com.ionspin.kotlin.bignum.integer.BigInteger` thay vì `java.math.BigInteger`
5. FOR ALL valid non-negative BigInteger values, encoding rồi decoding compact SCALE SHALL tạo ra giá trị tương đương (round-trip property)

### Requirement 25: Centrality — SS58 Address Parsing

**User Story:** Là developer, tôi muốn phân tích và xác thực địa chỉ SS58 trên mọi nền tảng, để CentralityManager xử lý địa chỉ CennzNet đa nền tảng.

#### Acceptance Criteria

1. WHEN a valid SS58 address string is provided, THE Centrality_Module SHALL parse và trả về public key tương ứng
2. WHEN an invalid SS58 address string is provided, THE Centrality_Module SHALL trả về null cho public key
3. THE Centrality_Module SHALL xác thực checksum SS58 bằng Blake2b-512 hash với prefix "SS58PRE"
4. FOR ALL valid SS58 addresses, parsing rồi re-encoding SHALL tạo ra cùng public key (round-trip property)

### Requirement 26: Centrality — ExtrinsicBuilder

**User Story:** Là developer, tôi muốn xây dựng và ký extrinsic Substrate trên mọi nền tảng, để giao dịch Centrality hoạt động đa nền tảng.

#### Acceptance Criteria

1. THE ExtrinsicBuilder SHALL tạo payload chứa method bytes, era, nonce (compact-encoded), transaction payment, specVersion (LE 4 bytes), transactionVersion (LE 4 bytes), genesisHash, và blockHash
2. THE ExtrinsicBuilder SHALL mã hóa extrinsic hoàn chỉnh bao gồm version byte (132), signature bytes, method bytes, và compact-encoded length prefix
3. WHEN a hex signature is provided, THE ExtrinsicBuilder SHALL gán signature bytes vào extrinsic
4. THE ExtrinsicBuilder SHALL output hex string với prefix "0x"

### Requirement 27: Cross-chain Bridge — Cardano ↔ Midnight

**User Story:** Là người dùng ví, tôi muốn bridge tài sản giữa Cardano và Midnight Network, để sử dụng ADA/tDUST trên cả hai chain.

#### Acceptance Criteria

1. WHEN người dùng yêu cầu bridge ADA từ Cardano sang Midnight, THE Bridge_Manager SHALL tạo lock transaction trên Cardano và initiate mint tDUST trên Midnight
2. WHEN người dùng yêu cầu bridge tDUST từ Midnight sang Cardano, THE Bridge_Manager SHALL tạo burn transaction trên Midnight và initiate unlock ADA trên Cardano
3. THE Bridge_Manager SHALL tính và hiển thị Bridge_Fee trước khi thực hiện bridge transaction
4. WHEN bridge transaction được initiate, THE Bridge_Manager SHALL trả về transaction ID để theo dõi trạng thái
5. IF số dư trên source chain không đủ cho amount và Bridge_Fee, THEN THE Bridge_Manager SHALL trả về lỗi mô tả rõ số dư hiện tại, amount yêu cầu, và phí bridge

### Requirement 28: Cross-chain Bridge — Ethereum ↔ Arbitrum

**User Story:** Là người dùng ví, tôi muốn bridge ETH giữa Ethereum và Arbitrum, để sử dụng tài sản trên Layer 2 với phí thấp hơn.

#### Acceptance Criteria

1. WHEN người dùng yêu cầu bridge ETH từ Ethereum sang Arbitrum, THE Bridge_Manager SHALL tạo deposit transaction qua Arbitrum bridge contract trên Ethereum
2. WHEN người dùng yêu cầu bridge ETH từ Arbitrum sang Ethereum, THE Bridge_Manager SHALL tạo withdrawal transaction qua Arbitrum bridge contract trên Arbitrum
3. THE Bridge_Manager SHALL tính và hiển thị Bridge_Fee (gas fee trên cả hai chain) trước khi thực hiện bridge
4. WHEN bridge transaction được initiate, THE Bridge_Manager SHALL trả về transaction ID
5. IF số dư trên source chain không đủ cho amount và gas fee, THEN THE Bridge_Manager SHALL trả về lỗi mô tả rõ số dư hiện tại và số lượng yêu cầu

### Requirement 29: Bridge Status Tracking

**User Story:** Là người dùng ví, tôi muốn theo dõi trạng thái bridge transaction, để biết khi nào tài sản đã được chuyển thành công.

#### Acceptance Criteria

1. WHEN người dùng cung cấp transaction hash, THE Bridge_Manager SHALL truy vấn Bridge_Status
2. THE Bridge_Manager SHALL trả về một trong các trạng thái: `pending`, `confirming`, `completed`, hoặc `failed`
3. WHEN bridge transaction hoàn tất, THE Bridge_Manager SHALL trả về trạng thái `completed` kèm transaction hash trên destination chain
4. IF transaction hash không tồn tại hoặc không phải bridge transaction, THEN THE Bridge_Manager SHALL trả về lỗi mô tả rõ transaction không tìm thấy

### Requirement 30: Generic Bridge Interface

**User Story:** Là developer, tôi muốn bridge interface có thể mở rộng cho các chain khác trong tương lai, để không cần refactor khi thêm bridge mới.

#### Acceptance Criteria

1. THE Bridge_Manager SHALL sử dụng IBridgeManager interface hiện có với methods `bridgeAsset` và `getBridgeStatus`
2. THE Wallet_Library SHALL cung cấp method `supportsBridge(fromChain, toChain)` trả về `true` cho các cặp chain được hỗ trợ: Cardano ↔ Midnight, Ethereum ↔ Arbitrum
3. WHEN `supportsBridge` được gọi với cặp chain chưa hỗ trợ, THE Wallet_Library SHALL trả về `false`
4. THE Bridge_Manager SHALL sử dụng Factory pattern (BridgeManagerFactory) để tạo bridge implementation phù hợp
5. IF `bridgeAsset` được gọi với cặp chain chưa hỗ trợ, THEN THE Bridge_Manager SHALL trả về lỗi `UnsupportedOperation`

### Requirement 31: Error Handling thống nhất

**User Story:** Là developer, tôi muốn error handling nhất quán trên toàn bộ thư viện, để dễ dàng debug và hiển thị lỗi cho người dùng.

#### Acceptance Criteria

1. THE Wallet_Library SHALL cung cấp sealed class `WalletError` với các subclass: ConnectionError, InsufficientFunds, InvalidAddress, TransactionRejected, UnsupportedOperation
2. THE Wallet_Library SHALL cung cấp sealed class `StakingError` với các subclass: PoolNotFound, InsufficientStakingBalance, DelegationAlreadyActive, NoDelegationActive
3. THE Wallet_Library SHALL cung cấp sealed class `BridgeError` với các subclass: UnsupportedBridgePair, BridgeServiceUnavailable, BridgeTransactionFailed, InsufficientBridgeBalance
4. THE Wallet_Library SHALL cung cấp sealed class `BitcoinError` với các subclass: InsufficientUtxos, InvalidTransaction
5. THE Wallet_Library SHALL cung cấp sealed class `RippleError` với các subclass: AccountNotFound, TransactionFailed
6. WHEN bất kỳ operation nào thất bại, THE Wallet_Library SHALL trả về error object chứa thông tin chi tiết về nguyên nhân lỗi
7. IF network request thất bại, THEN THE Wallet_Library SHALL trả về ConnectionError với endpoint và cause

### Requirement 32: Testing — Property-Based và Unit Tests

**User Story:** Là developer, tôi muốn bộ test đầy đủ trong commonTest, để đảm bảo tính đúng đắn trên mọi platform.

#### Acceptance Criteria

1. THE Test_Suite SHALL chạy trong commonTest và pass trên cả 3 platform: Android, iOS, JVM
2. THE Test_Suite SHALL sử dụng Kotest Property Testing (`io.kotest:kotest-property`) cho property-based tests với minimum 100 iterations
3. THE Test_Suite SHALL bao gồm property tests cho CBOR round-trip, address generation round-trip, transaction serialization round-trip, SCALE encoding round-trip
4. THE Test_Suite SHALL bao gồm unit tests cho BIP39 (mnemonic), BIP32 (key derivation), BIP44 (address generation) với known test vectors
5. THE Test_Suite SHALL bao gồm unit tests cho mỗi chain manager với Ktor mock client
6. THE Test_Suite SHALL bao gồm unit tests cho error conditions: invalid addresses, insufficient funds, network errors
7. THE Test_Suite SHALL bao gồm unit tests cho staking và bridge operations
8. FOR ALL test vector BIP39/BIP32 chuẩn, kết quả trong commonMain SHALL khớp chính xác với expected values

### Requirement 33: Backward Compatibility — Android CoinsManager

**User Story:** Là developer Android, tôi muốn CoinsManager trong androidMain vẫn hoạt động, để không phá vỡ ứng dụng hiện có.

#### Acceptance Criteria

1. THE androidMain CoinsManager SHALL delegate tới CommonCoinsManager cho tất cả coin đã migrate
2. THE androidMain CoinsManager SHALL giữ nguyên code Ethereum web3j (không xóa, không thay đổi)
3. THE androidMain CoinsManager SHALL wrap suspend calls trong launch coroutine và dispatch kết quả về Main thread
4. WHILE androidMain CoinsManager vẫn được sử dụng bởi Android app, THE CommonCoinsManager SHALL hoạt động độc lập mà không ảnh hưởng

### Requirement 34: Capability Matrix — Chain vs Interface

**User Story:** Là developer, tôi muốn biết rõ mỗi chain hỗ trợ interface nào, để sử dụng đúng API.

#### Acceptance Criteria

1. THE Bitcoin_Module SHALL implement: IWalletManager
2. THE Ethereum_Module SHALL implement: IWalletManager, ITokenManager, INFTManager, IFeeEstimator
3. THE Cardano_Module SHALL implement: IWalletManager, ITokenManager, IStakingManager
4. THE TON_Module SHALL implement: IWalletManager, ITokenManager, INFTManager, IStakingManager
5. THE Ripple_Module SHALL implement: IWalletManager
6. THE Midnight_Module SHALL implement: IWalletManager
7. THE Centrality_Module SHALL implement: IWalletManager
8. THE CommonCoinsManager SHALL trả về `false` cho capability check khi chain không implement interface tương ứng

---

## Phụ lục: Bảng tổng hợp Capability Matrix

**Chú thích trạng thái:**
- ✅ = Đã implement đầy đủ và hoạt động
- ⚠️ = Đã implement nhưng còn method stub/TODO hoặc dùng simulated response
- 🔲 = Chỉ có interface, chưa implement cho chain này
- — = Không áp dụng (chain không hỗ trợ capability này)

| Chain | IWalletManager | ITokenManager | INFTManager | IFeeEstimator | IStakingManager | IBridgeManager |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| Bitcoin | ✅ | — | — | — | — | — |
| Ethereum/Arbitrum | ✅ | ✅ | ✅ | ✅ | — | — |
| Cardano | ✅ | ✅ (native token, không qua ITokenManager interface) | — | — | ✅ | — |
| TON | ✅ | ✅ (Jetton, qua ITokenAndNFT) | ✅ | — | ✅ | — |
| Ripple/XRP | ✅ | — | — | — | — | — |
| Midnight | ✅ | — | — | — | — | — |
| Centrality | ✅ | — | — | — | — | — |

### Chi tiết trạng thái từng Chain Manager

| Chain Manager | Mức hoàn thiện | Ghi chú |
|---|:---:|---|
| BitcoinManager | ~100% | `transfer()` đã implement, broadcast qua BitcoinApiService.sendTransaction() |
| EthereumManager | ~100% | Token (ERC-20), Fee estimation, NFT (ERC-721 list + transfer) đầy đủ |
| CardanoManager | ~100% | Shelley/Byron address, staking (delegate/undelegate/rewards), native token gửi/nhận đều đã implement. Token operations tích hợp trực tiếp trong CardanoManager, không qua ITokenManager interface riêng |
| TonManager | ~100% | Jetton token, NFT listing + transfer (TEP-62), staking (Nominator/Tonstakers/Bemo), INFTManager interface đều đã implement |
| RippleManager | ~100% | Wallet operations cơ bản đầy đủ |
| MidnightManager | ~100% | Wallet operations cơ bản đầy đủ (getAddress, getBalance, getTransactionHistory, sendTDust) |
| CentralityManager | ~100% | Wallet operations đầy đủ với SCALE encoding và extrinsic builder |

## Phụ lục: Bảng tổng hợp Bridge Pairs

| Bridge Pair | IBridgeManager Implementation | Trạng thái |
|---|---|:---:|
| Cardano ↔ Midnight | CardanoMidnightBridge | ⚠️ ~80% — Cấu trúc đầy đủ, dùng simulated response (Midnight API chưa sẵn sàng cho production) |
| Ethereum ↔ Arbitrum | EthereumArbitrumBridge | ⚠️ ~80% — Cấu trúc đầy đủ, dùng simulated response (chưa kết nối Arbitrum bridge contract thật) |

## Phụ lục: Dependencies chính

| Thư viện | Mục đích | Source Set |
|---|---|---|
| `fr.acinq.bitcoin:bitcoin-kmp` | BIP32/39, Base58, Crypto | commonMain |
| `io.ktor:ktor-client-*` | HTTP networking | commonMain |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | JSON parsing | commonMain |
| `com.squareup.okio:okio` | I/O, hashing | commonMain |
| `com.soywiz.korlibs.krypto:krypto` | Crypto primitives | commonMain |
| `com.ionspin.kotlin:bignum` | Big number arithmetic | commonMain |
| `co.touchlab:kermit` | Logging | commonMain |
| `org.ton-community:ton-kotlin-*` | TON blockchain | commonMain |
| `fr.acinq.secp256k1:secp256k1-kmp` | Secp256k1 signing | commonMain |
| `io.kotest:kotest-property` | Property-based testing | commonTest |
