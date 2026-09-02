// src/pages/collectionsagency/constants.ts
//
// Pulled out of CollectionsAgencyPage.tsx to avoid a circular import:
// nearly every tab/sub-tab in this module needs the accent color, and
// CollectionsAgencyPage.tsx itself imports all of those tabs — so
// importing the constant back from there would make every file in this
// module part of one big import cycle. A single-purpose constants file
// has no imports of its own, so nothing cycles through it.
export const CA_ACCENT = "#5B21B6"
