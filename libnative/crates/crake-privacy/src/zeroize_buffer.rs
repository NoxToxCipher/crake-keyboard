//! Ephemeral zeroize buffer ensuring sensitive text and keys are cryptographically cleared from RAM on drop.

use zeroize::{Zeroize, Zeroizing};

#[derive(Debug, Default)]
pub struct EphemeralBuffer {
    inner: Zeroizing<Vec<u8>>,
}

impl EphemeralBuffer {
    pub fn new() -> Self {
        Self {
            inner: Zeroizing::new(Vec::new()),
        }
    }

    pub fn from_str(text: &str) -> Self {
        Self {
            inner: Zeroizing::new(text.as_bytes().to_vec()),
        }
    }

    pub fn from_bytes(bytes: &[u8]) -> Self {
        Self {
            inner: Zeroizing::new(bytes.to_vec()),
        }
    }

    pub fn push_str(&mut self, s: &str) {
        self.inner.extend_from_slice(s.as_bytes());
    }

    pub fn len(&self) -> usize {
        self.inner.len()
    }

    pub fn is_empty(&self) -> bool {
        self.inner.is_empty()
    }

    pub fn as_bytes(&self) -> &[u8] {
        &self.inner
    }

    pub fn as_str(&self) -> Result<&str, std::str::Utf8Error> {
        std::str::from_utf8(&self.inner)
    }

    /// Explicitly zeroes and empties the buffer.
    pub fn clear(&mut self) {
        self.inner.zeroize();
        self.inner.clear();
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_ephemeral_buffer_lifecycle() {
        let mut buf = EphemeralBuffer::from_str("my_secret_passphrase_123");
        assert_eq!(buf.as_str().unwrap(), "my_secret_passphrase_123");
        assert_eq!(buf.len(), 24);

        buf.clear();
        assert!(buf.is_empty());
    }
}
