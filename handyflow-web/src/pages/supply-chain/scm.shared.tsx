// src/pages/supply-chain/scm.shared.tsx
// Shared types, helpers and UI components for all SCM tabs
import { X, AlertTriangle, CheckCircle, AlertCircle, Info } from "lucide-react"

// ── Types ─────────────────────────────────────────────────────────────────────

export interface Summary   { totalSuppliers:number; openPurchaseOrders:number; pendingInvoices:number; invoicesForApproval:number; lowStockItems:number; overdueInvoices:number }
export interface Supplier  { id:string; name:string; contactName:string|null; contactEmail:string|null; contactPhone:string|null; bbbeeLevel:number|null; paymentTermsDays:number; status:string; onTimeRate:number|null; totalOrders:number; city:string|null; vatNumber:string|null; registrationNumber:string|null; bankName:string|null; bankAccount:string|null; notes:string|null }
export interface PurchaseOrder { id:string; orderNumber:string; supplierName:string; supplierId:string; status:string; totalAmount:number; currency:string; requiredByDate:string|null; projectRef:string|null; deliverToLocation:string|null; notes:string|null }
export interface PoLine    { id:string; purchaseOrderId:string; itemName:string; supplierSku:string|null; qtyOrdered:number; unitCost:number; lineTotal:number; lineTotalIncl:number; vatAmount:number; vatRate:number; catalogueItemId:string|null; isFullyReceived:boolean }
export interface InventoryItem { id:string; catalogueItemId:string; qtyOnHand:number; reorderPoint:number; reorderQty:number; avgCost:number; binLocation:string|null; lowStock:boolean }
export interface StockMovement { id:string; movementType:string; qty:number; costPerUnit:number; reference:string|null; movedAt:string; movedByName:string|null }
export interface StockLocation { id:string; name:string; locationType:string; isDefault:boolean }
export interface SupplierInvoice { id:string; invoiceNumber:string|null; supplierInvoiceRef:string|null; supplierId:string; totalAmount:number; dueDate:string; status:string; matchStatus:string; overdue:boolean }
export interface CatalogueItem { id:string; name:string; code:string|null; description:string|null; unitPrice:number|null; unit:string|null }

// ── Helpers ───────────────────────────────────────────────────────────────────

export function unwrap<T>(res: any): T[] {
  const d = res?.data?.data ?? res?.data ?? []
  if (Array.isArray(d)) return d as T[]
  if (d?.content) return d.content as T[]
  return []
}

