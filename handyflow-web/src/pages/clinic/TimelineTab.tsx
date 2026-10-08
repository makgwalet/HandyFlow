// src/pages/clinic/TimelineTab.tsx
// One newest-first record of everything that happened to a patient. Claims and payments only
// arrive from the server when the user may see billing, so there is nothing to hide here.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"

export interface TimelineEvent { kind: string; id: string; at: string; title: string; detail?: string | null; status?: string | null; summary?: string[]; people?: string[] }

export const KIND_LABEL: Record<string, string> = {
  APPOINTMENT: "Appointments", CONSULTATION: "Consultations", PRESCRIPTION: "Prescriptions",
  LAB: "Lab results", CLAIM: "Claims", PAYMENT: "Payments",
}
/** One colour per kind of event, so the line reads at a glance. The label beside it always names the kind: colour is never the only cue. */
export const KIND_COLOR: Record<string, string> = {
  APPOINTMENT: "var(--hf-info-text)", CONSULTATION: "var(--hf-success-text)", PRESCRIPTION: "var(--hf-violet-text)",
  LAB: "var(--hf-warning-text)", CLAIM: "var(--hf-accent-text)", PAYMENT: "var(--hf-primary-text)",
}
const colorOf = (kind: string) => KIND_COLOR[kind] ?? "var(--hf-text-muted)"

const CLINICAL = ["APPOINTMENT", "CONSULTATION", "PRESCRIPTION", "LAB"]
const BILLING = ["CLAIM", "PAYMENT"]

const unwrap = (r: any) => r.data?.data ?? r.data
const dayKey = (iso: string) => iso.slice(0, 10)
const dayLabel = (iso: string) =>
  new Date(iso).toLocaleDateString("en-ZA", { weekday: "short", day: "numeric", month: "short", year: "numeric" })
const timeLabel = (iso: string) => new Date(iso).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" })
const pretty = (s: string) => s.replace(/_/g, " ").toLowerCase()

export function groupByDay(events: TimelineEvent[]): { day: string; events: TimelineEvent[] }[] {
  const out: { day: string; events: TimelineEvent[] }[] = []
  for (const e of events) {
    const k = dayKey(e.at)
    const last = out[out.length - 1]
    if (last && last.day === k) last.events.push(e)
    else out.push({ day: k, events: [e] })
  }
  return out
}

export default function TimelineTab({ patientId }: { patientId: string }) {
  const [hidden, setHidden] = useState<string[]>([])
  const { data, isLoading, isError } = useQuery<TimelineEvent[]>({
    queryKey: ["clinic-timeline", patientId],
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/clinic/patients/${patientId}/timeline`)) ?? [],
  })

  if (isLoading) return <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Loading timeline…</div>
  if (isError) return <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>The timeline could not be loaded. Try again shortly.</div>

  const all = data ?? []
  const present = [...CLINICAL, ...BILLING].filter(k => all.some(e => e.kind === k))
  const shown = all.filter(e => !hidden.includes(e.kind))
  const toggle = (k: string) => setHidden(h => h.includes(k) ? h.filter(x => x !== k) : [...h, k])

  if (all.length === 0) return <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Nothing on record for this patient yet.</div>

  return (
    <div>
      <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginBottom: 14 }}>
        {present.map(k => {
          const on = !hidden.includes(k)
          return (
            <button key={k} type="button" aria-pressed={on} onClick={() => toggle(k)}
              style={{ padding: "4px 10px", borderRadius: 20, fontSize: 12, fontWeight: 600, cursor: "pointer",
                border: "1px solid var(--hf-border)", background: on ? "var(--hf-accent-soft)" : "transparent",
                color: on ? "var(--hf-accent-text)" : "var(--hf-text-muted)" }}>
              {KIND_LABEL[k] ?? k}
            </button>
          )
        })}
      </div>
      {shown.length === 0
        ? <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Every kind of event is hidden. Turn one back on above.</div>
        : (
          <ol aria-label="Patient timeline" style={{ listStyle: "none", margin: 0, padding: "22px 20px 22px 22px", position: "relative",
            border: "1px solid var(--hf-border)", borderRadius: 12, background: "linear-gradient(180deg, var(--hf-surface) 55%, var(--hf-accent-soft))" }}>
            <span aria-hidden="true" style={{ position: "absolute", left: 32, top: 34, bottom: 34, width: 2, background: "var(--hf-border-strong, var(--hf-text-faint))", opacity: 0.6 }} />
            {groupByDay(shown).flatMap(g => g.events.map((e, i) => ({ e, first: i === 0 }))).map(({ e, first }) => {
              const c = colorOf(e.kind)
              return (
                <li key={`${e.kind}-${e.id}`} style={{ position: "relative", paddingLeft: 48, paddingBottom: 26 }}>
                  <span aria-hidden="true" style={{ position: "absolute", left: 0, top: 2, width: 22, height: 22, boxSizing: "border-box", borderRadius: "50%",
                    border: `4px solid ${c}`, background: "var(--hf-surface)" }} />
                  <div style={{ display: "flex", alignItems: "baseline", justifyContent: "space-between", gap: 10 }}>
                    <div style={{ fontSize: first ? 24 : 20, fontWeight: 800, lineHeight: 1.1, color: c }}>{first ? dayLabel(e.at) : timeLabel(e.at)}</div>
                    {e.status && (
                      <span style={{ fontSize: 10, fontWeight: 700, padding: "1px 7px", borderRadius: 20, whiteSpace: "nowrap",
                        background: "var(--hf-surface-2, var(--hf-border))", color: "var(--hf-text-muted)" }}>{pretty(e.status)}</span>
                    )}
                  </div>
                  <div aria-hidden="true" style={{ borderBottom: "2px dashed var(--hf-border)", margin: "6px 0 8px", maxWidth: 360 }} />
                  <div style={{ fontSize: 12, fontWeight: 800, letterSpacing: "0.04em", color: c }}>
                    {(KIND_LABEL[e.kind] ?? e.kind).toUpperCase()}{first ? <span style={{ fontWeight: 600, color: "var(--hf-text-muted)" }}>{` · ${timeLabel(e.at)}`}</span> : null}
                  </div>
                  <div style={{ fontSize: 14, fontWeight: 600, color: "var(--hf-text)", marginTop: 2 }}>{e.title}</div>
                  {e.detail && <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 2 }}>{e.detail}</div>}
                  {(e.summary ?? []).length > 0 && (
                    <ul aria-label="Visit summary" style={{ margin: "6px 0 0", paddingLeft: 16, fontSize: 12, color: "var(--hf-text-muted)", lineHeight: 1.5 }}>
                      {(e.summary ?? []).map(l => <li key={l}>{l}</li>)}
                    </ul>)}
                  {(e.people ?? []).length > 0 && (
                    <div style={{ marginTop: 6, fontSize: 12, color: "var(--hf-text-muted)" }}>
                      <strong style={{ color: "var(--hf-text)", fontWeight: 600 }}>People: </strong>{(e.people ?? []).join(" · ")}
                    </div>)}
                </li>
              )
            })}
          </ol>
        )}
    </div>
  )
}
