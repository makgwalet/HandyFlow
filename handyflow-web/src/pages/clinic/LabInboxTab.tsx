// src/pages/clinic/LabInboxTab.tsx
// The practice's lab inbox: results waiting to be matched to a patient, typed up from the report, and signed off.
// Reference ranges and critical limits are whatever the lab printed on the report: the system holds none of its own, and
// a number with no range is shown as "not assessed", never as normal. Critical results that nobody has reviewed sit at the top.
import { useEffect, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { useCan } from "./clinicAccess"

interface LabRow {
  id: string; patientId?: string | null; patientNameRaw?: string | null; labReference?: string | null; source: string
  status: "UNREVIEWED" | "REVIEWED" | "FILED" | "REJECTED"; receivedAt: string; parsedMarkersJson?: string | null
  hasCritical?: boolean; hasAbnormal?: boolean
}
interface CriticalItem { id: string; patientName?: string | null; patientNameRaw?: string | null; labReference?: string | null; receivedAt: string; criticalMarkers?: string | null }
interface PatientHit { id: string; fullName: string }

/** One line of the entry form. Numbers stay as text until they are sent. */
export interface MarkerRow { marker: string; value: string; unit: string; refLow: string; refHigh: string; criticalLow: string; criticalHigh: string; flag: string }

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return Array.isArray(p) ? p : (p?.content ?? []) }
const when = (iso: string) => new Date(iso).toLocaleString("en-ZA", { dateStyle: "medium", timeStyle: "short", timeZone: "Africa/Johannesburg" })

export const blankMarker = (): MarkerRow => ({ marker: "", value: "", unit: "", refLow: "", refHigh: "", criticalLow: "", criticalHigh: "", flag: "" })

/** Reads the markers stored on a result back into form rows. Anything unreadable gives no rows. */
export function rowsFromJson(json?: string | null): MarkerRow[] {
  if (!json) return []
  try {
    const list = JSON.parse(json)
    if (!Array.isArray(list)) return []
    const t = (v: unknown) => (v === null || v === undefined ? "" : String(v))
    return list.map((m: any) => ({ marker: t(m.marker), value: t(m.value), unit: t(m.unit), refLow: t(m.refLow), refHigh: t(m.refHigh),
      criticalLow: t(m.criticalLow), criticalHigh: t(m.criticalHigh), flag: m.flag && !["NORMAL", "UNKNOWN", "LOW", "HIGH"].includes(m.flag) ? m.flag : "" }))
  } catch { return [] }
}

const num = (s: string): number | null | "bad" => {
  const t = s.trim().replace(",", ".")
  if (!t) return null
  const n = Number(t)
  return Number.isFinite(n) ? n : "bad"
}

/** Why the form cannot be saved yet, or null. Mirrors the server's checks. */
export function markersProblem(rows: MarkerRow[]): string | null {
  if (rows.length > 100) return "At most 100 markers per result"
  for (let i = 0; i < rows.length; i++) {
    const r = rows[i], at = `Line ${i + 1}: `
    if (!r.marker.trim()) return at + "enter the test name"
    if (!r.value.trim()) return at + `enter the result for ${r.marker.trim()}`
    const [rl, rh, cl, ch] = [num(r.refLow), num(r.refHigh), num(r.criticalLow), num(r.criticalHigh)]
    if ([rl, rh, cl, ch].includes("bad")) return at + "limits must be numbers"
    if (typeof rl === "number" && typeof rh === "number" && rl > rh) return at + "the lower reference limit is above the upper one"
    if (typeof cl === "number" && typeof rl === "number" && cl > rl) return at + "the lower critical limit is above the lower reference limit"
    if (typeof ch === "number" && typeof rh === "number" && ch < rh) return at + "the upper critical limit is below the upper reference limit"
    if (typeof cl === "number" && typeof ch === "number" && cl >= ch) return at + "the critical limits overlap"
  }
  return null
}

export function toMarkerRequest(rows: MarkerRow[]) {
  const n = (s: string) => { const v = num(s); return typeof v === "number" ? v : null }
  return {
    markers: rows.map(r => ({
      marker: r.marker.trim(), value: r.value.trim(), unit: r.unit.trim() || null,
      refLow: n(r.refLow), refHigh: n(r.refHigh), criticalLow: n(r.criticalLow), criticalHigh: n(r.criticalHigh), flag: r.flag || null,
    })),
  }
}

