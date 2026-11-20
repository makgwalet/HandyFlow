// src/pages/legalcompliance/RiskRegisterTab.tsx
//
// Enterprise risk register. Endpoints (RiskController, /api/v1/legalcompliance/risks):
//   GET / · GET /summary · GET /heatmap?basis= · GET /{id} · GET|PUT /settings
//   POST / · PUT /{id} · POST /{id}/assess · /treatment · /accept · /close · /reopen
//
// Assessments are append-only: re-assessing never overwrites, it adds a row and the old one stays in the history.
// A risk whose residual score is above tolerance cannot be accepted — the server refuses it; the button is hidden here as a courtesy.
// Residual risk is the assessor's judgement; it is not (yet) checked against control test results.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { input, primaryBtn, ghostBtn, unwrap, one, fmtDate, fmtDateTime, errMsg } from "./lcHelpers"
import { Modal, Field, Err, Badge, RowLimitNotice } from "./LcUi"
import GrcLinksPanel from "./GrcLinksPanel"
import { ShieldAlert, ChevronDown, ChevronUp, Plus, Settings } from "lucide-react"

interface Assessment {
  id: string; inherentLikelihood: number; inherentImpact: number; residualLikelihood: number; residualImpact: number; inherentScore: number
  residualScore: number; inherentRating: string; residualRating: string; appetiteMax: number; toleranceMax: number; residualPosition: string
  rationale: string; assessedByName: string | null; assessedAt: string; current: boolean
}
interface Risk {
  id: string; reference: string; title: string; description: string | null; category: string; status: string; emerging: boolean; ownerName: string
  nextReviewDate: string; reviewOverdue: boolean; treatmentStrategy: string | null; treatmentPlan: string | null; treatmentDue: string | null
  acceptedByName: string | null; acceptedAt: string | null; acceptanceReason: string | null; acceptanceExpires: string | null; closeReason: string | null
  assessment: Assessment | null; controlCount: number; obligationCount: number
}
interface Detail { risk: Risk; history: Assessment[] }
interface Summary { open: number; critical: number; high: number; aboveAppetite: number; outsideTolerance: number; overdueReview: number; accepted: number; unassessed: number }
interface HeatCell { likelihood: number; impact: number; count: number; references: string[] }
interface Heat { basis: string; cells: HeatCell[]; unassessed: number; total: number }

const CATEGORIES = ["STRATEGIC", "OPERATIONAL", "FINANCIAL", "LEGAL", "COMPLIANCE", "REPUTATIONAL", "TECHNOLOGY"]
const STRATEGIES = ["MITIGATE", "TRANSFER", "AVOID"]
const RATING_CFG: Record<string, { color: string; bg: string }> = {
  LOW: { color: "#166534", bg: "#DCFCE7" }, MEDIUM: { color: "#92400E", bg: "#FEF3C7" }, HIGH: { color: "#C2410C", bg: "#FFEDD5" }, CRITICAL: { color: "#991B1B", bg: "#FEE2E2" },
}
const STATUS_CFG: Record<string, { color: string; bg: string; label: string }> = {
  IDENTIFIED: { color: "#475569", bg: "#F1F5F9", label: "Not assessed" }, ASSESSED: { color: "#1D4ED8", bg: "#DBEAFE", label: "Assessed" },
  TREATING: { color: "#92400E", bg: "#FEF3C7", label: "Treating" }, ACCEPTED: { color: "#166534", bg: "#DCFCE7", label: "Accepted" }, CLOSED: { color: "#475569", bg: "#E2E8F0", label: "Closed" },
}
const POSITION: Record<string, string> = { WITHIN_APPETITE: "Within appetite", ABOVE_APPETITE: "Above appetite", OUTSIDE_TOLERANCE: "Outside tolerance" }
const ratingOf = (s: number) => s <= 4 ? "LOW" : s <= 9 ? "MEDIUM" : s <= 16 ? "HIGH" : "CRITICAL"
const SCALE = [1, 2, 3, 4, 5]
const base = "/api/v1/legalcompliance/risks"
const blankRisk = { title: "", description: "", category: "COMPLIANCE", emerging: false, ownerName: "", nextReviewDate: "" }

