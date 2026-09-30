// src/pages/collectionsagency/constants.ts
//
// Pulled out of CollectionsAgencyPage.tsx to avoid a circular import:
// nearly every tab/sub-tab in this module needs the accent color, and
// CollectionsAgencyPage.tsx itself imports all of those tabs — so
// importing the constant back from there would make every file in this
// module part of one big import cycle. A single-purpose constants file
// has no imports of its own, so nothing cycles through it.
// Module accent: deep violet (violet-800).
// Two tokens because fills and text diverge in dark mode: fills stay dark enough for
// white labels, text gets lighter to stay readable on dark surfaces.
export const CA_ACCENT = "var(--hf-violet-solid-strong)"       // fills, borders
export const CA_ACCENT_TEXT = "var(--hf-violet-text-strong)"  // text and icons
