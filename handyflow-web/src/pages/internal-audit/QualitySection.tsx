// Quality assurance: independent engagement quality reviews against the methodology checklist, and quality metrics (audit backlog A19).
import { useEffect, useState } from "react"
import { auditQualityApi, type QrSummary, type QrView, type QualityMetrics } from "../../api/auditQuality.api"
import { Card, Tile, TileRow } from "../../components/DashboardBits"
import { useAuthStore } from "../../store/auth.store"

const MUTED = "var(--hf-text-muted)"
const BORDER = "var(--hf-border)"
const MIN = 10
const msg = (e: any, f: string) => e?.response?.data?.message ?? f
const btn: React.CSSProperties = { padding: "6px 12px", fontSize: 12, fontWeight: 700, cursor: "pointer", borderRadius: 6, border: `1px solid ${BORDER}`, background: "var(--hf-surface)" }
const area: React.CSSProperties = { width: "100%", minHeight: 48, padding: 8, border: `1.5px solid ${BORDER}`, borderRadius: 6, fontSize: 13, fontFamily: "inherit", boxSizing: "border-box" }
const STATUS: Record<string, string> = { REQUESTED: "Requested", IN_PROGRESS: "In progress", COMPLETED: "Completed", CANCELLED: "Cancelled" }
const CONCLUSION: Record<string, string> = { SATISFACTORY: "Satisfactory", NEEDS_IMPROVEMENT: "Needs improvement", UNSATISFACTORY: "Unsatisfactory" }

export function QualitySection() {
  const [metrics, setMetrics] = useState<QualityMetrics | null>(null)
  const [list, setList] = useState<QrSummary[]>([])
  const [openId, setOpenId] = useState<string | null>(null)
  const [engagements, setEngagements] = useState<{ id: string; name: string }[]>([])
  const [users, setUsers] = useState<{ id: string; name: string }[]>([])
  const [engagementId, setEngagementId] = useState("")
  const [reviewerId, setReviewerId] = useState("")
  const [error, setError] = useState("")

  const load = () => {
    auditQualityApi.metrics().then(setMetrics).catch(e => setError(msg(e, "Failed to load the quality figures")))
    auditQualityApi.list().then(setList).catch(() => { setList([]); setError("Could not load the list of quality reviews. Refresh to try again.") })
  }
  useEffect(() => {
    load()
    auditQualityApi.engagements().then(setEngagements).catch(() => { setEngagements([]); setError("Could not load the list of engagements. Refresh to try again.") })
    auditQualityApi.users().then(setUsers).catch(() => { setUsers([]); setError("Could not load the list of people. Refresh to try again.") })
  }, [])

  const request = async () => {
    setError("")
    try { const v = await auditQualityApi.request(engagementId, reviewerId); load(); setOpenId(v.summary.id) }
    catch (e: any) { setError(msg(e, "Failed to request the review")) }
  }

  if (openId) return <ReviewDetail id={openId} onBack={() => { setOpenId(null); load() }} />
  const m = metrics
  return (
    <div>
      {error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13, marginBottom: 8 }}>{error}</div>}
      {m && (
        <>
          <TileRow>
            <Tile label="Reviews completed" value={m.reviewsCompleted} sub={m.reviewsLive ? `${m.reviewsLive} in progress` : undefined} />
            <Tile label="Satisfactory" value={`${m.satisfactoryPct}%`} />
            <Tile label="Average days to complete" value={m.averageDaysToComplete ?? "—"} />
            <Tile label="Issued without a completed review" value={m.issuedWithoutCompletedReview} tone={m.issuedWithoutCompletedReview ? "warning" : undefined} />
            <Tile label="Review notes cleared" value={`${m.reviewNotesClearedPct}%`} sub={`${m.reviewNotesCleared} of ${m.reviewNotesRaised}`} />
            <Tile label="Findings reopened" value={`${m.findingsReopenedPct}%`} sub={`${m.findingsReopened} of ${m.findingsTotal}`} />
          </TileRow>
          <Card title="Checks most often failed">
            {m.mostFailedChecks.length === 0 ? <span style={{ fontSize: 13, color: MUTED }}>No failed checks yet.</span> : (
              <ul style={{ margin: 0, paddingLeft: 18, fontSize: 13 }}>{m.mostFailedChecks.map(c => <li key={c.code}>{c.code}: {c.question} — {c.count}×</li>)}</ul>
            )}
          </Card>
        </>
      )}
      {engagements.length > 0 && (
        <Card title="Request a quality review">
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <select aria-label="Engagement to review" value={engagementId} onChange={e => setEngagementId(e.target.value)}>
              <option value="">Choose an engagement…</option>{engagements.map(g => <option key={g.id} value={g.id}>{g.name}</option>)}
            </select>
            <select aria-label="Reviewer" value={reviewerId} onChange={e => setReviewerId(e.target.value)}>
              <option value="">Choose an independent reviewer…</option>{users.map(u => <option key={u.id} value={u.id}>{u.name}</option>)}
            </select>
            <button style={btn} disabled={!engagementId || !reviewerId} onClick={request}>Request review</button>
          </div>
        </Card>
      )}
      <Card title="Quality reviews">
        {list.length === 0 ? <span style={{ fontSize: 13, color: MUTED }}>No reviews yet.</span> : list.map(r => (
          <div key={r.id} style={{ display: "flex", justifyContent: "space-between", padding: "6px 0", borderBottom: `1px solid ${BORDER}`, fontSize: 13 }}>
            <span>{r.engagementName} <span style={{ color: MUTED }}>· {r.reviewerName} · {STATUS[r.status] ?? r.status}{r.conclusion ? ` · ${CONCLUSION[r.conclusion]}` : ` · ${r.answered}/${r.total} answered`}</span></span>
            <button style={btn} onClick={() => setOpenId(r.id)}>Open</button>
          </div>
        ))}
      </Card>
    </div>
  )
}

