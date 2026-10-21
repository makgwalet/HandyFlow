// An employee's own letters: read them, download the PDF, and confirm they were received.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { fmtDate, fmtInstant } from "./er.logic"
import { card, downloadLetter, errorText, plain, primary, td, th, unwrap, type MineLetter } from "./letters.shared"
import { Chip, Notice, Section } from "./ErParts"

const BASE = "/api/v1/hr/letters/mine"

export default function MyLetters() {
  const [openId, setOpenId] = useState<string | null>(null)
  const q = useQuery<MineLetter[]>({ queryKey: ["letters-mine"], queryFn: async () => unwrap<MineLetter[]>(await apiClient.get(BASE)) ?? [] })
  if (openId) return <MyLetter id={openId} back={() => setOpenId(null)} />
  const rows = q.data ?? []
  return (
    <div>
      <p style={{ margin: "0 0 14px", fontSize: 13, color: "var(--hf-text-muted)" }}>Letters HR has issued to you.</p>
      {q.isError && <Notice kind="error">{errorText(q.error, "Your letters could not be loaded.")}</Notice>}
      <div style={card}>
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr><th style={th}>Letter</th><th style={th}>Issued</th><th style={th}>Confirmation</th></tr></thead>
          <tbody>
            {q.isLoading && <tr><td style={td} colSpan={3}>Loading…</td></tr>}
            {!q.isLoading && rows.length === 0 && <tr><td style={td} colSpan={3}>You have no letters.</td></tr>}
            {rows.map(r => (
              <tr key={r.id}>
                <td style={td}>
                  <button onClick={() => setOpenId(r.id)} style={{ background: "none", border: "none", padding: 0, cursor: "pointer", color: "var(--hf-primary)", fontWeight: 700, textAlign: "left" }}>{r.number}</button>
                  <div style={{ color: "var(--hf-text-muted)" }}>{r.subject}</div>
                </td>
                <td style={td}>{fmtDate(r.issuedOn)}</td>
                <td style={td}>{!r.requiresAck ? "Not needed" : r.acknowledgedAt ? "Confirmed" : "Please confirm"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function MyLetter({ id, back }: { id: string; back: () => void }) {
  const qc = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState<string | null>(null)
  const q = useQuery<MineLetter>({ queryKey: ["letters-mine", id], queryFn: async () => unwrap<MineLetter>(await apiClient.get(`${BASE}/${id}`)) })
  const ack = useMutation({
    mutationFn: async () => unwrap<MineLetter>(await apiClient.post(`${BASE}/${id}/acknowledge`, {})),
    onSuccess: d => { qc.setQueryData(["letters-mine", id], d); qc.invalidateQueries({ queryKey: ["letters-mine"] }); setError(null); setSaved("Thank you. We have recorded that you received this letter.") },
    onError: e => setError(errorText(e, "That could not be saved.")),
  })
  if (q.isLoading) return <div style={{ padding: 24, color: "var(--hf-text-faint)" }}>Loading…</div>
  if (q.isError || !q.data) return <div><button onClick={back} style={plain}>Back</button><Notice kind="error">{errorText(q.error, "This letter could not be opened.")}</Notice></div>
  const l = q.data
  const download = async () => { const m = await downloadLetter(`${BASE}/${id}/pdf`, `${l.number}.pdf`); setError(m) }
  return (
    <div>
      <button onClick={back} style={{ ...plain, marginBottom: 12 }}>← Back</button>
      <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap", marginBottom: 4 }}>
        <h2 style={{ margin: 0, fontSize: 20 }}>{l.subject}</h2>
        {l.requiresAck && <Chip text={l.acknowledgedAt ? "Receipt confirmed" : "Please confirm"} color={l.acknowledgedAt ? "var(--hf-success-text-strong)" : "var(--hf-warning-text-strong)"} bg={l.acknowledgedAt ? "var(--hf-success-soft-strong)" : "var(--hf-warning-soft)"} />}
      </div>
      <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: "4px 0 14px" }}>{l.number} · issued {fmtDate(l.issuedOn)}</p>
      {saved && <Notice kind="ok">{saved}</Notice>}
      {error && <Notice kind="error">{error}</Notice>}
      <Section title="The letter"><div style={{ whiteSpace: "pre-wrap", fontSize: 13, lineHeight: 1.5 }}>{l.body}</div></Section>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        <button onClick={download} style={plain}>Download PDF</button>
        {l.canAcknowledge && <button onClick={() => ack.mutate()} disabled={ack.isPending} style={primary}>I have received this letter</button>}
      </div>
      {l.canAcknowledge && <p style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Confirming receipt does not mean you agree with what the letter says.</p>}
      {l.acknowledgedAt && <p style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>You confirmed receipt on {fmtInstant(l.acknowledgedAt)}.</p>}
    </div>
  )
}
