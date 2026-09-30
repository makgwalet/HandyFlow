// src/pages/training/constants.ts
//
// Standalone — no imports — same circular-import-avoidance convention
// established for WHSE_ACCENT (warehousing/constants.ts) and CA_ACCENT.
// Module accent: green (the former green-700 is merged into the green-800 fill).
// Two tokens because fills and text diverge in dark mode: fills stay dark enough for
// white labels, text gets lighter to stay readable on dark surfaces.
export const TRAINING_ACCENT = "var(--hf-success-solid-strong)"       // fills, borders
export const TRAINING_ACCENT_TEXT = "var(--hf-success-text-strong)"  // text and icons
