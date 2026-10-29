use std::cell::RefCell;

use fxhash::FxHashMap;

// Spatial hash in CSR form; backing vectors survive across ticks.
struct CellCsr {
    slots: FxHashMap<u64, u32>,
    cell_of: Vec<u32>,
    counts: Vec<u32>,
    starts: Vec<u32>,
    entries: Vec<u32>,
}

impl CellCsr {
    fn build(&mut self, inputs: &[CrammingInput], cell_size: f64) {
        self.slots.clear();
        self.cell_of.clear();
        self.counts.clear();
        self.starts.clear();
        self.entries.clear();
        for input in inputs {
            let (cx, cz) = cell_coords(input.x, input.z, cell_size);
            let key = cell_hash(cx, cz);
            let next = self.counts.len() as u32;
            let slot = *self.slots.entry(key).or_insert(next);
            if slot == next {
                self.counts.push(0);
            }
            self.counts[slot as usize] += 1;
            self.cell_of.push(slot);
        }
        let mut acc = 0u32;
        self.starts.push(0);
        for c in &self.counts {
            acc += c;
            self.starts.push(acc);
        }
        self.entries.resize(inputs.len(), 0);
        for c in self.counts.iter_mut() {
            *c = 0;
        }
        for (i, &slot) in self.cell_of.iter().enumerate() {
            let at = self.starts[slot as usize] + self.counts[slot as usize];
            self.entries[at as usize] = i as u32;
            self.counts[slot as usize] += 1;
        }
    }

    #[inline]
    fn cell_entries(&self, key: u64) -> &[u32] {
        match self.slots.get(&key) {
            Some(&slot) => {
                let s = self.starts[slot as usize] as usize;
                let e = self.starts[slot as usize + 1] as usize;
                &self.entries[s..e]
            }
            None => &[],
        }
    }
}

thread_local! {
    static CELL_HASH: RefCell<CellCsr> = RefCell::new(CellCsr {
        slots: FxHashMap::default(),
        cell_of: Vec::new(),
        counts: Vec::new(),
        starts: Vec::new(),
        entries: Vec::new(),
    });
}

const BRUTE_FORCE_THRESHOLD: usize = 16;

pub const FLAG_PUSHABLE: u8 = 1 << 0;
pub const FLAG_VEHICLE: u8 = 1 << 1;
pub const FLAG_PASSENGER: u8 = 1 << 2;
pub const FLAG_NO_PHYSICS: u8 = 1 << 3;

const CONTACT_MIN: f64 = 0.01;
const PUSH_SCALE: f64 = 0.05;
const CELL_SIZE_MIN: f64 = 2.0;

// One pushable mob, 40 bytes, mirrors CrammingHandoff.
#[repr(C)]
#[derive(Copy, Clone, Debug)]
pub struct CrammingInput {
    pub entity_id: u32,
    pub flags: u8,
    pub _pad0: [u8; 3],
    pub x: f64,
    pub z: f64,
    pub aabb_half_width: f32,
    pub aabb_min_y: f32,
    pub aabb_max_y: f32,
    pub root_vehicle_id: i32,
}
const _: [(); 40] = [(); std::mem::size_of::<CrammingInput>()];

// Accumulated push and overlap counts per mob, 32 bytes.
#[repr(C)]
#[derive(Copy, Clone, Debug, Default)]
pub struct CrammingResult {
    pub entity_id: u32,
    pub _pad0: u32,
    pub accum_dx: f64,
    pub accum_dz: f64,
    pub neighbor_count: u32,
    pub crowded_count: u32,
}
const _: [(); 32] = [(); std::mem::size_of::<CrammingResult>()];

#[inline]
fn cell_coords(x: f64, z: f64, cell_size: f64) -> (i32, i32) {
    let cx = (x / cell_size).floor() as i32;
    let cz = (z / cell_size).floor() as i32;
    (cx, cz)
}

// Splitmix64 finalizer over the packed cell coordinates.
#[inline]
fn cell_hash(cx: i32, cz: i32) -> u64 {
    let mut k = ((cz as u32 as u64) << 32) | (cx as u32 as u64);
    k ^= 0xFE221E5EED1A0DBE;
    k = k.wrapping_mul(0xBF58476D1CE4E5B9);
    k ^= k >> 27;
    k = k.wrapping_mul(0x94D049BB133111EB);
    k ^= k >> 31;
    k
}

