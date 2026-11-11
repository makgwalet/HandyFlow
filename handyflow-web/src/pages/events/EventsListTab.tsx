// src/pages/events/EventsListTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  Plus, Calendar, MapPin, Users, Clock, ChevronRight,
  Radio, CheckCircle, XCircle, Eye, Megaphone, Search, Pencil, CalendarClock, Copy,
} from "lucide-react"
import { usePermissions } from "../../hooks/usePermission"
import { ErrorLine } from "../warehousing/whseUi"
import EventWizard from "./EventWizard"
import { CancelEventModal, DuplicateModal, EVENT_TYPES, EventFormModal, PostponeModal } from "./EventDialogs"
import { canCancel, canComplete, canDuplicate, canEdit, canGoLive, canPostpone, canPublish } from "./eventActions"

const fmtDT = (d: any) => d ? new Date(d).toLocaleString("en-ZA", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" }) : "—"
const fmtD  = (d: any) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"

const STATUS_CFG: Record<string, { label: string; color: string; bg: string; dot: string }> = {
  DRAFT:     { label: "Draft",     color: "var(--hf-text-muted)", bg: "var(--hf-surface-sunken)", dot: "var(--hf-text-faint)" },
  PUBLISHED: { label: "Published", color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)", dot: "var(--hf-success)" },
  SOLD_OUT:  { label: "Sold out",  color: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)", dot: "var(--hf-warning)" },
  LIVE:      { label: "Live",      color: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)", dot: "var(--hf-danger)" },
  COMPLETED: { label: "Completed", color: "var(--hf-sky-text)", bg: "var(--hf-sky-soft-strong)", dot: "var(--hf-sky-dot)" },
  CANCELLED: { label: "Cancelled", color: "var(--hf-text-faint)", bg: "var(--hf-surface-muted)", dot: "var(--hf-border-strong)" },
}
const TYPE_COLOR:  Record<string, string> = {
  CONFERENCE: "var(--hf-info-text)", WEDDING: "var(--hf-pink-text)", CHURCH: "var(--hf-violet-text)",
  FESTIVAL: "var(--hf-warning-text)", CORPORATE: "var(--hf-sky-text)", COMMUNITY: "var(--hf-success-text)",
  FUNDRAISER: "var(--hf-orange-text)", GENERAL: "var(--hf-text-muted)",
}

const SECONDARY: React.CSSProperties = { display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", background: "var(--hf-surface-muted)", color: "var(--hf-text-secondary)", border: "1px solid var(--hf-border)", borderRadius: 7, fontSize: 12, cursor: "pointer" }

interface Props { onSelectEvent: (id: string, title: string) => void }

export default function EventsListTab({ onSelectEvent }: Props) {
  const qc = useQueryClient()
  const [filterStatus, setFilterStatus] = useState("ALL")
  const [filterType,   setFilterType]   = useState("ALL")
  const [search,       setSearch]       = useState("")
  const [dialog, setDialog] = useState<{ kind: "create" } | { kind: "edit" | "postpone" | "copy" | "cancel"; event: any } | null>(null)
  const canWrite = usePermissions(["EVENTS_WRITE", "EVENTS_MANAGE"])
  const canApprove = usePermissions(["EVENTS_APPROVE", "EVENTS_MANAGE"])
  const canCancelEvents = usePermissions(["EVENTS_CANCEL", "EVENTS_MANAGE"])

  const { data: events = [], isLoading } = useQuery<any[]>({
    queryKey: ["events", filterStatus, filterType],
    queryFn: async () => {
      const params = new URLSearchParams({ size: "100" })
      if (filterStatus !== "ALL") params.set("status", filterStatus)
      if (filterType   !== "ALL") params.set("type", filterType)
      const r = await apiClient.get(`/api/v1/events?${params}`)
      const p = r.data?.data ?? r.data
      return p?.content ?? p ?? []
    },
  })

  const transition = useMutation({
    mutationFn: ({ id, action }: { id: string; action: string }) =>
      apiClient.post(`/api/v1/events/${id}/${action}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["events"] }); qc.invalidateQueries({ queryKey: ["events-kpi"] }) },
  })

  const filtered = (events as any[]).filter(e => {
    if (search && !e.title?.toLowerCase().includes(search.toLowerCase()) &&
        !e.venueName?.toLowerCase().includes(search.toLowerCase())) return false
    return true
  })

  return (
    <div>
      {/* Toolbar */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18, flexWrap: "wrap", gap: 10 }}>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          <div style={{ position: "relative" as const }}>
            <Search size={13} style={{ position: "absolute" as const, left: 9, top: "50%", transform: "translateY(-50%)", color: "var(--hf-text-faint)" }} />
            <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search events..."
              style={{ paddingLeft: 28, padding: "7px 10px 7px 28px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, outline: "none", width: 220 }} />
          </div>
          <select value={filterStatus} onChange={e => setFilterStatus(e.target.value)}
            style={{ padding: "7px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, outline: "none", background: "var(--hf-surface)" }}>
            <option value="ALL">All statuses</option>
            {Object.entries(STATUS_CFG).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
          </select>
          <select value={filterType} onChange={e => setFilterType(e.target.value)}
            style={{ padding: "7px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, outline: "none", background: "var(--hf-surface)" }}>
            <option value="ALL">All types</option>
            {EVENT_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
          </select>
        </div>
        {canWrite && <button onClick={() => setDialog({ kind: "create" })}
          style={{ display: "flex", alignItems: "center", gap: 7, background: "var(--hf-sky)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={15} /> New Event
        </button>}
      </div>

      <ErrorLine error={transition.error} fallback="That action could not be completed" />
      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "var(--hf-text-faint)" }}>Loading events...</div>
      ) : filtered.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "var(--hf-text-faint)" }}>
          <Calendar size={40} style={{ marginBottom: 12, opacity: 0.3 }} />
          <div style={{ fontWeight: 600, color: "var(--hf-text-tertiary)" }}>No events yet</div>
          <div style={{ fontSize: 13, marginTop: 4 }}>Create your first event to start managing registrations.</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {filtered.map((e: any) => {
            const sc  = STATUS_CFG[e.status] ?? STATUS_CFG.DRAFT
            const tc  = TYPE_COLOR[e.eventType] ?? "var(--hf-text-muted)"
            const isLive = e.status === "LIVE"
            return (
              <div key={e.id} style={{
                border: `1px solid ${isLive ? "var(--hf-danger-border)" : "var(--hf-border)"}`,
                borderLeft: `3px solid ${isLive ? "var(--hf-danger)" : tc}`,
                borderRadius: 10, padding: "16px 20px", background: isLive ? "color-mix(in srgb, var(--hf-danger-soft) 40%, var(--hf-surface))" : "var(--hf-surface)",
                display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12, flexWrap: "wrap",
              }}>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 6, flexWrap: "wrap" }}>
                    {isLive && <span style={{ display: "flex", alignItems: "center", gap: 4 }}>
                      <Radio size={12} style={{ color: 'var(--hf-danger-text)' }} /><span style={{ fontSize: 11, color: "var(--hf-danger-text)", fontWeight: 700 }}>LIVE</span>
                    </span>}
                    <span style={{ fontWeight: 700, fontSize: 15, color: "var(--hf-text)" }}>{e.title}</span>
                    <span style={{ fontFamily: "monospace", fontSize: 11, color: "var(--hf-text-faint)" }}>{e.eventNumber}</span>
                    <span style={{ background: `color-mix(in srgb, ${tc} 9%, transparent)`, color: tc, padding: "1px 7px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{e.eventType}</span>
                    <span style={{ background: sc.bg, color: sc.color, padding: "1px 7px", borderRadius: 20, fontSize: 11, fontWeight: 700, display: "flex", alignItems: "center", gap: 4 }}>
                      <span style={{ width: 6, height: 6, borderRadius: "50%", background: sc.dot, display: "inline-block" }} />{sc.label}
                    </span>
                    {e.isFree && <span style={{ background: "var(--hf-success-soft)", color: "var(--hf-success-text-strong)", padding: "1px 7px", borderRadius: 20, fontSize: 10, fontWeight: 700 }}>Free</span>}
                    {e.isPrivate && <span style={{ background: "var(--hf-violet-soft)", color: "var(--hf-violet-text)", padding: "1px 7px", borderRadius: 20, fontSize: 10, fontWeight: 700 }}>Private</span>}
                  </div>
                  <div style={{ display: "flex", gap: 16, fontSize: 12, color: "var(--hf-text-muted)", flexWrap: "wrap" }}>
                    <span style={{ display: "flex", alignItems: "center", gap: 4 }}><Calendar size={11} />{fmtDT(e.startDatetime)}</span>
                    {e.venueName && <span style={{ display: "flex", alignItems: "center", gap: 4 }}><MapPin size={11} />{e.venueName}</span>}
                    {e.venueCapacity && <span style={{ display: "flex", alignItems: "center", gap: 4 }}><Users size={11} />Cap: {e.venueCapacity}</span>}
                    {e.registrationDeadline && <span style={{ display: "flex", alignItems: "center", gap: 4 }}><Clock size={11} />Reg closes: {fmtD(e.registrationDeadline)}</span>}
                  </div>
                </div>

                {/* Actions */}
                <div style={{ display: "flex", gap: 7, flexShrink: 0, flexWrap: "wrap" }}>
                  {canApprove && canPublish(e.status) && (
                    <button onClick={() => transition.mutate({ id: e.id, action: "publish" })}
                      style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", background: "var(--hf-success-soft-strong)", color: "var(--hf-success-text-strong)", border: "1px solid var(--hf-success-border)", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                      <Megaphone size={12} /> Publish
                    </button>
                  )}
                  {canApprove && canGoLive(e.status) && (
                    <button onClick={() => transition.mutate({ id: e.id, action: "go-live" })}
                      style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", background: "var(--hf-danger-soft)", color: "var(--hf-danger-text)", border: "1px solid var(--hf-danger-border)", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                      <Radio size={12} /> Go Live
                    </button>
                  )}
                  {canApprove && canComplete(e.status) && (
                    <button onClick={() => transition.mutate({ id: e.id, action: "complete" })}
                      style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", background: "var(--hf-sky-soft-strong)", color: "var(--hf-sky-text)", border: "1px solid var(--hf-sky-border)", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                      <CheckCircle size={12} /> Complete
                    </button>
                  )}
                  {canWrite && canEdit(e.status) && <button onClick={() => setDialog({ kind: "edit", event: e })} style={SECONDARY}><Pencil size={12} /> Edit</button>}
                  {canWrite && canPostpone(e.status) && <button onClick={() => setDialog({ kind: "postpone", event: e })} style={SECONDARY}><CalendarClock size={12} /> Postpone</button>}
                  {canWrite && canDuplicate(e.status) && <button onClick={() => setDialog({ kind: "copy", event: e })} style={SECONDARY}><Copy size={12} /> Copy</button>}
                  {canCancelEvents && canCancel(e.status) && <button onClick={() => setDialog({ kind: "cancel", event: e })} style={SECONDARY}><XCircle size={12} /> Cancel</button>}
                  <button onClick={() => onSelectEvent(e.id, e.title)}
                    style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", background: "var(--hf-sky)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                    <Eye size={12} /> Guests <ChevronRight size={12} />
                  </button>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {dialog?.kind === "create" && <EventWizard onClose={() => setDialog(null)} onCreated={(id, title) => { setDialog(null); onSelectEvent(id, title) }} />}
      {dialog?.kind === "edit" && <EventFormModal event={dialog.event} onClose={() => setDialog(null)} />}
      {dialog?.kind === "postpone" && <PostponeModal event={dialog.event} onClose={() => setDialog(null)} />}
      {dialog?.kind === "copy" && <DuplicateModal event={dialog.event} onClose={() => setDialog(null)} />}
      {dialog?.kind === "cancel" && <CancelEventModal event={dialog.event} onClose={() => setDialog(null)} />}
    </div>
  )
}
