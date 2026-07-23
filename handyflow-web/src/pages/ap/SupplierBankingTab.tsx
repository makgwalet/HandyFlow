// src/pages/ap/SupplierBankingTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, X, Landmark, Edit3, Trash2, AlertTriangle } from "lucide-react"

interface Banking {
  id: string; supplierName: string; bankName: string | null
  accountHolder: string | null; accountNumber: string; branchCode: string
  vatNumber: string | null; notes: string | null; createdAt: string
}

const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, background: "#fff", outline: "none" }
const lbl: React.CSSProperties = { display: "block", fontSize: 11, fontWeight: 700, color: "#6B7280", textTransform: "uppercase", letterSpacing: "0.06em", marginBottom: 6 }
const btnP: React.CSSProperties = { display: "inline-flex", alignItems: "center", gap: 6, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 16px", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const btnS: React.CSSProperties = { display: "inline-flex", alignItems: "center", gap: 6, padding: "9px 14px", border: "1.5px solid #E2E8F0", borderRadius: 8, background: "#fff", fontSize: 13, cursor: "pointer", color: "#374151", fontWeight: 500 }

export function SupplierBankingTab() {
  const qc = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<Banking | null>(null)
  const [error, setError] = useState("")

  const initForm = () => ({ supplierName: "", bankName: "", accountHolder: "", accountNumber: "", branchCode: "", vatNumber: "", notes: "" })
  const [form, setForm] = useState(initForm())
  const f = (k: string, v: string) => setForm(p => ({ ...p, [k]: v }))

  const invalidate = () => qc.invalidateQueries({ queryKey: ["ap-supplier-banking"] })

  const { data: entries = [], isLoading } = useQuery<Banking[]>({
    queryKey: ["ap-supplier-banking"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/ap/suppliers/banking")
      return r.data?.data ?? r.data
    },
  })

  const { data: knownNames = [] } = useQuery<string[]>({
    queryKey: ["ap-known-supplier-names"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/ap/suppliers/known-names")
      return r.data?.data ?? r.data
    },
  })

  const create = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/ap/suppliers/banking", body),
    onSuccess: () => { invalidate(); setShowForm(false); setForm(initForm()); setError("") },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to save"),
  })

  const update = useMutation({
    mutationFn: (body: any) => apiClient.put(`/api/v1/ap/suppliers/banking/${editing?.id}`, body),
    onSuccess: () => { invalidate(); setShowForm(false); setEditing(null); setForm(initForm()); setError("") },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to save"),
  })

  const remove = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/ap/suppliers/banking/${id}`),
    onSuccess: invalidate,
  })

  const startEdit = (b: Banking) => {
    setEditing(b)
    setForm({ supplierName: b.supplierName, bankName: b.bankName ?? "", accountHolder: b.accountHolder ?? "",
      accountNumber: b.accountNumber, branchCode: b.branchCode, vatNumber: b.vatNumber ?? "", notes: b.notes ?? "" })
    setShowForm(true); setError("")
  }

  const configuredNames = new Set(entries.map(e => e.supplierName.toLowerCase()))
  const unconfiguredNames = knownNames.filter(n => !configuredNames.has(n.toLowerCase()))

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <div>
          <h2 style={{ fontSize: 16, fontWeight: 700, color: "#0F172A", margin: 0 }}>Supplier Banking Details</h2>
          <p style={{ fontSize: 12, color: "#94A3B8", margin: "3px 0 0" }}>
            Matched to bills by supplier name — used to fill in account number and branch code when exporting an EFT batch CSV
          </p>
        </div>
        <button onClick={() => { setEditing(null); setForm(initForm()); setShowForm(true); setError("") }} style={btnP}><Plus size={14} /> Add Supplier</button>
      </div>

      {unconfiguredNames.length > 0 && (
        <div style={{ padding: "12px 16px", background: "#FFFBEB", border: "1.5px solid #FDE68A", borderRadius: 10, marginBottom: 16, display: "flex", gap: 10, alignItems: "flex-start" }}>
          <AlertTriangle size={15} color="#D97706" style={{ flexShrink: 0, marginTop: 1 }} />
          <div style={{ fontSize: 12, color: "#92400E" }}>
            <strong>{unconfiguredNames.length} supplier{unconfiguredNames.length === 1 ? "" : "s"} with bills but no banking details:</strong>{" "}
            {unconfiguredNames.slice(0, 6).join(", ")}{unconfiguredNames.length > 6 ? `, +${unconfiguredNames.length - 6} more` : ""}.
            {" "}Their EFT batch CSV rows will export with blank account/branch columns until added here.
          </div>
        </div>
      )}

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 48, color: "#94A3B8" }}>Loading...</div>
      ) : entries.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px" }}>
          <Landmark size={40} style={{ marginBottom: 12, color: "#CBD5E1" }} />
          <div style={{ fontWeight: 700, color: "#475569", fontSize: 15, marginBottom: 6 }}>No supplier banking details yet</div>
          <div style={{ fontSize: 13, color: "#94A3B8", marginBottom: 16 }}>Add account details for suppliers you pay via EFT batch.</div>
          <button onClick={() => setShowForm(true)} style={{ ...btnP, margin: "0 auto" }}><Plus size={14} /> Add first supplier</button>
        </div>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" as const, fontSize: 13 }}>
            <thead>
              <tr style={{ background: "#F8FAFC", borderBottom: "1px solid #E2E8F0" }}>
                {["Supplier", "Bank", "Account Number", "Branch Code", "VAT Number", ""].map(h => (
                  <th key={h} style={{ padding: "10px 16px", textAlign: "left" as const, fontSize: 11, fontWeight: 700, color: "#64748B", letterSpacing: "0.05em" }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {entries.map((b, i) => (
                <tr key={b.id} style={{ background: i % 2 === 0 ? "#fff" : "#FAFAFA" }}>
                  <td style={{ padding: "12px 16px" }}>
                    <div style={{ fontWeight: 700, color: "#0F172A" }}>{b.supplierName}</div>
                    {b.accountHolder && <div style={{ fontSize: 11, color: "#94A3B8" }}>{b.accountHolder}</div>}
                  </td>
                  <td style={{ padding: "12px 16px", fontSize: 12, color: "#64748B" }}>{b.bankName || "—"}</td>
                  <td style={{ padding: "12px 16px", fontFamily: "monospace", fontSize: 12, color: "#374151" }}>{b.accountNumber}</td>
                  <td style={{ padding: "12px 16px", fontFamily: "monospace", fontSize: 12, color: "#374151" }}>{b.branchCode}</td>
                  <td style={{ padding: "12px 16px", fontSize: 12, color: "#64748B" }}>{b.vatNumber || "—"}</td>
                  <td style={{ padding: "12px 16px" }}>
                    <div style={{ display: "flex", gap: 6 }}>
                      <button onClick={() => startEdit(b)} title="Edit" style={{ padding: "5px 8px", background: "#F8FAFC", color: "#64748B", border: "1px solid #E2E8F0", borderRadius: 6, cursor: "pointer", display: "flex" }}><Edit3 size={11} /></button>
                      <button onClick={() => remove.mutate(b.id)} title="Remove" style={{ padding: "5px 8px", background: "#FEF2F2", color: "#DC2626", border: "1px solid #FECACA", borderRadius: 6, cursor: "pointer", display: "flex" }}><Trash2 size={11} /></button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {showForm && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.55)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1100, padding: 20, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 560, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 25px 80px rgba(0,0,0,0.25)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 800 }}>{editing ? "Edit Supplier" : "Add Supplier Banking"}</h3>
              <button onClick={() => { setShowForm(false); setEditing(null); setError("") }} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div style={{ gridColumn: "1/-1" }}>
                <label style={lbl}>Supplier name *</label>
                <input autoFocus={!editing} disabled={!!editing} value={form.supplierName}
                  onChange={e => f("supplierName", e.target.value)}
                  placeholder="Must match the name used on bills exactly"
                  list="known-supplier-names" style={{ ...inp, opacity: editing ? 0.6 : 1 }} />
                <datalist id="known-supplier-names">
                  {knownNames.map(n => <option key={n} value={n} />)}
                </datalist>
              </div>
              <div>
                <label style={lbl}>Bank name</label>
                <input value={form.bankName} onChange={e => f("bankName", e.target.value)} placeholder="FNB, Standard Bank..." style={inp} />
              </div>
              <div>
                <label style={lbl}>Account holder</label>
                <input value={form.accountHolder} onChange={e => f("accountHolder", e.target.value)} style={inp} />
              </div>
              <div>
                <label style={lbl}>Account number *</label>
                <input value={form.accountNumber} onChange={e => f("accountNumber", e.target.value)} style={inp} />
              </div>
              <div>
                <label style={lbl}>Branch code *</label>
                <input value={form.branchCode} onChange={e => f("branchCode", e.target.value)} style={inp} />
              </div>
              <div style={{ gridColumn: "1/-1" }}>
                <label style={lbl}>VAT number</label>
                <input value={form.vatNumber} onChange={e => f("vatNumber", e.target.value)} style={inp} />
              </div>
              <div style={{ gridColumn: "1/-1" }}>
                <label style={lbl}>Notes</label>
                <textarea value={form.notes} onChange={e => f("notes", e.target.value)} rows={2} style={{ ...inp, resize: "vertical" as const, fontFamily: "inherit" }} />
              </div>
            </div>
            {error && <div style={{ marginTop: 12, padding: "10px 14px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>{error}</div>}
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 22 }}>
              <button onClick={() => { setShowForm(false); setEditing(null); setError("") }} style={btnS}>Cancel</button>
              <button
                disabled={!form.supplierName || !form.accountNumber || !form.branchCode || create.isPending || update.isPending}
                onClick={() => {
                  const body = { supplierName: form.supplierName, bankName: form.bankName || null, accountHolder: form.accountHolder || null,
                    accountNumber: form.accountNumber, branchCode: form.branchCode, vatNumber: form.vatNumber || null, notes: form.notes || null }
                  editing ? update.mutate(body) : create.mutate(body)
                }}
                style={{ ...btnP, opacity: (!form.supplierName || !form.accountNumber || !form.branchCode) ? 0.5 : 1 }}>
                {(create.isPending || update.isPending) ? "Saving..." : editing ? "Save changes" : "Add supplier"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
