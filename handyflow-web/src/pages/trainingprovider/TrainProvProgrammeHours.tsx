// src/pages/trainingprovider/TrainProvProgrammeHours.tsx
//
// Optional workplace hours on one programme version, with the source they come from. A programme need not record any and none are assumed.
// Hours can be changed only while the version is a draft. This never compares logged hours to a pass.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  NONE_NOTE, SOURCE_NOTE, SOURCE_TYPES, emptyHours, hoursBody, hoursForm, hoursProblem, sourceLine, withdrawProblem, type HoursForm, type VersionHours,
} from "./programmeHoursRules"
import { Field, Notice } from "./tpUi"
import { errText, ghost, input, muted, solid, todayIso, unwrap } from "./tpStyle"
import { CAT } from "./tpApi"

export default function TrainProvProgrammeHours({ versionId, canManage }: { versionId: string; canManage: boolean }) {
  const qc = useQueryClient()
  const url = `${CAT}/programme-versions/${versionId}/workplace-hours`
  const { data } = useQuery<VersionHours>({ queryKey: ["tp-programme-hours", versionId], queryFn: async () => unwrap<VersionHours>(await apiClient.get(url)) })
  const [mode, setMode] = useState<"" | "edit" | "withdraw" | "history">("")
  const [f, setF] = useState<HoursForm>(emptyHours())
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  if (!data) return null
  const cur = data.current
  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true); setError("")
    try { await fn(); setMode(""); setReason(""); await qc.invalidateQueries({ queryKey: ["tp-programme-hours", versionId] }) } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  const earlier = data.history.filter(h => h.status === "WITHDRAWN")
  return (
    <div style={{ display: "grid", gap: 6, marginTop: 8 }}>
      <strong style={{ fontSize: 13 }}>Workplace hours (optional)</strong>
      {cur ? <div style={{ fontSize: 13 }}>{sourceLine(cur)}{cur.covers ? ` Covers: ${cur.covers}.` : ""}</div> : <div style={muted}>{NONE_NOTE}</div>}
      {!data.editable && <div style={muted}>This version is approved, so its hours are frozen. Start a new version to change them.</div>}
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        {canManage && data.editable && mode === "" && <button style={ghost} onClick={() => { setF(cur ? hoursForm(cur) : emptyHours()); setMode("edit"); setError("") }}>{cur ? "Change hours" : "Record workplace hours"}</button>}
        {canManage && data.editable && cur && mode === "" && <button style={ghost} onClick={() => { setMode("withdraw"); setError("") }}>Withdraw hours</button>}
        {earlier.length > 0 && <button style={ghost} onClick={() => setMode(mode === "history" ? "" : "history")}>{mode === "history" ? "Hide withdrawn" : `Withdrawn (${earlier.length})`}</button>}
      </div>
      {mode === "history" && earlier.map(h => <div key={h.id} style={muted}>{sourceLine(h)} Withdrawn: {h.withdrawnReason}</div>)}
      {mode === "edit" && (
        <div style={{ display: "grid", gap: 8 }}>
          <div style={muted}>{SOURCE_NOTE}</div>
          <Field label="Hours"><input style={input} inputMode="decimal" value={f.hours} onChange={e => setF({ ...f, hours: e.target.value })} /></Field>
          <Field label="What the hours cover (optional)"><input style={input} value={f.covers} onChange={e => setF({ ...f, covers: e.target.value })} /></Field>
          <Field label="Kind of source"><select style={input} value={f.sourceType} onChange={e => setF({ ...f, sourceType: e.target.value })}><option value="">Choose…</option>{Object.entries(SOURCE_TYPES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></Field>
          <Field label="Document or reference"><input style={input} value={f.sourceReference} onChange={e => setF({ ...f, sourceReference: e.target.value })} /></Field>
          <Field label="Section or page"><input style={input} value={f.sourceSection} onChange={e => setF({ ...f, sourceSection: e.target.value })} /></Field>
          <Field label="Source's own version or publication date (optional)"><input type="date" style={input} value={f.sourceVersionDate} onChange={e => setF({ ...f, sourceVersionDate: e.target.value })} /></Field>
          <Field label="Takes effect on"><input type="date" style={input} value={f.effectiveDate} onChange={e => setF({ ...f, effectiveDate: e.target.value })} /></Field>
          <Field label="Checked by"><input style={input} value={f.verifiedBy} onChange={e => setF({ ...f, verifiedBy: e.target.value })} /></Field>
          <Field label="Checked on"><input type="date" style={input} value={f.verifiedOn} onChange={e => setF({ ...f, verifiedOn: e.target.value })} /></Field>
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}>
            <button style={solid} disabled={busy} onClick={() => { const p = hoursProblem(f, todayIso()); if (p) { setError(p); return } void run(() => apiClient.put(url, hoursBody(f))) }}>Save hours</button>
            <button style={ghost} onClick={() => setMode("")}>Cancel</button>
          </div>
        </div>
      )}
      {mode === "withdraw" && (
        <div style={{ display: "grid", gap: 6 }}>
          <Field label="Why are the hours withdrawn?"><input style={input} value={reason} onChange={e => setReason(e.target.value)} /></Field>
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}>
            <button style={solid} disabled={busy} onClick={() => { const p = withdrawProblem(reason); if (p) { setError(p); return } void run(() => apiClient.post(`${url}/withdraw`, { reason: reason.trim() })) }}>Withdraw hours</button>
            <button style={ghost} onClick={() => setMode("")}>Cancel</button>
          </div>
        </div>
      )}
    </div>
  )
}
