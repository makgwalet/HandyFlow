// The letters of one employee relations case, with a way to prepare another from the case's own details.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { fmtDate } from "./er.logic"
import { templatesForCase } from "./letters.logic"
import { errorText, input, primary, unwrap, type LetterDetail as Detail, type LetterRow, type Templates } from "./letters.shared"
import { Notice, Section } from "./ErParts"
import LetterDetail from "./LetterDetail"
import { LetterStatus } from "./LetterParts"

const BASE = "/api/v1/hr/letters"

export default function CaseLetters({ caseId, caseType, canIssue }: { caseId: string; caseType: string; canIssue: boolean }) {
  const qc = useQueryClient()
  const [openId, setOpenId] = useState<string | null>(null)
  const [templateId, setTemplateId] = useState("")
  const [error, setError] = useState<string | null>(null)
  const rows = useQuery<LetterRow[]>({ queryKey: ["letters", "case", caseId], retry: false, queryFn: async () => unwrap<LetterRow[]>(await apiClient.get(`${BASE}?caseId=${caseId}`)) ?? [] })
  const tpl = useQuery<Templates>({ queryKey: ["letter-templates"], retry: false, enabled: canIssue, queryFn: async () => unwrap<Templates>(await apiClient.get(`${BASE}/templates`)) })
  const prepare = useMutation({
    mutationFn: async () => unwrap<Detail>(await apiClient.post(`${BASE}/prepare`, { templateId, employeeId: null, caseId })),
    onSuccess: d => { qc.invalidateQueries({ queryKey: ["letters"] }); setTemplateId(""); setOpenId(d.row.id) },
    onError: e => setError(errorText(e, "The letter could not be prepared.")),
  })

  if (rows.isError) return null // no permission to see letters: leave the section out
  if (openId) return <Section title="Letters"><LetterDetail id={openId} back={() => { setOpenId(null); qc.invalidateQueries({ queryKey: ["letters"] }) }} /></Section>

  const choices = templatesForCase(tpl.data?.templates ?? [], caseType)
  return (
    <Section title="Letters">
      {(rows.data ?? []).length === 0 ? <div style={{ fontSize: 13, color: "var(--hf-text-muted)", marginBottom: 10 }}>No letters for this case yet.</div> : (
        <ul style={{ listStyle: "none", margin: "0 0 12px", padding: 0, fontSize: 13 }}>
          {(rows.data ?? []).map(r => (
            <li key={r.id} style={{ display: "flex", gap: 10, alignItems: "center", marginBottom: 6, flexWrap: "wrap" }}>
              <button onClick={() => setOpenId(r.id)} style={{ background: "none", border: "none", padding: 0, cursor: "pointer", color: "var(--hf-primary)", fontWeight: 700 }}>{r.number}</button>
              <span>{r.subject}</span><LetterStatus status={r.status} />
              <span style={{ color: "var(--hf-text-faint)" }}>{r.issuedOn ? `issued ${fmtDate(r.issuedOn)}` : ""}{r.requiresAck && r.status === "ISSUED" ? (r.acknowledgedAt ? " · receipt confirmed" : " · waiting for confirmation") : ""}</span>
            </li>
          ))}
        </ul>
      )}
      {canIssue && choices.length > 0 && (
        <form aria-label="Prepare a case letter" onSubmit={e => { e.preventDefault(); setError(null); if (templateId) prepare.mutate() }} style={{ display: "flex", gap: 8, alignItems: "end", flexWrap: "wrap" }}>
          <div style={{ flex: 1, minWidth: 220 }}>
            <label htmlFor="cl-tpl" style={{ fontSize: 12, fontWeight: 600, display: "block", marginBottom: 4 }}>Prepare a letter for this case</label>
            <select id="cl-tpl" value={templateId} onChange={e => setTemplateId(e.target.value)} style={input}>
              <option value="">Choose a template…</option>{choices.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}
            </select>
          </div>
          <button type="submit" disabled={!templateId || prepare.isPending} style={primary}>{prepare.isPending ? "Preparing…" : "Prepare draft"}</button>
        </form>
      )}
      {error && <Notice kind="error">{error}</Notice>}
    </Section>
  )
}
