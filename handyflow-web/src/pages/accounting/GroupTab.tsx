// src/pages/accounting/GroupTab.tsx
//
// Finance F26: a group of companies. Each company stays its own business with its own books. A company joins only by
// entering an invitation code from the group's parent, which lets the parent read that company's ACCOUNT TOTALS (never its
// journal lines) to consolidate. Leaving, or being removed, ends that access at once.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { knownGroupLines, mapProblem, ownershipText, ruleProblem, type AccountRef, type Elimination, type GroupInfo, type Mapping } from "./group.logic"

interface Invite { id: string; label: string | null; ownershipPercent: number; expiresAt: string }
// "No group" comes back as data: null, so a null payload must stay null instead of falling back to the whole response.
const unwrap = (r: any) => (r.data && typeof r.data === "object" && "data" in r.data ? r.data.data : r.data)
const errorOf = (e: any) => e?.response?.data?.message ?? "Something went wrong. Please try again."
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 14, overflowX: "auto" }
const btn: React.CSSProperties = { padding: "7px 13px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", fontWeight: 600, fontSize: 12, cursor: "pointer" }
const field: React.CSSProperties = { padding: "7px 9px", border: "1px solid var(--hf-border)", borderRadius: 6, fontSize: 12, background: "var(--hf-surface)", color: "var(--hf-text)" }
const Err = ({ text }: { text: string | null }) => (text ? <div role="alert" style={{ color: "var(--hf-danger-text, #b91c1c)", fontSize: 12, marginTop: 6 }}>{text}</div> : null)

