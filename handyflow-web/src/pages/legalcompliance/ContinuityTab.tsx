// src/pages/legalcompliance/ContinuityTab.tsx
//
// Business continuity. Endpoints (/api/v1/legalcompliance/continuity): GET /summary · GET/POST /processes · GET/PUT /processes/{id} · POST /processes/{id}/bia-review|retire|plans
// · PUT /plans/{id} · POST /plans/{id}/approve|confirm-review|retire|tests
// Overdue exercises, plan reviews and impact-analysis reviews are worked out by the server from dates. The plan's author cannot approve it (the server enforces this);
// approving a new plan retires the previous approved one, and an approved plan is never edited in place.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { one, unwrap, input, primaryBtn, ghostBtn, fmtDate, errMsg } from "./lcHelpers"
import { Modal, Field, Err, Badge } from "./LcUi"
import { Plus } from "lucide-react"

const base = "/api/v1/legalcompliance/continuity"

type Crit = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL"
type Outcome = "PASSED" | "PASSED_WITH_ISSUES" | "FAILED"
type TestType = "TABLETOP" | "WALKTHROUGH" | "SIMULATION" | "FAILOVER"
type PlanStatus = "DRAFT" | "APPROVED" | "RETIRED"

interface Process {
  id: string; reference: string; name: string; description: string | null; ownerName: string; department: string | null; criticality: Crit; rtoHours: number; rpoHours: number
  dependencies: string | null; status: "ACTIVE" | "RETIRED"; biaReviewedOn: string | null; biaReviewMonths: number; biaOverdue: boolean
  approvedPlanId: string | null; approvedPlanReference: string | null; approvedPlanVersion: string | null; planReviewDue: string | null; planReviewOverdue: boolean
  lastTestDate: string | null; lastTestOutcome: Outcome | null; lastTestRtoMet: boolean | null; testDueOn: string | null; testOverdue: boolean; retiredReason: string | null
}
interface Plan {
  id: string; reference: string; title: string; versionLabel: string; ownerName: string; content: string; status: PlanStatus; reviewMonths: number; authorName: string
  approvedByName: string | null; approvedOn: string | null; nextReviewDue: string | null; reviewOverdue: boolean; retiredReason: string | null
}
interface Test {
  id: string; planReference: string; testDate: string; testType: TestType; outcome: Outcome; rtoTargetHours: number; achievedRtoHours: number | null; rtoMet: boolean | null
  issues: string | null; actions: string | null; conductedByName: string; recordedByName: string
}
interface Detail { process: Process; plans: Plan[]; tests: Test[] }
interface Summary { processes: number; critical: number; withoutApprovedPlan: number; criticalWithoutPlan: number; biaOverdue: number; planReviewOverdue: number; testOverdue: number; lastTestUnsuccessful: number }

const pretty = (s: string) => s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, " ")
const critTone: Record<Crit, { color: string; bg: string }> = {
  CRITICAL: { color: "#991B1B", bg: "#FEE2E2" }, HIGH: { color: "#9A3412", bg: "#FFEDD5" }, MEDIUM: { color: "#92400E", bg: "#FEF3C7" }, LOW: { color: "#475569", bg: "#E2E8F0" },
}

