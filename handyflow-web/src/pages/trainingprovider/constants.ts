// src/pages/trainingprovider/constants.ts
//
// Standalone — no imports of its own — same fix as every other
// provider module in this engagement (WHSE_ACCENT/CA_ACCENT/
// TRAINING_ACCENT all learned this the hard way: importing the accent
// back out of the page shell creates a circular import once every tab
// needs it too).
// Module accent (amber-700 in light mode). Two tokens because fills and text
// diverge in dark mode: fills stay dark enough for white labels, text gets
// lighter to stay readable on dark surfaces.
export const TRAINPROV_ACCENT = "var(--hf-warning-solid-strong)"      // backgrounds, borders
export const TRAINPROV_ACCENT_TEXT = "var(--hf-warning-text-strong)"  // text and icons
