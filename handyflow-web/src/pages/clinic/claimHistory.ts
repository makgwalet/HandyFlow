// The history of a claim's money (CLINIC-DEC-001 to 003): every payment, write-off, credit note and payment reversal, with
// what the scheme owed after each. Mirrors the server's ledger rules; the server remains the check.

export interface LedgerEntry {
  kind: string            // SCHEME_PAYMENT | PAYMENT_REVERSAL | WRITE_OFF | CREDIT_NOTE
  amount: number
  date: string
  reference?: string | null
  note?: string | null
  byUserId?: string | null
  recordedAt: string
  id?: string | null      // the payment's id on SCHEME_PAYMENT and PAYMENT_REVERSAL (the payment reversed)
  reversed?: boolean      // on a SCHEME_PAYMENT: it has since been reversed
}

export interface ClaimLedger {
  claimId: string
  status: string
  schemeDue: number
  paid: number
  writtenOff: number
  credited: number
  outstanding: number
  entries: LedgerEntry[]
}

const LABEL: Record<string, string> = {
  SCHEME_PAYMENT: "Scheme payment",
  PAYMENT_REVERSAL: "Payment reversed",
  WRITE_OFF: "Write-off",
  CREDIT_NOTE: "Credit note",
}

export function entryLabel(e: LedgerEntry): string {
  const base = LABEL[e.kind] ?? e.kind
  return e.kind === "SCHEME_PAYMENT" && e.reversed ? `${base} (reversed)` : base
}

/** What the entry does to the balance the scheme owes: a reversal puts back what the payment took off. */
export function effectOnOwed(e: LedgerEntry): number {
  return e.kind === "PAYMENT_REVERSAL" ? e.amount : -e.amount
}

export interface HistoryRow extends LedgerEntry { owedAfter: number }

/** Oldest first, each with what the scheme owed after it; the list is shown newest first. Never below zero, as on the server. */
export function withBalances(schemeDue: number, entries: LedgerEntry[]): HistoryRow[] {
  const ordered = [...entries].sort((a, b) => a.recordedAt.localeCompare(b.recordedAt))
  let owed = schemeDue
  return ordered.map(e => {
    owed = Math.max(0, Math.round((owed + effectOnOwed(e)) * 100) / 100)
    return { ...e, owedAfter: owed }
  }).reverse()
}

const RECEIVED_MONEY = ["ACCEPTED", "PARTIAL", "PAID", "CLOSED"]

/** Offer Reverse on a payment only when the server would accept it: not already reversed, claim has received money, and you may. */
export function canReversePayment(e: LedgerEntry, claimStatus: string, allowed: boolean): boolean {
  return allowed && e.kind === "SCHEME_PAYMENT" && !e.reversed && !!e.id && RECEIVED_MONEY.includes(claimStatus)
}
