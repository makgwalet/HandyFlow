// src/pages/accountant/TimeTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Clock, Plus, X, TrendingUp, Pencil, Trash2 } from "lucide-react"

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return Array.isArray(p) ? p : p?.content ?? [] }
const fmtR   = (n: any) => `R ${Number(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`
const fmtD   = (d: any) => d ? new Date(d).toLocaleDateString("en-ZA") : "—"
const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, outline: "none" }
const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }

const ACTIVITIES = ["AUDIT","BOOKKEEPING","TAX","SECRETARIAL","ADVISORY","TRAINING","ADMIN","OTHER"]

export default function TimeTab() {
  const qc = useQueryClient()
  const [showLog, setShowLog]   = useState(false)
  const [selClient, setSelClient] = useState<string>("ALL")
  const [error, setError] = useState("")
  // NEW: closes the accountant module audit's "staff-level time
  // report" gap.
  const [view, setView] = useState<"my-time" | "staff-report">("my-time")
  const now = new Date()
  const monthStart = new Date(now.getFullYear(), now.getMonth(), 1).toISOString().split("T")[0]
  const todayStr = now.toISOString().split("T")[0]
  const [reportFrom, setReportFrom] = useState(monthStart)
  const [reportTo, setReportTo] = useState(todayStr)

  const { data: staffSummary = [], isLoading: staffLoading } = useQuery<any[]>({
    queryKey: ["acc-staff-time-summary", reportFrom, reportTo],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/accountant/time/staff-summary", {
      params: { from: reportFrom, to: reportTo },
    })),
    enabled: view === "staff-report",
  })

  const INIT = () => ({
    clientId: "", entryDate: new Date().toISOString().split("T")[0],
    activityType: "BOOKKEEPING", description: "",
    hours: "", hourlyRate: "", billable: true,
  })
  const [form, setForm] = useState(INIT())
  const f = (k: string, v: any) => setForm(p => ({ ...p, [k]: v }))

  const { data: clients = [] } = useQuery<any[]>({
    queryKey: ["acc-clients"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/accountant/clients?size=200")),
  })

  const { data: allUnbilled = [] } = useQuery<any[]>({
    queryKey: ["acc-unbilled"],
    queryFn: async () => {
      if (selClient && selClient !== "ALL") {
        return unwrap(await apiClient.get(`/api/v1/accountant/clients/${selClient}/time/unbilled`))
      }
      // fetch all clients and aggregate
      const cs = await apiClient.get("/api/v1/accountant/clients?size=200")
      const list: any[] = unwrap(cs)
      const results = await Promise.all(list.map(async c => {
        try { return unwrap(await apiClient.get(`/api/v1/accountant/clients/${c.id}/time/unbilled`)) }
        catch { return [] }
      }))
      return results.flat()
    },
    refetchOnWindowFocus: false,
  })

  const logTime = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/accountant/time", body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["acc-unbilled"] })
      qc.invalidateQueries({ queryKey: ["accountant-dashboard"] })
      setShowLog(false); setForm(INIT()); setError("")
    },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to log time"),
  })

  // NEW: closes the accountant module audit's "time entry edit/delete"
  // gap — a wrong hour/rate entry previously couldn't be corrected once
  // logged. Every entry visible in this tab is already unbilled by
  // definition (this view is sourced from getUnbilledTime()), so no
  // status check is needed client-side — the backend still guards
  // against the race where an entry gets billed between page load and
  // the edit/delete click.
  const [editingEntry, setEditingEntry] = useState<any>(null)
  const [editForm, setEditForm] = useState(INIT())
  const [editError, setEditError] = useState("")
  // NEW: replaces window.confirm() with a proper styled modal matching
  // the rest of the app, instead of the plain browser-native dialog.
  const [deletingEntry, setDeletingEntry] = useState<any>(null)

  const updateTime = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) => apiClient.put(`/api/v1/accountant/time/${id}`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["acc-unbilled"] })
      qc.invalidateQueries({ queryKey: ["accountant-dashboard"] })
      setEditingEntry(null); setEditError("")
    },
    onError: (e: any) => setEditError(e.response?.data?.message ?? "Failed to update time entry"),
  })

  const deleteTime = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/accountant/time/${id}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["acc-unbilled"] })
      qc.invalidateQueries({ queryKey: ["accountant-dashboard"] })
      setDeletingEntry(null)
    },
    onError: (e: any) => setDeleteError(e.response?.data?.message ?? "Failed to delete time entry"),
  })
  const [deleteError, setDeleteError] = useState("")

  const entries  = allUnbilled as any[]
  const totalWip = entries.reduce((sum: number, e: any) => sum + (parseFloat(e.lineTotal ?? 0)), 0)
  const totalHrs = entries.reduce((sum: number, e: any) => sum + (parseFloat(e.hours ?? 0)), 0)

  const filteredEntries = selClient === "ALL" ? entries
    : entries.filter((e: any) => e.clientId === selClient)

  // Group by client for display
  const clientMap = (clients as any[]).reduce((m: any, c: any) => { m[c.id] = c.tradingName; return m }, {})

  const grouped = filteredEntries.reduce((acc: any, e: any) => {
    const name = clientMap[e.clientId] ?? e.clientId
    if (!acc[name]) acc[name] = []
    acc[name].push(e)
    return acc
  }, {})

  return (
    <div>
      {/* NEW: closes the "staff-level time report" gap. */}
      <div style={{ display: "flex", gap: 2, marginBottom: 18, border: "1px solid #E2E8F0", borderRadius: 10, width: "fit-content", overflow: "hidden" }}>
        <button onClick={() => setView("my-time")}
          style={{ padding: "8px 16px", border: "none", background: view === "my-time" ? "#1B3A6B" : "#fff", color: view === "my-time" ? "#fff" : "#64748B", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          Time Entries
        </button>
        <button onClick={() => setView("staff-report")}
          style={{ padding: "8px 16px", border: "none", background: view === "staff-report" ? "#1B3A6B" : "#fff", color: view === "staff-report" ? "#fff" : "#64748B", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          Staff Report
        </button>
      </div>

      {view === "my-time" && (
      <>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18, flexWrap: "wrap", gap: 10 }}>
        <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
          <div style={{ background: "#F0FDF9", border: "1px solid #99F6E4", borderRadius: 9, padding: "10px 16px" }}>
            <div style={{ fontSize: 20, fontWeight: 800, color: "#0D9488" }}>{fmtR(totalWip)}</div>
            <div style={{ fontSize: 11, color: "#0D9488" }}>Total unbilled WIP</div>
          </div>
          <div style={{ background: "#EFF6FF", border: "1px solid #BFDBFE", borderRadius: 9, padding: "10px 16px" }}>
            <div style={{ fontSize: 20, fontWeight: 800, color: "#1D4ED8" }}>{totalHrs.toFixed(2)}h</div>
            <div style={{ fontSize: 11, color: "#1D4ED8" }}>Unbilled hours</div>
          </div>
        </div>
        <div style={{ display: "flex", gap: 10 }}>
          <select value={selClient} onChange={e => setSelClient(e.target.value)}
            style={{ padding: "7px 10px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, outline: "none", background: "#fff" }}>
            <option value="ALL">All clients</option>
            {(clients as any[]).map((c: any) => <option key={c.id} value={c.id}>{c.tradingName}</option>)}
          </select>
          <button onClick={() => { setShowLog(true); setError("") }}
            style={{ display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
            <Plus size={15} /> Log Time
          </button>
        </div>
      </div>

      {filteredEntries.length === 0 ? (
        <div style={{ textAlign: "center", padding: "50px 20px", color: "#94A3B8" }}>
          <Clock size={40} style={{ marginBottom: 12, opacity: 0.3 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No unbilled time</div>
          <div style={{ fontSize: 13, marginTop: 4 }}>Log time to track work-in-progress.</div>
        </div>
      ) : (
        <div>
          {Object.entries(grouped).map(([clientName, entries]: any) => {
            const clientTotal = entries.reduce((s: number, e: any) => s + parseFloat(e.lineTotal ?? 0), 0)
            const clientHours = entries.reduce((s: number, e: any) => s + parseFloat(e.hours ?? 0), 0)
            return (
              <div key={clientName} style={{ marginBottom: 22 }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 9, padding: "8px 12px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 8 }}>
                  <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{clientName}</div>
                  <div style={{ display: "flex", gap: 16 }}>
                    <span style={{ fontSize: 13, color: "#64748B" }}>{clientHours.toFixed(2)}h</span>
                    <span style={{ fontSize: 13, fontWeight: 700, color: "#0D9488" }}>{fmtR(clientTotal)}</span>
                  </div>
                </div>
                <div style={{ display: "flex", flexDirection: "column", gap: 5 }}>
                  {entries.sort((a: any, b: any) => new Date(b.entryDate).getTime() - new Date(a.entryDate).getTime())
                    .map((e: any) => (
                      <div key={e.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "9px 14px", border: "1px solid #E2E8F0", borderRadius: 7, background: "#fff" }}>
                        <div style={{ flex: 1 }}>
                          <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 2 }}>
                            <span style={{ background: "#EFF6FF", color: "#1D4ED8", padding: "1px 7px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{e.activityType.replace("_"," ")}</span>
                            {!e.billable && <span style={{ background: "#F1F5F9", color: "#64748B", padding: "1px 7px", borderRadius: 20, fontSize: 10 }}>Non-billable</span>}
                            <span style={{ fontSize: 12, color: "#64748B" }}>{fmtD(e.entryDate)}</span>
                          </div>
                          {e.description && <div style={{ fontSize: 12, color: "#64748B", paddingLeft: 2 }}>{e.description}</div>}
                        </div>
                        <div style={{ textAlign: "right" as const, flexShrink: 0, marginLeft: 12 }}>
                          <div style={{ fontWeight: 700, fontSize: 13, color: "#0F172A" }}>{fmtR(e.lineTotal)}</div>
                          <div style={{ fontSize: 11, color: "#94A3B8" }}>{e.hours}h × {fmtR(e.hourlyRate)}</div>
                        </div>
                        {/* NEW: closes the audit's "time entry
                            edit/delete" gap. */}
                        <div style={{ display: "flex", gap: 4, marginLeft: 10, flexShrink: 0 }}>
                          <button onClick={() => {
                            setEditingEntry(e)
                            setEditForm({
                              clientId: e.clientId, entryDate: e.entryDate, activityType: e.activityType,
                              description: e.description ?? "", hours: String(e.hours),
                              hourlyRate: String(e.hourlyRate), billable: e.billable,
                            })
                            setEditError("")
                          }} style={{ background: "none", border: "none", cursor: "pointer", color: "#64748B", padding: 4, display: "flex" }}>
                            <Pencil size={13} />
                          </button>
                          <button onClick={() => { setDeletingEntry(e); setDeleteError("") }}
                            style={{ background: "none", border: "none", cursor: "pointer", color: "#DC2626", padding: 4, display: "flex" }}>
                            <Trash2 size={13} />
                          </button>
                        </div>
                      </div>
                    ))}
                </div>
              </div>
            )
          })}
        </div>
      )}
      </>
      )}

      {view === "staff-report" && (
        <div>
          <div style={{ display: "flex", gap: 10, alignItems: "flex-end", marginBottom: 20, flexWrap: "wrap" }}>
            <div>
              <label style={lbl}>From</label>
              <input type="date" value={reportFrom} onChange={e => setReportFrom(e.target.value)} style={inp} />
            </div>
            <div>
              <label style={lbl}>To</label>
              <input type="date" value={reportTo} onChange={e => setReportTo(e.target.value)} style={inp} />
            </div>
          </div>

          {staffLoading ? (
            <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading...</div>
          ) : staffSummary.length === 0 ? (
            <div style={{ textAlign: "center", padding: "50px 20px", color: "#94A3B8" }}>
              <TrendingUp size={40} style={{ marginBottom: 12, opacity: 0.3 }} />
              <div>No time logged in this period.</div>
            </div>
          ) : (
            <div style={{ border: "1px solid #E2E8F0", borderRadius: 10, overflow: "hidden" }}>
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead>
                  <tr style={{ background: "#F8FAFC" }}>
                    {["Staff Member", "Total Hours", "Billable Hours", "Amount Billed", "Entries"].map(h => (
                      <th key={h} style={{ padding: "10px 14px", textAlign: h === "Staff Member" ? "left" as const : "right" as const, fontSize: 10, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase" as const }}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {staffSummary.map((s: any) => (
                    <tr key={s.practitionerId ?? "unassigned"} style={{ borderTop: "1px solid #F1F5F9" }}>
                      <td style={{ padding: "10px 14px", fontSize: 13, fontWeight: 600, color: s.practitionerName === "Unassigned" ? "#94A3B8" : "#0F172A" }}>{s.practitionerName}</td>
                      <td style={{ padding: "10px 14px", fontSize: 13, textAlign: "right" as const }}>{Number(s.totalHours ?? 0).toFixed(2)}h</td>
                      <td style={{ padding: "10px 14px", fontSize: 13, textAlign: "right" as const, color: "#0D9488" }}>{Number(s.billableHours ?? 0).toFixed(2)}h</td>
                      <td style={{ padding: "10px 14px", fontSize: 13, fontWeight: 700, textAlign: "right" as const }}>{fmtR(s.totalBilled)}</td>
                      <td style={{ padding: "10px 14px", fontSize: 13, textAlign: "right" as const, color: "#94A3B8" }}>{s.entryCount}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Log time modal */}
      {showLog && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 520, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700 }}>Log Time</h3>
              <button onClick={() => setShowLog(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div style={{ gridColumn: "1/-1" }}>
                <label style={lbl}>Client *</label>
                <select value={form.clientId} onChange={e => { f("clientId", e.target.value); const c = (clients as any[]).find(x => x.id === e.target.value); if (c) f("hourlyRate", "750") }}
                  style={{ ...inp, background: "#fff" }}>
                  <option value="">Select client...</option>
                  {(clients as any[]).map((c: any) => <option key={c.id} value={c.id}>{c.tradingName}</option>)}
                </select>
              </div>
              <div>
                <label style={lbl}>Date *</label>
                <input type="date" value={form.entryDate} onChange={e => f("entryDate", e.target.value)} style={inp} />
              </div>
              <div>
                <label style={lbl}>Activity *</label>
                <select value={form.activityType} onChange={e => f("activityType", e.target.value)} style={{ ...inp, background: "#fff" }}>
                  {ACTIVITIES.map(a => <option key={a} value={a}>{a}</option>)}
                </select>
              </div>
              <div>
                <label style={lbl}>Hours *</label>
                <input type="number" min="0.25" max="24" step="0.25" value={form.hours} onChange={e => f("hours", e.target.value)} placeholder="1.50" style={inp} />
              </div>
              <div>
                <label style={lbl}>Hourly rate (R) *</label>
                <input type="number" value={form.hourlyRate} onChange={e => f("hourlyRate", e.target.value)} placeholder="750.00" style={inp} />
              </div>
              <div style={{ gridColumn: "1/-1" }}>
                <label style={lbl}>Description</label>
                <input value={form.description} onChange={e => f("description", e.target.value)} placeholder="Prepared monthly management accounts" style={inp} />
              </div>
              <div style={{ gridColumn: "1/-1", display: "flex", alignItems: "center", gap: 8 }}>
                <input type="checkbox" id="billable" checked={form.billable} onChange={e => f("billable", e.target.checked)} style={{ width: 16, height: 16 }} />
                <label htmlFor="billable" style={{ fontSize: 13, color: "#374151", cursor: "pointer" }}>Billable to client</label>
              </div>
            </div>
            {form.hours && form.hourlyRate && (
              <div style={{ marginTop: 14, padding: "10px 14px", background: "#F0FDF9", border: "1px solid #99F6E4", borderRadius: 8, fontSize: 13, fontWeight: 700, color: "#0D9488" }}>
                Line total: {fmtR(parseFloat(form.hours) * parseFloat(form.hourlyRate))}
              </div>
            )}
            {error && <div style={{ marginTop: 12, padding: "8px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>{error}</div>}
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => setShowLog(false)} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer" }}>Cancel</button>
              <button disabled={!form.clientId || !form.hours || !form.hourlyRate || logTime.isPending}
                onClick={() => logTime.mutate({ ...form, hours: parseFloat(form.hours), hourlyRate: parseFloat(form.hourlyRate), description: form.description || null })}
                style={{ padding: "9px 22px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {logTime.isPending ? "Saving..." : "Log Time"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* NEW: Edit time entry modal — closes the audit's "time entry
          edit/delete" gap. No client selector — reassigning the client
          on an existing entry is a bigger operation than a correction. */}
      {editingEntry && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 520, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700 }}>Edit Time Entry</h3>
              <button onClick={() => setEditingEntry(null)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label style={lbl}>Date *</label>
                <input type="date" value={editForm.entryDate} onChange={e => setEditForm(p => ({ ...p, entryDate: e.target.value }))} style={inp} />
              </div>
              <div>
                <label style={lbl}>Activity *</label>
                <select value={editForm.activityType} onChange={e => setEditForm(p => ({ ...p, activityType: e.target.value }))} style={{ ...inp, background: "#fff" }}>
                  {ACTIVITIES.map(a => <option key={a} value={a}>{a}</option>)}
                </select>
              </div>
              <div>
                <label style={lbl}>Hours *</label>
                <input type="number" min="0.25" max="24" step="0.25" value={editForm.hours} onChange={e => setEditForm(p => ({ ...p, hours: e.target.value }))} style={inp} />
              </div>
              <div>
                <label style={lbl}>Hourly rate (R) *</label>
                <input type="number" value={editForm.hourlyRate} onChange={e => setEditForm(p => ({ ...p, hourlyRate: e.target.value }))} style={inp} />
              </div>
              <div style={{ gridColumn: "1/-1" }}>
                <label style={lbl}>Description</label>
                <input value={editForm.description} onChange={e => setEditForm(p => ({ ...p, description: e.target.value }))} style={inp} />
              </div>
              <div style={{ gridColumn: "1/-1", display: "flex", alignItems: "center", gap: 8 }}>
                <input type="checkbox" id="edit-billable" checked={editForm.billable} onChange={e => setEditForm(p => ({ ...p, billable: e.target.checked }))} style={{ width: 16, height: 16 }} />
                <label htmlFor="edit-billable" style={{ fontSize: 13, color: "#374151", cursor: "pointer" }}>Billable to client</label>
              </div>
            </div>
            {editForm.hours && editForm.hourlyRate && (
              <div style={{ marginTop: 14, padding: "10px 14px", background: "#F0FDF9", border: "1px solid #99F6E4", borderRadius: 8, fontSize: 13, fontWeight: 700, color: "#0D9488" }}>
                Line total: {fmtR(parseFloat(editForm.hours) * parseFloat(editForm.hourlyRate))}
              </div>
            )}
            {editError && <div style={{ marginTop: 12, padding: "8px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>{editError}</div>}
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => setEditingEntry(null)} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer" }}>Cancel</button>
              <button disabled={!editForm.hours || !editForm.hourlyRate || updateTime.isPending}
                onClick={() => updateTime.mutate({
                  id: editingEntry.id,
                  body: { ...editForm, hours: parseFloat(editForm.hours), hourlyRate: parseFloat(editForm.hourlyRate), description: editForm.description || null }
                })}
                style={{ padding: "9px 22px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {updateTime.isPending ? "Saving..." : "Save Changes"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* NEW: replaces window.confirm() — a proper styled modal
          matching the rest of the app, showing the entry's own details
          so the confirmation is specific, not a blind "are you sure". */}
      {deletingEntry && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 420, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <h3 style={{ margin: "0 0 6px", fontSize: 17, fontWeight: 700 }}>Delete Time Entry</h3>
            <p style={{ margin: "0 0 18px", fontSize: 13, color: "#64748B" }}>This can't be undone.</p>
            <div style={{ padding: "12px 14px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 9, marginBottom: 20 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 4 }}>
                <span style={{ background: "#EFF6FF", color: "#1D4ED8", padding: "1px 7px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{deletingEntry.activityType?.replace("_", " ")}</span>
                <span style={{ fontSize: 12, color: "#64748B" }}>{fmtD(deletingEntry.entryDate)}</span>
              </div>
              {deletingEntry.description && <div style={{ fontSize: 12, color: "#64748B", marginBottom: 4 }}>{deletingEntry.description}</div>}
              <div style={{ fontSize: 13, fontWeight: 700, color: "#0F172A" }}>{fmtR(deletingEntry.lineTotal)} <span style={{ fontWeight: 400, color: "#94A3B8", fontSize: 11 }}>({deletingEntry.hours}h × {fmtR(deletingEntry.hourlyRate)})</span></div>
            </div>
            {deleteError && <div style={{ marginBottom: 14, padding: "8px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>{deleteError}</div>}
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
              <button onClick={() => setDeletingEntry(null)} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer" }}>Cancel</button>
              <button disabled={deleteTime.isPending}
                onClick={() => deleteTime.mutate(deletingEntry.id)}
                style={{ padding: "9px 22px", background: "#DC2626", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {deleteTime.isPending ? "Deleting..." : "Delete Entry"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
