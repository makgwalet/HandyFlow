// src/pages/clinic/QuestionForm.tsx
// Clinician-facing form for the question library. Shows the groups served for the visit type (ACTIVE content
// only, or demo content when the server is set to serve it), reveals questions as answers come in, shows
// warnings and urgent flags, and autosaves answers on the consultation. It never suggests a diagnosis.
import { useEffect, useRef, useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  BODY_REGIONS, DURATION_UNITS, cleanAnswers, scaleRange, toNumber, toggleInList, unitOptions, withUnit,
  type AnswerValue, type Answers, type QuestionOption,
} from "./questionForm.logic"

interface Rule { kind: string }
interface Question {
  code: string; label: string; helpText?: string; answerType: string
  options: QuestionOption[]; min?: number | null; max?: number | null
  defaultRequired: boolean; rules: Rule[]
}
interface Group {
  code: string; name: string; version: number; required: boolean; demo: boolean
  questions: Question[]
}
interface Evaluation {
  visible: string[]; required: string[]; disabled: string[]
  warnings: Record<string, string[]>; triggeredGroups: string[]
  redFlags: { code: string; label: string; severity: string; message?: string }[]
  urgent: boolean; effectiveAnswers: Answers; missingRequired: string[]; problems: Record<string, string>
}

const unwrap = (r: any) => r.data?.data ?? r.data

interface Props {
  consultationId: string | null     // null until the draft exists; nothing is saved before then
  patientId: string
  visitType: string
  readOnly?: boolean
  /** When set, show exactly these groups (those already started) instead of the ones served for the visit type. */
  groupCodes?: string[]
}

export default function QuestionForm({ consultationId, patientId, visitType, readOnly, groupCodes }: Props) {
  const fixed = groupCodes ? groupCodes.join(",") : null
  const { data: groups = [] } = useQuery<Group[]>({
    queryKey: ["clinic-question-groups", visitType, patientId, fixed],
    queryFn: async () => {
      if (groupCodes) {
        const out: Group[] = []
        for (const c of groupCodes) {
          try { out.push(unwrap(await apiClient.get(`/api/v1/clinic/question-groups/${c}`))) } catch { /* no longer served: skip */ }
        }
        return out
      }
      return unwrap(await apiClient.get("/api/v1/clinic/question-groups", { params: { visitType, patientId } })) ?? []
    },
    staleTime: 5 * 60_000,
  })
  const [extraCodes, setExtraCodes] = useState<string[]>([])
  const { data: extraGroups = [] } = useQuery<Group[]>({
    queryKey: ["clinic-question-groups-extra", extraCodes.join(",")],
    enabled: extraCodes.length > 0,
    queryFn: async () => {
      const out: Group[] = []
      for (const c of extraCodes) {
        try { out.push(unwrap(await apiClient.get(`/api/v1/clinic/question-groups/${c}`))) } catch { /* not served: skip */ }
      }
      return out
    },
  })

  const shown = [...groups, ...extraGroups.filter(g => !groups.some(x => x.code === g.code))]
  if (shown.length === 0) return null

  return (
    <div style={{ border: "1px solid var(--hf-border)", borderRadius: 10, background: "var(--hf-surface)", overflow: "hidden" }}>
      {shown.map(g => (
        <GroupSection key={g.code} group={g} consultationId={consultationId} patientId={patientId}
          visitType={visitType} readOnly={!!readOnly}
          onTriggered={codes => setExtraCodes(prev => Array.from(new Set([...prev, ...codes])))} />
      ))}
    </div>
  )
}

