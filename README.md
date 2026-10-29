# mms-ferrite

A Minecraft 1.21.11 Fabric backport of [Ferrite](https://github.com/VoiceLessQ/Ferrite) by VoiceLessQ (MIT), release 0.7.5-alpha+26.3. It carries only the features that keep vanilla behavior. Server-side only: clients do not need it.

## Features

- Rust cramming, off by default because it is not bit-exact with vanilla. One native call per level per tick replaces the mob-vs-mob `pushEntities` loop. `/ferrite cramming on|off|status`.
- Entity query index, on by default. A per-section bitset grid answers `EntitySection.getEntities` in vanilla order. An oracle replays 1 in 16 queries against the full vanilla walk. Off: `-Dferrite.entityquery.cache=false`. Oracle rate: `-Dferrite.entityquery.oracle=<n>`, 0 disables.
- Collider skip, on by default. `getEntityCollisions` returns the empty list when the level holds no boat, shulker or happy ghast and the source is not a boat or minecart. An oracle walks 1 in 16 eligible queries and logs any non-empty result. Off: `-Dferrite.entityquery.colliderskip=false`. Oracle rate: `-Dferrite.entityquery.colliderskip.oracle=<n>`.
- Sign and furnace ticker gates, on by default. Exact vanilla signs with no editor and exact vanilla furnaces with empty input and fuel lose their ticker until used.
- Hopper extract hint, on by default. Extraction starts at the first known non-empty slot. `/ferrite hopper hint on|off|status`, `/ferrite hopper hint validate on|off` counts stale hints.

`/ferrite entityquery status` prints both switches and the oracle mismatch totals. `/ferrite log monitors on|off|status` controls the 5 second report lines. Toggles persist in `config/mms_ferrite.properties` as deltas from defaults.

## Build

```bash
./gradlew build
```

The Rust library in `native/` builds on stable Rust through the `cargoBuild*` Gradle tasks for `aarch64-apple-darwin` and `x86_64-unknown-linux-gnu`. A target without an installed Rust std is skipped and the jar ships without that library, with cramming falling back to vanilla.

## License

MIT. See `LICENSE`.