// Applies the vanilla pair push to every overlapping pair, each pair once.
pub fn compute_cramming(inputs: &[CrammingInput], results: &mut [CrammingResult]) {
    let n = inputs.len();
    if n != results.len() {
        return;
    }

    for i in 0..n {
        results[i] = CrammingResult {
            entity_id: inputs[i].entity_id,
            ..CrammingResult::default()
        };
    }

    if n < 2 {
        return;
    }

    if n < BRUTE_FORCE_THRESHOLD {
        brute_force_cramming(inputs, results);
        return;
    }

    let max_half = inputs.iter().map(|e| e.aabb_half_width).fold(0.0f32, f32::max);
    let cell_size = (2.0 * max_half as f64).max(CELL_SIZE_MIN);

    CELL_HASH.with(|cell_ref| {
        let mut cells = cell_ref.borrow_mut();
        cells.build(inputs, cell_size);

        for i in 0..n {
            let a = inputs[i];
            if (a.flags & FLAG_NO_PHYSICS) != 0 {
                continue;
            }
            let (cx, cz) = cell_coords(a.x, a.z, cell_size);

            for dcx in -1..=1i32 {
                for dcz in -1..=1i32 {
                    let neighbor_key = cell_hash(cx + dcx, cz + dcz);
                    for &j_u32 in cells.cell_entries(neighbor_key) {
                        let j = j_u32 as usize;
                        if j <= i {
                            continue;
                        }
                        process_pair(inputs, results, i, j, &a);
                    }
                }
            }
        }
    });
}

#[inline]
fn brute_force_cramming(inputs: &[CrammingInput], results: &mut [CrammingResult]) {
    let n = inputs.len();
    for i in 0..n {
        let a = inputs[i];
        if (a.flags & FLAG_NO_PHYSICS) != 0 {
            continue;
        }
        for j in (i + 1)..n {
            process_pair(inputs, results, i, j, &a);
        }
    }
}

