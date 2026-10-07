// src/pages/invoicing/RecurringDetailPage.tsx  (route: /recurring/:id)
//
// One recurring schedule: what it bills and how often, when it runs next, the contract terms of a variable-hours
// schedule, and the invoices it has created so far.
import { useState } from "react"
import { Link, useNavigate, useParams } from "react-router-dom"
import { Gauge, Pause, Play, RefreshCw, Timer, XCircle } from "lucide-react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { PageHeader } from "../../components/ui/PageHeader"
import Chip from "../../components/ui/Chip"
import { usePermission } from "../../hooks/usePermission"
import {
  INVOICE_LABEL, INVOICE_TONE, RECURRING_LABEL, RECURRING_TONE, balanceOf, fmtDate, fmtR, frequencyLabel, label, monthlyValue, nextRunLabel,
  type RecurringSchedule,
} from "./billing.logic"
import { CycleHoursDialog } from "./dialogs"
import { apiMessage, useCustomerNames, useInvoices } from "./queries"
import { Btn, Facts, Loading, Notice, StateBox, Totals, panel, sectionTitle, td } from "./ui"

export default function RecurringDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const canCreate = usePermission("INVOICE_CREATE")
  const canDelete = usePermission("INVOICE_DELETE")
  const names = useCustomerNames()
  const [logging, setLogging] = useState(false)
  const [msg, setMsg] = useState("")

  const { data: s, isLoading, isError } = useQuery<RecurringSchedule>({
    queryKey: ["recurring", id], enabled: !!id,
    queryFn: async () => { const r = await apiClient.get(`/api/v1/invoicing/recurring-schedules/${id}`); return r.data?.data ?? r.data },
  })
  const { data: invoices } = useInvoices()
  const refresh = () => { qc.invalidateQueries({ queryKey: ["recurring", id] }); qc.invalidateQueries({ queryKey: ["recurring-schedules"] }) }
  const act = (path: string, method: "post" | "delete", fallback: string) => ({
    mutationFn: () => (method === "post" ? apiClient.post(path) : apiClient.delete(path)),
    onSuccess: () => { setMsg(""); refresh() },
    onError: (e: any) => setMsg(apiMessage(e, fallback)),
  })
  const base = `/api/v1/invoicing/recurring-schedules/${id}`
  const pause = useMutation(act(`${base}/pause`, "post", "The schedule could not be paused."))
  const resume = useMutation(act(`${base}/resume`, "post", "The schedule could not be resumed."))
  const cancel = useMutation(act(base, "delete", "The schedule could not be cancelled."))

  if (isLoading) return <Loading text="Loading schedule..." />
  if (isError || !s) return <StateBox icon={RefreshCw} tone="bad" title="Schedule not found" text="It may have been removed, or you may not have access." action={<Btn onClick={() => navigate("/recurring")}>Back to recurring billing</Btn>} />

  const generated = (invoices?.rows ?? []).filter(i => i.recurringScheduleId === s.id)
  const run = nextRunLabel(s)
  const customer = s.customerId ? names[s.customerId] ?? "Customer" : s.walkinClientName ? `${s.walkinClientName} (walk-in)` : "Walk-in client"
  const live = s.status === "ACTIVE" || s.status === "PAUSED"

  return (
    <div style={{ maxWidth: 1000 }}>
      <PageHeader breadcrumbs={[{ label: "Recurring billing", to: "/recurring" }, { label: s.title }]} icon={RefreshCw} title={s.title}
        subtitle={<span style={{ display: "inline-flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
          <Chip tone={RECURRING_TONE[s.status] ?? "neutral"}>{label(RECURRING_LABEL, s.status)}</Chip>
          {s.variableHours && <Chip tone="warn" icon={<Gauge size={10} />}>Variable hours</Chip>}
          <span>{customer} · {frequencyLabel(s)}</span>
        </span>}
        action={canCreate && <>
          {s.status === "ACTIVE" && <Btn icon={Pause} onClick={() => pause.mutate()} disabled={pause.isPending}>Pause</Btn>}
          {s.status === "PAUSED" && <Btn variant="success" icon={Play} onClick={() => resume.mutate()} disabled={resume.isPending}>Resume</Btn>}
          {s.variableHours && s.status === "ACTIVE" && <Btn variant="primary" icon={Timer} onClick={() => setLogging(true)}>Log cycle hours</Btn>}
          {canDelete && live && <Btn icon={XCircle} onClick={() => { if (window.confirm(`Cancel the recurring schedule "${s.title}"? No more invoices will be created.`)) cancel.mutate() }} disabled={cancel.isPending}>Cancel schedule</Btn>}
        </>} />
      {msg && <div style={{ marginBottom: 14 }}><Notice tone="bad">{msg}</Notice></div>}
      {s.status === "ACTIVE" && run === "Run overdue" && <div style={{ marginBottom: 14 }}><Notice tone="warn">The next run date has passed without an invoice being created. The nightly run at 02:45 picks it up, or check that the customer is still active.</Notice></div>}

      <div style={{ display: "flex", flexWrap: "wrap", gap: 16, alignItems: "flex-start" }}>
        <div style={{ display: "grid", gap: 16, flex: "2 1 480px", minWidth: 0 }}>
          {s.variableHours ? (
            <section style={{ ...panel, padding: 18 }} aria-label="Contract">
              <p style={sectionTitle}>Variable-hours contract</p>
              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(130px, 1fr))", gap: 12 }}>
                {[["Rate", `${fmtR(s.ratePerHour)}/hr`], ["Minimum a cycle", s.minimumHoursPerCycle != null ? `${s.minimumHoursPerCycle}h` : "—"],
                  ["Hours billed", `${s.totalHoursBilled ?? 0}h`], ["Contracted total", s.contractedTotalHours != null ? `${s.contractedTotalHours}h` : "Not set"]].map(([k, v]) => (
                  <div key={k}><div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{k}</div><div style={{ fontSize: 17, fontWeight: 800, color: "var(--hf-text-primary)" }}>{v}</div></div>))}
              </div>
              <div style={{ marginTop: 12, fontSize: 12.5, color: "var(--hf-text-muted)" }}>
                {s.remainingCycles >= 0 ? `About ${s.remainingCycles} cycle${s.remainingCycles === 1 ? "" : "s"} left on the contract` : "Open-ended contract, no end date"}
                {s.contractStartDate && ` · started ${fmtDate(s.contractStartDate)}`}{s.contractEndDate && ` · ends ${fmtDate(s.contractEndDate)}`}
              </div>
            </section>
          ) : (
            <section style={{ ...panel, overflow: "hidden" }} aria-label="Template line items">
              <p style={{ ...sectionTitle, padding: "16px 18px 0", margin: "0 0 10px" }}>Line items on each invoice</p>
              <div style={{ overflowX: "auto" }}>
                <table style={{ width: "100%", borderCollapse: "collapse" }}>
                  <thead><tr style={{ background: "var(--hf-surface-muted)" }}>
                    {["Description", "Qty", "Unit price", "VAT", "Total"].map((h, i) => <th key={h} style={{ textAlign: i === 0 ? "left" : "right", padding: "9px 14px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase" }}>{h}</th>)}
                  </tr></thead>
                  <tbody>
                    {s.lineItems.length === 0 ? <tr><td colSpan={5} style={{ ...td, textAlign: "center", color: "var(--hf-text-faint)", padding: 24 }}>No line items yet. Invoices will be empty until some are added.</td></tr> :
                      s.lineItems.map((li, i) => (
                        <tr key={li.id ?? i} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                          <td style={td}>{li.description}</td><td style={{ ...td, textAlign: "right" }}>{li.quantity}</td><td style={{ ...td, textAlign: "right" }}>{fmtR(li.unitPrice)}</td>
                          <td style={{ ...td, textAlign: "right" }}>{li.vatRate ?? 15}%</td><td style={{ ...td, textAlign: "right", fontWeight: 700, color: "var(--hf-text-primary)" }}>{fmtR(li.lineTotal)}</td>
                        </tr>))}
                  </tbody>
                </table>
              </div>
              <div style={{ display: "flex", justifyContent: "flex-end", padding: "14px 18px 18px" }}>
                <Totals rows={[["Subtotal", fmtR(s.subtotal)], ["VAT", fmtR(s.vatTotal)]]} total={fmtR(s.total)} totalLabel="Per invoice" />
              </div>
            </section>
          )}

          <section style={{ ...panel, overflow: "hidden" }} aria-label="Invoices created">
            <p style={{ ...sectionTitle, padding: "16px 18px 0", margin: "0 0 10px" }}>Invoices created · {generated.length}</p>
            {generated.length === 0 ? <div style={{ padding: "0 18px 18px", fontSize: 13, color: "var(--hf-text-faint)" }}>No invoices have been created by this schedule yet.</div> : (
              <div style={{ overflowX: "auto" }}>
                <table style={{ width: "100%", borderCollapse: "collapse" }}>
                  <thead><tr style={{ background: "var(--hf-surface-muted)" }}>
                    {["Invoice", "Issued", "Total", "Balance", "Status"].map((h, i) => <th key={h} style={{ textAlign: i === 2 || i === 3 ? "right" : "left", padding: "9px 14px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase" }}>{h}</th>)}
                  </tr></thead>
                  <tbody>
                    {generated.map(i => (
                      <tr key={i.id} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                        <td style={td}><Link to={`/invoices/${i.id}`} style={{ fontWeight: 700 }}>{i.invoiceNumber}</Link></td>
                        <td style={td}>{fmtDate(i.issuedAt ?? i.createdAt)}</td>
                        <td style={{ ...td, textAlign: "right" }}>{fmtR(i.total)}</td>
                        <td style={{ ...td, textAlign: "right" }}>{i.status === "DRAFT" || i.status === "CANCELLED" ? "—" : fmtR(balanceOf(i))}</td>
                        <td style={td}><Chip tone={INVOICE_TONE[i.status] ?? "neutral"}>{label(INVOICE_LABEL, i.status)}</Chip></td>
                      </tr>))}
                  </tbody>
                </table>
              </div>)}
          </section>
        </div>

        <aside style={{ display: "grid", gap: 16, flex: "1 1 260px", minWidth: 0 }}>
          <section style={{ ...panel, padding: 18 }} aria-label="Schedule">
            <p style={sectionTitle}>Schedule</p>
            <Facts rows={[
              ["Frequency", frequencyLabel(s)],
              ["Next run", s.status === "ACTIVE" ? fmtDate(s.nextRunAt) : "—"],
              ...(run ? [["", run] as [string, string]] : []),
              ["Last run", fmtDate(s.lastRunAt)],
              ["Per invoice", s.variableHours ? `${fmtR(s.ratePerHour)}/hr` : fmtR(s.total)],
              ["Per month, about", s.variableHours ? "—" : fmtR(monthlyValue(s))],
              ["Created", fmtDate(s.createdAt)],
            ]} />
          </section>
          <section style={{ ...panel, padding: 18 }} aria-label="Customer">
            <p style={sectionTitle}>Customer</p>
            <div style={{ fontSize: 14, fontWeight: 700, color: "var(--hf-text-primary)" }}>{customer}</div>
            {s.customerId && <div style={{ marginTop: 6, fontSize: 13 }}><Link to="/customers">View in CRM</Link></div>}
          </section>
        </aside>
      </div>
      {logging && <CycleHoursDialog schedule={s} onClose={() => setLogging(false)} onDone={refresh} />}
    </div>
  )
}
