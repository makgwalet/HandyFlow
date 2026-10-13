import { useState } from "react"
import ModalShell from "./ModalShell"
import { NOTES_MAX, notesProblem } from "./claimCorrection"

/** Notes stay editable after a claim is submitted (CLINIC-DEC-004); the amounts and lines do not change. */
export default function ClaimNotesModal({ initial, busy, error, onSave, onClose }: {
  initial: string; busy?: boolean; error?: string; onSave: (notes: string) => void; onClose: () => void
}) {
  const [notes, setNotes] = useState(initial)
  const problem = notesProblem(notes)
  return (
    <ModalShell title="Claim notes" onClose={onClose} width={460}
      footer={<>
        <button type="button" onClick={onClose} style={{ padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", cursor: "pointer" }}>Cancel</button>
        <button type="button" disabled={busy || problem !== null} onClick={() => onSave(notes)}
          style={{ padding: "9px 20px", border: "none", borderRadius: 9, background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", fontWeight: 600, cursor: "pointer", opacity: busy || problem ? 0.6 : 1 }}>
          {busy ? "Saving..." : "Save notes"}
        </button></>}>
      <div style={{ fontSize: 13, color: "var(--hf-text-muted)", marginBottom: 10 }}>Notes are for your own records. Changing them does not change anything the scheme was told.</div>
      <textarea aria-label="Notes" rows={5} value={notes} onChange={e => setNotes(e.target.value)} maxLength={NOTES_MAX + 200}
        style={{ width: "100%", padding: "9px 12px", boxSizing: "border-box", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 14 }} />
      {problem && <div role="alert" style={{ marginTop: 8, fontSize: 13, color: "var(--hf-danger-text)" }}>{problem}</div>}
      {error && <div role="alert" style={{ marginTop: 8, fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</div>}
    </ModalShell>
  )
}
