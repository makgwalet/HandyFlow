// Follow-up audits: check what became of the findings of an earlier engagement (audit backlog A20).
import { useEffect, useState } from "react"
import { auditFollowUpApi, type FuSummary, type FuView } from "../../api/auditFollowUp.api"
import { Card, Tile, TileRow } from "../../components/DashboardBits"

const MUTED = "var(--hf-text-muted)"
const BORDER = "var(--hf-border)"
const MIN = 10
const msg = (e: any, f: string) => e?.response?.data?.message ?? f
const btn: React.CSSProperties = { padding: "6px 12px", fontSize: 12, fontWeight: 700, cursor: "pointer", borderRadius: 6, border: `1px solid ${BORDER}`, background: "var(--hf-surface)" }
const area: React.CSSProperties = { width: "100%", minHeight: 48, padding: 8, border: `1.5px solid ${BORDER}`, borderRadius: 6, fontSize: 13, fontFamily: "inherit", boxSizing: "border-box" }
const OUTCOME: Record<string, string> = { IMPLEMENTED: "Implemented", PARTIALLY_IMPLEMENTED: "Partially implemented", NOT_IMPLEMENTED: "Not implemented", NO_LONGER_APPLICABLE: "No longer applicable" }

export function FollowUpSection() {
  const [list, setList] = useState<FuSummary[]>([])
  const [engagements, setEngagements] = useState<{ id: string; name: string }[]>([])
  const [engagementId, setEngagementId] = useState("")
  const [priorId, setPriorId] = useState("")
  const [openId, setOpenId] = useState<string | null>(null)
  const [error, setError] = useState("")

  const load = () => auditFollowUpApi.list().then(setList).catch(e => { setList([]); setError(msg(e, "Failed to load the follow-up audits")) })
  useEffect(() => { load(); auditFollowUpApi.engagements().then(setEngagements).catch(() => setEngagements([])) }, [])

  const start = async () => {
    setError("")
    try { const v = await auditFollowUpApi.start(engagementId, priorId); load(); setOpenId(v.summary.engagementId) }
    catch (e: any) { setError(msg(e, "Failed to start the follow-up")) }
  }

  if (openId) return <FollowUpDetail engagementId={openId} onBack={() => { setOpenId(null); load() }} />
  const linked = new Set(list.map(l => l.engagementId))
  return (
    <div>
      {error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13, marginBottom: 8 }}>{error}</div>}
      {engagements.length > 1 && (
        <Card title="Start a follow-up">
          <div style={{ fontSize: 12.5, color: MUTED, marginBottom: 8 }}>Pick the new engagement and the earlier one whose findings it checks. Each earlier finding gets an outcome.</div>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <select aria-label="Follow-up engagement" value={engagementId} onChange={e => setEngagementId(e.target.value)}>
              <option value="">This engagement…</option>{engagements.filter(g => !linked.has(g.id)).map(g => <option key={g.id} value={g.id}>{g.name}</option>)}
            </select>
            <select aria-label="Earlier engagement" value={priorId} onChange={e => setPriorId(e.target.value)}>
              <option value="">Follows up…</option>{engagements.filter(g => g.id !== engagementId).map(g => <option key={g.id} value={g.id}>{g.name}</option>)}
            </select>
            <button style={btn} disabled={!engagementId || !priorId} onClick={start}>Start follow-up</button>
          </div>
        </Card>
      )}
      <Card title="Follow-up audits">
        {list.length === 0 ? <span style={{ fontSize: 13, color: MUTED }}>No follow-up audits yet.</span> : list.map(s => (
          <div key={s.engagementId} style={{ display: "flex", justifyContent: "space-between", padding: "6px 0", borderBottom: `1px solid ${BORDER}`, fontSize: 13 }}>
            <span>{s.engagementName} <span style={{ color: MUTED }}>· follows {s.priorEngagementName} · {s.counts.implementedPct}% implemented · {s.counts.assessed}/{s.counts.total} assessed</span></span>
            <button style={btn} onClick={() => setOpenId(s.engagementId)}>Open</button>
          </div>
        ))}
      </Card>
    </div>
  )
}

