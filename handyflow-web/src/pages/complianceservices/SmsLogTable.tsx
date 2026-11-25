import { kindLabel, outcomeLabel, type SmsLog } from "./smsRules"

/** What was handed to the SMS provider. "Handed over" is not "delivered". */
export default function SmsLogTable({ rows, showClient }: { rows: SmsLog[]; showClient: boolean }) {
  if (rows.length === 0) return <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No SMS reminders have been sent yet.</div>
  return (
    <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
      <thead>
        <tr style={{ textAlign: "left", color: "var(--hf-text-muted)" }}>
          <th>Date</th>{showClient && <th>Client</th>}<th>Reminder</th><th>What happened</th>
        </tr>
      </thead>
      <tbody>
        {rows.map(r => (
          <tr key={r.id} style={{ borderTop: "1px solid var(--hf-border)" }}>
            <td>{r.runDate}</td>{showClient && <td>{r.clientName}</td>}<td>{kindLabel(r.kind)}</td>
            <td>{outcomeLabel(r.outcome)}{r.detail ? ` (${r.detail})` : ""}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