function ProcessForm({ initial, onClose, onSaved }: { initial: Process | null; onClose: () => void; onSaved: () => void }) {
  const [f, setF] = useState({
    name: initial?.name ?? "", description: initial?.description ?? "", ownerName: initial?.ownerName ?? "", department: initial?.department ?? "", criticality: (initial?.criticality ?? "HIGH") as Crit,
    rtoHours: String(initial?.rtoHours ?? 8), rpoHours: String(initial?.rpoHours ?? 4), dependencies: initial?.dependencies ?? "", biaReviewMonths: String(initial?.biaReviewMonths ?? 12),
  })
  const [err, setErr] = useState("")
  const save = useMutation({
    mutationFn: () => {
      const body = { ...f, rtoHours: Number(f.rtoHours), rpoHours: Number(f.rpoHours), biaReviewMonths: Number(f.biaReviewMonths), description: f.description || null, department: f.department || null, dependencies: f.dependencies || null }
      return initial ? apiClient.put(`${base}/processes/${initial.id}`, body) : apiClient.post(`${base}/processes`, body)
    },
    onSuccess: onSaved, onError: (e: unknown) => setErr(errMsg(e, "Could not save the process")),
  })
  const set = (k: keyof typeof f, v: string) => setF({ ...f, [k]: v })
  return (
    <Modal title={initial ? "Edit process" : "Add a critical process"} onClose={onClose}>
      <Err m={err} />
      <Field l="Process name *"><input style={input} value={f.name} onChange={e => set("name", e.target.value)} /></Field>
      <div style={{ display: "flex", gap: 10 }}>
        <div style={{ flex: 2 }}><Field l="Owner *"><input style={input} value={f.ownerName} onChange={e => set("ownerName", e.target.value)} /></Field></div>
        <div style={{ flex: 1 }}><Field l="Department"><input style={input} value={f.department} onChange={e => set("department", e.target.value)} /></Field></div>
      </div>
      <div style={{ display: "flex", gap: 10 }}>
        <div style={{ flex: 1 }}><Field l="Criticality *"><select style={input} value={f.criticality} onChange={e => set("criticality", e.target.value)}>{(["CRITICAL", "HIGH", "MEDIUM", "LOW"] as Crit[]).map(c => <option key={c} value={c}>{pretty(c)}</option>)}</select></Field></div>
        <div style={{ flex: 1 }}><Field l="Max downtime, RTO (hours) *"><input type="number" min={1} style={input} value={f.rtoHours} onChange={e => set("rtoHours", e.target.value)} /></Field></div>
        <div style={{ flex: 1 }}><Field l="Max data loss, RPO (hours) *"><input type="number" min={0} style={input} value={f.rpoHours} onChange={e => set("rpoHours", e.target.value)} /></Field></div>
      </div>
      <Field l="What it depends on (systems, suppliers, people)"><textarea style={{ ...input, minHeight: 50 }} value={f.dependencies} onChange={e => set("dependencies", e.target.value)} /></Field>
      <Field l="Description"><textarea style={{ ...input, minHeight: 50 }} value={f.description} onChange={e => set("description", e.target.value)} /></Field>
      <Field l="Review the impact analysis every (months)"><input type="number" min={1} max={36} style={input} value={f.biaReviewMonths} onChange={e => set("biaReviewMonths", e.target.value)} /></Field>
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={onClose}>Cancel</button><button style={primaryBtn} disabled={save.isPending} onClick={() => save.mutate()}>Save</button></div>
    </Modal>
  )
}

