// src/pages/legalcompliance/RegulatoryChangesTab.tsx
//
// "What did this regulatory change do to us, and what must we decide?" Endpoints (RegulatoryChangeController,
// /api/v1/legalcompliance/changes):
//   GET /summary · GET /impacts?status= · GET ?status=&page= · GET /{id} · POST /impacts/{id}/resolve
//
// A change is raised automatically when a new version is published (or the current one repealed). Unchanged requirements are
// carried forward with their decisions; everything else that the organisation has a stake in appears here as an impact that a
// person must resolve. Resolving never edits history: decisions are superseded, obligations are retired or detached with an audit event.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { input, primaryBtn, ghostBtn, unwrap, one, fmtDate, fmtDateTime, errMsg } from "./lcHelpers"
import { Modal, Field, Err, Badge } from "./LcUi"
import { GitCompare, ChevronDown, ChevronUp } from "lucide-react"

interface Change {
  id: string; instrumentId: string; instrumentTitle: string; fromLabel: string | null; toLabel: string | null; changeKind: string
  status: string; summary: string | null; addedCount: number; changedCount: number; removedCount: number; carriedCount: number
  createdByName: string | null; createdAt: string; closedAt: string | null
}
interface Impact {
  id: string; changeId: string; instrumentTitle: string; fromLabel: string | null; toLabel: string | null; impactType: string
  reference: string; title: string; diffSummary: string | null; status: string; outcome: string | null; resolutionNote: string | null
  resolvedByName: string | null; resolvedAt: string | null; dueDate: string; overdue: boolean; obligationId: string | null
}
interface Detail { change: Change; impacts: Impact[] }
interface Summary { pending: number; overdue: number; dueWithin7Days: number; openChanges: number }

const TYPE_CFG: Record<string, { label: string; color: string; bg: string }> = {
  ADDED: { label: "New requirement", color: "#166534", bg: "#DCFCE7" },
  CHANGED: { label: "Amended", color: "#92400E", bg: "#FEF3C7" },
  REMOVED: { label: "Removed", color: "#991B1B", bg: "#FEE2E2" },
}
const OUTCOME: Record<string, string> = {
  ASSESSED: "Assessed", REASSESSED: "Reassessed — still applies", OBLIGATION_UPDATED: "Reassessed — obligation updated",
  REASSESSED_NOT_APPLICABLE: "Reassessed — not applicable (awaiting approval)", OBLIGATION_RETIRED: "Obligation retired",
  OBLIGATION_KEPT_MANUAL: "Obligation kept as hand-managed", ACKNOWLEDGED: "Acknowledged",
}
// Which actions make sense for which impact type — the server enforces the same rules.
const ACTIONS: Record<string, { value: string; label: string; needsNote: boolean; help: string }[]> = {
  ADDED: [{ value: "ACKNOWLEDGE", label: "Acknowledge (already assessed)", needsNote: false, help: "Assess the new requirement on the Applicability tab first, then acknowledge here." }],
  CHANGED: [
    { value: "CONFIRM_APPLICABLE", label: "Still applies — reassess as applicable", needsNote: true, help: "Records a new decision against the amended text and keeps any obligation linked to it." },
    { value: "NO_LONGER_APPLICABLE", label: "No longer applies", needsNote: true, help: "Records a not-applicable decision. It needs approval, and any linked obligation stays live until you retire it." },
    { value: "ACKNOWLEDGE", label: "Acknowledge (already reassessed)", needsNote: false, help: "Use when the amended requirement already has a decision from the Applicability tab." },
  ],
  REMOVED: [
    { value: "RETIRE_OBLIGATION", label: "Retire the obligation", needsNote: true, help: "The duty no longer exists in the law. The obligation is retired and the retirement is logged." },
    { value: "KEEP_OBLIGATION", label: "Keep it as a hand-managed obligation", needsNote: true, help: "The business still wants to track it. It stays, but is no longer linked to the regulation." },
    { value: "ACKNOWLEDGE", label: "Acknowledge (nothing is linked)", needsNote: false, help: "Only available when no live obligation depends on the removed requirement." },
  ],
}
const base = "/api/v1/legalcompliance/changes"

