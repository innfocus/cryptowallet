# Ethereum & Arbitrum Integration cho Android

Hướng dẫn tích hợp Ethereum và Arbitrum vào ứng dụng Android. Cả 2 chain dùng chung `EthereumManager`, chỉ khác network config.

> **Tài liệu kỹ thuật chi tiết:** [Ethereum spec](../chains/ethereum.md)
> **CommonCoinsManager API:** [API Reference](../api/common-coins-manager.md)

---

## 1. Dependency

```groovy
dependencies {
    implementation 'io.github.innfocus:crypto-wallet-lib-android:1.0.2'
}
```

---

## 2. Khởi tạo

### 2.1 Qua CommonCoinsManager (recommended)

```kotlin
import com.lybia.cryptowallet.coinkits.CommonCoinsManager
import com.lybia.cryptowallet.enums.NetworkName
import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.enums.Network

Config.shared.setNetwork(Network.MAINNET)
// API keys (bắt buộc)
Config.shared.apiKeyInfura = "your-infura-project-id"
Config.shared.apiKeyEtherscan = "your-etherscan-api-key"
Config.shared.apiKeyArbiscan = "your-arbiscan-api-key"      // Cho Arbitrum
Config.shared.apiKeyOwlRacle = "your-owlracle-api-key"      // Cho Arbitrum gas

CommonCoinsManager.initialize("your mnemonic words ...")
val ccm = CommonCoinsManager.shared
```

### 2.2 Qua CoinsManager (callback, legacy)

```kotlin
import com.lybia.cryptowallet.coinkits.CoinsManager
import com.lybia.cryptowallet.coinkits.hdwallet.bip32.ACTCoin

CoinsManager.shared.updateMnemonic("your mnemonic words ...")
```

### 2.3 Trực tiếp qua EthereumManager (advanced)

```kotlin
import com.lybia.cryptowallet.wallets.ethereum.EthereumManager

val ethManager = EthereumManager("your mnemonic words ...")
```

---

## 3. Lấy địa chỉ ví

Ethereum và Arbitrum dùng **cùng 1 address** (EIP-55 checksummed).

```kotlin
// CommonCoinsManager
val ethAddress = ccm.getAddress(NetworkName.ETHEREUM)
val arbAddress = ccm.getAddress(NetworkName.ARBITRUM)  // Cùng giá trị

// CoinsManager
val address = CoinsManager.shared.addresses()[ACTCoin.Ethereum]
```

> **Derivation:** BIP-44 path `m/44'/60'/0'/0/0` (secp256k1)
> **Format:** `0x...` (42 ký tự hex, EIP-55 checksummed)
> Address giống nhau trên mainnet, testnet, Ethereum, và Arbitrum.

---

## 4. Lấy số dư

```kotlin
viewModelScope.launch {
    // ETH balance
    val ethResult = ccm.getBalance(NetworkName.ETHEREUM)
    Log.d("ETH", "ETH Balance: ${ethResult.balance}")

    // Arbitrum ETH balance
    val arbResult = ccm.getBalance(NetworkName.ARBITRUM)
    Log.d("ETH", "ARB ETH Balance: ${arbResult.balance}")
}

// Qua CoinsManager (callback)
CoinsManager.shared.getBalance(ACTCoin.Ethereum, object : BalanceHandle {
    override fun completionHandler(balance: Double, success: Boolean) {
        Log.d("ETH", "Balance: $balance ETH")
    }
})
```

> **Quy đổi:** 1 ETH = 10^18 wei = 10^9 gwei. API trả về Double (ETH).

---

## 5. Lịch sử giao dịch

### 5.1 Không phân trang

```kotlin
viewModelScope.launch {
    val txs = ccm.getTransactionHistory(NetworkName.ETHEREUM)
}
```

### 5.2 Có phân trang (page-based)

```kotlin
viewModelScope.launch {
    // Trang đầu — 20 giao dịch mới nhất
    val page1 = ccm.getTransactionHistoryPaginated(
        coin = NetworkName.ETHEREUM,
        limit = 20
    )

    // Trang tiếp
    if (page1.hasMore) {
        val page2 = ccm.getTransactionHistoryPaginated(
            coin = NetworkName.ETHEREUM,
            limit = 20,
            pageParam = page1.nextPageParam  // {"page": 2}
        )
    }

    // Arbitrum — cùng pattern
    val arbPage = ccm.getTransactionHistoryPaginated(
        coin = NetworkName.ARBITRUM,
        limit = 20
    )
}
```

