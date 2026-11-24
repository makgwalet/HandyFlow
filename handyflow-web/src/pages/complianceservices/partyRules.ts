// Allowed values and checks for the people and sites of a client. The server enforces them; this only catches mistakes early.

export const ROLES: [string, string][] = [
  ["DIRECTOR", "Director"], ["SHAREHOLDER", "Shareholder"], ["BENEFICIAL_OWNER", "Beneficial owner"], ["MEMBER", "Member"],
  ["TRUSTEE", "Trustee"], ["PARTNER", "Partner"], ["PUBLIC_OFFICER", "Public officer"],
]
export const ID_TYPES: [string, string][] = [["SA_ID", "SA ID number"], ["PASSPORT", "Passport"], ["REG_NO", "Registration number"], ["OTHER", "Other"]]
export const SITE_TYPES: [string, string][] = [
  ["HEAD_OFFICE", "Head office"], ["BRANCH", "Branch"], ["FACTORY", "Factory"], ["WAREHOUSE", "Warehouse"], ["SITE", "Site"], ["OTHER", "Other"],
]
export const OWNERSHIP_ROLES = ["SHAREHOLDER", "BENEFICIAL_OWNER", "MEMBER", "PARTNER"]

export const roleLabel = (r: string) => ROLES.find(x => x[0] === r)?.[1] ?? r
export const holdsPercentage = (role: string) => OWNERSHIP_ROLES.includes(role)

/** South African ID number: 13 digits, a real birth date, and a correct check digit. */
export function validSaId(raw: string): boolean {
  const id = raw.replace(/[\s-]/g, "")
  if (!/^\d{13}$/.test(id)) return false
  const yy = Number(id.slice(0, 2)), mm = Number(id.slice(2, 4)), dd = Number(id.slice(4, 6))
  const real = [1900, 2000].some(c => { const d = new Date(Date.UTC(c + yy, mm - 1, dd)); return d.getUTCMonth() === mm - 1 && d.getUTCDate() === dd })
  if (!real) return false
  let sum = 0
  for (let i = 0; i < 13; i++) {
    let d = Number(id[12 - i])
    if (i % 2 === 1) { d *= 2; if (d > 9) d -= 9 }
    sum += d
  }
  return sum % 10 === 0
}

export type PartyDraft = { fullName: string; role: string; idType: string; idNumber: string; ownershipPercent: string; appointedOn: string; resignedOn: string }

/** Why the person cannot be saved yet, or null. */
export function partyProblem(p: PartyDraft): string | null {
  if (!p.fullName.trim()) return "Enter a name"
  if (!p.role) return "Choose a role"
  if (p.idType === "SA_ID" && p.idNumber.trim() && !validSaId(p.idNumber)) return "That South African ID number is not valid"
  if (p.ownershipPercent.trim()) {
    if (!holdsPercentage(p.role)) return "Only owners and members hold a percentage"
    const n = Number(p.ownershipPercent)
    if (!Number.isFinite(n) || n < 0 || n > 100) return "A percentage is between 0 and 100"
  }
  if (p.appointedOn && p.resignedOn && p.resignedOn < p.appointedOn) return "Resignation cannot be before appointment"
  return null
}

export type Ownership = { shareholdingDeclared: number; fullyDeclared: boolean; hasDirector: boolean; hasBeneficialOwner: boolean; beneficialOwnerGap: boolean; directors: number }

/** The gaps worth pointing out on a client's ownership, in plain words. */
export function ownershipNotes(o: Ownership): string[] {
  const notes: string[] = []
  if (!o.hasDirector) notes.push("No director on file")
  if (o.shareholdingDeclared > 0 && !o.fullyDeclared) notes.push(`Shareholdings declared add up to ${o.shareholdingDeclared}%, not 100%`)
  if (o.beneficialOwnerGap) notes.push("A shareholder holds 5% or more, but no beneficial owner is on file")
  return notes
}
