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
    BTC("Bitcoin", "BTC", "bc1q..., bc1p..., 1..., 3..."),
    ETH("Ethereum", "ETH", "0x... (42 hex chars)"),
    SOL("Solana", "SOL", "32-44 Base58 chars"),
    XMR("Monero", "XMR", "4... or 8... (95-106 chars)"),
    TRX("Tron / USDT", "TRX", "T... (34 Base58 chars)"),
    LTC("Litecoin", "LTC", "ltc1..., L..., M..."),
    BNB("BNB Chain", "BNB", "0x... or bnb1..."),
    POL("Polygon", "POL", "0x... (42 hex chars)"),
    AVAX("Avalanche", "AVAX", "0x... or X-avax1..."),
    BASE("Base", "BASE", "0x... (42 hex chars)"),
    ARB("Arbitrum", "ARB", "0x... (42 hex chars)"),
    OP("Optimism", "OP", "0x... (42 hex chars)"),
    ADA("Cardano", "ADA", "addr1... (Bech32)"),
    DOGE("Dogecoin", "DOGE", "D... (34 chars)"),
    XRP("Ripple", "XRP", "r... (25-35 chars)"),
    DOT("Polkadot", "DOT", "1... (47-48 chars)"),
    RUNE("THORChain", "RUNE", "thor1... (38-44 chars)"),
    ATOM("Cosmos Hub", "ATOM", "cosmos1... (38-45 chars)"),
    UNKNOWN("Generic / Other", "CUSTOM", "");

    companion object {
        fun detectFromShortcut(shortcut: String): CryptoChain? {
            val s = shortcut.lowercase().removePrefix("!").trim()
            return when {
                s.startsWith("btc") -> BTC
                s.startsWith("eth") -> ETH
                s.startsWith("sol") -> SOL
                s.startsWith("xmr") -> XMR
                s.startsWith("trx") || s.startsWith("usdt") -> TRX
                s.startsWith("ltc") -> LTC
                s.startsWith("bnb") || s.startsWith("bsc") -> BNB
                s.startsWith("pol") || s.startsWith("matic") -> POL
                s.startsWith("avax") -> AVAX
                s.startsWith("base") -> BASE
                s.startsWith("arb") -> ARB
                s.startsWith("op") -> OP
                s.startsWith("ada") || s.startsWith("cardano") -> ADA
                s.startsWith("doge") -> DOGE
                s.startsWith("xrp") || s.startsWith("ripple") -> XRP
                s.startsWith("dot") || s.startsWith("polkadot") -> DOT
                s.startsWith("rune") || s.startsWith("thor") -> RUNE
                s.startsWith("atom") || s.startsWith("cosmos") -> ATOM
                else -> null
            }
        }

        fun detectFromAddress(address: String): CryptoChain? {
            val a = address.trim()
            if (a.isBlank()) return null
            return when {
                XMR.validate(a).isValid -> XMR
                SOL.validate(a).isValid -> SOL
                TRX.validate(a).isValid -> TRX
                BTC.validate(a).isValid -> BTC
                ETH.validate(a).isValid -> ETH
                LTC.validate(a).isValid -> LTC
                ADA.validate(a).isValid -> ADA
                DOGE.validate(a).isValid -> DOGE
                XRP.validate(a).isValid -> XRP
                DOT.validate(a).isValid -> DOT
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
                    ValidationResult(false, "This does not look like a valid BTC address (expected bc1q..., bc1p..., 1..., or 3...)")
                }
            }
            ETH, POL, AVAX, BASE, ARB, OP, BNB -> {
                val ethRegex = Regex("^0x[0-9a-fA-F]{40}$")
                val bnbBech32 = Regex("^bnb1[02-9ac-hj-np-z]{38,45}$", RegexOption.IGNORE_CASE)
                val avaxChain = Regex("^[XP]-avax1[02-9ac-hj-np-z]{38,45}$", RegexOption.IGNORE_CASE)
                if (ethRegex.matches(trimmed) || (this == BNB && bnbBech32.matches(trimmed)) || (this == AVAX && avaxChain.matches(trimmed))) {
                    ValidationResult(true, "Valid ${displayName} address")
                } else {
                    ValidationResult(false, "This does not look like a valid ${symbol} address (expected 0x followed by 40 hex characters)")
                }
            }
            SOL -> {
                val solRegex = Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$")
                if (solRegex.matches(trimmed)) {
                    ValidationResult(true, "Valid Solana address (Base58, ${trimmed.length} chars)")
                } else {
                    ValidationResult(false, "This does not look like a valid Solana address (expected 32-44 Base58 characters)")
                }
            }
            TRX -> {
                val trxRegex = Regex("^T[1-9A-HJ-NP-Za-km-z]{33}$")
                if (trxRegex.matches(trimmed)) {
                    ValidationResult(true, "Valid Tron (TRC20 / TRX) address")
                } else {
                    ValidationResult(false, "This does not look like a valid Tron address (expected 34 chars starting with 'T')")
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
            ADA -> {
                val adaRegex = Regex("^(addr1|addr_test1)[02-9ac-hj-np-z]{50,110}$", RegexOption.IGNORE_CASE)
                if (adaRegex.matches(trimmed) || (trimmed.startsWith("Ae2") || trimmed.startsWith("DdzFF"))) {
                    ValidationResult(true, "Valid Cardano (ADA) address")
                } else {
                    ValidationResult(false, "This does not look like a valid Cardano address (expected addr1...)")
                }
            }
            DOGE -> {
                val dogeRegex = Regex("^D[5-9A-HJ-NP-U][1-9A-HJ-NP-Za-km-z]{32}$")
                if (dogeRegex.matches(trimmed)) {
                    ValidationResult(true, "Valid Dogecoin address")
                } else {
                    ValidationResult(false, "This does not look like a valid Dogecoin address (expected 34 chars starting with 'D')")
                }
            }
            XRP -> {
                val xrpRegex = Regex("^r[0-9a-zA-Z]{24,34}$")
                if (xrpRegex.matches(trimmed)) {
                    ValidationResult(true, "Valid Ripple (XRP) address")
                } else {
                    ValidationResult(false, "This does not look like a valid XRP address (expected r...)")
                }
            }
            DOT -> {
                val dotRegex = Regex("^1[0-9a-zA-Z]{46,47}$")
                if (dotRegex.matches(trimmed)) {
                    ValidationResult(true, "Valid Polkadot (DOT) address")
                } else {
                    ValidationResult(false, "This does not look like a valid DOT address (expected 1...)")
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
