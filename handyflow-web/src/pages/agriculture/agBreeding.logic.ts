// src/pages/agriculture/agBreeding.logic.ts
// Which buttons a breeding record offers, and the birth form's checks. Mirrors AgBreedingRules on the server, which is the
// real guard; this only stops the screen offering buttons that would be refused.
//
//   PENDING -> CONFIRMED_PREGNANT | NOT_PREGNANT | BIRTHED | STILLBORN | ABORTED | FAILED
//   CONFIRMED_PREGNANT -> BIRTHED | STILLBORN | ABORTED | FAILED
//   everything else is final

export const BREEDING_TYPES: { value: string; label: string }[] = [
  { value: "NATURAL", label: "Natural" },
  { value: "AI", label: "Artificial insemination" },
  { value: "EMBRYO_TRANSFER", label: "Embryo transfer" },
]

export function breedingTypeLabel(type: string): string {
  const known = BREEDING_TYPES.find(t => t.value === type)
  if (known) return known.label
  return type === "ARTIFICIAL_INSEMINATION" ? "Artificial insemination" : type.replace(/_/g, " ").toLowerCase()
}

export function outcomeLabel(outcome: string | null | undefined): string {
  switch (outcome ?? "PENDING") {
    case "PENDING": return "Awaiting confirmation"
    case "CONFIRMED_PREGNANT": return "Confirmed pregnant"
    case "NOT_PREGNANT": return "Not pregnant"
    case "BIRTHED": return "Born"
    case "STILLBORN": return "Stillborn"
    case "ABORTED": return "Aborted"
    case "FAILED": return "Failed"
    default: return (outcome ?? "").replace(/_/g, " ").toLowerCase()
  }
}

export interface BreedingActions { confirm: boolean; notPregnant: boolean; birth: boolean; aborted: boolean; failed: boolean }

export function breedingActions(outcome: string | null | undefined): BreedingActions {
  const o = outcome ?? "PENDING"
  const open = o === "PENDING" || o === "CONFIRMED_PREGNANT"
  return { confirm: o === "PENDING", notPregnant: o === "PENDING", birth: open, aborted: open, failed: open }
}

export interface OffspringRow { sex: string; tagNumber: string; name: string }
export const NEWBORN_SEXES = ["MALE", "FEMALE"] as const
export interface BirthForm { date: string; live: string; stillborn: string; offspring?: OffspringRow[] }

/** One row per live offspring, keeping what was already typed. Capped so a typo cannot build a huge form. */
export function offspringRows(live: string, current: OffspringRow[]): OffspringRow[] {
  const t = live.trim()
  const n = t === "" ? 0 : /^\d+$/.test(t) ? Math.min(Number(t), 100) : 0
  return Array.from({ length: n }, (_, i) => current[i] ?? { sex: "", tagNumber: "", name: "" })
}

/** The temporary tag the server will give an offspring that has no typed tag: `<dam>-<yy>-<nn>`. A preview only. */
export function previewTemporaryTag(damTag: string, date: string, n: number): string {
  const yy = date.length >= 4 ? date.slice(2, 4) : "00"
  return `${damTag}-${yy}-${String(n).padStart(2, "0")}`
}

/** Every live offspring needs a sex; typed tags must not repeat inside the form. */
export function offspringError(rows: OffspringRow[]): string | null {
  for (let i = 0; i < rows.length; i++) {
    if (!NEWBORN_SEXES.includes(rows[i].sex as typeof NEWBORN_SEXES[number])) return `Choose the sex of offspring ${i + 1}.`
  }
  const seen = new Set<string>()
  for (const r of rows) {
    const t = r.tagNumber.trim().toLowerCase()
    if (!t) continue
    if (seen.has(t)) return `The tag ${r.tagNumber.trim()} is used twice.`
    seen.add(t)
  }
  return null
}

/** The message to show, or null when the form is fine to send. Counts may be left empty (zero) but not both. */
export function birthFormError(f: BirthForm, matingDate: string, today: string): string | null {
  if (!f.date) return "Enter the birth date."
  if (f.date > today) return "The birth date cannot be in the future."
  if (f.date < matingDate) return "The birth date cannot be before the mating date."
  const live = count(f.live), still = count(f.stillborn)
  if (live === null || still === null) return "Counts must be whole numbers of 0 or more."
  if (live + still === 0) return "Enter how many were born alive or stillborn."
  if (live > 100 || still > 100) return "That count is not credible. Check the number."
  if (f.offspring) return offspringError(f.offspring)
  return null
}

export function birthPayload(f: BirthForm): { actualBirthDate: string; offspringCount: number; stillbornCount: number; offspring?: { sex: string; tagNumber: string | null; name: string | null }[] } {
  const base = { actualBirthDate: f.date, offspringCount: count(f.live) ?? 0, stillbornCount: count(f.stillborn) ?? 0 }
  if (!f.offspring) return base
  return { ...base, offspring: f.offspring.map(o => ({ sex: o.sex, tagNumber: o.tagNumber.trim() || null, name: o.name.trim() || null })) }
}

function count(v: string): number | null {
  const t = v.trim()
  if (t === "") return 0
  if (!/^\d+$/.test(t)) return null
  return Number(t)
}
