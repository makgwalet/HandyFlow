// src/pages/customers/ScoringRoutingPanel.tsx
// Two lists of rules an account manager keeps: what makes a lead hot (scoring), and who gets new leads (routing). The server holds the rules; this sends and shows.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { errorMessage, unwrapList } from "./crmApi"
import { ConditionEditor } from "./ConditionEditor"
import {
  EMPTY_ROUTING, EMPTY_SCORING, movedOrder, routeResultText, routingBody, routingProblem, scoringBody, scoringProblem, toRoutingForm, toScoringForm,
  type RoutingForm, type RoutingRule, type ScoringForm, type ScoringRule,
} from "./rules.logic"
import { Loading, ErrorNote } from "./CrmKit"

const field: React.CSSProperties = { padding: "6px 9px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13 }
const ghost: React.CSSProperties = { ...field, cursor: "pointer" }
const small: React.CSSProperties = { ...ghost, padding: "3px 8px", fontSize: 12 }
interface UserRow { id: string; firstName?: string; lastName?: string; email?: string }
const userName = (u: UserRow) => [u.firstName, u.lastName].filter(Boolean).join(" ") || u.email || "User"

export default function ScoringRoutingPanel() {
  const qc = useQueryClient(), canEdit = usePermission("CUSTOMER_UPDATE")
  const [error, setError] = useState<string | null>(null), [note, setNote] = useState<string | null>(null)
  const [sEdit, setSEdit] = useState<string | "new" | null>(null), [sForm, setSForm] = useState<ScoringForm>(EMPTY_SCORING)
  const [rEdit, setREdit] = useState<string | "new" | null>(null), [rForm, setRForm] = useState<RoutingForm>(EMPTY_ROUTING)
  const scoring = useQuery({ queryKey: ["crm", "scoring-rules"], queryFn: async () => unwrapList<ScoringRule>(await apiClient.get("/api/v1/crm/scoring/rules")) })
  const routing = useQuery({ queryKey: ["crm", "routing-rules"], queryFn: async () => unwrapList<RoutingRule>(await apiClient.get("/api/v1/crm/routing/rules")) })
  const users = useQuery({ queryKey: ["crm", "task-users"], retry: false, queryFn: async () => unwrapList<UserRow>(await apiClient.get("/api/v1/identity/users", { params: { size: 200 } })) })
  const names = new Map((users.data ?? []).map(u => [u.id, userName(u)]))
  const fail = (e: unknown) => setError(errorMessage(e, "Something went wrong. Try again."))
  const doneS = () => { setError(null); setSEdit(null); qc.invalidateQueries({ queryKey: ["crm", "scoring-rules"] }); qc.invalidateQueries({ queryKey: ["crm", "scores"] }) }
  const doneR = () => { setError(null); setREdit(null); qc.invalidateQueries({ queryKey: ["crm", "routing-rules"] }) }
  const saveS = useMutation({ mutationFn: () => sEdit === "new" ? apiClient.post("/api/v1/crm/scoring/rules", scoringBody(sForm)) : apiClient.put(`/api/v1/crm/scoring/rules/${sEdit}`, scoringBody(sForm)), onSuccess: doneS, onError: fail })
  const toggleS = useMutation({ mutationFn: (r: ScoringRule) => apiClient.put(`/api/v1/crm/scoring/rules/${r.id}`, scoringBody({ ...toScoringForm(r), active: !r.active })), onSuccess: doneS, onError: fail })
  const delS = useMutation({ mutationFn: (id: string) => apiClient.delete(`/api/v1/crm/scoring/rules/${id}`), onSuccess: doneS, onError: fail })
  const saveR = useMutation({ mutationFn: () => rEdit === "new" ? apiClient.post("/api/v1/crm/routing/rules", routingBody(rForm)) : apiClient.put(`/api/v1/crm/routing/rules/${rEdit}`, routingBody(rForm)), onSuccess: doneR, onError: fail })
  const toggleR = useMutation({ mutationFn: (r: RoutingRule) => apiClient.put(`/api/v1/crm/routing/rules/${r.id}`, routingBody({ ...toRoutingForm(r), active: !r.active })), onSuccess: doneR, onError: fail })
  const delR = useMutation({ mutationFn: (id: string) => apiClient.delete(`/api/v1/crm/routing/rules/${id}`), onSuccess: doneR, onError: fail })
  const order = useMutation({ mutationFn: (ids: string[]) => apiClient.put("/api/v1/crm/routing/order", { ids }), onSuccess: doneR, onError: fail })
  const run = useMutation({ mutationFn: async () => unwrapRun(await apiClient.post("/api/v1/crm/routing/run")), onSuccess: r => { setError(null); setNote(routeResultText(r)); qc.invalidateQueries({ queryKey: ["crm", "pipeline"] }) }, onError: fail })

  const sProblem = sEdit ? scoringProblem(sForm) : null, rProblem = rEdit ? routingProblem(rForm) : null
  const rules = routing.data ?? [], ids = rules.map(r => r.id)
  const busy = saveS.isPending || toggleS.isPending || delS.isPending || saveR.isPending || toggleR.isPending || delR.isPending || order.isPending || run.isPending

  return (
    <div style={{ display: "grid", gap: 22, fontSize: 13 }}>
      {error && <div role="alert" style={{ color: "var(--hf-danger-text)" }}>{error}</div>}
      <section aria-label="Scoring rules">
        <h3 style={{ margin: "0 0 4px", fontSize: 15 }}>Lead scoring</h3>
        <p style={{ margin: "0 0 8px", color: "var(--hf-text-muted)" }}>A lead's score is the points of every rule it matches, kept between 0 and 100. 60 and over is hot, 30 and over is warm.</p>
        {scoring.isLoading && <Loading />}{scoring.isError && <ErrorNote>Couldn't load the scoring rules.</ErrorNote>}
        {scoring.data && scoring.data.length === 0 && <p style={{ color: "var(--hf-text-muted)" }}>No scoring rules yet.</p>}
        <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 6 }}>
          {(scoring.data ?? []).map(r => (
            <li key={r.id} aria-label={r.name} style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap", opacity: r.active ? 1 : 0.55 }}>
              <strong style={{ minWidth: 150 }}>{r.name}</strong><span style={{ color: "var(--hf-text-muted)" }}>{r.description}</span>
              <span style={{ fontWeight: 700, color: r.points > 0 ? "var(--hf-success-text)" : "var(--hf-danger-text)" }}>{r.points > 0 ? "+" : ""}{r.points}</span>
              {!r.active && <span>(off)</span>}
              {canEdit && <span style={{ display: "flex", gap: 6 }}>
                <button type="button" style={small} aria-label={`Edit ${r.name}`} onClick={() => { setSForm(toScoringForm(r)); setSEdit(r.id); setError(null) }}>Edit</button>
                <button type="button" style={small} aria-label={`${r.active ? "Turn off" : "Turn on"} ${r.name}`} disabled={busy} onClick={() => toggleS.mutate(r)}>{r.active ? "Turn off" : "Turn on"}</button>
                <button type="button" style={small} aria-label={`Delete ${r.name}`} disabled={busy} onClick={() => delS.mutate(r.id)}>Delete</button></span>}
            </li>))}
        </ul>
        {canEdit && !sEdit && <button type="button" style={{ ...ghost, marginTop: 8 }} onClick={() => { setSForm(EMPTY_SCORING); setSEdit("new"); setError(null) }}>Add scoring rule</button>}
        {canEdit && sEdit && (
          <form aria-label="Scoring rule" onSubmit={e => { e.preventDefault(); if (!sProblem) saveS.mutate() }} style={{ display: "grid", gap: 8, maxWidth: 520, marginTop: 8, padding: 10, border: "1px solid var(--hf-border)", borderRadius: 10 }}>
            <label>Name <input aria-label="Rule name" style={{ ...field, width: "100%" }} value={sForm.name} onChange={e => setSForm({ ...sForm, name: e.target.value })} /></label>
            <ConditionEditor label="Scoring condition" value={sForm.condition} onChange={c => setSForm({ ...sForm, condition: c })} />
            <label>Points (-100 to 100) <input aria-label="Points" inputMode="numeric" style={{ ...field, width: 90 }} value={sForm.points} onChange={e => setSForm({ ...sForm, points: e.target.value })} /></label>
            {sForm.name.trim() && sProblem && <span style={{ color: "var(--hf-danger-text)" }}>{sProblem}</span>}
            <div style={{ display: "flex", gap: 6 }}><button type="submit" style={{ ...ghost, fontWeight: 700 }} disabled={!!sProblem || busy}>{saveS.isPending ? "Saving…" : "Save rule"}</button><button type="button" style={ghost} onClick={() => { setSEdit(null); setError(null) }}>Cancel</button></div>
          </form>)}
      </section>

      <section aria-label="Routing rules">
        <h3 style={{ margin: "0 0 4px", fontSize: 15 }}>Lead routing</h3>
        <p style={{ margin: "0 0 8px", color: "var(--hf-text-muted)" }}>Rules are tried from the top; the first one a lead fits hands it to its people in turn. New leads from an import or web form are routed automatically. Leads added by a person stay with that person.</p>
        {routing.isLoading && <Loading />}{routing.isError && <ErrorNote>Couldn't load the routing rules.</ErrorNote>}
        {routing.data && rules.length === 0 && <p style={{ color: "var(--hf-text-muted)" }}>No routing rules yet.</p>}
        <ol style={{ margin: 0, paddingLeft: 20, display: "grid", gap: 6 }}>
          {rules.map(r => (
            <li key={r.id} aria-label={r.name} style={{ opacity: r.active ? 1 : 0.55 }}>
              <strong>{r.name}</strong> <span style={{ color: "var(--hf-text-muted)" }}>— {r.description} → {r.assigneeIds.map(i => names.get(i) ?? "Colleague").join(", ")}</span>{!r.active && " (off)"}
              {canEdit && <span style={{ display: "inline-flex", gap: 6, marginLeft: 8 }}>
                <button type="button" style={small} aria-label={`Move ${r.name} up`} disabled={busy || r.id === ids[0]} onClick={() => order.mutate(movedOrder(ids, r.id, -1))}>Up</button>
                <button type="button" style={small} aria-label={`Move ${r.name} down`} disabled={busy || r.id === ids[ids.length - 1]} onClick={() => order.mutate(movedOrder(ids, r.id, 1))}>Down</button>
                <button type="button" style={small} aria-label={`Edit routing ${r.name}`} onClick={() => { setRForm(toRoutingForm(r)); setREdit(r.id); setError(null) }}>Edit</button>
                <button type="button" style={small} aria-label={`${r.active ? "Turn off" : "Turn on"} routing ${r.name}`} disabled={busy} onClick={() => toggleR.mutate(r)}>{r.active ? "Turn off" : "Turn on"}</button>
                <button type="button" style={small} aria-label={`Delete routing ${r.name}`} disabled={busy} onClick={() => delR.mutate(r.id)}>Delete</button></span>}
            </li>))}
        </ol>
        {canEdit && !rEdit && (
          <div style={{ display: "flex", gap: 8, marginTop: 8, flexWrap: "wrap", alignItems: "center" }}>
            <button type="button" style={ghost} onClick={() => { setRForm(EMPTY_ROUTING); setREdit("new"); setError(null) }}>Add routing rule</button>
            <button type="button" style={ghost} disabled={busy || rules.length === 0} onClick={() => { setNote(null); run.mutate() }}>{run.isPending ? "Routing…" : "Route unassigned leads now"}</button>
            {note && <span role="status">{note}</span>}
          </div>)}
        {canEdit && rEdit && (
          <form aria-label="Routing rule" onSubmit={e => { e.preventDefault(); if (!rProblem) saveR.mutate() }} style={{ display: "grid", gap: 8, maxWidth: 520, marginTop: 8, padding: 10, border: "1px solid var(--hf-border)", borderRadius: 10 }}>
            <label>Name <input aria-label="Rule name" style={{ ...field, width: "100%" }} value={rForm.name} onChange={e => setRForm({ ...rForm, name: e.target.value })} /></label>
            <ConditionEditor label="Routing condition" optional value={rForm.condition} onChange={c => setRForm({ ...rForm, condition: c })} />
            <fieldset style={{ border: "1px solid var(--hf-border)", borderRadius: 8 }}><legend>Give leads to</legend>
              {users.isError && <div>Couldn't load the people list.</div>}
              {(users.data ?? []).map(u => <label key={u.id} style={{ display: "block" }}><input type="checkbox" checked={rForm.assigneeIds.includes(u.id)} onChange={e => setRForm({ ...rForm, assigneeIds: e.target.checked ? [...rForm.assigneeIds, u.id] : rForm.assigneeIds.filter(x => x !== u.id) })} /> {userName(u)}</label>)}
            </fieldset>
            {rForm.name.trim() && rProblem && <span style={{ color: "var(--hf-danger-text)" }}>{rProblem}</span>}
            <div style={{ display: "flex", gap: 6 }}><button type="submit" style={{ ...ghost, fontWeight: 700 }} disabled={!!rProblem || busy}>{saveR.isPending ? "Saving…" : "Save rule"}</button><button type="button" style={ghost} onClick={() => { setREdit(null); setError(null) }}>Cancel</button></div>
          </form>)}
      </section>
    </div>
  )
}

function unwrapRun(r: { data: unknown }): { routed: number; left: number } {
  const o = r.data as { data?: { routed?: number; left?: number } } | null
  const d = o && "data" in o && o.data ? o.data : (r.data as { routed?: number; left?: number })
  return { routed: d?.routed ?? 0, left: d?.left ?? 0 }
}
