// src/pages/agriculture/agLookups.ts
// Pick-lists so people choose instead of type: soil types and stock units are dropdowns; breeds and feed types are suggestions
// (the box still accepts anything else, because a farm may keep a breed or feed we do not list). A value already saved that is
// not in a list is kept in the dropdown, so editing an old record never loses it.

export const SOIL_TYPES = [
  "Sand", "Loamy sand", "Sandy loam", "Loam", "Silt loam", "Sandy clay loam", "Clay loam", "Clay",
  "Red apedal (Hutton)", "Black turf (vertic clay)", "Shallow rocky (Glenrosa)", "Peat", "Other",
]

/** Units a stock item can be counted in. Mass units (kg, g, t) convert into each other; the rest match exactly. */
export const STOCK_UNITS = ["kg", "g", "t", "L", "mL", "bags", "bales", "crates", "units", "doses", "head"]

/** The options for a dropdown, plus the current saved value when it is not one of them. */
export function withCurrent(options: string[], current: string | null | undefined): string[] {
  const c = (current ?? "").trim()
  if (!c || options.some(o => o.toLowerCase() === c.toLowerCase())) return options
  return [...options, c]
}

const BREEDS: Record<string, string[]> = {
  cattle: ["Nguni", "Bonsmara", "Brahman", "Afrikaner", "Beefmaster", "Angus", "Hereford", "Simmentaler", "Drakensberger", "Santa Gertrudis", "Holstein", "Jersey", "Ayrshire", "Brangus", "Charolais", "Limousin"],
  sheep: ["Dorper", "Merino", "Dormer", "Damara", "Meatmaster", "Namaqua Afrikaner", "Karakul", "SA Mutton Merino", "White Dorper"],
  goats: ["Boer goat", "Kalahari Red", "Savanna", "Angora", "Saanen", "Toggenburg", "Nubian", "Indigenous veld goat"],
  pigs: ["Large White", "Landrace", "Duroc", "Hampshire", "Kolbroek", "Windsnyer", "Pietrain"],
  horses: ["Boerperd", "Thoroughbred", "Arab", "Nooitgedachter", "SA Warmblood", "Basuto pony"],
  broilers: ["Ross 308", "Cobb 500", "Hubbard"],
  layers: ["Lohmann Brown", "Hy-Line Brown", "ISA Brown", "Hy-Line W-36", "Potchefstroom Koekoek", "Boschveld"],
  ducks: ["Pekin", "Muscovy", "Khaki Campbell", "Indian Runner"],
  turkeys: ["Bronze", "White Holland", "Beltsville"],
  ostriches: ["South African Black", "Blue Neck", "Zimbabwean Blue"],
  tilapia: ["Nile tilapia", "Mozambique tilapia", "Red tilapia"],
  trout: ["Rainbow trout"],
}

const FEEDS: Record<string, string[]> = {
  cattle: ["Lucerne hay", "Eragrostis hay", "Silage", "Maize meal", "Winter lick", "Summer lick", "Production lick", "Feedlot finisher", "Dairy concentrate", "Calf starter"],
  sheep: ["Lucerne hay", "Eragrostis hay", "Winter lick", "Summer lick", "Lamb creep feed", "Ewe production pellets", "Finisher pellets"],
  goats: ["Lucerne hay", "Eragrostis hay", "Winter lick", "Summer lick", "Kid creep feed", "Goat production pellets"],
  pigs: ["Pig creep feed", "Pig weaner", "Pig grower", "Pig finisher", "Sow lactation", "Sow gestation", "Boar feed"],
  horses: ["Lucerne hay", "Teff hay", "Horse cubes", "Oats", "Horse mix"],
  broilers: ["Broiler pre-starter", "Broiler starter", "Broiler grower", "Broiler finisher"],
  layers: ["Chick starter", "Pullet grower", "Layer mash", "Layer pellets", "Layer crumble"],
  ducks: ["Duck starter", "Duck grower", "Duck finisher"],
  turkeys: ["Turkey starter", "Turkey grower", "Turkey finisher"],
  ostriches: ["Ostrich chick starter", "Ostrich grower", "Ostrich maintenance", "Lucerne hay"],
  tilapia: ["Tilapia starter", "Tilapia grower", "Tilapia finisher"],
  trout: ["Trout starter", "Trout grower", "Trout finisher"],
}

const GENERAL_FEEDS = ["Lucerne hay", "Eragrostis hay", "Silage", "Maize meal", "Winter lick", "Summer lick", "Hominy chop", "Wheat bran", "Molasses meal"]

/** The starter list plus values this farm already uses (not in the list, ignoring case), blanks dropped, own values sorted after it. */
export function withOwn(starter: string[], own: Array<string | null | undefined>): string[] {
  const seen = new Set(starter.map(s => s.toLowerCase()))
  const extra: string[] = []
  for (const o of own) {
    const v = (o ?? "").trim()
    if (v && !seen.has(v.toLowerCase())) { seen.add(v.toLowerCase()); extra.push(v) }
  }
  return [...starter, ...extra.sort((a, b) => a.localeCompare(b))]
}

/** The farm's own breeds for a species (from the server), or nothing. */
export function ownBreeds(s: { breedsBySpecies: Record<string, string[]> } | undefined, speciesId: string | null | undefined): string[] {
  return (speciesId && s?.breedsBySpecies?.[speciesId]) || []
}

const key = (speciesName: string | null | undefined) => (speciesName ?? "").trim().toLowerCase()

/** Breeds to suggest for a species (matched by name). Empty when the species is unknown. */
export function breedSuggestions(speciesName: string | null | undefined): string[] {
  return BREEDS[key(speciesName)] ?? []
}

/** Feed types to suggest for a species, or a general list when the species is unknown. */
export function feedSuggestions(speciesName: string | null | undefined): string[] {
  return FEEDS[key(speciesName)] ?? GENERAL_FEEDS
}
