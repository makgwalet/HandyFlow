// src/pages/invoicing/RecurringListPage.tsx  (route: /recurring)
import { useMemo, useState } from "react"
import { useNavigate } from "react-router-dom"
import { CalendarClock, Gauge, Pause, Play, Plus, RefreshCw, Timer, TrendingUp, XCircle } from "lucide-react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { PageHeader } from "../../components/ui/PageHeader"
import Chip from "../../components/ui/Chip"
import { usePermission } from "../../hooks/usePermission"
import {
  RECURRING_LABEL, RECURRING_TONE, countBy, fmtDate, fmtR, fmtRShort, frequencyLabel, isTruncated, label, LIST_LIMIT, matches, nextRunLabel, paginate, partyName,
  recurringStats, sortBy, type RecurringSchedule, type SortDir,
} from "./billing.logic"
import { CycleHoursDialog } from "./dialogs"
import { apiMessage, useCustomerNames, useSchedules } from "./queries"
import { BillingNav, Btn, FilterPills, Kpis, Loading, LoadError, Notice, Pager, SearchBox, StateBox, TableShell, Th, td, trStyle } from "./ui"

const STATUSES = ["ALL", "ACTIVE", "PAUSED", "COMPLETED", "CANCELLED"]
const KINDS = [{ value: "ALL", label: "All kinds" }, { value: "FIXED", label: "Fixed amount" }, { value: "VARIABLE", label: "Variable hours" }]

