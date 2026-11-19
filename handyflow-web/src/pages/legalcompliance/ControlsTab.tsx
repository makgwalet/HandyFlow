// src/pages/legalcompliance/ControlsTab.tsx
//
// Control catalogue and coverage. Endpoints (ControlController, /api/v1/legalcompliance/controls):
//   GET / · GET /coverage · GET /{id} · POST / · PUT /{id} · POST /{id}/activate · /design-assessment · /retire
//
// "Active" means in operation, not effective. Operating effectiveness shows NOT TESTED until an approved control test sets it
// — no action on this tab can make a control "effective". Schedule and review tests on the Control Testing tab.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { input, primaryBtn, ghostBtn, unwrap, one, fmtDate, errMsg } from "./lcHelpers"
import { Modal, Field, Err, Badge } from "./LcUi"
import GrcLinksPanel from "./GrcLinksPanel"
import { ChevronDown, ChevronUp, Plus } from "lucide-react"

interface Control {
  id: string; reference: string; title: string; objective: string | null; procedure: string | null; controlType: string; nature: string; frequency: string
  ownerName: string; expectedEvidence: string | null; status: string; designRating: string; designAssessedByName: string | null; designAssessedAt: string | null
  designNote: string | null; operatingEffectiveness: string; lastTestedAt: string | null; nextTestDue: string | null; testOverdue: boolean; currentlyEffective: boolean; retireReason: string | null; obligationCount: number; riskCount: number
}
interface OblCov { obligationId: string; title: string; regulationReference: string | null; category: string; state: string; activeControls: number; testedEffectiveControls: number; risks: number }
interface RiskCov { riskId: string; reference: string; title: string; residualRating: string | null }
interface Coverage { obligations: number; uncovered: number; coveredUntested: number; coveredEffective: number; risksWithoutControl: number; draftControlsUnlinked: number; obligationRows: OblCov[]; unmitigatedRisks: RiskCov[] }

const TYPES = ["PREVENTIVE", "DETECTIVE", "CORRECTIVE"], NATURES = ["MANUAL", "AUTOMATED", "HYBRID"]
const FREQS = ["CONTINUOUS", "DAILY", "WEEKLY", "MONTHLY", "QUARTERLY", "ANNUALLY", "AD_HOC"]
const DESIGN_CFG: Record<string, { label: string; color: string; bg: string }> = {
  NOT_ASSESSED: { label: "Design not assessed", color: "#475569", bg: "#F1F5F9" }, ADEQUATE: { label: "Design adequate", color: "#166534", bg: "#DCFCE7" },
  NEEDS_IMPROVEMENT: { label: "Design needs improvement", color: "#92400E", bg: "#FEF3C7" }, INADEQUATE: { label: "Design inadequate", color: "#991B1B", bg: "#FEE2E2" },
}
const STATUS_CFG: Record<string, { color: string; bg: string }> = { DRAFT: { color: "#475569", bg: "#F1F5F9" }, ACTIVE: { color: "#1D4ED8", bg: "#DBEAFE" }, RETIRED: { color: "#475569", bg: "#E2E8F0" } }
const EFF_CFG: Record<string, { label: string; color: string; bg: string }> = {
  NOT_TESTED: { label: "Not tested", color: "#92400E", bg: "#FEF3C7" }, EFFECTIVE: { label: "Effective", color: "#166534", bg: "#DCFCE7" },
  PARTIALLY_EFFECTIVE: { label: "Partially effective", color: "#C2410C", bg: "#FFEDD5" }, INEFFECTIVE: { label: "Ineffective", color: "#991B1B", bg: "#FEE2E2" },
}
const COV_CFG: Record<string, { label: string; color: string; bg: string }> = {
  UNCOVERED: { label: "No active control", color: "#991B1B", bg: "#FEE2E2" }, COVERED_UNTESTED: { label: "Covered — untested", color: "#92400E", bg: "#FEF3C7" },
  COVERED_EFFECTIVE: { label: "Covered — effective", color: "#166534", bg: "#DCFCE7" },
}
const pretty = (s: string) => s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, " ")
const base = "/api/v1/legalcompliance/controls"
const blank = { title: "", objective: "", procedure: "", controlType: "PREVENTIVE", nature: "MANUAL", frequency: "QUARTERLY", ownerName: "", expectedEvidence: "" }

