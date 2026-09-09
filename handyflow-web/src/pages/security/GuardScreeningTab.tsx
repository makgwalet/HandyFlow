// src/pages/security/GuardScreeningTab.tsx
//
// FIX (P1 backlog): confirmed via direct source read before building —
// GuardScreeningController has a full screening workflow (create,
// record result, full history, a pre-shift gate check) with zero
// frontend surface. Small, per-guard-scoped controller (4 endpoints,
// SECURITY_MANAGE-gated at the class level).

import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { ShieldCheck, Plus, CheckCircle2, XCircle, HelpCircle, Clock3, AlertTriangle } from "lucide-react"

interface Guard { id: string; fullName: string }

interface ScreeningRecord {
  id: string; guardId: string
  screeningType: "POLYGRAPH" | "CRIMINAL_RECORD_CHECK" | "REFERENCE_CHECK" | "DRUG_TEST" | "PSYCHOMETRIC" | "CREDIT_CHECK" | "OTHER"
  reason: "ONBOARDING" | "PERIODIC" | "POST_INCIDENT" | "RANDOM" | "CLIENT_REQUESTED"
  result: "PASS" | "FAIL" | "INCONCLUSIVE" | "PENDING"
  conductedBy: string | null; conductedAt: string | null; nextDueAt: string | null
  reportRef: string | null; notes: string | null; createdAt: string
}

const RESULT_CFG: Record<string, { label: string; color: string; bg: string; icon: any }> = {
  PENDING:      { label: "Pending",      color: "#B45309", bg: "#FFFBEB", icon: Clock3 },
  PASS:         { label: "Pass",         color: "#166534", bg: "#DCFCE7", icon: CheckCircle2 },
  FAIL:         { label: "Fail",         color: "#DC2626", bg: "#FEF2F2", icon: XCircle },
  INCONCLUSIVE: { label: "Inconclusive", color: "#64748B", bg: "#F1F5F9", icon: HelpCircle },
}

const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }
const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, background: "#fff", outline: "none" }
const cancelBtn: React.CSSProperties = { padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }
const submitBtn: React.CSSProperties = { padding: "9px 18px", border: "none", borderRadius: 9, background: "#1B3A6B", color: "#fff", fontSize: 14, fontWeight: 600, cursor: "pointer" }
const fmtDate = (s: string | null) => s ? new Date(s).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"

