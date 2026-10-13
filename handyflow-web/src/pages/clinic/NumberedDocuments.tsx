// The patient's numbered documents (patch 0203): every sick note, referral, prescription, visit summary, letter and consent record the
// practice issued, with its number and status. Download returns the kept copy (same number). Sending to the patient is its own step.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { useCan } from "./clinicAccess"
import { canSend, downloadKeptCopy, emailToPatient, errorMessage, patientDocuments, sentLine, type IssuedDoc } from "./issuedDocs"

const btn: React.CSSProperties = { padding: "4px 10px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", fontSize: 12, cursor: "pointer", whiteSpace: "nowrap" }

export default function NumberedDocuments({ patientId }: { patientId: string }) {
  const qc = useQueryClient()
  const mayDownload = useCan("downloadDocuments")
  const mayEmail = useCan("emailDocuments")
  const key = ["pf-issued-documents", patientId]
  const { data: docs = [], isLoading, isError } = useQuery<IssuedDoc[]>({ queryKey: key, queryFn: () => patientDocuments(patientId), retry: false })
  const [busy, setBusy] = useState<string | null>(null)
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null)

  const run = async (id: string, work: () => Promise<string | void>, fallback: string) => {
    setBusy(id); setMessage(null)
    try { const ok = await work(); if (ok) setMessage({ ok: true, text: ok }) }
    catch (e: any) { setMessage({ ok: false, text: await errorMessage(e, fallback) }) }
    finally { setBusy(null) }
  }

  if (isLoading || isError || docs.length === 0) return null   // nothing issued yet, or not readable: the register above still shows
  return (
    <section aria-label="Numbered documents" style={{ marginTop: 22 }}>
      <div style={{ fontSize: 14, fontWeight: 700, color: "var(--hf-text)", marginBottom: 4 }}>Numbered documents</div>
      <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginBottom: 10 }}>
        Documents the practice issued with a number and verification code. Downloading gives the same copy again; nothing is sent to the patient unless you choose to.
      </div>
      {message && <div role={message.ok ? "status" : "alert"} style={{ marginBottom: 10, fontSize: 13, color: message.ok ? "var(--hf-success-text-strong)" : "var(--hf-danger-text)" }}>{message.text}</div>}
      <div style={{ border: "1px solid var(--hf-border)", borderRadius: 12, overflow: "hidden" }}>
        {docs.map((d, n) => (
          <div key={d.id} style={{ display: "flex", alignItems: "center", gap: 12, padding: "10px 14px", flexWrap: "wrap", borderTop: n ? "1px solid var(--hf-border)" : undefined }}>
            <div style={{ flex: 1, minWidth: 200 }}>
              <div style={{ fontSize: 13, fontWeight: 600, color: "var(--hf-text)" }}>
                {d.typeLabel} {d.number}
                {d.status === "VOIDED" && <span style={{ marginLeft: 8, fontSize: 10, fontWeight: 700, padding: "1px 6px", borderRadius: 20, background: "var(--hf-surface-sunken)", color: "var(--hf-text-muted)" }}>WITHDRAWN</span>}
              </div>
              <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
                Issued {new Date(d.issuedAt).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })}
                {d.status === "VOIDED" ? (d.voidReason ? ` · ${d.voidReason}` : "") : ` · ${sentLine(d)}`}
                {!d.copyKept && " · copy not kept (issued before copies were kept)"}
              </div>
            </div>
            {mayDownload && d.copyKept && (
              <button type="button" style={btn} disabled={busy === d.id} aria-label={`Download ${d.number}`}
                onClick={() => run(d.id, () => downloadKeptCopy(patientId, d), "The document could not be downloaded.")}>Download</button>)}
            {mayEmail && canSend(d) && (
              <button type="button" style={btn} disabled={busy === d.id} aria-label={`Email ${d.number} to the patient`}
                onClick={() => run(d.id, async () => { const r = await emailToPatient(patientId, d.id); qc.invalidateQueries({ queryKey: key }); return `${d.number} sent to ${r.recipient}.` }, "The document could not be sent.")}>
                Email to patient</button>)}
          </div>
        ))}
      </div>
    </section>
  )
}
