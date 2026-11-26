// src/pages/trainingprovider/TrainProvSitePermissionsPanel.tsx
//
// Who the provider has recorded as permitted to facilitate, assess or moderate at which delivery site. The provider's own record, beside the
// appointment checks. It is not a regulatory approval. Withdrawn with a reason, never deleted.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { ROLES, label, type Practitioner } from "./practitionerRules"
import {
  PERMISSION_NOTE, emptyPermission, periodText, permissionBody, permissionProblem, stateText, withdrawProblem, type PermissionForm, type SitePermission,
} from "./sitePermissionRules"
import { Badge, Field, Notice } from "./tpUi"
import { card, errText, ghost, input, muted, solid, todayIso, unwrap } from "./tpStyle"
import { LRN, PRC, useActiveProgrammes, useSites } from "./tpApi"

const API = `${LRN}/site-permissions`

function PermissionEditor({ onDone, onCancel }: { onDone: () => void; onCancel: () => void }) {
  const { data: sites = [] } = useSites()
  const programmes = useActiveProgrammes()
  const { data: people = [] } = useQuery<Practitioner[]>({ queryKey: ["tp-practitioners-active"], queryFn: async () => unwrap<Practitioner[]>(await apiClient.get(PRC, { params: { status: "ACTIVE" } })) })
  const [f, setF] = useState<PermissionForm>(emptyPermission(todayIso()))
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const save = async () => {
    const p = permissionProblem(f); if (p) { setError(p); return }
    setBusy(true); setError("")
    try { await apiClient.post(API, permissionBody(f)); onDone() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <div style={{ ...card, display: "grid", gap: 8 }}>
      <strong>Record a site permission</strong>
      <Field label="Person"><select aria-label="Person" style={input} value={f.practitionerId} onChange={e => setF({ ...f, practitionerId: e.target.value })}><option value="">Choose…</option>{people.map(p => <option key={p.id} value={p.id}>{p.firstNames} {p.surname} · {p.practitionerNo}</option>)}</select></Field>
      <Field label="Site"><select aria-label="Site" style={input} value={f.siteId} onChange={e => setF({ ...f, siteId: e.target.value })}><option value="">Choose…</option>{sites.filter(s => s.status === "ACTIVE").map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select></Field>
      <Field label="Role"><select aria-label="Role" style={input} value={f.role} onChange={e => setF({ ...f, role: e.target.value })}>{Object.entries(ROLES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></Field>
      <Field label="Programme (optional)" hint="Leave blank to cover any programme the person is appointed for."><select aria-label="Programme" style={input} value={f.programmeId} onChange={e => setF({ ...f, programmeId: e.target.value })}><option value="">Any programme</option>{(programmes.data ?? []).map(p => <option key={p.id} value={p.id}>{p.code} · {p.title}</option>)}</select></Field>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        <Field label="From"><input type="date" style={input} value={f.validFrom} onChange={e => setF({ ...f, validFrom: e.target.value })} /></Field>
        <Field label="To (optional)"><input type="date" style={input} value={f.validTo} onChange={e => setF({ ...f, validTo: e.target.value })} /></Field>
      </div>
      <Field label="Why is the person placed here?"><textarea style={input} rows={2} value={f.basis} onChange={e => setF({ ...f, basis: e.target.value })} /></Field>
      <Notice text={error} />
      <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={() => void save()}>Save permission</button><button style={ghost} onClick={onCancel}>Cancel</button></div>
    </div>
  )
}

function PermissionRow({ p, canManage, onChanged }: { p: SitePermission; canManage: boolean; onChanged: () => void }) {
  const [withdrawing, setWithdrawing] = useState(false)
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const state = stateText(p, todayIso())
  const send = async () => {
    const m = withdrawProblem(reason); if (m) { setError(m); return }
    setBusy(true); setError("")
    try { await apiClient.post(`${API}/${p.id}/withdraw`, { reason: reason.trim() }); setWithdrawing(false); setReason(""); onChanged() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <div style={{ ...card, display: "grid", gap: 4 }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
        <div>
          <strong>{p.practitionerName ?? "Person"}</strong> <span style={muted}>{p.practitionerNo}</span>
          <div style={muted}>{label(ROLES, p.role)} · {p.siteName ?? "—"} · {p.programmeTitle ?? "any programme"} · {periodText(p)}</div>
        </div>
        <Badge text={state} tone={state === "In force" ? "good" : state === "Withdrawn" ? "neutral" : "warn"} />
      </div>
      <div style={muted}>{p.basis}</div>
      {p.withdrawnReason && <div style={muted}>Withdrawn: {p.withdrawnReason}</div>}
      {canManage && p.status === "ACTIVE" && !withdrawing && <button style={{ ...ghost, justifySelf: "start" }} onClick={() => { setWithdrawing(true); setError("") }}>Withdraw</button>}
      {withdrawing && (
        <div style={{ display: "grid", gap: 6 }}>
          <Field label="Why is it withdrawn?"><input style={input} value={reason} onChange={e => setReason(e.target.value)} /></Field>
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={() => void send()}>Withdraw permission</button><button style={ghost} onClick={() => setWithdrawing(false)}>Cancel</button></div>
        </div>
      )}
    </div>
  )
}

export default function TrainProvSitePermissionsPanel() {
  const qc = useQueryClient()
  const canManage = usePermissions(["TRAININGPROVIDER_MANAGE", "TRAININGPROVIDER_ADMIN"])
  const { data: sites = [] } = useSites()
  const [siteId, setSiteId] = useState("")
  const [showWithdrawn, setShowWithdrawn] = useState(false)
  const [adding, setAdding] = useState(false)
  const { data = [], isLoading } = useQuery<SitePermission[]>({
    queryKey: ["tp-site-permissions", siteId, showWithdrawn],
    queryFn: async () => unwrap<SitePermission[]>(await apiClient.get(API, { params: { siteId: siteId || undefined, status: showWithdrawn ? undefined : "ACTIVE" } })),
  })
  const refresh = () => qc.invalidateQueries({ queryKey: ["tp-site-permissions"] })
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={muted}>{PERMISSION_NOTE}</div>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
        <select aria-label="Filter by site" style={{ ...input, maxWidth: 220 }} value={siteId} onChange={e => setSiteId(e.target.value)}><option value="">All sites</option>{sites.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select>
        <label style={{ fontSize: 13 }}><input type="checkbox" checked={showWithdrawn} onChange={e => setShowWithdrawn(e.target.checked)} /> Include withdrawn</label>
        {canManage && !adding && <button style={solid} onClick={() => setAdding(true)}>Record a permission</button>}
      </div>
      {adding && <PermissionEditor onDone={() => { setAdding(false); void refresh() }} onCancel={() => setAdding(false)} />}
      {isLoading ? <div style={muted}>Loading…</div> : data.length === 0 ? <div style={muted}>No site permissions recorded.</div> : data.map(p => <PermissionRow key={p.id} p={p} canManage={canManage} onChanged={() => void refresh()} />)}
    </div>
  )
}
