import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { btnGhost, btnPrimary, card, inputStyle, labelStyle } from "../warehousing/whseUi"
import {
  CONTROL_LABEL, STATE_HELP, asList, STATE_LABEL, isStepUp, unwrap, validateChange,
  type Blockers, type ControlState, type Observation, type Readiness,
} from "./opsPolicyOps"

const msg = (e: any, fb: string) => e?.response?.data?.message ?? fb
const Err = ({ text }: { text: string }) => text ? <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: "8px 0 0" }}>{text}</p> : null
const Note = ({ children }: { children: React.ReactNode }) => <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "4px 0 10px" }}>{children}</p>

/**
 * How firmly the arrival check and the proof-of-delivery rules are applied: Off, Monitor, Pilot (some depots), Enforced.
 * A step up goes one stage at a time and the server lists anything that stands in the way. Going back down is always allowed.
 * Only people who can configure Fuel see it. Completed deliveries are never re-judged when a stage changes.
 */
export default function EnforcementCard() {
  const can = usePermissions(["FUEL_CONFIGURATION_MANAGE"])
  const q = useQuery<ControlState[]>({ queryKey: ["fuel-enforcement"], retry: false, enabled: can, queryFn: async () => asList<ControlState>(unwrap(await apiClient.get("/api/v1/fuel/settings/enforcement"))) })
  if (!can) return null
  return (
    <section style={{ ...card, padding: 16, marginBottom: 14 }}>
      <h3 style={{ margin: "0 0 4px", fontSize: 14 }}>Rollout of enforcement</h3>
      <Note>Each control moves one stage at a time: Off, Monitor, Pilot, then Enforced. In Monitor nothing is refused, so you can see what would have been before drivers are affected. Safety-critical checks (a blocked vehicle, driver or product, an expired statutory certificate) are never waived by Monitor or by a pilot.</Note>
      {q.isError && <Err text={msg(q.error, "Could not load the rollout state")} />}
      {(q.data ?? []).map(c => <ControlRow key={c.control} c={c} />)}
      <Lookback />
    </section>
  )
}

