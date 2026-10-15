// src/pages/earthmoving/AlertsTab.tsx
//
// Alerts: things that need a person. Each is sent to the people the company chose for that kind of alert (company
// administrators if none are chosen), waits to be acknowledged, and goes to the next level if nobody does in time.
// Administrators choose who is told on the "Who is told" tab.
import { useState } from "react"
import { Bell, ShieldAlert } from "lucide-react"
import { ALERT_TYPE_LABEL } from "./shared/constants"
import { fmtDateTime, apiMessage } from "./shared/format"
import { Overlay, ModalHead, ModalFoot, ErrBanner, lbl, inputStyle } from "./shared/Modal"
import { useEarthPerms } from "./shared/useEarthPerms"
import { useAlerts, useAlertAction, useAlertRules, useSaveAlertRule, useAssignableUsers } from "./shared/hooks"
import type { EarthAlert, AlertRule } from "./shared/types"

const SEV_COLOR: Record<string, string> = {
  CRITICAL: "var(--hf-danger-text)", WARNING: "var(--hf-warning-text)", INFO: "var(--hf-text-secondary)",
}
const STATUS_LABEL: Record<string, string> = { OPEN: "Open", ACKNOWLEDGED: "Acknowledged", RESOLVED: "Resolved" }

const btn = (primary = false): React.CSSProperties => ({
  padding: "6px 12px", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer",
  border: primary ? "none" : "1px solid var(--hf-border)",
  background: primary ? "var(--hf-primary)" : "var(--hf-surface)",
  color: primary ? "var(--hf-text-on-solid)" : "var(--hf-text-secondary)",
})

type RuleForm = { enabled: boolean; escalate: string; initial: string[]; escalation: string[] }

