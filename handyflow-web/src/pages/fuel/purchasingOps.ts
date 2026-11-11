// Stage 4C helpers.
export interface PurchaseOrder {
  id: string; poNumber: string; supplierId: string; supplierName: string; fuelType: string
  litresOrdered: number; litresReceived: number; litresOutstanding: number; pricePerLitre: number; orderValue: number
  status: "DRAFT" | "PENDING_APPROVAL" | "ORDERED" | "PART_RECEIVED" | "RECEIVED" | "CLOSED_SHORT" | "CANCELLED"; expectedDate: string | null; overdue: boolean; cancelReason: string | null; closeReason?: string | null; notes: string | null; approvalNote?: string | null
}
export interface MatchLine { receiptId: string; receivedAt: string; litres: number; pricePerLitre: number; priceVariance: number; totalCost: number; deliveryNote: string | null; invoiceRef: string | null; reversed: boolean; priceDiffers: boolean }
export interface Match { order: PurchaseOrder; receivedValue: number; valueVariance: number; litresVariance: number; receipts: MatchLine[] }

export const STATUS_LABEL: Record<string, string> = { DRAFT: "Draft", PENDING_APPROVAL: "Waiting for approval", ORDERED: "Ordered", PART_RECEIVED: "Part received", RECEIVED: "Received", CLOSED_SHORT: "Closed short", CANCELLED: "Cancelled" }

export function validatePo(supplierId: string, fuelType: string, litres: string, price: string): string | null {
  if (!supplierId) return "Choose the supplier."
  if (!fuelType) return "Choose the fuel type."
  const l = litres.trim() === "" ? NaN : Number(litres); if (Number.isNaN(l) || l <= 0) return "Litres must be greater than zero."
  const p = price.trim() === "" ? NaN : Number(price); if (Number.isNaN(p) || p < 0) return "Enter the price per litre agreed with the supplier."
  return null
}

/** "-500 L short", "+20 L over", "exact". */
export function litresVarianceLabel(v: number): string {
  if (Math.abs(v) < 0.005) return "exact"
  const n = Math.abs(v).toLocaleString("en-ZA", { maximumFractionDigits: 2 })
  return v < 0 ? `${n} L short` : `${n} L over`
}

/** Why a reason is needed: closing short is a statement to the books that the rest will never arrive. */
export function validateCloseReason(reason: string): string | null {
  return reason.trim().length < 5 ? "Say why the rest will not be delivered (at least 5 characters)." : null
}
