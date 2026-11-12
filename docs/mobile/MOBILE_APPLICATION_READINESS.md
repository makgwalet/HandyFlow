# Mobile application readiness

Scale 0 to 5 (0 none, 1 scaffold, 2 basic structure, 3 core architecture and partial functionality, 4 mature with gaps, 5 production-ready). Scores are from reading files; nothing was built or run. **Technical readiness** is scored here; **business functionality readiness** is stated separately.

| Area | Fuel scaffold (`handyflow-fuel`) | Fuel prototype (`handyfarm-sync-alerts`) | Agriculture (`handyfarm-offline-increment`) |
|---|---|---|---|
| Project structure | 1 | 3 | 3 |
| Expo configuration | 1 | 2 | 2 |
| Dependency health | 2 | 2 | 3 |
| Navigation | 0 | 3 | 2 |
| UI/UX | 0 | 3 | 2 |
| Authentication | 0 | 1 (mock) | 1 (token read only) |
| Tenant integration | 0 | 0 | 1 |
| Permissions | 0 | 1 (role tabs, client only) | 0 |
| API integration | 0 | 0 | 3 |
| Offline | 0 | 2 (in-memory) | 2 |
| Testing | 0 | 0 | 0 |
| Security | 0 | 1 | 1 |
| Build pipeline | 0 | 0 | 0 |
| Documentation | 1 | 3 | 4 |
| **Total (of 70)** | **5** | **21** | **24** |

## Why
- **Fuel scaffold (5):** template app with dependencies declared but unused; `AGENTS.md` points at the wrong Expo version.
- **Fuel prototype (21):** strong screen coverage and sensible role-driven navigation and queue envelope, but every integration is mocked. Technically a clickable prototype.
- **Agriculture (24):** the only app with a real typed API client and a persistent queue, and the best documentation; it has no login, no permissions, no idempotency, and no tests.

## Business functionality readiness (separate)
| App | Functional status |
|---|---|
| Fuel scaffold | None |
| Fuel prototype | About 60 screens of UI; zero working business transactions |
| Agriculture | Livestock and crops recording against the real API; poultry, aquaculture, farm map unavailable (backend not built) |

## Out of scope
`clinic-desktop` is a Tauri/Vite desktop app and is not scored on the Expo scale. It tracks `node_modules` in git and needs its own review `[Deferred]`.

## Production-ready means
Section 4 of `MOBILE_TESTING_STANDARDS.md`. None of the three projects meets any item today except documentation for Agriculture.
