// The funders register. Funders are retired with a reason, never deleted. No amounts are held.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { FUNDING_NOTE, TYPES, emptyFunder, funderBody, funderForm, funderProblem, label, reasonProblem, type Funder, type FunderForm } from "./fundingRules"
import { Badge, Notice } from "./tpUi"
import { card, errText, ghost, input, muted, solid, unwrap } from "./tpStyle"
import { FND, useClients } from "./tpApi"

function FunderFormPanel({ initial, onSaved, onCancel }: { initial?: Funder; onSaved: () => void; onCancel: () => void }) {
  const { data: clients = [] } = useClients()
  const [f, setF] = useState<FunderForm>(initial ? funderForm(initial) : emptyFunder)
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const set = (k: keyof FunderForm) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => setF({ ...f, [k]: e.target.value })
  const save = async () => {
    const p = funderProblem(f); if (p) { setError(p); return }
    setBusy(true); setError("")
    try {
      await (initial ? apiClient.put(`${FND}/funders/${initial.id}`, funderBody(f)) : apiClient.post(`${FND}/funders`, funderBody(f)))
      onSaved()
    } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
        <input aria-label="Funder name" style={input} placeholder="Funder name" value={f.name} onChange={set("name")} />
        <select aria-label="Kind of funder" style={input} value={f.funderType} onChange={set("funderType")}><option value="">Kind of funder…</option>{Object.entries(TYPES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select>
        <input aria-label="Contact name" style={input} placeholder="Contact name (optional)" value={f.contactName} onChange={set("contactName")} />
        <input aria-label="Contact email" style={input} placeholder="Contact email (optional)" value={f.contactEmail} onChange={set("contactEmail")} />
        <input aria-label="Contact phone" style={input} placeholder="Contact phone (optional)" value={f.contactPhone} onChange={set("contactPhone")} />
        <select aria-label="Linked client" style={input} value={f.clientId} onChange={set("clientId")}><option value="">Not linked to a client</option>{clients.map(c => <option key={c.id} value={c.id}>{c.tradingName}</option>)}</select>
      </div>
      <textarea aria-label="Funder note" style={input} placeholder="Note (optional)" value={f.note} onChange={set("note")} />
      <Notice text={error} />
      <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={save}>{initial ? "Save changes" : "Add funder"}</button><button style={ghost} onClick={onCancel}>Cancel</button></div>
    </div>
  )
}

function FunderCard({ f, onChanged }: { f: Funder; onChanged: () => void }) {
  const canManage = usePermissions(["TRAININGPROVIDER_MANAGE", "TRAININGPROVIDER_ADMIN"])
  const [mode, setMode] = useState<"" | "edit" | "retire">("")
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const retire = async () => {
    const p = reasonProblem(reason); if (p) { setError(p); return }
    setBusy(true); setError("")
    try { await apiClient.post(`${FND}/funders/${f.id}/retire`, { reason: reason.trim() }); setMode(""); setReason(""); onChanged() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  if (mode === "edit") return <FunderFormPanel initial={f} onCancel={() => setMode("")} onSaved={() => { setMode(""); onChanged() }} />
  return (
    <div style={{ ...card, display: "grid", gap: 6 }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
        <div>
          <strong>{f.name}</strong>
          <div style={muted}>{f.funderNo} · {label(TYPES, f.funderType)}{f.clientName ? ` · client ${f.clientName}` : ""}</div>
          {(f.contactName || f.contactEmail || f.contactPhone) && <div style={muted}>{[f.contactName, f.contactEmail, f.contactPhone].filter(Boolean).join(" · ")}</div>}
          {f.note && <div style={muted}>{f.note}</div>}
        </div>
        <Badge text={f.status === "RETIRED" ? "Retired" : "Active"} tone={f.status === "RETIRED" ? "neutral" : "good"} />
      </div>
      <div style={muted}>{f.activeLinks} {f.activeLinks === 1 ? "enrolment" : "enrolments"} linked</div>
      {f.retireReason && <div style={muted}>Retired: {f.retireReason}</div>}
      {canManage && f.status === "ACTIVE" && mode === "" && (
        <div style={{ display: "flex", gap: 8 }}><button style={ghost} onClick={() => setMode("edit")}>Edit</button><button style={ghost} onClick={() => { setError(""); setMode("retire") }}>Retire funder</button></div>
      )}
      {mode === "retire" && (
        <div style={{ display: "grid", gap: 6 }}>
          <input aria-label="Reason for retiring the funder" style={input} placeholder="Why is this funder being retired?" value={reason} onChange={e => setReason(e.target.value)} />
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={retire}>Retire the funder</button><button style={ghost} onClick={() => setMode("")}>Back</button></div>
        </div>
      )}
    </div>
  )
}

export default function TrainProvFundersList() {
  const qc = useQueryClient()
  const canManage = usePermissions(["TRAININGPROVIDER_MANAGE", "TRAININGPROVIDER_ADMIN"])
  const [type, setType] = useState("")
  const [status, setStatus] = useState("ACTIVE")
  const [adding, setAdding] = useState(false)
  const { data = [], isLoading } = useQuery<Funder[]>({
    queryKey: ["tp-funders", type, status],
    queryFn: async () => unwrap<Funder[]>(await apiClient.get(`${FND}/funders`, { params: { type: type || undefined, status: status || undefined } })),
  })
  const changed = () => { void qc.invalidateQueries({ queryKey: ["tp-funders"] }); void qc.invalidateQueries({ queryKey: ["tp-funder-options"] }) }
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={muted}>{FUNDING_NOTE}</div>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        <select aria-label="Funder kind filter" style={{ ...input, width: "auto" }} value={type} onChange={e => setType(e.target.value)}><option value="">All kinds</option>{Object.entries(TYPES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select>
        <select aria-label="Funder status filter" style={{ ...input, width: "auto" }} value={status} onChange={e => setStatus(e.target.value)}><option value="ACTIVE">Active</option><option value="RETIRED">Retired</option><option value="">All</option></select>
        {canManage && !adding && <button style={solid} onClick={() => setAdding(true)}>Add a funder</button>}
      </div>
      {adding && <FunderFormPanel onCancel={() => setAdding(false)} onSaved={() => { setAdding(false); changed() }} />}
      {isLoading ? <div style={muted}>Loading…</div> : data.length === 0 ? <div style={muted}>No funders found.</div> : data.map(f => <FunderCard key={f.id} f={f} onChanged={changed} />)}
    </div>
  )
}
