import { useEffect, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { btnGhost, btnPrimary, card, Chip } from "../warehousing/whseUi"
import AlertRecipients from "./AlertRecipients"
import { DIVISION_CHOICES, describeScope, toggle, userLabel, type UserOption, type UserScope } from "./scopeOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const msg = (e: any, fb: string) => e?.response?.data?.message ?? fb

export default function AccessLimitsTab() {
  const canManage = usePermissions(["FUEL_CONFIGURATION_MANAGE"])
  const qc = useQueryClient()
  const scopes = useQuery<UserScope[]>({ queryKey: ["fuel-scopes"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/scopes")) ?? [] })
  const depots = useQuery<{ id: string; name: string }[]>({ queryKey: ["fuel-scope-depots"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/depots")) ?? [] })
  const users = useQuery<UserOption[]>({
    queryKey: ["fuel-scope-users"], retry: false,
    queryFn: async () => {
      const raw = unwrap(await apiClient.get("/api/v1/identity/users"))
      const rows: any[] = Array.isArray(raw) ? raw : raw?.content ?? []
      return rows.map(u => ({ id: u.id, name: `${u.firstName ?? ""} ${u.lastName ?? ""}`.trim() + (u.email ? ` · ${u.email}` : "") }))
    },
  })
  const [userId, setUserId] = useState("")
  const [divisions, setDivisions] = useState<string[]>([])
  const [depotIds, setDepotIds] = useState<string[]>([])
  const [err, setErr] = useState("")
  const depotNames = Object.fromEntries((depots.data ?? []).map(d => [d.id, d.name]))
  const known = users.data ?? []
  const choices = [...known, ...(scopes.data ?? []).filter(s => !known.some(u => u.id === s.userId)).map(s => ({ id: s.userId, name: userLabel(s.userId, known) }))]

  useEffect(() => {
    const s = (scopes.data ?? []).find(x => x.userId === userId)
    setDivisions(s?.divisions ?? []); setDepotIds(s?.depotIds ?? [])
  }, [userId, scopes.data])

  const save = useMutation({
    mutationFn: (b: { divisions: string[]; depotIds: string[] }) => apiClient.put(`/api/v1/fuel/scopes/users/${userId}`, b),
    onSuccess: () => { setErr(""); qc.invalidateQueries({ queryKey: ["fuel-scopes"] }) },
    onError: (e: any) => setErr(msg(e, "Could not save the limits")),
  })

  return (
    <div>
      <div style={{ ...card, padding: 16 }}>
        <h3 style={{ margin: "0 0 4px", fontSize: 14 }}>Access limits</h3>
        <p style={{ margin: "0 0 12px", fontSize: 12.5, color: "var(--hf-text-muted)" }}>
          Limit a person to some divisions or depots. People with no limits can use everything their role allows. Limits are checked on the server, so hiding a screen is not the only protection.
        </p>
        {(scopes.data ?? []).length === 0
          ? <p style={{ fontSize: 13 }}>Nobody is limited yet.</p>
          : (scopes.data ?? []).map(s => (
            <div key={s.userId} style={{ display: "flex", gap: 10, alignItems: "center", padding: "8px 0", borderTop: "1px solid var(--hf-border)" }}>
              <div style={{ flex: 1 }}>
                <strong style={{ fontSize: 13 }}>{userLabel(s.userId, known)}</strong>
                <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{describeScope(s, depotNames)}</div>
              </div>
              {canManage && <button style={btnGhost} onClick={() => setUserId(s.userId)} aria-label={`Edit limits for ${userLabel(s.userId, known)}`}>Edit</button>}
            </div>
          ))}
      </div>

      {canManage && (
        <div style={{ ...card, padding: 16, marginTop: 14 }}>
          <h3 style={{ margin: "0 0 8px", fontSize: 14 }}>Set limits for a person</h3>
          <label style={{ fontSize: 12.5, display: "block", marginBottom: 8 }}>Person
            <select aria-label="Person" value={userId} onChange={e => setUserId(e.target.value)} style={{ display: "block", marginTop: 4, minWidth: 260 }}>
              <option value="">Choose a person…</option>
              {choices.map(u => <option key={u.id} value={u.id}>{u.name}</option>)}
            </select>
          </label>
          {users.isError && <p style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>The list of people could not be loaded, so only people who already have limits are shown.</p>}
          {userId && (
            <>
              <fieldset style={{ border: "none", padding: 0, margin: "8px 0" }}>
                <legend style={{ fontSize: 12.5, fontWeight: 600 }}>Divisions they may use</legend>
                {DIVISION_CHOICES.map(d => (
                  <label key={d.id} style={{ display: "block", fontSize: 13 }}>
                    <input type="checkbox" checked={divisions.includes(d.id)} onChange={() => setDivisions(toggle(divisions, d.id))} /> {d.label}
                  </label>
                ))}
                <span style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>Leave all unticked for no division limit.</span>
              </fieldset>
              <fieldset style={{ border: "none", padding: 0, margin: "8px 0" }}>
                <legend style={{ fontSize: 12.5, fontWeight: 600 }}>Depots they may use</legend>
                {(depots.data ?? []).length === 0 && <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>No depots set up.</span>}
                {(depots.data ?? []).map(d => (
                  <label key={d.id} style={{ display: "block", fontSize: 13 }}>
                    <input type="checkbox" checked={depotIds.includes(d.id)} onChange={() => setDepotIds(toggle(depotIds, d.id))} /> {d.name}
                  </label>
                ))}
                <span style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>Leave all unticked for no depot limit. A depot limit hides other depots and their tanks, and blocks actions on them.</span>
              </fieldset>
              {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{err}</p>}
              <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                <button style={btnPrimary} disabled={save.isPending} onClick={() => save.mutate({ divisions, depotIds })}>Save limits</button>
                {divisions.length === 0 && depotIds.length === 0 && <Chip bg="var(--hf-surface-2)" fg="var(--hf-text-muted)">No limits</Chip>}
              </div>
            </>
          )}
        </div>
      )}
      {canManage && <AlertRecipients users={known} />}
    </div>
  )
}