export default function GuardScreeningTab() {
  const qc = useQueryClient()
  const [guardId, setGuardId] = useState("")
  const [error, setError] = useState("")

  const { data: guards = [] } = useQuery<Guard[]>({
    queryKey: ["guards-list-for-screening"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/security/guards?size=200")
      const p = r.data?.data ?? r.data
      return (p?.content ?? p) as Guard[]
    },
  })

  const { data: history = [], isLoading } = useQuery<ScreeningRecord[]>({
    queryKey: ["guard-screening-history", guardId],
    queryFn: async () => (await apiClient.get(`/api/v1/security/guards/${guardId}/screening`)).data?.data ?? [],
    enabled: !!guardId,
  })
  const { data: gateWarning } = useQuery<string | null>({
    queryKey: ["guard-screening-gate", guardId],
    queryFn: async () => (await apiClient.get(`/api/v1/security/guards/${guardId}/screening/gate`)).data?.data ?? null,
    enabled: !!guardId,
  })

  const [showNew, setShowNew] = useState(false)
  const [newForm, setNewForm] = useState({ screeningType: "CRIMINAL_RECORD_CHECK", reason: "ONBOARDING" })
  const createScreening = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/security/guards/${guardId}/screening`, newForm),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["guard-screening-history", guardId] })
      qc.invalidateQueries({ queryKey: ["guard-screening-gate", guardId] })
      setShowNew(false); setError("")
    },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to create screening"),
  })

  const [resultFor, setResultFor] = useState<ScreeningRecord | null>(null)
  const [resultForm, setResultForm] = useState({ result: "PASS", conductedBy: "", conductedAt: "", nextDueAt: "", reportRef: "", notes: "" })
  const recordResult = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/security/guards/${guardId}/screening/${resultFor!.id}/result`, resultForm),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["guard-screening-history", guardId] })
      qc.invalidateQueries({ queryKey: ["guard-screening-gate", guardId] })
      setResultFor(null); setError("")
    },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to record result"),
  })

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 18, flexWrap: "wrap", gap: 12 }}>
        <div>
          <h2 style={{ margin: 0, fontSize: 20, fontWeight: 800, color: "#0F172A" }}>Guard Screening</h2>
          <div style={{ fontSize: 13, color: "#64748B", marginTop: 2 }}>Vetting history — polygraph, criminal record, references, and more</div>
        </div>
        <div style={{ minWidth: 220 }}>
          <select value={guardId} onChange={e => setGuardId(e.target.value)} style={inp}>
            <option value="">Select a guard…</option>
            {guards.map(g => <option key={g.id} value={g.id}>{g.fullName}</option>)}
          </select>
        </div>
      </div>

      {!guardId ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12 }}>
          <ShieldCheck size={32} style={{ marginBottom: 10, opacity: 0.3 }} />
          <div>Select a guard to view or manage their screening history.</div>
        </div>
      ) : (
        <div>
          {gateWarning && (
            <div style={{ display: "flex", alignItems: "center", gap: 8, padding: "10px 14px", background: "#FFFBEB", border: "1px solid #FDE68A", borderRadius: 8, color: "#B45309", fontSize: 13, marginBottom: 16 }}>
              <AlertTriangle size={15} /> {gateWarning}
            </div>
          )}
          {error && <div style={{ padding: "10px 14px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, color: "#DC2626", fontSize: 13, marginBottom: 14 }}>{error}</div>}

          <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 12 }}>
            <button onClick={() => { setNewForm({ screeningType: "CRIMINAL_RECORD_CHECK", reason: "ONBOARDING" }); setShowNew(true); setError("") }}
              style={{ display: "flex", alignItems: "center", gap: 6, padding: "8px 16px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
              <Plus size={14} /> New Screening
            </button>
          </div>

          {isLoading ? (
            <div style={{ textAlign: "center", padding: 30, color: "#94A3B8" }}>Loading…</div>
          ) : history.length === 0 ? (
            <div style={{ textAlign: "center", padding: "40px 20px", color: "#94A3B8", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12 }}>
              No screening records for this guard yet.
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {history.map(r => {
                const rc = RESULT_CFG[r.result] ?? RESULT_CFG.PENDING
                const Icon = rc.icon
                return (
                  <div key={r.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 10, padding: "12px 16px" }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                      <div style={{ width: 36, height: 36, borderRadius: 9, background: rc.bg, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                        <Icon size={16} color={rc.color} />
                      </div>
                      <div>
                        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                          <span style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{r.screeningType.replace(/_/g, " ")}</span>
                          <span style={{ fontSize: 10, fontWeight: 700, color: "#1B3A6B", background: "#EFF6FF", padding: "2px 8px", borderRadius: 4 }}>{r.reason.replace(/_/g, " ")}</span>
                        </div>
                        <div style={{ fontSize: 12, color: "#94A3B8" }}>
                          Created {fmtDate(r.createdAt)}
                          {r.conductedAt && <> · Conducted {fmtDate(r.conductedAt)}{r.conductedBy && ` by ${r.conductedBy}`}</>}
                          {r.nextDueAt && <> · Next due {fmtDate(r.nextDueAt)}</>}
                        </div>
                        {r.notes && <div style={{ fontSize: 12, color: "#94A3B8", marginTop: 2 }}>{r.notes}</div>}
                      </div>
                    </div>
                    <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                      <span style={{ fontSize: 11, fontWeight: 600, padding: "3px 10px", borderRadius: 20, background: rc.bg, color: rc.color }}>{rc.label}</span>
                      {r.result === "PENDING" && (
                        <button onClick={() => { setResultForm({ result: "PASS", conductedBy: "", conductedAt: new Date().toISOString().slice(0, 10), nextDueAt: "", reportRef: "", notes: "" }); setResultFor(r) }}
                          style={{ padding: "6px 12px", borderRadius: 7, border: "1px solid #1B3A6B", background: "#EFF6FF", color: "#1B3A6B", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                          Record Result
                        </button>
                      )}
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      )}

      {/* New screening modal */}
      {showNew && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 26, width: 420 }}>
            <h3 style={{ margin: "0 0 18px", fontSize: 16, fontWeight: 700, color: "#0F172A" }}>New Screening</h3>
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Type</label>
              <select value={newForm.screeningType} onChange={e => setNewForm(f => ({ ...f, screeningType: e.target.value }))} style={inp}>
                <option value="POLYGRAPH">Polygraph</option>
                <option value="CRIMINAL_RECORD_CHECK">Criminal Record Check</option>
                <option value="REFERENCE_CHECK">Reference Check</option>
                <option value="DRUG_TEST">Drug Test</option>
                <option value="PSYCHOMETRIC">Psychometric</option>
                <option value="CREDIT_CHECK">Credit Check</option>
                <option value="OTHER">Other</option>
              </select>
            </div>
            <div style={{ marginBottom: 18 }}>
              <label style={lbl}>Reason</label>
              <select value={newForm.reason} onChange={e => setNewForm(f => ({ ...f, reason: e.target.value }))} style={inp}>
                <option value="ONBOARDING">Onboarding</option>
                <option value="PERIODIC">Periodic</option>
                <option value="POST_INCIDENT">Post-Incident</option>
                <option value="RANDOM">Random</option>
                <option value="CLIENT_REQUESTED">Client Requested</option>
              </select>
            </div>
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
              <button onClick={() => setShowNew(false)} style={cancelBtn}>Cancel</button>
              <button onClick={() => createScreening.mutate()} disabled={createScreening.isPending} style={submitBtn}>
                {createScreening.isPending ? "Creating…" : "Create"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Record result modal */}
      {resultFor && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 26, width: 440 }}>
            <h3 style={{ margin: "0 0 18px", fontSize: 16, fontWeight: 700, color: "#0F172A" }}>Record Result — {resultFor.screeningType.replace(/_/g, " ")}</h3>
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Result</label>
              <select value={resultForm.result} onChange={e => setResultForm(f => ({ ...f, result: e.target.value }))} style={inp}>
                <option value="PASS">Pass</option>
                <option value="FAIL">Fail</option>
                <option value="INCONCLUSIVE">Inconclusive</option>
              </select>
            </div>
            <div style={{ display: "flex", gap: 12, marginBottom: 14 }}>
              <div style={{ flex: 1 }}>
                <label style={lbl}>Conducted By</label>
                <input value={resultForm.conductedBy} onChange={e => setResultForm(f => ({ ...f, conductedBy: e.target.value }))} placeholder="Agency name" style={inp} />
              </div>
              <div style={{ flex: 1 }}>
                <label style={lbl}>Conducted At</label>
                <input type="date" value={resultForm.conductedAt} onChange={e => setResultForm(f => ({ ...f, conductedAt: e.target.value }))} style={inp} />
              </div>
            </div>
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Next Due (for periodic re-screening)</label>
              <input type="date" value={resultForm.nextDueAt} onChange={e => setResultForm(f => ({ ...f, nextDueAt: e.target.value }))} style={inp} />
            </div>
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Report Reference</label>
              <input value={resultForm.reportRef} onChange={e => setResultForm(f => ({ ...f, reportRef: e.target.value }))} placeholder="Pointer to encrypted document store — never the report itself" style={inp} />
            </div>
            <div style={{ marginBottom: 18 }}>
              <label style={lbl}>Notes</label>
              <textarea value={resultForm.notes} onChange={e => setResultForm(f => ({ ...f, notes: e.target.value }))} rows={2} style={{ ...inp, fontFamily: "inherit", resize: "vertical" as const }} />
            </div>
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
              <button onClick={() => setResultFor(null)} style={cancelBtn}>Cancel</button>
              <button onClick={() => recordResult.mutate()} disabled={recordResult.isPending} style={submitBtn}>
                {recordResult.isPending ? "Saving…" : "Save Result"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