export default function AlertsTab() {
  const perms = useEarthPerms()
  const [view, setView] = useState<"alerts" | "rules">("alerts")
  const [openOnly, setOpenOnly] = useState(true)
  const [resolving, setResolving] = useState<EarthAlert | null>(null)
  const [note, setNote] = useState("")
  const [editing, setEditing] = useState<AlertRule | null>(null)
  const [form, setForm] = useState<RuleForm>({ enabled: true, escalate: "", initial: [], escalation: [] })
  const [err, setErr] = useState("")

  const { data: alerts = [], isLoading } = useAlerts(openOnly)
  const { data: users = [] } = useAssignableUsers()
  const { data: rules = [] } = useAlertRules(perms.canAdmin && view === "rules")
  const act = useAlertAction()
  const saveRule = useSaveAlertRule(() => { setEditing(null); setErr("") })

  const name = (id: string | null) => id ? (users.find(u => u.id === id)?.name ?? "Another user") : "Nobody"

  const openRule = (r: AlertRule) => {
    setForm({ enabled: r.enabled, escalate: r.escalateAfterMinutes == null ? "" : String(r.escalateAfterMinutes), initial: r.initialUserIds, escalation: r.escalationUserIds })
    setErr(""); setEditing(r)
  }
  const toggle = (list: string[], id: string) => list.includes(id) ? list.filter(x => x !== id) : [...list, id]

  // ── alert cards ─────────────────────────────────────────────────────────
  const renderAlert = (a: EarthAlert) => (
    <div key={a.id} style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: "12px 16px" }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 12 }}>
        <div>
          <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
            <strong style={{ fontSize: 14 }}>{a.title}</strong>
            <span style={{ fontSize: 11, fontWeight: 700, color: SEV_COLOR[a.severity] }}>{a.severity}</span>
            <span style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{ALERT_TYPE_LABEL[a.alertType] ?? a.alertType}</span>
            {a.level === 2 && <span style={{ fontSize: 11, fontWeight: 700, color: "var(--hf-danger-text)" }}>Escalated</span>}
          </div>
          <div style={{ fontSize: 13, color: "var(--hf-text-secondary)", marginTop: 3 }}>{a.message}</div>
          <div style={{ fontSize: 12, color: "var(--hf-text-faint)", marginTop: 3 }}>
            Raised {fmtDateTime(a.createdAt)}
            {a.status === "ACKNOWLEDGED" && ` · acknowledged by ${name(a.acknowledgedBy)}`}
            {a.status === "RESOLVED" && ` · resolved ${fmtDateTime(a.resolvedAt)}${a.resolutionNote ? `: ${a.resolutionNote}` : ""}`}
          </div>
          {a.deliveries.length > 0 && (
            <div style={{ fontSize: 12, color: "var(--hf-text-faint)", marginTop: 3 }}>
              Sent to {a.deliveries.map(d => `${d.userId ? name(d.userId) : "nobody"}${d.status === "FAILED" ? " (failed)" : ""}`).join(", ")}
            </div>
          )}
        </div>
        <div style={{ textAlign: "right", flexShrink: 0 }}>
          <div style={{ fontSize: 12, fontWeight: 700 }}>{STATUS_LABEL[a.status] ?? a.status}</div>
          <div style={{ display: "flex", gap: 6, marginTop: 6, justifyContent: "flex-end" }}>
            {a.status === "OPEN" && perms.canWrite && (
              <button style={btn(true)} disabled={act.isPending}
                onClick={() => act.mutate({ id: a.id, action: "acknowledge" }, { onError: e => setErr(apiMessage(e, "Could not acknowledge.")) })}>Acknowledge</button>
            )}
            {a.status !== "RESOLVED" && perms.canMaintain && (
              <button style={btn()} onClick={() => { setResolving(a); setNote(""); setErr("") }}>Resolve…</button>
            )}
          </div>
        </div>
      </div>
    </div>
  )

  // ── rules ───────────────────────────────────────────────────────────────
  const renderRule = (r: AlertRule) => (
    <div key={r.alertType} style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: "12px 16px", display: "flex", justifyContent: "space-between", gap: 12 }}>
      <div>
        <strong style={{ fontSize: 14 }}>{ALERT_TYPE_LABEL[r.alertType] ?? r.alertType}</strong>
        {!r.enabled && <span style={{ marginLeft: 8, fontSize: 12, color: "var(--hf-text-faint)" }}>Switched off</span>}
        <div style={{ fontSize: 13, color: "var(--hf-text-secondary)", marginTop: 3 }}>
          First told: {r.usesAdminFallback ? "company administrators (nobody chosen)" : r.initialUserIds.map(name).join(", ")}
        </div>
        <div style={{ fontSize: 13, color: "var(--hf-text-secondary)" }}>
          {r.escalationUserIds.length === 0 ? "No escalation set up."
            : `If nobody acknowledges ${r.escalateAfterMinutes === 0 ? "at once" : `within ${r.escalateAfterMinutes} minutes`}: ${r.escalationUserIds.map(name).join(", ")}`}
        </div>
      </div>
      <button style={btn()} onClick={() => openRule(r)} aria-label={`Edit ${ALERT_TYPE_LABEL[r.alertType] ?? r.alertType}`}>Edit</button>
    </div>
  )

  const renderRuleDialog = (r: AlertRule) => {
    const label = ALERT_TYPE_LABEL[r.alertType] ?? r.alertType
    const hasEsc = form.escalation.length > 0
    const ok = !hasEsc || form.escalate !== ""
    const checks = (key: "initial" | "escalation", title: string) => (
      <fieldset style={{ border: "1px solid var(--hf-border)", borderRadius: 8, margin: "0 0 12px", padding: "8px 12px", maxHeight: 150, overflowY: "auto" }}>
        <legend style={{ fontSize: 12, fontWeight: 700, color: "var(--hf-text-secondary)" }}>{title}</legend>
        {users.map(u => (
          <label key={u.id} style={{ display: "flex", gap: 8, fontSize: 13, padding: "2px 0" }}>
            <input type="checkbox" checked={form[key].includes(u.id)} onChange={() => setForm(f => ({ ...f, [key]: toggle(f[key], u.id) }))} />
            {u.name}
          </label>
        ))}
      </fieldset>
    )
    return (
      <Overlay onClose={() => setEditing(null)} width={520}>
        <ModalHead title={`Who is told: ${label}`} onClose={() => setEditing(null)} />
        {r.canBeDisabled ? (
          <label style={{ display: "flex", gap: 8, fontSize: 13, marginBottom: 12 }}>
            <input type="checkbox" checked={form.enabled} onChange={e => setForm(f => ({ ...f, enabled: e.target.checked }))} /> Send these alerts
          </label>
        ) : (
          <div role="note" style={{ fontSize: 12, color: "var(--hf-text-faint)", marginBottom: 12 }}>
            Breakdown alerts cannot be switched off, and must escalate within 60 minutes if an escalation level is set.
          </div>
        )}
        {checks("initial", "Told first (leave empty to tell company administrators)")}
        {checks("escalation", "Told next if nobody acknowledges")}
        <label style={lbl} htmlFor="esc-min">Wait this many minutes before telling the next level (0 = at once)</label>
        <input id="esc-min" type="number" min="0" value={form.escalate} onChange={e => setForm(f => ({ ...f, escalate: e.target.value }))} style={inputStyle()} />
        {err && <ErrBanner msg={err} />}
        <ModalFoot onCancel={() => setEditing(null)} loading={saveRule.isPending} label="Save" disabled={!ok}
          onSubmit={() => { setErr(""); saveRule.mutate({ type: r.alertType, body: {
            enabled: form.enabled, escalateAfterMinutes: form.escalate === "" ? null : Number(form.escalate),
            initialUserIds: form.initial, escalationUserIds: form.escalation,
          } }, { onError: e => setErr(apiMessage(e, "Could not save.")) }) }} />
      </Overlay>
    )
  }

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16, flexWrap: "wrap", gap: 8 }}>
        <div style={{ display: "flex", gap: 6 }} role="group" aria-label="Alerts view">
          <button style={{ ...btn(view === "alerts"), borderRadius: 20 }} aria-pressed={view === "alerts"} onClick={() => setView("alerts")}>Alerts</button>
          {perms.canAdmin && (
            <button style={{ ...btn(view === "rules"), borderRadius: 20 }} aria-pressed={view === "rules"} onClick={() => setView("rules")}>Who is told</button>
          )}
        </div>
        {view === "alerts" && (
          <label style={{ fontSize: 13, display: "flex", gap: 6, alignItems: "center" }}>
            <input type="checkbox" checked={!openOnly} onChange={e => setOpenOnly(!e.target.checked)} /> Show resolved too
          </label>
        )}
      </div>
      {err && !editing && !resolving && <ErrBanner msg={err} />}

      {view === "alerts" ? (
        isLoading ? <div style={{ textAlign: "center", padding: 40, color: "var(--hf-text-faint)" }}>Loading...</div>
          : alerts.length === 0 ? (
            <div style={{ textAlign: "center", padding: "50px 20px", color: "var(--hf-text-faint)", border: "1px dashed var(--hf-border)", borderRadius: 12 }}>
              <Bell size={36} style={{ opacity: 0.4, marginBottom: 8 }} />
              <div>No {openOnly ? "open " : ""}alerts.</div>
            </div>
          ) : <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>{alerts.map(renderAlert)}</div>
      ) : (
        <div>
          <div style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 13, color: "var(--hf-text-secondary)", marginBottom: 12 }}>
            <ShieldAlert size={16} /> Choose who is told about each kind of alert, and who is told next if nobody acknowledges it.
          </div>
          <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>{rules.map(renderRule)}</div>
        </div>
      )}

      {editing && renderRuleDialog(editing)}
      {resolving && (
        <Overlay onClose={() => setResolving(null)} width={460}>
          <ModalHead title="Resolve alert" subtitle={resolving.title} onClose={() => setResolving(null)} />
          <label style={lbl} htmlFor="alert-note">What was done? *</label>
          <textarea id="alert-note" rows={3} value={note} onChange={e => setNote(e.target.value)} style={inputStyle()} />
          {err && <ErrBanner msg={err} />}
          <ModalFoot onCancel={() => setResolving(null)} loading={act.isPending} label="Resolve" disabled={note.trim().length < 5}
            onSubmit={() => act.mutate({ id: resolving.id, action: "resolve", body: { note: note.trim() } },
              { onSuccess: () => setResolving(null), onError: e => setErr(apiMessage(e, "Could not resolve.")) })} />
        </Overlay>
      )}
    </div>
  )
}