export default function RiskRegisterTab() {
  const qc = useQueryClient()
  const hasManage = usePermission("LEGALCOMPLIANCE_RISK_MANAGE")
  const hasAccept = usePermission("LEGALCOMPLIANCE_RISK_ACCEPT")
  const hasAdmin = usePermission("LEGALCOMPLIANCE_ADMIN")
  const canManage = hasManage || hasAdmin
  const canAccept = hasAccept || hasAdmin

  const [showClosed, setShowClosed] = useState(false)
  const [basis, setBasis] = useState<"RESIDUAL" | "INHERENT">("RESIDUAL")
  const [expanded, setExpanded] = useState<string | null>(null)
  const [riskForm, setRiskForm] = useState<{ id: string | null; f: typeof blankRisk } | null>(null)
  const [assess, setAssess] = useState<Risk | null>(null)
  const [aForm, setAForm] = useState({ il: 3, ii: 3, rl: 2, ri: 2, rationale: "" })
  const [treat, setTreat] = useState<Risk | null>(null)
  const [tForm, setTForm] = useState({ strategy: "MITIGATE", plan: "", dueDate: "" })
  const [accept, setAccept] = useState<Risk | null>(null)
  const [accForm, setAccForm] = useState({ reason: "", expiresOn: "" })
  const [reason, setReason] = useState<{ risk: Risk; action: "close" | "reopen" } | null>(null)
  const [reasonText, setReasonText] = useState("")
  const [showSettings, setShowSettings] = useState(false)
  const [sForm, setSForm] = useState({ appetiteMax: 9, toleranceMax: 16 })
  const [apiError, setApiError] = useState("")
  const [notice, setNotice] = useState("")

  const { data: summary } = useQuery<Summary>({ queryKey: ["lc-risks", "summary"], queryFn: async () => one(await apiClient.get(`${base}/summary`)) })
  const { data: heat } = useQuery<Heat>({ queryKey: ["lc-risks", "heat", basis], queryFn: async () => one(await apiClient.get(`${base}/heatmap`, { params: { basis } })) })
  const { data: settings } = useQuery<{ appetiteMax: number; toleranceMax: number }>({ queryKey: ["lc-risks", "settings"], queryFn: async () => one(await apiClient.get(`${base}/settings`)) })
  const { data: risks = [], isLoading } = useQuery<Risk[]>({
    queryKey: ["lc-risks", "list", showClosed], queryFn: async () => unwrap(await apiClient.get(base, { params: { includeClosed: showClosed } })),
  })
  const { data: detail } = useQuery<Detail>({
    queryKey: ["lc-risks", "detail", expanded], enabled: !!expanded, queryFn: async () => one(await apiClient.get(`${base}/${expanded}`)),
  })

  const refresh = () => { qc.invalidateQueries({ queryKey: ["lc-risks"] }); qc.invalidateQueries({ queryKey: ["lc-coverage"] }); setApiError("") }
  const fail = (m: string) => (e: any) => setApiError(errMsg(e, m))
  const ok = (msg: string, close: () => void) => () => { refresh(); close(); setNotice(msg) }

  const saveRisk = useMutation({
    mutationFn: ({ id, body }: { id: string | null; body: any }) => id ? apiClient.put(`${base}/${id}`, body) : apiClient.post(base, body),
    onSuccess: ok("Risk saved", () => setRiskForm(null)), onError: fail("Failed to save risk"),
  })
  const doAssess = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) => apiClient.post(`${base}/${id}/assess`, body),
    onSuccess: ok("Assessment recorded — the previous one stays in the history", () => setAssess(null)), onError: fail("Failed to record assessment"),
  })
  const doTreat = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) => apiClient.post(`${base}/${id}/treatment`, body),
    onSuccess: ok("Treatment plan saved", () => setTreat(null)), onError: fail("Failed to save treatment"),
  })
  const doAccept = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) => apiClient.post(`${base}/${id}/accept`, body),
    onSuccess: ok("Risk accepted", () => setAccept(null)), onError: fail("Failed to accept risk"),
  })
  const doReason = useMutation({
    mutationFn: ({ id, action, reason }: { id: string; action: string; reason: string }) => apiClient.post(`${base}/${id}/${action}`, { reason }),
    onSuccess: ok("Saved", () => setReason(null)), onError: fail("Failed to update risk"),
  })
  const doSettings = useMutation({
    mutationFn: (body: any) => apiClient.put(`${base}/settings`, body), onSuccess: ok("Appetite and tolerance saved — existing assessments keep the thresholds they were made under", () => setShowSettings(false)),
    onError: fail("Failed to save settings"),
  })

  const aInh = aForm.il * aForm.ii, aRes = aForm.rl * aForm.ri
  const residualTooHigh = aRes > aInh
  const heatColor = (l: number, i: number) => RATING_CFG[ratingOf(l * i)].bg
  const card = (label: string, v: number | undefined, color = "#0F172A") => (
    <div style={{ flex: 1, minWidth: 120, background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, padding: "12px 16px" }}>
      <div style={{ fontSize: 12, color: "#64748B", fontWeight: 600 }}>{label}</div><div style={{ fontSize: 24, fontWeight: 800, color }}>{v ?? "—"}</div>
    </div>
  )

  return (
    <div>
      <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 16 }}>
        {card("Open risks", summary?.open)}{card("Critical (residual)", summary?.critical, summary?.critical ? "#991B1B" : undefined)}
        {card("Above appetite", summary?.aboveAppetite, "#C2410C")}{card("Outside tolerance", summary?.outsideTolerance, summary?.outsideTolerance ? "#991B1B" : undefined)}
        {card("Reviews overdue", summary?.overdueReview)}{card("Not yet assessed", summary?.unassessed, "#92400E")}
      </div>

      <div style={{ display: "flex", gap: 20, flexWrap: "wrap", marginBottom: 18 }}>
        <div style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, padding: 16 }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8, gap: 12 }}>
            <b style={{ fontSize: 13, color: "#0F172A" }}>Heat map</b>
            <select aria-label="Heat map basis" style={{ ...input, width: 120, padding: "4px 8px", fontSize: 12 }} value={basis} onChange={e => setBasis(e.target.value as any)}>
              <option value="RESIDUAL">Residual</option><option value="INHERENT">Inherent</option>
            </select>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "auto repeat(5, 54px)", gap: 3, alignItems: "center", fontSize: 11, color: "#64748B" }}>
            {[5, 4, 3, 2, 1].map(l => (
              <div key={l} style={{ display: "contents" }}>
                <div style={{ paddingRight: 6 }}>L{l}</div>
                {SCALE.map(i => {
                  const cell = heat?.cells.find(c => c.likelihood === l && c.impact === i)
                  return <div key={i} title={cell?.references.join(", ") || "No risks"} style={{ height: 40, background: heatColor(l, i), borderRadius: 6, display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 800, fontSize: 15, color: "#0F172A", opacity: cell?.count ? 1 : 0.45 }}>{cell?.count || ""}</div>
                })}
              </div>
            ))}
            <div />{SCALE.map(i => <div key={i} style={{ textAlign: "center" }}>I{i}</div>)}
          </div>
          <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 6 }}>{heat ? `${heat.total - heat.unassessed} of ${heat.total} open risks plotted` : ""}</div>
        </div>
        <div style={{ flex: 1, minWidth: 260, background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 12, padding: 16, fontSize: 13, color: "#475569" }}>
          <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 6 }}><ShieldAlert size={16} color="#4338CA" /><b style={{ color: "#0F172A" }}>Appetite and tolerance</b></div>
          Scores at or below <b>{settings?.appetiteMax ?? "—"}</b> are within appetite. Above <b>{settings?.toleranceMax ?? "—"}</b> a risk is outside tolerance and cannot be accepted, only treated.
          <div style={{ marginTop: 8 }}>{canAccept && <button style={ghostBtn} onClick={() => { setSForm({ appetiteMax: settings?.appetiteMax ?? 9, toleranceMax: settings?.toleranceMax ?? 16 }); setApiError(""); setShowSettings(true) }}><Settings size={13} style={{ verticalAlign: -2 }} /> Change</button>}</div>
        </div>
      </div>

      <div style={{ display: "flex", gap: 10, alignItems: "center", marginBottom: 12, flexWrap: "wrap" }}>
        {canManage && <button style={primaryBtn} onClick={() => { setApiError(""); setRiskForm({ id: null, f: blankRisk }) }}><Plus size={14} style={{ verticalAlign: -2 }} /> New risk</button>}
        <label style={{ fontSize: 13, color: "#475569", display: "flex", gap: 6, alignItems: "center" }}><input type="checkbox" checked={showClosed} onChange={e => setShowClosed(e.target.checked)} /> Show closed</label>
      </div>
      {notice && <div style={{ background: "#ECFDF5", border: "1px solid #A7F3D0", color: "#065F46", borderRadius: 8, padding: "8px 12px", fontSize: 13, marginBottom: 12 }}>{notice}</div>}
      {apiError && !riskForm && !assess && !treat && !accept && !reason && !showSettings && <Err m={apiError} />}

      <RowLimitNotice count={risks.length} />
      {isLoading ? <p style={{ color: "#64748B" }}>Loading…</p> : risks.length === 0 ? <p style={{ color: "#64748B", fontSize: 14 }}>No risks recorded yet.</p> : risks.map(r => {
        const st = STATUS_CFG[r.status], a = r.assessment, open = expanded === r.id
        return (
          <div key={r.id} style={{ border: "1px solid #E2E8F0", borderRadius: 10, background: "#fff", marginBottom: 8 }}>
            <button onClick={() => setExpanded(open ? null : r.id)} style={{ all: "unset", cursor: "pointer", display: "flex", gap: 12, alignItems: "center", padding: "12px 16px", width: "calc(100% - 32px)" }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{r.reference} — {r.title}{r.emerging && <span style={{ marginLeft: 8, fontSize: 11, color: "#7C3AED" }}>emerging</span>}</div>
                <div style={{ fontSize: 12, color: "#64748B" }}>{r.category} · owner {r.ownerName} · review {fmtDate(r.nextReviewDate)}{r.reviewOverdue ? " (overdue)" : ""} · {r.controlCount} control(s) · {r.obligationCount} obligation(s)</div>
              </div>
              {a ? <><Badge text={`Inherent ${a.inherentScore}`} {...RATING_CFG[a.inherentRating]} /><Badge text={`Residual ${a.residualScore} · ${POSITION[a.residualPosition]}`} {...RATING_CFG[a.residualRating]} /></> : null}
              <Badge text={st.label} color={st.color} bg={st.bg} />
              {open ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
            </button>
            {open && (
              <div style={{ padding: "0 16px 14px", fontSize: 13, color: "#475569" }}>
                {r.description && <p style={{ marginTop: 0 }}>{r.description}</p>}
                {r.treatmentPlan && <p><b>Treatment ({r.treatmentStrategy}, due {fmtDate(r.treatmentDue)}):</b> {r.treatmentPlan}</p>}
                {r.status === "ACCEPTED" && <p><b>Accepted</b> by {r.acceptedByName} on {fmtDate(r.acceptedAt)} until {fmtDate(r.acceptanceExpires)}: {r.acceptanceReason}</p>}
                {r.status === "CLOSED" && <p><b>Closed:</b> {r.closeReason}</p>}
                <div style={{ display: "flex", gap: 8, flexWrap: "wrap", margin: "8px 0" }}>
                  {canManage && r.status !== "CLOSED" && <button style={ghostBtn} onClick={() => { setApiError(""); setAForm(a ? { il: a.inherentLikelihood, ii: a.inherentImpact, rl: a.residualLikelihood, ri: a.residualImpact, rationale: "" } : { il: 3, ii: 3, rl: 2, ri: 2, rationale: "" }); setAssess(r) }}>{a ? "Reassess" : "Assess"}</button>}
                  {canManage && r.status !== "CLOSED" && r.status !== "IDENTIFIED" && <button style={ghostBtn} onClick={() => { setApiError(""); setTForm({ strategy: r.treatmentStrategy ?? "MITIGATE", plan: r.treatmentPlan ?? "", dueDate: r.treatmentDue ?? "" }); setTreat(r) }}>Plan treatment</button>}
                  {canAccept && r.status !== "CLOSED" && r.status !== "IDENTIFIED" && a?.residualPosition !== "OUTSIDE_TOLERANCE" && <button style={ghostBtn} onClick={() => { setApiError(""); setAccForm({ reason: "", expiresOn: "" }); setAccept(r) }}>Accept risk</button>}
                  {canManage && r.status !== "CLOSED" && <button style={ghostBtn} onClick={() => { setApiError(""); setRiskForm({ id: r.id, f: { title: r.title, description: r.description ?? "", category: r.category, emerging: r.emerging, ownerName: r.ownerName, nextReviewDate: r.nextReviewDate } }) }}>Edit</button>}
                  {canManage && <button style={{ ...ghostBtn, color: r.status === "CLOSED" ? "#166534" : "#991B1B" }} onClick={() => { setApiError(""); setReasonText(""); setReason({ risk: r, action: r.status === "CLOSED" ? "reopen" : "close" }) }}>{r.status === "CLOSED" ? "Reopen" : "Close"}</button>}
                </div>
                {a && a.residualPosition === "OUTSIDE_TOLERANCE" && r.status !== "CLOSED" && <p style={{ color: "#991B1B", fontSize: 12 }}>Residual score is above tolerance — this risk must be treated; it cannot be accepted.</p>}
                <GrcLinksPanel kind="RISK" id={r.id} canEdit={canManage} />
                {detail?.risk.id === r.id && detail.history.length > 0 && (
                  <div style={{ marginTop: 12 }}>
                    <div style={{ fontSize: 12, fontWeight: 700, marginBottom: 4 }}>Assessment history</div>
                    {detail.history.map(h => (
                      <div key={h.id} style={{ fontSize: 12, padding: "4px 0", borderTop: "1px solid #F1F5F9", opacity: h.current ? 1 : 0.7 }}>
                        {fmtDateTime(h.assessedAt)} · {h.assessedByName ?? "—"} · inherent {h.inherentLikelihood}×{h.inherentImpact}={h.inherentScore} → residual {h.residualLikelihood}×{h.residualImpact}={h.residualScore} (appetite {h.appetiteMax}, tolerance {h.toleranceMax}){h.current ? " · current" : ""}
                        <div style={{ color: "#64748B" }}>{h.rationale}</div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        )
      })}

      {riskForm && (
        <Modal title={riskForm.id ? "Edit risk" : "New risk"} onClose={() => setRiskForm(null)}>
          <Err m={apiError} />
          <Field l="Title *"><input style={input} value={riskForm.f.title} onChange={e => setRiskForm({ ...riskForm, f: { ...riskForm.f, title: e.target.value } })} /></Field>
          <Field l="Description"><textarea style={{ ...input, minHeight: 70 }} value={riskForm.f.description} onChange={e => setRiskForm({ ...riskForm, f: { ...riskForm.f, description: e.target.value } })} /></Field>
          <div style={{ display: "flex", gap: 12 }}>
            <div style={{ flex: 1 }}><Field l="Category *"><select style={input} value={riskForm.f.category} onChange={e => setRiskForm({ ...riskForm, f: { ...riskForm.f, category: e.target.value } })}>{CATEGORIES.map(c => <option key={c}>{c}</option>)}</select></Field></div>
            <div style={{ flex: 1 }}><Field l="Next review *"><input type="date" style={input} value={riskForm.f.nextReviewDate} onChange={e => setRiskForm({ ...riskForm, f: { ...riskForm.f, nextReviewDate: e.target.value } })} /></Field></div>
          </div>
          <Field l="Risk owner *"><input style={input} value={riskForm.f.ownerName} onChange={e => setRiskForm({ ...riskForm, f: { ...riskForm.f, ownerName: e.target.value } })} /></Field>
          <label style={{ display: "flex", gap: 8, fontSize: 13, color: "#475569", marginBottom: 12 }}><input type="checkbox" checked={riskForm.f.emerging} onChange={e => setRiskForm({ ...riskForm, f: { ...riskForm.f, emerging: e.target.checked } })} /> Emerging risk</label>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setRiskForm(null)}>Cancel</button>
            <button style={primaryBtn} disabled={!riskForm.f.title.trim() || !riskForm.f.ownerName.trim() || !riskForm.f.nextReviewDate || saveRisk.isPending}
              onClick={() => saveRisk.mutate({ id: riskForm.id, body: { ...riskForm.f, description: riskForm.f.description || null } })}>Save</button>
          </div>
        </Modal>
      )}

      {assess && (
        <Modal title={`Assess ${assess.reference}`} onClose={() => setAssess(null)}>
          <p style={{ fontSize: 12, color: "#64748B", marginTop: 0 }}>Inherent = before controls. Residual = after the controls you rely on today. Likelihood and impact are each 1 (lowest) to 5 (highest).</p>
          <Err m={apiError} />
          {([["Inherent", "il", "ii"], ["Residual", "rl", "ri"]] as const).map(([name, lk, ik]) => (
            <div key={name} style={{ display: "flex", gap: 12, alignItems: "flex-end", marginBottom: 8 }}>
              <div style={{ width: 70, fontWeight: 700, fontSize: 13 }}>{name}</div>
              <div style={{ flex: 1 }}><Field l="Likelihood"><select style={input} value={aForm[lk]} onChange={e => setAForm({ ...aForm, [lk]: +e.target.value })}>{SCALE.map(n => <option key={n}>{n}</option>)}</select></Field></div>
              <div style={{ flex: 1 }}><Field l="Impact"><select style={input} value={aForm[ik]} onChange={e => setAForm({ ...aForm, [ik]: +e.target.value })}>{SCALE.map(n => <option key={n}>{n}</option>)}</select></Field></div>
              <div style={{ width: 90, paddingBottom: 12 }}><Badge text={`${aForm[lk] * aForm[ik]} · ${ratingOf(aForm[lk] * aForm[ik])}`} {...RATING_CFG[ratingOf(aForm[lk] * aForm[ik])]} /></div>
            </div>
          ))}
          {residualTooHigh && <div style={{ color: "#DC2626", fontSize: 13, marginBottom: 8 }}>Residual risk cannot be higher than inherent.</div>}
          <Field l="Rationale * (at least 15 characters)"><textarea style={{ ...input, minHeight: 70 }} value={aForm.rationale} onChange={e => setAForm({ ...aForm, rationale: e.target.value })} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setAssess(null)}>Cancel</button>
            <button style={primaryBtn} disabled={residualTooHigh || aForm.rationale.trim().length < 15 || doAssess.isPending}
              onClick={() => doAssess.mutate({ id: assess.id, body: { inherentLikelihood: aForm.il, inherentImpact: aForm.ii, residualLikelihood: aForm.rl, residualImpact: aForm.ri, rationale: aForm.rationale.trim() } })}>Record assessment</button>
          </div>
        </Modal>
      )}

      {treat && (
        <Modal title={`Treatment for ${treat.reference}`} onClose={() => setTreat(null)}>
          <Err m={apiError} />
          {treat.status === "ACCEPTED" && <p style={{ fontSize: 12, color: "#92400E" }}>Planning treatment withdraws the current acceptance.</p>}
          <Field l="Strategy *"><select style={input} value={tForm.strategy} onChange={e => setTForm({ ...tForm, strategy: e.target.value })}>{STRATEGIES.map(s => <option key={s}>{s}</option>)}</select></Field>
          <Field l="Plan *"><textarea style={{ ...input, minHeight: 80 }} value={tForm.plan} onChange={e => setTForm({ ...tForm, plan: e.target.value })} /></Field>
          <Field l="Due date *"><input type="date" style={input} value={tForm.dueDate} onChange={e => setTForm({ ...tForm, dueDate: e.target.value })} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setTreat(null)}>Cancel</button>
            <button style={primaryBtn} disabled={!tForm.plan.trim() || !tForm.dueDate || doTreat.isPending} onClick={() => doTreat.mutate({ id: treat.id, body: tForm })}>Save plan</button>
          </div>
        </Modal>
      )}

      {accept && (
        <Modal title={`Accept ${accept.reference}`} onClose={() => setAccept(null)}>
          <p style={{ fontSize: 12, color: "#64748B", marginTop: 0 }}>Acceptance is time-limited. When it expires the risk returns to “Assessed” and must be treated or accepted again.</p>
          <Err m={apiError} />
          <Field l="Reason * (at least 15 characters)"><textarea style={{ ...input, minHeight: 80 }} value={accForm.reason} onChange={e => setAccForm({ ...accForm, reason: e.target.value })} /></Field>
          <Field l="Accepted until *"><input type="date" style={input} value={accForm.expiresOn} onChange={e => setAccForm({ ...accForm, expiresOn: e.target.value })} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setAccept(null)}>Cancel</button>
            <button style={primaryBtn} disabled={accForm.reason.trim().length < 15 || !accForm.expiresOn || doAccept.isPending} onClick={() => doAccept.mutate({ id: accept.id, body: accForm })}>Accept risk</button>
          </div>
        </Modal>
      )}

      {reason && (
        <Modal title={`${reason.action === "close" ? "Close" : "Reopen"} ${reason.risk.reference}`} onClose={() => setReason(null)}>
          <Err m={apiError} />
          <Field l="Reason *"><textarea style={{ ...input, minHeight: 70 }} value={reasonText} onChange={e => setReasonText(e.target.value)} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setReason(null)}>Cancel</button>
            <button style={primaryBtn} disabled={!reasonText.trim() || doReason.isPending} onClick={() => doReason.mutate({ id: reason.risk.id, action: reason.action, reason: reasonText.trim() })}>Confirm</button>
          </div>
        </Modal>
      )}

      {showSettings && (
        <Modal title="Risk appetite and tolerance" onClose={() => setShowSettings(false)}>
          <p style={{ fontSize: 12, color: "#64748B", marginTop: 0 }}>Thresholds are scores out of 25. Appetite must be lower than tolerance.</p>
          <Err m={apiError} />
          <Field l="Appetite (acceptable up to)"><input type="number" min={1} max={24} style={input} value={sForm.appetiteMax} onChange={e => setSForm({ ...sForm, appetiteMax: +e.target.value })} /></Field>
          <Field l="Tolerance (never accept above)"><input type="number" min={2} max={25} style={input} value={sForm.toleranceMax} onChange={e => setSForm({ ...sForm, toleranceMax: +e.target.value })} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setShowSettings(false)}>Cancel</button>
            <button style={primaryBtn} disabled={sForm.appetiteMax >= sForm.toleranceMax || doSettings.isPending} onClick={() => doSettings.mutate(sForm)}>Save</button>
          </div>
        </Modal>
      )}
    </div>
  )
}
