// src/pages/agriculture/AgConfirmHost.tsx
// Shows the question asked by agConfirm(). Uses the browser's own <dialog>, which keeps focus inside, closes on Escape and
// is announced as a dialog by screen readers. "No" is the first thing focused, so Enter never deletes by accident.
import { useEffect, useId, useRef, useSyncExternalStore } from "react"
import { btnGhost, btnPrimary, btnDanger } from "./constants"
import { currentConfirm, subscribeConfirm } from "./agConfirm"

export default function AgConfirmHost() {
  const open = useSyncExternalStore(subscribeConfirm, currentConfirm, () => null)
  const ref = useRef<HTMLDialogElement>(null)
  const noRef = useRef<HTMLButtonElement>(null)
  const id = useId()
  useEffect(() => {
    const d = ref.current
    if (open && d && !d.open) { d.showModal(); noRef.current?.focus() }
    if (!open && d?.open) d.close()
  }, [open])
  if (!open) return null
  return (
    <dialog ref={ref} className="hf-ag-dialog" aria-labelledby={`${id}-t`} aria-describedby={open.message ? `${id}-m` : undefined}
      onCancel={e => { e.preventDefault(); open.resolve(false) }}
      onClick={e => { if (e.target === e.currentTarget) open.resolve(false) }}>
      <h2 id={`${id}-t`} style={{ fontSize: 15, fontWeight: 800, color: "var(--hf-text)", margin: "0 0 6px" }}>{open.title}</h2>
      {open.message && <p id={`${id}-m`} style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: "0 0 14px" }}>{open.message}</p>}
      <div style={{ display: "flex", gap: 8, justifyContent: "flex-end", marginTop: open.message ? 0 : 14 }}>
        <button ref={noRef} type="button" onClick={() => open.resolve(false)} style={btnGhost}>Cancel</button>
        <button type="button" onClick={() => open.resolve(true)} style={open.danger ? { ...btnDanger, background: "var(--hf-danger)", color: "#fff", border: "none", fontWeight: 700 } : btnPrimary}>{open.confirmLabel ?? "Yes"}</button>
      </div>
    </dialog>
  )
}