> **Pagination model:** Page-based (1-indexed). `pageParam = {"page": Int}`. Max offset: 10,000/page.
> **Sort:** Mới nhất trước (`desc`).

---

## 6. Gửi ETH

### 6.1 Qua CommonCoinsManager (recommended)

```kotlin
viewModelScope.launch {
    val result = ccm.sendCoin(
        coin = NetworkName.ETHEREUM,
        toAddress = "0xRecipient...",
        amount = 0.5    // 0.5 ETH
    )
    if (result.success) {
        Log.d("ETH", "TX: ${result.txHash}")
    }
}
```

### 6.2 Gửi trên Arbitrum

```kotlin
viewModelScope.launch {
    val result = ccm.sendCoin(
        coin = NetworkName.ARBITRUM,    // Chỉ khác NetworkName
        toAddress = "0xRecipient...",
        amount = 0.1
    )
}
```

### 6.3 Với Service Fee

```kotlin
viewModelScope.launch {
    val result = ccm.sendCoin(
        coin = NetworkName.ETHEREUM,
        toAddress = "0xRecipient...",
        amount = 1.0,
        serviceAddress = "0xServiceAddr...",
        serviceFee = 0.01  // 0.01 ETH service fee
    )
    // 2 transactions: main + service fee
    // Network fee × 2 (FEE_MULTIPLIER)
}
```

### 6.4 Nâng cao (qua EthereumManager)

```kotlin
viewModelScope.launch {
    val coinNetwork = CoinNetwork(NetworkName.ETHEREUM)

    // EIP-1559 transaction (recommended)
    val result = ethManager.sendEthBigInt(
        toAddress = "0xRecipient...",
        amountWei = ethManager.ethToWei(0.5),  // BigInteger safe
        coinNetwork = coinNetwork,
        gasLimit = 21000L,                      // Optional override
        maxPriorityFeeGwei = 2L,                // Optional tip override
        maxFeeGwei = 30L                        // Optional max fee override
    )

    // Legacy transaction (fallback)
    val legacyResult = ethManager.sendEth(
        toAddress = "0xRecipient...",
        amountWei = 500_000_000_000_000_000L,   // 0.5 ETH (Long, max ~9.2 ETH!)
        coinNetwork = coinNetwork,
        gasPriceGwei = 20L
    )
}
```

> **Quan trọng:** `Long.MAX_VALUE` ≈ 9.2 ETH. Dùng `sendEthBigInt()` + `ethToWei()` cho amount lớn.

---

## 7. ERC-20 Token

### 7.1 Lấy số dư

```kotlin
viewModelScope.launch {
    val result = ccm.getTokenBalance(
        coin = NetworkName.ETHEREUM,
        address = myAddress,
        contractAddress = "0xdAC17F958D2ee523a2206206994597C13D831ec7"  // USDT
    )
    Log.d("ETH", "USDT: ${result.balance}")
}
```

### 7.2 Lịch sử giao dịch token (phân trang)

```kotlin
viewModelScope.launch {
    // Trang đầu — ERC-20 token transactions
    val page1 = ccm.getTokenTransactionHistoryPaginated(
        coin = NetworkName.ETHEREUM,
        policyId = "0xUSDT_ContractAddress...",  // ERC-20 contract address
        assetName = "",                           // Không dùng cho ETH
        limit = 20
    )
    // page1.transactions: List<TransactionToken>
    // page1.hasMore, page1.nextPageParam = {"page": 2}

    // Trang tiếp
    if (page1.hasMore) {
        val page2 = ccm.getTokenTransactionHistoryPaginated(
            coin = NetworkName.ETHEREUM,
            policyId = "0xUSDT_ContractAddress...",
            assetName = "",
            limit = 20,
            pageParam = page1.nextPageParam
        )
    }
}
```

### 7.3 Gửi ERC-20

