/*
 * Copyright (C) 2026 The Crake Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.lib.util

enum class CryptoChain(val displayName: String, val symbol: String, val prefixHint: String) {
    BTC("Bitcoin", "BTC", "bc1q..., 1..., 3..."),
    ETH("Ethereum", "ETH", "0x... (42 hex chars)"),
    LTC("Litecoin", "LTC", "ltc1..., L..., M..."),
    XMR("Monero", "XMR", "4... or 8... (95-106 chars)"),
    RUNE("THORChain", "RUNE", "thor1... (38-44 chars)"),
    ATOM("Cosmos Hub", "ATOM", "cosmos1... (38-45 chars)"),
    ARB("Arbitrum", "ARB", "0x... (42 hex chars)"),
    UNKNOWN("Generic / Other", "CUSTOM", "");

    companion object {
        fun detectFromShortcut(shortcut: String): CryptoChain? {
            val s = shortcut.lowercase().removePrefix("!")
            return when {
                s.startsWith("btc") -> BTC
                s.startsWith("eth") -> ETH
                s.startsWith("ltc") -> LTC
                s.startsWith("xmr") -> XMR
                s.startsWith("rune") || s.startsWith("thor") -> RUNE
                s.startsWith("atom") || s.startsWith("cosmos") -> ATOM
                s.startsWith("arb") -> ARB
                else -> null
            }
        }

        fun detectFromAddress(address: String): CryptoChain? {
            val a = address.trim()
            if (a.isBlank()) return null
            return when {
                XMR.validate(a).isValid -> XMR
                BTC.validate(a).isValid -> BTC
                ETH.validate(a).isValid -> ETH
                LTC.validate(a).isValid -> LTC
                RUNE.validate(a).isValid -> RUNE
                ATOM.validate(a).isValid -> ATOM
                else -> null
            }
        }
    }

    fun validate(address: String): ValidationResult {
        val trimmed = address.trim()
        if (trimmed.isBlank()) {
            return ValidationResult(isValid = false, message = "Address cannot be empty")
        }
        return when (this) {
            BTC -> {
                val btcLegacySegwit = Regex("^[13][a-km-zA-HJ-NP-Z1-9]{25,34}$")
                val btcBech32 = Regex("^(bc1)[0-9a-zA-HJ-NP-Zac-hj-np-z]{38,62}$", RegexOption.IGNORE_CASE)
                if (btcLegacySegwit.matches(trimmed) || btcBech32.matches(trimmed)) {
                    ValidationResult(true, "Valid Bitcoin address (Base58 / Bech32 / Taproot)")
                } else {
                    ValidationResult(false, "This does not look like a valid BTC address (expected bc1q..., 1..., or 3...)")
                }
            }
            ETH, ARB -> {
                val ethRegex = Regex("^0x[0-9a-fA-F]{40}$")
                if (ethRegex.matches(trimmed)) {
                    ValidationResult(true, "Valid ${displayName} address (42-char hex)")
                } else {
                    ValidationResult(false, "This does not look like a valid ${symbol} address (expected 0x followed by 40 hex characters)")
                }
            }
            LTC -> {
                val ltcLegacy = Regex("^[LM3][a-km-zA-HJ-NP-Z1-9]{26,34}$")
                val ltcBech32 = Regex("^(ltc1)[0-9a-zA-HJ-NP-Zac-hj-np-z]{38,58}$", RegexOption.IGNORE_CASE)
                if (ltcLegacy.matches(trimmed) || ltcBech32.matches(trimmed)) {
                    ValidationResult(true, "Valid Litecoin address (Base58 / Bech32)")
                } else {
                    ValidationResult(false, "This does not look like a valid LTC address (expected ltc1..., L..., or M...)")
                }
            }
            XMR -> {
                val xmrRegex = Regex("^[48][0-9ABCT-Za-km-z]{94,105}$")
                if (xmrRegex.matches(trimmed) && (trimmed.length == 95 || trimmed.length == 106)) {
                    ValidationResult(true, "Valid Monero address (Standard / Subaddress / Integrated)")
                } else {
                    ValidationResult(false, "This does not look like a valid XMR address (expected 95 or 106 chars starting with 4 or 8)")
                }
            }
            RUNE -> {
                val runeRegex = Regex("^thor1[02-9ac-hj-np-z]{38,42}$", RegexOption.IGNORE_CASE)
                if (runeRegex.matches(trimmed)) {
                    ValidationResult(true, "Valid THORChain (RUNE) Bech32 address")
                } else {
                    ValidationResult(false, "This does not look like a valid RUNE address (expected thor1...)")
                }
            }
            ATOM -> {
                val atomRegex = Regex("^cosmos1[02-9ac-hj-np-z]{38,45}$", RegexOption.IGNORE_CASE)
                if (atomRegex.matches(trimmed)) {
                    ValidationResult(true, "Valid Cosmos Hub (ATOM) Bech32 address")
                } else {
                    ValidationResult(false, "This does not look like a valid ATOM address (expected cosmos1...)")
                }
            }
            UNKNOWN -> ValidationResult(true, "Custom Text Snippet")
        }
    }
}

data class ValidationResult(val isValid: Boolean, val message: String)
