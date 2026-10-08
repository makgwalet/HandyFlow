// Small pieces shared by the consultation pages: step heading, review card, chip button style.
import { BORDER, GRAY_TEXT, sectionLabel } from "./consultationSession.shared"

export const chipStyle = (on: boolean): React.CSSProperties => ({ padding:"6px 12px", borderRadius:20, fontSize:12, fontWeight:600, cursor:"pointer",
  border:`1px solid ${on ? "var(--hf-primary)" : BORDER}`, background: on ? "var(--hf-primary-text)" : "var(--hf-surface)", color: on ? "var(--hf-surface)" : "var(--hf-text)" })

export function StepTitle({ n, title, help }: { n: number; title: string; help: string }) {
  return (
    <div>
      <h2 style={{ margin:0, fontSize:17, fontWeight:800, color:"var(--hf-text)" }}>{n}. {title}</h2>
      <div style={{ fontSize:12, color:GRAY_TEXT }}>{help}</div>
    </div>
  )
}

export function Recap({ title, rows, onEdit }: { title: string; rows: [string, string][]; onEdit: () => void }) {
  return (
    <section aria-label={title} style={{ padding:"12px 14px", background:"var(--hf-surface)", border:`1px solid ${BORDER}`, borderRadius:10 }}>
      <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:6 }}>
        <span style={sectionLabel}>{title}</span>
        <button type="button" onClick={onEdit} style={{ background:"none", border:"none", color:"var(--hf-accent-text)", fontSize:12, fontWeight:600, cursor:"pointer" }}>Edit</button>
      </div>
      {rows.map(([k, v]) => (
        <div key={k} style={{ marginBottom:6 }}>
          <div style={{ fontSize:11, color:GRAY_TEXT }}>{k}</div>
          {v.trim() ? <div style={{ fontSize:13, color:"var(--hf-text)", whiteSpace:"pre-wrap" }}>{v}</div>
                    : <div style={{ fontSize:13, color:"var(--hf-text-disabled)" }}>Not recorded</div>}
        </div>
      ))}
    </section>
  )
}
