// A masked guard value with a "Show" button. Showing asks for a reason, which is written to the audit log.
// The full value is kept only in this component and disappears after SHOW_SECONDS.
import { useEffect, useState } from "react"
import { useMutation } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { FIELD_LABEL, SHOW_SECONDS, isMasked, reasonProblem, revealBody, type PiiField } from "./piiRevealLogic"

const small: React.CSSProperties = { padding: "4px 10px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", fontSize: 12, fontWeight: 600, cursor: "pointer" }

export default function RevealPii({ guardId, field, masked, canReveal }: {
  guardId: string; field: PiiField; masked: string | null; canReveal: boolean
}) {
  const [asking, setAsking] = useState(false)
  const [reason, setReason] = useState("")
  const [value, setValue] = useState<string | null>(null)
  const [err, setErr] = useState("")

  useEffect(() => {
    if (value === null) return
    const t = setTimeout(() => setValue(null), SHOW_SECONDS * 1000)
    return () => clearTimeout(t)
  }, [value])

  const reveal = useMutation({
    mutationFn: async () => (await apiClient.post(`/api/v1/security/guards/${guardId}/reveal`, revealBody(field, reason))).data?.data as { value: string },
    onSuccess: d => { setValue(d.value); setAsking(false); setReason(""); setErr("") },
    onError: (e: unknown) => setErr((e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? "Could not show it. Please try again."),
  })

  const submit = () => {
    const p = reasonProblem(reason)
    if (p) { setErr(p); return }
    reveal.mutate()
  }

  if (!masked) return <span>—</span>
  return (
    <span>
      <span>{value ?? masked}</span>
      {canReveal && isMasked(masked) && value === null && !asking && (
        <button type="button" style={{ ...small, marginLeft: 8 }} onClick={() => { setAsking(true); setErr("") }}>Show</button>
      )}
      {value !== null && (
        <button type="button" style={{ ...small, marginLeft: 8 }} onClick={() => setValue(null)}>Hide</button>
      )}
      {asking && (
        <div style={{ marginTop: 6, display: "flex", flexDirection: "column", gap: 6 }}>
          <input value={reason} onChange={e => setReason(e.target.value)} aria-label={`Reason for showing the ${FIELD_LABEL[field]}`}
            placeholder={`Why do you need the ${FIELD_LABEL[field]}? (recorded)`}
            style={{ padding: "7px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 12 }} />
          <div style={{ display: "flex", gap: 6 }}>
            <button type="button" style={small} onClick={submit} disabled={reveal.isPending}>{reveal.isPending ? "Checking…" : "Show"}</button>
            <button type="button" style={small} onClick={() => { setAsking(false); setReason(""); setErr("") }}>Cancel</button>
          </div>
        </div>
      )}
      {err && <div role="alert" style={{ marginTop: 4, fontSize: 12, color: "var(--hf-danger)" }}>{err}</div>}
    </span>
  )
}
