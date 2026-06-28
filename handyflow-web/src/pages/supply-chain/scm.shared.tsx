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
  DRAFT:               { bg: "#F1F5F9", color: "#475569" },
  PENDING_APPROVAL:    { bg: "#FEF3C7", color: "#92400E" },
  APPROVED:            { bg: "#DCFCE7", color: "#166534" },
  SENT:                { bg: "#DBEAFE", color: "#1D4ED8" },
  ACKNOWLEDGED:        { bg: "#EDE9FE", color: "#7C3AED" },
  PARTIALLY_RECEIVED:  { bg: "#EDE9FE", color: "#7C3AED" },
  FULLY_RECEIVED:      { bg: "#DCFCE7", color: "#166534" },
  INVOICED:            { bg: "#F3E8FF", color: "#7C3AED" },
  CANCELLED:           { bg: "#FEF2F2", color: "#DC2626" },
  RECEIVED:            { bg: "#F1F5F9", color: "#475569" },
  UNDER_REVIEW:        { bg: "#FEF3C7", color: "#92400E" },
  PAID:                { bg: "#DCFCE7", color: "#166534" },
  DISPUTED:            { bg: "#FEF2F2", color: "#DC2626" },
  ACTIVE:              { bg: "#DCFCE7", color: "#166534" },
  INACTIVE:            { bg: "#F1F5F9", color: "#475569" },
  BLACKLISTED:         { bg: "#FEF2F2", color: "#DC2626" },
  MATCHED:             { bg: "#DCFCE7", color: "#166534" },
  PO_MATCHED:          { bg: "#DBEAFE", color: "#1D4ED8" },
  PARTIAL_MATCH:       { bg: "#FEF3C7", color: "#92400E" },
  NO_PO:               { bg: "#FEF2F2", color: "#DC2626" },
  POSTED:              { bg: "#DCFCE7", color: "#166534" },
  OPENING:             { bg: "#F1F5F9", color: "#475569" },
  PURCHASE:            { bg: "#DBEAFE", color: "#1D4ED8" },
  ADJUSTMENT:          { bg: "#FEF3C7", color: "#92400E" },
}

export function Badge({ status }: { status: string }) {
  const s = SC[status] ?? { bg: "#F1F5F9", color: "#475569" }
  return (
    <span style={{ background: s.bg, color: s.color, fontSize: 11, fontWeight: 700,
      padding: "2px 9px", borderRadius: 20, whiteSpace: "nowrap" as const }}>
      {status.replace(/_/g, " ")}
    </span>
  )
}

// ── Shared styles ─────────────────────────────────────────────────────────────

export const inp: React.CSSProperties = {
  width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0",
  borderRadius: 8, fontSize: 13, boxSizing: "border-box" as const,
  outline: "none", background: "#fff", color: "#0F172A",
}
export const TH: React.CSSProperties = {
  padding: "10px 14px", textAlign: "left" as const, fontSize: 11,
  fontWeight: 700, color: "#94A3B8", textTransform: "uppercase" as const, letterSpacing: "0.05em",
}
export const TD: React.CSSProperties = {
  padding: "11px 14px", fontSize: 13, color: "#374151", verticalAlign: "middle" as const,
}

// ── Reusable components ───────────────────────────────────────────────────────

export function Modal({ title, children, onClose, wide }: { title: string; children: React.ReactNode; onClose: () => void; wide?: boolean }) {
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.45)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
      <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: wide ? 720 : 600, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.18)" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
          <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: "#0F172A" }}>{title}</h3>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", padding: 4 }}><X size={18} /></button>
        </div>
        {children}
      </div>
    </div>
  )
}

export function ModalFooter({ onCancel, onConfirm, label, loading }: { onCancel: () => void; onConfirm: () => void; label: string; loading?: boolean }) {
  return (
    <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 24 }}>
      <button onClick={onCancel} style={{ padding: "8px 16px", border: "1px solid #E2E8F0", borderRadius: 8, background: "#fff", fontSize: 13, cursor: "pointer", color: "#374151" }}>Cancel</button>
      <button onClick={onConfirm} disabled={loading} style={{ padding: "8px 16px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: loading ? "not-allowed" : "pointer", opacity: loading ? .6 : 1 }}>{label}</button>
    </div>
  )
}

export function Field({ label, children, span }: { label: string; children: React.ReactNode; span?: number }) {
  return (
    <div style={span ? { gridColumn: `span ${span}` } : undefined}>
      <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5 }}>{label}</label>
      {children}
    </div>
  )
}

export function ErrBox({ msg }: { msg: string }) {
  return <div style={{ marginTop: 10, padding: "8px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, color: "#DC2626", fontSize: 13 }}>{msg}</div>
}

export function Spinner() {
  return <div style={{ padding: "48px 0", textAlign: "center", color: "#94A3B8", fontSize: 13 }}>Loading…</div>
}

export function EmptyState({ icon: Icon, title, sub }: { icon: React.ElementType; title: string; sub: string }) {
  return (
    <div style={{ textAlign: "center", padding: "56px 20px", color: "#94A3B8" }}>
      <Icon size={38} style={{ marginBottom: 12, opacity: .25 }} />
      <div style={{ fontWeight: 600, color: "#475569", marginBottom: 4 }}>{title}</div>
      <div style={{ fontSize: 13 }}>{sub}</div>
    </div>
  )
}

export function Banner({ variant, children }: { variant: "error" | "warning" | "info"; children: React.ReactNode }) {
  const styles = {
    error:   { bg: "#FEF2F2", border: "#FECACA", color: "#DC2626",   Icon: AlertTriangle },
    warning: { bg: "#FEF3C7", border: "#FCD34D", color: "#92400E",   Icon: AlertCircle   },
    info:    { bg: "#EFF6FF", border: "#BFDBFE", color: "#1D4ED8",   Icon: Info          },
  }[variant]
  return (
    <div style={{ background: styles.bg, border: `1px solid ${styles.border}`, borderRadius: 10, padding: "12px 16px", marginBottom: 14, display: "flex", alignItems: "center", gap: 10 }}>
      <styles.Icon size={16} color={styles.color} style={{ flexShrink: 0 }} />
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
    border: active ? "1.5px solid #D97706" : "1px solid #E2E8F0",
    background: active ? "#FEF3C7" : "#fff", color: active ? "#92400E" : "#64748B",
  }
}