function FollowUpDetail({ engagementId, onBack }: { engagementId: string; onBack: () => void }) {
  const [v, setV] = useState<FuView | null>(null)
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const [draft, setDraft] = useState<Record<string, { outcome: string; note: string }>>({})

  useEffect(() => { auditFollowUpApi.get(engagementId).then(setV).catch(e => setError(msg(e, "Failed to load the follow-up"))) }, [engagementId])
  if (!v) return error ? <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>{error}</div> : <div style={{ color: MUTED, fontSize: 13 }}>Loading…</div>

  const c = v.summary.counts
  const save = async (findingId: string) => {
    const d = draft[findingId]
    setBusy(true); setError("")
    try { setV(await auditFollowUpApi.assess(engagementId, findingId, d.outcome, d.note)); setDraft(p => { const n = { ...p }; delete n[findingId]; return n }) }
    catch (e: any) { setError(msg(e, "Failed to save the outcome")) } finally { setBusy(false) }
  }
  return (
    <div>
      <button style={btn} onClick={onBack}>← Back</button>
      <h2 style={{ fontSize: 16, margin: "12px 0 2px" }}>{v.summary.engagementName}</h2>
      <div style={{ fontSize: 12.5, color: MUTED, marginBottom: 12 }}>Follows up {v.summary.priorEngagementName}</div>
      {error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13, marginBottom: 8 }}>{error}</div>}
      <TileRow>
        <Tile label="Implemented" value={`${c.implementedPct}%`} sub={`${c.implemented} of ${c.total - c.noLongerApplicable} applicable`} />
        <Tile label="Partially implemented" value={c.partial} />
        <Tile label="Not implemented" value={c.notImplemented} tone={c.notImplemented ? "warning" : undefined} />
        <Tile label="Assessed" value={`${c.assessed}/${c.total}`} />
      </TileRow>
      <Card title="Earlier findings">
        {v.items.map(i => {
          const d = draft[i.findingId]
          const edit = (patch: Partial<{ outcome: string; note: string }>) => setDraft(p => ({ ...p, [i.findingId]: { ...({ outcome: i.outcome ?? "IMPLEMENTED", note: i.note ?? "" } as { outcome: string; note: string }), ...p[i.findingId], ...patch } }))
          return (
            <div key={i.findingId} style={{ padding: "8px 0", borderBottom: `1px solid ${BORDER}`, fontSize: 13 }}>
              <div><strong>{i.title}</strong> <span style={{ color: MUTED }}>· {i.severity ?? "unrated"} · {i.outcome ? OUTCOME[i.outcome] : "Not yet assessed"}{i.assessedByName ? ` · ${i.assessedByName}` : ""}</span></div>
              {i.note && !d && <div style={{ marginTop: 4 }}>{i.note}</div>}
              {d ? (
                <div style={{ marginTop: 6, display: "grid", gap: 6 }}>
                  <select aria-label={`Outcome for ${i.title}`} value={d.outcome} onChange={e => edit({ outcome: e.target.value })}>
                    {Object.entries(OUTCOME).map(([k, l]) => <option key={k} value={k}>{l}</option>)}
                  </select>
                  <textarea aria-label={`What was checked for ${i.title}`} style={area} value={d.note} onChange={e => edit({ note: e.target.value })} placeholder="What did you check?" />
                  <div><button style={btn} disabled={busy || d.note.trim().length < MIN} onClick={() => save(i.findingId)}>Save outcome</button></div>
                </div>
              ) : <div style={{ marginTop: 4 }}><button style={btn} onClick={() => edit({})}>{i.outcome ? "Revise" : "Assess"}</button></div>}
            </div>
          )
        })}
      </Card>
    </div>
  )
}
