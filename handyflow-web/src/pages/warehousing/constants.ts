// src/pages/warehousing/constants.ts
//
// Standalone — no imports of its own — so every sub-tab can import the
// accent color without creating a circular import back through
// WarehousingPage.tsx (same fix already applied for collectionsagency's
// CA_ACCENT after that exact cycle was caught pre-delivery there).
export const WHSE_ACCENT = "#0F766E" // teal-700 — distinct from legalcompliance (indigo), debtcollection (rust), collectionsagency (violet)
