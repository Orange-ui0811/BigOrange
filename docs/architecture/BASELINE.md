# OrangeVC architecture baseline

Captured from branch `V3` on 2026-08-05 before architecture governance work.

## Current debt

- 80 Spring-managed classes use field injection.
- 11 controllers directly depend on mapper classes.
- 13 Java production files exceed 300 lines.
- 10 Vue views/layouts exceed 500 lines.
- `orange-web` has 65 ESLint warnings; `orange-admin` has 19.
- The largest current JavaScript chunks remain below the existing 1,250,000-byte hard limit.

The architecture checks use explicit baselines so existing debt can be reduced in small batches without allowing new debt. Baseline entries may be removed but must never be added to make a failing change pass.

## Stable dependency direction

`Controller -> application service -> policy/assembler/validator -> mapper or external runtime`

Frontend features use `View -> feature component -> composable -> Pinia store/API`. API modules transport data only and stores never invoke UI notification components.

## Compatibility promise

Architecture-only changes do not modify public URLs, request or response fields, database semantics, UI behavior, AI routing, quota charging, environment variables, or deployment layout.
