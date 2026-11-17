// The live sales, refunds and voids a sender sent for one staged shift, with what they add up to beside the shift's meters.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { StatusPill } from "./fuelUi"
import { fmtL, fmtR } from "./orderOps"
import { asShiftTransactions, litresCheckText, problemCount, txKindLabel, type ShiftTransactions } from "./importOps"

const unwrap = (r: any) => r.data?.data ?? r.data

export default function ImportTransactions({ importId }: { importId: string }) {
  const q = useQuery<ShiftTransactions | null>({ queryKey: ["fuel-import-transactions", importId], retry: false,
    queryFn: async () => asShiftTransactions(unwrap(await apiClient.get(`/api/v1/fuel/forecourt/imports/${importId}/transactions`))) })
  const t = q.data
  if (!t || t.transactions.length === 0) return null
  const s = t.summary
  const issues = problemCount(t)
  const check = litresCheckText(s)
  return (
    <div style={{ marginTop: 10 }}>
      <strong style={{ fontSize: 13 }}>Live transactions</strong>
      <p style={{ margin: "2px 0 6px", color: "var(--hf-text-muted)" }}>
        {s.sales} sale{s.sales === 1 ? "" : "s"} · {fmtL(s.salesLitres)} · {fmtR(s.salesAmount)}; refunds {fmtR(s.refundsAmount)}, voids {fmtR(s.voidsAmount)}; net {fmtR(s.netAmount)}.
        {check ? ` ${check}` : ""} Refunds and voids lower the expected takings when you accept the shift.
      </p>
      {issues > 0 && <p role="status" style={{ margin: "0 0 6px", color: "var(--hf-warning-text-strong)" }}>{issues} transaction problem{issues === 1 ? "" : "s"} to fix before this shift can be accepted.</p>}
      <table style={{ borderCollapse: "collapse", fontSize: 12.5 }}>
        <thead><tr>{["Ref", "Type", "Of sale", "Pump", "Litres", "Amount", "Tender", "Status"].map(h => <th key={h} style={{ textAlign: "left", padding: "2px 12px 2px 0", color: "var(--hf-text-muted)" }}>{h}</th>)}</tr></thead>
        <tbody>
          {t.transactions.map(x => (
            <tr key={x.id}>
              <td style={{ padding: "2px 12px 2px 0" }}>{x.externalId}</td>
              <td style={{ paddingRight: 12 }}>{txKindLabel(x.kind)}</td>
              <td style={{ paddingRight: 12 }}>{x.originalExternalId ?? ""}</td>
              <td style={{ paddingRight: 12 }}>{x.pumpLabel ?? ""}</td>
              <td style={{ paddingRight: 12 }}>{x.litres ?? ""}</td>
              <td style={{ paddingRight: 12 }}>{x.kind === "SALE" ? fmtR(x.amount) : `-${fmtR(x.amount)}`}</td>
              <td style={{ paddingRight: 12 }}>{x.tender ?? ""}</td>
              <td><StatusPill status={x.status} /></td>
            </tr>
          ))}
        </tbody>
      </table>
      {t.transactions.flatMap(x => x.problems.map(p => <p key={x.id + p} role="status" style={{ margin: "4px 0 0", color: "var(--hf-warning-text-strong)" }}>{p}</p>))}
    </div>
  )
}
