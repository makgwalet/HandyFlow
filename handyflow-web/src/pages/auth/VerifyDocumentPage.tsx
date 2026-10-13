// Public page: check that a document was issued by the practice (CLINIC-DEC-025, patch 0194). No login. Shows authenticity only,
// never a patient or clinical detail. The code is sent in a POST body so it stays out of logs; ?code= on the link only pre-fills the box.
import { useState } from "react"
import { useSearchParams } from "react-router-dom"
import { apiClient } from "../../api/client"
import { NOT_A_SIGNATURE, headline, looksComplete, outcome, tidyCode, type VerifyAnswer } from "../clinic/verifyDocumentView"

export function VerifyDocumentPage() {
  const [params] = useSearchParams()
  const [code, setCode] = useState(tidyCode(params.get("code") ?? ""))
  const [answer, setAnswer] = useState<VerifyAnswer | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")

  const check = async () => {
    setBusy(true); setError(""); setAnswer(null)
    try {
      const r: any = await apiClient.post("/api/v1/public/clinic/document-verification", { code })
      setAnswer(r?.data?.data ?? r?.data ?? null)
    } catch (e: any) {
      setError(e?.response?.status === 429 ? "Too many checks from this connection. Wait a few minutes and try again." : "The check could not be made. Try again in a moment.")
    } finally { setBusy(false) }
  }
  const o = answer ? outcome(answer) : null

  return (
    <main style={{ maxWidth: 520, margin: "48px auto", padding: "0 16px", fontFamily: "inherit" }}>
      <h1 style={{ fontSize: 22, marginBottom: 6 }}>Check a document</h1>
      <p style={{ fontSize: 14, color: "var(--hf-text-muted)", marginTop: 0 }}>Type the verification code printed at the foot of the document.</p>
      <form onSubmit={e => { e.preventDefault(); if (looksComplete(code) && !busy) void check() }} style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        <input aria-label="Verification code" value={code} onChange={e => { setCode(tidyCode(e.target.value)); setAnswer(null) }} placeholder="XXXX-XXXX-XXXX-XXXX"
          autoComplete="off" spellCheck={false} style={{ flex: 1, minWidth: 220, padding: "9px 12px", fontSize: 16, letterSpacing: 1 }} />
        <button type="submit" disabled={!looksComplete(code) || busy} style={{ padding: "9px 18px", fontSize: 15 }}>{busy ? "Checking…" : "Check"}</button>
      </form>
      {error && <div role="alert" style={{ marginTop: 14, fontSize: 14, color: "var(--hf-danger-text)" }}>{error}</div>}
      {answer && o && (
        <section aria-live="polite" style={{ marginTop: 18, border: "1px solid var(--hf-border)", borderRadius: 10, padding: 14 }}>
          <div style={{ fontWeight: 700, color: o === "GENUINE" ? "inherit" : "var(--hf-danger-text)" }}>{headline(o)}</div>
          {o !== "UNKNOWN" && (
            <dl style={{ fontSize: 14, margin: "10px 0 0" }}>
              <dt style={{ fontWeight: 600 }}>Document</dt><dd style={{ margin: "0 0 6px" }}>{answer.documentType} {answer.documentNumber}</dd>
              <dt style={{ fontWeight: 600 }}>Issued on</dt><dd style={{ margin: "0 0 6px" }}>{answer.issuedOn}</dd>
              <dt style={{ fontWeight: 600 }}>Issued by</dt><dd style={{ margin: 0 }}>{answer.issuedBy}</dd>
            </dl>
          )}
          {o !== "UNKNOWN" && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", marginBottom: 0 }}>{NOT_A_SIGNATURE}</p>}
        </section>
      )}
    </main>
  )
}
