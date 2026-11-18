// src/pages/supply-chain/AccessTab.tsx — which roles hold which Supply Chain permissions, and which approvals need a second person.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { ShieldCheck } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { TH, TD, ErrBox, Spinner, EmptyState, Banner, filterPill } from "./scm.shared"
import { asList, errorText, scmPermissionGroups, effectivePermissions, permissionIds, realEdits, revokeWarning, SECOND_PERSON_PERMISSIONS, SECOND_PERSON_NOTE } from "./scmOps"
import { Panel, Pill } from "./scmUi"
import { ApprovalLimitsPanel } from "./ApprovalLimitsPanel"

interface Role { id: string; name: string; description: string; permissions: string[]; userCount: number }
interface Perm { id: string; name: string; description: string }
const ID = "/api/v1/identity"
const second = new Set<string>(SECOND_PERSON_PERMISSIONS)

export function AccessTab() {
  const qc = useQueryClient()
  const canManage = usePermissions(["ROLE_MANAGE"])
  const [edits, setEdits] = useState<Record<string, Record<string, boolean>>>({}) // roleId → permission → granted
  const [err, setErr] = useState(""); const [saved, setSaved] = useState("")

  const roles = useQuery({ queryKey: ["team-roles"], queryFn: () => apiClient.get(`${ID}/roles`).then(r => asList<Role>(r)) })
  const perms = useQuery({ queryKey: ["team-permissions"], queryFn: () => apiClient.get(`${ID}/permissions`).then(r => asList<Perm>(r)) })
  const save = useMutation({
    mutationFn: ({ role, names }: { role: Role; names: string[] }) => apiClient.put(`${ID}/roles/${role.id}/permissions`, permissionIds(names, Object.fromEntries((perms.data ?? []).map(p => [p.name, p.id])))),
    onSuccess: (_r, { role }) => { setEdits(e => { const n = { ...e }; delete n[role.id]; return n }); setErr(""); setSaved(`${role.name} updated.`); qc.invalidateQueries({ queryKey: ["team-roles"] }) },
    onError: e => { setSaved(""); setErr(errorText(e)) },
  })

  if (roles.isLoading || perms.isLoading) return <Spinner />
  if (roles.isError || perms.isError) return <div style={{ padding: 20 }}><ErrBox msg={errorText(roles.error ?? perms.error, "Could not load roles. You may not have permission to view them.")} /></div>
  const groups = scmPermissionGroups(perms.data ?? [])
  const rs = roles.data ?? []
  const has = (r: Role, p: string) => { const e = edits[r.id]?.[p]; return e !== undefined ? e : r.permissions.includes(p) }
  const toggle = (r: Role, p: string, on: boolean) => { setSaved(""); setEdits(e => ({ ...e, [r.id]: { ...(e[r.id] ?? {}), [p]: on } })) }
  const dirty = rs.filter(r => Object.keys(realEdits(r.permissions, edits[r.id] ?? {})).length > 0)

  return (
    <div style={{ padding: 20, display: "grid", gap: 14 }}>
      <Banner variant="info">Approval is by permission, a second-person rule on sensitive steps, and (once switched on below) a limit by amount.</Banner>
      <ApprovalLimitsPanel />
      {err && <ErrBox msg={err} />}{saved && <Banner variant="info">{saved}</Banner>}

      <Panel title="Approvals that need a second person">
        <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 6, fontSize: 13 }}>
          {SECOND_PERSON_PERMISSIONS.map(p => <li key={p}><Pill tone="warn">{p}</Pill> <span style={{ color: "var(--hf-text-tertiary)" }}>{SECOND_PERSON_NOTE[p]}</span></li>)}
        </ul>
      </Panel>

      {rs.length === 0 || groups.length === 0 ? <EmptyState icon={ShieldCheck} title="No roles" sub="Roles and their Supply Chain permissions appear here." /> : (
        <div style={{ overflowX: "auto", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12 }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={TH}>Permission</th>{rs.map(r => <th key={r.id} style={{ ...TH, textAlign: "center", textTransform: "none" }}>{r.name}<div style={{ fontWeight: 400 }}>{r.userCount} user{r.userCount === 1 ? "" : "s"}</div></th>)}</tr></thead>
            <tbody>{groups.map(g => [
              <tr key={g.area}><td colSpan={rs.length + 1} style={{ ...TD, background: "var(--hf-surface-sunken)", fontWeight: 700, fontSize: 12 }}>{g.area}</td></tr>,
              ...g.items.map(p => (
                <tr key={p.id} style={{ borderTop: "1px solid var(--hf-border)" }}>
                  <td style={TD}><code style={{ fontSize: 12 }}>{p.name}</code>{second.has(p.name) && <> <Pill tone="warn">2nd person</Pill></>}<div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{p.description}</div></td>
                  {rs.map(r => <td key={r.id} style={{ ...TD, textAlign: "center" }}>
                    <input type="checkbox" aria-label={`${r.name} ${p.name}`} disabled={!canManage || save.isPending} checked={has(r, p.name)} onChange={e => toggle(r, p.name, e.target.checked)} /></td>)}
                </tr>)),
            ])}</tbody>
          </table>
        </div>)}

      {dirty.length > 0 && (
        <Panel title="Unsaved changes">
          <div style={{ display: "grid", gap: 8 }}>{dirty.map(r => {
            const changes = realEdits(r.permissions, edits[r.id]); const warn = revokeWarning(r.name, changes, r.userCount)
            return (<div key={r.id} style={{ fontSize: 13 }}>
              <strong>{r.name}</strong>: {Object.entries(changes).map(([n, on]) => `${on ? "+" : "−"}${n}`).join(", ")}
              {warn && <Banner variant="warning">{warn}</Banner>}
              <div style={{ marginTop: 6, display: "flex", gap: 8 }}>
                <button style={filterPill(true)} disabled={save.isPending} onClick={() => save.mutate({ role: r, names: effectivePermissions(r.permissions, changes) })}>Save {r.name}</button>
                <button style={filterPill(false)} onClick={() => setEdits(e => { const n = { ...e }; delete n[r.id]; return n })}>Discard</button>
              </div></div>)
          })}</div>
        </Panel>)}
      {!canManage && <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>You can view roles but not change them.</div>}
    </div>
  )
}