function ReviewDetail({ id, onBack }: { id: string; onBack: () => void }) {
  const me = useAuthStore(s => s.user?.userId)
  const [v, setV] = useState<QrView | null>(null)
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const [notes, setNotes] = useState<Record<string, string>>({})
  const [conclusion, setConclusion] = useState("SATISFACTORY")
  const [note, setNote] = useState("")
  const [reason, setReason] = useState("")

  useEffect(() => { auditQualityApi.get(id).then(setV).catch(e => setError(msg(e, "Failed to load the review"))) }, [id])
  const run = async (fn: () => Promise<QrView>, fallback: string) => {
    setBusy(true); setError("")
    try { setV(await fn()) } catch (e: any) { setError(msg(e, fallback)) } finally { setBusy(false) }
  }
  if (!v) return error ? <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>{error}</div> : <div style={{ color: MUTED, fontSize: 13 }}>Loading…</div>

  const s = v.summary
  const live = s.status === "REQUESTED" || s.status === "IN_PROGRESS"
  const isReviewer = live && me === s.reviewerId
  const areas = Array.from(new Set(v.checks.map(c => c.area)))
  return (
    <div>
      <button style={btn} onClick={onBack}>← Back</button>
      <h2 style={{ fontSize: 16, margin: "12px 0 2px" }}>{s.engagementName}</h2>
      <div style={{ fontSize: 12.5, color: MUTED, marginBottom: 12 }}>Reviewer {s.reviewerName} · requested by {s.requestedByName} · {STATUS[s.status] ?? s.status}{s.conclusion ? ` · ${CONCLUSION[s.conclusion]}` : ""}</div>
      {error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13, marginBottom: 8 }}>{error}</div>}
      {v.conclusionNote && <Card title="Conclusion"><div style={{ fontSize: 13 }}>{v.conclusionNote}</div></Card>}
      {v.cancelReason && <Card title="Cancelled"><div style={{ fontSize: 13 }}>{v.cancelReason}</div></Card>}
      {areas.map(area0 => (
        <Card key={area0} title={area0.charAt(0) + area0.slice(1).toLowerCase()}>
          {v.checks.filter(c => c.area === area0).map(c => (
            <div key={c.code} style={{ padding: "6px 0", borderBottom: `1px solid ${BORDER}`, fontSize: 13 }}>
              <div>{c.code}. {c.question} {c.result && <strong>— {c.result === "NA" ? "Not applicable" : c.result === "YES" ? "Yes" : "No"}</strong>}</div>
              {c.note && <div style={{ color: MUTED }}>{c.note}</div>}
              {isReviewer && (
                <div style={{ marginTop: 4 }}>
                  <textarea aria-label={`Note for ${c.code}`} style={area} placeholder="Required for No and Not applicable (at least 10 characters)" value={notes[c.code] ?? ""} onChange={e => setNotes({ ...notes, [c.code]: e.target.value })} />
                  {(["YES", "NO", "NA"] as const).map(r => (
                    <button key={r} style={{ ...btn, marginRight: 6, marginTop: 4 }} disabled={busy || (r !== "YES" && (notes[c.code] ?? "").trim().length < MIN)}
                      onClick={() => run(() => auditQualityApi.answer(id, c.code, r, notes[c.code] ?? ""), "Failed to save the answer")}>{c.code} {r === "NA" ? "N/A" : r === "YES" ? "Yes" : "No"}</button>
                  ))}
                </div>
              )}
            </div>
          ))}
        </Card>
      ))}
      {isReviewer && (
        <Card title="Conclude the review">
          <select aria-label="Conclusion" value={conclusion} onChange={e => setConclusion(e.target.value)}>
            {Object.entries(CONCLUSION).map(([k, l]) => <option key={k} value={k}>{l}</option>)}
          </select>
          <textarea aria-label="Conclusion note" style={{ ...area, marginTop: 6 }} placeholder="Why? (at least 10 characters)" value={note} onChange={e => setNote(e.target.value)} />
          <button style={{ ...btn, marginTop: 6 }} disabled={busy || note.trim().length < MIN} onClick={() => run(() => auditQualityApi.complete(id, conclusion, note), "Failed to conclude")}>Complete review</button>
        </Card>
      )}
      {live && (
        <Card title="Cancel this review">
          <textarea aria-label="Reason for cancelling" style={area} placeholder="Reason (at least 10 characters)" value={reason} onChange={e => setReason(e.target.value)} />
          <button style={{ ...btn, marginTop: 6 }} disabled={busy || reason.trim().length < MIN} onClick={() => run(() => auditQualityApi.cancel(id, reason), "Failed to cancel")}>Cancel review</button>
        </Card>
      )}
    </div>
  )
}
