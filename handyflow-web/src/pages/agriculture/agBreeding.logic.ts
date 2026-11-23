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

export interface BirthForm { date: string; live: string; stillborn: string }

/** The message to show, or null when the form is fine to send. Counts may be left empty (zero) but not both. */
export function birthFormError(f: BirthForm, matingDate: string, today: string): string | null {
  if (!f.date) return "Enter the birth date."
  if (f.date > today) return "The birth date cannot be in the future."
  if (f.date < matingDate) return "The birth date cannot be before the mating date."
  const live = count(f.live), still = count(f.stillborn)
  if (live === null || still === null) return "Counts must be whole numbers of 0 or more."
  if (live + still === 0) return "Enter how many were born alive or stillborn."
  if (live > 100 || still > 100) return "That count is not credible. Check the number."
  return null
}

export function birthPayload(f: BirthForm) {
  return { actualBirthDate: f.date, offspringCount: count(f.live) ?? 0, stillbornCount: count(f.stillborn) ?? 0 }
}

function count(v: string): number | null {
  const t = v.trim()
  if (t === "") return 0
  if (!/^\d+$/.test(t)) return null
  return Number(t)
}
