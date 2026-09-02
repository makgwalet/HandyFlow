// src/pages/trainingprovider/constants.ts
//
// Standalone — no imports of its own — same fix as every other
// provider module in this engagement (WHSE_ACCENT/CA_ACCENT/
// TRAINING_ACCENT all learned this the hard way: importing the accent
// back out of the page shell creates a circular import once every tab
// needs it too).
export const TRAINPROV_ACCENT = "#B45309" // amber-700 — distinct from warehousing (teal #0F766E), collectionsagency (violet #5B21B6), training/4a (green #15803D)
