import { useRef, useState } from "react"
import { apiClient } from "../../api/client"
import { type Capture, isReadable } from "./capture.logic"

/**
 * "Read from a file": sends the file to be read and hands the suggested fields back.
 * Nothing is saved; the person checks the form and saves as usual.
 */
export default function CaptureFromFile({ onRead }: { onRead: (c: Capture) => void }) {
  const input = useRef<HTMLInputElement>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")
  const [notes, setNotes] = useState<string[]>([])

  const pick = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    e.target.value = ""
    if (!file) return
    setError(""); setNotes([])
    if (!isReadable(file.name, file.type)) {
      setError("Only PDF, JPEG and PNG files can be read. Fill the fields in by hand.")
      return
    }
    setBusy(true)
    try {
      const body = new FormData()
      body.append("file", file)
      const r: any = await apiClient.post("/api/v1/capture/extract", body, { headers: { "Content-Type": "multipart/form-data" } })
      const c: Capture = r.data?.data ?? r.data
      setNotes(c.notes ?? [])
      if (c.textFound) onRead(c)
    } catch (err: any) {
      setError(err?.response?.data?.message || "The file could not be read.")
    } finally {
      setBusy(false)
    }
  }

  return (
    <div style={{ border: "1.5px dashed var(--hf-border)", borderRadius: 10, padding: 10, marginBottom: 14, fontSize: 12 }}>
      <input ref={input} type="file" accept="application/pdf,image/jpeg,image/png,.pdf,.jpg,.jpeg,.png" onChange={pick} style={{ display: "none" }} aria-label="Invoice or receipt file" />
      <button type="button" disabled={busy} onClick={() => input.current?.click()}
        style={{ padding: "6px 12px", borderRadius: 8, border: "1.5px solid var(--hf-border)", background: "var(--hf-surface)", cursor: "pointer", fontSize: 12, fontWeight: 600 }}>
        {busy ? "Reading…" : "Read from a file"}
      </button>
      <span style={{ marginLeft: 8, color: "var(--hf-text-faint)" }}>Fills in what it can find. Check every field before saving.</span>
      {error && <div role="alert" style={{ marginTop: 6, color: "var(--hf-danger-text, #b91c1c)" }}>{error}</div>}
      {notes.length > 0 && <ul style={{ margin: "6px 0 0", paddingLeft: 18, color: "var(--hf-text-muted)" }}>{notes.map(n => <li key={n}>{n}</li>)}</ul>}
    </div>
  )
}
