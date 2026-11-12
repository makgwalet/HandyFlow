// Pure helpers for Wholesale controls: supplier approval, held stock adjustments, the two control settings.
export interface AdjustmentRequest {
  id: string; tankId: string; tankName: string; litres: number; reason: string; status: "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED"
  requestedBy: string | null; requestedAt: string; decidedBy?: string | null; decidedAt?: string | null; decisionNote?: string | null
}
export interface Controls { requireSupplierApproval: boolean; adjustmentThresholdLitres: number | null; varianceTolerancePct?: number | null; cashVarianceTolerance?: number | null; requireSeparateCloser?: boolean; priceChangeApprovalPct?: number | null; minMarginPct?: number | null }
export interface ControlsForm { requireSupplierApproval: boolean; threshold: string; variance: string; cashTol: string; separateCloser: boolean; priceChange: string; minMargin: string }

export const SUPPLIER_STATUS_LABEL: Record<string, string> = { APPROVED: "Approved", PENDING_APPROVAL: "Waiting for approval", SUSPENDED: "Suspended" }

export const controlsToForm = (c: Controls): ControlsForm => ({ requireSupplierApproval: c.requireSupplierApproval, threshold: c.adjustmentThresholdLitres == null ? "" : String(c.adjustmentThresholdLitres), variance: c.varianceTolerancePct == null ? "" : String(c.varianceTolerancePct), cashTol: c.cashVarianceTolerance == null ? "5" : String(c.cashVarianceTolerance), separateCloser: !!c.requireSeparateCloser, priceChange: c.priceChangeApprovalPct == null ? "" : String(c.priceChangeApprovalPct), minMargin: c.minMarginPct == null ? "" : String(c.minMarginPct) })

/** Blank threshold means no limit. */
export function validateControls(f: ControlsForm): string | null {
  if (f.threshold.trim() !== "") {
    const n = Number(f.threshold)
    if (!Number.isFinite(n) || n <= 0) return "Enter a number of litres above 0, or leave it empty for no limit."
  }
  if (f.variance.trim() !== "") {
    const v = Number(f.variance)
    if (!Number.isFinite(v) || v < 0 || v > 100) return "The variance tolerance must be between 0 and 100 percent, or empty to switch the check off."
  }
  const ct = Number(f.cashTol)
  if (f.cashTol.trim() === "" || !Number.isFinite(ct) || ct < 0) return "Enter the cash tolerance in rand (0 or more)."
  if (f.priceChange.trim() !== "") {
    const p = Number(f.priceChange)
    if (!Number.isFinite(p) || p < 0) return "Enter the price-change limit as a percentage (0 or more), or leave it empty to switch it off."
  }
  if (f.minMargin.trim() !== "") {
    const m = Number(f.minMargin)
    if (!Number.isFinite(m) || m < 0 || m >= 100) return "The minimum margin must be between 0 and under 100 percent, or empty to switch it off."
  }
  return null
}
export const toControlsRequest = (f: ControlsForm) => ({ requireSupplierApproval: f.requireSupplierApproval, adjustmentThresholdLitres: f.threshold.trim() === "" ? null : Number(f.threshold), varianceTolerancePct: f.variance.trim() === "" ? null : Number(f.variance), cashVarianceTolerance: Number(f.cashTol), requireSeparateCloser: f.separateCloser, priceChangeApprovalPct: f.priceChange.trim() === "" ? 0 : Number(f.priceChange), minMarginPct: f.minMargin.trim() === "" ? 0 : Number(f.minMargin) })

/** What the adjust endpoint's answer means for the person who sent it. */
export function adjustMessage(outcome: { pendingApproval?: boolean } | undefined): string | null {
  return outcome?.pendingApproval ? "This adjustment is large enough to need a second person. It has been sent for approval, and no stock has moved yet." : null
}

/** A person may decide a request only if it is pending and they did not raise it. The server enforces this; this only hides the buttons. */
export const canDecide = (r: AdjustmentRequest, me: string | undefined) => r.status === "PENDING" && !!me && r.requestedBy !== me
export const canWithdraw = (r: AdjustmentRequest, me: string | undefined) => r.status === "PENDING" && !!me && r.requestedBy === me
