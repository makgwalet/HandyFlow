// src/pages/earthmoving/PlansTab.tsx
//
// Service plans: "every 250 hours" or "every 90 days". A plan that falls due raises one work order (never two for the
// same plan). The check runs each morning, on "Check now", and the moment a machine's meter is updated.
import { useState } from "react"
import { Plus, CalendarClock, RefreshCw } from "lucide-react"
import { MAINTENANCE_TYPES } from "./shared/constants"
import { fmtDate, apiMessage, meterWordOf } from "./shared/format"
import { Overlay, ModalHead, ModalFoot, ErrBanner, lbl, inputStyle } from "./shared/Modal"
import { useEarthPerms } from "./shared/useEarthPerms"
import { useAssets, usePlans, useSavePlan, useScanPlans } from "./shared/hooks"
import type { MaintenancePlan } from "./shared/types"

const STATE_CFG: Record<string, { label: string; color: string }> = {
  OK: { label: "On track", color: "var(--hf-success-text-strong)" },
  DUE_SOON: { label: "Due soon", color: "var(--hf-warning-text)" },
  DUE: { label: "Due", color: "var(--hf-danger-text)" },
  OVERDUE: { label: "Overdue", color: "var(--hf-danger-text)" },
}
const PRIORITIES = ["LOW", "NORMAL", "HIGH", "URGENT"]

const EMPTY = {
  assetId: "", name: "", taskType: "SERVICE", triggerType: "HOURS", intervalValue: "250", leadValue: "25",
  lastDoneHours: "", lastDoneDate: "", priority: "NORMAL", blocksRelease: true, active: true,
}

