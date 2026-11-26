// src/pages/trainingprovider/TrainProvSamplingPanel.tsx
//
// The provider's own moderation sampling: the rules and their versions, and the draws made under them. A draw keeps the rule version,
// the seed, the whole eligible population and why each result was or was not picked. The moderation itself is recorded on each attempt.
// Nothing here says a sample meets any regulator's requirement.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { useAuthStore } from "../../store/auth.store"
import { TYPES } from "./assessmentRules"
import type { Cohort } from "./cohortRules"
import {
  BASES, DECISIONS, METHODS, OWN_RULE_NOTE, REASONS, SCOPES, STATUS, actionBody, actionProblem, closeProblem, describeVersion, drawBody, drawProblem, emptyAction, emptyClose,
  emptyDraw, emptyRule, emptySettings, reasonProblem, ruleBody, ruleProblem, settingsBody, settingsFromVersion, settingsProblem, type ActionForm, type CloseForm, type Draw,
  type DrawForm, type DrawRow, type Rule, type RuleForm, type SettingsForm,
} from "./samplingRules"
import { Badge, Field, Notice } from "./tpUi"
import { card, errText, ghost, input, muted, solid, todayIso, unwrap } from "./tpStyle"
import { LRN, useActiveProgrammes } from "./tpApi"

const API = `${LRN}/moderation-sampling`

