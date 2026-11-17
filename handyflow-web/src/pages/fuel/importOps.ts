// Phase 8e (D03): shifts sent by a pump or POS system, held for a supervisor to accept or reject.
export type ImportReading = { pumpName: string; openingMeter: number; closingMeter: number; testLitres: number | null }
export type ImportShift = { source: string; externalId: string; attendantName: string; openedAt: string | null; closedAt: string | null; cash: number; card: number; account: number; other: number; fleetCard: number | null; notes: string | null; readings: ImportReading[] }
export type ImportView = { id: string; source: string; externalId: string; status: "PENDING" | "ACCEPTED" | "REJECTED"; receivedAt: string; problems: string[]; shift: ImportShift; decidedBy: string | null; decidedAt: string | null; decisionNote: string | null; shiftId: string | null }
export type KeyView = { id: string; name: string; keyPrefix: string; createdAt: string; lastUsedAt: string | null; revokedAt: string | null }
export type CreatedKey = { key: KeyView; apiKey: string }

export const STATUS_FILTERS: [string, string][] = [["PENDING", "Waiting for review"], ["ACCEPTED", "Accepted"], ["REJECTED", "Rejected"]]

export function litres(r: ImportReading): number { return Math.max(0, r.closingMeter - r.openingMeter - (r.testLitres ?? 0)) }
export function totalLitres(s: ImportShift): number { return s.readings.reduce((a, r) => a + litres(r), 0) }
export function totalTendered(s: ImportShift): number { return s.cash + s.card + s.account + s.other + (s.fleetCard ?? 0) }
export function statusLabel(s: string): string { return STATUS_FILTERS.find(f => f[0] === s)?.[1] ?? s }

// What a sender calls a pump, mapped to the Fuel pump. Per source; without a label the pump is matched by its name.
export type AliasView = { id: string; source: string; alias: string; pumpId: string; pumpName: string; createdAt: string }

/** Null when the label can be saved. */
export function aliasProblem(source: string, alias: string, pumpId: string, existing: AliasView[]): string | null {
  if (!source.trim()) return "Enter the source, as the sender names itself."
  if (!alias.trim()) return "Enter the pump label the sender uses."
  if (!pumpId) return "Choose the Fuel pump."
  const s = source.trim().toLowerCase(), a = alias.trim().toLowerCase()
  return existing.some(x => x.source === s && x.alias === a) ? "That label is already mapped for this source." : null
}

/** Sources seen on staged shifts, so the person can pick one instead of typing it. */
export function knownSources(imports: { source: string }[]): string[] {
  return Array.from(new Set(imports.map(i => i.source))).sort()
}

// Live transactions (sales, refunds, voids) a sender sent for a staged shift. They take effect only when the shift is accepted.
export type TxView = { id: string; externalId: string; kind: "SALE" | "REFUND" | "VOID"; originalExternalId: string | null; pumpLabel: string | null; litres: number | null; amount: number
  tender: string | null; occurredAt: string | null; reason: string | null; status: "RECEIVED" | "APPLIED" | "NOT_APPLIED"; problems: string[]; adjustmentId: string | null }
export type TxSummary = { sales: number; salesLitres: number; salesAmount: number; refundsAmount: number; voidsAmount: number; netAmount: number; netByTender: Record<string, number>
  meterLitres: number | null; litresDifference: number | null; notApplied: number }
export type ShiftTransactions = { transactions: TxView[]; summary: TxSummary }

/** The answer is an object with a list; anything else (an error body, an empty reply) is treated as no transactions. */
export function asShiftTransactions(v: unknown): ShiftTransactions | null {
  const o = v as any
  if (!o || Array.isArray(o) || !Array.isArray(o.transactions) || !o.summary) return null
  return o as ShiftTransactions
}

export function txKindLabel(k: string): string { return k === "SALE" ? "Sale" : k === "REFUND" ? "Refund" : k === "VOID" ? "Void" : k }

/** One line saying how the sender's litres compare with the meters. Empty when the shift has not been received. */
export function litresCheckText(s: TxSummary): string {
  if (s.litresDifference == null || s.meterLitres == null) return ""
  const d = Math.round(s.litresDifference * 1000) / 1000
  if (d === 0) return "Sales litres match the meters."
  return `Sales litres are ${Math.abs(d)} L ${d > 0 ? "more" : "less"} than the meters.`
}

export function problemCount(t: ShiftTransactions): number { return t.transactions.reduce((n, x) => n + x.problems.length, 0) }
