// src/pages/warehousing/constants.ts
//
// Standalone — no imports of its own — so every sub-tab can import the
// accent color without creating a circular import back through
// WarehousingPage.tsx (same fix already applied for collectionsagency's
// CA_ACCENT after that exact cycle was caught pre-delivery there).
// Module accent: teal (teal-700).
// Two tokens because fills and text diverge in dark mode: fills stay dark enough for
// white labels, text gets lighter to stay readable on dark surfaces.
export const WHSE_ACCENT = "var(--hf-accent-solid-strong)"       // fills, borders
export const WHSE_ACCENT_TEXT = "var(--hf-accent-text-strong)"  // text and icons
