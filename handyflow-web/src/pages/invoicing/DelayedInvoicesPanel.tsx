// src/pages/invoicing/DelayedInvoicesPanel.tsx
//
// Recurring invoices held back by the hard invoice cap. They are delayed, never skipped: each waits with its original
// billing date and the period it covers, and is issued once, on the day the plan has room.
import { useQuery } from "@tanstack/react-query"
import { Hourglass } from "lucide-react"
import { apiClient } from "../../api/client"
import Chip from "../../components/ui/Chip"
import { fmtDate } from "./billing.logic"
import { Notice, TableShell, Th, td, trStyle, sectionTitle } from "./ui"

export interface DelayedEntry {
  id: string; scheduleId: string; scheduleTitle: string; scheduledFor: string; periodStart: string; periodEnd: string
  status: "QUEUED" | "ISSUED"; blockedReason?: string | null; escalatedAt?: string | null; invoiceId?: string | null; issuedAt?: string | null
}
export interface DelayedItem { entry: DelayedEntry; daysDelayed: number; overdue: boolean }
export interface DelayedInvoices { maxDelayDays: number; waiting: number; overdue: number; items: DelayedItem[] }

export const useDelayedInvoices = () => useQuery<DelayedInvoices | null>({
  queryKey: ["recurring-delayed"],
  queryFn: async () => {
    const r: any = await apiClient.get("/api/v1/invoicing/recurring-schedules/delayed")
    const d = r.data?.data ?? r.data
    return d && Array.isArray(d.items) ? d : null
  },
})

export function DelayedInvoicesPanel() {
  const { data } = useDelayedInvoices()
  if (!data || data.items.length === 0) return null
  const waiting = data.items.filter(i => i.entry.status === "QUEUED")
  return (
    <section aria-label="Delayed recurring invoices" style={{ marginBottom: 18 }}>
      <h2 style={sectionTitle}>Delayed recurring invoices</h2>
      {waiting.length > 0 && <div style={{ marginBottom: 10 }}><Notice tone={data.overdue > 0 ? "bad" : "warn"}>
        <Hourglass size={14} aria-hidden="true" style={{ marginTop: 2, flexShrink: 0 }} />
        <span><strong>{data.waiting} recurring invoice{data.waiting === 1 ? " is" : "s are"} waiting</strong> because your plan's monthly invoice allowance is used up.
          They are not skipped: each is issued, once, with the day it is issued as its date, as soon as there is room (a new month or a plan upgrade).
          {data.overdue > 0 && <> <strong>{data.overdue} {data.overdue === 1 ? "has" : "have"} waited longer than {data.maxDelayDays} days.</strong></>}</span>
      </Notice></div>}
      <TableShell>
        <thead><tr style={{ background: "var(--hf-surface-muted)" }}>
          <Th>Schedule</Th><Th>Due on</Th><Th>Service period</Th><Th>Waiting</Th><Th>Status</Th>
        </tr></thead>
        <tbody>
          {data.items.map(({ entry: e, daysDelayed, overdue }) => (
            <tr key={e.id} style={trStyle(false)}>
              <td style={td}>{e.scheduleTitle}{e.blockedReason && <div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{e.blockedReason}</div>}</td>
              <td style={td}>{fmtDate(e.scheduledFor)}</td>
              <td style={td}>{fmtDate(e.periodStart)} to {fmtDate(e.periodEnd)}</td>
              <td style={td}>{e.status === "QUEUED" ? `${daysDelayed} day${daysDelayed === 1 ? "" : "s"}` : "—"}</td>
              <td style={td}>{e.status === "ISSUED" ? <Chip tone="ok">Issued {fmtDate(e.issuedAt)}</Chip>
                : overdue ? <Chip tone="bad">Overdue — waiting more than {data.maxDelayDays} days</Chip> : <Chip tone="warn">Waiting for room</Chip>}</td>
            </tr>
          ))}
        </tbody>
      </TableShell>
    </section>
  )
}
