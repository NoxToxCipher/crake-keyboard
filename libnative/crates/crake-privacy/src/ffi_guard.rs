//! 5-Layer FFI Guard: Fail-closed panic containment and checked arithmetic bounds defense.

use std::panic::{catch_unwind, AssertUnwindSafe};

/// Errors encountered at the FFI / JNI boundary.
#[derive(Debug, PartialEq, Eq)]
pub enum FfiError {
    /// A panic was caught and contained within the native boundary.
    PanicCaught,
    /// An offset or slice length violated memory bounds.
    BoundsCheckFailed,
    /// The input bytes were not valid UTF-8.
    Utf8Error,
    /// An opaque handle ID was invalid or not found.
    InvalidHandle,
}

impl std::fmt::Display for FfiError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::PanicCaught => write!(f, "Panic caught at FFI boundary"),
            Self::BoundsCheckFailed => write!(f, "Checked arithmetic or slice bounds check failed"),
            Self::Utf8Error => write!(f, "Invalid UTF-8 sequence at FFI boundary"),
            Self::InvalidHandle => write!(f, "Opaque handle lookup failed"),
        }
    }
}

impl std::error::Error for FfiError {}

/// Executes a closure inside a fail-closed panic boundary.
/// If the closure panics, the panic is caught and mapped to `FfiError::PanicCaught`.
///
/// # Errors
/// Returns `FfiError::PanicCaught` if the internal operation panics.
pub fn catch_ffi_panic<F, T>(op: F) -> Result<T, FfiError>
where
    F: FnOnce() -> T,
{
    match catch_unwind(AssertUnwindSafe(op)) {
        Ok(val) => Ok(val),
        Err(_) => Err(FfiError::PanicCaught),
    }
}

/// Checked byte slice extraction guarding against integer overflow on 32-bit/64-bit targets.
///
/// # Errors
/// Returns `FfiError::BoundsCheckFailed` if `offset + len` overflows or exceeds `data.len()`.
pub fn checked_slice(data: &[u8], offset: usize, len: usize) -> Result<&[u8], FfiError> {
    let end = offset.checked_add(len).ok_or(FfiError::BoundsCheckFailed)?;
    if end > data.len() {
        return Err(FfiError::BoundsCheckFailed);
    }
    Ok(&data[offset..end])
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_checked_slice_overflow_protection() {
        let buf = [1u8, 2, 3, 4, 5];
        assert_eq!(checked_slice(&buf, 1, 3).unwrap(), &[2, 3, 4]);

        // Out of bounds
        assert_eq!(checked_slice(&buf, 3, 5), Err(FfiError::BoundsCheckFailed));

        // Integer overflow
        assert_eq!(
            checked_slice(&buf, usize::MAX, 1),
            Err(FfiError::BoundsCheckFailed)
        );
    }

    #[test]
    fn test_panic_containment() {
        let result = catch_ffi_panic(|| {
            panic!("Simulated internal engine panic!");
        });

        assert_eq!(result, Err(FfiError::PanicCaught));
    }
}