function ControlRow({ c }: { c: ControlState }) {
  const qc = useQueryClient()
  const [target, setTarget] = useState(""); const [reason, setReason] = useState(""); const [depots, setDepots] = useState<string[]>([])
  const [err, setErr] = useState(""); const [saved, setSaved] = useState(false); const [showObs, setShowObs] = useState(false)
  const depotList = useQuery<{ id: string; name: string }[]>({ queryKey: ["fuel-scope-depots"], queryFn: async () => asList<{ id: string; name: string }>(unwrap(await apiClient.get("/api/v1/fuel/depots"))) })
  const blockers = useQuery<Blockers>({
    queryKey: ["fuel-enforcement-blockers", c.control, target, depots.join(","), reason.trim().length >= 5], retry: false, enabled: !!target,
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/fuel/settings/enforcement/${c.control}/blockers`, { params: { target, depots: depots.join(","), reason } })),
  })
  const obs = useQuery<Observation[]>({ queryKey: ["fuel-enforcement-obs", c.control], retry: false, enabled: showObs, queryFn: async () => asList<Observation>(unwrap(await apiClient.get(`/api/v1/fuel/settings/enforcement/${c.control}/observations`))) })
  const change = useMutation({
    mutationFn: () => apiClient.put(`/api/v1/fuel/settings/enforcement/${c.control}`, { state: target, pilotDepotIds: target === "PILOT" ? depots : [], reason: reason.trim() }),
    onSuccess: () => {
      setErr(""); setSaved(true); setTarget(""); setReason(""); setDepots([])
      for (const k of ["fuel-enforcement", "fuel-arrival-policy", "fuel-pod-policy"]) qc.invalidateQueries({ queryKey: [k] })
    },
    onError: (e: any) => { setSaved(false); setErr(msg(e, "Could not change the stage")) },
  })
  const label = CONTROL_LABEL[c.control] ?? c.control
  const stepUp = target ? isStepUp(c.state, target) : false
  const listed = asList<string>(blockers.data?.blockers)
  // A missing reason is asked for by the form itself; the other blockers come from the server.
  const serverBlocks = listed.filter(b => !/reason/i.test(b))
  const pilotNames = asList<string>(c.pilotDepotIds).map(id => depotList.data?.find(d => d.id === id)?.name ?? id)
  return (
    <div style={{ borderTop: "1px solid var(--hf-border)", padding: "12px 0" }}>
      <div style={{ display: "flex", gap: 10, alignItems: "baseline", flexWrap: "wrap" }}>
        <strong style={{ fontSize: 13.5 }}>{label}</strong>
        <span aria-label={`${label} stage`} style={{ fontSize: 12.5, fontWeight: 600 }}>{STATE_LABEL[c.state] ?? c.state}</span>
        {c.stateSince && <span style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>since {new Date(c.stateSince).toLocaleDateString()}</span>}
      </div>
      <Note>{STATE_HELP[c.state] ?? ""}{c.state === "PILOT" && pilotNames.length ? ` Enforced at: ${pilotNames.join(", ")}.` : ""}{c.reason ? ` Last reason: ${c.reason}` : ""}</Note>
      <p style={{ fontSize: 12.5, margin: "0 0 8px" }}>
        {c.observationsLast7Days} delivery check{c.observationsLast7Days === 1 ? "" : "s"} would have been refused in the last 7 days. <button style={{ ...btnGhost, padding: "2px 8px" }} onClick={() => setShowObs(s => !s)}>{showObs ? "Hide" : "Show"}</button>
      </p>
      {showObs && (
        <ul style={{ fontSize: 12, margin: "0 0 8px", paddingLeft: 18 }}>
          {obs.isError && <li>Could not load the list.</li>}
          {(obs.data ?? []).length === 0 && !obs.isError && <li>Nothing recorded.</li>}
          {(obs.data ?? []).map((o, i) => <li key={i}>{new Date(o.at).toLocaleString()}: {o.message}</li>)}
        </ul>
      )}
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        {asList<string>(c.nextStates).map(s => (
          <button key={s} style={target === s ? btnPrimary : btnGhost} aria-label={`${label}: ${isStepUp(c.state, s) ? "move to" : s === c.state ? "change the depots of" : "go back to"} ${STATE_LABEL[s]}`}
            onClick={() => { setSaved(false); setErr(""); setTarget(t => t === s ? "" : s); if (s === "PILOT") setDepots(c.pilotDepotIds) }}>
            {s === c.state ? "Change pilot depots" : (isStepUp(c.state, s) ? "Move to " : "Go back to ") + STATE_LABEL[s]}
          </button>
        ))}
      </div>
      {target && (
        <div style={{ border: "1px solid var(--hf-border)", borderRadius: 8, padding: 12, marginTop: 10 }}>
          <p style={{ fontSize: 12.5, margin: "0 0 6px" }}><strong>{STATE_LABEL[target]}.</strong> {STATE_HELP[target]}</p>
          {target === "PILOT" && (
            <fieldset style={{ border: "none", padding: 0, margin: "0 0 8px" }}>
              <legend style={{ fontSize: 12.5, fontWeight: 600 }}>Depots in the pilot</legend>
              {(depotList.data ?? []).map(d => (
                <label key={d.id} style={{ display: "block", fontSize: 13 }}>
                  <input type="checkbox" aria-label={d.name} checked={depots.includes(d.id)} onChange={() => setDepots(x => x.includes(d.id) ? x.filter(i => i !== d.id) : [...x, d.id])} /> {d.name}
                </label>
              ))}
            </fieldset>
          )}
          <label style={labelStyle} htmlFor={`why-${c.control}`}>Reason</label>
          <textarea id={`why-${c.control}`} aria-label={`${label} reason`} value={reason} onChange={e => setReason(e.target.value)} rows={2} style={inputStyle} />
          {stepUp && serverBlocks.length > 0 && (
            <div role="alert" style={{ marginTop: 8 }}>
              <p style={{ fontSize: 12.5, fontWeight: 600, margin: "0 0 4px", color: "var(--hf-danger-text)" }}>Not yet possible:</p>
              <ul style={{ fontSize: 12.5, margin: 0, paddingLeft: 18 }}>{serverBlocks.map((b, i) => <li key={i}>{b}</li>)}</ul>
            </div>
          )}
          {blockers.isError && <Err text={msg(blockers.error, "Could not check the requirements")} />}
          <Err text={err} />
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 10 }}>
            <button style={btnGhost} onClick={() => setTarget("")}>Cancel</button>
            <button style={btnPrimary} disabled={change.isPending || (stepUp && serverBlocks.length > 0)}
              onClick={() => { const p = validateChange(target, reason, depots); if (p) return setErr(p); setErr(""); change.mutate() }}>Apply</button>
          </div>
        </div>
      )}
      {saved && <p role="status" style={{ fontSize: 12, color: "var(--hf-success-text)" }}>Saved. Deliveries already completed are not judged again.</p>}
    </div>
  )
}

/** What the two controls would have refused over recent completed deliveries. Counts only. Read only. */
function Lookback() {
  const [days, setDays] = useState(30)
  const q = useQuery<Readiness>({ queryKey: ["fuel-enforcement-readiness", days], retry: false, queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/settings/enforcement-readiness", { params: { days } })) })
  const r = q.data
  return (
    <div style={{ borderTop: "1px solid var(--hf-border)", paddingTop: 12 }}>
      <strong style={{ fontSize: 13.5 }}>Look back</strong>
      <Note>How many completed deliveries would have been refused if the rules had been on. A high number is how many loads drivers complete today without the step.</Note>
      <label style={labelStyle} htmlFor="lookback-days">Window</label>
      <select id="lookback-days" aria-label="Look-back window" value={days} onChange={e => setDays(Number(e.target.value))} style={{ ...inputStyle, maxWidth: 160 }}>
        {[7, 30, 90].map(d => <option key={d} value={d}>Last {d} days</option>)}
      </select>
      {q.isError && <Err text={msg(q.error, "Could not load the look-back")} />}
      {r && (
        <table style={{ fontSize: 12.5, marginTop: 8, borderCollapse: "collapse" }}>
          <tbody>
            <tr><td style={{ paddingRight: 16 }}>Deliveries completed</td><td aria-label="Deliveries completed">{r.delivered}</td></tr>
            <tr><td style={{ paddingRight: 16 }}>No receiver name and no exception</td><td aria-label="No receiver">{r.wouldRefuseNoReceiver}</td></tr>
            <tr><td style={{ paddingRight: 16 }}>Receiver named but no signature</td><td aria-label="No signature">{r.wouldRefuseNoSignature}</td></tr>
            <tr><td style={{ paddingRight: 16 }}>No arrival check on record</td><td aria-label="No arrival check">{r.wouldRefuseNoArrivalCheck}</td></tr>
          </tbody>
        </table>
      )}
      {r && <Note>A stale or blocked check is not counted, so the arrival check figure is at least this high.</Note>}
    </div>
  )
}
