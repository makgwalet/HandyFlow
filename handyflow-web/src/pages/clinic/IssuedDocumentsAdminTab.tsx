// Numbered documents the clinic has issued, with the power to withdraw one (CLINIC-DEC-024). The PDF itself is not kept, only its hash.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import ModalShell from "./ModalShell"
import { BORDER, GRAY, lbl, sinp } from "./patientFile.shared"
import { STATUSES, TYPES, canVoid, listParams, statusLabel, voidProblem, type IssuedRow } from "./issuedDocumentsView"

const unwrap = (r: any) => r.data?.data ?? r.data
const day = (iso: string) => new Date(iso).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })

export default function IssuedDocumentsAdminTab() {
  const qc = useQueryClient()
  const [type, setType] = useState("")
  const [status, setStatus] = useState("")
  const [text, setText] = useState("")
  const [voiding, setVoiding] = useState<IssuedRow | null>(null)
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")

  const q = useQuery<IssuedRow[]>({
    queryKey: ["clinic-issued-documents", type, status, text], retry: false,
    queryFn: async () => unwrap(await apiClient.get("/api/v1/clinic/issued-documents", { params: listParams(type, status, text) })) ?? [],
  })
  const withdraw = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/clinic/issued-documents/${voiding!.id}/void`, { reason: reason.trim() }),
    onSuccess: () => { setVoiding(null); setReason(""); setError(""); qc.invalidateQueries({ queryKey: ["clinic-issued-documents"] }) },
    onError: (e: any) => setError(e?.response?.data?.message ?? "Could not withdraw the document."),
  })
  const problem = voidProblem(reason)

  return (
    <div>
      <div style={{ fontWeight: 700 }}>Issued documents</div>
      <div style={{ fontSize: 12, color: GRAY, marginBottom: 12, maxWidth: 640 }}>
        Every numbered document the clinic has issued. Withdrawing one keeps the record and tells anyone who checks its code that it was withdrawn. It cannot be undone.
      </div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 12 }}>
        <div><label style={lbl} htmlFor="id-type">Type</label>
          <select id="id-type" style={sinp} value={type} onChange={e => setType(e.target.value)}>{TYPES.map(t => <option key={t.id} value={t.id}>{t.label}</option>)}</select></div>
        <div><label style={lbl} htmlFor="id-status">Status</label>
          <select id="id-status" style={sinp} value={status} onChange={e => setStatus(e.target.value)}>{STATUSES.map(t => <option key={t.id} value={t.id}>{t.label}</option>)}</select></div>
        <div><label style={lbl} htmlFor="id-q">Number or patient</label>
          <input id="id-q" style={sinp} value={text} onChange={e => setText(e.target.value)} placeholder="SN-2026-… or a name" /></div>
      </div>
      {q.isLoading && <div style={{ fontSize: 13, color: GRAY }}>Loading…</div>}
      {q.isError && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>The documents could not be loaded.</div>}
      {q.data && q.data.length === 0 && <div style={{ fontSize: 13, color: GRAY }}>No documents match.</div>}
      {q.data && q.data.length > 0 && (
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
          <thead><tr style={{ textAlign: "left", color: GRAY, fontSize: 11 }}>
            {["Number", "Type", "Patient", "Issued", "Status", ""].map(h => <th key={h} style={{ padding: "6px 8px" }}>{h}</th>)}
          </tr></thead>
          <tbody>{q.data.map(r => (
            <tr key={r.id} style={{ borderTop: `1px solid ${BORDER}`, opacity: r.status === "VOIDED" ? 0.65 : 1 }}>
              <td style={{ padding: "8px", whiteSpace: "nowrap" }}>{r.number}</td>
              <td style={{ padding: "8px" }}>{r.typeLabel}</td>
              <td style={{ padding: "8px" }}>{r.patientName ?? "—"}</td>
              <td style={{ padding: "8px", whiteSpace: "nowrap" }}>{day(r.issuedAt)}</td>
              <td style={{ padding: "8px" }}>{statusLabel(r.status)}{r.voidReason && <div style={{ fontSize: 11, color: GRAY }}>{r.voidReason}</div>}</td>
              <td style={{ padding: "8px" }}>
                {canVoid(r) && <button type="button" onClick={() => { setError(""); setReason(""); setVoiding(r) }}
                  style={{ padding: "4px 10px", border: `1px solid ${BORDER}`, borderRadius: 7, background: "var(--hf-surface)", color: "var(--hf-danger-text)", cursor: "pointer", fontSize: 12 }}>Withdraw…</button>}
              </td>
            </tr>))}
          </tbody>
        </table>
      )}
      {voiding && (
        <ModalShell title="Withdraw document" onClose={() => setVoiding(null)} width={440}
          footer={<>
            <button type="button" onClick={() => setVoiding(null)} style={{ padding: "9px 18px", border: `1px solid ${BORDER}`, borderRadius: 9, background: "var(--hf-surface)", cursor: "pointer" }}>Cancel</button>
            <button type="button" disabled={withdraw.isPending || problem !== null} onClick={() => withdraw.mutate()}
              style={{ padding: "9px 20px", border: "none", borderRadius: 9, background: "var(--hf-danger)", color: "var(--hf-text-on-solid)", fontWeight: 600, cursor: "pointer", opacity: withdraw.isPending || problem ? 0.6 : 1 }}>
              {withdraw.isPending ? "Saving..." : "Withdraw document"}
            </button></>}>
          <div style={{ fontSize: 13, marginBottom: 10 }}><b>{voiding.number}</b> · {voiding.typeLabel}{voiding.patientName ? ` · ${voiding.patientName}` : ""}</div>
          <label style={lbl} htmlFor="id-reason">Reason *</label>
          <textarea id="id-reason" aria-label="Reason" rows={3} style={sinp} value={reason} onChange={e => setReason(e.target.value)} />
          {reason && problem && <div role="alert" style={{ marginTop: 8, fontSize: 13, color: "var(--hf-danger-text)" }}>{problem}</div>}
          {error && <div role="alert" style={{ marginTop: 8, fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</div>}
        </ModalShell>
      )}
    </div>
  )
}
