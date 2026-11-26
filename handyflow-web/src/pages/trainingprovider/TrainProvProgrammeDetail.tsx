// One programme: versions with their modules, offerings per site, and the honest accredited label.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { coverageLabel } from "./accreditationRules"
import {
  ACTIVITIES, DELIVERY_MODES, MODULE_TYPES, PROGRAMME_TYPES, accreditedClaim, creditTotal, emptyModule, emptyVersion, label, moduleBody, moduleProblem,
  reasonProblem, statusLabel, versionBody, versionProblem, type ModuleForm, type ProgrammeDetail, type ProgrammeVersion, type VersionForm,
} from "./catalogueRules"
import { Badge, Field, Notice } from "./tpUi"
import { errText, fmtDate, ghost, input, muted, solid, card, unwrap } from "./tpStyle"
import { CAT, useSites } from "./tpApi"
import TrainProvProgrammeHours from "./TrainProvProgrammeHours"
import TrainProvProgrammeNeeds from "./TrainProvProgrammeNeeds"


export default function TrainProvProgrammeDetail({ id, canManage, isAdmin, onBack }: { id: string; canManage: boolean; isAdmin: boolean; onBack: () => void }) {
  const qc = useQueryClient()
  const { data: sites = [] } = useSites()
  const { data, isLoading } = useQuery<ProgrammeDetail>({ queryKey: ["tp-programme", id], queryFn: async () => unwrap<ProgrammeDetail>(await apiClient.get(`${CAT}/programmes/${id}`)) })
  const [panel, setPanel] = useState("")
  const [vf, setVf] = useState<VersionForm>(emptyVersion)
  const [editingVersion, setEditingVersion] = useState<string | null>(null)
  const [mf, setMf] = useState<ModuleForm>(emptyModule)
  const [siteId, setSiteId] = useState("")
  const [reason, setReason] = useState("")
  const [override, setOverride] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const refresh = () => { qc.invalidateQueries({ queryKey: ["tp-programme", id] }); qc.invalidateQueries({ queryKey: ["tp-programmes"] }) }
  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true); setError("")
    try { await fn(); setPanel(""); setReason(""); setOverride(""); setEditingVersion(null); refresh() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  if (isLoading || !data) return <div style={muted}>Loading…</div>
  const p = data.programme
  const accredited = p.trainingActivity === "ACCREDITED"
  const hasDraft = data.versions.some(v => v.status === "DRAFT")
  const withReason = (fn: (r: string) => Promise<unknown>) => { const m = reasonProblem(reason); if (m) setError(m); else run(() => fn(reason.trim())) }
  const openVersion = (v?: ProgrammeVersion) => {
    setPanel(v ? `edit:${v.id}` : "version"); setError(""); setEditingVersion(v?.id ?? null)
    setVf(v ? { qualificationVersionId: v.qualificationVersionId ?? "", learningHours: v.learningHours?.toString() ?? "", credits: v.credits?.toString() ?? "", summary: v.summary ?? "", effectiveFrom: v.effectiveFrom ?? "" } : emptyVersion)
  }
  const saveVersion = () => {
    const m = versionProblem(vf); if (m) { setError(m); return }
    run(() => (editingVersion ? apiClient.put(`${CAT}/programme-versions/${editingVersion}`, versionBody(vf)) : apiClient.post(`${CAT}/programmes/${id}/versions`, versionBody(vf))))
  }
  const saveModule = (versionId: string) => { const m = moduleProblem(mf); if (m) { setError(m); return } run(() => apiClient.post(`${CAT}/programme-versions/${versionId}/modules`, moduleBody(mf))) }
  const versionForm = (
    <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
      <input aria-label="Learning hours" style={input} placeholder="Learning hours" value={vf.learningHours} onChange={e => setVf({ ...vf, learningHours: e.target.value })} />
      <input aria-label="Version credits" style={input} placeholder="Credits" value={vf.credits} onChange={e => setVf({ ...vf, credits: e.target.value })} />
      <textarea aria-label="Summary" style={{ ...input, minHeight: 50 }} placeholder="Summary of this version" value={vf.summary} onChange={e => setVf({ ...vf, summary: e.target.value })} />
      <Field label="Effective from"><input aria-label="Effective from" type="date" style={input} value={vf.effectiveFrom} onChange={e => setVf({ ...vf, effectiveFrom: e.target.value })} /></Field>
      <Notice text={error} />
      <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={saveVersion}>{editingVersion ? "Save version" : "Add version"}</button><button style={ghost} onClick={() => setPanel("")}>Cancel</button></div>
    </div>
  )

  return (
    <div style={{ display: "grid", gap: 12 }}>
      <div><button style={ghost} onClick={onBack}>← All programmes</button></div>
      <div style={card}>
        <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
          <div><strong style={{ fontSize: 16 }}>{p.title}</strong><div style={muted}>{p.code} · {label(PROGRAMME_TYPES, p.programmeType)} · {label(DELIVERY_MODES, p.deliveryMode)}</div></div>
          <Badge text={statusLabel(p.status)} tone={p.status === "ACTIVE" ? "good" : "neutral"} />
        </div>
        <div style={{ marginTop: 6 }}><Badge text={accreditedClaim(p)} tone={accredited ? (p.advertisableAsAccredited ? "good" : "warn") : "neutral"} /></div>
        <div style={{ ...muted, marginTop: 6 }}>{label(ACTIVITIES, p.trainingActivity)}{p.qualificationTitle ? ` · ${p.qualificationTitle}` : ""} · {p.approvedVersionNo ? `version ${p.approvedVersionNo} approved` : "no approved version yet"} · {p.liveOfferings} live offering{p.liveOfferings === 1 ? "" : "s"}</div>
        {p.description && <div style={{ fontSize: 13, marginTop: 6 }}>{p.description}</div>}
        {p.retiredReason && <div style={muted}>Retired: {p.retiredReason}</div>}
        {canManage && p.status !== "RETIRED" && (
          <div style={{ display: "flex", gap: 8, marginTop: 10, flexWrap: "wrap" }}>
            {p.status === "DRAFT" && isAdmin && <button style={solid} disabled={busy} onClick={() => run(() => apiClient.post(`${CAT}/programmes/${id}/activate`))}>Activate</button>}
            {!hasDraft && <button style={ghost} onClick={() => openVersion()}>New version</button>}
            {isAdmin && <button style={ghost} onClick={() => { setPanel("retire"); setError("") }}>Retire</button>}
          </div>
        )}
        {p.status === "DRAFT" && !isAdmin && <div style={muted}>An administrator activates a programme once it has an approved version.</div>}
      </div>
      {panel === "retire" && (
        <div style={{ ...card, display: "grid", gap: 6, background: "var(--hf-surface-muted)" }}>
          <input aria-label="Retire reason" style={input} placeholder="Why is this programme being retired? Nothing is deleted" value={reason} onChange={e => setReason(e.target.value)} />
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={() => withReason(r => apiClient.post(`${CAT}/programmes/${id}/retire`, { reason: r }))}>Retire programme</button><button style={ghost} onClick={() => setPanel("")}>Cancel</button></div>
        </div>
      )}
      {panel === "version" && versionForm}

      <strong>Versions</strong>
      {data.versions.length === 0 ? <div style={muted}>No versions yet. Add a version, list its modules, then an administrator approves it.</div> : data.versions.map(v => {
        const totals = creditTotal(v.modules)
        return (
          <div key={v.id} style={card}>
            <div style={{ display: "flex", justifyContent: "space-between", gap: 8, alignItems: "center" }}>
              <div><strong>Version {v.versionNo}</strong> <span style={muted}>{v.learningHours != null ? `${v.learningHours} hours` : "hours not set"} · {v.credits != null ? `${v.credits} credits` : "credits not set"} · from {fmtDate(v.effectiveFrom)}</span></div>
              <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                <Badge text={statusLabel(v.status)} tone={v.status === "APPROVED" ? "good" : "neutral"} />
                {canManage && v.status === "DRAFT" && <button style={ghost} onClick={() => openVersion(v)}>Edit</button>}
                {isAdmin && v.status === "DRAFT" && <button style={solid} disabled={busy} onClick={() => { setPanel(`approve:${v.id}`); setError("") }}>Approve</button>}
              </div>
            </div>
            {v.summary && <div style={{ fontSize: 13 }}>{v.summary}</div>}
            {v.creditsNote && <div style={muted}>{v.creditsNote}</div>}
            {v.approvalOverrideReason && <div style={muted}>Approved without cover because: {v.approvalOverrideReason}</div>}
            <TrainProvProgrammeHours versionId={v.id} canManage={canManage} />
            <TrainProvProgrammeNeeds versionId={v.id} canManage={canManage} />
            {panel === `edit:${v.id}` && versionForm}
            {panel === `approve:${v.id}` && (
              <div style={{ display: "grid", gap: 6, marginTop: 8 }}>
                <div style={muted}>Approving makes this the version in use and supersedes the previous one. For an accredited programme the server checks the qualification and cover first. If cover is missing and you must go ahead, say why below.</div>
                <input aria-label="Approval override reason" style={input} placeholder="Only if cover is missing: why you are approving anyway" value={override} onChange={e => setOverride(e.target.value)} />
                <Notice text={error} />
                <div style={{ display: "flex", gap: 8 }}>
                  <button style={solid} disabled={busy} onClick={() => run(() => apiClient.post(`${CAT}/programme-versions/${v.id}/approve`, override.trim() ? { overrideReason: override.trim() } : {}))}>Approve version</button>
                  <button style={ghost} onClick={() => setPanel("")}>Cancel</button>
                </div>
              </div>
            )}
            <div style={{ marginTop: 8 }}>
              {v.modules.length === 0 ? <div style={muted}>No modules listed.</div> : v.modules.map(m => (
                <div key={m.id} style={{ fontSize: 13, display: "flex", justifyContent: "space-between", gap: 8, padding: "3px 0" }}>
                  <span>{m.seq}. {m.title} <span style={muted}>{label(MODULE_TYPES, m.moduleType)}{m.required ? "" : " · optional"}{m.credits != null ? ` · ${m.credits} cr` : ""}{m.notionalHours != null ? ` · ${m.notionalHours} h` : ""}</span></span>
                  {canManage && v.status === "DRAFT" && <button style={ghost} onClick={() => run(() => apiClient.delete(`${CAT}/modules/${m.id}`))}>Remove</button>}
                </div>
              ))}
              {v.modules.length > 0 && <div style={muted}>Module credits: {totals.all} in total, {totals.required} required.</div>}
            </div>
            {canManage && v.status === "DRAFT" && panel !== `module:${v.id}` && <div style={{ marginTop: 6 }}><button style={ghost} onClick={() => { setPanel(`module:${v.id}`); setMf(emptyModule); setError("") }}>Add a module</button></div>}
            {panel === `module:${v.id}` && (
              <div style={{ display: "grid", gap: 6, marginTop: 8 }}>
                <input aria-label="Module title" style={input} placeholder="Module title" value={mf.title} onChange={e => setMf({ ...mf, title: e.target.value })} />
                <select aria-label="Module type" style={input} value={mf.moduleType} onChange={e => setMf({ ...mf, moduleType: e.target.value })}>{Object.entries(MODULE_TYPES).map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select>
                <input aria-label="Module credits" style={input} placeholder="Credits" value={mf.credits} onChange={e => setMf({ ...mf, credits: e.target.value })} />
                <input aria-label="Notional hours" style={input} placeholder="Notional hours" value={mf.notionalHours} onChange={e => setMf({ ...mf, notionalHours: e.target.value })} />
                <label style={{ fontSize: 13 }}><input type="checkbox" checked={mf.required} onChange={e => setMf({ ...mf, required: e.target.checked })} /> Required</label>
                <textarea aria-label="Learning outcomes" style={{ ...input, minHeight: 50 }} placeholder="Learning outcomes" value={mf.learningOutcomes} onChange={e => setMf({ ...mf, learningOutcomes: e.target.value })} />
                <Notice text={error} />
                <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={() => saveModule(v.id)}>Add module</button><button style={ghost} onClick={() => setPanel("")}>Cancel</button></div>
              </div>
            )}
          </div>
        )
      })}

      <strong>Offerings <span style={muted}>(where it is delivered)</span></strong>
      {canManage && p.status === "ACTIVE" && panel !== "offering" && <div><button style={ghost} onClick={() => { setPanel("offering"); setSiteId(""); setError("") }}>Offer at a site</button></div>}
      {panel === "offering" && (
        <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
          <select aria-label="Offering site" style={input} value={siteId} onChange={e => setSiteId(e.target.value)}><option value="">Choose a site</option>{sites.filter(s => s.status === "ACTIVE").map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select>
          {accredited && <div style={muted}>An accredited programme can only be offered at a site where an approved accreditation covers it.</div>}
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy || !siteId} onClick={() => run(() => apiClient.post(`${CAT}/programmes/${id}/offerings`, { siteId }))}>Add offering</button><button style={ghost} onClick={() => setPanel("")}>Cancel</button></div>
        </div>
      )}
      {data.offerings.length === 0 ? <div style={muted}>Not offered anywhere yet.</div> : data.offerings.map(o => (
        <div key={o.id} style={card}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, alignItems: "center" }}>
            <div><strong>{o.siteName}</strong> <span style={muted}>from {fmtDate(o.offeredFrom)}</span></div>
            <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
              <Badge text={o.status === "ACTIVE" ? "Offered" : "Withdrawn"} tone={o.status === "ACTIVE" ? "good" : "neutral"} />
              {accredited && o.status === "ACTIVE" && <Badge text={coverageLabel(o.coverageOutcome)} tone={o.covered ? "good" : "bad"} />}
              {canManage && o.status === "ACTIVE" && <button style={ghost} onClick={() => { setPanel(`withdraw:${o.id}`); setError("") }}>Withdraw</button>}
            </div>
          </div>
          {accredited && o.status === "ACTIVE" && !o.covered && <div style={muted}>{o.coverageMessage}</div>}
          {o.withdrawnReason && <div style={muted}>Withdrawn: {o.withdrawnReason}</div>}
          {panel === `withdraw:${o.id}` && (
            <div style={{ display: "grid", gap: 6, marginTop: 8 }}>
              <input aria-label="Withdraw offering reason" style={input} placeholder="Why is it being withdrawn?" value={reason} onChange={e => setReason(e.target.value)} />
              <Notice text={error} />
              <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={() => withReason(r => apiClient.post(`${CAT}/offerings/${o.id}/withdraw`, { reason: r }))}>Withdraw</button><button style={ghost} onClick={() => setPanel("")}>Cancel</button></div>
            </div>
          )}
        </div>
      ))}
    </div>
  )
}
