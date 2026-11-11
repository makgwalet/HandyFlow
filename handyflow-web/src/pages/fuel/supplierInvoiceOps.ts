// Supplier invoice helpers: validation that mirrors the server, and the totals shown while capturing.
export interface InvReceipt { receiptId: string; receivedAt: string; litres: number; pricePerLitre: number; totalCost: number; deliveryNote: string | null; reversed: boolean }
export interface SupplierInvoice {
  id: string; supplierId: string; supplierName: string; purchaseOrderId: string | null; poNumber: string | null; invoiceNumber: string
  invoiceDate: string; dueDate: string | null; litres: number; pricePerLitre: number; otherCharges: number; vatAmount: number; totalAmount: number
  status: "CAPTURED" | "APPROVED" | "REJECTED"; matchStatus: "MATCHED" | "VARIANCE" | "UNMATCHED"; decisionReason: string | null; notes: string | null
  capturedAt: string; decidedAt: string | null; litresReceived: number; receiptValue: number; litresVariance: number; goodsValue: number; valueVariance: number
  priceVsPo: number | null; issues: string[]; receipts: InvReceipt[]
}

const num = (s: string) => (s.trim() === "" ? NaN : Number(s))
const round2 = (n: number) => Math.round((n + Number.EPSILON) * 100) / 100

export const MATCH_LABEL: Record<string, string> = { MATCHED: "Matches", VARIANCE: "Does not match", UNMATCHED: "Nothing to match" }
export const STATUS_LABEL: Record<string, string> = { CAPTURED: "Awaiting approval", APPROVED: "Approved", REJECTED: "Rejected" }

export interface CaptureForm { supplierId: string; invoiceNumber: string; invoiceDate: string; dueDate: string; litres: string; price: string; other: string; vat: string }

export function invoiceTotal(f: Pick<CaptureForm, "litres" | "price" | "other" | "vat">): number | null {
  const l = num(f.litres), p = num(f.price)
  if (Number.isNaN(l) || Number.isNaN(p)) return null
  const o = f.other.trim() === "" ? 0 : num(f.other), v = f.vat.trim() === "" ? 0 : num(f.vat)
  if (Number.isNaN(o) || Number.isNaN(v)) return null
  return round2(round2(l * p) + o + v)
}

export function validateCapture(f: CaptureForm): string | null {
  if (!f.supplierId) return "Choose the supplier."
  if (!f.invoiceNumber.trim()) return "Enter the supplier's invoice number."
  if (!f.invoiceDate) return "Enter the invoice date."
  if (f.dueDate && f.dueDate < f.invoiceDate) return "The due date is before the invoice date."
  const l = num(f.litres); if (Number.isNaN(l) || l <= 0) return "Litres must be more than zero."
  const p = num(f.price); if (Number.isNaN(p) || p < 0) return "Enter the price per litre."
  for (const [v, what] of [[f.other, "Other charges"], [f.vat, "VAT"]] as const) {
    if (v.trim() !== "" && (Number.isNaN(Number(v)) || Number(v) < 0)) return `${what} must be zero or more.`
  }
  return null
}

/** Mirrors the server: anything but a clean match needs a reason of 5+ characters to approve. */
export const needsReason = (m: SupplierInvoice["matchStatus"]) => m !== "MATCHED"

export function validateDecision(reason: string, required: boolean): string | null {
  if (required && reason.trim().length < 5) return "Give a reason of at least 5 characters."
  return null
}
