// Audit Settings: the rules internal audit runs by. Anyone with audit access can read them; the Head of Internal Audit (AUDIT_ADMIN) can change them.

import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { auditSettingsApi, type AuditSettingValues } from "../../api/auditSettings.api"
import { usePermission } from "../../hooks/usePermission"
import { Panel, Pill } from "./auditUi"
import { filterField } from "./auditUi.styles"
import { bandsFrom, problems, sameValues, toNumber } from "./settings.logic"

const MUTED = "var(--hf-text-muted)"
const TONE: Record<string, "success" | "info" | "warning" | "muted"> = { LOW: "success", MEDIUM: "info", HIGH: "warning", CRITICAL: "warning" }
const row: React.CSSProperties = { display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, fontSize: 13, padding: "6px 0", borderBottom: "1px solid var(--hf-border-subtle)" }
const num: React.CSSProperties = { ...filterField, width: 72, textAlign: "right" }
const btn: React.CSSProperties = { padding: "7px 14px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text-secondary)", fontWeight: 600, fontSize: 13, cursor: "pointer" }
const primary: React.CSSProperties = { ...btn, background: "var(--hf-primary)", borderColor: "var(--hf-primary)", color: "var(--hf-text-on-solid)" }

type Draft = Record<keyof AuditSettingValues, string>
const toDraft = (v: AuditSettingValues): Draft => ({ lowMax: String(v.lowMax), mediumMax: String(v.mediumMax), highMax: String(v.highMax), escalateAfterDays: String(v.escalateAfterDays), repeatEveryDays: String(v.repeatEveryDays), firstReviewReminderDay: String(v.firstReviewReminderDay) })
const fromDraft = (d: Draft): AuditSettingValues => ({ lowMax: toNumber(d.lowMax), mediumMax: toNumber(d.mediumMax), highMax: toNumber(d.highMax), escalateAfterDays: toNumber(d.escalateAfterDays), repeatEveryDays: toNumber(d.repeatEveryDays), firstReviewReminderDay: toNumber(d.firstReviewReminderDay) })

export function SettingsSection() {
  const qc = useQueryClient()
  const canEdit = usePermission("AUDIT_ADMIN")
  const { data, isLoading, isError } = useQuery({ queryKey: ["ia-settings"], queryFn: auditSettingsApi.get })
  const [draft, setDraft] = useState<Draft | null>(null)
  const [message, setMessage] = useState("")
  const [error, setError] = useState("")

  const after = (s: Awaited<ReturnType<typeof auditSettingsApi.get>>, text: string) => { qc.setQueryData(["ia-settings"], s); setDraft(null); setError(""); setMessage(text) }
  const fail = (e: unknown) => { setMessage(""); setError((e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? "The settings could not be saved. Try again; if it keeps failing, contact support.") }
  const save = useMutation({ mutationFn: auditSettingsApi.save, onSuccess: s => after(s, "Saved. New risk assessments use these bands; assessments already made keep their level."), onError: fail })
  const reset = useMutation({ mutationFn: auditSettingsApi.reset, onSuccess: s => after(s, "Back to the standard rules."), onError: fail })

  if (isLoading) return <div style={{ color: MUTED, fontSize: 13 }}>Loading…</div>
  if (isError || !data) return <div role="alert" style={{ padding: "10px 14px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, color: "var(--hf-danger-text)", fontSize: 13 }}>Settings could not be loaded. Refresh the page; if it keeps failing, contact support.</div>

  const d = draft ?? toDraft(data.values)
  const values = fromDraft(d)
  const issues = problems(values)
  const changed = !sameValues(values, data.values)
  const set = (k: keyof AuditSettingValues, v: string) => { setDraft({ ...d, [k]: v }); setMessage("") }
  const bands = issues.length === 0 ? bandsFrom(values) : data.riskBands
  const field = (k: keyof AuditSettingValues, label: string) => (
    <label style={row}><span>{label}</span>
      {canEdit ? <input inputMode="numeric" aria-label={label} value={d[k]} onChange={e => set(k, e.target.value)} style={num} /> : <strong>{d[k]}</strong>}
    </label>
  )

  return (
    <div style={{ display: "grid", gap: 14 }}>
      <p style={{ fontSize: 13, color: MUTED, margin: 0 }}>
        {canEdit ? "These are the rules audit follows. Change them here; every change is recorded in the audit trail." : "These are the rules audit follows. The Head of Internal Audit can change them."}
        {data.custom ? " This company has changed them from the standard rules." : " This company is using the standard rules."}
      </p>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))", gap: 14 }}>
        <Panel title="Risk scoring">
          <p style={{ fontSize: 12.5, color: MUTED, margin: "0 0 10px" }}>Each area is scored 1 to 5 on {data.riskInputs.length} things ({data.riskInputs.join(", ").toLowerCase()}), equally weighted. The total ({data.scoreMin} to {data.scoreMax}) sets the level. An auditor can override the level with a reason.</p>
          {field("lowMax", "Low ends at")}
          {field("mediumMax", "Medium ends at")}
          {field("highMax", "High ends at")}
          <div style={{ marginTop: 10 }}>
            {bands.map(b => (
              <div key={b.level} style={row}><Pill label={b.level.charAt(0) + b.level.slice(1).toLowerCase()} tone={TONE[b.level] ?? "muted"} /><span>{b.from} to {b.to}</span></div>
            ))}
          </div>
        </Panel>
        <Panel title="Reminders">
          {field("repeatEveryDays", "Remind every (days)")}
          {field("escalateAfterDays", "Escalate to a manager after (days late)")}
          {field("firstReviewReminderDay", "Unanswered review comment, first reminder (days)")}
          <p style={{ fontSize: 12, color: MUTED, margin: "10px 0 0" }}>Overdue items are reminded on day 1, then at the interval above. Escalation happens on the first reminder day on or after the escalation day.</p>
        </Panel>
        <Panel title="Engagement roles">
          <ul style={{ listStyle: "none", margin: 0, padding: 0 }}>
            {data.engagementRoles.map(r => <li key={r.key} style={row}><span>{r.label}</span></li>)}
          </ul>
          <p style={{ fontSize: 12, color: MUTED, margin: "10px 0 0" }}>Roles are given to people on each engagement, from the Engagements list.</p>
        </Panel>
      </div>

      {canEdit && (
        <div style={{ display: "grid", gap: 8 }}>
          {changed && issues.length > 0 && <ul role="alert" style={{ margin: 0, paddingLeft: 18, color: "var(--hf-danger-text)", fontSize: 13 }}>{issues.map(i => <li key={i}>{i}</li>)}</ul>}
          {error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>{error}</div>}
          {message && <div role="status" style={{ color: "var(--hf-success-text)", fontSize: 13 }}>{message}</div>}
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <button type="button" style={primary} disabled={!changed || issues.length > 0 || save.isPending} onClick={() => save.mutate(values)}>{save.isPending ? "Saving…" : "Save changes"}</button>
            {changed && <button type="button" style={btn} onClick={() => { setDraft(null); setMessage("") }}>Discard changes</button>}
            {data.custom && !changed && <button type="button" style={btn} disabled={reset.isPending} onClick={() => reset.mutate()}>Go back to standard rules</button>}
          </div>
          <p style={{ fontSize: 12, color: MUTED, margin: 0 }}>New risk assessments use the new bands. Assessments already made keep the level they were given until they are assessed again.</p>
        </div>
      )}
    </div>
  )
}
