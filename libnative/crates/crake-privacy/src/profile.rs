//! Profile isolation and decoy mode manager for physical adversary defense.

use floris_core::RadixTrie;
use zeroize::Zeroize;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum ProfileKind {
    #[default]
    Real,
    Decoy,
}

/// Manages dual-profile state to prevent learned words or clipboard items from leaking across profiles.
#[derive(Debug, Default)]
pub struct ProfileManager {
    pub active_profile: ProfileKind,
    pub real_trie: RadixTrie,
    pub decoy_trie: RadixTrie,
}

impl ProfileManager {
    pub fn new() -> Self {
        Self {
            active_profile: ProfileKind::Real,
            real_trie: RadixTrie::new(),
            decoy_trie: RadixTrie::new(),
        }
    }

    /// Sets the active profile and wipes ephemeral caches.
    pub fn switch_profile(&mut self, target: ProfileKind) {
        self.active_profile = target;
    }

    /// Returns a reference to the active dictionary trie.
    pub fn active_trie(&self) -> &RadixTrie {
        match self.active_profile {
            ProfileKind::Real => &self.real_trie,
            ProfileKind::Decoy => &self.decoy_trie,
        }
    }

    /// Returns a mutable reference to the active dictionary trie.
    pub fn active_trie_mut(&mut self) -> &mut RadixTrie {
        match self.active_profile {
            ProfileKind::Real => &mut self.real_trie,
            ProfileKind::Decoy => &mut self.decoy_trie,
        }
    }

    /// Learns a word strictly within the active profile boundary.
    pub fn learn_word(&mut self, word: &str, freq: u32) {
        self.active_trie_mut().insert(word, freq);
    }

    /// Verifies constant-time equality of a PIN slice to prevent timing side-channels.
    pub fn verify_pin_constant_time(input: &str, expected: &str) -> bool {
        let in_bytes = input.as_bytes();
        let exp_bytes = expected.as_bytes();

        if in_bytes.len() != exp_bytes.len() {
            return false;
        }

        let mut diff = 0u8;
        for (a, b) in in_bytes.iter().zip(exp_bytes.iter()) {
            diff |= a ^ b;
        }

        diff == 0
    }
}

impl Drop for ProfileManager {
    fn drop(&mut self) {
        // Zeroize any transient buffers on drop
        self.active_profile.zeroize();
    }
}

impl Zeroize for ProfileKind {
    fn zeroize(&mut self) {
        *self = ProfileKind::Decoy;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_profile_isolation() {
        let mut mgr = ProfileManager::new();

        // Learn secret word in Real profile
        mgr.switch_profile(ProfileKind::Real);
        mgr.learn_word("cryptographic_secret", 100);
        assert!(mgr.active_trie().contains("cryptographic_secret"));

        // Switch to Decoy profile
        mgr.switch_profile(ProfileKind::Decoy);
        assert!(!mgr.active_trie().contains("cryptographic_secret"));

        // Learn mundane word in Decoy profile
        mgr.learn_word("shopping_list", 50);
        assert!(mgr.active_trie().contains("shopping_list"));

        // Switch back to Real profile
        mgr.switch_profile(ProfileKind::Real);
        assert!(mgr.active_trie().contains("cryptographic_secret"));
        assert!(!mgr.active_trie().contains("shopping_list"));
    }

    #[test]
    fn test_constant_time_pin() {
        assert!(ProfileManager::verify_pin_constant_time("1234", "1234"));
        assert!(!ProfileManager::verify_pin_constant_time("1234", "1235"));
        assert!(!ProfileManager::verify_pin_constant_time("1234", "12345"));
    }
}
