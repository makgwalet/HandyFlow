// Driver delivery execution: what happened to one load, in time order, from arrival checks, proof, failed attempts and the delivery itself.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Modal } from "../warehousing/whseUi"
import { fmtL } from "./orderOps"
import { StatusPill, toneInk, toneSoft } from "./fuelUi"
import { SEVERITY_TONE, TYPE_LABEL, figureNotes, type Execution } from "./executionOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const when = (iso: string) => new Date(iso).toLocaleString("en-ZA", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" })

export default function ExecutionModal({ deliveryId, label, onClose }: { deliveryId: string; label: string; onClose: () => void }) {
  const q = useQuery<Execution>({ queryKey: ["fuel-execution", deliveryId], queryFn: async () => unwrap(await apiClient.get(`/api/v1/fuel/deliveries/${deliveryId}/execution`)) })
  const x = q.data
  return (
    <Modal title={`Delivery timeline: ${label}`} onClose={onClose} width={620}>
      {q.isLoading && <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p>}
      {q.isError && <p role="alert" style={{ color: "var(--hf-danger-text)" }}>Could not load the timeline.</p>}
      {x && (<div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
        <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", fontSize: 13 }}>
          <StatusPill status={x.status} />
          <span>{x.driverName ?? "No driver"}{x.vehicleReg ? ` · ${x.vehicleReg}` : ""}</span>
          <span style={{ color: "var(--hf-text-muted)" }}>
            {x.litresOrdered != null && <>ordered {fmtL(x.litresOrdered)}</>}{x.litresLoaded != null && <> · loaded {fmtL(x.litresLoaded)}</>}{x.litresDelivered != null && <> · delivered {fmtL(x.litresDelivered)}</>}
          </span>
        </div>
        {figureNotes(x).length > 0 && <ul aria-label="Points to look at" style={{ margin: 0, paddingLeft: 18, fontSize: 12.5, color: "var(--hf-warning-text-deep)" }}>{figureNotes(x).map(n => <li key={n}>{n}</li>)}</ul>}
        {x.events.length === 0 ? <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Nothing has been recorded for this load yet.</p> : (
          <ol aria-label="Timeline" style={{ listStyle: "none", margin: 0, padding: 0, display: "flex", flexDirection: "column", gap: 8 }}>
            {x.events.map((e, i) => {
              const tone = SEVERITY_TONE[e.severity] ?? "green"
              return (
                <li key={i} style={{ display: "flex", gap: 10, alignItems: "flex-start" }}>
                  <span aria-hidden="true" style={{ marginTop: 5, width: 10, height: 10, borderRadius: 99, background: toneInk(tone), flexShrink: 0 }} />
                  <div style={{ flex: 1, padding: "6px 10px", borderRadius: 8, background: toneSoft(tone), color: "var(--hf-text)", fontSize: 13 }}>
                    <div style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>{when(e.at)} · {TYPE_LABEL[e.type] ?? e.type}</div>
                    <div>{e.text}</div>
                  </div>
                </li>)
            })}
          </ol>)}
        <p style={{ margin: 0, fontSize: 11.5, color: "var(--hf-text-muted)" }}>The time the driver started the trip is not recorded, so there is no event for it; the status shows whether the load is on the road.</p>
      </div>)}
    </Modal>
  )
}
