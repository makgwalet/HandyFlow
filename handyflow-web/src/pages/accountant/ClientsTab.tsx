// src/pages/accountant/ClientsTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, X, ChevronDown, ChevronUp, AlertTriangle, CheckCircle, Users, Search } from "lucide-react"

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const fmtD   = (d: any) => d ? new Date(d).toLocaleDateString("en-ZA") : "—"

const ENTITY_TYPES = ["PTY_LTD","CC","SOLE_PROP","TRUST","NPO","INDIVIDUAL","PARTNERSHIP","FOREIGN","ARTIST","TRADER","OTHER"]
const RISK_CFG: Record<string, { color: string; bg: string }> = {
  LOW:    { color: "#166534", bg: "#DCFCE7" },
  MEDIUM: { color: "#D97706", bg: "#FFFBEB" },
  HIGH:   { color: "#DC2626", bg: "#FEF2F2" },
}

const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, outline: "none", background: "#fff" }
const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }

export default function ClientsTab() {
  const qc = useQueryClient()
  const [search, setSearch]     = useState("")
  const [expanded, setExpanded] = useState<string | null>(null)
  const [showCreate, setCreate] = useState(false)
  const [error, setError]       = useState("")

  const INIT = () => ({
    entityType: "PTY_LTD", tradingName: "", registeredName: "", registrationNumber: "",
    taxReferenceNumber: "", vatNumber: "", vatCategory: "", yearEndMonth: 2,
    contactEmail: "", contactPhone: "",
  })
  const [form, setForm] = useState(INIT())
  const f = (k: string, v: any) => setForm(p => ({ ...p, [k]: v }))

  const { data: clients = [], isLoading } = useQuery<any[]>({
    queryKey: ["acc-clients"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/accountant/clients?size=200")),
  })

  const createClient = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/accountant/clients", body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["acc-clients"] }); setCreate(false); setForm(INIT()); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to create client"),
  })

  const markFica = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/accountant/clients/${id}/fica-complete`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["acc-clients"] }),
  })

  const markSars = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/accountant/clients/${id}/sars-agent`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["acc-clients"] }),
  })

  const generateDeadlines = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/accountant/clients/${id}/deadlines/generate`, { periodYear: new Date().getFullYear() }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["acc-clients"] }),
  })

  const filtered = (clients as any[]).filter(c =>
    !search || c.tradingName?.toLowerCase().includes(search.toLowerCase()) ||
    c.registrationNumber?.includes(search) || c.taxReferenceNumber?.includes(search))

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18, flexWrap: "wrap", gap: 10 }}>
        <div style={{ position: "relative" as const }}>
          <Search size={13} style={{ position: "absolute" as const, left: 9, top: "50%", transform: "translateY(-50%)", color: "#94A3B8" }} />
          <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search clients..."
            style={{ paddingLeft: 28, padding: "7px 10px 7px 28px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, outline: "none", width: 240 }} />
        </div>
        <button onClick={() => { setCreate(true); setError("") }}
          style={{ display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={15} /> Add Client
        </button>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading...</div>
      ) : filtered.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Users size={40} style={{ marginBottom: 12, opacity: 0.3 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No clients yet</div>
          <div style={{ fontSize: 13, marginTop: 4 }}>Add your first client to start managing their compliance.</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {filtered.map((c: any) => {
            const risk   = RISK_CFG[c.riskRating] ?? RISK_CFG.LOW
            const isOpen = expanded === c.id
            return (
              <div key={c.id} style={{ border: "1px solid #E2E8F0", borderLeft: `3px solid ${risk.color}`, borderRadius: 10, overflow: "hidden" }}>
                <div onClick={() => setExpanded(isOpen ? null : c.id)}
                  style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "13px 20px", cursor: "pointer", background: isOpen ? "#F8FAFC" : "#fff" }}>
                  <div style={{ flex: 1 }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 4, flexWrap: "wrap" }}>
                      <span style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{c.tradingName}</span>
                      <span style={{ background: "#F8FAFC", color: "#64748B", padding: "1px 7px", borderRadius: 20, fontSize: 11, border: "1px solid #E2E8F0" }}>{c.entityType.replace("_"," ")}</span>
                      <span style={{ background: risk.bg, color: risk.color, padding: "1px 7px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{c.riskRating}</span>
                      {!c.ficaCompleted && <span style={{ background: "#FFFBEB", color: "#D97706", padding: "1px 7px", borderRadius: 20, fontSize: 10, fontWeight: 700, border: "1px solid #FDE68A" }}>FICA pending</span>}
                      {c.overdueDeadlines > 0 && <span style={{ background: "#FEF2F2", color: "#DC2626", padding: "1px 7px", borderRadius: 20, fontSize: 10, fontWeight: 700, border: "1px solid #FECACA" }}>{c.overdueDeadlines} overdue</span>}
                    </div>
                    <div style={{ fontSize: 12, color: "#64748B", display: "flex", gap: 12, flexWrap: "wrap" }}>
                      {c.registrationNumber && <span>{c.registrationNumber}</span>}
                      {c.taxReferenceNumber && <span>TRN: {c.taxReferenceNumber}</span>}
                      {c.vatNumber && <span>VAT: {c.vatNumber}</span>}
                      {c.yearEndMonth && <span>YE: {new Date(0, c.yearEndMonth - 1).toLocaleString("en", { month: "short" })}</span>}
                    </div>
                  </div>
                  <div style={{ display: "flex", alignItems: "center", gap: 8, flexShrink: 0 }}>
                    {c.wip > 0 && <span style={{ fontSize: 12, fontWeight: 700, color: "#0D9488" }}>WIP R{Number(c.wip).toLocaleString("en-ZA", { maximumFractionDigits: 0 })}</span>}
                    {isOpen ? <ChevronUp size={16} color="#94A3B8" /> : <ChevronDown size={16} color="#94A3B8" />}
                  </div>
                </div>

                {isOpen && (
                  <div style={{ borderTop: "1px solid #E2E8F0", padding: "16px 20px", background: "#FAFAFA" }}>
                    {/* Compliance status */}
                    <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 10, marginBottom: 16 }}>
                      {[
                        { l: "Onboarding",     v: c.onboardingStatus },
                        { l: "FICA",           v: c.ficaCompleted ? "Complete" : "Pending",   ok: c.ficaCompleted },
                        { l: "SARS Agent",     v: c.sarsAgentAppointed ? "Appointed" : "Pending", ok: c.sarsAgentAppointed },
                        { l: "TCS PIN",        v: c.tcsPin ?? "Not on file",                  ok: !!c.tcsPin },
                        { l: "Contact email",  v: c.contactEmail ?? "—" },
                        { l: "Contact phone",  v: c.contactPhone ?? "—" },
                        { l: "Open deadlines", v: c.openDeadlines },
                        { l: "Overdue",        v: c.overdueDeadlines, color: c.overdueDeadlines > 0 ? "#DC2626" : undefined },
                      ].map((item: any) => (
                        <div key={item.l} style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 7, padding: "8px 12px" }}>
                          <div style={{ fontSize: 10, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase" as const, marginBottom: 2 }}>{item.l}</div>
                          <div style={{ fontSize: 13, fontWeight: 600, color: item.color ?? (item.ok === false ? "#DC2626" : item.ok === true ? "#166534" : "#0F172A") }}>
                            {item.v}
                          </div>
                        </div>
                      ))}
                    </div>

                    <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                      {!c.ficaCompleted && (
                        <button onClick={() => markFica.mutate(c.id)}
                          style={{ padding: "6px 12px", background: "#FFFBEB", color: "#D97706", border: "1px solid #FDE68A", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                          Mark FICA complete
                        </button>
                      )}
                      {!c.sarsAgentAppointed && (
                        <button onClick={() => markSars.mutate(c.id)}
                          style={{ padding: "6px 12px", background: "#EFF6FF", color: "#1D4ED8", border: "1px solid #BFDBFE", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                          Mark SARS agent appointed
                        </button>
                      )}
                      <button onClick={() => generateDeadlines.mutate(c.id)}
                        style={{ padding: "6px 12px", background: "#F0FDF4", color: "#166534", border: "1px solid #86EFAC", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                        Generate {new Date().getFullYear()} deadlines
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {/* Create client modal */}
      {showCreate && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 680, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Add Client</h3>
              <button onClick={() => setCreate(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label style={lbl}>Entity type *</label>
                <select value={form.entityType} onChange={e => f("entityType", e.target.value)} style={{ ...inp, background: "#fff" }}>
                  {ENTITY_TYPES.map(t => <option key={t} value={t}>{t.replace("_"," ")}</option>)}
                </select>
              </div>
              <div>
                <label style={lbl}>Year-end month *</label>
                <select value={form.yearEndMonth} onChange={e => f("yearEndMonth", parseInt(e.target.value))} style={{ ...inp, background: "#fff" }}>
                  {Array.from({ length: 12 }, (_, i) => (
                    <option key={i+1} value={i+1}>{new Date(0, i).toLocaleString("en", { month: "long" })}</option>
                  ))}
                </select>
              </div>
              <div style={{ gridColumn: "1/-1" }}>
                <label style={lbl}>Trading name *</label>
                <input autoFocus value={form.tradingName} onChange={e => f("tradingName", e.target.value)} placeholder="Acme Trading (Pty) Ltd" style={inp} />
              </div>
              <div>
                <label style={lbl}>Registered name</label>
                <input value={form.registeredName} onChange={e => f("registeredName", e.target.value)} style={inp} />
              </div>
              <div>
                <label style={lbl}>CIPC registration number</label>
                <input value={form.registrationNumber} onChange={e => f("registrationNumber", e.target.value)} placeholder="2020/123456/07" style={inp} />
              </div>
              <div>
                <label style={lbl}>SARS tax reference number</label>
                <input value={form.taxReferenceNumber} onChange={e => f("taxReferenceNumber", e.target.value)} placeholder="1234567890" style={inp} />
              </div>
              <div>
                <label style={lbl}>VAT number</label>
                <input value={form.vatNumber} onChange={e => f("vatNumber", e.target.value)} placeholder="4123456789" style={inp} />
              </div>
              <div>
                <label style={lbl}>VAT category</label>
                <select value={form.vatCategory} onChange={e => f("vatCategory", e.target.value)} style={{ ...inp, background: "#fff" }}>
                  <option value="">Not VAT registered</option>
                  <option value="A">A — Bi-monthly (Feb/Apr/Jun/Aug/Oct/Dec)</option>
                  <option value="B">B — Bi-monthly (Jan/Mar/May/Jul/Sep/Nov)</option>
                  <option value="C">C — Monthly</option>
                  <option value="E">E — Annual</option>
                </select>
              </div>
              <div>
                <label style={lbl}>Contact email</label>
                <input type="email" value={form.contactEmail} onChange={e => f("contactEmail", e.target.value)} style={inp} />
              </div>
              <div>
                <label style={lbl}>Contact phone</label>
                <input value={form.contactPhone} onChange={e => f("contactPhone", e.target.value)} style={inp} />
              </div>
            </div>

            {error && <div style={{ marginTop: 14, padding: "10px 14px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>{error}</div>}
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => setCreate(false)} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button>
              <button disabled={!form.tradingName || createClient.isPending}
                onClick={() => createClient.mutate({ ...form, vatCategory: form.vatCategory || null, registeredName: form.registeredName || null, registrationNumber: form.registrationNumber || null, taxReferenceNumber: form.taxReferenceNumber || null, vatNumber: form.vatNumber || null, contactEmail: form.contactEmail || null, contactPhone: form.contactPhone || null })}
                style={{ padding: "9px 22px", background: !form.tradingName ? "#94A3B8" : "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {createClient.isPending ? "Adding..." : "Add Client"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
