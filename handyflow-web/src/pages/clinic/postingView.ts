// Claim money that has not reached the general ledger yet (patch 0185): wording and the request to post it.

export interface Posting {
  id: string; claimId: string; patientName?: string; type: string; amount: number
  reference?: string; occurredOn: string; status: "PENDING" | "FAILED"; reason?: string; needsBank: boolean
}
export interface BankChoice { id: string; label: string }

const rand = (v: number) => `R ${v.toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`

export function postingTitle(p: Posting): string {
  const what = p.type === "CLAIM_WRITTEN_OFF" ? "Write-off" : "Scheme payment"
  return `${what} ${rand(p.amount)}${p.patientName ? ` · ${p.patientName}` : ""}`
}

/** One line for the banner: how much is waiting and how much needs a bank account chosen. */
export function bannerSummary(items: Posting[]): string {
  if (items.length === 0) return ""
  const payments = items.filter(i => i.needsBank)
  const failed = items.filter(i => i.status === "FAILED")
  const parts = [`${items.length} ${items.length === 1 ? "item is" : "items are"} not in the accounts yet`]
  if (payments.length) parts.push(`${payments.length} scheme ${payments.length === 1 ? "payment needs" : "payments need"} a bank account`)
  if (failed.length) parts.push(`${failed.length} could not be posted`)
  return parts.join(" · ")
}

export const needsBankChoice = (items: Posting[]) => items.some(i => i.needsBank)

/** The post request, or an error message when a bank account is still to be chosen. */
export function postBody(items: Posting[], bankId: string): { body?: { bankAccountId?: string; postingIds: string[] }; error?: string } {
  if (needsBankChoice(items) && !bankId) return { error: "Choose the bank account the money was paid into." }
  return { body: { ...(bankId && needsBankChoice(items) ? { bankAccountId: bankId } : {}), postingIds: items.map(i => i.id) } }
}