export default function PlansTab() {
  const perms = useEarthPerms()
  const [editing, setEditing] = useState<MaintenancePlan | "new" | null>(null)
  const [form, setForm] = useState(EMPTY)
  const [err, setErr] = useState("")
  const [scanMsg, setScanMsg] = useState("")

  const { data: assets = [] } = useAssets()
  const { data: plans = [], isLoading } = usePlans()
  const save = useSavePlan(() => { setEditing(null); setErr("") })
  const scan = useScanPlans(r => setScanMsg(
    r.workOrdersRaised === 0 ? `Checked ${r.checked} plan${r.checked === 1 ? "" : "s"}. Nothing new is due.`
      : `Checked ${r.checked} plans. Raised ${r.workOrdersRaised} work order${r.workOrdersRaised === 1 ? "" : "s"}: ${r.raised.join(", ")}.`))

  const assetName = (id: string) => {
    const a = assets.find(x => x.id === id)
    return a ? `${a.fleetNumber ? a.fleetNumber + " — " : ""}${a.name}` : "Unknown machine"
  }
  const openNew = () => { setForm(EMPTY); setErr(""); setEditing("new") }
  const openEdit = (p: MaintenancePlan) => {
    setForm({
      assetId: p.assetId, name: p.name, taskType: p.taskType, triggerType: p.triggerType,
      intervalValue: String(p.intervalValue), leadValue: String(p.leadValue),
      lastDoneHours: p.lastDoneHours == null ? "" : String(p.lastDoneHours), lastDoneDate: p.lastDoneDate ?? "",
      priority: p.priority, blocksRelease: p.blocksRelease, active: p.active,
    })
    setErr(""); setEditing(p)
  }

  const renderRow = (p: MaintenancePlan) => {
    const cfg = STATE_CFG[p.state] ?? STATE_CFG.OK
    return (
      <div key={p.id} style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: "12px 16px", display: "flex", justifyContent: "space-between", gap: 12, opacity: p.active ? 1 : 0.6 }}>
        <div>
          <div style={{ fontSize: 14, fontWeight: 600 }}>{p.name}{!p.active && " (paused)"}</div>
          <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>
            {assetName(p.assetId)} · {p.taskType} · every {p.intervalValue} {p.unit}
            {p.lastDoneDate && ` · last done ${fmtDate(p.lastDoneDate)}`}
          </div>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <div style={{ textAlign: "right" }}>
            <div style={{ fontSize: 12, fontWeight: 700, color: cfg.color }}>{cfg.label}</div>
            <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>
              {p.remaining >= 0 ? `${Math.round(p.remaining)} ${p.unit} left` : `${Math.round(-p.remaining)} ${p.unit} over`}
            </div>
          </div>
          {perms.canMaintain && (
            <button onClick={() => openEdit(p)} aria-label={`Edit ${p.name}`}
              style={{ padding: "6px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", cursor: "pointer", fontSize: 13 }}>Edit</button>
          )}
        </div>
      </div>
    )
  }

  const renderForm = () => {
    const isNew = editing === "new"
    const ok = form.name.trim() !== "" && (!isNew || form.assetId !== "") && Number(form.intervalValue) > 0
    const meterWord = meterWordOf(assets.find(x => x.id === form.assetId))
    const unit = form.triggerType === "HOURS" ? meterWord : "days"
    return (
      <Overlay onClose={() => setEditing(null)} width={540}>
        <ModalHead title={isNew ? "New service plan" : "Edit service plan"} onClose={() => setEditing(null)} />
        {isNew && (
          <>
            <label style={lbl} htmlFor="pl-asset">Machine *</label>
            <select id="pl-asset" value={form.assetId} onChange={e => setForm(f => ({ ...f, assetId: e.target.value }))} style={inputStyle()}>
              <option value="">Choose a machine…</option>
              {assets.filter(a => a.status !== "RETIRED").map(a => <option key={a.id} value={a.id}>{assetName(a.id)}</option>)}
            </select>
          </>
        )}
        <label style={{ ...lbl, marginTop: 12 }} htmlFor="pl-name">Name *</label>
        <input id="pl-name" value={form.name} placeholder="e.g. 250 hour service" onChange={e => setForm(f => ({ ...f, name: e.target.value }))} style={inputStyle()} />
        {isNew && (
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, marginTop: 12 }}>
            <div><label style={lbl} htmlFor="pl-type">Job type</label>
              <select id="pl-type" value={form.taskType} onChange={e => setForm(f => ({ ...f, taskType: e.target.value }))} style={inputStyle()}>
                {MAINTENANCE_TYPES.map(t => <option key={t}>{t}</option>)}</select></div>
            <div><label style={lbl} htmlFor="pl-trig">Counted in</label>
              <select id="pl-trig" value={form.triggerType} onChange={e => setForm(f => ({ ...f, triggerType: e.target.value }))} style={inputStyle()}>
                <option value="HOURS">{meterWord === "kilometres" ? "Kilometres" : "Operating hours"}</option><option value="DAYS">Days</option></select></div>
          </div>
        )}
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 10, marginTop: 12 }}>
          <div><label style={lbl} htmlFor="pl-int">Every ({unit}) *</label>
            <input id="pl-int" type="number" min="1" value={form.intervalValue} onChange={e => setForm(f => ({ ...f, intervalValue: e.target.value }))} style={inputStyle()} /></div>
          <div><label style={lbl} htmlFor="pl-lead">Warn ({unit}) before</label>
            <input id="pl-lead" type="number" min="0" value={form.leadValue} onChange={e => setForm(f => ({ ...f, leadValue: e.target.value }))} style={inputStyle()} /></div>
          <div><label style={lbl} htmlFor="pl-pri">Priority</label>
            <select id="pl-pri" value={form.priority} onChange={e => setForm(f => ({ ...f, priority: e.target.value }))} style={inputStyle()}>
              {PRIORITIES.map(t => <option key={t}>{t}</option>)}</select></div>
        </div>
        {isNew && (
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, marginTop: 12 }}>
            <div><label style={lbl} htmlFor="pl-lh">Last done at ({meterWord})</label>
              <input id="pl-lh" type="number" min="0" step="0.1" placeholder="Leave blank to count from now" value={form.lastDoneHours} onChange={e => setForm(f => ({ ...f, lastDoneHours: e.target.value }))} style={inputStyle()} /></div>
            <div><label style={lbl} htmlFor="pl-ld">Last done on</label>
              <input id="pl-ld" type="date" value={form.lastDoneDate} onChange={e => setForm(f => ({ ...f, lastDoneDate: e.target.value }))} style={inputStyle()} /></div>
          </div>
        )}
        <label style={{ display: "flex", gap: 8, marginTop: 14, fontSize: 13, alignItems: "center" }}>
          <input type="checkbox" checked={form.blocksRelease} onChange={e => setForm(f => ({ ...f, blocksRelease: e.target.checked }))} />
          Its work orders stop the machine being released
        </label>
        {!isNew && (
          <label style={{ display: "flex", gap: 8, marginTop: 8, fontSize: 13, alignItems: "center" }}>
            <input type="checkbox" checked={form.active} onChange={e => setForm(f => ({ ...f, active: e.target.checked }))} />
            Plan is active
          </label>
        )}
        {err && <ErrBanner msg={err} />}
        <ModalFoot onCancel={() => setEditing(null)} loading={save.isPending} label={isNew ? "Create plan" : "Save"} disabled={!ok}
          onSubmit={() => {
            setErr("")
            const common = {
              name: form.name, intervalValue: Number(form.intervalValue), leadValue: Number(form.leadValue || 0),
              priority: form.priority, blocksRelease: form.blocksRelease,
            }
            const onError = (e: unknown) => setErr(apiMessage(e, "Could not save the plan."))
            if (isNew) {
              save.mutate({ body: {
                ...common, assetId: form.assetId, taskType: form.taskType, triggerType: form.triggerType,
                lastDoneHours: form.lastDoneHours === "" ? null : Number(form.lastDoneHours), lastDoneDate: form.lastDoneDate || null,
              } }, { onError })
            } else {
              save.mutate({ id: (editing as MaintenancePlan).id, body: { ...common, active: form.active } }, { onError })
            }
          }} />
      </Overlay>
    )
  }

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18, gap: 10, flexWrap: "wrap" }}>
        <div style={{ fontSize: 13, color: "var(--hf-text-secondary)" }}>
          Plans are checked every morning. A plan that is due raises one work order.
        </div>
        {perms.canMaintain && (
          <div style={{ display: "flex", gap: 8 }}>
            <button onClick={() => { setScanMsg(""); scan.mutate(undefined, { onError: e => setScanMsg(apiMessage(e, "The check failed.")) }) }} disabled={scan.isPending}
              style={{ display: "flex", alignItems: "center", gap: 6, padding: "8px 14px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", cursor: "pointer", fontSize: 13, fontWeight: 600 }}>
              <RefreshCw size={14} /> {scan.isPending ? "Checking…" : "Check now"}
            </button>
            <button onClick={openNew}
              style={{ display: "flex", alignItems: "center", gap: 6, padding: "8px 14px", border: "none", borderRadius: 8, background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", cursor: "pointer", fontSize: 13, fontWeight: 600 }}>
              <Plus size={14} /> New plan
            </button>
          </div>
        )}
      </div>
      {scanMsg && <div role="status" style={{ marginBottom: 14, padding: "10px 14px", borderRadius: 8, background: "var(--hf-info-soft)", color: "var(--hf-info-text)", fontSize: 13 }}>{scanMsg}</div>}

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "var(--hf-text-faint)" }}>Loading...</div>
      ) : plans.length === 0 ? (
        <div style={{ textAlign: "center", padding: "50px 20px", color: "var(--hf-text-faint)", border: "1px dashed var(--hf-border)", borderRadius: 12 }}>
          <CalendarClock size={36} style={{ opacity: 0.4, marginBottom: 8 }} />
          <div>No service plans yet.</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>{plans.map(renderRow)}</div>
      )}
      {editing && renderForm()}
    </div>
  )
}
