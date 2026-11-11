use std::collections::HashMap;
use std::sync::Mutex;

use fxhash::FxBuildHasher;

use crate::nibbles::LAYER_BYTES;

type Key = (i64, i64);

// Compressed 2048-byte light layers keyed by owner and section position.
pub struct LightStore {
    layers: HashMap<Key, Box<[u8]>, FxBuildHasher>,
    compressed_bytes: usize,
}

impl LightStore {
    pub const fn new() -> Self {
        Self {
            layers: HashMap::with_hasher(FxBuildHasher::new()),
            compressed_bytes: 0,
        }
    }

    pub fn put(&mut self, owner: i64, pos: i64, layer: &[u8]) -> bool {
        if layer.len() != LAYER_BYTES {
            return false;
        }
        let packed = lz4_flex::block::compress(layer).into_boxed_slice();
        self.compressed_bytes += packed.len();
        if let Some(old) = self.layers.insert((owner, pos), packed) {
            self.compressed_bytes -= old.len();
        }
        true
    }

    pub fn get(&self, owner: i64, pos: i64, out: &mut [u8]) -> bool {
        let Some(packed) = self.layers.get(&(owner, pos)) else {
            return false;
        };
        matches!(lz4_flex::block::decompress_into(packed, out), Ok(LAYER_BYTES))
    }

    pub fn remove(&mut self, owner: i64, pos: i64) -> bool {
        match self.layers.remove(&(owner, pos)) {
            Some(old) => {
                self.compressed_bytes -= old.len();
                true
            }
            None => false,
        }
    }

    pub fn clear_owner(&mut self, owner: i64) {
        let mut freed = 0;
        self.layers.retain(|key, packed| {
            let keep = key.0 != owner;
            if !keep {
                freed += packed.len();
            }
            keep
        });
        self.compressed_bytes -= freed;
    }

    pub fn len(&self) -> usize {
        self.layers.len()
    }

    pub fn compressed_bytes(&self) -> usize {
        self.compressed_bytes
    }
}

pub static STORE: Mutex<LightStore> = Mutex::new(LightStore::new());

#[cfg(test)]
mod tests {
    use super::*;

    fn layer(seed: u8) -> Vec<u8> {
        (0..LAYER_BYTES).map(|i| ((i / 64) as u8).wrapping_add(seed) & 0xFF).collect()
    }

    #[test]
    fn round_trips_and_tracks_bytes() {
        let mut store = LightStore::new();
        let a = layer(1);
        assert!(store.put(7, 42, &a));
        assert!(store.compressed_bytes() < LAYER_BYTES);
        let mut out = vec![0u8; LAYER_BYTES];
        assert!(store.get(7, 42, &mut out));
        assert_eq!(out, a);
        assert!(!store.get(8, 42, &mut out));
    }

    #[test]
    fn replace_remove_and_clear_keep_byte_count_exact() {
        let mut store = LightStore::new();
        store.put(1, 1, &layer(1));
        store.put(1, 1, &layer(2));
        store.put(1, 2, &layer(3));
        store.put(2, 1, &layer(4));
        assert_eq!(store.len(), 3);
        assert!(store.remove(1, 2));
        assert!(!store.remove(1, 2));
        store.clear_owner(1);
        assert_eq!(store.len(), 1);
        let mut out = vec![0u8; LAYER_BYTES];
        assert!(store.get(2, 1, &mut out));
        let expected = lz4_flex::block::compress(&layer(4)).len();
        assert_eq!(store.compressed_bytes(), expected);
    }

    #[test]
    fn rejects_wrong_sizes() {
        let mut store = LightStore::new();
        assert!(!store.put(1, 1, &[0u8; 10]));
        assert_eq!(store.len(), 0);
    }
}
