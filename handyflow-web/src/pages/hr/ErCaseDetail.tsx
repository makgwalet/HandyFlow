// One case, for HR: what it is, what to watch, and the buttons the server says are allowed right now.
import { useState } from "react"
import { useAuth } from "../../hooks/useAuth"
import { letterRights } from "./letters.logic"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { ACTION_LABEL, TYPE_LABEL, fmtDate, words } from "./er.logic"
import { errorText, plain, primary, unwrap, type Detail } from "./er.shared"
import CaseLetters from "./CaseLetters"
import ErActionForm from "./ErActionForms"
import { Chip, Notice, Section, StatusChip } from "./ErParts"
import ErSections from "./ErSections"

const BASE = "/api/v1/hr/employee-relations"

export default function ErCaseDetail({ id, back }: { id: string; back: () => void }) {
  const qc = useQueryClient()
  const { user } = useAuth() as { user?: { permissions?: string[] } }
  const letters = letterRights(user?.permissions ?? [])
  const [action, setAction] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState<string | null>(null)

  const q = useQuery<Detail>({ queryKey: ["er-case", id], queryFn: async () => unwrap<Detail>(await apiClient.get(`${BASE}/cases/${id}`)) })
  const m = useMutation({
    mutationFn: async (v: { path: string; body?: unknown }) => unwrap<Detail>(await apiClient.post(`${BASE}/cases/${id}/${v.path}`, v.body ?? {})),
    onSuccess: (d, v) => {
      qc.setQueryData(["er-case", id], d)
      qc.invalidateQueries({ queryKey: ["er-cases"] })
      setError(null); setAction(null); setSaved(`${ACTION_LABEL[labelFor(v.path)] ?? "Saved"} – saved.`)
    },
    onError: e => setError(errorText(e, "That could not be saved.")),
  })

  if (q.isLoading) return <div style={{ padding: 24, color: "var(--hf-text-faint)" }}>Loading the case…</div>
  if (q.isError || !q.data) return (
    <div>
      <button onClick={back} style={plain}>Back to cases</button>
      <Notice kind="error">{errorText(q.error, "This case could not be opened. It may not exist, or you may not be allowed to see it.")}</Notice>
    </div>
  )
  const c = q.data
  const a = c.advice
  const choose = (name: string) => { setError(null); setSaved(null); setAction(name) }

  return (
    <div>
      <button onClick={back} style={{ ...plain, marginBottom: 12 }}>← Back to cases</button>
      <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap", marginBottom: 4 }}>
        <h2 style={{ margin: 0, fontSize: 20 }}>{c.number} · {c.title}</h2>
        <Chip text={TYPE_LABEL[c.type] ?? c.type} color="var(--hf-text-secondary)" bg="var(--hf-surface-sunken)" />
        <StatusChip status={c.status} />
        {c.confidential && <Chip text="Confidential" color="var(--hf-danger-text)" bg="var(--hf-danger-soft)" />}
        {c.legacy && <Chip text="Imported record" color="var(--hf-text-muted)" bg="var(--hf-surface-sunken)" />}
      </div>
      {c.legacy && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "4px 0 0" }}>This record came from the old screen, so some details (such as how long a warning stays valid) may be missing.</p>}

      {a?.responseDue && (
        <div style={{ margin: "12px 0" }}>
          <Notice kind={a.overdue ? "error" : "ok"}>{a.overdue ? `The response to this grievance was due ${fmtDate(a.responseDue)} and is late.` : `The response to this grievance is due ${fmtDate(a.responseDue)}.`}</Notice>
        </div>
      )}
      {saved && <div style={{ margin: "12px 0" }}><Notice kind="ok">{saved}</Notice></div>}

      {c.actions.length > 0 && (
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap", margin: "14px 0" }} aria-label="Actions">
          {c.actions.map(name => (
            <button key={name} onClick={() => choose(name)} style={name === action ? primary : plain}>{ACTION_LABEL[name] ?? words(name)}</button>
          ))}
        </div>
      )}
      {action && <ErActionForm action={action} c={c} busy={m.isPending} error={error} send={(path, body) => m.mutate({ path, body })} cancel={() => { setAction(null); setError(null) }} />}

      {a && (a.activeWarnings.length > 0 || a.unknownValidity.length > 0 || a.guidance) && (
        <Section title="For whoever decides">
          {a.guidance && <p style={{ fontSize: 13, margin: "0 0 8px" }}>{a.guidance}</p>}
          {a.activeWarnings.length > 0 && (
            <>
              <div style={{ fontSize: 12, fontWeight: 700, marginBottom: 4 }}>Warnings in force</div>
              <ul style={{ margin: "0 0 8px", paddingLeft: 18, fontSize: 13 }}>
                {a.activeWarnings.map(w => <li key={w.caseId}>{words(w.sanction)} ({w.number}), given {fmtDate(w.issuedOn)}, valid until {fmtDate(w.validUntil)}</li>)}
              </ul>
            </>
          )}
          {a.unknownValidity.length > 0 && (
            <>
              <div style={{ fontSize: 12, fontWeight: 700, marginBottom: 4 }}>Earlier warnings with no recorded validity (not counted as in force)</div>
              <ul style={{ margin: 0, paddingLeft: 18, fontSize: 13 }}>
                {a.unknownValidity.map(w => <li key={w.caseId}>{words(w.sanction)} ({w.number}), given {fmtDate(w.issuedOn)}</li>)}
              </ul>
            </>
          )}
        </Section>
      )}

      {letters.read && <CaseLetters caseId={c.id} caseType={c.type} canIssue={letters.issue} />}

      <ErSections c={c} />
    </div>
  )
}

/** Which button name a request path belongs to, so the confirmation can use its label. */
function labelFor(path: string): string {
  const map: Record<string, string> = {
    assign: "ASSIGN", parties: "PARTIES", confidential: "CONFIDENTIAL", investigate: "INVESTIGATE", withdraw: "WITHDRAW", close: "CLOSE", informal: "INFORMAL",
    notice: "NOTICE", hearing: "HEARING", decision: "DECIDE", "grievance-decision": "DECIDE", "incapacity-decision": "DECIDE", communicate: "COMMUNICATE",
    appeal: "APPEAL", "appeal-decision": "DECIDE_APPEAL", "grievance-escalation-decision": "DECIDE_APPEAL", suspend: "SUSPEND", "suspension/end": "END_SUSPENSION",
    steps: "STEP", review: "REVIEW", "return-to-investigation": "RETURN_TO_INVESTIGATION",
  }
  return map[path] ?? ""
}
