import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { useAuth } from "../../hooks/useAuth"
import { usePermission } from "../../hooks/usePermission"

export interface ActionPlan {
  id: string; findingId: string; description: string; owner: string | null; ownerName: string | null; dueDate: string
  status: "OPEN" | "COMPLETED" | "VALIDATED" | "CANCELLED"; overdue: boolean
  completionNote: string | null; validationNote: string | null; timesReturned: number; cancelReason: string | null
}
interface UserOption { id: string; firstName: string; lastName: string }

const MIN_NOTE = 10
const listOf = <T,>(d: any): T[] => (Array.isArray(d) ? d : d?.data ?? d?.content ?? [])
const errorOf = (e: any, fallback: string) => e?.response?.data?.message ?? fallback

const STATUS_TEXT: Record<ActionPlan["status"], string> = { OPEN: "To do", COMPLETED: "Awaiting check", VALIDATED: "Checked", CANCELLED: "Cancelled" }

/** What management will do about one finding, and whether an auditor has checked it. */
export default function ActionPlansPanel({ findingId, findingClosed, users }: { findingId: string; findingClosed: boolean; users: UserOption[] }) {
  const qc = useQueryClient()
  const { user } = useAuth()
  const myId: string | null = (user as any)?.userId ?? (user as any)?.id ?? null
  const canManageAudit = usePermission("AUDIT_MANAGE")   // both hooks are always called; `||` between them would skip the second
  const canAdminAudit = usePermission("AUDIT_ADMIN")
  const canManage = canManageAudit || canAdminAudit
  const [adding, setAdding] = useState(false)
  const [form, setForm] = useState({ description: "", owner: "", dueDate: "" })
  const [error, setError] = useState("")
  const key = ["ia-action-plans", findingId]

  const { data: plans = [] } = useQuery<ActionPlan[]>({
    queryKey: key,
    queryFn: async () => listOf<ActionPlan>((await apiClient.get(`/api/v1/internal-audit/findings/${findingId}/action-plans`)).data),
  })
  const done = () => { qc.invalidateQueries({ queryKey: key }); qc.invalidateQueries({ queryKey: ["ia-findings"] }); setError("") }
  const fail = (fallback: string) => (e: any) => setError(errorOf(e, fallback))

  const create = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/internal-audit/findings/${findingId}/action-plans`, { description: form.description.trim(), owner: form.owner || null, dueDate: form.dueDate }),
    onSuccess: () => { done(); setAdding(false); setForm({ description: "", owner: "", dueDate: "" }) },
    onError: fail("Could not add the action"),
  })
  const act = useMutation({
    mutationFn: ({ id, verb, note }: { id: string; verb: "complete" | "validate" | "return" | "cancel"; note: string }) =>
      apiClient.post(`/api/v1/internal-audit/action-plans/${id}/${verb}`, { note }),
    onSuccess: done,
    onError: fail("Could not update the action"),
  })
  const withNote = (id: string, verb: "complete" | "validate" | "return" | "cancel", ask: string) => {
    const note = window.prompt(`${ask} (at least ${MIN_NOTE} characters)`)
    if (note && note.trim().length >= MIN_NOTE) act.mutate({ id, verb, note: note.trim() })
    else if (note) setError(`A note of at least ${MIN_NOTE} characters is needed.`)
  }

  const btn: React.CSSProperties = { padding: "2px 8px", borderRadius: 6, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", fontSize: 10.5, fontWeight: 600, cursor: "pointer", color: "var(--hf-text-muted)" }
  const field: React.CSSProperties = { padding: "6px 8px", border: "1.5px solid var(--hf-border)", borderRadius: 6, fontSize: 12, background: "var(--hf-surface)" }

  return (
    <div style={{ marginTop: 10, paddingTop: 8, borderTop: "1px dashed var(--hf-border)" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 6 }}>
        <span style={{ fontSize: 10.5, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase" }}>Actions — {plans.length}</span>
        {canManage && !findingClosed && <button style={btn} onClick={() => setAdding(a => !a)}>{adding ? "Cancel" : "Add action"}</button>}
      </div>

      {adding && (
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginBottom: 8 }}>
          <input aria-label="What will be done" placeholder="What will be done" value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} style={{ ...field, flex: 2, minWidth: 180 }} />
          <select aria-label="Action owner" value={form.owner} onChange={e => setForm(f => ({ ...f, owner: e.target.value }))} style={field}>
            <option value="">No owner</option>
            {users.map(u => <option key={u.id} value={u.id}>{u.firstName} {u.lastName}</option>)}
          </select>
          <input aria-label="Action due date" type="date" value={form.dueDate} onChange={e => setForm(f => ({ ...f, dueDate: e.target.value }))} style={field} />
          <button style={{ ...btn, background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none" }} disabled={!form.description.trim() || !form.dueDate || create.isPending} onClick={() => create.mutate()}>Save action</button>
        </div>
      )}

      {plans.length === 0 && !adding && <div style={{ fontSize: 11.5, color: "var(--hf-text-faint)" }}>No actions yet. A finding with actions can only be closed once every action has been checked.</div>}

      {plans.map(p => {
        const mine = !!myId && p.owner === myId
        return (
          <div key={p.id} style={{ display: "flex", justifyContent: "space-between", gap: 8, padding: "6px 0", fontSize: 12, borderTop: "1px solid var(--hf-border-subtle)" }}>
            <div style={{ minWidth: 0 }}>
              <div style={{ color: "var(--hf-text)" }}>{p.description}</div>
              <div style={{ color: p.overdue ? "var(--hf-danger-text)" : "var(--hf-text-faint)", fontSize: 11 }}>
                {STATUS_TEXT[p.status]}{p.timesReturned > 0 ? ` · returned ${p.timesReturned}×` : ""} · Due {p.dueDate}{p.overdue ? " · OVERDUE" : ""}{p.ownerName ? ` · ${p.ownerName}` : ""}
              </div>
              {p.completionNote && p.status === "COMPLETED" && <div style={{ color: "var(--hf-info-text)", fontSize: 11 }}>Done: {p.completionNote}</div>}
              {p.validationNote && <div style={{ color: "var(--hf-text-faint)", fontSize: 11 }}>Auditor: {p.validationNote}</div>}
            </div>
            <div style={{ display: "flex", gap: 4, alignItems: "flex-start", flexShrink: 0 }}>
              {p.status === "OPEN" && (mine || canManage) && <button style={btn} onClick={() => withNote(p.id, "complete", "What was done?")}>Mark done</button>}
              {p.status === "COMPLETED" && canManage && !mine && <>
                <button style={btn} onClick={() => withNote(p.id, "validate", "What did you check?")}>Validate</button>
                <button style={btn} onClick={() => withNote(p.id, "return", "Why is it being returned?")}>Return</button>
              </>}
              {(p.status === "OPEN" || p.status === "COMPLETED") && canManage && <button style={btn} onClick={() => withNote(p.id, "cancel", "Why is this action cancelled?")}>Cancel</button>}
            </div>
          </div>
        )
      })}
      {error && <div role="alert" style={{ marginTop: 6, fontSize: 11.5, color: "var(--hf-danger-text)" }}>{error}</div>}
    </div>
  )
}