```kotlin
viewModelScope.launch {
    val coinNetwork = CoinNetwork(NetworkName.ETHEREUM)

    // BigInteger version (recommended, no overflow)
    val result = ethManager.sendErc20TokenBigInt(
        contractAddress = "0xUSDT...",
        toAddress = "0xRecipient...",
        amount = BigInteger.fromLong(10_000_000),  // 10 USDT (6 decimals)
        coinNetwork = coinNetwork,
        gasLimit = 65000L   // ERC-20 cần gas cao hơn ETH transfer
    )

    // Legacy Long version
    val result2 = ethManager.sendErc20Token(
        contractAddress = "0xUSDT...",
        toAddress = "0xRecipient...",
        amount = 10_000_000L,
        coinNetwork = coinNetwork
    )
}
```

> **ABI:** Dùng selector `0xa9059cbb` (`transfer(address,uint256)`)
> **Gas:** ERC-20 transfer thường cần ~65,000 gas (vs 21,000 cho ETH transfer)

### 7.4 Approve ERC-20

Cho phép một address (vd: DEX contract) chi tiêu token thay bạn.

```kotlin
viewModelScope.launch {
    val coinNetwork = CoinNetwork(NetworkName.ETHEREUM)

    // Approve spender chi tiêu 100 USDT (6 decimals)
    val result = ethManager.approveErc20Token(
        contractAddress = "0xUSDT...",
        spenderAddress = "0xDexRouter...",
        amount = BigInteger.fromLong(100_000_000),  // 100 USDT
        coinNetwork = coinNetwork
    )

    if (result.success) {
        Log.d("ETH", "Approve tx: ${result.txHash}")
    }
}
```

> **ABI:** Dùng selector `0x095ea7b3` (`approve(address,uint256)`)
> **Gas:** ERC-20 approve thường cần ~46,000 gas

### 7.5 Check Allowance

Kiểm tra số lượng token đã approve cho spender (read-only, không tốn gas).

```kotlin
viewModelScope.launch {
    val coinNetwork = CoinNetwork(NetworkName.ETHEREUM)

    val allowance = ethManager.getAllowanceErc20Token(
        contractAddress = "0xUSDT...",
        ownerAddress = myAddress,
        spenderAddress = "0xDexRouter...",
        coinNetwork = coinNetwork
    )

    Log.d("ETH", "Allowance: $allowance")  // BigInteger in smallest unit
}
```

> **ABI:** Dùng selector `0xdd62ed3e` (`allowance(address,address)`) via `eth_call`

---

## 8. NFT (ERC-721)

### 8.1 Lấy danh sách (with metadata)

```kotlin
viewModelScope.launch {
    val nfts = ccm.getNFTs(NetworkName.ETHEREUM, myAddress)
    nfts?.forEach { nft ->
        Log.d("ETH", "NFT: ${nft.name} #${nft.index}")
        Log.d("ETH", "  Description: ${nft.description}")
        Log.d("ETH", "  Image: ${nft.imageUrl}")
    }
}
```

> `getNFTs` tự động fetch metadata (name, description, imageUrl) qua `tokenURI` cho mỗi NFT.

### 8.2 Fetch metadata cho 1 NFT

```kotlin
viewModelScope.launch {
    val coinNetwork = CoinNetwork(NetworkName.ETHEREUM)
    val metadata = ethManager.getNFTMetadata(
        contractAddress = "0xNftContract...",
        tokenId = BigInteger.fromLong(1234),
        coinNetwork = coinNetwork
    )
    Log.d("ETH", "Name: ${metadata?.name}, Image: ${metadata?.imageUrl}")
}
```

> Hỗ trợ: HTTP URLs, IPFS (`ipfs://` → `https://ipfs.io/ipfs/`), on-chain data URIs.

### 8.3 Transfer (safeTransferFrom)

```kotlin
viewModelScope.launch {
    val result = ccm.transferNFT(
        coin = NetworkName.ETHEREUM,
        nftAddress = "0xNftContract...",
        toAddress = "0xRecipient...",
        memo = "1234"   // Token ID (bắt buộc)
    )
    if (result.success) {
        Log.d("ETH", "NFT transfer tx: ${result.txHash}")
    }
}
```

> **ABI:** Dùng `safeTransferFrom(address,address,uint256)` selector `0x42842e0e`.
> **memo** chứa token ID — bắt buộc cho ERC-721 transfer.
> Chỉ hỗ trợ ERC-721, chưa có ERC-1155.

