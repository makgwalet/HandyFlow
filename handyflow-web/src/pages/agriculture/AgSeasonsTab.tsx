// src/pages/agriculture/AgSeasonsTab.tsx
// Growing seasons for one farm. The server puts no guard on activate/close (several seasons can be active, and a
// closed one can be re-activated), so the screen warns about a second active season instead of blocking it.
import { useState } from "react"
import { Plus } from "lucide-react"
import { usePermission } from "../../hooks/usePermission"
import { agKeys, api, useAgMutation, useCropCycles, useSeasons } from "./agCrops.api"
import type { Season } from "./agCrops.types"
import { Empty, Field, Warn } from "./agCropsUi"
import { btnDanger, btnGhost, btnPrimary, card, fmtDate, grid, inp, panel, statusBadge } from "./constants"

function SeasonForm({ farmId, initial, onDone }: { farmId: string; initial?: Season; onDone: () => void }) {
  const [f, setF] = useState({ name: initial?.name ?? "", start: initial?.startDate ?? "", end: initial?.endDate ?? "", notes: initial?.notes ?? "" })
  const endBeforeStart = !!f.end && !!f.start && f.end < f.start
  const valid = !!f.name.trim() && !!f.start && !endBeforeStart
  const keys = [agKeys.seasons(farmId)]
  const create = useAgMutation(() => api.post(`/farms/${farmId}/seasons`, { farmId, name: f.name.trim(), startDate: f.start, endDate: f.end || undefined, notes: f.notes.trim() || undefined }),
    { invalidate: keys, success: "Season created", failure: "Couldn't create the season." })
  const update = useAgMutation(() => api.put(`/seasons/${initial?.id}`, { name: f.name.trim(), startDate: f.start, endDate: f.end || undefined, notes: f.notes.trim() || undefined }),
    { invalidate: keys, success: "Season saved", failure: "Couldn't save the season." })
  const run = initial ? update : create
  return (
    <div style={panel}>
      <div style={grid}>
        <Field label="Name *" htmlFor="se-name"><input id="se-name" style={inp} value={f.name} onChange={e => setF({ ...f, name: e.target.value })} placeholder="e.g. 2026/27 summer" /></Field>
        <Field label="Start date *" htmlFor="se-start"><input id="se-start" type="date" style={inp} value={f.start} onChange={e => setF({ ...f, start: e.target.value })} /></Field>
        <Field label="End date" htmlFor="se-end"><input id="se-end" type="date" style={inp} value={f.end} onChange={e => setF({ ...f, end: e.target.value })} /></Field>
        <Field label="Notes" htmlFor="se-notes"><input id="se-notes" style={inp} value={f.notes} onChange={e => setF({ ...f, notes: e.target.value })} /></Field>
      </div>
      {endBeforeStart && <Warn tone="danger">The end date can't be before the start date.</Warn>}
      <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
        <button type="button" style={{ ...btnPrimary, opacity: valid && !run.isPending ? 1 : 0.5 }} disabled={!valid || run.isPending} onClick={() => run.mutate(undefined, { onSuccess: onDone })}>{initial ? "Save" : "Create season"}</button>
        <button type="button" style={btnGhost} onClick={onDone}>Cancel</button>
      </div>
    </div>
  )
}

export default function AgSeasonsTab({ farmId }: { farmId: string }) {
  const canManage = usePermission("AGRICULTURE_MANAGE")
  const canDelete = usePermission("AGRICULTURE_ADMIN")
  const [adding, setAdding] = useState(false)
  const [editing, setEditing] = useState<Season | null>(null)
  const [confirm, setConfirm] = useState<{ kind: "activate" | "delete"; season: Season } | null>(null)
  const { data: seasons = [], isLoading } = useSeasons(farmId)
  const cycles = useCropCycles(farmId).data ?? []
  const keys = [agKeys.seasons(farmId)]
  const activate = useAgMutation((id: string) => api.patch(`/seasons/${id}/activate`), { invalidate: keys, success: "Season activated", failure: "Couldn't activate the season." })
  const close = useAgMutation((id: string) => api.patch(`/seasons/${id}/close`), { invalidate: keys, success: "Season closed", failure: "Couldn't close the season." })
  const remove = useAgMutation((id: string) => api.del(`/seasons/${id}`), { invalidate: [...keys, agKeys.cyclesOfFarm(farmId)], success: "Season deleted", failure: "Couldn't delete the season." })
  const sorted = [...seasons].sort((a, b) => b.startDate.localeCompare(a.startDate))
  const activeOthers = (s: Season) => seasons.filter(x => x.status === "ACTIVE" && x.id !== s.id)
  const cycleCount = (s: Season) => cycles.filter(c => c.seasonId === s.id).length
  const small: React.CSSProperties = { ...btnGhost, padding: "4px 10px", fontSize: 11.5 }

  return (
    <div>
      {canManage && !adding && !editing && <div style={{ marginBottom: 12 }}><button type="button" style={btnPrimary} onClick={() => setAdding(true)}><Plus size={14} />New season</button></div>}
      {adding && <SeasonForm farmId={farmId} onDone={() => setAdding(false)} />}
      {editing && <SeasonForm farmId={farmId} initial={editing} onDone={() => setEditing(null)} />}
      {confirm && (
        <div style={panel}>
          {confirm.kind === "activate" ? (
            <p style={{ fontSize: 13, margin: "0 0 10px" }}>{activeOthers(confirm.season).map(s => s.name).join(", ")} {activeOthers(confirm.season).length === 1 ? "is" : "are"} already active. Activate "{confirm.season.name}" as well?</p>
          ) : cycleCount(confirm.season) > 0 ? (
            <p role="alert" style={{ fontSize: 13, margin: "0 0 10px" }}>"{confirm.season.name}" can't be deleted while {cycleCount(confirm.season)} crop cycle(s) are linked to it. Move or delete them first.</p>
          ) : (
            <p style={{ fontSize: 13, margin: "0 0 10px" }}>Delete "{confirm.season.name}"?</p>
          )}
          <div style={{ display: "flex", gap: 8 }}>
            {!(confirm.kind === "delete" && cycleCount(confirm.season) > 0) && (
              <button type="button" style={confirm.kind === "delete" ? btnDanger : btnPrimary}
                onClick={() => { (confirm.kind === "activate" ? activate : remove).mutate(confirm.season.id); setConfirm(null) }}>{confirm.kind === "activate" ? "Activate" : "Delete season"}</button>
            )}
            <button type="button" style={btnGhost} onClick={() => setConfirm(null)}>{confirm.kind === "delete" && cycleCount(confirm.season) > 0 ? "OK" : "Cancel"}</button>
          </div>
        </div>
      )}
      {isLoading ? <Empty>Loading seasons…</Empty> : sorted.length === 0 ? <Empty>No seasons yet. A season groups crop cycles, for example "2026/27 summer".</Empty> : (
        <div style={{ display: "grid", gap: 10 }}>
          {sorted.map(s => (
            <div key={s.id} style={{ ...card, display: "flex", alignItems: "center", gap: 14, flexWrap: "wrap" }}>
              <div style={{ flex: 1, minWidth: 200 }}>
                <div style={{ display: "flex", alignItems: "center", gap: 8 }}><strong style={{ fontSize: 14, color: "var(--hf-text)" }}>{s.name}</strong><span style={statusBadge(s.status)}>{s.status === "PLANNING" ? "Planning" : s.status === "ACTIVE" ? "Active" : "Closed"}</span></div>
                <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 3 }}>{fmtDate(s.startDate)} → {s.endDate ? fmtDate(s.endDate) : "open-ended"} · {cycleCount(s)} crop cycle{cycleCount(s) === 1 ? "" : "s"}</div>
                {s.notes && <div style={{ fontSize: 12, color: "var(--hf-text-faint)", marginTop: 2 }}>{s.notes}</div>}
              </div>
              {canManage && (
                <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                  {s.status !== "ACTIVE" && <button type="button" style={small} onClick={() => (activeOthers(s).length ? setConfirm({ kind: "activate", season: s }) : activate.mutate(s.id))}>{s.status === "CLOSED" ? "Reopen" : "Activate"}</button>}
                  {s.status === "ACTIVE" && <button type="button" style={small} onClick={() => close.mutate(s.id)}>Close season</button>}
                  <button type="button" style={small} onClick={() => { setAdding(false); setEditing(s) }}>Edit</button>
                  {canDelete && <button type="button" style={{ ...small, ...btnDanger, padding: "4px 10px", fontSize: 11.5 }} onClick={() => setConfirm({ kind: "delete", season: s })}>Delete</button>}
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