export default function GroupTab() {
  const qc = useQueryClient()
  const canAdmin = usePermission("ACCOUNTING_ADMIN")
  const [error, setError] = useState<string | null>(null)
  const [name, setName] = useState("")
  const [code, setCode] = useState("")

  const { data: group, isLoading } = useQuery<GroupInfo | null>({
    queryKey: ["acc-group"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/accounting/group")) ?? null,
  })
  const refresh = () => qc.invalidateQueries({ queryKey: ["acc-group"] })
  const create = useMutation({
    mutationFn: () => apiClient.post("/api/v1/accounting/group", { name: name.trim() }),
    onSuccess: () => { setError(null); setName(""); refresh() }, onError: e => setError(errorOf(e)),
  })
  const join = useMutation({
    mutationFn: () => apiClient.post("/api/v1/accounting/group/join", { code: code.trim() }),
    onSuccess: () => { setError(null); setCode(""); refresh() }, onError: e => setError(errorOf(e)),
  })
  const leave = useMutation({
    mutationFn: () => apiClient.post("/api/v1/accounting/group/leave"),
    onSuccess: () => { setError(null); refresh() }, onError: e => setError(errorOf(e)),
  })

  if (isLoading) return <p style={{ fontSize: 13 }}>Loading…</p>

  if (!group) {
    return (
      <div>
        <h2 style={{ marginTop: 0 }}>Company group</h2>
        <p style={{ fontSize: 13, color: "var(--hf-text-secondary)" }}>
          Link several companies you own to see them as one set of statements. Each company keeps its own books, VAT and users.
        </p>
        {canAdmin ? (
          <>
            <div style={card}>
              <h3 style={{ marginTop: 0 }}>Start a group with this company as the parent</h3>
              <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                <input aria-label="Group name" placeholder="Group name" value={name} onChange={e => setName(e.target.value)} style={{ ...field, width: 240 }} />
                <button style={btn} disabled={!name.trim() || create.isPending} onClick={() => create.mutate()}>Create group</button>
              </div>
            </div>
            <div style={card}>
              <h3 style={{ marginTop: 0 }}>Join a group</h3>
              <p style={{ fontSize: 12, color: "var(--hf-text-secondary)", marginTop: 0 }}>
                Joining lets the group's parent company read this company's account totals for consolidation. It cannot see individual transactions. You can leave at any time.
              </p>
              <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                <input aria-label="Invitation code" placeholder="XXXX-XXXX-XXXX-XXXX" value={code} onChange={e => setCode(e.target.value)} style={{ ...field, width: 240, fontFamily: "monospace" }} />
                <button style={btn} disabled={!code.trim() || join.isPending} onClick={() => join.mutate()}>Join group</button>
              </div>
            </div>
            <Err text={error} />
          </>
        ) : <p style={{ fontSize: 13 }}>Ask an accounting administrator to set up or join a group.</p>}
      </div>
    )
  }

  if (group.myRole === "SUBSIDIARY") {
    return (
      <div>
        <h2 style={{ marginTop: 0 }}>Company group</h2>
        <div style={card}>
          <p style={{ marginTop: 0, fontSize: 13 }}>This company is part of <b>{group.name}</b>, owned by <b>{group.parentName}</b>.</p>
          <p style={{ fontSize: 12, color: "var(--hf-text-secondary)" }}>{group.parentName} can read this company's account totals to consolidate. It cannot see individual transactions.</p>
          {canAdmin && <button style={btn} disabled={leave.isPending} onClick={() => { if (window.confirm(`Leave ${group.name}? ${group.parentName} will no longer see this company's figures.`)) leave.mutate() }}>Leave the group</button>}
          <Err text={error} />
        </div>
      </div>
    )
  }
  return <ParentView group={group} canAdmin={canAdmin} refresh={refresh} />
}

function ParentView({ group, canAdmin, refresh }: { group: GroupInfo; canAdmin: boolean; refresh: () => void }) {
  const qc = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [label, setLabel] = useState("")
  const [pct, setPct] = useState("100")
  const [newCode, setNewCode] = useState<string | null>(null)

  const { data: invites = [] } = useQuery<Invite[]>({
    queryKey: ["acc-group-invites"], enabled: canAdmin,
    queryFn: async () => unwrap(await apiClient.get("/api/v1/accounting/group/invites")) ?? [],
  })
  const invalidate = () => { refresh(); qc.invalidateQueries({ queryKey: ["acc-group-invites"] }) }
  const invite = useMutation({
    mutationFn: () => apiClient.post("/api/v1/accounting/group/invites", { label: label.trim() || null, ownershipPercent: Number(pct) }),
    onSuccess: r => { setError(null); setNewCode(unwrap(r).code); setLabel(""); invalidate() }, onError: e => setError(errorOf(e)),
  })
  const cancel = useMutation({ mutationFn: (id: string) => apiClient.delete(`/api/v1/accounting/group/invites/${id}`), onSuccess: invalidate })
  const remove = useMutation({
    mutationFn: (tenantId: string) => apiClient.delete(`/api/v1/accounting/group/members/${tenantId}`),
    onSuccess: () => { setError(null); invalidate(); qc.invalidateQueries({ queryKey: ["acc-group-mappings"] }); qc.invalidateQueries({ queryKey: ["acc-group-elims"] }) },
    onError: e => setError(errorOf(e)),
  })
  const setOwn = useMutation({
    mutationFn: (v: { tenantId: string; p: number }) => apiClient.put(`/api/v1/accounting/group/members/${v.tenantId}/ownership`, { ownershipPercent: v.p }),
    onSuccess: invalidate, onError: e => setError(errorOf(e)),
  })

  return (
    <div>
      <h2 style={{ marginTop: 0 }}>{group.name}</h2>
      <div style={card}>
        <h3 style={{ marginTop: 0 }}>Companies</h3>
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
          <tbody>
            {group.members.map(m => (
              <tr key={m.tenantId} style={{ borderTop: "1px solid var(--hf-border)" }}>
                <td>{m.name}</td>
                <td>{m.role === "PARENT" ? "Parent (this company)" : "Subsidiary"}</td>
                <td>{ownershipText(m.ownershipPercent)}</td>
                <td style={{ textAlign: "right" }}>
                  {canAdmin && m.role !== "PARENT" && <>
                    <button style={btn} onClick={() => { const v = window.prompt(`Ownership of ${m.name} (percent)`, String(m.ownershipPercent)); if (v) setOwn.mutate({ tenantId: m.tenantId, p: Number(v) }) }}>Ownership</button>{" "}
                    <button style={btn} onClick={() => { if (window.confirm(`Remove ${m.name} from the group?`)) remove.mutate(m.tenantId) }}>Remove</button></>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <p style={{ fontSize: 11, color: "var(--hf-text-secondary)", marginBottom: 0 }}>
          A company owned for less than 100% is still consolidated at 100%; the minority share is not worked out.
        </p>
        <Err text={error} />
      </div>

      {canAdmin && (
        <div style={card}>
          <h3 style={{ marginTop: 0 }}>Invite a company</h3>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <input aria-label="Who is it for" placeholder="Who is it for (optional)" value={label} onChange={e => setLabel(e.target.value)} style={{ ...field, width: 220 }} />
            <input aria-label="Ownership percent" type="number" value={pct} onChange={e => setPct(e.target.value)} style={{ ...field, width: 90 }} />
            <button style={btn} disabled={invite.isPending} onClick={() => invite.mutate()}>Make an invitation code</button>
          </div>
          {newCode && <div role="status" style={{ marginTop: 10, fontSize: 13 }}>Give this code to the other company's administrator. It is shown only now and works for 7 days:
            <div style={{ fontFamily: "monospace", fontSize: 16, fontWeight: 700 }}>{newCode}</div></div>}
          {invites.length > 0 && (
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12, marginTop: 10 }}>
              <tbody>{invites.map(i => (
                <tr key={i.id} style={{ borderTop: "1px solid var(--hf-border)" }}>
                  <td>{i.label || "Unnamed invitation"}</td><td>{ownershipText(i.ownershipPercent)}</td><td>expires {i.expiresAt.slice(0, 10)}</td>
                  <td style={{ textAlign: "right" }}><button style={btn} onClick={() => cancel.mutate(i.id)}>Cancel</button></td></tr>))}</tbody>
            </table>
          )}
        </div>
      )}
      {group.members.length > 1 && <Mappings group={group} canAdmin={canAdmin} />}
      {group.members.length > 1 && <Eliminations group={group} canAdmin={canAdmin} />}
    </div>
  )
}

function useAccounts(tenantId: string) {
  return useQuery<AccountRef[]>({
    queryKey: ["acc-group-accounts", tenantId], enabled: !!tenantId,
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/accounting/group/members/${tenantId}/accounts`)) ?? [],
  })
}

function Mappings({ group, canAdmin }: { group: GroupInfo; canAdmin: boolean }) {
  const qc = useQueryClient()
  const [tenant, setTenant] = useState("")
  const [account, setAccount] = useState("")
  const [groupCode, setGroupCode] = useState("")
  const [groupName, setGroupName] = useState("")
  const [error, setError] = useState<string | null>(null)
  const { data: mappings = [] } = useQuery<Mapping[]>({ queryKey: ["acc-group-mappings"], queryFn: async () => unwrap(await apiClient.get("/api/v1/accounting/group/mappings")) ?? [] })
  const { data: accounts = [] } = useAccounts(tenant)
  const save = useMutation({
    mutationFn: () => apiClient.put("/api/v1/accounting/group/mappings", { tenantId: tenant, accountCode: account, groupCode: groupCode.trim(), groupName: groupName.trim() || null }),
    onSuccess: () => { setError(null); setAccount(""); setGroupCode(""); setGroupName(""); qc.invalidateQueries({ queryKey: ["acc-group-mappings"] }) }, onError: e => setError(errorOf(e)),
  })
  const del = useMutation({ mutationFn: (id: string) => apiClient.delete(`/api/v1/accounting/group/mappings/${id}`), onSuccess: () => qc.invalidateQueries({ queryKey: ["acc-group-mappings"] }) })
  const problem = mapProblem(tenant, account, groupCode)
  const known = knownGroupLines(mappings)
  return (
    <div style={card}>
      <h3 style={{ marginTop: 0 }}>Account mapping</h3>
      <p style={{ fontSize: 12, color: "var(--hf-text-secondary)", marginTop: 0 }}>
        Accounts with the same code are added together automatically. Map an account only when a company uses a different code for the same thing.
      </p>
      {mappings.length > 0 && <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12, marginBottom: 8 }}><tbody>{mappings.map(m => (
        <tr key={m.id} style={{ borderTop: "1px solid var(--hf-border)" }}><td>{m.companyName}</td><td>{m.accountCode}</td><td>→ {m.groupCode} {m.groupName ?? ""}</td>
          <td style={{ textAlign: "right" }}>{canAdmin && <button style={btn} onClick={() => del.mutate(m.id)}>Remove</button>}</td></tr>))}</tbody></table>}
      {canAdmin && (
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          <select aria-label="Company" value={tenant} onChange={e => { setTenant(e.target.value); setAccount("") }} style={field}>
            <option value="">Company…</option>{group.members.map(m => <option key={m.tenantId} value={m.tenantId}>{m.name}</option>)}</select>
          <select aria-label="Account to map" value={account} onChange={e => setAccount(e.target.value)} style={field}>
            <option value="">Account…</option>{accounts.map(a => <option key={a.code} value={a.code}>{a.code} — {a.name}</option>)}</select>
          <input aria-label="Group line code" list="group-lines" placeholder="Group line code" value={groupCode} onChange={e => setGroupCode(e.target.value)} style={{ ...field, width: 130 }} />
          <datalist id="group-lines">{known.map(k => <option key={k.code} value={k.code}>{k.name}</option>)}</datalist>
          <input aria-label="Group line name" placeholder="Name (optional)" value={groupName} onChange={e => setGroupName(e.target.value)} style={{ ...field, width: 180 }} />
          <button style={btn} disabled={!!problem || save.isPending} onClick={() => save.mutate()}>Map</button>
        </div>
      )}
      <Err text={error} />
    </div>
  )
}

function Eliminations({ group, canAdmin }: { group: GroupInfo; canAdmin: boolean }) {
  const qc = useQueryClient()
  const [name, setName] = useState("")
  const [ta, setTa] = useState(""), [ca, setCa] = useState(""), [tb, setTb] = useState(""), [cb, setCb] = useState("")
  const [error, setError] = useState<string | null>(null)
  const { data: rules = [] } = useQuery<Elimination[]>({ queryKey: ["acc-group-elims"], queryFn: async () => unwrap(await apiClient.get("/api/v1/accounting/group/eliminations")) ?? [] })
  const { data: accA = [] } = useAccounts(ta)
  const { data: accB = [] } = useAccounts(tb)
  const add = useMutation({
    mutationFn: () => apiClient.post("/api/v1/accounting/group/eliminations", { name: name.trim(), tenantAId: ta, accountCodeA: ca, tenantBId: tb, accountCodeB: cb }),
    onSuccess: () => { setError(null); setName(""); setCa(""); setCb(""); qc.invalidateQueries({ queryKey: ["acc-group-elims"] }) }, onError: e => setError(errorOf(e)),
  })
  const del = useMutation({ mutationFn: (id: string) => apiClient.delete(`/api/v1/accounting/group/eliminations/${id}`), onSuccess: () => qc.invalidateQueries({ queryKey: ["acc-group-elims"] }) })
  const problem = ruleProblem(name, { tenant: ta, code: ca }, { tenant: tb, code: cb })
  const companies = group.members.map(m => <option key={m.tenantId} value={m.tenantId}>{m.name}</option>)
  return (
    <div style={card}>
      <h3 style={{ marginTop: 0 }}>Intercompany rules</h3>
      <p style={{ fontSize: 12, color: "var(--hf-text-secondary)", marginTop: 0 }}>
        Pair an account in one company with its mirror in another (a loan receivable against the loan payable, intercompany sales against intercompany costs).
        Both are removed from the consolidated figures. If the two sides differ, the difference is shown so it can be investigated.
      </p>
      {rules.length > 0 && <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12, marginBottom: 8 }}><tbody>{rules.map(r => (
        <tr key={r.id} style={{ borderTop: "1px solid var(--hf-border)" }}><td>{r.name}</td><td>{r.companyA} {r.accountCodeA}</td><td>↔ {r.companyB} {r.accountCodeB}</td>
          <td style={{ textAlign: "right" }}>{canAdmin && <button style={btn} onClick={() => del.mutate(r.id)}>Remove</button>}</td></tr>))}</tbody></table>}
      {canAdmin && (
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          <input aria-label="Rule name" placeholder="Rule name" value={name} onChange={e => setName(e.target.value)} style={{ ...field, width: 180 }} />
          <select aria-label="First company" value={ta} onChange={e => { setTa(e.target.value); setCa("") }} style={field}><option value="">First company…</option>{companies}</select>
          <select aria-label="First account" value={ca} onChange={e => setCa(e.target.value)} style={field}><option value="">Account…</option>{accA.map(a => <option key={a.code} value={a.code}>{a.code} — {a.name}</option>)}</select>
          <select aria-label="Second company" value={tb} onChange={e => { setTb(e.target.value); setCb("") }} style={field}><option value="">Second company…</option>{companies}</select>
          <select aria-label="Second account" value={cb} onChange={e => setCb(e.target.value)} style={field}><option value="">Account…</option>{accB.map(a => <option key={a.code} value={a.code}>{a.code} — {a.name}</option>)}</select>
          <button style={btn} disabled={!!problem || add.isPending} onClick={() => add.mutate()}>Add rule</button>
        </div>
      )}
      <Err text={error} />
    </div>
  )
}
