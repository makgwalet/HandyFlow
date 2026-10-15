import { useCallback, useEffect, useState } from "react"
import { auditPackApi, type PackRun, type PackVerification } from "../../api/auditPack.api"

const BORDER = "var(--hf-border)"
const MUTED = "var(--hf-text-muted)"
const FAINT = "var(--hf-text-faint)"
const INK = "var(--hf-text)"
const NAVY = "var(--hf-primary)"
const field = { width: "100%", padding: "8px 10px", border: `1.5px solid ${BORDER}`, borderRadius: 6, fontSize: 13, boxSizing: "border-box" as const }
const btn = { padding: "5px 12px", background: "var(--hf-surface)", border: `1px solid ${BORDER}`, borderRadius: 6, fontSize: 12, fontWeight: 700, cursor: "pointer" }
const day = (d: string) => new Date(d.length === 10 ? d + "T00:00:00" : d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })

interface EngagementOption { id: string; name: string; periodFrom: string; periodTo: string }

/** The two-year limit the server also enforces, so the button can say why it is off. */
export const periodProblem = (from: string, to: string): string | null => {
  if (!from || !to) return "Choose the period to audit"
  if (to < from) return "The period must end on or after the day it starts"
  const days = (new Date(to).getTime() - new Date(from).getTime()) / 86400000
  return days > 731 ? "A pack covers at most two years" : null
}

/** Builds the financial schedules an auditor asks for (trial balance, ledger, journals, reconciliations, debtors, fixed assets, VAT) for a period, and checks an earlier pack still matches the books. */
export function AuditPackPanel({ engagements = [] }: { engagements?: EngagementOption[] }) {
  const [from, setFrom] = useState("")
  const [to, setTo] = useState("")
  const [engagementId, setEngagementId] = useState("")
  const [runs, setRuns] = useState<PackRun[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")
  const [results, setResults] = useState<Record<string, PackVerification>>({})

  const load = useCallback(() => { auditPackApi.runs().then(setRuns).catch(() => setRuns([])) }, [])
  useEffect(load, [load])

  const pickEngagement = (id: string) => {
    setEngagementId(id)
    const e = engagements.find(x => x.id === id)
    if (e) { setFrom(e.periodFrom); setTo(e.periodTo) }
  }
  const problem = periodProblem(from, to)

  const build = async () => {
    setBusy(true); setError("")
    try { await auditPackApi.build(from, to, engagementId || undefined); load() }
    catch (e: any) { setError(e?.response?.data?.message ?? "Could not build the pack") }
    finally { setBusy(false) }
  }
  const verify = async (id: string) => {
    setError("")
    try { const v = await auditPackApi.verify(id); setResults(r => ({ ...r, [id]: v })) }
    catch (e: any) { setError(e?.response?.data?.message ?? "Could not check this pack") }
  }

  return (
    <div>
      <div style={{ fontSize: 12, color: FAINT, marginBottom: 10 }}>
        One ZIP of spreadsheets for the period: trial balance with last year beside it, every ledger line, all journals including reversals, bank reconciliations, debtors, fixed assets and VAT. Debtors and fixed assets are as at today.
        Creditors and approval histories are not in it yet.
      </div>
      {error && <div role="alert" style={{ padding: "8px 12px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 6, fontSize: 12.5, color: "var(--hf-danger-text)", marginBottom: 10 }}>{error}</div>}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 10, marginBottom: 10 }}>
        {engagements.length > 0 && (
          <label style={{ fontSize: 12, color: MUTED, gridColumn: "1 / span 3" }}>For an engagement (fills in its period)
            <select aria-label="Pack engagement" value={engagementId} onChange={e => pickEngagement(e.target.value)} style={field}>
              <option value="">Not for a particular engagement</option>{engagements.map(e => <option key={e.id} value={e.id}>{e.name}</option>)}</select></label>
        )}
        <label style={{ fontSize: 12, color: MUTED }}>Pack period from<input aria-label="Pack period from" type="date" value={from} onChange={e => setFrom(e.target.value)} style={field} /></label>
        <label style={{ fontSize: 12, color: MUTED }}>Pack period to<input aria-label="Pack period to" type="date" value={to} onChange={e => setTo(e.target.value)} style={field} /></label>
        <div style={{ display: "flex", alignItems: "flex-end" }}>
          <button disabled={!!problem || busy} onClick={build} style={{ ...btn, background: NAVY, color: "var(--hf-text-on-solid)" }}>{busy ? "Building…" : "Build and download"}</button>
        </div>
      </div>
      {problem && (from || to) && <div style={{ fontSize: 12, color: MUTED, marginBottom: 8 }}>{problem}</div>}

      {runs.length > 0 && (
        <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
          <div style={{ fontWeight: 700, fontSize: 12.5, color: INK }}>Packs built</div>
          {runs.map(r => {
            const v = results[r.id]
            return (
              <div key={r.id} style={{ border: `1px solid ${BORDER}`, borderRadius: 8, padding: "6px 12px", fontSize: 12.5 }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <span>{day(r.from)} to {day(r.to)} · built {day(r.generatedAt)} · {r.fileCount} files · <span title={r.packSha256} style={{ color: FAINT }}>{r.packSha256.slice(0, 10)}</span></span>
                  <button style={btn} onClick={() => verify(r.id)}>Check it still matches</button>
                </div>
                {v && (v.matches
                  ? <div style={{ color: "var(--hf-success-text-strong)", marginTop: 4 }}>Still matches the books.</div>
                  : <div style={{ color: "var(--hf-danger-text)", marginTop: 4 }}>The books have changed since this pack was built. Files that differ: {v.changedFiles.join(", ")}.</div>)}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
