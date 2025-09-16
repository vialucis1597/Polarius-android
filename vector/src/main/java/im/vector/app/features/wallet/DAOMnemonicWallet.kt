/*
 * Copyright 2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.wallet

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import org.matrix.android.sdk.api.session.Session

data class DAOWalletData(
    val daoId: String,
    val daoName: String,
    val mnemonic: String,
    val address: String,
    val privateKey: String,
    val currency: String = "B",
    val balance: Long = 0,
    val contributionValue: Long = 1,
    val createdAt: String = Date().toString()
)

data class DAOWalletSummary(
    val daoId: String,
    val daoName: String,
    val address: String,
    val currency: String = "B",
    val balance: Long = 0,
    val contributionValue: Long = 1
)

class DAOMnemonicWallet private constructor(
    private val context: Context,
    private val session: Session? = null
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("dao_wallet", Context.MODE_PRIVATE)
    private val daoWallets = mutableMapOf<String, DAOWalletData>()
    private val listeners = mutableListOf<(List<DAOWalletSummary>) -> Unit>()

    companion object {
        @Volatile
        private var INSTANCE: DAOMnemonicWallet? = null

        fun getInstance(context: Context, session: Session? = null): DAOMnemonicWallet {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DAOMnemonicWallet(context.applicationContext, session).also { INSTANCE = it }
            }
        }
    }

    init {
        loadWalletsFromStorage()
    }

    fun generateNewMnemonic(): String {
        val words = listOf(
            "abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract", "absurd", "abuse",
            "access", "accident", "account", "accuse", "achieve", "acid", "acoustic", "acquire", "across", "act",
            "action", "actor", "actress", "actual", "adapt", "add", "addict", "address", "adjust", "admit",
            "adult", "advance", "advice", "aerobic", "affair", "afford", "afraid", "again", "age", "agent",
            "agree", "ahead", "aim", "air", "airport", "aisle", "alarm", "album", "alcohol", "alert",
            "alien", "all", "alley", "allow", "almost", "alone", "alpha", "already", "also", "alter"
        )
        
        val random = SecureRandom()
        return (1..12).map { words[random.nextInt(words.size)] }.joinToString(" ")
    }

    fun createDAOWallet(daoId: String, daoName: String, currency: String = "B", contributionValue: Long = 1): DAOWalletData {
        val mnemonic = generateNewMnemonic()
        return createDAOWalletFromMnemonic(daoId, daoName, currency, contributionValue, mnemonic)
    }

    fun createDAOWalletFromMnemonic(
        daoId: String,
        daoName: String,
        currency: String,
        contributionValue: Long,
        mnemonic: String
    ): DAOWalletData {
        if (!validateMnemonicPhrase(mnemonic)) {
            throw IllegalArgumentException("Invalid mnemonic phrase")
        }

        val address = generateAddressFromMnemonic(mnemonic)
        val privateKey = generatePrivateKeyFromMnemonic(mnemonic)
        
        // 디버깅을 위한 로그
        android.util.Log.d("WalletDebug", "Creating wallet with daoId: $daoId")
        android.util.Log.d("WalletDebug", "Generated address: $address")
        android.util.Log.d("WalletDebug", "Generated privateKey: $privateKey")
        
        // 간단한 잔액 설정
        val recoveredBalance = 0L
        
        val daoWallet = DAOWalletData(
            daoId = daoId,
            daoName = daoName,
            mnemonic = mnemonic,
            address = address,
            privateKey = privateKey,
            currency = currency,
            balance = recoveredBalance,
            contributionValue = contributionValue,
            createdAt = Date().toString()
        )

        // 기존 지갑이 있다면 모두 삭제하고 새로 생성
        if (daoWallets.isNotEmpty()) {
            android.util.Log.d("WalletDebug", "Clearing existing wallets before creating new one")
            daoWallets.clear()
        }
        
        // 현재 DAO에 지갑 저장
        daoWallets[daoId] = daoWallet
        
        saveWalletsToStorage()
        notifyListeners()

        return daoWallet
    }

    fun getDAOWallet(daoId: String): DAOWalletData? {
        // 먼저 해당 DAO ID로 지갑을 찾아보고, 없으면 첫 번째 지갑을 반환하되 현재 DAO ID로 수정
        val wallet = daoWallets[daoId] ?: daoWallets.values.firstOrNull()
        val result = wallet?.copy(daoId = daoId, daoName = "DAO Wallet")
        
        // 디버깅을 위한 로그
        android.util.Log.d("WalletDebug", "getDAOWallet called with daoId: $daoId")
        android.util.Log.d("WalletDebug", "Found wallet: ${wallet?.address}")
        android.util.Log.d("WalletDebug", "Result wallet address: ${result?.address}")
        android.util.Log.d("WalletDebug", "All wallets: ${daoWallets.keys}")
        
        return result
    }

    fun getAllDAOWallets(): List<DAOWalletSummary> {
        // 중복 제거: 동일한 주소를 가진 지갑들을 하나로 합침
        val uniqueWallets = daoWallets.values.groupBy { it.address }
        return uniqueWallets.values.map { wallets ->
            val firstWallet = wallets.first()
            DAOWalletSummary(
                daoId = firstWallet.daoId,
                daoName = firstWallet.daoName,
                address = firstWallet.address,
                currency = firstWallet.currency,
                balance = firstWallet.balance,
                contributionValue = firstWallet.contributionValue
            )
        }
    }

    fun validateMnemonicPhrase(mnemonic: String): Boolean {
        val words = mnemonic.trim().split("\\s+".toRegex())
        return words.size == 12 && words.all { it.isNotEmpty() }
    }

    fun deleteDAOWallet(daoId: String) {
        daoWallets.remove(daoId)
        saveWalletsToStorage()
        notifyListeners()
    }

    fun clearAllWallets() {
        daoWallets.clear()
        saveWalletsToStorage()
        notifyListeners()
    }

    fun addListener(listener: (List<DAOWalletSummary>) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (List<DAOWalletSummary>) -> Unit) {
        listeners.remove(listener)
    }

    private fun generateAddressFromMnemonic(mnemonic: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(mnemonic.toByteArray())
        return "0x" + hash.joinToString("") { "%02x".format(it) }.take(40)
    }

    private fun generatePrivateKeyFromMnemonic(mnemonic: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(mnemonic.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }


    private fun saveWalletsToStorage() {
        val editor = prefs.edit()
        val walletsJson = daoWallets.values.joinToString("|") { wallet ->
            "${wallet.daoId}:${wallet.daoName}:${wallet.mnemonic}:${wallet.address}:${wallet.privateKey}:${wallet.currency}:${wallet.balance}:${wallet.contributionValue}:${wallet.createdAt}"
        }
        editor.putString("dao_wallets", walletsJson)
        editor.apply()
    }

    private fun loadWalletsFromStorage() {
        val walletsJson = prefs.getString("dao_wallets", null)
        if (walletsJson != null && walletsJson.isNotEmpty()) {
            val walletStrings = walletsJson.split("|")
            for (walletString in walletStrings) {
                val parts = walletString.split(":")
                if (parts.size >= 9) {
                    val address = parts[3]
                    // 잘못된 주소 (matrix.org 등)는 무시
                    if (address.startsWith("0x") && address.length == 42) {
                        val wallet = DAOWalletData(
                            daoId = parts[0],
                            daoName = parts[1],
                            mnemonic = parts[2],
                            address = address,
                            privateKey = parts[4],
                            currency = parts[5],
                            balance = parts[6].toLongOrNull() ?: 0,
                            contributionValue = parts[7].toLongOrNull() ?: 1,
                            createdAt = parts[8]
                        )
                        daoWallets[wallet.daoId] = wallet
                    } else {
                        android.util.Log.d("WalletDebug", "Skipping invalid wallet address: $address")
                    }
                }
            }
        }
        notifyListeners()
    }

    private fun notifyListeners() {
        val summaries = getAllDAOWallets()
        listeners.forEach { it(summaries) }
    }

    // 프로토콜상 존재하는 모든 DAO에 대한 잔액 조회 (클라이언트에 없는 DAO 포함)
    fun getAllProtocolDAOBalances(): List<DAOWalletSummary> {
        try {
            // 이미 생성된 지갑이 있다면 그 주소를 사용
            val existingWallets = getAllDAOWallets()
            if (existingWallets.isEmpty()) {
                return emptyList() // 지갑이 없으면 빈 리스트 반환
            }

            // TODO: Matrix SDK의 올바른 API를 사용하여 모든 스페이스 룸을 가져와야 함
            // 현재는 기존 지갑들만 반환 (하나의 지갑이 모든 DAO에 적용됨)
            return existingWallets.sortedByDescending { it.balance } // 잔액 많은 순으로 정렬
        } catch (error: Exception) {
            // 실패시 기존 지갑만 반환
            return getAllDAOWallets()
        }
    }

    // 원장에서 지갑 주소의 최신 잔액 복구
    private fun recoverBalanceFromLedger(): Long {
        try {
            // TODO: Matrix SDK의 올바른 API를 사용하여 원장에서 잔액을 조회해야 함
            // 현재는 기본값 0 반환
            return 0L
        } catch (error: Exception) {
            return 0L
        }
    }

    // DAO의 원장 룸 찾기
    private fun findLedgerRoom(): String? {
        try {
            // TODO: Matrix SDK의 올바른 API를 사용하여 DAO 스페이스의 자식 룸 중 "ledger" 이름의 룸 찾기
            return null
        } catch (error: Exception) {
            return null
        }
    }
}