function GroupSection({ group, consultationId, patientId, visitType, readOnly, onTriggered }: {
  group: Group; consultationId: string | null; patientId: string; visitType: string; readOnly: boolean
  onTriggered: (codes: string[]) => void
}) {
  const [answers, setAnswers] = useState<Answers>({})
  const [ev, setEv] = useState<Evaluation | null>(null)
  const [state, setState] = useState<"idle" | "saving" | "saved" | "error">("idle")
  const [open, setOpen] = useState(true)
  const loadedFor = useRef<string | null>(null)
  const touched = useRef(false)

  // Load previously saved answers once the consultation exists.
  useEffect(() => {
    if (!consultationId || loadedFor.current === consultationId) return
    loadedFor.current = consultationId
    ;(async () => {
      try {
        const fd = unwrap(await apiClient.get(`/api/v1/clinic/consultations/${consultationId}/form-data`))
        const saved = fd?.groups?.[group.code]?.answers
        if (saved && !touched.current) setAnswers(saved)
      } catch { /* nothing saved yet */ }
    })()
  }, [consultationId, group.code])

  // Evaluate (and save when valid) shortly after the answers settle.
  useEffect(() => {
    const t = setTimeout(async () => {
      try {
        const e: Evaluation = unwrap(await apiClient.post(`/api/v1/clinic/question-groups/${group.code}/evaluate`,
          { patientId, visitType, answers: cleanAnswers(answers) }))
        setEv(e)
        if (e.triggeredGroups?.length) onTriggered(e.triggeredGroups)
        if (consultationId && !readOnly && touched.current && Object.keys(e.problems ?? {}).length === 0) {
          setState("saving")
          await apiClient.put(`/api/v1/clinic/consultations/${consultationId}/form-data/${group.code}`, { answers: cleanAnswers(answers) })
          setState("saved")
        }
      } catch { setState("error") }
    }, 700)
    return () => clearTimeout(t)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [answers, consultationId])

  const set = (code: string, v: AnswerValue) => { touched.current = true; setAnswers(p => ({ ...p, [code]: v })) }
  const visible = new Set(ev?.visible ?? group.questions.filter(q => !q.rules.some(r => r.kind === "SHOW_WHEN")).map(q => q.code))

  return (
    <div style={{ borderBottom: "1px solid var(--hf-border)" }}>
      <button onClick={() => setOpen(o => !o)} aria-expanded={open}
        style={{ width: "100%", textAlign: "left", padding: "10px 14px", background: "var(--hf-surface-muted)", border: "none",
          cursor: "pointer", display: "flex", gap: 8, alignItems: "center", fontWeight: 700, fontSize: 13, color: "var(--hf-text)" }}>
        <span style={{ flex: 1 }}>{group.name}{group.required ? " *" : ""}</span>
        {group.demo && <span style={{ fontSize: 10, padding: "2px 6px", borderRadius: 6, background: "var(--hf-warning-soft)", color: "var(--hf-warning-text)" }}>DEMO</span>}
        <span style={{ fontSize: 11, fontWeight: 500, color: "var(--hf-text-muted)" }}>
          {state === "saving" ? "Saving…" : state === "saved" ? "Saved" : state === "error" ? "Not saved" : ""}
        </span>
        <span aria-hidden>{open ? "▾" : "▸"}</span>
      </button>
      {open && (
        <div style={{ padding: 14, display: "grid", gap: 12 }}>
          {ev?.redFlags?.map(f => (
            <div key={f.code} role={f.severity === "URGENT" ? "alert" : "status"}
              style={{ padding: "8px 10px", borderRadius: 8, fontSize: 13, fontWeight: 600,
                background: f.severity === "URGENT" ? "var(--hf-danger-soft)" : "var(--hf-info-soft)",
                color: f.severity === "URGENT" ? "var(--hf-danger-text)" : "var(--hf-info-text)" }}>
              {f.severity === "URGENT" ? "Needs attention: " : "Note: "}{f.label}{f.message ? `. ${f.message}` : ""}
            </div>
          ))}
          {group.questions.filter(q => visible.has(q.code)).map(q => {
            const required = (ev?.required ?? []).includes(q.code) || q.defaultRequired
            const disabled = readOnly || (ev?.disabled ?? []).includes(q.code)
            const problem = ev?.problems?.[q.code]
            const missing = ev?.missingRequired?.includes(q.code)
            return (
              <div key={q.code}>
                <label style={{ display: "block", fontSize: 13, fontWeight: 600, color: "var(--hf-text)", marginBottom: 4 }}>
                  {q.label}{required ? " *" : ""}
                </label>
                {q.helpText && <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginBottom: 4 }}>{q.helpText}</div>}
                <Field q={q} value={answers[q.code]} disabled={disabled} onChange={v => set(q.code, v)} />
                {problem && <div role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text)", marginTop: 4 }}>{problem}</div>}
                {!problem && missing && answers[q.code] !== undefined && (
                  <div style={{ fontSize: 12, color: "var(--hf-warning-text)", marginTop: 4 }}>Required</div>
                )}
                {(ev?.warnings?.[q.code] ?? []).map((w, i) => (
                  <div key={i} style={{ fontSize: 12, color: "var(--hf-warning-text)", marginTop: 4 }}>{w}</div>
                ))}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

const inputStyle: React.CSSProperties = {
  padding: "7px 9px", borderRadius: 8, border: "1px solid var(--hf-border)", fontSize: 13,
  background: "var(--hf-surface)", color: "var(--hf-text)", maxWidth: "100%",
}
const chip = (on: boolean): React.CSSProperties => ({
  padding: "5px 11px", borderRadius: 16, fontSize: 12, cursor: "pointer", fontWeight: 600,
  border: "1px solid " + (on ? "var(--hf-accent)" : "var(--hf-border)"),
  background: on ? "var(--hf-accent)" : "var(--hf-surface)", color: on ? "var(--hf-text-on-solid)" : "var(--hf-text)",
})

function Field({ q, value, disabled, onChange }: {
  q: Question; value: AnswerValue; disabled: boolean; onChange: (v: AnswerValue) => void
}) {
  const label = (o: QuestionOption) => o.label ?? o.value
  switch (q.answerType) {
    case "YES_NO":
      return (
        <div style={{ display: "flex", gap: 6 }}>
          <button type="button" disabled={disabled} style={chip(value === true)} onClick={() => onChange(true)}>Yes</button>
          <button type="button" disabled={disabled} style={chip(value === false)} onClick={() => onChange(false)}>No</button>
        </div>)
    case "TOGGLE":
      return <input type="checkbox" disabled={disabled} checked={value === true} onChange={e => onChange(e.target.checked)} />
    case "SINGLE_SELECT":
    case "RADIO_GROUP":
      return (
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          {q.options.map(o => (
            <button key={o.value} type="button" disabled={disabled} style={chip(value === o.value)}
              onClick={() => onChange(o.value)}>{label(o)}</button>))}
        </div>)
    case "MULTI_SELECT":
    case "CHECKLIST":
      return (
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          {q.options.map(o => (
            <button key={o.value} type="button" disabled={disabled}
              style={chip(Array.isArray(value) && value.includes(o.value))}
              onClick={() => onChange(toggleInList(value, o.value))}>{label(o)}</button>))}
        </div>)
    case "NUMBER":
    case "DECIMAL":
      return <input type="number" inputMode="decimal" disabled={disabled} style={{ ...inputStyle, width: 120 }}
        value={typeof value === "number" ? value : ""} min={q.min ?? undefined} max={q.max ?? undefined}
        step={q.answerType === "NUMBER" ? 1 : "any"} onChange={e => onChange(toNumber(e.target.value))} />
    case "TEXT":
      return <input type="text" disabled={disabled} maxLength={500} style={{ ...inputStyle, width: "100%" }}
        value={typeof value === "string" ? value : ""} onChange={e => onChange(e.target.value)} />
    case "LONG_TEXT":
      return <textarea disabled={disabled} rows={3} maxLength={5000} style={{ ...inputStyle, width: "100%" }}
        value={typeof value === "string" ? value : ""} onChange={e => onChange(e.target.value)} />
    case "DATE":
      return <input type="date" disabled={disabled} style={inputStyle}
        value={typeof value === "string" ? value : ""} onChange={e => onChange(e.target.value)} />
    case "DATE_TIME":
      return <input type="datetime-local" disabled={disabled} style={inputStyle}
        value={typeof value === "string" ? value : ""} onChange={e => onChange(e.target.value)} />
    case "DURATION":
    case "MEASUREMENT": {
      const isDuration = q.answerType === "DURATION"
      const units = isDuration ? [...DURATION_UNITS] : unitOptions(q.options, ["kg"])
      const cur = value && typeof value === "object" && !Array.isArray(value) ? value : null
      return (
        <div style={{ display: "flex", gap: 6 }}>
          <input type="number" inputMode="decimal" disabled={disabled} style={{ ...inputStyle, width: 100 }}
            value={cur ? cur.value : ""} step="any"
            onChange={e => onChange(withUnit(value, { value: e.target.value }, units[0]))} />
          <select disabled={disabled} style={inputStyle} value={cur?.unit ?? units[0]}
            onChange={e => onChange(withUnit(value, { unit: e.target.value }, units[0]))}>
            {units.map(u => <option key={u} value={u}>{isDuration ? u.toLowerCase() : u}</option>)}
          </select>
        </div>)
    }
    case "SCALE":
      return (
        <div style={{ display: "flex", gap: 4, flexWrap: "wrap" }}>
          {scaleRange(q.min, q.max).map(n => (
            <button key={n} type="button" disabled={disabled} style={{ ...chip(value === n), padding: "5px 9px" }}
              onClick={() => onChange(n)}>{n}</button>))}
        </div>)
    case "BODY":
      return (
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          {BODY_REGIONS.map(r => (
            <button key={r.value} type="button" disabled={disabled}
              style={chip(Array.isArray(value) && value.includes(r.value))}
              onClick={() => onChange(toggleInList(value, r.value))}>{r.label}</button>))}
        </div>)
    default:
      return <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Unsupported answer type {q.answerType}</div>
  }
}
