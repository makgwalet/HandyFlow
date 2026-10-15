// src/pages/earthmoving/WorkOrdersTab.tsx
//
// Repair and service jobs. Completing a job records the work and the test result; it does NOT put the machine back in
// service. Release is a separate step in Fleet (it needs EARTHMOVING_RELEASE), and open jobs that block release stop it.
import { useState } from "react"
import { Plus, ClipboardList, Lock } from "lucide-react"
import { MAINTENANCE_TYPES } from "./shared/constants"
import { fmtDate, fmtCurrency2dp, apiMessage, meterUnitOf, meterWordOf } from "./shared/format"
import { Overlay, ModalHead, ModalFoot, ErrBanner, Sect, lbl, inputStyle } from "./shared/Modal"
import { useEarthPerms } from "./shared/useEarthPerms"
import {
  useAssets, useWorkOrders, useAssignableUsers, useCreateWorkOrder, useWorkOrderAction, useWorkOrderLine,
} from "./shared/hooks"
import type { WorkOrder } from "./shared/types"

const PRIORITIES = ["LOW", "NORMAL", "HIGH", "URGENT"]
const NEEDS_TEST = ["REPAIR", "ELECTRICAL", "HYDRAULICS", "ENGINE", "TRACKS"]
const FILTERS: { id: string; label: string }[] = [
  { id: "OPEN_ALL", label: "Open" }, { id: "COMPLETED", label: "Completed" }, { id: "CANCELLED", label: "Cancelled" }, { id: "ALL", label: "All" },
]
const STATUS_LABEL: Record<string, string> = {
  OPEN: "Open", IN_PROGRESS: "In progress", AWAITING_PARTS: "Awaiting parts", COMPLETED: "Completed", CANCELLED: "Cancelled",
}
const PRIORITY_COLOR: Record<string, string> = {
  URGENT: "var(--hf-danger-text)", HIGH: "var(--hf-warning-text)", NORMAL: "var(--hf-text-secondary)", LOW: "var(--hf-text-faint)",
}
const isOpen = (w: WorkOrder) => w.status !== "COMPLETED" && w.status !== "CANCELLED"

type Dialog = { kind: "complete" | "cancel" | "blocks"; w: WorkOrder } | null

const btn = (primary = false): React.CSSProperties => ({
  padding: "7px 14px", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer",
  border: primary ? "none" : "1px solid var(--hf-border)",
  background: primary ? "var(--hf-primary)" : "var(--hf-surface)",
  color: primary ? "var(--hf-text-on-solid)" : "var(--hf-text-secondary)",
})

const EMPTY_NEW = { assetId: "", title: "", description: "", type: "REPAIR", priority: "NORMAL", dueDate: "", assignedToUserId: "", blocksRelease: true }
const EMPTY_LINE = { kind: "PART", description: "", quantity: "1", unitCost: "", supplier: "" }
const EMPTY_DONE = { workDone: "", testResult: "", meterHours: "", downtimeHours: "", labourHours: "" }