export const fmtR = (n: number | null | undefined) =>
  `R ${Number(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

export const fmtDate = (d: string | null | undefined) =>
  d ? new Date(d).toLocaleDateString("en-ZA") : "—"

// ── Status colours ────────────────────────────────────────────────────────────

const SC: Record<string, { bg: string; color: string }> = {
  DRAFT:               { bg: "var(--hf-surface-sunken)", color: "var(--hf-text-tertiary)" },
  PENDING_APPROVAL:    { bg: "var(--hf-warning-soft-strong)", color: "var(--hf-warning-text-deep)" },
  APPROVED:            { bg: "var(--hf-success-soft-strong)", color: "var(--hf-success-text-strong)" },
  SENT:                { bg: "var(--hf-info-soft-strong)", color: "var(--hf-info-text)" },
  ACKNOWLEDGED:        { bg: "var(--hf-violet-soft-strong)", color: "var(--hf-violet-text)" },
  PARTIALLY_RECEIVED:  { bg: "var(--hf-violet-soft-strong)", color: "var(--hf-violet-text)" },
  FULLY_RECEIVED:      { bg: "var(--hf-success-soft-strong)", color: "var(--hf-success-text-strong)" },
  INVOICED:            { bg: "var(--hf-violet-soft-strong)", color: "var(--hf-violet-text)" },
  CANCELLED:           { bg: "var(--hf-danger-soft)", color: "var(--hf-danger-text)" },
  RECEIVED:            { bg: "var(--hf-surface-sunken)", color: "var(--hf-text-tertiary)" },
  UNDER_REVIEW:        { bg: "var(--hf-warning-soft-strong)", color: "var(--hf-warning-text-deep)" },
  PAID:                { bg: "var(--hf-success-soft-strong)", color: "var(--hf-success-text-strong)" },
  DISPUTED:            { bg: "var(--hf-danger-soft)", color: "var(--hf-danger-text)" },
  ACTIVE:              { bg: "var(--hf-success-soft-strong)", color: "var(--hf-success-text-strong)" },
  INACTIVE:            { bg: "var(--hf-surface-sunken)", color: "var(--hf-text-tertiary)" },
  BLACKLISTED:         { bg: "var(--hf-danger-soft)", color: "var(--hf-danger-text)" },
  MATCHED:             { bg: "var(--hf-success-soft-strong)", color: "var(--hf-success-text-strong)" },
  PO_MATCHED:          { bg: "var(--hf-info-soft-strong)", color: "var(--hf-info-text)" },
  PARTIAL_MATCH:       { bg: "var(--hf-warning-soft-strong)", color: "var(--hf-warning-text-deep)" },
  NO_PO:               { bg: "var(--hf-danger-soft)", color: "var(--hf-danger-text)" },
  POSTED:              { bg: "var(--hf-success-soft-strong)", color: "var(--hf-success-text-strong)" },
  OPENING:             { bg: "var(--hf-surface-sunken)", color: "var(--hf-text-tertiary)" },
  PURCHASE:            { bg: "var(--hf-info-soft-strong)", color: "var(--hf-info-text)" },
  ADJUSTMENT:          { bg: "var(--hf-warning-soft-strong)", color: "var(--hf-warning-text-deep)" },
}

export function Badge({ status }: { status: string }) {
  const s = SC[status] ?? { bg: "var(--hf-surface-sunken)", color: "var(--hf-text-tertiary)" }
  return (
    <span style={{ background: s.bg, color: s.color, fontSize: 11, fontWeight: 700,
      padding: "2px 9px", borderRadius: 20, whiteSpace: "nowrap" as const }}>
      {status.replace(/_/g, " ")}
    </span>
  )
}

// ── Shared styles ─────────────────────────────────────────────────────────────

export const inp: React.CSSProperties = {
  width: "100%", padding: "9px 12px", border: "1.5px solid var(--hf-border)",
  borderRadius: 8, fontSize: 13, boxSizing: "border-box" as const,
  outline: "none", background: "var(--hf-surface)", color: "var(--hf-text)",
}
export const TH: React.CSSProperties = {
  padding: "10px 14px", textAlign: "left" as const, fontSize: 11,
  fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase" as const, letterSpacing: "0.05em",
}
export const TD: React.CSSProperties = {
  padding: "11px 14px", fontSize: 13, color: "var(--hf-text-secondary)", verticalAlign: "middle" as const,
}

// ── Reusable components ───────────────────────────────────────────────────────

export function Modal({ title, children, onClose, wide }: { title: string; children: React.ReactNode; onClose: () => void; wide?: boolean }) {
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.45)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
      <div style={{ background: "var(--hf-surface)", borderRadius: 14, padding: 28, width: wide ? 720 : 600, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.18)" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
          <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: "var(--hf-text)" }}>{title}</h3>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-faint)", padding: 4 }}><X size={18} /></button>
        </div>
        {children}
      </div>
    </div>
  )
}

export function ModalFooter({ onCancel, onConfirm, label, loading }: { onCancel: () => void; onConfirm: () => void; label: string; loading?: boolean }) {
  return (
    <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 24 }}>
      <button onClick={onCancel} style={{ padding: "8px 16px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", fontSize: 13, cursor: "pointer", color: "var(--hf-text-secondary)" }}>Cancel</button>
      <button onClick={onConfirm} disabled={loading} style={{ padding: "8px 16px", background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: loading ? "not-allowed" : "pointer", opacity: loading ? .6 : 1 }}>{label}</button>
    </div>
  )
}

export function Field({ label, children, span }: { label: string; children: React.ReactNode; span?: number }) {
  return (
    <div style={span ? { gridColumn: `span ${span}` } : undefined}>
      <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 5 }}>{label}</label>
      {children}
    </div>
  )
}

export function ErrBox({ msg }: { msg: string }) {
  return <div style={{ marginTop: 10, padding: "8px 12px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, color: "var(--hf-danger-text)", fontSize: 13 }}>{msg}</div>
}

export function Spinner() {
  return <div style={{ padding: "48px 0", textAlign: "center", color: "var(--hf-text-faint)", fontSize: 13 }}>Loading…</div>
}

export function EmptyState({ icon: Icon, title, sub }: { icon: React.ElementType; title: string; sub: string }) {
  return (
    <div style={{ textAlign: "center", padding: "56px 20px", color: "var(--hf-text-faint)" }}>
      <Icon size={38} style={{ marginBottom: 12, opacity: .25 }} />
      <div style={{ fontWeight: 600, color: "var(--hf-text-tertiary)", marginBottom: 4 }}>{title}</div>
      <div style={{ fontSize: 13 }}>{sub}</div>
    </div>
  )
}

export function Banner({ variant, children }: { variant: "error" | "warning" | "info"; children: React.ReactNode }) {
  const styles = {
    error:   { bg: "var(--hf-danger-soft)", border: "var(--hf-danger-border)", color: "var(--hf-danger-text)",   Icon: AlertTriangle },
    warning: { bg: "var(--hf-warning-soft-strong)", border: "var(--hf-warning-border-strong)", color: "var(--hf-warning-text-deep)",   Icon: AlertCircle   },
    info:    { bg: "var(--hf-info-soft)", border: "var(--hf-info-border)", color: "var(--hf-info-text)",   Icon: Info          },
  }[variant]
  return (
    <div style={{ background: styles.bg, border: `1px solid ${styles.border}`, borderRadius: 10, padding: "12px 16px", marginBottom: 14, display: "flex", alignItems: "center", gap: 10 }}>
      <styles.Icon size={16} style={{ flexShrink: 0, color: styles.color }} />
      <span style={{ fontSize: 13, color: styles.color, fontWeight: 500 }}>{children}</span>
    </div>
  )
}

export function ActionChip({ label, color, bg, border, onClick }: { label: string; color: string; bg: string; border: string; onClick: () => void }) {
  return (
    <button onClick={onClick} style={{ padding: "4px 10px", background: bg, color, border: `1px solid ${border}`, borderRadius: 6, fontSize: 11, fontWeight: 700, cursor: "pointer" }}>
      {label}
    </button>
  )
}

export function filterPill(active: boolean): React.CSSProperties {
  return {
    padding: "5px 12px", borderRadius: 20, cursor: "pointer", fontSize: 12, fontWeight: active ? 700 : 400,
    border: active ? "1.5px solid var(--hf-warning)" : "1px solid var(--hf-border)",
    background: active ? "var(--hf-warning-soft-strong)" : "var(--hf-surface)", color: active ? "var(--hf-warning-text-deep)" : "var(--hf-text-muted)",
  }
}
