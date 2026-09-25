import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { FileText, Plus, X, CheckCircle, AlertCircle } from "lucide-react"
import { apiClient } from "../../api/client"

interface VatPeriod { id: string; periodStart: string; periodEnd: string; status: string
  outputVat: number; inputVat: number; vatPayable: number }
interface Vat201 { from: string; to: string; invoiceCount: number; totalSales: number
  outputVat: number; inputVat: number; netVatPayable: number }

const fmtR  = (n: number) => `R ${(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`
const fmtDt = (d: string) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const inp: React.CSSProperties = {
  width: "100%", padding: "8px 12px", border: "1.5px solid var(--hf-border)",
  borderRadius: 8, fontSize: 13, outline: "none", boxSizing: "border-box",
}

const STATUS_STYLE: Record<string, { bg: string; color: string }> = {
  OPEN:      { bg: "var(--hf-success-soft)", color: "var(--hf-success-text-strong)" },
  CLOSED:    { bg: "var(--hf-surface-sunken)", color: "var(--hf-text-tertiary)" },
  SUBMITTED: { bg: "var(--hf-info-soft)", color: "var(--hf-info-text)" },
}

export default function VatReturnsTab() {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [selectedPeriod, setSelectedPeriod] = useState<VatPeriod | null>(null)
  const [form, setForm] = useState({ periodStart: "", periodEnd: "" })
  const [vatFrom, setVatFrom] = useState("")
  const [vatTo,   setVatTo]   = useState("")
  const [error, setError] = useState("")

  const { data: periods = [], isLoading } = useQuery<VatPeriod[]>({
    queryKey: ["vat-periods"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/accounting/vat-periods")
      return (res.data?.data ?? res.data) as VatPeriod[]
    },
  })

  const { data: vat201, isLoading: vatLoading, refetch: runVat201 } = useQuery<Vat201>({
    queryKey: ["vat201", vatFrom, vatTo],
    enabled: false,
    queryFn: async () => {
      const res = await apiClient.get(`/api/v1/accounting/reports/vat201?from=${vatFrom}&to=${vatTo}`)
      return (res.data?.data ?? res.data) as Vat201
    },
  })

  const create = useMutation({
    mutationFn: () => apiClient.post("/api/v1/accounting/vat-periods", form),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["vat-periods"] }); setShowCreate(false); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to create VAT period"),
  })

  const close = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/accounting/vat-periods/${id}/close`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["vat-periods"] }); setSelectedPeriod(null) },
  })

  const attach = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/accounting/vat-periods/${id}/attach-vat201`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["vat-periods"] }); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to attach VAT201 result"),
  })

  const openPeriod = periods.find(p => p.status === "OPEN")
  const datesMatchOpenPeriod = openPeriod && openPeriod.periodStart === vatFrom && openPeriod.periodEnd === vatTo

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div>
          <h2 style={{ fontSize: 16, fontWeight: 700, color: "var(--hf-text)", margin: 0 }}>VAT Returns</h2>
          <p style={{ fontSize: 12, color: "var(--hf-text-faint)", margin: "3px 0 0" }}>
            SARS VAT201 — manage periods, capture output/input VAT from invoices and journals
          </p>
        </div>
        <button onClick={() => { setShowCreate(true); setError("") }}
          style={{ display: "flex", alignItems: "center", gap: 6, background: "var(--hf-violet)", color: "white",
            border: "none", borderRadius: 9, padding: "9px 16px", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={14} /> New VAT Period
        </button>
      </div>

      {/* VAT201 Calculator */}
      <div style={{ background: "white", border: "1px solid var(--hf-violet-border)", borderRadius: 12,
        padding: 20, marginBottom: 20 }}>
        <div style={{ fontSize: 14, fontWeight: 700, color: "var(--hf-violet-text)", marginBottom: 12 }}>
          VAT201 Calculator
        </div>
        <div style={{ display: "flex", gap: 12, alignItems: "flex-end" }}>
          <div>
            <label style={{ display: "block", fontSize: 11, fontWeight: 700, color: "var(--hf-text-secondary)", marginBottom: 5 }}>From</label>
            <input type="date" value={vatFrom} onChange={e => setVatFrom(e.target.value)} style={{ ...inp, width: "auto" }} />
          </div>
          <div>
            <label style={{ display: "block", fontSize: 11, fontWeight: 700, color: "var(--hf-text-secondary)", marginBottom: 5 }}>To</label>
            <input type="date" value={vatTo} onChange={e => setVatTo(e.target.value)} style={{ ...inp, width: "auto" }} />
          </div>
          <button disabled={!vatFrom || !vatTo || vatLoading}
            onClick={() => runVat201()}
            style={{ padding: "8px 18px", background: "var(--hf-violet)", color: "white",
              border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
            {vatLoading ? "Calculating..." : "Calculate VAT201"}
          </button>
        </div>

        {vat201 && (
          <div style={{ marginTop: 16, display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 12 }}>
            {[
              { label: "Total Sales (excl VAT)", value: fmtR(vat201.totalSales), sub: `${vat201.invoiceCount} invoices`, color: "var(--hf-primary-text)" },
              { label: "Output VAT (Box 4)", value: fmtR(vat201.outputVat), sub: "VAT charged on sales", color: "var(--hf-accent-text)" },
              { label: "Input VAT (Box 15)", value: fmtR(vat201.inputVat), sub: "VAT claimable on purchases", color: "var(--hf-violet-text)" },
              { label: "Net VAT Payable (Box 17)", value: fmtR(vat201.netVatPayable), sub: vat201.netVatPayable >= 0 ? "Payable to SARS" : "Refund from SARS",
                color: vat201.netVatPayable >= 0 ? "var(--hf-danger-text)" : "var(--hf-success-text-strong)" },
            ].map(k => (
              <div key={k.label} style={{ background: "var(--hf-violet-soft)", borderRadius: 8, padding: "12px 14px" }}>
                <div style={{ fontSize: 10, fontWeight: 700, color: "var(--hf-violet-text)", textTransform: "uppercase",
                  letterSpacing: "0.05em", marginBottom: 4 }}>{k.label}</div>
                <div style={{ fontSize: 18, fontWeight: 800, color: k.color }}>{k.value}</div>
                <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 2 }}>{k.sub}</div>
              </div>
            ))}
          </div>
        )}

        {vat201 && openPeriod && (
          <div style={{ marginTop: 14, padding: "12px 14px", borderRadius: 8,
            background: datesMatchOpenPeriod ? "var(--hf-success-soft)" : "var(--hf-warning-soft)",
            display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12 }}>
            <div style={{ fontSize: 12, color: datesMatchOpenPeriod ? "var(--hf-success-text-strong)" : "var(--hf-warning-text-deep)" }}>
              {datesMatchOpenPeriod ? (
                <>Matches your open period ({fmtDt(openPeriod.periodStart)} – {fmtDt(openPeriod.periodEnd)}).</>
              ) : (
                <>Your open period is {fmtDt(openPeriod.periodStart)} – {fmtDt(openPeriod.periodEnd)}, different from the dates above.
                  Attaching recalculates using the <strong>period's own dates</strong>, not what's shown here.</>
              )}
            </div>
            <button disabled={attach.isPending} onClick={() => attach.mutate(openPeriod.id)}
              style={{ padding: "7px 14px", border: "none", borderRadius: 7, fontSize: 12, fontWeight: 700,
                background: "var(--hf-violet)", color: "white", cursor: "pointer", whiteSpace: "nowrap" as const }}>
              {attach.isPending ? "Attaching..." : "Attach to open period"}
            </button>
          </div>
        )}
        {vat201 && !openPeriod && (
          <div style={{ marginTop: 14, padding: "12px 14px", background: "var(--hf-surface-sunken)", borderRadius: 8,
            fontSize: 12, color: "var(--hf-text-muted)" }}>
            No open VAT period to attach this to — create one first.
          </div>
        )}
        {error && (
          <div style={{ marginTop: 12, padding: "8px 12px", background: "var(--hf-danger-soft)", borderRadius: 8,
            fontSize: 12, color: "var(--hf-danger-text)" }}>{error}</div>
        )}
      </div>

      {/* VAT Periods */}
      <div style={{ background: "white", border: "1px solid var(--hf-border)", borderRadius: 12, overflow: "hidden" }}>
        <div style={{ padding: "12px 20px", background: "var(--hf-surface-muted)", borderBottom: "1px solid var(--hf-border-subtle)" }}>
          <span style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)" }}>VAT Periods</span>
        </div>
        {isLoading ? (
          <div style={{ padding: 40, textAlign: "center", color: "var(--hf-text-faint)" }}>Loading...</div>
        ) : periods.length === 0 ? (
          <div style={{ padding: 40, textAlign: "center", color: "var(--hf-text-faint)" }}>No VAT periods — create one to start.</div>
        ) : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead>
              <tr style={{ background: "var(--hf-surface-muted)", borderBottom: "1px solid var(--hf-border-subtle)" }}>
                {["Period", "Output VAT", "Input VAT", "Net Payable", "Status", "Actions"].map(h => (
                  <th key={h} style={{ textAlign: "left", padding: "9px 16px", fontSize: 11,
                    fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase", letterSpacing: "0.05em" }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {periods.map(p => {
                const ss = STATUS_STYLE[p.status] ?? STATUS_STYLE.OPEN
                return (
                  <tr key={p.id} style={{ borderBottom: "1px solid var(--hf-border-subtle)" }}
                    onMouseEnter={e => (e.currentTarget.style.background = "var(--hf-surface-muted)")}
                    onMouseLeave={e => (e.currentTarget.style.background = "white")}>
                    <td style={{ padding: "12px 16px", fontSize: 13, fontWeight: 600, color: "var(--hf-text)" }}>
                      {fmtDt(p.periodStart)} – {fmtDt(p.periodEnd)}
                    </td>
                    <td style={{ padding: "12px 16px", fontSize: 13, color: "var(--hf-accent-text)", fontWeight: 600 }}>{fmtR(p.outputVat)}</td>
                    <td style={{ padding: "12px 16px", fontSize: 13, color: "var(--hf-violet-text)", fontWeight: 600 }}>{fmtR(p.inputVat)}</td>
                    <td style={{ padding: "12px 16px", fontSize: 13, fontWeight: 700,
                      color: p.vatPayable >= 0 ? "var(--hf-danger-text)" : "var(--hf-success-text-strong)" }}>{fmtR(p.vatPayable)}</td>
                    <td style={{ padding: "12px 16px" }}>
                      <span style={{ background: ss.bg, color: ss.color, fontSize: 11,
                        fontWeight: 700, padding: "3px 10px", borderRadius: 10 }}>{p.status}</span>
                    </td>
                    <td style={{ padding: "12px 16px" }}>
                      {p.status === "OPEN" && (
                        <button onClick={() => close.mutate(p.id)}
                          style={{ display: "flex", alignItems: "center", gap: 4, padding: "5px 10px",
                            background: "var(--hf-success-soft)", color: "var(--hf-success-text-strong)", border: "1px solid var(--hf-success-border-subtle)",
                            borderRadius: 6, fontSize: 11, fontWeight: 600, cursor: "pointer" }}>
                          <CheckCircle size={11} /> Close Period
                        </button>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </div>

      {/* Create VAT Period Modal */}
      {showCreate && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex",
          alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "white", borderRadius: 16, padding: 28, width: 420, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700 }}>New VAT Period</h3>
              <button onClick={() => setShowCreate(false)}
                style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-faint)" }}><X size={18} /></button>
            </div>
            <div style={{ display: "flex", gap: 12, marginBottom: 16 }}>
              <div style={{ flex: 1 }}>
                <label style={{ display: "block", fontSize: 12, fontWeight: 700, color: "var(--hf-text-secondary)", marginBottom: 5 }}>Period Start *</label>
                <input type="date" value={form.periodStart}
                  onChange={e => setForm(p => ({ ...p, periodStart: e.target.value }))} style={inp} />
              </div>
              <div style={{ flex: 1 }}>
                <label style={{ display: "block", fontSize: 12, fontWeight: 700, color: "var(--hf-text-secondary)", marginBottom: 5 }}>Period End *</label>
                <input type="date" value={form.periodEnd}
                  onChange={e => setForm(p => ({ ...p, periodEnd: e.target.value }))} style={inp} />
              </div>
            </div>
            <div style={{ padding: "10px 14px", background: "var(--hf-violet-soft)", borderRadius: 8, fontSize: 12,
              color: "var(--hf-violet-text)", marginBottom: 16 }}>
              Only one OPEN period is allowed at a time. Close the current period before opening a new one.
            </div>
            {error && (
              <div style={{ padding: "8px 12px", background: "var(--hf-danger-soft)", borderRadius: 8,
                fontSize: 12, color: "var(--hf-danger-text)", marginBottom: 12 }}>{error}</div>
            )}
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
              <button onClick={() => setShowCreate(false)}
                style={{ padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "white", fontSize: 13, cursor: "pointer", color: "var(--hf-text-secondary)" }}>Cancel</button>
              <button disabled={create.isPending || !form.periodStart || !form.periodEnd}
                onClick={() => create.mutate()}
                style={{ padding: "9px 20px", border: "none", borderRadius: 9, fontSize: 13, fontWeight: 700,
                  background: "var(--hf-violet)", color: "white", cursor: "pointer" }}>
                {create.isPending ? "Creating..." : "Create Period"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
