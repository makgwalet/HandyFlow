// A thin progress strip above the session panels: the five steps and which still need filling in.
import { AlertCircle, CheckCircle, Circle } from "lucide-react"
import { BORDER, GRAY_TEXT, GREEN_TEXT, AMBER_TEXT } from "./consultationSession.shared"
import type { ConsultStep } from "./consultSteps"

export default function ConsultationStepper({ steps }: { steps: ConsultStep[] }) {
  return (
    <ol aria-label="Consultation steps" style={{ display:"flex", gap:6, listStyle:"none", margin:"0 0 12px", padding:0, flexWrap:"wrap" }}>
      {steps.map((s, i) => {
        const color = s.state === "done" ? GREEN_TEXT : s.state === "attention" ? AMBER_TEXT : GRAY_TEXT
        const Icon = s.state === "done" ? CheckCircle : s.state === "attention" ? AlertCircle : Circle
        return (
          <li key={s.id} data-state={s.state} title={s.hint}
            style={{ flex:"1 1 130px", display:"flex", alignItems:"center", gap:6, padding:"6px 10px",
              border:`1px solid ${BORDER}`, borderRadius:8, fontSize:12, color, background:"var(--hf-surface)" }}>
            <Icon size={14} aria-hidden="true" />
            <span style={{ fontWeight:600 }}>{i + 1}. {s.label}</span>
            <span style={{ fontSize:11, color:GRAY_TEXT, overflow:"hidden", textOverflow:"ellipsis", whiteSpace:"nowrap" }}>{s.hint}</span>
          </li>
        )
      })}
    </ol>
  )
}
