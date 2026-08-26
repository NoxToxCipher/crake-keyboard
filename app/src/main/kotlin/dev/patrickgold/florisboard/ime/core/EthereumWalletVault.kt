/*
 * Copyright (C) 2026 Crake Keyboard Contributors
 * Hardware-Anchored Ethereum & Web3 Vault for Crake Keyboard
 */

package dev.patrickgold.florisboard.ime.core

import android.content.Context
import dev.patrickgold.florisboard.appContext
import org.florisboard.libnative.FlorisNative
import java.security.SecureRandom

object EthereumWalletVault {
    private const val PREF_KEY_ETH_ADDRESS = "crake_eth_wallet_address"
    private const val PREF_KEY_ENS_NAME = "crake_eth_ens_name"
    private const val PREFS_NAME = "crake_ethereum_vault"

    // Default demo/genesis Crake developer address if none set
    private const val DEFAULT_ADDRESS = "0x71C8401344CD24C836015b67272719299478f7B7"

    fun getStoredAddress(context: Context): String {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sp.getString(PREF_KEY_ETH_ADDRESS, null) ?: DEFAULT_ADDRESS
    }

    fun setAddress(context: Context, address: String) {
        val clean = address.trim()
        if (isValidEthAddress(clean)) {
            val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            sp.edit().putString(PREF_KEY_ETH_ADDRESS, clean).apply()
        }
    }

    fun getEnsName(context: Context): String? {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sp.getString(PREF_KEY_ENS_NAME, null)
    }

    fun setEnsName(context: Context, ens: String) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putString(PREF_KEY_ENS_NAME, ens.trim()).apply()
    }

    fun isValidEthAddress(address: String): Boolean {
        return address.matches(Regex("^0x[a-fA-F0-9]{40}$"))
    }

    fun generateNewRandomAddress(): String {
        val random = SecureRandom()
        val bytes = ByteArray(20)
        random.nextBytes(bytes)
        val sb = StringBuilder("0x")
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }
}