export default function WorkOrdersTab() {
  const perms = useEarthPerms()
  const [filter, setFilter] = useState("OPEN_ALL")
  const [openId, setOpenId] = useState<string | null>(null)
  const [showNew, setShowNew] = useState(false)
  const [dialog, setDialog] = useState<Dialog>(null)
  const [err, setErr] = useState("")
  const [newForm, setNewForm] = useState(EMPTY_NEW)
  const [line, setLine] = useState(EMPTY_LINE)
  const [done, setDone] = useState(EMPTY_DONE)
  const [reason, setReason] = useState("")

  const { data: assets = [] } = useAssets()
  const { data: orders = [], isLoading } = useWorkOrders()
  const { data: users = [] } = useAssignableUsers()

  const create = useCreateWorkOrder(() => { setShowNew(false); setNewForm(EMPTY_NEW); setErr("") })
  const act = useWorkOrderAction()
  const lineMut = useWorkOrderLine(() => { setLine(EMPTY_LINE); setErr("") })

  const assetName = (id: string) => {
    const a = assets.find(x => x.id === id)
    return a ? `${a.fleetNumber ? a.fleetNumber + " — " : ""}${a.name}` : "Unknown machine"
  }
  const shown = orders.filter(w =>
    filter === "ALL" ? true : filter === "OPEN_ALL" ? isOpen(w) : w.status === filter)
  const current = orders.find(w => w.id === openId) ?? null

  const run = (id: string, action: string, body?: Record<string, unknown>, after?: () => void) => {
    setErr("")
    act.mutate({ id, action, body }, { onSuccess: () => after?.(), onError: e => setErr(apiMessage(e, "That did not work.")) })
  }
  const closeDialog = () => { setDialog(null); setErr(""); setReason(""); setDone(EMPTY_DONE) }

  // ── rows ────────────────────────────────────────────────────────────────
  const renderRow = (w: WorkOrder) => (
    <button key={w.id} onClick={() => { setOpenId(w.id); setErr("") }}
      style={{ textAlign: "left", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: "12px 16px", cursor: "pointer", display: "flex", justifyContent: "space-between", gap: 12 }}>
      <div>
        <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 3, flexWrap: "wrap" }}>
          <strong style={{ fontSize: 13 }}>{w.number}</strong>
          <span style={{ fontSize: 14, fontWeight: 600, color: "var(--hf-text)" }}>{w.title}</span>
          <span style={{ fontSize: 11, fontWeight: 700, color: PRIORITY_COLOR[w.priority] }}>{w.priority}</span>
          {isOpen(w) && w.blocksRelease && (
            <span title="This job stops the machine being released" style={{ display: "inline-flex", alignItems: "center", gap: 3, fontSize: 11, color: "var(--hf-warning-text)" }}>
              <Lock size={11} /> Blocks release
            </span>
          )}
        </div>
        <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>
          {assetName(w.assetId)} · {w.type}
          {w.assignedToName && ` · ${w.assignedToName}`}
          {w.dueDate && ` · due ${fmtDate(w.dueDate)}`}
          {w.overdue && <span style={{ color: "var(--hf-danger-text)", fontWeight: 700 }}> · overdue</span>}
        </div>
      </div>
      <div style={{ textAlign: "right", flexShrink: 0 }}>
        <div style={{ fontSize: 12, fontWeight: 700 }}>{STATUS_LABEL[w.status] ?? w.status}</div>
        {w.totalCost > 0 && <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>{fmtCurrency2dp(w.totalCost)}</div>}
      </div>
    </button>
  )

  // ── detail ──────────────────────────────────────────────────────────────
  const renderDetail = (w: WorkOrder) => {
    const open = isOpen(w)
    const canWork = perms.canMaintain && open
    return (
      <Overlay onClose={() => setOpenId(null)} width={680}>
        <ModalHead title={`${w.number} · ${w.title}`} subtitle={`${assetName(w.assetId)} · ${STATUS_LABEL[w.status] ?? w.status}`} onClose={() => setOpenId(null)} />
        {w.description && <p style={{ marginTop: 0, fontSize: 14, color: "var(--hf-text-secondary)" }}>{w.description}</p>}
        <div style={{ fontSize: 13, color: "var(--hf-text-secondary)", marginBottom: 14 }}>
          {w.type} · {w.priority} priority · raised {fmtDate(w.createdAt)}
          {w.source !== "MANUAL" && ` · from ${w.source === "PLAN" ? "a service plan" : "an incident"}`}
        </div>

        {open && (
          <div role="note" style={{ marginBottom: 14, padding: "10px 14px", borderRadius: 8, fontSize: 13, background: w.blocksRelease ? "var(--hf-warning-soft-strong)" : "var(--hf-surface-muted)", color: "var(--hf-text-secondary)" }}>
            {w.blocksRelease
              ? "While this job is open the machine cannot be released from maintenance or breakdown."
              : "This job does not hold up release (switched off with a reason)."}
          </div>
        )}

        {canWork && (
          <div style={{ marginBottom: 14 }}>
            <label style={lbl} htmlFor="wo-assign">Assigned to</label>
            <select id="wo-assign" value={w.assignedToUserId ?? ""} onChange={e => e.target.value && run(w.id, "assign", { userId: e.target.value })} style={inputStyle()}>
              <option value="">{w.assignedToName ?? "Nobody yet"}</option>
              {users.map(u => <option key={u.id} value={u.id}>{u.name}</option>)}
            </select>
          </div>
        )}
        {!canWork && w.assignedToName && <div style={{ fontSize: 13, marginBottom: 14 }}>Assigned to {w.assignedToName}</div>}

        <Sect title="Parts, labour and other costs">
          {w.lines.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Nothing added yet.</div>}
          {w.lines.map(l => (
            <div key={l.id} style={{ display: "flex", justifyContent: "space-between", fontSize: 13, padding: "5px 0", borderBottom: "1px solid var(--hf-border-subtle)" }}>
              <span>{l.kind} · {l.description} · {l.quantity} × {fmtCurrency2dp(l.unitCost)}{l.supplier ? ` · ${l.supplier}` : ""}</span>
              <span>
                {fmtCurrency2dp(l.total)}
                {canWork && (
                  <button aria-label={`Remove ${l.description}`} onClick={() => lineMut.mutate({ id: w.id, remove: l.id }, { onError: e => setErr(apiMessage(e, "Could not remove the line.")) })}
                    style={{ marginLeft: 8, background: "none", border: "none", color: "var(--hf-danger-text)", cursor: "pointer" }}>Remove</button>
                )}
              </span>
            </div>
          ))}
          <div style={{ textAlign: "right", fontWeight: 700, marginTop: 6 }}>Total {fmtCurrency2dp(w.totalCost)}</div>
          {canWork && (
            <div style={{ display: "grid", gridTemplateColumns: "90px 1fr 70px 100px", gap: 8, marginTop: 10 }}>
              <select aria-label="Line kind" value={line.kind} onChange={e => setLine(f => ({ ...f, kind: e.target.value }))} style={inputStyle()}>
                <option>PART</option><option>LABOUR</option><option>OTHER</option>
              </select>
              <input aria-label="Line description" placeholder="Description" value={line.description} onChange={e => setLine(f => ({ ...f, description: e.target.value }))} style={inputStyle()} />
              <input aria-label="Line quantity" type="number" min="0" step="any" value={line.quantity} onChange={e => setLine(f => ({ ...f, quantity: e.target.value }))} style={inputStyle()} />
              <input aria-label="Line unit cost" type="number" min="0" step="any" placeholder="Unit cost" value={line.unitCost} onChange={e => setLine(f => ({ ...f, unitCost: e.target.value }))} style={inputStyle()} />
              <button style={{ ...btn(), gridColumn: "1 / -1", justifySelf: "start" }} disabled={lineMut.isPending}
                onClick={() => lineMut.mutate({ id: w.id, body: { kind: line.kind, description: line.description, quantity: Number(line.quantity), unitCost: Number(line.unitCost), supplier: null } },
                  { onError: e => setErr(apiMessage(e, "Could not add the line.")) })}>
                Add line
              </button>
            </div>
          )}
        </Sect>

        {w.status === "COMPLETED" && (
          <Sect title="Result">
            <div style={{ fontSize: 13 }}>
              <div><strong>Work done:</strong> {w.workDone}</div>
              {w.testResult && <div><strong>Test result:</strong> {w.testResult}</div>}
              {w.meterHours != null && <div><strong>Meter:</strong> {w.meterHours} {meterUnitOf(assets.find(x => x.id === w.assetId))}</div>}
              {w.downtimeHours != null && <div><strong>Downtime:</strong> {w.downtimeHours} hrs</div>}
              <div style={{ marginTop: 6, color: "var(--hf-text-faint)" }}>Completed {fmtDate(w.completedAt)}. The machine still has to be released from Fleet by someone with release authority.</div>
            </div>
          </Sect>
        )}
        {w.status === "CANCELLED" && <Sect title="Cancelled"><div style={{ fontSize: 13 }}>{w.cancelReason}</div></Sect>}

        {err && <ErrBanner msg={err} />}

        {canWork && (
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginTop: 16 }}>
            {w.status === "OPEN" && <button style={btn(true)} onClick={() => run(w.id, "start")}>Start work</button>}
            {w.status === "IN_PROGRESS" && <button style={btn()} onClick={() => run(w.id, "awaiting-parts")}>Waiting for parts</button>}
            {w.status === "AWAITING_PARTS" && <button style={btn(true)} onClick={() => run(w.id, "resume")}>Parts arrived, resume</button>}
            {w.status === "IN_PROGRESS" && <button style={btn(true)} onClick={() => { setErr(""); setDialog({ kind: "complete", w }) }}>Complete…</button>}
            <button style={btn()} onClick={() => { setErr(""); setDialog({ kind: "blocks", w }) }}>{w.blocksRelease ? "Stop blocking release…" : "Block release again"}</button>
            <button style={btn()} onClick={() => { setErr(""); setDialog({ kind: "cancel", w }) }}>Cancel job…</button>
          </div>
        )}
      </Overlay>
    )
  }

  // ── small dialogs ───────────────────────────────────────────────────────
  const renderDialog = (d: NonNullable<Dialog>) => {
    const w = d.w
    if (d.kind === "complete") {
      const needsTest = NEEDS_TEST.includes(w.type)
      const meterNeeded = w.type === "SERVICE"
      const ok = done.workDone.trim().length >= 5 && (!needsTest || done.testResult.trim().length >= 5) && (!meterNeeded || done.meterHours !== "")
      return (
        <Overlay onClose={closeDialog} width={520}>
          <ModalHead title={`Complete ${w.number}`} subtitle="This records the work. It does not release the machine." onClose={closeDialog} />
          <label style={lbl} htmlFor="wo-done">Work done *</label>
          <textarea id="wo-done" rows={3} value={done.workDone} onChange={e => setDone(f => ({ ...f, workDone: e.target.value }))} style={inputStyle()} />
          <label style={{ ...lbl, marginTop: 12 }} htmlFor="wo-test">Test result{needsTest ? " *" : ""}</label>
          <textarea id="wo-test" rows={2} value={done.testResult} placeholder="What was checked after the work, and what it showed" onChange={e => setDone(f => ({ ...f, testResult: e.target.value }))} style={inputStyle()} />
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 10, marginTop: 12 }}>
            <div><label style={lbl} htmlFor="wo-meter">Meter {meterWordOf(assets.find(x => x.id === w.assetId))}{meterNeeded ? " *" : ""}</label>
              <input id="wo-meter" type="number" min="0" step="0.1" value={done.meterHours} onChange={e => setDone(f => ({ ...f, meterHours: e.target.value }))} style={inputStyle()} /></div>
            <div><label style={lbl} htmlFor="wo-down">Downtime hrs</label>
              <input id="wo-down" type="number" min="0" step="0.1" value={done.downtimeHours} onChange={e => setDone(f => ({ ...f, downtimeHours: e.target.value }))} style={inputStyle()} /></div>
            <div><label style={lbl} htmlFor="wo-labour">Labour hrs</label>
              <input id="wo-labour" type="number" min="0" step="0.1" value={done.labourHours} onChange={e => setDone(f => ({ ...f, labourHours: e.target.value }))} style={inputStyle()} /></div>
          </div>
          {err && <ErrBanner msg={err} />}
          <ModalFoot onCancel={closeDialog} loading={act.isPending} label="Complete job" disabled={!ok}
            onSubmit={() => run(w.id, "complete", {
              workDone: done.workDone, testResult: done.testResult || null,
              meterHours: done.meterHours === "" ? null : Number(done.meterHours),
              downtimeHours: done.downtimeHours === "" ? null : Number(done.downtimeHours),
              labourHours: done.labourHours === "" ? null : Number(done.labourHours),
            }, closeDialog)} />
        </Overlay>
      )
    }
    const isCancel = d.kind === "cancel"
    const turningOff = d.kind === "blocks" && w.blocksRelease
    const needReason = isCancel || turningOff
    return (
      <Overlay onClose={closeDialog} width={480}>
        <ModalHead title={isCancel ? `Cancel ${w.number}` : turningOff ? "Stop this job blocking release" : "Block release again"} onClose={closeDialog} />
        {needReason && (
          <>
            <label style={lbl} htmlFor="wo-reason">Reason *</label>
            <textarea id="wo-reason" rows={3} value={reason} onChange={e => setReason(e.target.value)} style={inputStyle()} />
            {turningOff && <p style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>This is recorded in the audit trail. Use it only when the job really does not affect whether the machine is safe to release.</p>}
          </>
        )}
        {err && <ErrBanner msg={err} />}
        <ModalFoot onCancel={closeDialog} loading={act.isPending} label={isCancel ? "Cancel job" : "Confirm"} disabled={needReason && reason.trim().length < 5}
          onSubmit={() => isCancel ? run(w.id, "cancel", { reason }, closeDialog)
            : run(w.id, "blocks-release", { blocksRelease: !w.blocksRelease, reason: reason || null }, closeDialog)} />
      </Overlay>
    )
  }

  // ── new job ─────────────────────────────────────────────────────────────
  const renderNew = () => {
    const ok = newForm.assetId !== "" && newForm.title.trim() !== ""
    return (
      <Overlay onClose={() => setShowNew(false)} width={560}>
        <ModalHead title="Raise a work order" onClose={() => setShowNew(false)} />
        <label style={lbl} htmlFor="nwo-asset">Machine *</label>
        <select id="nwo-asset" value={newForm.assetId} onChange={e => setNewForm(f => ({ ...f, assetId: e.target.value }))} style={inputStyle()}>
          <option value="">Choose a machine…</option>
          {assets.filter(a => a.status !== "RETIRED").map(a => <option key={a.id} value={a.id}>{assetName(a.id)}</option>)}
        </select>
        <label style={{ ...lbl, marginTop: 12 }} htmlFor="nwo-title">What needs doing *</label>
        <input id="nwo-title" value={newForm.title} onChange={e => setNewForm(f => ({ ...f, title: e.target.value }))} style={inputStyle()} />
        <label style={{ ...lbl, marginTop: 12 }} htmlFor="nwo-desc">Details</label>
        <textarea id="nwo-desc" rows={2} value={newForm.description} onChange={e => setNewForm(f => ({ ...f, description: e.target.value }))} style={inputStyle()} />
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 10, marginTop: 12 }}>
          <div><label style={lbl} htmlFor="nwo-type">Type</label>
            <select id="nwo-type" value={newForm.type} onChange={e => setNewForm(f => ({ ...f, type: e.target.value }))} style={inputStyle()}>
              {MAINTENANCE_TYPES.map(t => <option key={t}>{t}</option>)}</select></div>
          <div><label style={lbl} htmlFor="nwo-pri">Priority</label>
            <select id="nwo-pri" value={newForm.priority} onChange={e => setNewForm(f => ({ ...f, priority: e.target.value }))} style={inputStyle()}>
              {PRIORITIES.map(t => <option key={t}>{t}</option>)}</select></div>
          <div><label style={lbl} htmlFor="nwo-due">Due</label>
            <input id="nwo-due" type="date" value={newForm.dueDate} onChange={e => setNewForm(f => ({ ...f, dueDate: e.target.value }))} style={inputStyle()} /></div>
        </div>
        {perms.canMaintain && (
          <div style={{ marginTop: 12 }}>
            <label style={lbl} htmlFor="nwo-user">Assign to</label>
            <select id="nwo-user" value={newForm.assignedToUserId} onChange={e => setNewForm(f => ({ ...f, assignedToUserId: e.target.value }))} style={inputStyle()}>
              <option value="">Nobody yet</option>
              {users.map(u => <option key={u.id} value={u.id}>{u.name}</option>)}
            </select>
          </div>
        )}
        <label style={{ display: "flex", gap: 8, marginTop: 14, fontSize: 13, alignItems: "center" }}>
          <input type="checkbox" checked={newForm.blocksRelease} onChange={e => setNewForm(f => ({ ...f, blocksRelease: e.target.checked }))} />
          Machine cannot be released until this is done
        </label>
        {err && <ErrBanner msg={err} />}
        <ModalFoot onCancel={() => setShowNew(false)} loading={create.isPending} label="Raise work order" disabled={!ok}
          onSubmit={() => { setErr(""); create.mutate({
            assetId: newForm.assetId, title: newForm.title, description: newForm.description || null, type: newForm.type,
            priority: newForm.priority, dueDate: newForm.dueDate || null,
            assignedToUserId: perms.canMaintain && newForm.assignedToUserId ? newForm.assignedToUserId : null,
            blocksRelease: newForm.blocksRelease,
          }, { onError: e => setErr(apiMessage(e, "Could not raise the work order.")) }) }} />
      </Overlay>
    )
  }

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18, flexWrap: "wrap", gap: 10 }}>
        <div style={{ display: "flex", gap: 6 }} role="group" aria-label="Filter work orders">
          {FILTERS.map(f => (
            <button key={f.id} onClick={() => setFilter(f.id)} aria-pressed={filter === f.id}
              style={{ ...btn(filter === f.id), borderRadius: 20 }}>{f.label}</button>
          ))}
        </div>
        {perms.canWrite && (
          <button onClick={() => { setShowNew(true); setErr("") }} style={{ ...btn(true), display: "flex", alignItems: "center", gap: 6 }}>
            <Plus size={14} /> New work order
          </button>
        )}
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "var(--hf-text-faint)" }}>Loading...</div>
      ) : shown.length === 0 ? (
        <div style={{ textAlign: "center", padding: "50px 20px", color: "var(--hf-text-faint)", border: "1px dashed var(--hf-border)", borderRadius: 12 }}>
          <ClipboardList size={36} style={{ opacity: 0.4, marginBottom: 8 }} />
          <div>No work orders here.</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>{shown.map(renderRow)}</div>
      )}

      {showNew && renderNew()}
      {current && !dialog && renderDetail(current)}
      {dialog && renderDialog(dialog)}
    </div>
  )
}