const field = { padding: "6px 8px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)", minWidth: 0 } as const
const small = { padding: "5px 12px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "transparent", color: "var(--hf-text-muted)", fontSize: 12, fontWeight: 600, cursor: "pointer" } as const

function MarkerEditor({ result, onClose }: { result: LabRow; onClose: () => void }) {
  const qc = useQueryClient()
  const [rows, setRows] = useState<MarkerRow[]>(() => { const r = rowsFromJson(result.parsedMarkersJson); return r.length ? r : [blankMarker()] })
  const [error, setError] = useState("")
  const save = useMutation({
    mutationFn: () => apiClient.put(`/api/v1/clinic/lab/results/${result.id}/markers`, toMarkerRequest(rows)),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["lab-inbox"] }); qc.invalidateQueries({ queryKey: ["lab-critical"] }); onClose() },
    onError: (e: any) => setError(e?.response?.data?.message ?? "Could not save the results"),
  })
  const set = (i: number, k: keyof MarkerRow, v: string) => setRows(rs => rs.map((r, j) => (j === i ? { ...r, [k]: v } : r)))
  return (
    <div style={{ marginTop: 12, padding: 12, background: "var(--hf-surface-muted)", borderRadius: 10 }}>
      <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginBottom: 8 }}>
        Copy each line from the lab report, including the lab's own reference range and, if printed, its critical limits. Leave a limit blank if the report has none.
      </div>
      {error && <div role="alert" style={{ marginBottom: 8, fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</div>}
      <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
        {rows.map((r, i) => (
          <div key={i} style={{ display: "grid", gridTemplateColumns: "2fr 1fr 1fr repeat(4, 0.8fr) 1fr auto", gap: 6, alignItems: "center" }}>
            <input aria-label={`Test ${i + 1}`} placeholder="Test" value={r.marker} onChange={e => set(i, "marker", e.target.value)} style={field} />
            <input aria-label={`Result ${i + 1}`} placeholder="Result" value={r.value} onChange={e => set(i, "value", e.target.value)} style={field} />
            <input aria-label={`Unit ${i + 1}`} placeholder="Unit" value={r.unit} onChange={e => set(i, "unit", e.target.value)} style={field} />
            <input aria-label={`Ref low ${i + 1}`} placeholder="Ref low" value={r.refLow} onChange={e => set(i, "refLow", e.target.value)} style={field} />
            <input aria-label={`Ref high ${i + 1}`} placeholder="Ref high" value={r.refHigh} onChange={e => set(i, "refHigh", e.target.value)} style={field} />
            <input aria-label={`Critical low ${i + 1}`} placeholder="Crit low" value={r.criticalLow} onChange={e => set(i, "criticalLow", e.target.value)} style={field} />
            <input aria-label={`Critical high ${i + 1}`} placeholder="Crit high" value={r.criticalHigh} onChange={e => set(i, "criticalHigh", e.target.value)} style={field} />
            <select aria-label={`Flag ${i + 1}`} value={r.flag} onChange={e => set(i, "flag", e.target.value)} style={field}>
              <option value="">Flag: from limits</option>
              <option value="NORMAL">Normal</option>
              <option value="ABNORMAL">Abnormal</option>
              <option value="CRITICAL">Critical</option>
            </select>
            <button type="button" aria-label={`Remove line ${i + 1}`} style={small} onClick={() => setRows(rs => rs.filter((_, j) => j !== i))}>Remove</button>
          </div>
        ))}
      </div>
      <div style={{ display: "flex", gap: 8, marginTop: 10 }}>
        <button type="button" style={small} onClick={() => setRows(rs => [...rs, blankMarker()])}>Add line</button>
        <button type="button" disabled={save.isPending} onClick={() => {
          const p = markersProblem(rows)
          if (p) { setError(p); return }
          setError(""); save.mutate()
        }} style={{ padding: "6px 16px", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer", background: "var(--hf-accent)", color: "var(--hf-text-on-solid)" }}>
          {save.isPending ? "Saving..." : "Save results"}
        </button>
        <button type="button" style={small} onClick={onClose}>Cancel</button>
      </div>
    </div>
  )
}

function MatchBox({ result, onClose }: { result: LabRow; onClose: () => void }) {
  const qc = useQueryClient()
  const [q, setQ] = useState(result.patientNameRaw ?? "")
  const [term, setTerm] = useState(result.patientNameRaw ?? "")
  const [error, setError] = useState("")
  useEffect(() => { const t = setTimeout(() => setTerm(q.trim()), 300); return () => clearTimeout(t) }, [q])
  const hits = useQuery<PatientHit[]>({
    queryKey: ["lab-match-search", term], enabled: term.length >= 2,
    queryFn: async () => unwrap(await apiClient.get("/api/v1/clinic/patients", { params: { search: term, size: 8 } })),
  })
  const match = useMutation({
    mutationFn: (patientId: string) => apiClient.post(`/api/v1/clinic/lab/results/${result.id}/match-patient`, null, { params: { patientId } }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["lab-inbox"] }); qc.invalidateQueries({ queryKey: ["lab-critical"] }); onClose() },
    onError: (e: any) => setError(e?.response?.data?.message ?? "Could not match the patient"),
  })
  return (
    <div style={{ marginTop: 12, padding: 12, background: "var(--hf-surface-muted)", borderRadius: 10 }}>
      <input aria-label="Search patients" placeholder="Search patient by name" value={q} onChange={e => setQ(e.target.value)} style={{ ...field, width: "100%", boxSizing: "border-box" }} />
      {error && <div role="alert" style={{ marginTop: 8, fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</div>}
      <div style={{ marginTop: 8, display: "flex", flexDirection: "column", gap: 6 }}>
        {term.length < 2 ? <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Type at least two letters.</div>
          : hits.isLoading ? <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Searching...</div>
          : (hits.data ?? []).length === 0 ? <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>No patient found.</div>
          : (hits.data ?? []).map(p => (
            <div key={p.id} style={{ display: "flex", alignItems: "center", gap: 10 }}>
              <span style={{ flex: 1, fontSize: 13, color: "var(--hf-text)" }}>{p.fullName}</span>
              <button type="button" style={small} disabled={match.isPending} onClick={() => match.mutate(p.id)}>Match to this patient</button>
            </div>
          ))}
      </div>
      <button type="button" style={{ ...small, marginTop: 10 }} onClick={onClose}>Cancel</button>
    </div>
  )
}

export default function LabInboxTab() {
  const qc = useQueryClient()
  const canWrite = useCan("reviewResult")
  const [status, setStatus] = useState("UNREVIEWED")
  const [open, setOpen] = useState<{ id: string; mode: "markers" | "match" } | null>(null)
  const [error, setError] = useState("")

  const critical = useQuery<CriticalItem[]>({ queryKey: ["lab-critical"], refetchInterval: 60000,
    queryFn: async () => unwrap(await apiClient.get("/api/v1/clinic/lab/critical")) })
  const list = useQuery<LabRow[]>({ queryKey: ["lab-inbox", status],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/clinic/lab/results", { params: status ? { status } : undefined })) })
  const review = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/clinic/lab/results/${id}/review`),
    onSuccess: () => { setError(""); qc.invalidateQueries({ queryKey: ["lab-inbox"] }); qc.invalidateQueries({ queryKey: ["lab-critical"] }) },
    onError: (e: any) => setError(e?.response?.data?.message ?? "Could not mark the result as reviewed"),
  })

  return (
    <div style={{ maxWidth: 900 }}>
      <h2 style={{ margin: "0 0 4px", fontSize: 18, fontWeight: 700, color: "var(--hf-text)" }}>Results inbox</h2>
      <p style={{ margin: "0 0 16px", fontSize: 13, color: "var(--hf-text-muted)" }}>
        This is the clinic-wide worklist: results that arrive from the lab with no patient yet, critical results for every patient, and everything waiting for review. A patient's own results are on their file under Lab results. Match each result to a patient, type up the report using the lab's own ranges, then mark it reviewed. Reviewing a result sends the patient a notice if they have an email address.
      </p>

      {(critical.data ?? []).length > 0 && (
        <div role="alert" style={{ marginBottom: 16, padding: "12px 14px", border: "1px solid var(--hf-danger-border)", background: "var(--hf-danger-soft)", borderRadius: 10 }}>
          <div style={{ fontSize: 13, fontWeight: 800, color: "var(--hf-danger-text)", marginBottom: 6 }}>
            {(critical.data ?? []).length} critical result{(critical.data ?? []).length === 1 ? "" : "s"} waiting for review
          </div>
          {(critical.data ?? []).map(c => (
            <div key={c.id} style={{ fontSize: 13, color: "var(--hf-text)" }}>
              <b>{c.patientName || c.patientNameRaw || "Unmatched patient"}</b> · {c.criticalMarkers || "critical marker"} · {c.labReference || "no reference"} · received {when(c.receivedAt)}
            </div>
          ))}
        </div>
      )}

      {error && <div role="alert" style={{ marginBottom: 12, fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</div>}

      <label style={{ fontSize: 12, fontWeight: 600, color: "var(--hf-text-muted)", display: "flex", flexDirection: "column", gap: 4, maxWidth: 220, marginBottom: 14 }}>
        Show
        <select aria-label="Status" value={status} onChange={e => setStatus(e.target.value)} style={field}>
          <option value="UNREVIEWED">Waiting for review</option>
          <option value="REVIEWED">Reviewed</option>
          <option value="FILED">Filed</option>
          <option value="">All</option>
        </select>
      </label>

      {list.isError ? <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>Could not load the lab results.</div>
        : list.isLoading ? <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Loading...</div>
        : (list.data ?? []).length === 0 ? <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Nothing here.</div>
        : (
          <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
            {(list.data ?? []).map(r => {
              const unreviewed = r.status === "UNREVIEWED"
              const lines = rowsFromJson(r.parsedMarkersJson).length
              return (
                <div key={r.id} style={{ padding: "12px 14px", border: "1px solid var(--hf-border)", borderLeft: `4px solid ${r.hasCritical ? "var(--hf-danger)" : "var(--hf-border)"}`, borderRadius: 10 }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
                    <div style={{ flex: 1, minWidth: 220 }}>
                      <div style={{ fontSize: 14, fontWeight: 700, color: "var(--hf-text)" }}>
                        {r.labReference || "No lab reference"} <span style={{ fontWeight: 400, color: "var(--hf-text-muted)" }}>· {r.source}</span>
                      </div>
                      <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
                        {r.patientId ? "Matched to a patient" : `Not matched${r.patientNameRaw ? ` (report says "${r.patientNameRaw}")` : ""}`} · received {when(r.receivedAt)} · {lines} line{lines === 1 ? "" : "s"} entered
                      </div>
                    </div>
                    {r.hasCritical && <span style={{ background: "var(--hf-danger)", color: "var(--hf-text-on-solid)", padding: "2px 8px", borderRadius: 20, fontSize: 11, fontWeight: 800 }}>CRITICAL</span>}
                    {!r.hasCritical && r.hasAbnormal && <span style={{ background: "var(--hf-warning-soft)", color: "var(--hf-warning-text)", padding: "2px 8px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>Abnormal</span>}
                    <span style={{ fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)" }}>{r.status}</span>
                    {canWrite && unreviewed && (
                      <>
                        <button type="button" style={small} onClick={() => setOpen({ id: r.id, mode: "match" })}>{r.patientId ? "Change patient" : "Match patient"}</button>
                        <button type="button" style={small} onClick={() => setOpen({ id: r.id, mode: "markers" })}>{lines ? "Edit results" : "Enter results"}</button>
                        <button type="button" style={{ ...small, color: "var(--hf-accent-text)" }} disabled={review.isPending || !r.patientId}
                          title={r.patientId ? undefined : "Match a patient first"} onClick={() => review.mutate(r.id)}>Mark reviewed</button>
                      </>
                    )}
                  </div>
                  {open?.id === r.id && open.mode === "match" && <MatchBox result={r} onClose={() => setOpen(null)} />}
                  {open?.id === r.id && open.mode === "markers" && <MarkerEditor result={r} onClose={() => setOpen(null)} />}
                </div>
              )
            })}
          </div>
        )}
    </div>
  )
}
