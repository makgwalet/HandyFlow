// src/pages/clinic/ObservationMatrix.tsx
// Recorded measurements as a grid: one row per measurement, one column per day (newest on the left), with a small
// trend line. It shows what was recorded and nothing more: an "abnormal" mark appears only when the server
// flagged the value against a reference range that was supplied with it. No thresholds are built in here.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"

export interface Observation {
  id: string; code: string; label?: string; value: number | string; unit?: string
  abnormalFlag?: string | null; takenAt: string; status?: string
}
export interface MatrixRow {
  code: string; label: string; unit: string
  cells: (Observation | null)[]        // aligned with `days`
  trend: number[]                      // oldest to newest, across the shown days
}
export interface Matrix { days: string[]; rows: MatrixRow[] }

const unwrap = (r: any) => r.data?.data ?? r.data
const num = (v: number | string) => Number(v)

/** The last `maxDays` days that have any reading, newest first; per day and code the latest reading wins. */
export function buildMatrix(all: Observation[], maxDays = 6): Matrix {
  const obs = all.filter(o => o.status !== "VOIDED" && Number.isFinite(num(o.value)) && o.takenAt)
  const days = [...new Set(obs.map(o => o.takenAt.slice(0, 10)))].sort().reverse().slice(0, maxDays)
  const byCode = new Map<string, Observation[]>()
  for (const o of obs) if (days.includes(o.takenAt.slice(0, 10))) byCode.set(o.code, [...(byCode.get(o.code) ?? []), o])
  const rows: MatrixRow[] = [...byCode.entries()].map(([code, list]) => {
    const cells = days.map(d => {
      const ofDay = list.filter(o => o.takenAt.slice(0, 10) === d).sort((a, b) => b.takenAt.localeCompare(a.takenAt))
      return ofDay[0] ?? null
    })
    const first = list[0]
    return {
      code, label: first.label || code, unit: first.unit ?? "", cells,
      trend: [...cells].reverse().filter((c): c is Observation => !!c).map(c => num(c.value)),
    }
  }).sort((a, b) => a.label.localeCompare(b.label))
  return { days, rows }
}

function Spark({ values }: { values: number[] }) {
  if (values.length < 2) return null
  const w = 60, h = 18, min = Math.min(...values), max = Math.max(...values), span = max - min || 1
  const pts = values.map((v, i) => `${(i / (values.length - 1)) * w},${h - 2 - ((v - min) / span) * (h - 4)}`).join(" ")
  return <svg width={w} height={h} aria-hidden="true"><polyline points={pts} fill="none" stroke="var(--hf-accent-text)" strokeWidth="1.5" /></svg>
}

const dayLabel = (d: string) => new Date(d + "T12:00:00").toLocaleDateString("en-ZA", { day: "numeric", month: "short" })
const cell: React.CSSProperties = { padding: "4px 8px", fontSize: 12, textAlign: "right", whiteSpace: "nowrap" }

export default function ObservationMatrix({ patientId }: { patientId: string }) {
  const { data, isLoading, isError } = useQuery<Observation[]>({
    queryKey: ["clinic-observations", patientId],
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/clinic/patients/${patientId}/observations`)) ?? [],
  })
  const head = <div style={{ fontSize: 10, fontWeight: 700, color: "var(--hf-text-faint)", letterSpacing: "0.06em", marginBottom: 6 }}>MEASUREMENTS</div>
  if (isLoading) return null
  if (isError) return <div style={{ marginTop: 16 }}>{head}<div style={{ fontSize: 12, color: "var(--hf-danger-text)" }}>Measurements could not be loaded.</div></div>
  const m = buildMatrix(data ?? [])
  if (m.rows.length === 0) return <div style={{ marginTop: 16 }}>{head}<div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No measurements recorded yet.</div></div>
  return (
    <div style={{ marginTop: 16, overflowX: "auto" }}>
      {head}
      <table style={{ borderCollapse: "collapse", width: "100%" }}>
        <thead>
          <tr>
            <th style={{ ...cell, textAlign: "left" }} />
            {m.days.map(d => <th key={d} style={{ ...cell, fontWeight: 600, color: "var(--hf-text-muted)" }}>{dayLabel(d)}</th>)}
            <th style={cell} />
          </tr>
        </thead>
        <tbody>
          {m.rows.map(r => (
            <tr key={r.code} style={{ borderTop: "1px solid var(--hf-border)" }}>
              <th scope="row" style={{ ...cell, textAlign: "left", fontWeight: 600 }}>{r.label}{r.unit ? <span style={{ color: "var(--hf-text-muted)", fontWeight: 400 }}> ({r.unit})</span> : null}</th>
              {r.cells.map((c, i) => (
                <td key={i} style={{ ...cell, color: c?.abnormalFlag ? "var(--hf-danger-text)" : "var(--hf-text)", fontWeight: c?.abnormalFlag ? 700 : 400 }}
                  title={c?.abnormalFlag ? `Flagged ${String(c.abnormalFlag).toLowerCase()} against the reference range given` : undefined}>
                  {c ? String(c.value) : "·"}
                </td>
              ))}
              <td style={cell}><Spark values={r.trend} /></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