function ProcessModal({ id, canManage, onClose }: { id: string; canManage: boolean; onClose: () => void }) {
  const qc = useQueryClient()
  const [err, setErr] = useState("")
  const [editing, setEditing] = useState(false)
  const [planForm, setPlanForm] = useState<{ id: string | null; title: string; versionLabel: string; ownerName: string; content: string; reviewMonths: string } | null>(null)
  const [testFor, setTestFor] = useState<Plan | null>(null)
  const [test, setTest] = useState({ testDate: new Date().toISOString().slice(0, 10), testType: "TABLETOP" as TestType, outcome: "PASSED" as Outcome, achievedRtoHours: "", issues: "", actions: "", conductedBy: "" })
  const [reason, setReason] = useState<{ kind: "process" | "plan"; id: string; text: string } | null>(null)
  const [viewing, setViewing] = useState<string | null>(null)

  const { data: d, isLoading } = useQuery<Detail>({ queryKey: ["lc-bcm", "process", id], queryFn: async () => one(await apiClient.get(`${base}/processes/${id}`)) })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["lc-bcm"] }); setErr("") }
  const fail = (e: unknown) => setErr(errMsg(e, "That did not work"))
  const act = useMutation({ mutationFn: ({ path, body }: { path: string; body?: unknown }) => apiClient.post(`${base}/${path}`, body), onSuccess: () => { refresh(); setReason(null); setTestFor(null) }, onError: fail })
  const savePlan = useMutation({
    mutationFn: () => {
      if (!planForm) return Promise.resolve(null)
      const body = { title: planForm.title, versionLabel: planForm.versionLabel, ownerName: planForm.ownerName, content: planForm.content, reviewMonths: Number(planForm.reviewMonths) }
      return planForm.id ? apiClient.put(`${base}/plans/${planForm.id}`, body) : apiClient.post(`${base}/processes/${id}/plans`, body)
    },
    onSuccess: () => { refresh(); setPlanForm(null) }, onError: fail,
  })

  if (isLoading || !d) return <Modal title="Process" onClose={onClose} wide><p style={{ color: "#64748B" }}>Loading…</p></Modal>
  const p = d.process
  const retired = p.status === "RETIRED"
  const today = new Date().toISOString().slice(0, 10)

  return (
    <Modal title={`${p.reference} · ${p.name}`} onClose={onClose} wide>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 8 }}>
        <Badge text={pretty(p.criticality)} color={critTone[p.criticality].color} bg={critTone[p.criticality].bg} />
        <Badge text={`RTO ${p.rtoHours}h`} color="#3730A3" bg="#E0E7FF" /><Badge text={`RPO ${p.rpoHours}h`} color="#3730A3" bg="#E0E7FF" />
        {retired && <Badge text="Retired" color="#475569" bg="#E2E8F0" />}
        {!retired && !p.approvedPlanId && <Badge text="No approved plan" color="#991B1B" bg="#FEE2E2" />}
        {p.testOverdue && <Badge text="Exercise overdue" color="#991B1B" bg="#FEE2E2" />}
        {p.planReviewOverdue && <Badge text="Plan review overdue" color="#92400E" bg="#FEF3C7" />}
        {p.biaOverdue && <Badge text="Impact analysis review overdue" color="#92400E" bg="#FEF3C7" />}
      </div>
      <div style={{ fontSize: 12, color: "#64748B", marginBottom: 10 }}>
        Owner {p.ownerName}{p.department ? ` · ${p.department}` : ""}. Impact analysis last reviewed {p.biaReviewedOn ? fmtDate(p.biaReviewedOn) : "never"} (every {p.biaReviewMonths} months).
        {p.dependencies ? ` Depends on: ${p.dependencies}` : ""}{p.retiredReason ? ` Retired: ${p.retiredReason}` : ""}
      </div>
      <Err m={err} />
      {canManage && !retired && (
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 14 }}>
          <button style={ghostBtn} onClick={() => setEditing(true)}>Edit</button>
          <button style={ghostBtn} onClick={() => act.mutate({ path: `processes/${id}/bia-review`, body: { reviewedOn: today } })}>Impact analysis reviewed today</button>
          <button style={ghostBtn} onClick={() => setPlanForm({ id: null, title: `${p.name} recovery plan`, versionLabel: "v1", ownerName: p.ownerName, content: "", reviewMonths: "12" })}>Draft a plan</button>
          <button style={{ ...ghostBtn, color: "#B91C1C" }} onClick={() => setReason({ kind: "process", id, text: "" })}>Retire process</button>
        </div>
      )}

      {reason && (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 8, padding: 12, marginBottom: 12 }}>
          <Field l="Reason (min. 10 characters)"><textarea style={{ ...input, minHeight: 50 }} value={reason.text} onChange={e => setReason({ ...reason, text: e.target.value })} /></Field>
          <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}><button style={ghostBtn} onClick={() => setReason(null)}>Cancel</button>
            <button style={primaryBtn} disabled={reason.text.trim().length < 10 || act.isPending} onClick={() => act.mutate({ path: reason.kind === "process" ? `processes/${reason.id}/retire` : `plans/${reason.id}/retire`, body: { reason: reason.text } })}>Retire</button></div>
        </div>
      )}

      {planForm && (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 8, padding: 12, marginBottom: 12 }}>
          <div style={{ display: "flex", gap: 10 }}>
            <div style={{ flex: 3 }}><Field l="Title *"><input style={input} value={planForm.title} onChange={e => setPlanForm({ ...planForm, title: e.target.value })} /></Field></div>
            <div style={{ flex: 1 }}><Field l="Version *"><input style={input} value={planForm.versionLabel} onChange={e => setPlanForm({ ...planForm, versionLabel: e.target.value })} /></Field></div>
          </div>
          <div style={{ display: "flex", gap: 10 }}>
            <div style={{ flex: 2 }}><Field l="Plan owner *"><input style={input} value={planForm.ownerName} onChange={e => setPlanForm({ ...planForm, ownerName: e.target.value })} /></Field></div>
            <div style={{ flex: 1 }}><Field l="Review every (months)"><input type="number" min={1} max={36} style={input} value={planForm.reviewMonths} onChange={e => setPlanForm({ ...planForm, reviewMonths: e.target.value })} /></Field></div>
          </div>
          <Field l="The plan * (who does what, in what order, with what; min. 50 characters)"><textarea style={{ ...input, minHeight: 140 }} value={planForm.content} onChange={e => setPlanForm({ ...planForm, content: e.target.value })} /></Field>
          <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}><button style={ghostBtn} onClick={() => setPlanForm(null)}>Cancel</button><button style={primaryBtn} disabled={savePlan.isPending} onClick={() => savePlan.mutate()}>Save draft</button></div>
        </div>
      )}

      <div style={{ fontSize: 13, fontWeight: 700, margin: "8px 0 6px" }}>Plans</div>
      {d.plans.length === 0 && <p style={{ fontSize: 13, color: "#64748B" }}>No plan yet.</p>}
      {d.plans.map(pl => (
        <div key={pl.id} style={{ borderTop: "1px solid #F1F5F9", padding: "8px 0", fontSize: 13, opacity: pl.status === "RETIRED" ? 0.6 : 1 }}>
          <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
            <b>{pl.reference} {pl.versionLabel}</b><Badge text={pretty(pl.status)} color={pl.status === "APPROVED" ? "#166534" : pl.status === "DRAFT" ? "#92400E" : "#475569"} bg={pl.status === "APPROVED" ? "#DCFCE7" : pl.status === "DRAFT" ? "#FEF3C7" : "#E2E8F0"} />
            {pl.reviewOverdue && <Badge text="Review overdue" color="#991B1B" bg="#FEE2E2" />}
            <span style={{ color: "#64748B" }}>Author {pl.authorName}{pl.approvedByName ? ` · approved by ${pl.approvedByName} on ${fmtDate(pl.approvedOn)}` : ""}{pl.nextReviewDue ? ` · review due ${fmtDate(pl.nextReviewDue)}` : ""}</span>
          </div>
          {pl.retiredReason && <div style={{ color: "#64748B", fontSize: 12 }}>Retired: {pl.retiredReason}</div>}
          <div style={{ display: "flex", gap: 6, marginTop: 6, flexWrap: "wrap" }}>
            <button style={{ ...ghostBtn, padding: "3px 8px", fontSize: 12 }} onClick={() => setViewing(viewing === pl.id ? null : pl.id)}>{viewing === pl.id ? "Hide text" : "Read plan"}</button>
            {canManage && pl.status === "DRAFT" && !retired && <>
              <button style={{ ...ghostBtn, padding: "3px 8px", fontSize: 12 }} onClick={() => setPlanForm({ id: pl.id, title: pl.title, versionLabel: pl.versionLabel, ownerName: pl.ownerName, content: pl.content, reviewMonths: String(pl.reviewMonths) })}>Edit draft</button>
              <button style={{ ...primaryBtn, padding: "3px 10px", fontSize: 12 }} disabled={act.isPending} onClick={() => act.mutate({ path: `plans/${pl.id}/approve` })}>Approve (not the author)</button>
              <button style={{ ...ghostBtn, padding: "3px 8px", fontSize: 12 }} onClick={() => setReason({ kind: "plan", id: pl.id, text: "" })}>Discard</button></>}
            {canManage && pl.status === "APPROVED" && <>
              <button style={{ ...ghostBtn, padding: "3px 8px", fontSize: 12 }} onClick={() => setTestFor(pl)}>Record an exercise</button>
              <button style={{ ...ghostBtn, padding: "3px 8px", fontSize: 12 }} onClick={() => act.mutate({ path: `plans/${pl.id}/confirm-review` })}>Confirm still current</button>
              <button style={{ ...ghostBtn, padding: "3px 8px", fontSize: 12 }} onClick={() => setReason({ kind: "plan", id: pl.id, text: "" })}>Retire</button></>}
          </div>
          {viewing === pl.id && <pre style={{ whiteSpace: "pre-wrap", background: "#F8FAFC", borderRadius: 8, padding: 10, fontSize: 12, marginTop: 6 }}>{pl.content}</pre>}
        </div>
      ))}

      {testFor && (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 8, padding: 12, margin: "10px 0" }}>
          <div style={{ fontWeight: 700, fontSize: 13, marginBottom: 8 }}>Exercise of {testFor.reference} (target recovery {p.rtoHours}h)</div>
          <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
            <div style={{ flex: 1, minWidth: 130 }}><Field l="Date"><input type="date" style={input} max={today} value={test.testDate} onChange={e => setTest({ ...test, testDate: e.target.value })} /></Field></div>
            <div style={{ flex: 1, minWidth: 130 }}><Field l="Type"><select style={input} value={test.testType} onChange={e => setTest({ ...test, testType: e.target.value as TestType })}>{(["TABLETOP", "WALKTHROUGH", "SIMULATION", "FAILOVER"] as TestType[]).map(x => <option key={x} value={x}>{pretty(x)}</option>)}</select></Field></div>
            <div style={{ flex: 1, minWidth: 130 }}><Field l="Outcome"><select style={input} value={test.outcome} onChange={e => setTest({ ...test, outcome: e.target.value as Outcome })}>{(["PASSED", "PASSED_WITH_ISSUES", "FAILED"] as Outcome[]).map(x => <option key={x} value={x}>{pretty(x)}</option>)}</select></Field></div>
            <div style={{ flex: 1, minWidth: 130 }}><Field l={`Recovery achieved (h)${test.testType === "FAILOVER" ? " *" : ""}`}><input type="number" min={0} step="0.25" style={input} value={test.achievedRtoHours} onChange={e => setTest({ ...test, achievedRtoHours: e.target.value })} /></Field></div>
          </div>
          <Field l="Conducted by *"><input style={input} value={test.conductedBy} onChange={e => setTest({ ...test, conductedBy: e.target.value })} /></Field>
          {test.outcome !== "PASSED" && <>
            <Field l="What went wrong * (min. 15 characters)"><textarea style={{ ...input, minHeight: 50 }} value={test.issues} onChange={e => setTest({ ...test, issues: e.target.value })} /></Field>
            <Field l="Follow-up actions * (min. 15 characters)"><textarea style={{ ...input, minHeight: 50 }} value={test.actions} onChange={e => setTest({ ...test, actions: e.target.value })} /></Field></>}
          <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}><button style={ghostBtn} onClick={() => setTestFor(null)}>Cancel</button>
            <button style={primaryBtn} disabled={act.isPending || !test.conductedBy.trim()} onClick={() => act.mutate({ path: `plans/${testFor.id}/tests`, body: { ...test, achievedRtoHours: test.achievedRtoHours === "" ? null : Number(test.achievedRtoHours), issues: test.issues || null, actions: test.actions || null } })}>Record</button></div>
        </div>
      )}

      <div style={{ fontSize: 13, fontWeight: 700, margin: "14px 0 6px" }}>Exercises</div>
      {d.tests.length === 0 && <p style={{ fontSize: 13, color: "#64748B" }}>None recorded.</p>}
      {d.tests.map(x => (
        <div key={x.id} style={{ borderTop: "1px solid #F1F5F9", padding: "6px 0", fontSize: 13 }}>
          <b>{fmtDate(x.testDate)}</b> · {pretty(x.testType)} · {pretty(x.outcome)} · {x.planReference}
          {x.achievedRtoHours !== null && <span style={{ color: x.rtoMet ? "#166534" : "#991B1B" }}> · recovered in {x.achievedRtoHours}h against {x.rtoTargetHours}h ({x.rtoMet ? "met" : "missed"})</span>}
          <div style={{ color: "#64748B", fontSize: 12 }}>Conducted by {x.conductedByName}</div>
          {x.issues && <div style={{ fontSize: 12 }}>Issues: {x.issues}</div>}{x.actions && <div style={{ fontSize: 12 }}>Actions: {x.actions}</div>}
        </div>
      ))}
      {editing && <ProcessForm initial={p} onClose={() => setEditing(false)} onSaved={() => { setEditing(false); refresh() }} />}
    </Modal>
  )
}

