/*
 * Copyright 2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.wallet

import android.content.Context
import android.content.SharedPreferences
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.matrix.android.sdk.api.session.Session

@RunWith(MockitoJUnitRunner::class)
class DAOMnemonicWalletTest {

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var session: Session

    @Mock
    private lateinit var sharedPreferences: SharedPreferences

    @Mock
    private lateinit var editor: SharedPreferences.Editor

    private lateinit var wallet: DAOMnemonicWallet

    @Before
    fun setUp() {
        `when`(context.getSharedPreferences("dao_wallet", Context.MODE_PRIVATE))
            .thenReturn(sharedPreferences)
        `when`(sharedPreferences.edit()).thenReturn(editor)
        `when`(editor.putString(anyString(), anyString())).thenReturn(editor)
        `when`(editor.apply()).then { }
        
        wallet = DAOMnemonicWallet.getInstance(context, session)
    }

    @Test
    fun `test generateNewMnemonic returns valid mnemonic`() {
        // When
        val mnemonic = wallet.generateNewMnemonic()
        
        // Then
        assertNotNull(mnemonic)
        assertTrue(mnemonic.isNotEmpty())
        
        val words = mnemonic.split(" ")
        assertEquals(12, words.size)
        assertTrue(words.all { it.isNotEmpty() })
    }

    @Test
    fun `test validateMnemonicPhrase returns true for valid mnemonic`() {
        // Given
        val validMnemonic = "abandon ability able about above absent absorb abstract absurd abuse access accident"
        
        // When
        val result = wallet.validateMnemonicPhrase(validMnemonic)
        
        // Then
        assertTrue(result)
    }

    @Test
    fun `test validateMnemonicPhrase returns false for invalid mnemonic`() {
        // Given
        val invalidMnemonic = "abandon ability able about above absent absorb abstract absurd abuse access"
        
        // When
        val result = wallet.validateMnemonicPhrase(invalidMnemonic)
        
        // Then
        assertFalse(result)
    }

    @Test
    fun `test createDAOWallet creates wallet with correct properties`() {
        // Given
        val daoId = "!dao:example.com"
        val daoName = "Test DAO"
        val currency = "B"
        val contributionValue = 1L
        
        // When
        val walletData = wallet.createDAOWallet(daoId, daoName, currency, contributionValue)
        
        // Then
        assertNotNull(walletData)
        assertEquals(daoId, walletData.daoId)
        assertEquals(daoName, walletData.daoName)
        assertEquals(currency, walletData.currency)
        assertEquals(contributionValue, walletData.contributionValue)
        assertTrue(walletData.address.startsWith("0x"))
        assertEquals(42, walletData.address.length)
        assertTrue(walletData.mnemonic.isNotEmpty())
        assertTrue(walletData.privateKey.isNotEmpty())
    }

    @Test
    fun `test getWalletAddressForDCA returns wallet address`() {
        // Given
        val daoId = "!dao:example.com"
        val daoName = "Test DAO"
        val walletData = wallet.createDAOWallet(daoId, daoName)
        
        // When
        val address = wallet.getWalletAddressForDCA(daoId)
        
        // Then
        assertNotNull(address)
        assertEquals(walletData.address, address)
    }

    @Test
    fun `test getWalletAddressForDCA returns null when no wallet exists`() {
        // Given
        val daoId = "!nonexistent:example.com"
        
        // When
        val address = wallet.getWalletAddressForDCA(daoId)
        
        // Then
        assertNull(address)
    }

    @Test
    fun `test createDAOWalletFromMnemonic creates wallet with provided mnemonic`() {
        // Given
        val daoId = "!dao:example.com"
        val daoName = "Test DAO"
        val currency = "B"
        val contributionValue = 1L
        val mnemonic = "abandon ability able about above absent absorb abstract absurd abuse access accident"
        
        // When
        val walletData = wallet.createDAOWalletFromMnemonic(daoId, daoName, currency, contributionValue, mnemonic)
        
        // Then
        assertNotNull(walletData)
        assertEquals(daoId, walletData.daoId)
        assertEquals(daoName, walletData.daoName)
        assertEquals(mnemonic, walletData.mnemonic)
        assertTrue(walletData.address.startsWith("0x"))
        assertEquals(42, walletData.address.length)
    }

    @Test
    fun `test createDAOWalletFromMnemonic throws exception for invalid mnemonic`() {
        // Given
        val daoId = "!dao:example.com"
        val daoName = "Test DAO"
        val currency = "B"
        val contributionValue = 1L
        val invalidMnemonic = "invalid mnemonic"
        
        // When & Then
        assertThrows(IllegalArgumentException::class.java) {
            wallet.createDAOWalletFromMnemonic(daoId, daoName, currency, contributionValue, invalidMnemonic)
        }
    }

    @Test
    fun `test getAllDAOWallets returns all wallets`() {
        // Given
        val daoId1 = "!dao1:example.com"
        val daoId2 = "!dao2:example.com"
        val daoName1 = "Test DAO 1"
        val daoName2 = "Test DAO 2"
        
        wallet.createDAOWallet(daoId1, daoName1)
        wallet.createDAOWallet(daoId2, daoName2)
        
        // When
        val wallets = wallet.getAllDAOWallets()
        
        // Then
        assertEquals(1, wallets.size) // Only one unique wallet address
        assertTrue(wallets.any { it.daoId == daoId1 || it.daoId == daoId2 })
    }

    @Test
    fun `test deleteDAOWallet removes wallet`() {
        // Given
        val daoId = "!dao:example.com"
        val daoName = "Test DAO"
        wallet.createDAOWallet(daoId, daoName)
        
        // When
        wallet.deleteDAOWallet(daoId)
        
        // Then
        val address = wallet.getWalletAddressForDCA(daoId)
        assertNull(address)
    }

    @Test
    fun `test clearAllWallets removes all wallets`() {
        // Given
        val daoId1 = "!dao1:example.com"
        val daoId2 = "!dao2:example.com"
        val daoName1 = "Test DAO 1"
        val daoName2 = "Test DAO 2"
        
        wallet.createDAOWallet(daoId1, daoName1)
        wallet.createDAOWallet(daoId2, daoName2)
        
        // When
        wallet.clearAllWallets()
        
        // Then
        val wallets = wallet.getAllDAOWallets()
        assertTrue(wallets.isEmpty())
    }
}
