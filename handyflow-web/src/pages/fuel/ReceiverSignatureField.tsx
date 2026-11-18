import { useEffect, useRef } from "react"
import { btnGhost } from "../warehousing/whseUi"

/**
 * A box the receiver signs in with a finger, stylus or mouse. Gives back a PNG data URI when they lift off, and an empty string when
 * it is cleared. A typed name is not a signature: the server asks for this (or a signature uploaded on the load) once the proof-of-delivery
 * policy is enforced.
 */
export default function ReceiverSignatureField({ onChange, disabled }: { onChange: (dataUri: string) => void; disabled?: boolean }) {
  const ref = useRef<HTMLCanvasElement>(null)
  const drawing = useRef(false)
  const last = useRef<{ x: number; y: number } | null>(null)

  const ctx = () => ref.current?.getContext?.("2d") ?? null
  useEffect(() => {
    const c = ctx(); if (!c) return
    c.lineWidth = 2; c.lineCap = "round"; c.strokeStyle = "#111"
  }, [])

  const pos = (e: React.PointerEvent<HTMLCanvasElement>) => {
    const r = e.currentTarget.getBoundingClientRect()
    return { x: (e.clientX - r.left) * (e.currentTarget.width / (r.width || 1)), y: (e.clientY - r.top) * (e.currentTarget.height / (r.height || 1)) }
  }
  const down = (e: React.PointerEvent<HTMLCanvasElement>) => {
    if (disabled) return
    drawing.current = true; last.current = pos(e)
    e.currentTarget.setPointerCapture?.(e.pointerId)
  }
  const move = (e: React.PointerEvent<HTMLCanvasElement>) => {
    if (!drawing.current) return
    const c = ctx(); const p = pos(e); const l = last.current
    if (c && l) { c.beginPath(); c.moveTo(l.x, l.y); c.lineTo(p.x, p.y); c.stroke() }
    last.current = p
  }
  const up = () => {
    if (!drawing.current) return
    drawing.current = false; last.current = null
    try { onChange(ref.current?.toDataURL("image/png") ?? "") } catch { onChange("") }
  }
  const clear = () => {
    const el = ref.current; const c = ctx()
    if (el && c) c.clearRect(0, 0, el.width, el.height)
    onChange("")
  }
  return (
    <div>
      <canvas ref={ref} width={480} height={160} aria-label="Receiver signature" role="img" onPointerDown={down} onPointerMove={move} onPointerUp={up} onPointerLeave={up}
        style={{ width: "100%", maxWidth: 480, height: 160, border: "1px solid var(--hf-border)", borderRadius: 8, background: "#fff", touchAction: "none", cursor: disabled ? "not-allowed" : "crosshair" }} />
      <div style={{ display: "flex", gap: 8, alignItems: "center", marginTop: 4 }}>
        <button type="button" style={btnGhost} onClick={clear} disabled={disabled}>Clear signature</button>
        <span style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>The receiver signs above. A typed name is not a signature.</span>
      </div>
    </div>
  )
}
