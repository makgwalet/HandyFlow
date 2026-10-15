// src/pages/earthmoving/BillingTab.tsx
//
// Monthly billing: for a finished month, shows every deployment on automatic billing, what it would charge, and what needs a
// person (no rate, no readings, a disputed reading, a month that is only part charged). Running it raises the charges and, if
// asked, puts each customer's on one DRAFT invoice. Nothing is issued or collected. Running a month twice is safe.
import { useState } from "react"
import { CalendarClock } from "lucide-react"
import { apiMessage, fmtCurrency2dp, fmtDate, fmtDateTime } from "./shared/format"
import { ErrBanner } from "./shared/Modal"
import { HIRE_BASIS_LABEL, HIRE_RATE_SOURCE_LABEL } from "./shared/constants"
import { useEarthPerms } from "./shared/useEarthPerms"
import { useBillingPreview, useBillingHistory, useRunBilling, useSwitchScheduledBilling } from "./shared/hooks"
import type { BillingLine, BillingRun } from "./shared/types"

const btn = (primary = false): React.CSSProperties => ({
  padding: "6px 12px", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer",
  border: primary ? "none" : "1px solid var(--hf-border)",
  background: primary ? "var(--hf-primary)" : "var(--hf-surface)",
  color: primary ? "var(--hf-text-on-solid)" : "var(--hf-text-secondary)",
})
const card: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 10, padding: "12px 16px", background: "var(--hf-surface)" }

/** The month before this one, as YYYY-MM: the one that has just ended. */
function lastMonth(): string {
  const d = new Date()
  const y = d.getMonth() === 0 ? d.getFullYear() - 1 : d.getFullYear()
  const m = d.getMonth() === 0 ? 12 : d.getMonth()
  return `${y}-${String(m).padStart(2, "0")}`
}

function Line({ l }: { l: BillingLine }) {
  return (
    <div style={{ ...card, display: "flex", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}>
      <div>
        <div style={{ fontWeight: 700, color: "var(--hf-text)" }}>{l.machine ?? "A customer"} · {l.customerName ?? "No customer linked"}</div>
        <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 2 }}>
          {l.siteName ? `${l.siteName} · ` : ""}{fmtDate(l.periodFrom)} to {fmtDate(l.periodTo)}{l.basis ? ` · ${HIRE_BASIS_LABEL[l.basis]?.toLowerCase() ?? l.basis}` : ""}
        </div>
        {l.message && <div style={{ fontSize: 12, marginTop: 4, color: l.state === "EXCEPTION" ? "var(--hf-warning-text)" : "var(--hf-text-muted)", fontWeight: l.state === "EXCEPTION" ? 600 : 400 }}>{l.message}</div>}
      </div>
      {l.state === "READY" && l.amount != null && l.total != null && (
        <div style={{ fontSize: 12, textAlign: "right", color: "var(--hf-text-secondary)" }}>
          {l.quantity} at {fmtCurrency2dp(l.unitRate ?? 0)}{l.rateSource ? ` (${HIRE_RATE_SOURCE_LABEL[l.rateSource] ?? l.rateSource})` : ""}<br />
          {fmtCurrency2dp(l.amount)} + VAT = <strong>{fmtCurrency2dp(l.total)}</strong>
        </div>
      )}
    </div>
  )
}

