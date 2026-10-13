import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import ModalShell from "./ModalShell"
import { canReversePayment, entryLabel, withBalances, type ClaimLedger, type LedgerEntry } from "./claimHistory"
import { rand } from "./claimMoney"

const BORDER = "var(--hf-border)", GRAY = "var(--hf-text-muted)"
const day = (d: string) => new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })

/** Every payment, write-off, credit note and reversal on one claim, newest first, with what the scheme owed after each. */
export default function ClaimLedgerModal({ claimId, canReverse, onReverse, onClose }: {
  claimId: string; canReverse: boolean; onReverse: (payment: LedgerEntry) => void; onClose: () => void
}) {
  const { data, isLoading, error } = useQuery<ClaimLedger>({
    queryKey: ["clinic-claim-ledger", claimId],
    queryFn: async () => (await apiClient.get(`/api/v1/clinic/billing/claims/${claimId}/ledger`)).data?.data,
  })
  const rows = data ? withBalances(data.schemeDue, data.entries) : []
  return (
    <ModalShell title="Money history" onClose={onClose} width={760}
      footer={<button type="button" onClick={onClose} style={{ padding: "9px 18px", border: `1px solid ${BORDER}`, borderRadius: 9, background: "var(--hf-surface)", cursor: "pointer" }}>Close</button>}>
      {isLoading && <div>Loading...</div>}
      {error && <div role="alert" style={{ color: "var(--hf-danger-text)" }}>The history could not be loaded.</div>}
      {data && (
        <>
          <div aria-label="Ledger totals" style={{ display: "flex", gap: 18, flexWrap: "wrap", fontSize: 13, marginBottom: 14 }}>
            <span>Scheme portion <b>{rand(data.schemeDue)}</b></span>
            <span>Paid <b>{rand(data.paid)}</b></span>
            {data.writtenOff > 0 && <span>Written off <b>{rand(data.writtenOff)}</b></span>}
            {data.credited > 0 && <span>Credited <b>{rand(data.credited)}</b></span>}
            <span>Still owed <b>{rand(data.outstanding)}</b></span>
          </div>
          {rows.length === 0 ? (
            <div style={{ color: GRAY, fontSize: 13 }}>No money has moved on this claim yet.</div>
          ) : (
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
              <thead>
                <tr style={{ textAlign: "left", color: GRAY, fontSize: 11 }}>
                  {["Date", "What", "Amount", "Reference or reason", "Owed after", ""].map(h => <th key={h} style={{ padding: "6px 8px" }}>{h}</th>)}
                </tr>
              </thead>
              <tbody>
                {rows.map((e, i) => (
                  <tr key={`${e.kind}-${e.id ?? i}-${e.recordedAt}`} style={{ borderTop: `1px solid ${BORDER}`, opacity: e.reversed ? 0.6 : 1 }}>
                    <td style={{ padding: "7px 8px", whiteSpace: "nowrap" }}>{day(e.date)}</td>
                    <td style={{ padding: "7px 8px" }}>{entryLabel(e)}</td>
                    <td style={{ padding: "7px 8px", textDecoration: e.reversed ? "line-through" : "none" }}>{rand(e.amount)}</td>
                    <td style={{ padding: "7px 8px", color: GRAY }}>{[e.reference, e.note].filter(Boolean).join(" · ") || "—"}</td>
                    <td style={{ padding: "7px 8px" }}>{rand(e.owedAfter)}</td>
                    <td style={{ padding: "7px 8px" }}>
                      {canReversePayment(e, data.status, canReverse) && (
                        <button type="button" onClick={() => onReverse(e)}
                          style={{ padding: "4px 10px", border: `1px solid ${BORDER}`, borderRadius: 7, background: "var(--hf-surface)", color: "var(--hf-danger-text)", cursor: "pointer", fontSize: 12 }}>
                          Reverse…
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      )}
    </ModalShell>
  )
}
