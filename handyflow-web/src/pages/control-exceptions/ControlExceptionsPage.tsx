// src/pages/control-exceptions/ControlExceptionsPage.tsx
//
// The shared "needs attention" board — Stage 1 of the Financial
// Control & Assurance plan, Option C. Every open exception raised by
// any module that's adopted ControlExceptionFacade, in one place.
// First (and currently only) real source: SCM's three-way match
// dispute flow, which now raises a parallel record here at the same
// moment it already notifies internally — see ScmService's
// createSupplierInvoice() dual-write hook.
//
// Deliberately view-and-resolve only — no filtering/sorting UI, no
// severity breakdown, nothing fancy. This is the first real screen for
// a brand-new concept; better to prove the concept works end-to-end
// with something simple than to over-build a dashboard for a board
// that, as of this pass, has exactly one real source feeding it.
import { useEffect, useState } from "react"
import { controlExceptionsApi, type ControlException } from "../../api/controlExceptions.api"

const fmtDT = (d: any) => (d ? new Date(d).toLocaleString("en-ZA", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }) : "—")

const NAVY = "#1B3A6B"
const BORDER = "#E2E8F0"
const CANVAS = "#F8FAFC"
const INK = "#0F172A"
const MUTED = "#64748B"
const FAINT = "#94A3B8"

export function ControlExceptionsPage() {
  const [exceptions, setExceptions] = useState<ControlException[]>([])
  const [loading, setLoading] = useState(true)
  const [resolving, setResolving] = useState<ControlException | null>(null)

  const refetch = () => {
    setLoading(true)
    controlExceptionsApi.listOpen().then(setExceptions).finally(() => setLoading(false))
  }
  useEffect(refetch, [])

  return (
    <div style={{ padding: "24px 32px", fontFamily: "'Inter', system-ui, sans-serif", maxWidth: 900 }}>
      <h1 style={{ fontSize: 20, fontWeight: 800, color: INK, marginBottom: 4 }}>Needs Attention</h1>
      <p style={{ fontSize: 13, color: MUTED, marginBottom: 24 }}>
        Everything flagged across the business that hasn't been resolved yet.
      </p>

      {loading ? (
        <div style={{ padding: 32, textAlign: "center" as const, color: FAINT, fontSize: 13 }}>Loading…</div>
      ) : exceptions.length === 0 ? (
        <div style={{ padding: 40, textAlign: "center" as const, color: FAINT, fontSize: 14, background: "#fff",
          border: `1px solid ${BORDER}`, borderRadius: 8 }}>
          Nothing needs attention right now.
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {exceptions.map(e => (
            <div key={e.id} style={{ background: "#fff", border: `1px solid ${BORDER}`, borderRadius: 8, padding: "14px 16px" }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                <div style={{ flex: 1 }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 4 }}>
                    <SeverityBadge severity={e.severity} />
                    <span style={{ fontSize: 11, color: FAINT, textTransform: "uppercase" as const, letterSpacing: "0.03em" }}>
                      {e.sourceModule} · {e.controlType.replace(/_/g, " ")}
                    </span>
                  </div>
                  <div style={{ fontSize: 13.5, color: INK }}>{e.description}</div>
                  <div style={{ fontSize: 11, color: FAINT, marginTop: 6 }}>Flagged {fmtDT(e.detectedAt)}</div>
                </div>
                <button onClick={() => setResolving(e)} style={btnSecondary}>Resolve</button>
              </div>
            </div>
          ))}
        </div>
      )}

      {resolving && (
        <ResolveModal exception={resolving} onClose={() => setResolving(null)}
          onResolved={() => { setResolving(null); refetch() }} />
      )}
    </div>
  )
}

function SeverityBadge({ severity }: { severity: string }) {
  const tones: Record<string, { c: string; bg: string }> = {
    WARNING: { c: "#D97706", bg: "#FFFBEB" },
    CRITICAL: { c: "#DC2626", bg: "#FEF2F2" },
  }
  const t = tones[severity] ?? tones.WARNING
  return <span style={{ background: t.bg, color: t.c, padding: "2px 8px", borderRadius: 20, fontSize: 10, fontWeight: 700 }}>{severity}</span>
}

function ResolveModal({ exception, onClose, onResolved }: { exception: ControlException; onClose: () => void; onResolved: () => void }) {
  const [notes, setNotes] = useState("")
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState("")

  const submit = async () => {
    setSaving(true); setError("")
    try {
      await controlExceptionsApi.resolve(exception.id, notes || undefined)
      onResolved()
    } catch (e: any) {
      setError(e.response?.data?.message ?? "Failed to resolve")
    } finally { setSaving(false) }
  }

  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}
      onClick={onClose}>
      <div style={{ background: "#fff", borderRadius: 12, padding: 24, width: 420, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}
        onClick={e => e.stopPropagation()}>
        <h3 style={{ margin: "0 0 12px", fontSize: 15, fontWeight: 800, color: INK }}>Resolve</h3>
        <p style={{ fontSize: 13, color: MUTED, marginBottom: 14 }}>{exception.description}</p>
        <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: MUTED, marginBottom: 4 }}>
          Notes (optional)
        </label>
        <textarea value={notes} onChange={e => setNotes(e.target.value)}
          style={{ width: "100%", padding: "8px 10px", border: `1.5px solid ${BORDER}`, borderRadius: 6, fontSize: 13,
            minHeight: 60, resize: "vertical" as const, fontFamily: "inherit", boxSizing: "border-box" as const, marginBottom: 12 }} />
        {error && <div style={{ padding: "8px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 6, fontSize: 12.5, color: "#DC2626", marginBottom: 12 }}>{error}</div>}
        <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
          <button onClick={onClose} style={btnSecondary}>Cancel</button>
          <button onClick={submit} disabled={saving} style={btnPrimary}>{saving ? "Saving…" : "Mark Resolved"}</button>
        </div>
      </div>
    </div>
  )
}

const btnPrimary: React.CSSProperties = { padding: "7px 14px", background: NAVY, color: "#fff", border: "none", borderRadius: 6, fontSize: 12.5, fontWeight: 700, cursor: "pointer" }
const btnSecondary: React.CSSProperties = { padding: "6px 12px", background: "#fff", color: NAVY, border: `1px solid ${NAVY}`, borderRadius: 6, fontSize: 12, fontWeight: 700, cursor: "pointer" }