export default function ContinuityTab() {
  const qc = useQueryClient()
  const hasManage = usePermission("LEGALCOMPLIANCE_BCM_MANAGE")
  const hasAdmin = usePermission("LEGALCOMPLIANCE_ADMIN")
  const canManage = hasManage || hasAdmin
  const [adding, setAdding] = useState(false)
  const [openId, setOpenId] = useState<string | null>(null)
  const [showRetired, setShowRetired] = useState(false)
  const { data: summary } = useQuery<Summary>({ queryKey: ["lc-bcm", "summary"], queryFn: async () => one(await apiClient.get(`${base}/summary`)) })
  const { data: list = [], isLoading } = useQuery<Process[]>({ queryKey: ["lc-bcm", "list", showRetired], queryFn: async () => unwrap(await apiClient.get(`${base}/processes`, { params: { includeRetired: showRetired } })) })

  const tile = (label: string, v: number | undefined, bad?: boolean) => (
    <div style={{ border: "1px solid #E2E8F0", borderRadius: 10, padding: "10px 14px", minWidth: 120 }}>
      <div style={{ fontSize: 22, fontWeight: 800, color: bad && (v ?? 0) > 0 ? "#B91C1C" : "#0F172A" }}>{v ?? "—"}</div><div style={{ fontSize: 12, color: "#64748B" }}>{label}</div>
    </div>
  )

  return (
    <div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 16 }}>
        {tile("Processes", summary?.processes)}{tile("Critical, no approved plan", summary?.criticalWithoutPlan, true)}{tile("No approved plan", summary?.withoutApprovedPlan, true)}
        {tile("Exercises overdue", summary?.testOverdue, true)}{tile("Plan reviews overdue", summary?.planReviewOverdue, true)}{tile("Impact reviews overdue", summary?.biaOverdue, true)}{tile("Last exercise failed or too slow", summary?.lastTestUnsuccessful, true)}
      </div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
        <label style={{ fontSize: 13, color: "#475569" }}><input type="checkbox" checked={showRetired} onChange={e => setShowRetired(e.target.checked)} /> Show retired</label>
        {canManage && <button style={{ ...primaryBtn, display: "flex", gap: 6, alignItems: "center" }} onClick={() => setAdding(true)}><Plus size={14} />Add process</button>}
      </div>
      {isLoading && <p style={{ color: "#64748B" }}>Loading…</p>}
      {!isLoading && list.length === 0 && <p style={{ color: "#64748B" }}>No critical processes recorded yet.</p>}
      {list.length > 0 && (
        <div style={{ overflowX: "auto", border: "1px solid #E2E8F0", borderRadius: 10 }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
            <thead><tr>{["Ref", "Process", "Criticality", "RTO / RPO", "Approved plan", "Last exercise", "Needs attention"].map(h => <th key={h} style={{ textAlign: "left", padding: "8px 10px", background: "#F8FAFC", color: "#475569" }}>{h}</th>)}</tr></thead>
            <tbody>
              {list.map(p => (
                <tr key={p.id} onClick={() => setOpenId(p.id)} style={{ cursor: "pointer", borderTop: "1px solid #F1F5F9", opacity: p.status === "RETIRED" ? 0.55 : 1 }}>
                  <td style={{ padding: "8px 10px", color: "#64748B" }}>{p.reference}</td>
                  <td style={{ padding: "8px 10px", fontWeight: 600 }}>{p.name}</td>
                  <td style={{ padding: "8px 10px" }}><Badge text={pretty(p.criticality)} color={critTone[p.criticality].color} bg={critTone[p.criticality].bg} /></td>
                  <td style={{ padding: "8px 10px" }}>{p.rtoHours}h / {p.rpoHours}h</td>
                  <td style={{ padding: "8px 10px" }}>{p.approvedPlanReference ? `${p.approvedPlanReference} ${p.approvedPlanVersion}` : "None"}</td>
                  <td style={{ padding: "8px 10px" }}>{p.lastTestDate ? `${fmtDate(p.lastTestDate)} · ${pretty(p.lastTestOutcome ?? "")}` : "Never"}</td>
                  <td style={{ padding: "8px 10px" }}>{[p.status === "ACTIVE" && !p.approvedPlanId ? "No plan" : "", p.testOverdue ? "Exercise" : "", p.planReviewOverdue ? "Plan review" : "", p.biaOverdue ? "Impact review" : ""].filter(Boolean).join(", ") || "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {adding && <ProcessForm initial={null} onClose={() => setAdding(false)} onSaved={() => { setAdding(false); qc.invalidateQueries({ queryKey: ["lc-bcm"] }) }} />}
      {openId && <ProcessModal id={openId} canManage={canManage} onClose={() => setOpenId(null)} />}
    </div>
  )
}
