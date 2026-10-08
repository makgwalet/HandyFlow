// Wraps a patient's clinical record. A restricted record shows a gate instead of its content until the person breaks
// the glass with a typed reason (CLINIC-DEC-008, 009). Practice managers can restrict or lift from the strip.
import { useState, type ReactNode } from "react"
import { ShieldAlert } from "lucide-react"
import { usePermission } from "../../hooks/usePermission"
import { CATEGORIES, categoryLabel, reasonProblem, useRestriction } from "./useRestriction"

const box: React.CSSProperties = { maxWidth: 520, margin: "48px auto", padding: 24, background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 14 }
const field: React.CSSProperties = { width: "100%", boxSizing: "border-box", padding: 8, border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 14, marginTop: 6 }
const primary: React.CSSProperties = { padding: "8px 14px", borderRadius: 8, border: "none", background: "var(--hf-primary)", color: "#fff", fontWeight: 600, cursor: "pointer" }
const quiet: React.CSSProperties = { padding: "6px 10px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "none", cursor: "pointer", fontSize: 13 }

const clock = (iso: string | null) => (iso ? new Date(iso).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit", timeZone: "Africa/Johannesburg" }) : "")
const apiMessage = (e: any, fallback: string) => e?.response?.data?.message ?? fallback

export default function RestrictedRecordGate({ patientId, children }: { patientId: string; children: ReactNode }) {
  const { restriction, breakGlass, flag, release } = useRestriction(patientId)
  const canBreak = usePermission("CLINIC_BREAK_GLASS_VIEW")
  const canManage = usePermission("CLINIC_RESTRICTED_RECORD_MANAGE")
  const [reason, setReason] = useState("")
  const [category, setCategory] = useState(CATEGORIES[0].value)
  const [form, setForm] = useState<"" | "flag" | "release">("")
  const [error, setError] = useState("")

  const problem = reasonProblem(reason)
  const run = (fn: () => Promise<unknown>, fallback: string) => {
    setError("")
    fn().then(() => { setReason(""); setForm("") }).catch(e => setError(apiMessage(e, fallback)))
  }

  if (restriction.restricted && !restriction.canAccess) {
    return (
      <div role="alert" style={box}>
        <div style={{ display: "flex", gap: 10, alignItems: "center", fontWeight: 700, fontSize: 16 }}>
          <ShieldAlert size={20}/> This record is restricted ({categoryLabel(restriction.category)})
        </div>
        <p style={{ fontSize: 14, color: "var(--hf-text-muted)" }}>
          Open it only if you need it to care for this patient. Your reason is recorded, the practice is alerted, and everything you view is logged.
        </p>
        {canBreak ? (
          <>
            <label style={{ fontSize: 13, fontWeight: 600 }}>Why do you need this record?
              <textarea aria-label="Reason" value={reason} onChange={e => setReason(e.target.value)} rows={3} style={field}/>
            </label>
            {error && <div role="alert" style={{ color: "var(--hf-danger, #b00020)", fontSize: 13, marginTop: 6 }}>{error}</div>}
            <button style={{ ...primary, marginTop: 10, opacity: problem || breakGlass.isPending ? 0.5 : 1 }} disabled={!!problem || breakGlass.isPending}
              title={problem ?? undefined} onClick={() => run(() => breakGlass.mutateAsync(reason), "Could not open the record.")}>
              Open record for 60 minutes
            </button>
          </>
        ) : (
          <div style={{ fontSize: 14 }}>You do not have permission to open restricted records. Ask the practice manager.</div>
        )}
      </div>
    )
  }

  return (
    <>
      {restriction.restricted && (
        <div role="status" style={{ display: "flex", gap: 10, alignItems: "center", padding: "8px 14px", background: "var(--hf-warning-bg, #fff4e5)", fontSize: 13 }}>
          <ShieldAlert size={16}/>
          <span>Restricted record ({categoryLabel(restriction.category)}){restriction.breakGlassUntil ? ` — open until ${clock(restriction.breakGlassUntil)}, every view is logged` : " — you have standing access"}</span>
          {canManage && <button style={quiet} onClick={() => { setError(""); setForm(form === "release" ? "" : "release") }}>Lift restriction</button>}
        </div>
      )}
      {!restriction.restricted && canManage && (
        <div style={{ textAlign: "right", padding: "4px 14px" }}>
          <button style={quiet} onClick={() => { setError(""); setForm(form === "flag" ? "" : "flag") }}>Restrict this record</button>
        </div>
      )}
      {form && (
        <div style={{ padding: "10px 14px", borderBottom: "1px solid var(--hf-border)" }}>
          {form === "flag" && (
            <label style={{ fontSize: 13, fontWeight: 600 }}>Category
              <select aria-label="Category" value={category} onChange={e => setCategory(e.target.value)} style={field}>
                {CATEGORIES.map(c => <option key={c.value} value={c.value}>{c.label}</option>)}
              </select>
            </label>
          )}
          <label style={{ fontSize: 13, fontWeight: 600, display: "block", marginTop: 8 }}>Reason (kept in the record)
            <textarea aria-label="Reason" value={reason} onChange={e => setReason(e.target.value)} rows={2} style={field}/>
          </label>
          {error && <div role="alert" style={{ color: "var(--hf-danger, #b00020)", fontSize: 13, marginTop: 6 }}>{error}</div>}
          <button style={{ ...primary, marginTop: 8, opacity: problem ? 0.5 : 1 }} disabled={!!problem} title={problem ?? undefined}
            onClick={() => form === "flag"
              ? run(() => flag.mutateAsync({ category, reason }), "Could not restrict the record.")
              : run(() => release.mutateAsync(reason), "Could not lift the restriction.")}>
            {form === "flag" ? "Restrict" : "Lift restriction"}
          </button>
        </div>
      )}
      {children}
    </>
  )
}
