// What the SMS reminder screens say. The server holds the rules; these are only wording and an early look at what was typed.

export type SmsSettings = { enabled: boolean; updatedAt: string | null }
export type Consent = {
  id: string; clientId: string; contactName: string; phone: string; basis: string; givenBy: string; givenAt: string
  active: boolean; withdrawnBy: string | null; withdrawnAt: string | null; withdrawnNote: string | null
}
export type SmsLog = { id: string; clientId: string; clientName: string; deadlineId: string; kind: string; runDate: string; outcome: string; outcomeText: string; detail: string | null; createdAt: string }
export type SmsRun = { handedOver: number; failed: number; alreadySent: number; noConsent: number }

export const MIN_NOTE = 10
const E164 = /^\+[1-9][0-9]{7,14}$/

export const tidyPhone = (p: string) => p.replace(/[\s\-()]/g, "")

export function consentProblem(f: { contactName: string; phone: string; basis: string }): string | null {
  if (!f.contactName.trim()) return "Say who the number belongs to"
  if (!E164.test(tidyPhone(f.phone))) return "Give the number in international form, for example +27821234567"
  if (f.basis.trim().length < MIN_NOTE) return `Say how consent was given (at least ${MIN_NOTE} characters)`
  return null
}

export const withdrawProblem = (note: string): string | null => (note.trim().length < MIN_NOTE ? `Give a reason (at least ${MIN_NOTE} characters)` : null)

export const KIND_LABEL: Record<string, string> = { WEEK_AHEAD: "A week ahead", DAY_BEFORE: "Day before" }
export const kindLabel = (k: string) => KIND_LABEL[k] ?? k

export const outcomeLabel = (o: string) => (o === "HANDED_OVER" ? "Handed to the SMS provider" : o === "FAILED" ? "Could not be handed over" : o)

export const runText = (r: SmsRun) =>
  `${r.handedOver} handed to the SMS provider, ${r.failed} failed, ${r.alreadySent} already sent earlier, ${r.noConsent} not sent for lack of consent. Delivery is not confirmed.`