---

## 9. Fee Estimation

### 9.1 Qua CommonCoinsManager

```kotlin
viewModelScope.launch {
    val fee = ccm.estimateFee(
        coin = NetworkName.ETHEREUM,
        amount = 0.5,
        toAddress = "0xRecipient..."
    )
    Log.d("ETH", "Fee: ${fee.fee} ETH, Gas: ${fee.gasLimit}, Price: ${fee.gasPrice} gwei")
}
```

### 9.2 Gas Price (3 tiers)

```kotlin
viewModelScope.launch {
    val coinNetwork = CoinNetwork(NetworkName.ETHEREUM)
    val gasPrice = ethManager.getAllGasPrice(coinNetwork)
    // gasPrice.SafeGasPrice     → Slow (30+ min)
    // gasPrice.ProposeGasPrice  → Standard (10-15 min)
    // gasPrice.FastGasPrice     → Fast (< 2 min)
}
```

### 9.3 Gas Limit Estimation

```kotlin
viewModelScope.launch {
    val gasLimit = ethManager.getEstGas(
        TransferTokenModel(from = myAddress, to = "0xRecipient...", value = "0x..."),
        CoinNetwork(NetworkName.ETHEREUM)
    )
    // ETH transfer: ~21,000
    // ERC-20 transfer: ~65,000
    // Complex contract: varies
}
```

> **Arbitrum:** Dùng OwlRacle thay vì Etherscan cho gas price.

---

## 10. Ethereum vs Arbitrum

| Thuộc tính | Ethereum | Arbitrum |
|-----------|----------|---------|
| NetworkName | `ETHEREUM` | `ARBITRUM` |
| ACTCoin | `ACTCoin.Ethereum` | `ACTCoin.Arbitrum` |
| Address | Cùng address | Cùng address |
| RPC | Infura mainnet | Infura arbitrum-mainnet |
| Explorer | Etherscan | Arbiscan |
| Gas pricing | Etherscan gasoracle | OwlRacle |
| TX type | EIP-1559 | EIP-1559 |
| Gas cost | Cao (~20-50 gwei) | Thấp (~0.1-0.5 gwei) |
| Finality | ~12s (1 block) | ~0.25s |

---

## 11. Threading

```kotlin
viewModelScope.launch {
    val balance = ccm.getBalance(NetworkName.ETHEREUM)
    // Update UI trực tiếp
}
```

- `CommonCoinsManager`: `suspend fun` — gọi trong `CoroutineScope`
- `CoinsManager` (legacy): Callback trên `Dispatchers.Main`
- `EthereumManager`: `suspend fun` — gọi trong `CoroutineScope`

---

## 12. Network & Explorer

| Mục | Ethereum Mainnet | Ethereum Sepolia | Arbitrum Mainnet | Arbitrum Sepolia |
|---|---|---|---|---|
| RPC | `mainnet.infura.io` | `sepolia.infura.io` | `arbitrum-mainnet.infura.io` | `arbitrum-sepolia.infura.io` |
| Explorer | `etherscan.io` | `sepolia.etherscan.io` | `arbiscan.io` | `sepolia.arbiscan.io` |

```kotlin
Config.shared.setNetwork(Network.TESTNET)
// Tự động đổi sang Sepolia endpoints
```

---

## 13. Lỗi thường gặp

| Lỗi | Nguyên nhân | Cách fix |
|---|---|---|
| Insufficient funds | Balance < amount + gas fee | Kiểm tra balance trước khi send |
| Nonce too low | TX đã submit với nonce này | Retry — nonce tự tăng |
| Gas too low | Contract interaction phức tạp | Tăng `gasLimit` |
| Long overflow (> 9.2 ETH) | Dùng `sendEth()` với Long | Dùng `sendEthBigInt()` + `ethToWei()` |
| Token transfer fail | Không đủ ETH cho gas | Cần ETH trong ví để trả gas cho ERC-20 transfer |
| API key missing | Thiếu Infura/Etherscan key | Set `Config.shared.apiKeyInfura/apiKeyEtherscan` |
| Wrong network | Mainnet vs Testnet | Kiểm tra `Config.shared.getNetwork()` |
