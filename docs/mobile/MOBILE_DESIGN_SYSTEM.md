# Mobile design system

## 1. Existing `[Verified]`
- Fuel prototype: `src/theme/tokens.ts` and shared components `Card`, `StatusPill`, `SelectableRow`, `QuickActionButton`, `SignaturePad`, `TankRow`, `WizardHeader`.
- Agriculture: `src/design-system/tokens.ts`, `ThemeProvider.tsx`, `FormPrimitives`, `SyncStatusBar`.
- Two token files that do not share values or names. The web app has its own theme-token work (`feat/theme-token-phase0` branch, `UI-MODERNIZATION-PROGRESS.md`); the mobile tokens should derive from the same palette `[Proposed]`.

## 2. Proposal
| Layer | Content | Owner |
|---|---|---|
| `packages/design-tokens` | Colour roles (not hex literals in screens), type scale, spacing 4-pt grid, radii, elevation, motion; light and dark | Platform |
| `packages/ui` | Button, Input, Select, Card, ListRow, StatusPill, Banner, EmptyState, ErrorState, Skeleton, SyncBar, Sheet, SignaturePad, CameraCapture, Stepper | Platform |
| App theme | Accent colour and iconography per module | App |
| Tenant branding | Logo and accent from tenant settings where available `[Deferred]` | App |

## 3. Rules
- Touch targets at least 44 pt (field apps 56 pt: gloves, sunlight).
- Contrast meets WCAG AA; do not signal status by colour alone (the pills carry text).
- Support system font scaling; test at 200%.
- Dark mode: tokens support it; the apps currently force `userInterfaceStyle: "light"`. Enable per app when tokens are done `[Deferred]`.
- States for every list and form: loading (skeleton), empty (explain and offer action), error (message and retry), offline (bar), permission-denied.
- Wizards for multi-step field flows (dispense, dip, delivery) with a visible step header and a draft saved on exit.
- Large numeric entry for litres and readings; unit always shown; confirm screen before queueing.

## 4. Role-specific experiences
| Persona | Design emphasis |
|---|---|
| Driver | Very large buttons, one task per screen, glanceable status, works one-handed |
| Forecourt operator | Fast repeated entry, vehicle search, PIN, minimal text |
| Manager | Dense lists, approvals inbox, alerts first |
| Clinic practitioner | Calm, readable, privacy-first, lock on idle |
| Warehouse | Scan-first, hardware scanner support, high contrast |
| Customer/supplier | Simple, branded, read-mostly (Phase 4) |

A driver app must not look like an accountant's app; the shared system supplies parts, not layouts.

## 5. Charts and dashboards
Few and simple on mobile; reuse tokens; no dashboards in field apps beyond "Today".
