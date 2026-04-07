# Ethereum & Arbitrum Technical Spec

Tài liệu kỹ thuật cho Ethereum và Arbitrum trong CryptoWallet library. Cả 2 chain dùng chung `EthereumManager`.

## 1. Tổng quan kiến trúc

```
commonMain/
├── wallets/ethereum/
│   ├── EthereumManager.kt           # Core: address, balance, sign, send, token, NFT, fee
│   └── EthTransactionSigner.kt      # RLP + secp256k1 signing (Legacy + EIP-1559)
├── services/
│   ├── InfuraRpcService.kt          # JSON-RPC: eth_* methods (Infura)
│   ├── ExplorerRpcService.kt        # REST: Etherscan/Arbiscan (history, gas, token)
│   └── OwlRacleService.kt           # Gas pricing cho Arbitrum
└── models/
    ├── InfuraModel.kt               # RPC request/response models
    └── ExplorerModel.kt             # Transaction, Token, NFT models
```

### Shared Manager Pattern
```kotlin
// ChainManagerFactory
NetworkName.ETHEREUM -> EthereumManager(mnemonic)
NetworkName.ARBITRUM -> EthereumManager(mnemonic)  // Same class, different CoinNetwork
```

Ethereum vs Arbitrum chỉ khác:
- RPC endpoint (Infura)
- Explorer endpoint (Etherscan vs Arbiscan)
- Gas pricing source (Etherscan vs OwlRacle)

---

## 2. Interfaces

```kotlin
class EthereumManager(mnemonic: String?) :
    BaseCoinManager(),    // getAddress, getBalance, transfer, getTransactionHistory
    ITokenManager,        // getTokenBalance, getTokenTransactionHistory, transferToken
    INFTManager,          // getNFTs, transferNFT
    IFeeEstimator,        // estimateFee
    ITransactionFee,      // getEstGas, getAllGasPrice
    ITokenAndNFT          // Legacy: getBalanceToken, TransferToken
```

---

## 3. Address & Key Derivation

| Thuoc tinh | Gia tri |
|-----------|---------|
| **Algorithm** | secp256k1 ECDSA |
| **Derivation** | BIP-44: `m/44'/60'/0'/0/0` |
| **Format** | EIP-55 checksummed hex (`0x...`, 42 chars) |
| **Mainnet = Testnet** | Cung address tren moi network |

```kotlin
private val hdWallet by lazy { ACTHDWallet(mnemonic) }
override fun getAddress(): String = hdWallet.getAddressForCoin(ACTCoin.Ethereum)
```

---

## 4. Transaction Types

### 4.1 Legacy (Type 0 — EIP-155)

```
RLP([nonce, gasPrice, gasLimit, to, value, data, chainId, 0, 0])
→ Keccak256 hash
→ secp256k1 sign
→ v = chainId × 2 + 35 + recId
→ RLP([nonce, gasPrice, gasLimit, to, value, data, v, r, s])
```

**Khi dung:** Fallback khi mang khong ho tro EIP-1559 (khong co `baseFeePerGas` trong block header).

### 4.2 EIP-1559 (Type 2)

```
0x02 || RLP([chainId, nonce, maxPriorityFeePerGas, maxFeePerGas,
             gasLimit, to, value, data, accessList])
→ Keccak256 hash
→ secp256k1 sign
→ 0x02 || RLP([chainId, nonce, maxPriorityFeePerGas, maxFeePerGas,
               gasLimit, to, value, data, accessList, yParity, r, s])
```

**Khi dung:** Mac dinh cho Ethereum mainnet/Sepolia va Arbitrum.

### 4.3 Fee Calculation (EIP-1559)

```
baseFee        = eth_getBlockByNumber("latest").baseFeePerGas
priorityFee    = eth_maxPriorityFeePerGas || fallback 1.5 gwei
maxFeePerGas   = 2 × baseFee + priorityFee
gasLimit       = eth_estimateGas || user override
totalFee (max) = gasLimit × maxFeePerGas
```

---

## 5. Signing Flow

```
1. getAddress()                    → EOA address
2. getNonce(coinNetwork)           → eth_getTransactionCount
3. getChainId(coinNetwork)         → eth_chainId
4. estimateGas (optional)          → eth_estimateGas
5. Check EIP-1559 support          → eth_getBlockByNumber → baseFeePerGas?
6. Sign transaction
   ├── EIP-1559: signEip1559Transaction(privateKey, nonce, maxPriority, maxFee, gasLimit, to, value, data, chainId)
   └── Legacy:   signLegacyTransaction(privateKey, nonce, gasPrice, gasLimit, to, value, data, chainId)
7. Broadcast                       → eth_sendRawTransaction
8. Return txHash
```