export default function RegulatoryChangesTab() {
  const qc = useQueryClient()
  const hasReview = usePermission("LEGALCOMPLIANCE_CHANGE_REVIEW")
  const hasAdmin = usePermission("LEGALCOMPLIANCE_ADMIN")
  const canReview = hasReview || hasAdmin

  const [view, setView] = useState<"PENDING" | "CHANGES" | "RESOLVED">("PENDING")
  const [expanded, setExpanded] = useState<string | null>(null)
  const [resolve, setResolve] = useState<Impact | null>(null)
  const [form, setForm] = useState({ action: "", justification: "", updateObligation: false })
  const [apiError, setApiError] = useState("")
  const [notice, setNotice] = useState("")

  const { data: summary } = useQuery<Summary>({ queryKey: ["lc-changes-summary"], queryFn: async () => one(await apiClient.get(`${base}/summary`)) })
  const { data: pending = [], isLoading: loadingPending } = useQuery<Impact[]>({
    queryKey: ["lc-impacts", "PENDING"], queryFn: async () => unwrap(await apiClient.get(`${base}/impacts`, { params: { status: "PENDING" } })),
  })
  const { data: resolved = [] } = useQuery<Impact[]>({
    queryKey: ["lc-impacts", "RESOLVED"], enabled: view === "RESOLVED",
    queryFn: async () => unwrap(await apiClient.get(`${base}/impacts`, { params: { status: "RESOLVED" } })),
  })
  const { data: changes = [], isLoading: loadingChanges } = useQuery<Change[]>({
    queryKey: ["lc-changes", "list"], enabled: view === "CHANGES",
    queryFn: async () => unwrap(await apiClient.get(base, { params: { size: 50 } })),
  })
  const { data: detail } = useQuery<Detail>({
    queryKey: ["lc-changes", "detail", expanded], enabled: !!expanded,
    queryFn: async () => one(await apiClient.get(`${base}/${expanded}`)),
  })

  const refresh = () => {
    qc.invalidateQueries({ queryKey: ["lc-changes"] }); qc.invalidateQueries({ queryKey: ["lc-changes-summary"] }); qc.invalidateQueries({ queryKey: ["lc-impacts"] })
    qc.invalidateQueries({ queryKey: ["lc-worklist"] }); qc.invalidateQueries({ queryKey: ["lc-obligations"] }); qc.invalidateQueries({ queryKey: ["lc-obligations-all"] })
    qc.invalidateQueries({ queryKey: ["lc-calendar-30"] })
  }
  const doResolve = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) => apiClient.post(`${base}/impacts/${id}/resolve`, body),
    onSuccess: () => { refresh(); setResolve(null); setApiError(""); setNotice("Impact resolved") },
    onError: (e: any) => setApiError(errMsg(e, "Failed to resolve impact")),
  })

  const actions = resolve ? ACTIONS[resolve.impactType] ?? [] : []
  const chosen = actions.find(a => a.value === form.action)
  const noteOk = !chosen?.needsNote || form.justification.trim().length >= 15
  const openResolve = (i: Impact) => {
    setApiError(""); setForm({ action: (ACTIONS[i.impactType] ?? [])[0]?.value ?? "", justification: "", updateObligation: false }); setResolve(i)
  }

  const ImpactRow = ({ i }: { i: Impact }) => {
    const t = TYPE_CFG[i.impactType] ?? TYPE_CFG.CHANGED
    return (
      <div style={{ display: "flex", gap: 12, alignItems: "center", padding: "12px 16px", border: `1px solid ${i.overdue ? "#FECACA" : "#E2E8F0"}`, borderRadius: 10, background: "#fff", marginBottom: 8 }}>
        <Badge text={t.label} color={t.color} bg={t.bg} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontWeight: 700, fontSize: 13, color: "#0F172A" }}>{i.reference} — {i.title}</div>
          <div style={{ fontSize: 12, color: "#64748B" }}>
            {i.instrumentTitle} · {i.fromLabel ?? "—"} → {i.toLabel ?? "repealed"}{i.diffSummary ? ` · ${i.diffSummary}` : ""}{i.obligationId ? " · has a live obligation" : ""}
          </div>
          {i.status === "RESOLVED" && <div style={{ fontSize: 12, color: "#475569", marginTop: 2 }}>
            {OUTCOME[i.outcome ?? ""] ?? i.outcome} by {i.resolvedByName ?? "—"} on {fmtDate(i.resolvedAt)}{i.resolutionNote ? ` — ${i.resolutionNote}` : ""}</div>}
        </div>
        {i.status === "PENDING" && <>
          <div style={{ fontSize: 12, fontWeight: 700, color: i.overdue ? "#B91C1C" : "#475569", whiteSpace: "nowrap" }}>{i.overdue ? "Overdue · " : "Due "}{fmtDate(i.dueDate)}</div>
          {canReview && <button style={{ ...primaryBtn, padding: "6px 14px", fontSize: 13 }} onClick={() => openResolve(i)}>Resolve</button>}
        </>}
      </div>
    )
  }

  const card = (label: string, value: number | undefined, color: string) => (
    <div style={{ flex: 1, minWidth: 140, background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, padding: "14px 18px" }}>
      <div style={{ fontSize: 12, color: "#64748B", fontWeight: 600 }}>{label}</div>
      <div style={{ fontSize: 26, fontWeight: 800, color }}>{value ?? "—"}</div>
    </div>
  )

  return (
    <div>
      <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 16 }}>
        {card("Awaiting decision", summary?.pending, "#4338CA")}
        {card("Overdue", summary?.overdue, summary?.overdue ? "#B91C1C" : "#0F172A")}
        {card("Due in 7 days", summary?.dueWithin7Days, "#92400E")}
        {card("Open changes", summary?.openChanges, "#0F172A")}
      </div>

      <div style={{ display: "flex", gap: 8, marginBottom: 14, alignItems: "center", flexWrap: "wrap" }}>
        {([["PENDING", "To resolve"], ["CHANGES", "All changes"], ["RESOLVED", "Resolved"]] as const).map(([v, l]) => (
          <button key={v} onClick={() => setView(v)} style={{ ...ghostBtn, background: view === v ? "#EEF2FF" : "#fff", color: view === v ? "#4338CA" : "#475569", borderColor: view === v ? "#C7D2FE" : "#E2E8F0" }}>{l}</button>
        ))}
        <span style={{ fontSize: 12, color: "#64748B", display: "flex", alignItems: "center", gap: 6 }}>
          <GitCompare size={14} /> Raised automatically when a version is published or repealed. Unchanged requirements keep their decisions.
        </span>
      </div>

      {notice && <div style={{ background: "#ECFDF5", border: "1px solid #A7F3D0", color: "#065F46", borderRadius: 8, padding: "8px 12px", fontSize: 13, marginBottom: 12 }}>{notice}</div>}

      {view === "PENDING" && (loadingPending ? <p style={{ color: "#64748B" }}>Loading…</p>
        : pending.length === 0 ? <p style={{ color: "#64748B", fontSize: 14 }}>Nothing is waiting for a decision.</p>
        : pending.map(i => <ImpactRow key={i.id} i={i} />))}

      {view === "RESOLVED" && (resolved.length === 0 ? <p style={{ color: "#64748B", fontSize: 14 }}>No resolved impacts yet.</p> : resolved.map(i => <ImpactRow key={i.id} i={i} />))}

      {view === "CHANGES" && (loadingChanges ? <p style={{ color: "#64748B" }}>Loading…</p>
        : changes.length === 0 ? <p style={{ color: "#64748B", fontSize: 14 }}>No regulatory changes have been recorded.</p>
        : changes.map(c => (
          <div key={c.id} style={{ border: "1px solid #E2E8F0", borderRadius: 10, background: "#fff", marginBottom: 8 }}>
            <button onClick={() => setExpanded(expanded === c.id ? null : c.id)} style={{ all: "unset", cursor: "pointer", display: "flex", gap: 12, alignItems: "center", padding: "12px 16px", width: "calc(100% - 32px)" }}>
              <div style={{ flex: 1 }}>
                <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{c.instrumentTitle} · {c.fromLabel ?? "—"} → {c.toLabel ?? "repealed"}</div>
                <div style={{ fontSize: 12, color: "#64748B" }}>
                  {c.changeKind === "REPEALED" ? "Repealed" : `${c.addedCount} added · ${c.changedCount} amended · ${c.removedCount} removed · ${c.carriedCount} carried forward`} · {fmtDateTime(c.createdAt)} by {c.createdByName ?? "—"}
                </div>
              </div>
              <Badge text={c.status === "OPEN" ? "Open" : "Closed"} color={c.status === "OPEN" ? "#92400E" : "#166534"} bg={c.status === "OPEN" ? "#FEF3C7" : "#DCFCE7"} />
              {expanded === c.id ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
            </button>
            {expanded === c.id && (
              <div style={{ padding: "0 16px 12px" }}>
                {c.summary && <p style={{ fontSize: 13, color: "#475569", marginTop: 0 }}>{c.summary}</p>}
                {!detail ? <p style={{ color: "#64748B", fontSize: 13 }}>Loading…</p>
                  : detail.impacts.length === 0 ? <p style={{ color: "#64748B", fontSize: 13 }}>No impacts — nothing needed a decision.</p>
                  : detail.impacts.map(i => <ImpactRow key={i.id} i={i} />)}
              </div>
            )}
          </div>
        )))}

      {resolve && (
        <Modal title={`Resolve ${resolve.reference}`} onClose={() => setResolve(null)}>
          <p style={{ fontSize: 13, color: "#475569", marginTop: 0 }}>{resolve.title} — {resolve.instrumentTitle}{resolve.diffSummary ? ` (${resolve.diffSummary})` : ""}</p>
          <Err m={apiError} />
          <Field l="What are you doing about it? *">
            <select style={input} value={form.action} onChange={e => setForm({ ...form, action: e.target.value })}>
              {actions.map(a => <option key={a.value} value={a.value}>{a.label}</option>)}
            </select>
          </Field>
          {chosen && <p style={{ fontSize: 12, color: "#64748B", marginTop: -4 }}>{chosen.help}</p>}
          {chosen?.needsNote && (
            <Field l="Justification * (at least 15 characters)">
              <textarea style={{ ...input, minHeight: 80 }} value={form.justification} onChange={e => setForm({ ...form, justification: e.target.value })} />
            </Field>
          )}
          {form.action === "CONFIRM_APPLICABLE" && resolve.obligationId && (
            <label style={{ display: "flex", gap: 8, fontSize: 13, color: "#475569", marginBottom: 12 }}>
              <input type="checkbox" checked={form.updateObligation} onChange={e => setForm({ ...form, updateObligation: e.target.checked })} />
              Refresh the obligation's wording and evidence expectation from the amended text (owner and dates are untouched)
            </label>
          )}
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setResolve(null)}>Cancel</button>
            <button style={primaryBtn} disabled={!form.action || !noteOk || doResolve.isPending}
              onClick={() => doResolve.mutate({ id: resolve.id, body: { action: form.action, justification: form.justification.trim() || null, updateObligation: form.updateObligation } })}>Resolve</button>
          </div>
        </Modal>
      )}
    </div>
  )
}
