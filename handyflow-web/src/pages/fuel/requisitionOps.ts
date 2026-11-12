// B05: purchase requisitions: request, approve, convert to a draft purchase order.
export interface Requisition {
  id: string; reqNumber: string; status: string; fuelType: string; litres: number; tankId: string | null; supplierId: string | null; supplierName: string | null
  estimatedPrice: number | null; estimatedValue: number | null; neededBy: string | null; reason: string; requestedBy: string | null; approvalNote: string | null
  purchaseOrderId: string | null; purchaseOrderNumber: string | null; cancelReason: string | null; createdAt: string
}
export interface ReqForm { fuelType: string; litres: string; estimatedPrice: string; neededBy: string; supplierId: string; reason: string }
export const emptyReq = (): ReqForm => ({ fuelType: "DIESEL", litres: "", estimatedPrice: "", neededBy: "", supplierId: "", reason: "" })
export const fromRequisition = (r: Requisition): ReqForm => ({ fuelType: r.fuelType, litres: String(r.litres), estimatedPrice: r.estimatedPrice == null ? "" : String(r.estimatedPrice),
  neededBy: r.neededBy ?? "", supplierId: r.supplierId ?? "", reason: r.reason })

export const FUELS = ["DIESEL", "DIESEL_50PPM", "DIESEL_500PPM", "PETROL_95", "PETROL_93", "PARAFFIN"]
export const STATUS_LABEL: Record<string, string> = { DRAFT: "Draft", PENDING_APPROVAL: "Waiting for approval", APPROVED: "Approved", CONVERTED: "Ordered", CANCELLED: "Cancelled" }

export function validateRequisition(f: ReqForm): string | null {
  const l = Number(f.litres)
  if (!f.fuelType) return "Choose the fuel type."
  if (!f.litres.trim() || Number.isNaN(l) || l <= 0) return "Litres must be more than zero."
  if (f.estimatedPrice.trim() && (Number.isNaN(Number(f.estimatedPrice)) || Number(f.estimatedPrice) < 0)) return "The estimated price cannot be negative."
  if (f.reason.trim().length < 5) return "Say why this fuel is needed (at least 5 characters)."
  return null
}
export const toRequisitionRequest = (f: ReqForm) => ({
  fuelType: f.fuelType, litres: Number(f.litres), tankId: null, supplierId: f.supplierId || null,
  estimatedPrice: f.estimatedPrice.trim() ? Number(f.estimatedPrice) : null, neededBy: f.neededBy || null, reason: f.reason.trim(),
})

export type ReqAction = "edit" | "submit" | "cancel" | "convert"
/** What can be done to a requisition now. Submitting needs a price so approval can judge the value. */
export function actionsFor(r: Requisition): ReqAction[] {
  switch (r.status) {
    case "DRAFT": return ["edit", "submit", "cancel"]
    case "PENDING_APPROVAL": return ["cancel"]
    case "APPROVED": return ["convert", "cancel"]
    default: return []
  }
}
export const canSubmit = (r: Requisition) => r.estimatedPrice != null

/** Null when a draft purchase order can be raised. The supplier and price come from the requisition unless given here. */
export function validateConvert(r: Requisition, supplierId: string, price: string): string | null {
  if (!(supplierId || r.supplierId)) return "Choose the supplier to order from."
  const p = price.trim() ? Number(price) : r.estimatedPrice
  if (p == null || Number.isNaN(p)) return "Enter the price per litre."
  if (p < 0) return "Price cannot be negative."
  return null
}