---

## 6. ERC-20 Token Support

### Balance
```kotlin
suspend fun getTokenBalance(address: String, contractAddress: String, coinNetwork: CoinNetwork): Double
// Via ExplorerRpcService → Etherscan/Arbiscan tokenbalance API
```

### Transfer
```kotlin
suspend fun sendErc20TokenBigInt(
    contractAddress: String,
    toAddress: String,
    amount: BigInteger,           // smallest unit (e.g., 6 decimals for USDT)
    coinNetwork: CoinNetwork,
    gasLimit: Long? = null
): TransferResponseModel
```

**ABI Encoding:**
```
Function selector: 0xa9059cbb  (transfer(address,uint256))
Params: padded_address(32 bytes) + padded_amount(32 bytes)
Data: selector(4) + address(32) + amount(32) = 68 bytes
```

### Transaction History
```kotlin
suspend fun getTokenTransactionHistory(address: String, contractAddress: String, coinNetwork: CoinNetwork): Any?
// Via ExplorerRpcService → tokentx API
// Returns: List<TransactionToken>
```

---

## 7. NFT Support (ERC-721)

### Listing (with metadata)
```kotlin
override suspend fun getNFTs(address: String, coinNetwork: CoinNetwork): List<NFTItem>?
// Via ExplorerRpcService → tokennfttx
// Deduplicates by contractAddress + tokenID
// Fetches metadata (name, description, imageUrl) via tokenURI for each NFT
```

### Metadata Fetch
```kotlin
suspend fun getNFTMetadata(
    contractAddress: String,
    tokenId: BigInteger,
    coinNetwork: CoinNetwork
): NFTItem?
// Calls tokenURI(uint256) via eth_call → fetches JSON metadata
// Supports: HTTP URLs, IPFS (ipfs:// → https://ipfs.io/ipfs/), on-chain data URIs
```

**ABI Encoding:**
```
tokenURI selector: 0xc87b56dd  (tokenURI(uint256))
Data: selector(4) + tokenId(32) = 36 bytes
Result: ABI-encoded string (offset + length + data)
```

### Transfer (safeTransferFrom)
```kotlin
override suspend fun transferNFT(
    nftAddress: String,       // NFT contract address
    toAddress: String,        // Recipient
    memo: String?,            // Token ID (required)
    coinNetwork: CoinNetwork
): TransferResponseModel
```

**ABI Encoding:**
```
Function selector: 0x42842e0e  (safeTransferFrom(address,address,uint256))
Params: padded_from(32) + padded_to(32) + padded_tokenId(32)
Data: selector(4) + from(32) + to(32) + tokenId(32) = 100 bytes
```

