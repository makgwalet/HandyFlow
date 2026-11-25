// Report obligations set up by the provider. Each one carries a written source note. Retired obligations stay listed.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { CADENCES, REPORT_NOTE, emptyObligation, label, obligationBody, obligationForm, obligationProblem, reasonProblem, updateBody, type Obligation, type ObligationForm } from "./reportRules"
import { Badge, Notice } from "./tpUi"
import { card, errText, ghost, input, muted, solid, unwrap } from "./tpStyle"
import { RPT, useActiveProgrammes, useAuthorities } from "./tpApi"

function ObligationFormPanel({ initial, onSaved, onCancel }: { initial?: Obligation; onSaved: () => void; onCancel: () => void }) {
  const { data: authorities = [] } = useAuthorities()
  const { data: programmes = [] } = useActiveProgrammes()
  const [f, setF] = useState<ObligationForm>(initial ? obligationForm(initial) : emptyObligation)
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const set = (k: keyof ObligationForm) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => setF({ ...f, [k]: e.target.value })
  const save = async () => {
    const p = obligationProblem(f, !initial); if (p) { setError(p); return }
    setBusy(true); setError("")
    try { await (initial ? apiClient.put(`${RPT}/report-obligations/${initial.id}`, updateBody(f)) : apiClient.post(`${RPT}/report-obligations`, obligationBody(f))); onSaved() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
      {!initial && (
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
          <select aria-label="Authority" style={input} value={f.authorityId} onChange={set("authorityId")}>
            <option value="">Choose the authority</option>{authorities.filter(a => a.status === "ACTIVE").map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
          </select>
          <select aria-label="Programme" style={input} value={f.programmeId} onChange={set("programmeId")}>
            <option value="">All programmes</option>{programmes.map(p => <option key={p.id} value={p.id}>{p.code} · {p.title}</option>)}
          </select>
        </div>
      )}
      <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr 1fr", gap: 8 }}>
        <input aria-label="Obligation name" style={input} placeholder="Name of the report" value={f.name} onChange={set("name")} />
        <select aria-label="Cadence" style={input} value={f.cadence} onChange={set("cadence")}>{Object.entries(CADENCES).map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select>
        <input aria-label="Days after the period ends" style={input} inputMode="numeric" placeholder="Days after period end" value={f.dueDays} onChange={set("dueDays")} />
      </div>
      <textarea aria-label="Where this rule came from" style={input} placeholder="Where this rule came from (the notice, letter or guide you took it from)" value={f.sourceNote} onChange={set("sourceNote")} />
      <Notice text={error} />
      <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={save}>{initial ? "Save changes" : "Add obligation"}</button><button style={ghost} onClick={onCancel}>Cancel</button></div>
    </div>
  )
}

function ObligationCard({ o, onChanged }: { o: Obligation; onChanged: () => void }) {
  const canManage = usePermissions(["TRAININGPROVIDER_MANAGE", "TRAININGPROVIDER_ADMIN"])
  const [mode, setMode] = useState<"" | "edit" | "retire">("")
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const close = () => { setMode(""); setReason(""); setError("") }
  const retire = async () => {
    const p = reasonProblem(reason); if (p) { setError(p); return }
    setBusy(true); setError("")
    try { await apiClient.post(`${RPT}/report-obligations/${o.id}/retire`, { reason: reason.trim() }); close(); onChanged() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  if (mode === "edit") return <ObligationFormPanel initial={o} onCancel={close} onSaved={() => { close(); onChanged() }} />
  return (
    <div style={card}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
        <div>
          <strong>{o.name}</strong>
          <div style={muted}>{o.obligationNo} · {o.authorityName ?? "Authority"}{o.programmeTitle ? ` · ${o.programmeTitle}` : " · all programmes"}</div>
          <div style={muted}>{label(CADENCES, o.cadence)} · due {o.dueDaysAfterEnd} days after the period ends</div>
        </div>
        <Badge text={o.status === "ACTIVE" ? "Active" : "Retired"} tone={o.status === "ACTIVE" ? "good" : "neutral"} />
      </div>
      <div style={muted}>Source: {o.sourceNote}</div>
      {o.retiredReason && <div style={muted}>Retired: {o.retiredReason}</div>}
      {canManage && o.status === "ACTIVE" && mode === "" && (
        <div style={{ display: "flex", gap: 8, marginTop: 8 }}><button style={ghost} onClick={() => setMode("edit")}>Edit</button><button style={ghost} onClick={() => setMode("retire")}>Retire</button></div>
      )}
      {mode === "retire" && (
        <div style={{ display: "grid", gap: 8, marginTop: 8 }}>
          <div style={muted}>A retired obligation stays on record and its periods stay listed. No new periods can be added.</div>
          <input aria-label="Reason for retiring the obligation" style={input} placeholder="Why is this obligation being retired?" value={reason} onChange={e => setReason(e.target.value)} />
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={retire}>Retire obligation</button><button style={ghost} onClick={close}>Cancel</button></div>
        </div>
      )}
    </div>
  )
}

export default function TrainProvObligationsPanel() {
  const qc = useQueryClient()
  const canManage = usePermissions(["TRAININGPROVIDER_MANAGE", "TRAININGPROVIDER_ADMIN"])
  const [adding, setAdding] = useState(false)
  const [showRetired, setShowRetired] = useState(false)
  const { data = [], isLoading } = useQuery<Obligation[]>({
    queryKey: ["tp-obligations", showRetired],
    queryFn: async () => unwrap<Obligation[]>(await apiClient.get(`${RPT}/report-obligations`, { params: { status: showRetired ? undefined : "ACTIVE" } })),
  })
  const changed = () => { void qc.invalidateQueries({ queryKey: ["tp-obligations"] }); void qc.invalidateQueries({ queryKey: ["tp-report-periods"] }) }
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={muted}>{REPORT_NOTE}</div>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
        {canManage && !adding && <button style={solid} onClick={() => setAdding(true)}>Add an obligation</button>}
        <label style={{ fontSize: 13 }}><input type="checkbox" checked={showRetired} onChange={e => setShowRetired(e.target.checked)} /> Show retired</label>
      </div>
      {adding && <ObligationFormPanel onCancel={() => setAdding(false)} onSaved={() => { setAdding(false); changed() }} />}
      {isLoading ? <div style={muted}>Loading…</div> : data.length === 0 ? <div style={muted}>No obligations set up yet.</div> : data.map(o => <ObligationCard key={o.id} o={o} onChanged={changed} />)}
    </div>
  )
}
