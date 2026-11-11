pub const LAYER_BYTES: usize = 2048;

// Adds delta to every 4-bit light value, clamped to 0..=15.
pub fn shift_nibbles(src: &[u8], dst: &mut [u8], delta: i32) {
    for (out, &packed) in dst.iter_mut().zip(src) {
        let low = ((packed & 0x0F) as i32 + delta).clamp(0, 15) as u8;
        let high = ((packed >> 4) as i32 + delta).clamp(0, 15) as u8;
        *out = low | (high << 4);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn darkens_and_clamps_both_nibbles() {
        let src = [0xF0u8, 0x0F, 0x35, 0x00];
        let mut dst = [0u8; 4];
        shift_nibbles(&src, &mut dst, -5);
        assert_eq!(dst, [0xA0, 0x0A, 0x00, 0x00]);
    }

    #[test]
    fn brightens_and_clamps_both_nibbles() {
        let src = [0x0Cu8, 0xC0, 0x21];
        let mut dst = [0u8; 3];
        shift_nibbles(&src, &mut dst, 5);
        assert_eq!(dst, [0x5F, 0xF5, 0x76]);
    }
}