export default function ControlsTab() {
  const qc = useQueryClient()
  const hasManage = usePermission("LEGALCOMPLIANCE_CONTROL_MANAGE")
  const hasAssess = usePermission("LEGALCOMPLIANCE_CONTROL_ASSESS")
  const hasAdmin = usePermission("LEGALCOMPLIANCE_ADMIN")
  const canManage = hasManage || hasAdmin
  const canAssess = hasAssess || hasAdmin

  const [view, setView] = useState<"CATALOGUE" | "COVERAGE">("CATALOGUE")
  const [showRetired, setShowRetired] = useState(false)
  const [expanded, setExpanded] = useState<string | null>(null)
  const [form, setForm] = useState<{ id: string | null; f: typeof blank } | null>(null)
  const [design, setDesign] = useState<Control | null>(null)
  const [dForm, setDForm] = useState({ rating: "ADEQUATE", note: "" })
  const [retire, setRetire] = useState<Control | null>(null)
  const [retireReason, setRetireReason] = useState("")
  const [apiError, setApiError] = useState("")
  const [notice, setNotice] = useState("")

  const { data: controls = [], isLoading } = useQuery<Control[]>({
    queryKey: ["lc-controls", showRetired], queryFn: async () => unwrap(await apiClient.get(base, { params: { includeRetired: showRetired } })),
  })
  const { data: coverage } = useQuery<Coverage>({ queryKey: ["lc-coverage"], queryFn: async () => one(await apiClient.get(`${base}/coverage`)) })

  const refresh = () => { qc.invalidateQueries({ queryKey: ["lc-controls"] }); qc.invalidateQueries({ queryKey: ["lc-coverage"] }); qc.invalidateQueries({ queryKey: ["lc-grc-links"] }); qc.invalidateQueries({ queryKey: ["lc-risks"] }); setApiError("") }
  const fail = (m: string) => (e: any) => setApiError(errMsg(e, m))
  const ok = (msg: string, close?: () => void) => () => { refresh(); close?.(); setNotice(msg) }

  const save = useMutation({
    mutationFn: ({ id, body }: { id: string | null; body: any }) => id ? apiClient.put(`${base}/${id}`, body) : apiClient.post(base, body),
    onSuccess: ok("Control saved", () => setForm(null)), onError: fail("Failed to save control"),
  })
  const activate = useMutation({ mutationFn: (id: string) => apiClient.post(`${base}/${id}/activate`), onSuccess: ok("Control activated — it still needs testing before it can be called effective"), onError: fail("Failed to activate") })
  const doDesign = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) => apiClient.post(`${base}/${id}/design-assessment`, body), onSuccess: ok("Design assessed", () => setDesign(null)), onError: fail("Failed to assess design"),
  })
  const doRetire = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) => apiClient.post(`${base}/${id}/retire`, { reason }), onSuccess: ok("Control retired", () => setRetire(null)), onError: fail("Failed to retire"),
  })

  const card = (label: string, v: number | undefined, color = "#0F172A") => (
    <div style={{ flex: 1, minWidth: 130, background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, padding: "12px 16px" }}>
      <div style={{ fontSize: 12, color: "#64748B", fontWeight: 600 }}>{label}</div><div style={{ fontSize: 24, fontWeight: 800, color }}>{v ?? "—"}</div>
    </div>
  )
  const set = (k: string, v: string) => form && setForm({ ...form, f: { ...form.f, [k]: v } })

  return (
    <div>
      <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 16 }}>
        {card("Obligations", coverage?.obligations)}{card("No active control", coverage?.uncovered, coverage?.uncovered ? "#991B1B" : undefined)}
        {card("Covered, untested", coverage?.coveredUntested, "#92400E")}{card("Covered, effective", coverage?.coveredEffective, "#166534")}
        {card("Risks without a control", coverage?.risksWithoutControl, coverage?.risksWithoutControl ? "#C2410C" : undefined)}
      </div>
      <div style={{ display: "flex", gap: 8, marginBottom: 14, alignItems: "center", flexWrap: "wrap" }}>
        {([["CATALOGUE", "Control catalogue"], ["COVERAGE", "Coverage gaps"]] as const).map(([v, l]) => (
          <button key={v} onClick={() => setView(v)} style={{ ...ghostBtn, background: view === v ? "#EEF2FF" : "#fff", color: view === v ? "#4338CA" : "#475569", borderColor: view === v ? "#C7D2FE" : "#E2E8F0" }}>{l}</button>
        ))}
        {view === "CATALOGUE" && canManage && <button style={primaryBtn} onClick={() => { setApiError(""); setForm({ id: null, f: blank }) }}><Plus size={14} style={{ verticalAlign: -2 }} /> New control</button>}
        {view === "CATALOGUE" && <label style={{ fontSize: 13, color: "#475569", display: "flex", gap: 6, alignItems: "center" }}><input type="checkbox" checked={showRetired} onChange={e => setShowRetired(e.target.checked)} /> Show retired</label>}
      </div>
      {notice && <div style={{ background: "#ECFDF5", border: "1px solid #A7F3D0", color: "#065F46", borderRadius: 8, padding: "8px 12px", fontSize: 13, marginBottom: 12 }}>{notice}</div>}
      {apiError && !form && !design && !retire && <Err m={apiError} />}

      {view === "CATALOGUE" && (isLoading ? <p style={{ color: "#64748B" }}>Loading…</p> : controls.length === 0 ? <p style={{ color: "#64748B", fontSize: 14 }}>No controls defined yet.</p> : controls.map(c => {
        const open = expanded === c.id, d = DESIGN_CFG[c.designRating], e = EFF_CFG[c.operatingEffectiveness]
        return (
          <div key={c.id} style={{ border: "1px solid #E2E8F0", borderRadius: 10, background: "#fff", marginBottom: 8 }}>
            <button onClick={() => setExpanded(open ? null : c.id)} style={{ all: "unset", cursor: "pointer", display: "flex", gap: 12, alignItems: "center", padding: "12px 16px", width: "calc(100% - 32px)" }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{c.reference} — {c.title}</div>
                <div style={{ fontSize: 12, color: "#64748B" }}>{pretty(c.controlType)} · {pretty(c.nature)} · {pretty(c.frequency)} · owner {c.ownerName} · {c.obligationCount} obligation(s) · {c.riskCount} risk(s)</div>
              </div>
              <Badge text={pretty(c.status)} {...STATUS_CFG[c.status]} /><Badge text={d.label} color={d.color} bg={d.bg} /><Badge text={c.testOverdue && c.operatingEffectiveness === "EFFECTIVE" ? "Effective — test overdue" : e.label} color={c.testOverdue && c.operatingEffectiveness === "EFFECTIVE" ? "#C2410C" : e.color} bg={c.testOverdue && c.operatingEffectiveness === "EFFECTIVE" ? "#FFEDD5" : e.bg} />
              {open ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
            </button>
            {open && (
              <div style={{ padding: "0 16px 14px", fontSize: 13, color: "#475569" }}>
                {c.objective && <p style={{ marginTop: 0 }}><b>Objective:</b> {c.objective}</p>}
                {c.procedure && <p><b>Procedure:</b> {c.procedure}</p>}
                {c.expectedEvidence && <p><b>Expected evidence:</b> {c.expectedEvidence}</p>}
                {c.designAssessedAt && <p><b>Design assessed</b> by {c.designAssessedByName} on {fmtDate(c.designAssessedAt)}{c.designNote ? `: ${c.designNote}` : ""}</p>}
                {c.lastTestedAt && <p><b>Last tested</b> {fmtDate(c.lastTestedAt)}{c.nextTestDue ? ` · next test due ${fmtDate(c.nextTestDue)}${c.testOverdue ? " (overdue)" : ""}` : ""}</p>}
                {c.status === "RETIRED" && <p><b>Retired:</b> {c.retireReason}</p>}
                <div style={{ display: "flex", gap: 8, flexWrap: "wrap", margin: "8px 0" }}>
                  {canManage && c.status === "DRAFT" && <button style={primaryBtn} disabled={activate.isPending} onClick={() => activate.mutate(c.id)}>Activate</button>}
                  {canAssess && c.status !== "RETIRED" && <button style={ghostBtn} onClick={() => { setApiError(""); setDForm({ rating: c.designRating === "NOT_ASSESSED" ? "ADEQUATE" : c.designRating, note: "" }); setDesign(c) }}>Assess design</button>}
                  {canManage && c.status !== "RETIRED" && <button style={ghostBtn} onClick={() => { setApiError(""); setForm({ id: c.id, f: { title: c.title, objective: c.objective ?? "", procedure: c.procedure ?? "", controlType: c.controlType, nature: c.nature, frequency: c.frequency, ownerName: c.ownerName, expectedEvidence: c.expectedEvidence ?? "" } }) }}>Edit</button>}
                  {canManage && c.status !== "RETIRED" && <button style={{ ...ghostBtn, color: "#991B1B" }} onClick={() => { setApiError(""); setRetireReason(""); setRetire(c) }}>Retire</button>}
                </div>
                {c.status === "DRAFT" && <p style={{ fontSize: 12, color: "#64748B" }}>To activate: state the objective, procedure and expected evidence, and link at least one obligation or risk.</p>}
                <GrcLinksPanel kind="CONTROL" id={c.id} canEdit={canManage} />
              </div>
            )}
          </div>
        )
      }))}

      {view === "COVERAGE" && coverage && (
        <div>
          <p style={{ fontSize: 13, color: "#64748B", marginTop: 0 }}>Active controls only. “Covered — untested” means a control is in operation but no reviewed test has shown it works.</p>
          {coverage.obligationRows.length === 0 ? <p style={{ color: "#64748B", fontSize: 14 }}>No obligations to cover.</p> : coverage.obligationRows.map(o => (
            <div key={o.obligationId} style={{ display: "flex", gap: 12, alignItems: "center", padding: "10px 14px", border: "1px solid #E2E8F0", borderRadius: 10, background: "#fff", marginBottom: 6 }}>
              <div style={{ flex: 1 }}><div style={{ fontWeight: 700, fontSize: 13, color: "#0F172A" }}>{o.title}</div><div style={{ fontSize: 12, color: "#64748B" }}>{o.regulationReference ?? "—"} · {o.category} · {o.activeControls} active control(s) · {o.risks} risk(s)</div></div>
              <Badge text={COV_CFG[o.state].label} color={COV_CFG[o.state].color} bg={COV_CFG[o.state].bg} />
            </div>
          ))}
          {coverage.unmitigatedRisks.length > 0 && <>
            <h4 style={{ margin: "18px 0 8px", fontSize: 14, color: "#0F172A" }}>Open risks with no active control</h4>
            {coverage.unmitigatedRisks.map(r => (
              <div key={r.riskId} style={{ padding: "8px 14px", border: "1px solid #E2E8F0", borderRadius: 10, background: "#fff", marginBottom: 6, fontSize: 13 }}>{r.reference} — {r.title}{r.residualRating ? ` · residual ${r.residualRating}` : " · not assessed"}</div>
            ))}
          </>}
          {coverage.draftControlsUnlinked > 0 && <p style={{ fontSize: 13, color: "#92400E" }}>{coverage.draftControlsUnlinked} draft control(s) are not linked to any risk or obligation.</p>}
        </div>
      )}

      {form && (
        <Modal title={form.id ? "Edit control" : "New control"} onClose={() => setForm(null)} wide>
          <Err m={apiError} />
          {form.id && <p style={{ fontSize: 12, color: "#92400E", marginTop: 0 }}>Changing the objective, procedure, type, nature or frequency resets the design assessment.</p>}
          <Field l="Title *"><input style={input} value={form.f.title} onChange={e => set("title", e.target.value)} /></Field>
          <Field l="Objective — what must this control achieve?"><textarea style={{ ...input, minHeight: 60 }} value={form.f.objective} onChange={e => set("objective", e.target.value)} /></Field>
          <Field l="Procedure — how is it performed?"><textarea style={{ ...input, minHeight: 60 }} value={form.f.procedure} onChange={e => set("procedure", e.target.value)} /></Field>
          <Field l="Expected evidence — what proves it was performed?"><textarea style={{ ...input, minHeight: 50 }} value={form.f.expectedEvidence} onChange={e => set("expectedEvidence", e.target.value)} /></Field>
          <div style={{ display: "flex", gap: 12 }}>
            <div style={{ flex: 1 }}><Field l="Type"><select style={input} value={form.f.controlType} onChange={e => set("controlType", e.target.value)}>{TYPES.map(t => <option key={t} value={t}>{pretty(t)}</option>)}</select></Field></div>
            <div style={{ flex: 1 }}><Field l="Nature"><select style={input} value={form.f.nature} onChange={e => set("nature", e.target.value)}>{NATURES.map(t => <option key={t} value={t}>{pretty(t)}</option>)}</select></Field></div>
            <div style={{ flex: 1 }}><Field l="Frequency"><select style={input} value={form.f.frequency} onChange={e => set("frequency", e.target.value)}>{FREQS.map(t => <option key={t} value={t}>{pretty(t)}</option>)}</select></Field></div>
          </div>
          <Field l="Control owner *"><input style={input} value={form.f.ownerName} onChange={e => set("ownerName", e.target.value)} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setForm(null)}>Cancel</button>
            <button style={primaryBtn} disabled={!form.f.title.trim() || !form.f.ownerName.trim() || save.isPending}
              onClick={() => save.mutate({ id: form.id, body: { ...form.f, objective: form.f.objective || null, procedure: form.f.procedure || null, expectedEvidence: form.f.expectedEvidence || null } })}>Save</button>
          </div>
        </Modal>
      )}

      {design && (
        <Modal title={`Assess design of ${design.reference}`} onClose={() => setDesign(null)}>
          <p style={{ fontSize: 12, color: "#64748B", marginTop: 0 }}>Is the control, as defined, capable of achieving its objective if it operates as described? This is not a test of whether it operates.</p>
          <Err m={apiError} />
          <Field l="Design rating *"><select style={input} value={dForm.rating} onChange={e => setDForm({ ...dForm, rating: e.target.value })}>
            {["ADEQUATE", "NEEDS_IMPROVEMENT", "INADEQUATE"].map(r => <option key={r} value={r}>{pretty(r)}</option>)}</select></Field>
          {dForm.rating !== "ADEQUATE" && <Field l="What is wrong? * (at least 15 characters)"><textarea style={{ ...input, minHeight: 70 }} value={dForm.note} onChange={e => setDForm({ ...dForm, note: e.target.value })} /></Field>}
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setDesign(null)}>Cancel</button>
            <button style={primaryBtn} disabled={(dForm.rating !== "ADEQUATE" && dForm.note.trim().length < 15) || doDesign.isPending}
              onClick={() => doDesign.mutate({ id: design.id, body: { rating: dForm.rating, note: dForm.note.trim() || null } })}>Save</button>
          </div>
        </Modal>
      )}

      {retire && (
        <Modal title={`Retire ${retire.reference}`} onClose={() => setRetire(null)}>
          <p style={{ fontSize: 12, color: "#64748B", marginTop: 0 }}>Its links are kept for history, but it stops counting towards coverage.</p>
          <Err m={apiError} />
          <Field l="Reason * (at least 15 characters)"><textarea style={{ ...input, minHeight: 70 }} value={retireReason} onChange={e => setRetireReason(e.target.value)} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setRetire(null)}>Cancel</button>
            <button style={{ ...primaryBtn, background: "#B91C1C" }} disabled={retireReason.trim().length < 15 || doRetire.isPending} onClick={() => doRetire.mutate({ id: retire.id, reason: retireReason.trim() })}>Retire</button>
          </div>
        </Modal>
      )}
    </div>
  )
}
