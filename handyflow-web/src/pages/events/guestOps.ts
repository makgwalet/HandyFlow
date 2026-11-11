// src/pages/events/guestOps.ts
//
// The checks and wording behind the guest-operations screens (walk-in, group, transfer, waitlist, CSV import, door scan). Kept free of React so the rules can
// be tested directly. They mirror the limits on the request DTOs; the server stays the authority.

export const MAX_GROUP = 50
export const MAX_IMPORT_BYTES = 2 * 1024 * 1024

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
export const emailOk = (v: string) => v.trim() === "" || EMAIL.test(v.trim())

/** A money field typed as text: empty is allowed (means "not stated"); otherwise zero or more with at most two decimals and eight digits. */
export function moneyProblem(v: string): string | null {
  const t = v.trim()
  if (t === "") return null
  if (!/^\d{1,8}(\.\d{1,2})?$/.test(t)) return "Enter an amount like 150 or 150.00."
  return null
}

export interface Person { fullName: string; email: string; phone: string; company: string; dietaryRequirements: string }
export const emptyPerson = (): Person => ({ fullName: "", email: "", phone: "", company: "", dietaryRequirements: "" })

function personProblem(p: Person, emailRequired: boolean): string | null {
  if (!p.fullName.trim()) return "Every person needs a name."
  if (emailRequired && !p.email.trim()) return "An email address is needed so the ticket can be sent."
  if (!emailOk(p.email)) return `"${p.email.trim()}" is not a valid email address.`
  return null
}

export const walkInProblem = (p: Person, amount: string) => personProblem(p, false) ?? moneyProblem(amount)
export const waitlistProblem = (p: Person) => personProblem(p, true)

export function transferProblem(p: Person, reason: string): string | null {
  return personProblem(p, true) ?? (reason.length > 500 ? "The reason can be at most 500 characters." : null)
}

/** Group rows: blank rows (nothing typed at all) are ignored; the rest are checked and named by position so the person can find them. */
export function groupRows(rows: Person[]): Person[] {
  return rows.filter(r => Object.values(r).some(v => v.trim() !== ""))
}

export function groupProblem(rows: Person[], amountEach: string): string | null {
  const used = groupRows(rows)
  if (used.length === 0) return "Add at least one person."
  if (used.length > MAX_GROUP) return `A group can have at most ${MAX_GROUP} people.`
  for (let i = 0; i < used.length; i++) {
    const p = personProblem(used[i], false)
    if (p) return `Person ${i + 1}: ${p}`
  }
  return moneyProblem(amountEach)
}

const nz = (v: string) => (v.trim() === "" ? null : v.trim())
export const personBody = (p: Person) => ({ fullName: p.fullName.trim(), email: nz(p.email), phone: nz(p.phone), company: nz(p.company), dietaryRequirements: nz(p.dietaryRequirements) })
export const amountBody = (v: string) => (v.trim() === "" ? null : Number(v))

export function importFileProblem(file: { name: string; size: number } | null): string | null {
  if (!file) return "Choose a CSV file."
  if (!/\.csv$/i.test(file.name)) return "The file must be a .csv file."
  if (file.size === 0) return "The file is empty."
  if (file.size > MAX_IMPORT_BYTES) return "The file is too large; the maximum is 2 MB."
  return null
}

export interface ImportReport { dryRun: boolean; totalRows: number; imported: number; duplicates: number; invalid: number; rejected: number }

/** One sentence for the result of an import or its preview. */
export function importSummary(r: ImportReport): string {
  const parts = [`${r.imported} ${r.dryRun ? "would be imported" : "imported"}`]
  if (r.duplicates) parts.push(`${r.duplicates} already registered`)
  if (r.invalid) parts.push(`${r.invalid} invalid`)
  if (r.rejected) parts.push(`${r.rejected} rejected`)
  return `${r.totalRows} rows: ${parts.join(", ")}.`
}

/** A real import is only offered after a preview that found something to import, and only with the lawful-basis box ticked. */
export const canRunImport = (preview: ImportReport | null, consent: boolean) => !!preview && preview.dryRun && preview.imported > 0 && consent

export const IMPORT_TEMPLATE = "full_name,email,phone,company,dietary_requirements,tier\n"

export interface CheckInView { tone: "ok" | "warn" | "bad"; title: string; hint: string }

/** What the door sees for each result the server can return. Unknown results are shown as a problem, never as a welcome. */
export function checkInView(result: string): CheckInView {
  switch (result) {
    case "SUCCESS": return { tone: "ok", title: "Welcome!", hint: "" }
    case "WALK_IN": return { tone: "ok", title: "Walk-in checked in", hint: "" }
    case "ALREADY_CHECKED_IN": return { tone: "warn", title: "Already checked in", hint: "This ticket has been used. Check who is presenting it." }
    case "PAYMENT_PENDING": return { tone: "warn", title: "Payment not confirmed", hint: "The seat is held but not paid for. Send them to the payment desk." }
    case "EXPIRED_TICKET": return { tone: "bad", title: "Reservation expired", hint: "The hold ran out before payment. They can register again if there is room." }
    case "TRANSFERRED_TICKET": return { tone: "bad", title: "Ticket was transferred", hint: "This ticket now belongs to someone else. Ask for the new ticket." }
    case "CANCELLED_TICKET": return { tone: "bad", title: "Cancelled ticket", hint: "" }
    case "NOT_FOUND": return { tone: "bad", title: "Not found", hint: "No ticket matches this code for this event." }
    default: return { tone: "bad", title: "Could not check in", hint: "Try again, or look the guest up in the list." }
  }
}

export const waitlistStatusText = (s: string) => ({ WAITING: "Waiting", PROMOTED: "Promoted", REMOVED: "Removed" } as Record<string, string>)[s] ?? s
