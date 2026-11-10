// src/pages/warehousing/WhseTasksTab.tsx
//
// The floor worklist. Pick tasks are stops on a pick walk (in walking order, highest priority first); put-away tasks move received stock from a
// dock or staging location to storage, with suggested destinations. A person claims a task and completes it. Picking less than asked needs a
// reason and leaves the task short, which holds the order back until it is dealt with.
import { useEffect, useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { RefreshCw } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { btnDanger, btnGhost, btnPrimary, card, Chip, ErrorLine, fmtQty, inputStyle, labelStyle, ReasonModal } from "./whseUi"
import { parseQty } from "./receiving"

export interface TaskRow {
  id: string; taskType: "PICK" | "PUTAWAY"; status: string; priority: number; clientName: string; sku: string; itemDescription: string | null
  lotNumber: string | null; lotExpiry: string | null; fromLocationId: string; fromCode: string; toLocationId: string | null; toCode: string | null
  qty: number; qtyDone: number; referenceNumber: string | null; assignedTo: string | null; note: string | null
  resolution?: string | null; resolutionNote?: string | null
}
interface Suggestion { locationId: string; code: string; reason: string; roomLeft: number | null }
interface Summary { openPicks: number; inProgressPicks: number; shortPicks: number; openPutaways: number; inProgressPutaways: number }

const STATUS_CHIP: Record<string, { bg: string; fg: string; label: string }> = {
  OPEN: { bg: "var(--hf-info-soft)", fg: "var(--hf-info-text)", label: "Open" },
  IN_PROGRESS: { bg: "var(--hf-warning-soft-strong)", fg: "var(--hf-warning-text-deep)", label: "In progress" },
  SHORT: { bg: "var(--hf-danger-soft)", fg: "var(--hf-danger-text)", label: "Short" },
  DONE: { bg: "var(--hf-success-soft)", fg: "var(--hf-success-text-strong)", label: "Done" },
  SHORT_CLOSED: { bg: "var(--hf-surface-muted)", fg: "var(--hf-text-muted)", label: "Short, closed" },
  CANCELLED: { bg: "var(--hf-surface-muted)", fg: "var(--hf-text-muted)", label: "Cancelled" },
}

const RESOLUTION_TEXT: Record<string, string> = {
  SHIP_SHORT: "The line ships what was found; the rest was released.",
  SOURCE_ELSEWHERE: "What was found ships; the rest was allocated from other stock.",
  MOVE_LINE: "The whole line was allocated again at another location.",
}

function TaskCard({ task, canWork }: { task: TaskRow; canWork: boolean }) {
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)
  const [qty, setQty] = useState(String(task.qty - task.qtyDone))
  const [reason, setReason] = useState("")
  const [dest, setDest] = useState(task.toLocationId ?? "")
  const [cancelling, setCancelling] = useState(false)
  const [resolving, setResolving] = useState<"SHIP_SHORT" | "FIND_ELSEWHERE" | null>(null)
  const planned = task.qty - task.qtyDone
  const n = parseQty(qty)
  const isShort = task.taskType === "PICK" && n != null && n >= 0 && n < planned
  const problem = n == null || n < 0 || (n === 0 && task.taskType === "PUTAWAY") ? "Enter the quantity done." : n > planned ? `At most ${fmtQty(planned)}.`
    : isShort && reason.trim().length < 5 ? "Picking less than asked needs a reason (at least 5 characters)."
    : task.taskType === "PUTAWAY" && !dest ? "Choose where it goes." : null

  const { data: suggestions = [] } = useQuery<Suggestion[]>({
    queryKey: ["whse-task-suggestions", task.id], enabled: open && task.taskType === "PUTAWAY",
    queryFn: async () => (await apiClient.get(`/api/v1/warehousing/tasks/${task.id}/suggestions`)).data,
  })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["whse-tasks"] }); qc.invalidateQueries({ queryKey: ["whse-task-summary"] }); qc.invalidateQueries({ queryKey: ["whse-inventory"] }) }
  const act = useMutation({ mutationFn: async (path: string) => apiClient.post(`/api/v1/warehousing/tasks/${task.id}/${path}`), onSuccess: refresh })
  const complete = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/warehousing/tasks/${task.id}/complete`, { qty: n, reason: reason.trim() || null, toLocationId: task.taskType === "PUTAWAY" ? dest : null }),
    onSuccess: () => { setOpen(false); refresh() },
  })
  const cancel = useMutation({
    mutationFn: async (r: string) => apiClient.post(`/api/v1/warehousing/tasks/${task.id}/cancel`, { reason: r }),
    onSuccess: () => { setCancelling(false); refresh() },
  })
  const resolve = useMutation({
    mutationFn: async (v: { action: string; reason: string }) => apiClient.post(`/api/v1/warehousing/tasks/${task.id}/resolve-short`, v),
    onSuccess: () => { setResolving(null); refresh(); qc.invalidateQueries({ queryKey: ["whse-outbound-order-lines"] }); qc.invalidateQueries({ queryKey: ["whse-outbound-lot-allocations"] }); qc.invalidateQueries({ queryKey: ["whse-tasks-order"] }) },
  })
  const chip = STATUS_CHIP[task.status] ?? STATUS_CHIP.OPEN
  const finished = task.status === "DONE" || task.status === "CANCELLED" || task.status === "SHORT_CLOSED"

  return (
    <div style={{ ...card, padding: 14, marginBottom: 10 }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 10, flexWrap: "wrap", alignItems: "center" }}>
        <div>
          <p style={{ fontSize: 13.5, fontWeight: 700, margin: 0, color: "var(--hf-text)" }}>
            {task.taskType === "PICK" ? "Pick" : "Put away"} {fmtQty(planned)} × {task.sku}
            {task.lotNumber && <span style={{ fontWeight: 400, color: "var(--hf-text-muted)" }}> · lot {task.lotNumber}{task.lotExpiry ? ` (exp ${task.lotExpiry})` : ""}</span>}
          </p>
          <p style={{ fontSize: 12, color: "var(--hf-text-faint)", margin: "2px 0 0" }}>
            From <strong>{task.fromCode}</strong>{task.taskType === "PUTAWAY" ? ` → ${task.toCode ?? "no suggestion"}` : ""} · {task.clientName}{task.referenceNumber ? ` · ${task.referenceNumber}` : ""}
            {task.priority <= 3 ? " · urgent" : ""}
          </p>
          {task.status === "SHORT" && <p style={{ fontSize: 12, color: "var(--hf-danger-text)", margin: "4px 0 0" }}>Picked {fmtQty(task.qtyDone)} of {fmtQty(task.qty)}: {task.note}. The order cannot be packed until you ship it short or find the rest elsewhere.</p>}
          {task.status === "SHORT_CLOSED" && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "4px 0 0" }}>Picked {fmtQty(task.qtyDone)} of {fmtQty(task.qty)}. {RESOLUTION_TEXT[task.resolution ?? ""] ?? "Closed."} {task.resolutionNote}</p>}
        </div>
        <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
          <Chip bg={chip.bg} fg={chip.fg}>{chip.label}</Chip>
          {canWork && task.status === "SHORT" && task.taskType === "PICK" && (
            <>
              <button onClick={() => setResolving("SHIP_SHORT")} style={btnGhost}>Ship short</button>
              <button onClick={() => setResolving("FIND_ELSEWHERE")} style={btnPrimary}>Find elsewhere</button>
            </>
          )}
          {canWork && !finished && task.status !== "SHORT" && (
            <>
              {task.status === "OPEN" && <button onClick={() => act.mutate("claim")} disabled={act.isPending} style={btnGhost}>Claim</button>}
              {task.status === "IN_PROGRESS" && <button onClick={() => act.mutate("release")} disabled={act.isPending} style={btnGhost}>Hand back</button>}
              <button onClick={() => setOpen(o => !o)} style={btnPrimary}>{open ? "Close" : "Do it"}</button>
            </>
          )}
          {canWork && !finished && <button onClick={() => setCancelling(true)} style={btnDanger}>Cancel</button>}
        </div>
      </div>
      <ErrorLine error={act.error} />
      {open && (
        <div style={{ marginTop: 12, display: "grid", gap: 8 }}>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "flex-end" }}>
            <div style={{ width: 90 }}><label style={labelStyle}>{task.taskType === "PICK" ? "Picked" : "Moved"}</label><input aria-label="Quantity done" inputMode="decimal" style={{ ...inputStyle, width: "100%" }} value={qty} onChange={e => setQty(e.target.value)} /></div>
            {task.taskType === "PUTAWAY" && (
              <div style={{ flex: 1, minWidth: 200 }}><label style={labelStyle}>Put away at</label>
                <select aria-label="Destination" style={{ ...inputStyle, width: "100%" }} value={dest} onChange={e => setDest(e.target.value)}>
                  <option value="">Choose…</option>
                  {task.toLocationId && !suggestions.some(s => s.locationId === task.toLocationId) && <option value={task.toLocationId}>{task.toCode}</option>}
                  {suggestions.map(s => <option key={s.locationId} value={s.locationId}>{s.code} · {s.reason}{s.roomLeft != null ? ` · room for ${fmtQty(s.roomLeft)}` : ""}</option>)}
                </select></div>
            )}
          </div>
          {isShort && <div><label style={labelStyle}>Why less? *</label><input aria-label="Short pick reason" style={{ ...inputStyle, width: "100%" }} value={reason} onChange={e => setReason(e.target.value)} /></div>}
          <div style={{ display: "flex", gap: 10, alignItems: "center" }}>
            <button onClick={() => complete.mutate()} disabled={!!problem || complete.isPending} style={{ ...btnPrimary, opacity: problem ? 0.5 : 1 }}>
              {complete.isPending ? "Saving…" : isShort ? "Record short pick" : "Complete"}
            </button>
            {problem && <span style={{ fontSize: 11.5, color: "var(--hf-text-faint)" }}>{problem}</span>}
          </div>
          <ErrorLine error={complete.error} fallback="Could not complete this task" />
        </div>
      )}
      {resolving && (
        <ReasonModal
          title={resolving === "SHIP_SHORT" ? "Ship this line short" : "Find the stock elsewhere"}
          intro={resolving === "SHIP_SHORT"
            ? "The line will ship only what was found. The rest of its reservation is released and the order shows it as short."
            : "Looks for the missing quantity in other stock (first expiry first for lot-tracked items). An item that is not lot tracked moves wholly to another location that holds all of it. Nothing changes if no other stock is found."}
          confirmLabel={resolving === "SHIP_SHORT" ? "Ship short" : "Find elsewhere"}
          pending={resolve.isPending} error={resolve.error}
          onConfirm={r => resolve.mutate({ action: resolving, reason: r })}
          onClose={() => { setResolving(null); resolve.reset() }} />
      )}
      {cancelling && <ReasonModal title="Cancel this task" confirmLabel="Cancel task" pending={cancel.isPending} error={cancel.error} onConfirm={r => cancel.mutate(r)} onClose={() => { setCancelling(false); cancel.reset() }} />}
    </div>
  )
}

export default function WhseTasksTab() {
  const qc = useQueryClient()
  const canWork = usePermission("WAREHOUSING_MANAGE") || usePermission("WAREHOUSING_ADMIN")
  const [type, setType] = useState<"PICK" | "PUTAWAY">("PICK")
  const [mine, setMine] = useState(false)

  const sync = useMutation({
    mutationFn: async () => (await apiClient.post("/api/v1/warehousing/tasks/putaway/sync")).data as { created: number },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["whse-tasks"] }); qc.invalidateQueries({ queryKey: ["whse-task-summary"] }) },
  })
  // Opening the put-away list brings it up to date with what is sitting at the docks.
  useEffect(() => { if (type === "PUTAWAY" && canWork) sync.mutate() }, [type, canWork]) // eslint-disable-line react-hooks/exhaustive-deps

  const { data: summary } = useQuery<Summary>({ queryKey: ["whse-task-summary"], queryFn: async () => (await apiClient.get("/api/v1/warehousing/tasks/summary")).data })
  const { data: tasks = [], isLoading } = useQuery<TaskRow[]>({
    queryKey: ["whse-tasks", type, mine],
    queryFn: async () => (await apiClient.get(`/api/v1/warehousing/tasks?type=${type}${mine ? "&mine=true" : ""}`)).data,
  })

  const tile = (label: string, n: number | undefined, warn = false) => (
    <div style={{ ...card, padding: "10px 14px", minWidth: 110 }}>
      <p style={{ fontSize: 20, fontWeight: 800, margin: 0, color: warn && (n ?? 0) > 0 ? "var(--hf-danger-text)" : "var(--hf-text)" }}>{n ?? "–"}</p>
      <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: 0 }}>{label}</p>
    </div>
  )

  return (
    <div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 16 }}>
        {tile("Picks open", summary?.openPicks)}{tile("Picks in progress", summary?.inProgressPicks)}{tile("Short picks", summary?.shortPicks, true)}
        {tile("Put-aways open", summary?.openPutaways)}{tile("Put-aways in progress", summary?.inProgressPutaways)}
      </div>
      <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap", marginBottom: 14 }}>
        <div role="tablist" aria-label="Task type" style={{ display: "inline-flex", border: "1px solid var(--hf-border)", borderRadius: 8, overflow: "hidden" }}>
          {(["PICK", "PUTAWAY"] as const).map(v => (
            <button key={v} role="tab" aria-selected={type === v} onClick={() => setType(v)}
              style={{ padding: "7px 16px", border: "none", fontSize: 12.5, fontWeight: 600, cursor: "pointer", background: type === v ? "var(--hf-accent-solid-strong)" : "var(--hf-surface)", color: type === v ? "var(--hf-text-on-solid)" : "var(--hf-text-muted)" }}>
              {v === "PICK" ? "Picking" : "Put-away"}
            </button>
          ))}
        </div>
        <label style={{ fontSize: 12.5, color: "var(--hf-text-muted)", display: "flex", gap: 6, alignItems: "center" }}>
          <input type="checkbox" checked={mine} onChange={e => setMine(e.target.checked)} /> Only mine
        </label>
        {type === "PUTAWAY" && canWork && <button onClick={() => sync.mutate()} disabled={sync.isPending} style={btnGhost}><RefreshCw size={13} /> {sync.isPending ? "Checking docks…" : "Refresh from docks"}</button>}
      </div>
      <ErrorLine error={sync.error} />
      {isLoading ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>Loading…</p> : tasks.length === 0
        ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>{type === "PICK" ? "No pick tasks. They appear when an order starts picking." : "Nothing to put away. Stock received into a dock or staging location appears here."}</p>
        : tasks.map(t => <TaskCard key={t.id} task={t} canWork={canWork} />)}
    </div>
  )
}