### Limitations
- Chi ho tro ERC-721 (khong co ERC-1155)
- IPFS metadata fetch dung public gateway (https://ipfs.io/ipfs/)

---

## 7.5. ERC-20 Token Approval (approve / allowance)

### Approve
```kotlin
suspend fun approveErc20Token(
    contractAddress: String,      // ERC-20 token contract
    spenderAddress: String,       // Address duoc phep chi tieu
    amount: BigInteger,           // So luong approve (smallest unit)
    coinNetwork: CoinNetwork,
    gasLimit: Long? = null
): TransferResponseModel
```

**ABI Encoding:**
```
Function selector: 0x095ea7b3  (approve(address,uint256))
Params: padded_spender(32 bytes) + padded_amount(32 bytes)
Data: selector(4) + spender(32) + amount(32) = 68 bytes
```

### Allowance (read-only, no gas)
```kotlin
suspend fun getAllowanceErc20Token(
    contractAddress: String,      // ERC-20 token contract
    ownerAddress: String,         // Token owner
    spenderAddress: String,       // Spender can kiem tra
    coinNetwork: CoinNetwork
): BigInteger?                    // Allowance amount (smallest unit)
```

**ABI Encoding:**
```
Function selector: 0xdd62ed3e  (allowance(address,address))
Params: padded_owner(32 bytes) + padded_spender(32 bytes)
Data: selector(4) + owner(32) + spender(32) = 68 bytes
Result: uint256 (32 bytes) — parsed via eth_call (read-only)
```

---

## 7.6. Generic Contract ABI Interaction

### ABI Parameter Types
```kotlin
sealed class AbiParam {
    data class Uint256(val value: BigInteger)    // uint256
    data class Address(val value: String)         // address (20 bytes, left-padded)
    data class Bool(val value: Boolean)           // bool
    data class Bytes32(val value: ByteArray)      // bytes32
    data class Raw(val value: ByteArray)          // pre-encoded bytes
}
```

### Function Selector
```kotlin
// Compute 4-byte selector from Solidity signature
EthTransactionSigner.functionSelector("transfer(address,uint256)")
// → 0xa9059cbb
```

### Read-only Call (eth_call, no gas)
```kotlin
suspend fun callContract(
    contractAddress: String,
    functionSignature: String,     // e.g. "balanceOf(address)"
    params: List<AbiParam>,
    coinNetwork: CoinNetwork
): String?                         // Raw hex result
```

### Write Transaction (build + sign + broadcast)
```kotlin
suspend fun executeContract(
    contractAddress: String,
    functionSignature: String,     // e.g. "approve(address,uint256)"
    params: List<AbiParam>,
    valueWei: BigInteger = BigInteger.ZERO,  // ETH to send with call
    coinNetwork: CoinNetwork,
    gasLimit: Long? = null
): TransferResponseModel
```

### Generic Encoding
```kotlin
// Encode any contract call: selector + params
EthTransactionSigner.encodeContractCall(
    signature = "transfer(address,uint256)",
    params = listOf(
        AbiParam.Address("0xRecipient..."),
        AbiParam.Uint256(BigInteger.fromLong(1000000))
    )
)
// → 68-byte ABI-encoded call data
```

---

## 7.7. ETH-Arbitrum Bridge

### L1 → L2 (Deposit): ~10 min
```kotlin
// Sends ETH to Arbitrum Delayed Inbox contract on Ethereum L1
// Inbox: 0x4Dbd4fc535Ac27206064B68FfCf827b0A60BAB3f (mainnet)
//        0xaAe29B0366299461418F5324a79Afc425BE5ae21 (sepolia)
val result = bridge.bridgeAsset(
    fromChain = NetworkName.ETHEREUM,
    toChain = NetworkName.ARBITRUM,
    amount = 1_000_000_000_000_000_000L  // 1 ETH in wei
)
```

### L2 → L1 (Withdrawal): ~7 day challenge period
```kotlin
// Calls ArbSys precompile (0x...0064) withdrawEth(address) on Arbitrum L2
val result = bridge.bridgeAsset(
    fromChain = NetworkName.ARBITRUM,
    toChain = NetworkName.ETHEREUM,
    amount = 500_000_000_000_000_000L  // 0.5 ETH
)
```

### Status Query
```kotlin
val status = bridge.getBridgeStatus(txHash)
// "pending" | "confirming" | "completed" | "failed"
// Queries eth_getTransactionReceipt on both L1 and L2
```

---

## 7.8. Batch RPC Calls

### Raw batch call
```kotlin
val results = ethManager.batchRpcCall(
    requests = listOf(
        "eth_getBalance" to listOf(address, "latest"),
        "eth_getTransactionCount" to listOf(address, "latest"),
        "eth_gasPrice" to emptyList()
    ),
    coinNetwork = coinNetwork
)
// results: List<String?> — hex values, null for failed calls
```

### Balance + Nonce (1 round trip thay vì 2)
```kotlin
val (balance, nonce) = ethManager.getBalanceAndNonce(address, coinNetwork)
    ?: throw Exception("Batch RPC failed")
```

### Multi-token balances (1 round trip thay vì N)
```kotlin
val balances = ethManager.getTokenBalancesBatch(
    ownerAddress = myAddress,
    contractAddresses = listOf("0xUSDT...", "0xUSDC...", "0xDAI..."),
    coinNetwork = coinNetwork
)
// balances: Map<contractAddress, BigInteger>
```

---

## 8. Gas/Fee Estimation

### Gas Price Sources

| Chain | Source | API |
|-------|--------|-----|
| Ethereum | ExplorerRpcService | Etherscan `gasoracle` |
| Arbitrum | OwlRacleService | OwlRacle `/v4/arb/gas` |

### GasPrice Model
```kotlin
data class GasPrice(
    val SafeGasPrice: String,     // Slow (30+ min)
    val ProposeGasPrice: String,  // Standard (10-15 min)
    val FastGasPrice: String      // Fast (< 2 min)
)
```

### Fee Estimation Flow
```kotlin
suspend fun estimateFee(params: FeeEstimateParams, coinNetwork: CoinNetwork): FeeEstimate {
    val gasLimit = getEstGas(model, coinNetwork)       // eth_estimateGas
    val gasPrice = getAllGasPrice(coinNetwork)           // Etherscan or OwlRacle
    val fee = gasLimit * gasPriceGwei / 1e9             // Convert to ETH
    return FeeEstimate(fee, gasLimit, gasPriceGwei, "gwei")
}
```

---

## 9. Wei / Gwei / ETH Conversion

| Don vi | Gia tri | Dung khi |
|--------|---------|----------|
| **wei** | 1 | Smallest unit, dung trong RPC |
| **gwei** | 10^9 wei | Gas price |
| **ETH** | 10^18 wei | Display, balance |

```kotlin
fun ethToWei(ethAmount: Double): BigInteger {
    val gwei = (ethAmount * 1_000_000_000).toLong()
    return BigInteger.fromLong(gwei) * BigInteger.fromLong(1_000_000_000)
}
```

> **Chu y:** `Long.MAX_VALUE` ≈ 9.2 ETH. Dung `BigInteger` cho amount > 9 ETH.

---

## 10. API Endpoints

### Mainnet

| Service | Ethereum | Arbitrum |
|---------|----------|---------|
| RPC (Infura) | `mainnet.infura.io/v3/{key}` | `arbitrum-mainnet.infura.io/v3/{key}` |
| Explorer | `api.etherscan.io/api` | `api.arbiscan.io/api` |
| Gas Oracle | Etherscan gasoracle | `api.owlracle.info/v4/arb/gas` |

### Testnet (Sepolia)

| Service | Ethereum | Arbitrum |
|---------|----------|---------|
| RPC (Infura) | `sepolia.infura.io/v3/{key}` | `arbitrum-sepolia.infura.io/v3/{key}` |
| Explorer | `api-sepolia.etherscan.io/api` | `api-sepolia.arbiscan.io/api` |

### API Keys
```kotlin
Config.shared.apiKeyInfura      // Infura project ID
Config.shared.apiKeyEtherscan   // Etherscan API key
Config.shared.apiKeyArbiscan    // Arbiscan API key
Config.shared.apiKeyOwlRacle    // OwlRacle API key
```

---

## 11. Error Handling

| Error | Khi nao | Response |
|-------|---------|----------|
| RPC error | Invalid params, insufficient funds | `TransferResponseModel(success=false, error=message)` |
| Nonce too low | TX already submitted | Retry — nonce auto-increments |
| Gas too low | Complex contract interaction | Increase `gasLimit` parameter |
| Insufficient funds | Balance < amount + fee | Check balance truoc khi send |
| Invalid address | Sai format hex | Validate regex truoc khi send |

---

## 12. Hien trang & Gaps

### Da implement

| Feature | Standard | Trang thai |
|---------|----------|-----------|
| ETH transfer | EIP-155, EIP-1559 | ✅ Full |
| ERC-20 token | ERC-20 | ✅ Full (balance, transfer, history) |
| ERC-721 NFT | ERC-721 | ✅ Full (listing + metadata + safeTransferFrom) |
| Fee estimation | EIP-1559 | ✅ Full (baseFee + priorityFee) |
| Gas price | Legacy + EIP-1559 | ✅ Full (3 tiers: Safe/Propose/Fast) |
| Address | EIP-55 | ✅ Checksummed |
| BigInteger | -- | ✅ Safe cho amount > 9 ETH |
| Arbitrum | L2 | ✅ Shared manager, rieng gas pricing |

### Chua implement (Gaps)

| Feature | Standard | Muc do |
|---------|----------|--------|
| Transaction pagination | Etherscan page/offset | ✅ Da implement (page-based, 1-based) |
| ERC-1155 multi-token | ERC-1155 | COULD: Chua ho tro |
| ~~NFT metadata fetch~~ | ~~--~~ | ✅ Da implement (`getNFTMetadata` via `tokenURI` + JSON fetch) |
| ~~NFT transfer (ERC-721)~~ | ~~ERC-721~~ | ✅ Da implement (`safeTransferFrom` ABI encoding) |
| ~~Contract ABI interaction~~ | ~~--~~ | ✅ Da implement (`callContract` + `executeContract` + generic ABI encoding) |
| Account Abstraction | ERC-4337 | COULD: Chua ho tro |
| Permit / Gasless approval | EIP-2612 | COULD: Chua ho tro |
| ~~ETH-Arbitrum Bridge~~ | ~~--~~ | ✅ Da implement (Delayed Inbox deposit + ArbSys withdrawal) |
| ~~Batch RPC calls~~ | ~~--~~ | ✅ Da implement (`batchCall` + `getBalanceAndNonce` + `getTokenBalancesBatch`) |
| ~~Token approval (approve)~~ | ~~ERC-20~~ | ✅ Da implement (`approveErc20Token`) |
| ~~Token allowance check~~ | ~~ERC-20~~ | ✅ Da implement (`getAllowanceErc20Token` via `eth_call`) |
