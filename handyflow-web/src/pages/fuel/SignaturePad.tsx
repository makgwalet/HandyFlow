import { useRef, useState } from "react"
import { btnGhost, btnPrimary } from "../warehousing/whseUi"

/** A finger or stylus signature. Saves a PNG data URL; refuses an empty pad. */
export default function SignaturePad({ onSave, onCancel }: { onSave: (dataUrl: string) => void; onCancel: () => void }) {
  const ref = useRef<HTMLCanvasElement>(null)
  const drawing = useRef(false)
  const [inked, setInked] = useState(false)
  const [problem, setProblem] = useState("")

  const at = (e: React.PointerEvent<HTMLCanvasElement>) => {
    const r = e.currentTarget.getBoundingClientRect()
    const sx = r.width ? e.currentTarget.width / r.width : 1, sy = r.height ? e.currentTarget.height / r.height : 1
    return { x: (e.clientX - r.left) * sx, y: (e.clientY - r.top) * sy }
  }
  const down = (e: React.PointerEvent<HTMLCanvasElement>) => {
    const ctx = ref.current?.getContext("2d"); if (!ctx) return
    drawing.current = true; setProblem("")
    const { x, y } = at(e); ctx.lineWidth = 3; ctx.lineCap = "round"; ctx.strokeStyle = "#111"; ctx.beginPath(); ctx.moveTo(x, y)
    e.currentTarget.setPointerCapture?.(e.pointerId)
  }
  const move = (e: React.PointerEvent<HTMLCanvasElement>) => {
    if (!drawing.current) return
    const ctx = ref.current?.getContext("2d"); if (!ctx) return
    const { x, y } = at(e); ctx.lineTo(x, y); ctx.stroke(); setInked(true)
  }
  const up = () => { drawing.current = false }
  const clear = () => { const c = ref.current; c?.getContext("2d")?.clearRect(0, 0, c.width, c.height); setInked(false); setProblem("") }
  const save = () => {
    if (!inked || !ref.current) return setProblem("Ask the receiver to sign first.")
    onSave(ref.current.toDataURL("image/png"))
  }
  return (
    <div>
      <canvas ref={ref} width={600} height={240} aria-label="Signature pad" onPointerDown={down} onPointerMove={move} onPointerUp={up} onPointerLeave={up}
        style={{ width: "100%", height: 160, background: "#fff", border: "1px solid var(--hf-border)", borderRadius: 8, touchAction: "none" }} />
      {problem && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13, margin: "6px 0 0" }}>{problem}</p>}
      <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
        <button style={{ ...btnGhost, flex: 1, padding: 12 }} onClick={onCancel}>Back</button>
        <button style={{ ...btnGhost, flex: 1, padding: 12 }} onClick={clear}>Clear</button>
        <button style={{ ...btnPrimary, flex: 2, padding: 12 }} onClick={save}>Save signature</button>
      </div>
    </div>
  )
}
