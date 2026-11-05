// src/pages/audit-trail/SoleApproverReportPage.tsx
//
// "Steps done without a second person": every approval, posting or payout where the same person made and checked a
// transaction because nobody else held the permission to check it (the sole-user rule, IAM target architecture 15.3).
// Nothing was blocked and nothing is hidden: this is the list an owner hands to an accountant or auditor. The rule
// switches itself on for a step as soon as a second person is given the permission.
import { useEffect, useState } from "react"
import { auditTrailApi, type AuditEvent } from "../../api/auditTrail.api"

const BORDER = "var(--hf-border)"
const INK = "var(--hf-text)"
const MUTED = "var(--hf-text-muted)"
const FAINT = "var(--hf-text-faint)"

const when = (d: string) =>
  new Date(d).toLocaleString("en-ZA", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" })

/** What kind of step it was, in plain words. */
export function stepLabel(itemType: string): string {
  const known: Record<string, string> = {
    JOURNAL: "Journal posted by its maker",
    EXPENSE_CLAIM: "Expense claim approved or paid by the person it involves",
    AP_APPROVE_OWN_BILL: "Supplier bill approved by whoever captured it",
    AP_SECOND_APPROVAL: "Supplier bill given both approvals by one person",
    AP_PAY_OWN_BILL: "Supplier bill paid by whoever captured it",
    AP_RELEASE_OWN_BATCH: "Payment batch released by whoever made it",
    AP_VERIFY_OWN_BANKING: "Supplier bank details checked by whoever entered them",
  }
  return known[itemType] ?? itemType.replace(/_/g, " ").toLowerCase()
}

function parse(json?: string | null): { permission?: string; detail?: string } {
  if (!json) return {}
  try { return JSON.parse(json) } catch { return {} }
}

const csvCell = (v: string) => `"${v.replace(/"/g, '""')}"`

export function toCsv(rows: AuditEvent[]): string {
  const head = ["When", "Who", "Step", "Detail", "Permission nobody else held"]
  const lines = rows.map(r => {
    const a = parse(r.afterJson)
    return [when(r.at), r.actorName ?? "", stepLabel(r.itemType), a.detail ?? "", a.permission ?? ""].map(csvCell).join(",")
  })
  return [head.map(csvCell).join(","), ...lines].join("\r\n")
}

function download(rows: AuditEvent[]) {
  const url = URL.createObjectURL(new Blob([toCsv(rows)], { type: "text/csv;charset=utf-8" }))
  const a = document.createElement("a")
  a.href = url
  a.download = "steps-without-a-second-person.csv"
  a.click()
  URL.revokeObjectURL(url)
}

export function SoleApproverReportPage() {
  const [rows, setRows] = useState<AuditEvent[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    auditTrailApi.soleApprover()
      .then(setRows)
      .catch((e: any) => setError(e?.response?.status === 403
        ? "You need the audit permission to see this report."
        : e?.response?.data?.message ?? "Could not load the report."))
      .finally(() => setLoading(false))
  }, [])

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif", maxWidth: 960 }}>
      <h1 style={{ fontSize: 20, fontWeight: 800, color: INK, marginBottom: 4 }}>Steps done without a second person</h1>
      <p style={{ fontSize: 13, color: MUTED, marginBottom: 16 }}>
        Where the same person made and checked something because nobody else was able to check it. Nothing was blocked.
        When a second person is given that permission, the check applies from then on. Hand this list to your accountant or auditor.
      </p>

      {loading ? (
        <div style={{ padding: 32, textAlign: "center", color: FAINT, fontSize: 13 }}>Loading…</div>
      ) : error ? (
        <div role="alert" style={{ padding: 16, color: "var(--hf-danger, #b91c1c)", fontSize: 13 }}>{error}</div>
      ) : rows.length === 0 ? (
        <div style={{ padding: 40, textAlign: "center", color: FAINT, fontSize: 14, background: "var(--hf-surface)",
          border: `1px solid ${BORDER}`, borderRadius: 8 }}>
          No step has been done without a second person.
        </div>
      ) : (
        <>
          <div style={{ marginBottom: 10 }}>
            <button onClick={() => download(rows)}
              style={{ padding: "6px 12px", fontSize: 13, border: `1px solid ${BORDER}`, borderRadius: 6, background: "var(--hf-surface)", color: INK, cursor: "pointer" }}>
              Download CSV
            </button>
          </div>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
            <thead>
              <tr style={{ textAlign: "left", color: MUTED }}>
                <th style={{ padding: "6px 8px" }}>When</th><th style={{ padding: "6px 8px" }}>Who</th>
                <th style={{ padding: "6px 8px" }}>Step</th><th style={{ padding: "6px 8px" }}>Detail</th>
              </tr>
            </thead>
            <tbody>
              {rows.map(r => {
                const a = parse(r.afterJson)
                return (
                  <tr key={r.id} style={{ borderTop: `1px solid ${BORDER}`, color: INK }}>
                    <td style={{ padding: "8px" }}>{when(r.at)}</td>
                    <td style={{ padding: "8px" }}>{r.actorName ?? "Unknown"}</td>
                    <td style={{ padding: "8px" }}>{stepLabel(r.itemType)}</td>
                    <td style={{ padding: "8px", color: MUTED }}>{a.detail ?? ""}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </>
      )}
    </div>
  )
}
