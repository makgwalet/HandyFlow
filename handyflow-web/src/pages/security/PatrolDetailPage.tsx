// src/pages/security/PatrolDetailPage.tsx
//
// One patrol round (/security/patrols/:id): when it was due, what was scanned and by whom, what is still to scan, any
// off-schedule warning, and a supervisor's acknowledgement of a missed or partial round. Acknowledging records that it was
// seen; it does not change the round's status.
import { useState } from "react"
import { Link, useParams } from "react-router-dom"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Footprints } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import Chip from "../../components/ui/Chip"
import { PageHeader } from "../../components/ui/PageHeader"
import { clock } from "./schedule.logic"
import { METHOD_LABEL, STATUS_LABEL, STATUS_TONE, canAcknowledge, progress, unscanned, type PatrolDetail } from "./patrol.logic"

const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 16 }
const h: React.CSSProperties = { fontSize: 11, color: "var(--hf-text-muted)", fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.4, marginBottom: 8 }
const muted: React.CSSProperties = { fontSize: 13, color: "var(--hf-text-muted)" }
const fmtT = (d: string) => new Date(d).toLocaleString("en-ZA", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit", timeZone: "Africa/Johannesburg" })
const errText = (e: any) => e?.response?.data?.message ?? "That did not work. Please try again."

export default function PatrolDetailPage() {
  const { id = "" } = useParams()
  const qc = useQueryClient()
  const canManage = usePermission("SECURITY_MANAGE")
  const [note, setNote] = useState("")
  const [err, setErr] = useState("")
  const { data, isLoading, error } = useQuery<PatrolDetail>({
    queryKey: ["patrol", id],
    queryFn: async () => { const r = await apiClient.get(`/api/v1/security/patrols/${id}`); return r.data?.data ?? r.data },
  })
  const ack = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/security/patrols/${id}/acknowledge`, { note: note.trim() }),
    onSuccess: () => { setNote(""); setErr(""); qc.invalidateQueries({ queryKey: ["patrol", id] }); qc.invalidateQueries({ queryKey: ["patrols"] }) },
    onError: e => setErr(errText(e)),
  })
  const crumbs = (t: string) => [{ label: "Security", to: "/security/dashboard" }, { label: "Patrols", to: "/security/patrols" }, { label: t }]

  if (isLoading) return <div><PageHeader title="Patrol round" icon={Footprints} breadcrumbs={crumbs("Round")} /><div style={muted}>Loading…</div></div>
  if (error || !data) return <div><PageHeader title="Patrol round" icon={Footprints} breadcrumbs={crumbs("Round")} /><div role="alert" style={{ color: "var(--hf-danger-text)" }}>This round could not be loaded.</div></div>

  const { round: r, checkpoints, acknowledgementNote } = data
  const title = `${r.siteName ?? "Site"}: round ${r.roundNumber}`
  const left = unscanned(checkpoints)
  const submit = () => { if (!note.trim()) setErr("Write a note about what happened."); else { setErr(""); ack.mutate() } }

  return (
    <div>
      <PageHeader title={title} icon={Footprints} breadcrumbs={crumbs(title)} />

      <div style={card}>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center", marginBottom: 8 }}>
          <Chip tone={STATUS_TONE[r.status] ?? "neutral"}>{STATUS_LABEL[r.status] ?? r.status}</Chip>
          {r.offSchedule && <Chip tone="warn">Off schedule</Chip>}
          {r.acknowledged && <Chip tone="neutral">Acknowledged</Chip>}
          <span style={muted}>{r.checkpointsScanned} of {r.checkpointsExpected} checkpoints ({progress(r)}%)</span>
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(190px, 1fr))", gap: 12, fontSize: 13 }}>
          <div><div style={h}>Route</div>{r.routeName ?? "No route"}</div>
          <div><div style={h}>Guard</div>{r.guardName ?? "-"}</div>
          <div><div style={h}>Due</div>{r.expectedStartAt ? `${fmtT(r.expectedStartAt)}${r.expectedEndAt ? ` to ${clock(r.expectedEndAt)}` : ""}` : "-"}</div>
          <div><div style={h}>Started</div>{r.startedAt ? fmtT(r.startedAt) : "No scan yet"}</div>
          <div><div style={h}>Finished</div>{r.completedAt ? fmtT(r.completedAt) : "-"}</div>
        </div>
        {r.offSchedule && r.offScheduleReason && <div style={{ marginTop: 10, color: "var(--hf-warning-text)", fontSize: 13 }}>{r.offScheduleReason}</div>}
        <div style={{ marginTop: 10, fontSize: 13 }}><Link to={`/security/sites/${r.siteId}`} style={{ color: "var(--hf-primary-text)", fontWeight: 600, textDecoration: "none" }}>Open site</Link></div>
      </div>

      <div style={card}>
        <div style={h}>Checkpoints on this route{left.length > 0 && r.status !== "EXPECTED" ? ` · ${left.length} not scanned` : ""}</div>
        {checkpoints.length === 0 ? <div style={muted}>This round has no route checkpoints on record.</div> : checkpoints.map(c => (
          <div key={c.id} style={{ display: "flex", justifyContent: "space-between", gap: 10, padding: "8px 0", borderTop: "1px solid var(--hf-border)", fontSize: 13 }}>
            <div><span style={{ color: "var(--hf-text-muted)" }}>{c.sequence}. </span>{c.name}
              {c.scannedAt && <div style={muted}>{fmtT(c.scannedAt)}{c.scannedBy ? ` · ${c.scannedBy}` : ""}{c.method ? ` · ${METHOD_LABEL[c.method] ?? c.method}` : ""}</div>}</div>
            <Chip tone={c.scannedAt ? "ok" : r.status === "MISSED" || r.status === "PARTIAL" ? "bad" : "neutral"}>{c.scannedAt ? "Scanned" : "Not scanned"}</Chip>
          </div>
        ))}
      </div>

      {(acknowledgementNote || canAcknowledge(r)) && (
        <div style={card}>
          <div style={h}>Supervisor acknowledgement</div>
          {acknowledgementNote && <div style={{ fontSize: 13 }}>{acknowledgementNote}</div>}
          {canAcknowledge(r) && canManage && (
            <div style={{ display: "grid", gap: 8, maxWidth: 520 }}>
              <label style={{ fontSize: 12 }}>What happened (for the record)
                <textarea value={note} onChange={e => setNote(e.target.value)} rows={3} style={{ width: "100%", boxSizing: "border-box", padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text-primary)" }} /></label>
              {err && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>{err}</div>}
              <div><button disabled={ack.isPending} onClick={submit} style={{ padding: "8px 12px", border: "none", borderRadius: 9, background: "var(--hf-accent)", color: "var(--hf-text-on-solid)", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>{ack.isPending ? "Saving…" : "Acknowledge"}</button></div>
              <div style={muted}>This records that the round was seen. It does not change its status.</div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
