// Who is paying for one enrolment. Loads only when opened. No amounts are held, and a link does not mean funding was approved or paid.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { FUNDING_NOTE, TYPES, emptyLink, endProblem, label, linkBody, linkLine, linkProblem, type EnrolmentFunding, type Funder, type FundingLink, type LinkForm } from "./fundingRules"
import { Badge, Notice } from "./tpUi"
import { card, errText, fmtDate, ghost, input, muted, solid, todayIso, unwrap } from "./tpStyle"
import { FND } from "./tpApi"

function LinkCard({ l, canManage, onChanged }: { l: FundingLink; canManage: boolean; onChanged: () => void }) {
  const [ending, setEnding] = useState(false)
  const [day, setDay] = useState(todayIso())
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const end = async () => {
    const p = endProblem(day, l.fundedFrom, reason, todayIso()); if (p) { setError(p); return }
    setBusy(true); setError("")
    try { await apiClient.post(`${FND}/enrolment-funding/${l.id}/end`, { endedOn: day, reason: reason.trim() }); setEnding(false); setReason(""); onChanged() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <div style={{ ...card, display: "grid", gap: 6 }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
        <div><strong>{l.funderName}</strong><div style={muted}>{l.funderNo} · {label(TYPES, l.funderType)} · {linkLine(l)}</div>{l.note && <div style={muted}>{l.note}</div>}</div>
        <Badge text={l.status === "ENDED" ? "Ended" : "Active"} tone={l.status === "ENDED" ? "neutral" : "good"} />
      </div>
      {canManage && l.status === "ACTIVE" && !ending && <div><button style={ghost} onClick={() => { setError(""); setEnding(true) }}>End funding</button></div>}
      {ending && (
        <div style={{ display: "grid", gap: 6 }}>
          <input aria-label="Day the funding ended" type="date" style={input} value={day} onChange={e => setDay(e.target.value)} />
          <input aria-label="Reason the funding ended" style={input} placeholder="Why did the funding end?" value={reason} onChange={e => setReason(e.target.value)} />
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={end}>Save as ended</button><button style={ghost} onClick={() => setEnding(false)}>Cancel</button></div>
        </div>
      )}
    </div>
  )
}

export default function TrainProvFundingPanel({ enrolmentId, enrolmentStatus, startDate }: { enrolmentId: string; enrolmentStatus: string; startDate: string | null }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["TRAININGPROVIDER_MANAGE", "TRAININGPROVIDER_ADMIN"])
  const [open, setOpen] = useState(false)
  const [adding, setAdding] = useState(false)
  const [f, setF] = useState<LinkForm>(emptyLink(todayIso()))
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const { data } = useQuery<EnrolmentFunding>({
    queryKey: ["tp-funding", enrolmentId], enabled: open,
    queryFn: async () => unwrap<EnrolmentFunding>(await apiClient.get(`${FND}/learner-enrolments/${enrolmentId}/funding`)),
  })
  const { data: funders = [] } = useQuery<Funder[]>({
    queryKey: ["tp-funder-options"], enabled: open && adding,
    queryFn: async () => unwrap<Funder[]>(await apiClient.get(`${FND}/funders`, { params: { status: "ACTIVE" } })),
  })
  if (!open) return <div style={{ marginTop: 8 }}><button style={ghost} onClick={() => setOpen(true)}>Funding</button></div>
  const changed = () => { void qc.invalidateQueries({ queryKey: ["tp-funding", enrolmentId] }); void qc.invalidateQueries({ queryKey: ["tp-funders"] }) }
  const live = (data?.links ?? []).filter(l => l.status === "ACTIVE").map(l => l.funderId)
  const save = async () => {
    const p = linkProblem(f, startDate, live, todayIso()); if (p) { setError(p); return }
    setBusy(true); setError("")
    try { await apiClient.post(`${FND}/enrolment-funding`, linkBody(enrolmentId, f)); setAdding(false); setF(emptyLink(todayIso())); changed() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  const cancelled = enrolmentStatus === "CANCELLED"
  return (
    <div style={{ ...card, marginTop: 8, display: "grid", gap: 8 }}>
      <div style={muted}>{FUNDING_NOTE}</div>
      {!data ? <div style={muted}>Loading…</div> : <>
        <div>{data.summary}</div>
        {data.links.map(l => <LinkCard key={l.id} l={l} canManage={canManage} onChanged={changed} />)}
      </>}
      {adding && (
        <div style={{ display: "grid", gap: 8 }}>
          <select aria-label="Funder" style={input} value={f.funderId} onChange={e => setF({ ...f, funderId: e.target.value })}><option value="">Choose the funder…</option>{funders.map(x => <option key={x.id} value={x.id}>{x.name} ({label(TYPES, x.funderType)})</option>)}</select>
          <input aria-label="Funding starts" type="date" style={input} value={f.fundedFrom} onChange={e => setF({ ...f, fundedFrom: e.target.value })} />
          <input aria-label="Agreement reference" style={input} placeholder="Agreement reference (optional)" value={f.agreementRef} onChange={e => setF({ ...f, agreementRef: e.target.value })} />
          <input aria-label="Funder's own reference" style={input} placeholder="The funder's own reference (optional)" value={f.funderReference} onChange={e => setF({ ...f, funderReference: e.target.value })} />
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={save}>Link the funder</button><button style={ghost} onClick={() => { setAdding(false); setError("") }}>Cancel</button></div>
        </div>
      )}
      {canManage && !adding && (cancelled ? <div style={muted}>A cancelled enrolment cannot be linked to a funder.</div> : <div><button style={solid} onClick={() => setAdding(true)}>Link a funder</button></div>)}
      <div style={muted}>{data ? `Started ${fmtDate(startDate)}` : ""}</div>
      <div><button style={ghost} onClick={() => setOpen(false)}>Hide</button></div>
    </div>
  )
}
