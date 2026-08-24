//! Ephemeral zeroize buffer ensuring sensitive text and keys are cryptographically cleared from RAM on drop.

use std::convert::Infallible;
use std::str::FromStr;
use zeroize::{Zeroize, Zeroizing};

/// A secure memory buffer that automatically zeroes out its contents when dropped from RAM.
#[derive(Debug, Default)]
pub struct EphemeralBuffer {
    inner: Zeroizing<Vec<u8>>,
}

impl EphemeralBuffer {
    /// Creates a new, empty `EphemeralBuffer`.
    #[must_use]
    pub fn new() -> Self {
        Self {
            inner: Zeroizing::new(Vec::new()),
        }
    }

    /// Creates an `EphemeralBuffer` initialized with a string slice.
    #[must_use]
    pub fn from_text(text: &str) -> Self {
        Self {
            inner: Zeroizing::new(text.as_bytes().to_vec()),
        }
    }

    /// Creates an `EphemeralBuffer` initialized with raw bytes.
    #[must_use]
    pub fn from_bytes(bytes: &[u8]) -> Self {
        Self {
            inner: Zeroizing::new(bytes.to_vec()),
        }
    }

    /// Appends a string slice to the buffer.
    pub fn push_str(&mut self, s: &str) {
        self.inner.extend_from_slice(s.as_bytes());
    }

    /// Returns the length of the buffer in bytes.
    #[must_use]
    pub fn len(&self) -> usize {
        self.inner.len()
    }

    /// Returns `true` if the buffer is empty.
    #[must_use]
    pub fn is_empty(&self) -> bool {
        self.inner.is_empty()
    }

    /// Returns a byte slice view of the buffer contents.
    #[must_use]
    pub fn as_bytes(&self) -> &[u8] {
        &self.inner
    }

    /// Attempts to interpret the buffer as a UTF-8 string.
    ///
    /// # Errors
    /// Returns `std::str::Utf8Error` if the byte sequence is not valid UTF-8.
    pub fn as_str(&self) -> Result<&str, std::str::Utf8Error> {
        std::str::from_utf8(&self.inner)
    }

    /// Explicitly zeroes and empties the buffer.
    pub fn clear(&mut self) {
        self.inner.zeroize();
        self.inner.clear();
    }
}

impl From<&str> for EphemeralBuffer {
    fn from(s: &str) -> Self {
        Self::from_text(s)
    }
}

impl From<String> for EphemeralBuffer {
    fn from(s: String) -> Self {
        Self {
            inner: Zeroizing::new(s.into_bytes()),
        }
    }
}

impl FromStr for EphemeralBuffer {
    type Err = Infallible;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        Ok(Self::from_text(s))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_ephemeral_buffer_lifecycle() {
        let mut buf = EphemeralBuffer::from_text("my_secret_passphrase_123");
        assert_eq!(buf.as_str().unwrap(), "my_secret_passphrase_123");
        assert_eq!(buf.len(), 24);

        buf.clear();
        assert!(buf.is_empty());
    }

    #[test]
    fn test_from_traits() {
        let buf_str: EphemeralBuffer = "passphrase".into();
        assert_eq!(buf_str.as_str().unwrap(), "passphrase");

        let buf_parsed: EphemeralBuffer = "parsed_pass".parse().unwrap();
        assert_eq!(buf_parsed.as_str().unwrap(), "parsed_pass");
    }
}
