// A thin progress strip above the session panels: the five steps and which still need filling in.
import { AlertCircle, CheckCircle, Circle } from "lucide-react"
import { BORDER, GRAY_TEXT, GREEN_TEXT, AMBER_TEXT } from "./consultationSession.shared"
import { STEP_TARGET, type ConsultStep } from "./consultSteps"

export default function ConsultationStepper({ steps, onJump, current, onSelect }:
  { steps: ConsultStep[]; onJump?: (targetId: string) => void; current?: ConsultStep["id"]; onSelect?: (id: ConsultStep["id"]) => void }) {
  return (
    <ol aria-label="Consultation steps" style={{ display:"flex", gap:6, listStyle:"none", margin:"0 0 12px", padding:0, flexWrap:"wrap" }}>
      {steps.map((s, i) => {
        const color = s.state === "done" ? GREEN_TEXT : s.state === "attention" ? AMBER_TEXT : GRAY_TEXT
        const Icon = s.state === "done" ? CheckCircle : s.state === "attention" ? AlertCircle : Circle
        return (
          <li key={s.id} data-state={s.state} title={s.hint} style={{ flex:"1 1 130px", display:"flex" }}>
            <button type="button" aria-current={current === s.id ? "step" : undefined}
              onClick={() => onSelect ? onSelect(s.id) : onJump?.(STEP_TARGET[s.id])}
              style={{ flex:1, display:"flex", alignItems:"center", gap:6, padding:"6px 10px", textAlign:"left", cursor:(onJump || onSelect) ? "pointer" : "default",
                border:`1px solid ${current === s.id ? "var(--hf-primary)" : BORDER}`, boxShadow: current === s.id ? "0 0 0 1px var(--hf-primary)" : "none", borderRadius:8, fontSize:12, color, background:"var(--hf-surface)" }}>
              <Icon size={14} aria-hidden="true" />
              <span style={{ fontWeight:600 }}>{i + 1}. {s.label}{s.required ? <span aria-label="required" title="Required to sign" style={{ color:AMBER_TEXT }}> *</span> : null}</span>
              <span style={{ fontSize:11, color:GRAY_TEXT, overflow:"hidden", textOverflow:"ellipsis", whiteSpace:"nowrap" }}>{s.hint}</span>
            </button>
          </li>
        )
      })}
    </ol>
  )
}