function SettingsFields({ f, setF }: { f: SettingsForm; setF: (v: SettingsForm) => void }) {
  return (
    <div style={{ display: "grid", gap: 8 }}>
      <Field label="Starts on"><input type="date" style={input} value={f.effectiveFrom} onChange={e => setF({ ...f, effectiveFrom: e.target.value })} /></Field>
      <Field label="How many results"><select style={input} value={f.basis} onChange={e => setF({ ...f, basis: e.target.value })}>{Object.entries(BASES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></Field>
      {f.basis === "PERCENTAGE" && <Field label="Percentage"><input style={input} inputMode="decimal" value={f.percentage} onChange={e => setF({ ...f, percentage: e.target.value })} /></Field>}
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        <Field label="At least (optional)"><input style={input} inputMode="numeric" value={f.minCount} onChange={e => setF({ ...f, minCount: e.target.value })} /></Field>
        <Field label="At most (optional)"><input style={input} inputMode="numeric" value={f.maxCount} onChange={e => setF({ ...f, maxCount: e.target.value })} /></Field>
      </div>
      <Field label="How results are chosen"><select style={input} value={f.selectionMethod} onChange={e => setF({ ...f, selectionMethod: e.target.value })}>{Object.entries(METHODS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></Field>
      {f.selectionMethod === "RISK_BASED" && <Field label="Risk factors you look at" hint="Describe them in your own words. The draw scores each result on what the system knows: an assessor with a past moderation query, an assessor not yet moderated, and repeat attempts."><input style={input} value={f.riskFactors} onChange={e => setF({ ...f, riskFactors: e.target.value })} /></Field>}
      <label style={{ fontSize: 13 }}><input type="checkbox" checked={f.extra} onChange={e => setF({ ...f, extra: e.target.checked })} /> Sample more after moderation queries</label>
      {f.extra && (
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          <Field label="Queries that trigger it"><input style={input} inputMode="numeric" value={f.extraTriggerQueries} onChange={e => setF({ ...f, extraTriggerQueries: e.target.value })} /></Field>
          <Field label="Out of the last moderations"><input style={input} inputMode="numeric" value={f.extraLookback} onChange={e => setF({ ...f, extraLookback: e.target.value })} /></Field>
          <Field label="Extra percentage"><input style={input} inputMode="decimal" value={f.extraPercentage} onChange={e => setF({ ...f, extraPercentage: e.target.value })} /></Field>
        </div>
      )}
      <Field label="Why the rule is set this way"><textarea style={input} rows={2} value={f.rationale} onChange={e => setF({ ...f, rationale: e.target.value })} /></Field>
    </div>
  )
}

function RuleCard({ rule, canAdmin, onChanged }: { rule: Rule; canAdmin: boolean; onChanged: () => void }) {
  const [mode, setMode] = useState<"" | "version" | "retire" | "history">("")
  const [sf, setSf] = useState<SettingsForm>(emptySettings(todayIso()))
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true); setError("")
    try { await fn(); setMode(""); setReason(""); onChanged() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  const scope = rule.scopeType === "PROGRAMME" ? rule.programmeTitle : rule.scopeType === "COHORT" ? rule.cohortName : rule.scopeType === "ASSESSMENT_TYPE" ? TYPES[rule.assessmentType ?? ""] ?? rule.assessmentType : null
  return (
    <div style={{ ...card, display: "grid", gap: 6 }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
        <div><strong>{rule.name}</strong><div style={muted}>{rule.ruleNo} · {SCOPES[rule.scopeType] ?? rule.scopeType}{scope ? `: ${scope}` : ""}</div></div>
        <Badge text={STATUS[rule.status] ?? rule.status} tone={rule.status === "ACTIVE" ? "good" : "neutral"} />
      </div>
      {rule.current && <div style={{ fontSize: 13 }}>Version {rule.current.versionNo} from {rule.current.effectiveFrom}: {describeVersion(rule.current)}</div>}
      {rule.current && <div style={muted}>{rule.current.rationale}</div>}
      {rule.retiredReason && <div style={muted}>Retired: {rule.retiredReason}</div>}
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        <button style={ghost} onClick={() => setMode(mode === "history" ? "" : "history")}>{mode === "history" ? "Hide earlier versions" : `Versions (${rule.versions.length})`}</button>
        {canAdmin && rule.status === "ACTIVE" && <button style={ghost} onClick={() => { setMode("version"); setSf(rule.current ? settingsFromVersion(rule.current, todayIso()) : emptySettings(todayIso())); setError("") }}>New version</button>}
        {canAdmin && rule.status === "ACTIVE" && <button style={ghost} onClick={() => { setMode("retire"); setError("") }}>Retire</button>}
      </div>
      {mode === "history" && rule.versions.map(v => <div key={v.id} style={muted}>Version {v.versionNo} from {v.effectiveFrom}: {describeVersion(v)}. {v.rationale}</div>)}
      {mode === "version" && (
        <div style={{ display: "grid", gap: 8 }}>
          <div style={muted}>Earlier versions and the draws made under them stay as they were.</div>
          <SettingsFields f={sf} setF={setSf} />
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}>
            <button style={solid} disabled={busy} onClick={() => {
              const p = settingsProblem(sf, rule.current?.effectiveFrom ?? null); if (p) { setError(p); return }
              void run(() => apiClient.post(`${API}/rules/${rule.id}/versions`, settingsBody(sf)))
            }}>Save version</button>
            <button style={ghost} onClick={() => setMode("")}>Cancel</button>
          </div>
        </div>
      )}
      {mode === "retire" && (
        <div style={{ display: "grid", gap: 6 }}>
          <Field label="Why is it retired?"><input style={input} value={reason} onChange={e => setReason(e.target.value)} /></Field>
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}>
            <button style={solid} disabled={busy} onClick={() => { const p = reasonProblem(reason); if (p) { setError(p); return } void run(() => apiClient.post(`${API}/rules/${rule.id}/retire`, { reason: reason.trim() })) }}>Retire rule</button>
            <button style={ghost} onClick={() => setMode("")}>Cancel</button>
          </div>
        </div>
      )}
    </div>
  )
}

function RuleCreator({ onDone, onCancel }: { onDone: () => void; onCancel: () => void }) {
  const [f, setF] = useState<RuleForm>(emptyRule(todayIso()))
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const programmes = useActiveProgrammes()
  const cohorts = useQuery<Cohort[]>({ queryKey: ["tp-cohorts-all"], queryFn: async () => unwrap<Cohort[]>(await apiClient.get(`${LRN}/cohorts`)), enabled: f.scopeType === "COHORT" })
  const save = async () => {
    const p = ruleProblem(f); if (p) { setError(p); return }
    setBusy(true); setError("")
    try { await apiClient.post(`${API}/rules`, ruleBody(f)); onDone() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <div style={{ ...card, display: "grid", gap: 8 }}>
      <strong>New sampling rule</strong>
      <Field label="Name"><input style={input} value={f.name} onChange={e => setF({ ...f, name: e.target.value })} /></Field>
      <Field label="Applies to"><select style={input} value={f.scopeType} onChange={e => setF({ ...f, scopeType: e.target.value })}>{Object.entries(SCOPES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></Field>
      {f.scopeType === "PROGRAMME" && <Field label="Programme"><select style={input} value={f.programmeId} onChange={e => setF({ ...f, programmeId: e.target.value })}><option value="">Choose…</option>{(programmes.data ?? []).map(p => <option key={p.id} value={p.id}>{p.code} · {p.title}</option>)}</select></Field>}
      {f.scopeType === "COHORT" && <Field label="Cohort"><select style={input} value={f.cohortId} onChange={e => setF({ ...f, cohortId: e.target.value })}><option value="">Choose…</option>{(cohorts.data ?? []).map(c => <option key={c.id} value={c.id}>{c.cohortNo} · {c.name}</option>)}</select></Field>}
      {f.scopeType === "ASSESSMENT_TYPE" && <Field label="Assessment type"><select style={input} value={f.assessmentType} onChange={e => setF({ ...f, assessmentType: e.target.value })}><option value="">Choose…</option>{Object.entries(TYPES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></Field>}
      <SettingsFields f={f.settings} setF={s => setF({ ...f, settings: s })} />
      <Notice text={error} />
      <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={() => void save()}>Save rule</button><button style={ghost} onClick={onCancel}>Cancel</button></div>
    </div>
  )
}

function DrawDetail({ id, canManage, onBack }: { id: string; canManage: boolean; onBack: () => void }) {
  const qc = useQueryClient()
  const me = useAuthStore(s => s.user?.userId ?? null)
  const { data: d } = useQuery<Draw>({ queryKey: ["tp-sampling-draw", id], queryFn: async () => unwrap<Draw>(await apiClient.get(`${API}/draws/${id}`)) })
  const [closing, setClosing] = useState(false)
  const [cf, setCf] = useState<CloseForm>(emptyClose())
  const [adding, setAdding] = useState(false)
  const [af, setAf] = useState<ActionForm>(emptyAction())
  const [voiding, setVoiding] = useState(false)
  const [reason, setReason] = useState("")
  const [showAll, setShowAll] = useState(false)
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const [closeNote, setCloseNote] = useState<Record<string, string>>({})
  const refresh = () => Promise.all([qc.invalidateQueries({ queryKey: ["tp-sampling-draw", id] }), qc.invalidateQueries({ queryKey: ["tp-sampling-draws"] })])
  const run = async (fn: () => Promise<unknown>, after?: () => void) => {
    setBusy(true); setError("")
    try { await fn(); after?.(); await refresh() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  if (!d) return <div style={muted}>Loading…</div>
  const picked = d.items.filter(i => i.selected)
  const shown = showAll ? d.items : picked
  const open = d.status === "OPEN"
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <button style={{ ...ghost, justifySelf: "start" }} onClick={onBack}>Back to draws</button>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
        <div><strong>{d.drawNo} · {d.ruleName}</strong><div style={muted}>Version {d.versionNo} · decided {d.decidedFrom} to {d.decidedTo} · drawn {d.drawnOn} · seed {d.seed}</div></div>
        <Badge text={STATUS[d.status] ?? d.status} tone={d.status === "CLOSED" ? "good" : d.status === "VOID" ? "neutral" : "warn"} />
      </div>
      <div style={{ ...card, fontSize: 13 }}>{d.ruleSnapshot}</div>
      <div style={muted}>{d.eligibleCount} eligible · {d.requiredCount} asked for · {d.selectedCount} picked · {d.moderatedCount} moderated</div>
      {d.summary && <div style={{ fontSize: 13 }}>{DECISIONS[d.decision ?? ""] ?? d.decision}: {d.decisionNote} <span style={muted}>({d.summary})</span></div>}
      {d.voidReason && <div style={muted}>Voided: {d.voidReason}</div>}
      <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
        <strong>{showAll ? "Everything in the population" : "Picked results"}</strong>
        <button style={ghost} onClick={() => setShowAll(!showAll)}>{showAll ? "Show picked only" : `Show all ${d.items.length}`}</button>
      </div>
      {shown.map(i => (
        <div key={i.id} style={{ ...card, display: "grid", gap: 2 }}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
            <div>{i.learnerName ?? "Learner"} · {i.instrumentTitle}<div style={muted}>{i.attemptNo} · assessor {i.assessorName ?? "—"} · {i.selected ? REASONS[i.selectedReason ?? ""] ?? i.selectedReason : "Not picked"} · risk score {i.riskScore}{i.riskFactors ? ` (${i.riskFactors})` : ""}</div></div>
            {i.selected && (i.moderationNo ? <Badge text={i.moderationOutcome === "QUERIED" ? (i.moderationResolved ? "Query answered" : "Query open") : "Confirmed"} tone={i.moderationOutcome === "QUERIED" && !i.moderationResolved ? "warn" : "good"} /> : <Badge text="Not moderated yet" tone="neutral" />)}
          </div>
          {i.targetNote && <div style={muted}>Chosen on purpose: {i.targetNote}</div>}
        </div>
      ))}
      <div style={muted}>Moderate each picked result from the Moderation queue or the attempt itself. The draw only records which ones to moderate.</div>
      <strong>Corrective actions</strong>
      {d.actions.length === 0 ? <div style={muted}>None recorded.</div> : d.actions.map(a => (
        <div key={a.id} style={{ ...card, display: "grid", gap: 4 }}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
            <div>{a.description}<div style={muted}>{a.ownerName}{a.dueOn ? ` · due ${a.dueOn}` : ""}</div></div>
            <Badge text={a.status === "OPEN" ? "Open" : a.status === "DONE" ? "Done" : "Cancelled"} tone={a.status === "OPEN" ? "warn" : a.status === "DONE" ? "good" : "neutral"} />
          </div>
          {a.closedNote && <div style={muted}>{a.closedNote}</div>}
          {canManage && a.status === "OPEN" && (
            <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
              <input style={{ ...input, maxWidth: 320 }} placeholder="Note (at least 10 characters)" value={closeNote[a.id] ?? ""} onChange={e => setCloseNote({ ...closeNote, [a.id]: e.target.value })} />
              {[true, false].map(done => (
                <button key={String(done)} style={ghost} disabled={busy} onClick={() => {
                  const p = reasonProblem(closeNote[a.id] ?? ""); if (p) { setError(p); return }
                  void run(() => apiClient.post(`${API}/actions/${a.id}/close`, { done, note: (closeNote[a.id] ?? "").trim() }))
                }}>{done ? "Mark done" : "Cancel action"}</button>
              ))}
            </div>
          )}
        </div>
      ))}
      {canManage && d.status !== "VOID" && !adding && <button style={{ ...ghost, justifySelf: "start" }} onClick={() => { setAdding(true); setAf(emptyAction()); setError("") }}>Add a corrective action</button>}
      {adding && (
        <div style={{ ...card, display: "grid", gap: 6 }}>
          <Field label="What needs to happen"><textarea style={input} rows={2} value={af.description} onChange={e => setAf({ ...af, description: e.target.value })} /></Field>
          <Field label="Who owns it"><input style={input} value={af.ownerName} onChange={e => setAf({ ...af, ownerName: e.target.value })} /></Field>
          <Field label="Due on (optional)"><input type="date" style={input} value={af.dueOn} onChange={e => setAf({ ...af, dueOn: e.target.value })} /></Field>
          <div style={{ display: "flex", gap: 8 }}>
            <button style={solid} disabled={busy} onClick={() => { const p = actionProblem(af); if (p) { setError(p); return } void run(() => apiClient.post(`${API}/draws/${id}/actions`, actionBody(af)), () => setAdding(false)) }}>Save action</button>
            <button style={ghost} onClick={() => setAdding(false)}>Cancel</button>
          </div>
        </div>
      )}
      {canManage && open && !closing && !voiding && (
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          <button style={solid} onClick={() => { setClosing(true); setCf(emptyClose()); setError("") }}>Close draw</button>
          <button style={ghost} onClick={() => { setVoiding(true); setError("") }}>Void draw</button>
        </div>
      )}
      {closing && (
        <div style={{ ...card, display: "grid", gap: 6 }}>
          <Field label="Decision"><select style={input} value={cf.decision} onChange={e => setCf({ ...cf, decision: e.target.value })}>{Object.entries(DECISIONS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></Field>
          <Field label="Reason for the decision"><textarea style={input} rows={2} value={cf.note} onChange={e => setCf({ ...cf, note: e.target.value })} /></Field>
          {me && d.drawnBy === me && <label style={{ fontSize: 13 }}><input type="checkbox" checked={cf.confirmAlone} onChange={e => setCf({ ...cf, confirmAlone: e.target.checked })} /> I drew this sample and I am closing it myself</label>}
          <div style={{ display: "flex", gap: 8 }}>
            <button style={solid} disabled={busy} onClick={() => { const p = closeProblem(cf, d, me); if (p) { setError(p); return } void run(() => apiClient.post(`${API}/draws/${id}/close`, cf), () => setClosing(false)) }}>Close draw</button>
            <button style={ghost} onClick={() => setClosing(false)}>Cancel</button>
          </div>
        </div>
      )}
      {voiding && (
        <div style={{ ...card, display: "grid", gap: 6 }}>
          <Field label="Why is it voided?" hint="Only possible while no picked result has been moderated."><input style={input} value={reason} onChange={e => setReason(e.target.value)} /></Field>
          <div style={{ display: "flex", gap: 8 }}>
            <button style={solid} disabled={busy} onClick={() => { const p = reasonProblem(reason); if (p) { setError(p); return } void run(() => apiClient.post(`${API}/draws/${id}/void`, { reason: reason.trim() }), () => setVoiding(false)) }}>Void draw</button>
            <button style={ghost} onClick={() => setVoiding(false)}>Cancel</button>
          </div>
        </div>
      )}
      <Notice text={error} />
    </div>
  )
}

export default function TrainProvSamplingPanel() {
  const qc = useQueryClient()
  const canManage = usePermissions(["TRAININGPROVIDER_MANAGE", "TRAININGPROVIDER_ADMIN"])
  const canAdmin = usePermissions(["TRAININGPROVIDER_ADMIN"])
  const rules = useQuery<Rule[]>({ queryKey: ["tp-sampling-rules"], queryFn: async () => unwrap<Rule[]>(await apiClient.get(`${API}/rules`)) })
  const draws = useQuery<DrawRow[]>({ queryKey: ["tp-sampling-draws"], queryFn: async () => unwrap<DrawRow[]>(await apiClient.get(`${API}/draws`)) })
  const [creating, setCreating] = useState(false)
  const [drawing, setDrawing] = useState(false)
  const [df, setDf] = useState<DrawForm>(emptyDraw(todayIso()))
  const [openId, setOpenId] = useState<string | null>(null)
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const live = (rules.data ?? []).filter(r => r.status === "ACTIVE")
  const refreshRules = () => qc.invalidateQueries({ queryKey: ["tp-sampling-rules"] })
  if (openId) return <DrawDetail id={openId} canManage={canManage} onBack={() => setOpenId(null)} />
  const makeDraw = async () => {
    const p = drawProblem(df, todayIso()); if (p) { setError(p); return }
    setBusy(true); setError("")
    try {
      const d = unwrap<Draw>(await apiClient.post(`${API}/draws`, drawBody(df)))
      setDrawing(false); await qc.invalidateQueries({ queryKey: ["tp-sampling-draws"] }); setOpenId(d.id)
    } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <div style={{ display: "grid", gap: 12 }}>
      <div style={muted}>{OWN_RULE_NOTE}</div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
        <strong>Draws</strong>
        {canManage && !drawing && <button style={solid} onClick={() => { setDrawing(true); setDf(emptyDraw(todayIso())); setError("") }}>Make a draw</button>}
      </div>
      {drawing && (
        <div style={{ ...card, display: "grid", gap: 8 }}>
          <Field label="Rule"><select style={input} value={df.ruleId} onChange={e => setDf({ ...df, ruleId: e.target.value })}><option value="">Choose…</option>{live.map(r => <option key={r.id} value={r.id}>{r.ruleNo} · {r.name}</option>)}</select></Field>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <Field label="Decisions made from"><input type="date" style={input} value={df.decidedFrom} onChange={e => setDf({ ...df, decidedFrom: e.target.value })} /></Field>
            <Field label="to"><input type="date" style={input} value={df.decidedTo} onChange={e => setDf({ ...df, decidedTo: e.target.value })} /></Field>
          </div>
          <div style={muted}>The draw uses the version of the rule in force today and keeps a copy of its settings. Results already moderated, or already in another draw, are left out.</div>
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={() => void makeDraw()}>Draw</button><button style={ghost} onClick={() => setDrawing(false)}>Cancel</button></div>
        </div>
      )}
      {draws.isLoading ? <div style={muted}>Loading…</div> : (draws.data ?? []).length === 0 ? <div style={muted}>No draws yet.</div> : (draws.data ?? []).map(d => (
        <button key={d.id} onClick={() => setOpenId(d.id)} style={{ ...card, textAlign: "left", background: "transparent", cursor: "pointer", color: "inherit" }}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
            <div><strong>{d.drawNo} · {d.ruleName}</strong><div style={muted}>Version {d.versionNo} · decided {d.decidedFrom} to {d.decidedTo} · {d.selectedCount} picked of {d.eligibleCount}</div></div>
            <Badge text={d.decision ? DECISIONS[d.decision] ?? d.decision : STATUS[d.status] ?? d.status} tone={d.status === "CLOSED" ? "good" : d.status === "VOID" ? "neutral" : "warn"} />
          </div>
        </button>
      ))}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
        <strong>Rules</strong>
        {canAdmin && !creating && <button style={solid} onClick={() => setCreating(true)}>New rule</button>}
      </div>
      {creating && <RuleCreator onDone={() => { setCreating(false); void refreshRules() }} onCancel={() => setCreating(false)} />}
      {rules.isLoading ? <div style={muted}>Loading…</div> : (rules.data ?? []).length === 0 ? <div style={muted}>No sampling rules yet. Nothing is moderated by sampling until you write one.</div> : (rules.data ?? []).map(r => <RuleCard key={r.id} rule={r} canAdmin={canAdmin} onChanged={() => void refreshRules()} />)}
    </div>
  )
}