#[inline]
fn process_pair(
    inputs: &[CrammingInput],
    results: &mut [CrammingResult],
    i: usize,
    j: usize,
    a: &CrammingInput,
) {
    let b = inputs[j];
    if (b.flags & FLAG_NO_PHYSICS) != 0 {
        return;
    }

    if a.root_vehicle_id == b.root_vehicle_id {
        return;
    }

    let dx = b.x - a.x;
    let dz = b.z - a.z;

    let half_sum = a.aabb_half_width as f64 + b.aabb_half_width as f64;
    if dx.abs() >= half_sum || dz.abs() >= half_sum {
        return;
    }
    if !(a.aabb_min_y < b.aabb_max_y && b.aabb_min_y < a.aabb_max_y) {
        return;
    }

    let (head, tail) = results.split_at_mut(j);
    let res_a = &mut head[i];
    let res_b = &mut tail[0];

    // Counting precedes the push distance skip, as vanilla counts by box overlap alone.
    res_a.neighbor_count += 1;
    res_b.neighbor_count += 1;
    if (b.flags & FLAG_PUSHABLE) != 0 && (b.flags & FLAG_PASSENGER) == 0 {
        res_a.crowded_count += 1;
    }
    if (a.flags & FLAG_PUSHABLE) != 0 && (a.flags & FLAG_PASSENGER) == 0 {
        res_b.crowded_count += 1;
    }

    let f = dx.abs().max(dz.abs());
    if f < CONTACT_MIN {
        return;
    }
    let sqrt_f = f.sqrt();
    let dn = dx / sqrt_f;
    let en = dz / sqrt_f;
    let g = (1.0 / sqrt_f).min(1.0);
    let push_dx = dn * g * PUSH_SCALE;
    let push_dz = en * g * PUSH_SCALE;

    let a_eligible = (a.flags & FLAG_PUSHABLE) != 0 && (a.flags & FLAG_VEHICLE) == 0;
    let b_eligible = (b.flags & FLAG_PUSHABLE) != 0 && (b.flags & FLAG_VEHICLE) == 0;

    if a_eligible {
        res_a.accum_dx -= push_dx;
        res_a.accum_dz -= push_dz;
    }
    if b_eligible {
        res_b.accum_dx += push_dx;
        res_b.accum_dz += push_dz;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn make_mob(id: u32, x: f64, z: f64, min_y: f32, max_y: f32) -> CrammingInput {
        CrammingInput {
            entity_id: id,
            flags: FLAG_PUSHABLE,
            _pad0: [0; 3],
            x,
            z,
            aabb_half_width: 0.3,
            aabb_min_y: min_y,
            aabb_max_y: max_y,
            root_vehicle_id: id as i32,
        }
    }

    #[test]
    fn two_overlapping_mobs_push_apart() {
        let inputs = [
            make_mob(1, 0.0, 0.0, 64.0, 66.0),
            make_mob(2, 0.5, 0.5, 64.0, 66.0),
        ];
        let mut results = [CrammingResult::default(); 2];
        compute_cramming(&inputs, &mut results);

        assert!(results[0].accum_dx < 0.0, "A dx = {}", results[0].accum_dx);
        assert!(results[0].accum_dz < 0.0, "A dz = {}", results[0].accum_dz);
        assert!(results[1].accum_dx > 0.0, "B dx = {}", results[1].accum_dx);
        assert!(results[1].accum_dz > 0.0, "B dz = {}", results[1].accum_dz);
        assert_eq!(results[0].neighbor_count, 1);
        assert_eq!(results[1].neighbor_count, 1);

        assert!((results[0].accum_dx + results[1].accum_dx).abs() < 1e-12);
        assert!((results[0].accum_dz + results[1].accum_dz).abs() < 1e-12);

        assert!((results[1].accum_dx - 0.03535534).abs() < 1e-6);
    }

    #[test]
    fn y_separated_mobs_dont_push() {
        let inputs = [
            make_mob(1, 0.0, 0.0, 64.0, 66.0),
            make_mob(2, 0.0, 0.0, 70.0, 72.0),
        ];
        let mut results = [CrammingResult::default(); 2];
        compute_cramming(&inputs, &mut results);

        for r in &results {
            assert_eq!(r.accum_dx, 0.0);
            assert_eq!(r.accum_dz, 0.0);
            assert_eq!(r.neighbor_count, 0);
            assert_eq!(r.crowded_count, 0);
        }
    }

    #[test]
    fn three_mob_accumulation() {
        let inputs = [
            make_mob(1, 0.0, 0.0, 64.0, 66.0),
            make_mob(2, 0.5, 0.0, 64.0, 66.0),
            make_mob(3, 1.0, 0.0, 64.0, 66.0),
        ];
        let mut results = [CrammingResult::default(); 3];
        compute_cramming(&inputs, &mut results);

        assert_eq!(results[0].neighbor_count, 1);
        assert_eq!(results[1].neighbor_count, 2);
        assert_eq!(results[2].neighbor_count, 1);

        assert!(results[0].accum_dx < 0.0);
        assert!(results[2].accum_dx > 0.0);
        assert!(results[1].accum_dx.abs() < 1e-12,
                "B accum_dx should be zero by symmetry, got {}", results[1].accum_dx);
        for r in &results {
            assert!(r.accum_dz.abs() < 1e-12);
        }
    }

    #[test]
    fn non_pushable_does_not_receive_push_but_pushes_neighbor() {
        let mut a = make_mob(1, 0.0, 0.0, 64.0, 66.0);
        let mut b = make_mob(2, 0.5, 0.0, 64.0, 66.0);
        b.flags = 0;
        let inputs = [a, b];
        let mut results = [CrammingResult::default(); 2];
        a.flags |= FLAG_PUSHABLE;
        let _ = a;
        compute_cramming(&inputs, &mut results);

        assert!(results[0].accum_dx < 0.0, "A should still be pushed");
        assert_eq!(results[1].accum_dx, 0.0, "B is not pushable -> no accum");
        assert_eq!(results[0].neighbor_count, 1);
        assert_eq!(results[1].neighbor_count, 1);
    }

    #[test]
    fn same_root_vehicle_skips_pair() {
        let mut a = make_mob(1, 0.0, 0.0, 64.0, 66.0);
        let mut b = make_mob(2, 0.5, 0.0, 64.0, 66.0);
        a.root_vehicle_id = 100;
        b.root_vehicle_id = 100;
        let inputs = [a, b];
        let mut results = [CrammingResult::default(); 2];
        compute_cramming(&inputs, &mut results);

        assert_eq!(results[0].accum_dx, 0.0, "A should not be pushed");
        assert_eq!(results[1].accum_dx, 0.0, "B should not be pushed");
        assert_eq!(results[0].neighbor_count, 0);
        assert_eq!(results[1].neighbor_count, 0);
    }

    #[test]
    fn different_vehicle_or_no_vehicle_pushes_normally() {
        let mut a = make_mob(1, 0.0, 0.0, 64.0, 66.0);
        let mut b = make_mob(2, 0.5, 0.0, 64.0, 66.0);
        a.root_vehicle_id = 100;
        b.root_vehicle_id = 200;
        let inputs = [a, b];
        let mut results = [CrammingResult::default(); 2];
        compute_cramming(&inputs, &mut results);
        assert!(results[0].accum_dx < 0.0);
        assert!(results[1].accum_dx > 0.0);

        let inputs2 = [make_mob(1, 0.0, 0.0, 64.0, 66.0), make_mob(2, 0.5, 0.0, 64.0, 66.0)];
        let mut results2 = [CrammingResult::default(); 2];
        compute_cramming(&inputs2, &mut results2);
        assert!(results2[0].accum_dx < 0.0);
    }

    #[test]
    fn vehicle_does_not_push_its_own_passenger() {
        let mut vehicle = make_mob(100, 0.0, 0.0, 64.0, 66.0);
        vehicle.flags = FLAG_PUSHABLE | FLAG_VEHICLE;
        vehicle.root_vehicle_id = 100;
        let mut passenger = make_mob(101, 0.4, 0.0, 64.0, 66.0);
        passenger.flags = FLAG_PUSHABLE | FLAG_PASSENGER;
        passenger.root_vehicle_id = 100;
        let inputs = [vehicle, passenger];
        let mut results = [CrammingResult::default(); 2];
        compute_cramming(&inputs, &mut results);
        assert_eq!(results[0].accum_dx, 0.0, "vehicle should not be pushed");
        assert_eq!(results[1].accum_dx, 0.0, "passenger should not be pushed by its own mount");
        assert_eq!(results[0].neighbor_count, 0);
        assert_eq!(results[1].neighbor_count, 0);
        assert_eq!(results[0].crowded_count, 0);
        assert_eq!(results[1].crowded_count, 0);
    }

    #[test]
    fn crowded_count_only_counts_pushable_non_passenger() {
        let a = make_mob(1, 0.0, 0.0, 64.0, 66.0);
        let mut b = make_mob(2, 0.4, 0.0, 64.0, 66.0);
        b.flags |= FLAG_PASSENGER;
        let mut c = make_mob(3, 0.0, 0.4, 64.0, 66.0);
        c.flags = 0;
        let inputs = [a, b, c];
        let mut results = [CrammingResult::default(); 3];
        compute_cramming(&inputs, &mut results);

        assert_eq!(results[0].neighbor_count, 2, "A overlaps both B and C");
        assert_eq!(results[0].crowded_count, 0,
                   "A's crowded_count excludes passengers (B) and non-pushables (C)");
    }

    #[test]
    fn crowded_count_counts_normal_pushable_neighbor() {
        let inputs = [
            make_mob(1, 0.0, 0.0, 64.0, 66.0),
            make_mob(2, 0.5, 0.0, 64.0, 66.0),
        ];
        let mut results = [CrammingResult::default(); 2];
        compute_cramming(&inputs, &mut results);
        assert_eq!(results[0].crowded_count, 1);
        assert_eq!(results[1].crowded_count, 1);
    }

    #[test]
    fn same_position_pile_still_increments_crowded_count() {
        let inputs = [
            make_mob(1, 0.0, 0.0, 64.0, 66.0),
            make_mob(2, 0.0, 0.0, 64.0, 66.0),
            make_mob(3, 0.0, 0.0, 64.0, 66.0),
        ];
        let mut results = [CrammingResult::default(); 3];
        compute_cramming(&inputs, &mut results);

        assert_eq!(results[0].crowded_count, 2,
                   "mob 0 should count 2 overlapping pushable non-passenger neighbors");
        assert_eq!(results[1].crowded_count, 2);
        assert_eq!(results[2].crowded_count, 2);

        for r in &results {
            assert_eq!(r.accum_dx, 0.0);
            assert_eq!(r.accum_dz, 0.0);
        }
    }

    #[test]
    fn wide_mob_pair_found_across_cell_boundary() {
        let mut a = CrammingInput {
            entity_id: 1, flags: FLAG_PUSHABLE, _pad0: [0; 3],
            x: 0.0, z: 0.0, aabb_half_width: 1.5,
            aabb_min_y: 64.0, aabb_max_y: 66.0, root_vehicle_id: 1,
        };
        let mut b = CrammingInput {
            entity_id: 2, flags: FLAG_PUSHABLE, _pad0: [0; 3],
            x: 2.5, z: 0.0, aabb_half_width: 1.5,
            aabb_min_y: 64.0, aabb_max_y: 66.0, root_vehicle_id: 2,
        };
        let _ = (&mut a, &mut b);
        let inputs = [a, b];
        let mut results = [CrammingResult::default(); 2];
        compute_cramming(&inputs, &mut results);

        assert!(results[0].accum_dx < 0.0, "wide mob A must be pushed; got {}", results[0].accum_dx);
        assert!(results[1].accum_dx > 0.0, "wide mob B must be pushed; got {}", results[1].accum_dx);
        assert_eq!(results[0].neighbor_count, 1);
        assert_eq!(results[1].neighbor_count, 1);
    }

    #[test]
    fn no_physics_skips_pair_entirely() {
        let mut a = make_mob(1, 0.0, 0.0, 64.0, 66.0);
        a.flags |= FLAG_NO_PHYSICS;
        let b = make_mob(2, 0.5, 0.0, 64.0, 66.0);
        let inputs = [a, b];
        let mut results = [CrammingResult::default(); 2];
        compute_cramming(&inputs, &mut results);

        for r in &results {
            assert_eq!(r.accum_dx, 0.0);
            assert_eq!(r.neighbor_count, 0);
        }
    }
}
