// src/pages/agriculture/AgProduceNotice.tsx
//
// Tells the person milk or eggs from this animal or group are inside a withdrawal period and must not be sold or used yet.
// Information only: the module records no milk or egg sales, so there is nothing for it to block. Renders nothing when clear.
import { useProduceWithdrawal } from "./agCompliance.api"
import { produceLines } from "./agCompliance.logic"
import { todayISO } from "./agCrops.logic"

export default function AgProduceNotice({ kind, id }: { kind: "animal" | "group"; id: string }) {
  const { data } = useProduceWithdrawal(kind, id, todayISO())
  const lines = produceLines(data)
  if (lines.length === 0) return null
  return (
    <div role="status" style={{ border: "1px solid var(--hf-warning-border, var(--hf-border))", background: "var(--hf-warning-bg, var(--hf-surface-muted))", borderRadius: 8, padding: "8px 12px", margin: "0 0 12px" }}>
      <p style={{ fontSize: 12.5, fontWeight: 700, color: "var(--hf-warning-text)", margin: "0 0 2px" }}>Withdrawal period running</p>
      {lines.map(l => <p key={l} style={{ fontSize: 12, color: "var(--hf-text)", margin: 0 }}>{l}</p>)}
      <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: "4px 0 0" }}>Keep it out of sale and out of the food chain until then.</p>
    </div>
  )
}
