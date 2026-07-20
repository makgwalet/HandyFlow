// src/pages/accountant/DeadlinesTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Calendar, CheckCircle, AlertTriangle, Clock, Filter, ChevronDown, Layers } from "lucide-react"

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return Array.isArray(p) ? p : p?.content ?? [] }
const fmtD   = (d: any) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, outline: "none" }
const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }

const TYPE_COLOR: Record<string, string> = {
  VAT201: "#0D9488", ITR14: "#1D4ED8", ITR12: "#7C3AED", EMP201: "#D97706",
  EMP501: "#EA580C", IRP6_P1: "#166534", IRP6_P2: "#166534", IRP6_P3: "#166534",
  CIPC_RETURN: "#64748B", OTHER: "#94A3B8",
}
const STATUS_CFG: Record<string, { label: string; color: string; bg: string }> = {
  PENDING: { label: "Pending", color: "#D97706", bg: "#FFFBEB" },
  FILED:   { label: "Filed",   color: "#166534", bg: "#DCFCE7" },
  OVERDUE: { label: "Overdue", color: "#DC2626", bg: "#FEF2F2" },
  WAIVED:  { label: "Waived",  color: "#64748B", bg: "#F1F5F9" },
}

export default function DeadlinesTab() {
  const qc = useQueryClient()
  const [filterType,   setFilterType]   = useState("ALL")
  const [filterStatus, setFilterStatus] = useState("ALL")
  const [filing,       setFiling]       = useState<any>(null)
  const [fileForm,     setFileForm]     = useState({ filedDate: "", sarsReference: "", filingAmount: "", notes: "" })
  const [error, setError] = useState("")

  // NEW: closes the accountant module audit's "bulk deadline
  // generation" quick-win gap — generateDeadlines was per-client only;
  // a practice with many clients had to click through each one
  // individually at year-start.
  const [bulkResult, setBulkResult] = useState<any>(null)
  const generateAllMutation = useMutation({
    mutationFn: () => apiClient.post("/api/v1/accountant/deadlines/generate-all", { periodYear: new Date().getFullYear() }),
    onSuccess: (r) => {
      qc.invalidateQueries({ queryKey: ["acc-deadlines"] })
      qc.invalidateQueries({ queryKey: ["accountant-dashboard"] })
      setBulkResult(r.data?.data ?? r.data)
      setError("")
    },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to generate deadlines"),
  })

  const today = new Date().toISOString().split("T")[0]
  const in90  = new Date(Date.now() + 90 * 864e5).toISOString().split("T")[0]

  const { data: deadlines = [], isLoading } = useQuery<any[]>({
    queryKey: ["acc-deadlines"],
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/accountant/deadlines?from=${today}&to=${in90}`)),
  })

  const fileMutation = useMutation({
    mutationFn: ({ clientId, deadlineId, body }: any) =>
      apiClient.post(`/api/v1/accountant/clients/${clientId}/deadlines/${deadlineId}/file`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["acc-deadlines"] })
      qc.invalidateQueries({ queryKey: ["accountant-dashboard"] })
      setFiling(null)
      setFileForm({ filedDate: "", sarsReference: "", filingAmount: "", notes: "" })
      setError("")
    },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to record filing"),
  })

  const filtered = (deadlines as any[]).filter(d => {
    if (filterType !== "ALL"   && d.deadlineType !== filterType)   return false
    if (filterStatus !== "ALL" && d.status !== filterStatus)       return false
    return true
  })

  const grouped = filtered.reduce((acc: any, d: any) => {
    const key = d.clientName ?? "Unknown"
    if (!acc[key]) acc[key] = []
    acc[key].push(d)
    return acc
  }, {})

  const overdue  = filtered.filter((d: any) => d.status === "OVERDUE").length
  const pending  = filtered.filter((d: any) => d.status === "PENDING").length
  const filed    = filtered.filter((d: any) => d.status === "FILED").length

  return (
    <div>
      {/* Summary strip */}
      <div style={{ display: "flex", gap: 10, marginBottom: 20, flexWrap: "wrap", alignItems: "center" }}>
        {[
          { l: "Overdue",  v: overdue, color: "#DC2626", bg: "#FEF2F2" },
          { l: "Pending",  v: pending, color: "#D97706", bg: "#FFFBEB" },
          { l: "Filed",    v: filed,   color: "#166534", bg: "#DCFCE7" },
        ].map(s => (
          <div key={s.l} onClick={() => setFilterStatus(s.l.toUpperCase())}
            style={{ background: s.bg, borderRadius: 9, padding: "10px 16px", cursor: "pointer", border: filterStatus === s.l.toUpperCase() ? `2px solid ${s.color}` : "2px solid transparent", minWidth: 100 }}>
            <div style={{ fontSize: 20, fontWeight: 800, color: s.color }}>{s.v}</div>
            <div style={{ fontSize: 11, color: s.color, opacity: 0.8 }}>{s.l}</div>
          </div>
        ))}
        {filterStatus !== "ALL" && (
          <button onClick={() => setFilterStatus("ALL")}
            style={{ alignSelf: "center", padding: "6px 12px", border: "1px solid #E2E8F0", borderRadius: 7, background: "#fff", fontSize: 12, cursor: "pointer", color: "#64748B" }}>
            Clear filter
          </button>
        )}
        {/* NEW: closes the audit's "bulk deadline generation" gap. */}
        <button onClick={() => { setBulkResult(null); generateAllMutation.mutate() }}
          disabled={generateAllMutation.isPending}
          style={{ marginLeft: "auto", display: "flex", alignItems: "center", gap: 6, padding: "9px 16px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 13, fontWeight: 700, cursor: "pointer" }}>
          <Layers size={14} />
          {generateAllMutation.isPending ? "Generating..." : `Generate ${new Date().getFullYear()} Deadlines — All Clients`}
        </button>
      </div>

      {bulkResult && (
        <div style={{
          marginBottom: 20, padding: "12px 16px", borderRadius: 10,
          background: bulkResult.failures?.length > 0 ? "#FFFBEB" : "#DCFCE7",
          border: `1px solid ${bulkResult.failures?.length > 0 ? "#FDE68A" : "#86EFAC"}`,
        }}>
          <div style={{ fontSize: 13, fontWeight: 700, color: bulkResult.failures?.length > 0 ? "#92400E" : "#166534", marginBottom: bulkResult.failures?.length > 0 ? 6 : 0 }}>
            Generated deadlines for {bulkResult.succeeded} of {bulkResult.totalClients} client{bulkResult.totalClients !== 1 ? "s" : ""}.
          </div>
          {bulkResult.failures?.length > 0 && (
            <div style={{ fontSize: 12, color: "#92400E" }}>
              {bulkResult.failures.map((f: string, i: number) => <div key={i}>{"\u26a0"} {f}</div>)}
            </div>
          )}
        </div>
      )}

      {/* Filters */}
      <div style={{ display: "flex", gap: 10, marginBottom: 20, flexWrap: "wrap" }}>
        <select value={filterType} onChange={e => setFilterType(e.target.value)}
          style={{ padding: "7px 10px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, outline: "none", background: "#fff" }}>
          <option value="ALL">All types</option>
          {Object.keys(TYPE_COLOR).map(t => <option key={t} value={t}>{t}</option>)}
        </select>
        <select value={filterStatus} onChange={e => setFilterStatus(e.target.value)}
          style={{ padding: "7px 10px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, outline: "none", background: "#fff" }}>
          <option value="ALL">All statuses</option>
          {Object.keys(STATUS_CFG).map(s => <option key={s} value={s}>{STATUS_CFG[s].label}</option>)}
        </select>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading deadlines...</div>
      ) : filtered.length === 0 ? (
        <div style={{ textAlign: "center", padding: "50px 20px", color: "#94A3B8" }}>
          <Calendar size={40} style={{ marginBottom: 12, opacity: 0.3 }} />
          <div>No deadlines found for the selected filters.</div>
          <div style={{ fontSize: 12, marginTop: 6 }}>Generate deadlines from the Clients tab.</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 18 }}>
          {Object.entries(grouped).map(([client, items]: any) => (
            <div key={client}>
              <div style={{ fontSize: 12, fontWeight: 700, color: "#64748B", textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 8 }}>{client}</div>
              <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                {items.sort((a: any, b: any) => new Date(a.adjustedDueDate).getTime() - new Date(b.adjustedDueDate).getTime())
                  .map((d: any) => {
                    const sc    = STATUS_CFG[d.status] ?? STATUS_CFG.PENDING
                    const tc    = TYPE_COLOR[d.deadlineType] ?? "#64748B"
                    const overdue = d.daysUntilDue < 0 && d.status !== "FILED"
                    return (
                      <div key={d.id} style={{
                        display: "flex", alignItems: "center", justifyContent: "space-between",
                        padding: "11px 16px", border: `1px solid ${overdue ? "#FECACA" : "#E2E8F0"}`,
                        borderLeft: `3px solid ${overdue ? "#DC2626" : tc}`,
                        borderRadius: 8, background: overdue ? "#FFF8F8" : "#fff", gap: 10,
                      }}>
                        <div style={{ flex: 1 }}>
                          <div style={{ display: "flex", alignItems: "center", gap: 7, marginBottom: 3, flexWrap: "wrap" }}>
                            <span style={{ background: `${tc}18`, color: tc, padding: "1px 8px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{d.deadlineType}</span>
                            <span style={{ background: sc.bg, color: sc.color, padding: "1px 8px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{sc.label}</span>
                            {d.periodMonth ? <span style={{ fontSize: 12, color: "#64748B" }}>Period: {d.periodMonth}/{d.periodYear}</span>
                              : <span style={{ fontSize: 12, color: "#64748B" }}>Year: {d.periodYear}</span>}
                          </div>
                          <div style={{ fontSize: 11, color: "#94A3B8" }}>
                            Due: {fmtD(d.adjustedDueDate)}
                            {d.statutoryDueDate !== d.adjustedDueDate && ` (statutory: ${fmtD(d.statutoryDueDate)})`}
                            {d.filedDate && ` · Filed: ${fmtD(d.filedDate)}`}
                            {d.sarsReference && ` · Ref: ${d.sarsReference}`}
                          </div>
                        </div>
                        <div style={{ display: "flex", alignItems: "center", gap: 10, flexShrink: 0 }}>
                          <span style={{ fontWeight: 700, fontSize: 13, color: overdue ? "#DC2626" : d.daysUntilDue <= 7 ? "#D97706" : "#64748B" }}>
                            {d.status === "FILED" ? "✓" : overdue ? `${Math.abs(d.daysUntilDue)}d late` : `${d.daysUntilDue}d`}
                          </span>
                          {d.status !== "FILED" && d.status !== "WAIVED" && (
                            <button onClick={() => { setFiling(d); setFileForm({ filedDate: today, sarsReference: "", filingAmount: "", notes: "" }) }}
                              style={{ padding: "5px 12px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                              File
                            </button>
                          )}
                        </div>
                      </div>
                    )
                  })}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* File modal */}
      {filing && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 480, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <h3 style={{ margin: "0 0 6px", fontSize: 16, fontWeight: 700 }}>Record Filing</h3>
            <p style={{ margin: "0 0 20px", fontSize: 13, color: "#64748B" }}>
              {filing.deadlineType} · {filing.clientName} · {filing.periodMonth ? `${filing.periodMonth}/${filing.periodYear}` : filing.periodYear}
            </p>
            <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
              <div>
                <label style={lbl}>Filed date *</label>
                <input type="date" value={fileForm.filedDate} onChange={e => setFileForm(p => ({ ...p, filedDate: e.target.value }))} style={inp} />
              </div>
              <div>
                <label style={lbl}>SARS reference number</label>
                <input value={fileForm.sarsReference} onChange={e => setFileForm(p => ({ ...p, sarsReference: e.target.value }))} placeholder="e.g. 0000000000" style={inp} />
              </div>
              <div>
                <label style={lbl}>Amount paid / refund (R)</label>
                <input type="number" value={fileForm.filingAmount} onChange={e => setFileForm(p => ({ ...p, filingAmount: e.target.value }))} placeholder="0.00" style={inp} />
              </div>
              <div>
                <label style={lbl}>Notes</label>
                <textarea value={fileForm.notes} onChange={e => setFileForm(p => ({ ...p, notes: e.target.value }))} rows={2} style={{ ...inp, resize: "vertical" as const }} />
              </div>
            </div>
            {error && <div style={{ marginTop: 12, padding: "8px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>{error}</div>}
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => setFiling(null)} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer" }}>Cancel</button>
              <button disabled={!fileForm.filedDate || fileMutation.isPending}
                onClick={() => fileMutation.mutate({ clientId: filing.clientId, deadlineId: filing.id, body: { filedDate: fileForm.filedDate, sarsReference: fileForm.sarsReference || null, filingAmount: fileForm.filingAmount ? parseFloat(fileForm.filingAmount) : null, notes: fileForm.notes || null } })}
                style={{ padding: "9px 22px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {fileMutation.isPending ? "Saving..." : "Record Filing"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
