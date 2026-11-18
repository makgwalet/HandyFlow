// src/pages/supply-chain/ApprovalLimitsPanel.tsx — who may approve how much, per document type, and the switch that enforces it.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { inp, TH, TD, Field, ErrBox, Spinner, Banner, filterPill } from "./scm.shared"
import { asList, asOne, errorText } from "./scmOps"
import { Panel, Pill } from "./scmUi"
import { LIMIT_DOCS, docLabel, emptyLimit, limitProblem, limitBody, limitText, authorityText, enforceHelp, type LimitForm } from "./scmLimits"

interface Row { id: string; subjectType: string; subjectId: string; subjectName: string | null; documentType: string; maxAmount: number | null }
interface View { mode: string; enforceReady: boolean; limits: Row[] }
interface Mine { mode: string; authorities: { documentType: string; granted: boolean; unlimited: boolean; maxAmount: number | null }[] }
interface Role { id: string; name: string }
interface User { id: string; firstName?: string; lastName?: string; email?: string }

const BASE = "/api/v1/supply-chain/approval-limits"

export function ApprovalLimitsPanel() {
  const qc = useQueryClient()
  const canManage = usePermissions(["SCM_APPROVAL_LIMIT_MANAGE", "SCM_ADMIN"])
  const [form, setForm] = useState<LimitForm>(emptyLimit())
  const [err, setErr] = useState("")

  const view = useQuery({ queryKey: ["scm-approval-limits"], queryFn: () => apiClient.get(BASE).then(r => asOne<View>(r)) })
  const mine = useQuery({ queryKey: ["scm-approval-limits-mine"], queryFn: () => apiClient.get(`${BASE}/mine`).then(r => asOne<Mine>(r)) })
  const roles = useQuery({ queryKey: ["team-roles"], queryFn: () => apiClient.get("/api/v1/identity/roles").then(r => asList<Role>(r)) })
  const users = useQuery({ queryKey: ["scm-limit-users"], enabled: canManage && form.subjectType === "USER",
    queryFn: () => apiClient.get("/api/v1/identity/users").then(r => asList<User>(r)) })

  const refresh = () => { qc.invalidateQueries({ queryKey: ["scm-approval-limits"] }); qc.invalidateQueries({ queryKey: ["scm-approval-limits-mine"] }) }
  const save = useMutation({ mutationFn: () => apiClient.put(BASE, limitBody(form)), onSuccess: () => { setErr(""); setForm(emptyLimit()); refresh() }, onError: e => setErr(errorText(e)) })
  const remove = useMutation({ mutationFn: (id: string) => apiClient.delete(`${BASE}/${id}`), onSuccess: () => { setErr(""); refresh() }, onError: e => setErr(errorText(e)) })
  const setMode = useMutation({ mutationFn: (mode: string) => apiClient.put(`${BASE}/mode`, { mode }), onSuccess: () => { setErr(""); refresh() }, onError: e => setErr(errorText(e)) })

  const submit = () => { const p = limitProblem(form); if (p) { setErr(p); return } setErr(""); save.mutate() }
  const v = view.data
  const enforcing = v?.mode === "ENFORCE"
  const subjects = form.subjectType === "ROLE"
    ? (roles.data ?? []).map(r => ({ id: r.id, name: r.name }))
    : (users.data ?? []).map(u => ({ id: u.id, name: `${u.firstName ?? ""} ${u.lastName ?? ""}`.trim() || u.email || u.id }))

  return (
    <Panel title="Approval limits by amount" right={v && <Pill tone={enforcing ? "ok" : "neutral"}>{enforcing ? "Enforced" : "Not enforced"}</Pill>}>
      {view.isLoading ? <Spinner /> : view.isError ? <ErrBox msg={errorText(view.error, "Could not load approval limits")} /> : v && (
        <div style={{ display: "grid", gap: 12 }}>
          <Banner variant={enforcing ? "info" : v.enforceReady ? "warning" : "info"}>{enforceHelp(v.mode, v.enforceReady)}</Banner>
          {err && <ErrBox msg={err} />}

          {mine.data?.authorities && (
            <div style={{ fontSize: 13 }}>
              <strong>Your authority</strong>
              <ul style={{ margin: "6px 0 0", padding: 0, listStyle: "none", display: "grid", gap: 2 }}>
                {mine.data.authorities.map(a => <li key={a.documentType}>{docLabel(a.documentType)}: {authorityText(a)}</li>)}
              </ul>
            </div>
          )}

          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr>{["Who", "Document", "Limit", ""].map(h => <th key={h} style={TH}>{h}</th>)}</tr></thead>
            <tbody>
              {(v.limits ?? []).length === 0 && <tr><td style={TD} colSpan={4}>No limits set yet.</td></tr>}
              {(v.limits ?? []).map(l => (
                <tr key={l.id} style={{ borderTop: "1px solid var(--hf-border)" }}>
                  <td style={TD}>{l.subjectName ?? l.subjectId} <small>({l.subjectType === "ROLE" ? "role" : "person"})</small></td>
                  <td style={TD}>{docLabel(l.documentType)}</td>
                  <td style={TD}>{limitText(l.maxAmount)}</td>
                  <td style={TD}>{canManage && <button style={filterPill(false)} disabled={remove.isPending} onClick={() => remove.mutate(l.id)}>Remove</button>}</td>
                </tr>))}
            </tbody>
          </table>

          {canManage ? (
            <>
              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))", gap: 10 }}>
                <Field label="Applies to">
                  <select style={inp} aria-label="Subject type" value={form.subjectType} onChange={e => setForm({ ...form, subjectType: e.target.value as "USER" | "ROLE", subjectId: "" })}>
                    <option value="ROLE">A role</option><option value="USER">A person</option>
                  </select>
                </Field>
                <Field label={form.subjectType === "ROLE" ? "Role" : "Person"}>
                  <select style={inp} aria-label="Subject" value={form.subjectId} onChange={e => setForm({ ...form, subjectId: e.target.value })}>
                    <option value="">Choose…</option>{subjects.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
                  </select>
                </Field>
                <Field label="Document">
                  <select style={inp} aria-label="Document type" value={form.documentType} onChange={e => setForm({ ...form, documentType: e.target.value })}>
                    {LIMIT_DOCS.map(d => <option key={d.value} value={d.value}>{d.label}</option>)}
                  </select>
                </Field>
                <Field label="Up to (R)">
                  <input style={inp} aria-label="Limit amount" disabled={form.unlimited} value={form.amount} onChange={e => setForm({ ...form, amount: e.target.value })} />
                </Field>
              </div>
              <label style={{ fontSize: 13 }}><input type="checkbox" checked={form.unlimited} onChange={e => setForm({ ...form, unlimited: e.target.checked })} /> Unlimited</label>
              <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                <button style={filterPill(true)} disabled={save.isPending} onClick={submit}>Save limit</button>
                {enforcing
                  ? <button style={filterPill(false)} disabled={setMode.isPending} onClick={() => setMode.mutate("OFF")}>Stop enforcing limits</button>
                  : <button style={filterPill(false)} disabled={setMode.isPending || !v.enforceReady} onClick={() => setMode.mutate("ENFORCE")}>Enforce limits</button>}
              </div>
              <p style={{ fontSize: 12, color: "var(--hf-text-faint)", margin: 0 }}>
                A person's own limit for a document beats their role limits; otherwise the most generous role counts. A document equal to the limit can be approved. With no limit at all, a person cannot approve once limits are enforced.
              </p>
            </>
          ) : <p style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>Only an administrator can change approval limits.</p>}
        </div>
      )}
    </Panel>
  )
}