export default function RecurringListPage() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const canCreate = usePermission("INVOICE_CREATE")
  const canDelete = usePermission("INVOICE_DELETE")
  const { data, isLoading, isError, refetch } = useSchedules()
  const names = useCustomerNames()
  const [status, setStatus] = useState("ALL")
  const [kind, setKind] = useState("ALL")
  const [search, setSearch] = useState("")
  const [sort, setSort] = useState<{ key: string; dir: SortDir }>({ key: "nextRunAt", dir: "asc" })
  const [page, setPage] = useState(0)
  const [logging, setLogging] = useState<RecurringSchedule | null>(null)
  const [msg, setMsg] = useState("")

  const schedules = data?.rows ?? []
  const stats = useMemo(() => recurringStats(schedules), [schedules])
  const counts = useMemo(() => countBy(schedules, s => s.status), [schedules])

  const setRunning = (action: "pause" | "resume") => ({
    mutationFn: (id: string) => apiClient.post(`/api/v1/invoicing/recurring-schedules/${id}/${action}`),
    onSuccess: () => { setMsg(""); qc.invalidateQueries({ queryKey: ["recurring-schedules"] }) },
    onError: (e: any) => setMsg(apiMessage(e, `The schedule could not be ${action === "pause" ? "paused" : "resumed"}.`)),
  })
  const pause = useMutation(setRunning("pause"))
  const resume = useMutation(setRunning("resume"))
  const cancel = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/invoicing/recurring-schedules/${id}`),
    onSuccess: () => { setMsg(""); qc.invalidateQueries({ queryKey: ["recurring-schedules"] }) },
    onError: (e: any) => setMsg(apiMessage(e, "The schedule could not be cancelled.")),
  })

  const view = useMemo(() => {
    const filtered = schedules.filter(s => (status === "ALL" || s.status === status)
      && (kind === "ALL" || (kind === "VARIABLE") === !!s.variableHours)
      && matches(search, s.title, partyName(s.customerId, s.walkinClientName, names)))
    const keys: Record<string, (s: RecurringSchedule) => string | number | null> = {
      title: s => s.title, customer: s => partyName(s.customerId, s.walkinClientName, names), frequency: s => s.frequency,
      total: s => Number(s.total), nextRunAt: s => (s.status === "ACTIVE" ? s.nextRunAt : null), status: s => s.status,
    }
    return paginate(sortBy(filtered, keys[sort.key] ?? keys.nextRunAt, sort.dir), page)
  }, [schedules, status, kind, search, sort, page, names])

  const onSort = (key: string) => { setSort(s => ({ key, dir: s.key === key && s.dir === "asc" ? "desc" : "asc" })); setPage(0) }
  const th = { active: sort.key, dir: sort.dir, onSort }

  return (
    <div>
      <PageHeader title="Recurring billing" icon={RefreshCw} subtitle="Quotes, invoices, recurring billing and retainers"
        action={canCreate && <>
          <Btn icon={Gauge} onClick={() => navigate("/recurring/variable-hours/new")}>Variable-hours contract</Btn>
          <Btn variant="primary" icon={Plus} onClick={() => navigate("/recurring/new")}>New schedule</Btn>
        </>} />
      <BillingNav />

      <Kpis items={[
        { label: "Monthly recurring revenue", value: fmtRShort(stats.mrr), hint: "active schedules, per month", icon: TrendingUp, tone: "ok" },
        { label: "Active schedules", value: stats.active, hint: `${stats.paused} paused`, icon: RefreshCw, tone: "info", onClick: () => setStatus("ACTIVE") },
        { label: "Run in the next 7 days", value: stats.dueThisWeek, hint: "invoices about to be created", icon: CalendarClock, tone: "neutral" },
        { label: "Variable-hours contracts", value: stats.variable, hint: "need hours logged each cycle", icon: Timer, tone: stats.variable > 0 ? "warn" : "neutral", onClick: () => setKind("VARIABLE") },
      ]} />

      <div style={{ marginBottom: 14 }}><Notice tone="ok"><RefreshCw size={14} aria-hidden="true" style={{ marginTop: 2, flexShrink: 0 }} />
        <span>Active schedules run automatically at <strong>02:45 every morning</strong>. Each run creates an issued invoice linked back to its schedule.</span></Notice></div>

      <div style={{ display: "flex", gap: 12, flexWrap: "wrap", alignItems: "center", marginBottom: 10 }}>
        <SearchBox value={search} onChange={v => { setSearch(v); setPage(0) }} placeholder="Search schedule or customer" />
        <FilterPills label="Schedule kind" value={kind} onChange={v => { setKind(v); setPage(0) }} options={KINDS} />
      </div>
      <div style={{ marginBottom: 14 }}>
        <FilterPills label="Schedule status" value={status} onChange={v => { setStatus(v); setPage(0) }}
          options={STATUSES.map(s => ({ value: s, label: s === "ALL" ? "All" : label(RECURRING_LABEL, s), count: s === "ALL" ? schedules.length : counts[s] ?? 0 }))} />
      </div>
      {msg && <div style={{ marginBottom: 12 }}><Notice tone="bad">{msg}</Notice></div>}
      {isTruncated(data?.total, schedules.length) && <div style={{ marginBottom: 12 }}><Notice tone="info">Showing the newest {LIST_LIMIT} of {data?.total} schedules.</Notice></div>}

      {isLoading ? <Loading text="Loading schedules..." /> : isError ? <LoadError onRetry={() => refetch()} /> :
        view.total === 0 ? (
          <StateBox icon={RefreshCw} title={schedules.length === 0 ? "No recurring schedules yet" : "No schedules match"}
            text={schedules.length === 0 ? "Set up automatic invoicing for site fees, equipment hire or any regular service." : "Try a different search, status or kind."}
            action={schedules.length === 0 && canCreate ? <Btn variant="primary" icon={Plus} onClick={() => navigate("/recurring/new")}>Create the first schedule</Btn> : undefined} />
        ) : (
          <>
            <TableShell>
              <thead><tr style={{ background: "var(--hf-surface-muted)" }}>
                <Th sortKey="title" {...th}>Schedule</Th><Th sortKey="customer" {...th}>Customer</Th><Th sortKey="frequency" {...th}>Frequency</Th>
                <Th sortKey="total" align="right" {...th}>Per invoice</Th><Th sortKey="nextRunAt" {...th}>Next run</Th><Th sortKey="status" {...th}>Status</Th><Th>Actions</Th>
              </tr></thead>
              <tbody>
                {view.rows.map(s => {
                  const run = nextRunLabel(s)
                  return (
                    <tr key={s.id} style={trStyle()} onClick={() => navigate(`/recurring/${s.id}`)} tabIndex={0} onKeyDown={e => { if (e.key === "Enter") navigate(`/recurring/${s.id}`) }}>
                      <td style={td}>
                        <div style={{ fontWeight: 700, color: "var(--hf-text-primary)" }}>{s.title}</div>
                        {s.variableHours && <div style={{ marginTop: 3 }}><Chip tone="warn" icon={<Gauge size={10} />}>Variable hours</Chip></div>}
                      </td>
                      <td style={td}>{partyName(s.customerId, s.walkinClientName, names)}</td>
                      <td style={td}>{frequencyLabel(s)}</td>
                      <td style={{ ...td, textAlign: "right", fontWeight: 700, color: "var(--hf-text-primary)" }}>{s.variableHours ? `${fmtR(s.ratePerHour)}/hr` : fmtR(s.total)}</td>
                      <td style={td}>{s.status === "ACTIVE" ? fmtDate(s.nextRunAt) : "—"}{run && <div style={{ fontSize: 11, color: run === "Run overdue" ? "var(--hf-danger-text)" : "var(--hf-text-muted)" }}>{run}</div>}</td>
                      <td style={td}><Chip tone={RECURRING_TONE[s.status] ?? "neutral"}>{label(RECURRING_LABEL, s.status)}</Chip></td>
                      <td style={td} onClick={e => e.stopPropagation()}>
                        {canCreate && <div style={{ display: "flex", gap: 6 }}>
                          {s.status === "ACTIVE" && <Btn small icon={Pause} onClick={() => pause.mutate(s.id)} disabled={pause.isPending}>Pause</Btn>}
                          {s.status === "PAUSED" && <Btn small variant="success" icon={Play} onClick={() => resume.mutate(s.id)} disabled={resume.isPending}>Resume</Btn>}
                          {s.variableHours && s.status === "ACTIVE" && <Btn small icon={Timer} onClick={() => setLogging(s)}>Log hours</Btn>}
                          {canDelete && (s.status === "ACTIVE" || s.status === "PAUSED") &&
                            <Btn small icon={XCircle} aria-label={`Cancel ${s.title}`} onClick={() => { if (window.confirm(`Cancel the recurring schedule "${s.title}"? No more invoices will be created.`)) cancel.mutate(s.id) }} disabled={cancel.isPending}>Cancel</Btn>}
                        </div>}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </TableShell>
            <Pager {...view} onPage={setPage} />
          </>
        )}
      {logging && <CycleHoursDialog schedule={logging} onClose={() => setLogging(null)} />}
    </div>
  )
}