export default function BillingTab() {
  const perms = useEarthPerms()
  const [month, setMonth] = useState(lastMonth())
  const [err, setErr] = useState("")
  const [done, setDone] = useState<BillingRun | null>(null)

  const preview = useBillingPreview(month, perms.canAdmin)
  const history = useBillingHistory()
  const run = useRunBilling(r => setDone(r))
  const sw = useSwitchScheduledBilling()

  const lines = preview.data?.lines ?? []
  const of = (state: string) => lines.filter(l => l.state === state)
  const go = (createDrafts: boolean) => {
    setErr(""); setDone(null)
    run.mutate({ month, createDrafts }, { onError: (e: unknown) => setErr(apiMessage(e, "Could not run the billing")) })
  }
  const section = (title: string, rows: BillingLine[], hint?: string) => rows.length > 0 && (
    <div style={{ marginTop: 14 }}>
      <div style={{ fontWeight: 700, fontSize: 13, marginBottom: 6 }}>{title} ({rows.length})</div>
      {hint && <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginBottom: 6 }}>{hint}</div>}
      <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>{rows.map((l, i) => <Line key={`${l.deploymentId ?? "x"}-${i}`} l={l} />)}</div>
    </div>
  )

  return (
    <div>
      <div style={{ ...card, marginBottom: 14, fontSize: 13, color: "var(--hf-text-secondary)" }}>
        Deployments you put on automatic billing are charged for each finished month in the way you chose for them. Charges are
        raised as not invoiced, and can become draft invoices, one per customer. Nothing is issued or collected here. A month
        started counts as a whole week or month; if a deployment started or ended mid-month, only those days are charged.
      </div>

      {perms.canAdmin && (
        <label style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 13, marginBottom: 14 }}>
          <input type="checkbox" aria-label="Run automatically on the 2nd of each month" checked={history.data?.autoBillingEnabled ?? false}
            disabled={sw.isPending || !history.data}
            onChange={e => { setErr(""); sw.mutate(e.target.checked, { onError: (x: unknown) => setErr(apiMessage(x, "Could not change the schedule")) }) }} />
          Run automatically on the 2nd of each month for the month that just ended (charges and draft invoices only)
        </label>
      )}

      {!perms.canAdmin ? (
        <div style={{ ...card, color: "var(--hf-text-muted)" }}>Only an administrator can preview or run monthly billing. Past runs are listed below.</div>
      ) : (
        <>
          <div style={{ display: "flex", gap: 12, alignItems: "end", flexWrap: "wrap" }}>
            <div>
              <label htmlFor="bl-month" style={{ display: "block", fontSize: 12, fontWeight: 600, marginBottom: 4 }}>Month</label>
              <input id="bl-month" type="month" value={month} onChange={e => { setMonth(e.target.value); setDone(null); setErr("") }}
                style={{ padding: "6px 10px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text)" }} />
            </div>
            <button style={btn()} disabled={run.isPending || preview.isLoading || lines.length === 0} onClick={() => go(false)}>Create charges</button>
            <button style={btn(true)} disabled={run.isPending || preview.isLoading || lines.length === 0} onClick={() => go(true)}>Create charges and draft invoices</button>
          </div>

          {err && <ErrBanner msg={err} />}
          {preview.isError && <ErrBanner msg={apiMessage(preview.error, "Could not work out this month")} />}
          {done && (
            <div role="status" style={{ ...card, marginTop: 12, color: "var(--hf-success-text-strong)" }}>
              {done.chargesCreated} charge{done.chargesCreated === 1 ? "" : "s"} created
              {done.invoices.length > 0 ? `, ${done.invoices.length} draft invoice${done.invoices.length === 1 ? "" : "s"} (${done.invoices.map(i => i.invoiceNumber).join(", ")}). Review and issue them in Invoicing` : ""}.
              {done.exceptions > 0 ? ` ${done.exceptions} need${done.exceptions === 1 ? "s" : ""} a person, see below.` : ""}
            </div>
          )}

          {preview.isLoading ? <div style={{ marginTop: 14, color: "var(--hf-text-muted)" }}>Working out {month}…</div>
            : !preview.isError && lines.length === 0 ? (
              <div style={{ textAlign: "center", padding: "40px 20px", marginTop: 14, border: "1px dashed var(--hf-border)", borderRadius: 12, color: "var(--hf-text-faint)" }}>
                <CalendarClock size={34} style={{ marginBottom: 10, opacity: 0.4 }} />
                <div style={{ fontWeight: 600 }}>Nothing on automatic billing for this month</div>
                <div style={{ fontSize: 13, marginTop: 4 }}>Put a deployment on automatic billing from its hire contract dialog.</div>
              </div>
            ) : (
              <>
                {section("Needs a person", of("EXCEPTION"), "These are left out of the run until fixed. Fix the cause, then run the month again.")}
                {section("Ready", of("READY"))}
                {section("Already billed", of("ALREADY_BILLED"))}
              </>
            )}
        </>
      )}

      <div style={{ marginTop: 22 }}>
        <div style={{ fontWeight: 700, fontSize: 13, marginBottom: 6 }}>Past runs</div>
        {(history.data?.runs ?? []).length === 0 ? <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No runs yet.</div> : (
          <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
            {history.data?.runs.map(r => (
              <div key={r.id} style={{ ...card, fontSize: 12, display: "flex", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}>
                <span><strong>{r.month}</strong> · {r.trigger === "SCHEDULED" ? "scheduled" : "by a person"} · {fmtDateTime(r.createdAt)}</span>
                <span>{r.chargesCreated} charges · {r.invoicesCreated} draft invoices · {r.alreadyBilled} already billed · {r.exceptions} needed a person</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
