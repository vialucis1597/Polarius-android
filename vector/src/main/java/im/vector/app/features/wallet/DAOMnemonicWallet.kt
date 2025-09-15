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
    private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("dao_wallet", Context.MODE_PRIVATE)
    private val daoWallets = mutableMapOf<String, DAOWalletData>()
    private val listeners = mutableListOf<(List<DAOWalletSummary>) -> Unit>()

    companion object {
        @Volatile
        private var INSTANCE: DAOMnemonicWallet? = null

        fun getInstance(context: Context): DAOMnemonicWallet {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DAOMnemonicWallet(context.applicationContext).also { INSTANCE = it }
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

        daoWallets[daoId] = daoWallet
        saveWalletsToStorage()
        notifyListeners()

        return daoWallet
    }

    fun getDAOWallet(daoId: String): DAOWalletData? {
        return daoWallets[daoId]
    }

    fun getAllDAOWallets(): List<DAOWalletSummary> {
        return daoWallets.values.map { wallet ->
            DAOWalletSummary(
                daoId = wallet.daoId,
                daoName = wallet.daoName,
                address = wallet.address,
                currency = wallet.currency,
                balance = wallet.balance,
                contributionValue = wallet.contributionValue
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
                    val wallet = DAOWalletData(
                        daoId = parts[0],
                        daoName = parts[1],
                        mnemonic = parts[2],
                        address = parts[3],
                        privateKey = parts[4],
                        currency = parts[5],
                        balance = parts[6].toLongOrNull() ?: 0,
                        contributionValue = parts[7].toLongOrNull() ?: 1,
                        createdAt = parts[8]
                    )
                    daoWallets[wallet.daoId] = wallet
                }
            }
        }
        notifyListeners()
    }

    private fun notifyListeners() {
        val summaries = getAllDAOWallets()
        listeners.forEach { it(summaries) }
    }
}
