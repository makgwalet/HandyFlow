// src/pages/complianceservices/ClientPartiesTab.tsx
//
// Who owns and runs the client (directors, shareholders, beneficial owners) and where it operates.
// A person who leaves is marked as resigned, not deleted, so the history stays.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { AlertTriangle } from "lucide-react"
import { ROLES, ID_TYPES, SITE_TYPES, roleLabel, partyProblem, ownershipNotes, holdsPercentage, type Ownership, type PartyDraft } from "./partyRules"

interface Party {
  id: string; fullName: string; role: string; idType: string | null; idNumber: string | null; ownershipPercent: number | null
  appointedOn: string | null; resignedOn: string | null; active: boolean
}
interface Parties { parties: Party[]; ownership: Ownership }
interface Site { id: string; name: string; siteType: string; address: string | null; employeeCount: number | null; active: boolean }

const ACCENT = "var(--hf-success-solid-strong)"
const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
const errText = (e: unknown): string => {
  const x = e as { response?: { data?: { message?: string } }; message?: string }
  return x?.response?.data?.message ?? x?.message ?? "Something went wrong"
}
const input: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)", boxSizing: "border-box" }
const button: React.CSSProperties = { padding: "8px 14px", border: "none", borderRadius: 8, background: ACCENT, color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const ghost: React.CSSProperties = { padding: "5px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "transparent", color: "var(--hf-text-muted)", fontSize: 12, cursor: "pointer" }
const row: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 10, padding: "10px 12px", display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }

const emptyParty: PartyDraft = { fullName: "", role: "DIRECTOR", idType: "SA_ID", idNumber: "", ownershipPercent: "", appointedOn: "", resignedOn: "" }
const emptySite = { name: "", siteType: "BRANCH", address: "", employeeCount: "" }

export default function ClientPartiesTab({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["COMPLIANCE_SERVICES_MANAGE", "COMPLIANCE_SERVICES_ADMIN"])
  const base = `/api/v1/compliance-services/clients/${clientId}`
  const [party, setParty] = useState<PartyDraft>(emptyParty)
  const [site, setSite] = useState(emptySite)
  const [error, setError] = useState("")

  const { data: people } = useQuery<Parties>({ queryKey: ["cs-parties", clientId], queryFn: async () => unwrap<Parties>(await apiClient.get(`${base}/parties`)) })
  const { data: sites = [] } = useQuery<Site[]>({ queryKey: ["cs-sites", clientId], queryFn: async () => unwrap<Site[]>(await apiClient.get(`${base}/sites`)) })

  const onError = (e: unknown) => setError(errText(e))
  const addParty = useMutation({
    mutationFn: async (p: PartyDraft) => unwrap<Parties>(await apiClient.post(`${base}/parties`, {
      fullName: p.fullName.trim(), role: p.role, idType: p.idNumber.trim() ? p.idType : null, idNumber: p.idNumber.trim() || null,
      ownershipPercent: p.ownershipPercent.trim() === "" ? null : Number(p.ownershipPercent), appointedOn: p.appointedOn || null, resignedOn: null,
    })),
    onSuccess: (d: Parties) => { setError(""); setParty(emptyParty); qc.setQueryData(["cs-parties", clientId], d) },
    onError,
  })
  const resign = useMutation({
    mutationFn: async (id: string) => unwrap<Parties>(await apiClient.post(`${base}/parties/${id}/resign`, {})),
    onSuccess: (d: Parties) => { setError(""); qc.setQueryData(["cs-parties", clientId], d) },
    onError,
  })
  const addSite = useMutation({
    mutationFn: async (s: typeof emptySite) => unwrap<Site[]>(await apiClient.post(`${base}/sites`, {
      name: s.name.trim(), siteType: s.siteType, address: s.address.trim() || null, employeeCount: s.employeeCount.trim() === "" ? null : Number(s.employeeCount),
    })),
    onSuccess: (d: Site[]) => { setError(""); setSite(emptySite); qc.setQueryData(["cs-sites", clientId], d) },
    onError,
  })
  const toggleSite = useMutation({
    mutationFn: async (v: { id: string; active: boolean }) => unwrap<Site[]>(await apiClient.post(`${base}/sites/${v.id}/${v.active ? "reactivate" : "deactivate"}`, {})),
    onSuccess: (d: Site[]) => { setError(""); qc.setQueryData(["cs-sites", clientId], d) },
    onError,
  })

  const problem = partyProblem(party)
  const notes = people ? ownershipNotes(people.ownership) : []
  const siteProblem = !site.name.trim() ? "Enter a name" : site.employeeCount.trim() !== "" && !(Number.isInteger(Number(site.employeeCount)) && Number(site.employeeCount) >= 0) ? "Employees must be a whole number" : null
  const active = people?.parties.filter(p => p.active) ?? []
  const former = people?.parties.filter(p => !p.active) ?? []

  return (
    <div>
      {error && <div role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text, #c0392b)", marginBottom: 10 }}>{error}</div>}

      <h3 style={{ fontSize: 14, margin: "0 0 8px" }}>Owners and directors</h3>
      {notes.length > 0 && (
        <div style={{ display: "grid", gap: 4, marginBottom: 10 }}>
          {notes.map(n => (
            <div key={n} style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 12, color: "var(--hf-warning-text, #8a5a00)" }}>
              <AlertTriangle size={13} /> {n}
            </div>
          ))}
        </div>
      )}
      <div style={{ display: "grid", gap: 8 }}>
        {active.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No one recorded yet.</div>}
        {active.map(p => (
          <div key={p.id} style={row}>
            <span style={{ fontSize: 13, fontWeight: 600, minWidth: 160 }}>{p.fullName}</span>
            <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
              {roleLabel(p.role)}{p.ownershipPercent != null ? ` · ${p.ownershipPercent}%` : ""}{p.idNumber ? ` · ${p.idNumber}` : ""}{p.appointedOn ? ` · since ${p.appointedOn}` : ""}
            </span>
            <span style={{ flex: 1 }} />
            {canManage && <button style={ghost} disabled={resign.isPending} onClick={() => resign.mutate(p.id)}>Mark as resigned</button>}
          </div>
        ))}
        {former.length > 0 && (
          <details>
            <summary style={{ fontSize: 12, color: "var(--hf-text-faint)", cursor: "pointer" }}>{former.length} former</summary>
            <div style={{ display: "grid", gap: 6, marginTop: 6 }}>
              {former.map(p => (
                <div key={p.id} style={{ ...row, opacity: 0.7 }}>
                  <span style={{ fontSize: 13, minWidth: 160 }}>{p.fullName}</span>
                  <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{roleLabel(p.role)} · until {p.resignedOn}</span>
                </div>
              ))}
            </div>
          </details>
        )}
      </div>

      {canManage && (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))", gap: 8, marginTop: 12 }}>
          <input style={input} placeholder="Full name" value={party.fullName} onChange={e => setParty({ ...party, fullName: e.target.value })} />
          <select style={input} value={party.role} onChange={e => setParty({ ...party, role: e.target.value })}>{ROLES.map(r => <option key={r[0]} value={r[0]}>{r[1]}</option>)}</select>
          <select style={input} value={party.idType} onChange={e => setParty({ ...party, idType: e.target.value })}>{ID_TYPES.map(r => <option key={r[0]} value={r[0]}>{r[1]}</option>)}</select>
          <input style={input} placeholder="ID number" value={party.idNumber} onChange={e => setParty({ ...party, idNumber: e.target.value })} />
          {holdsPercentage(party.role) && <input style={input} placeholder="Share %" inputMode="decimal" value={party.ownershipPercent} onChange={e => setParty({ ...party, ownershipPercent: e.target.value })} />}
          <input style={input} type="date" title="Appointed on" value={party.appointedOn} onChange={e => setParty({ ...party, appointedOn: e.target.value })} />
          <button style={{ ...button, opacity: problem || addParty.isPending ? 0.5 : 1 }} disabled={!!problem || addParty.isPending} onClick={() => addParty.mutate(party)}>Add person</button>
          {problem && party.fullName.length > 0 && <div style={{ fontSize: 11, color: "var(--hf-text-faint)", gridColumn: "1 / -1" }}>{problem}</div>}
        </div>
      )}

      <h3 style={{ fontSize: 14, margin: "28px 0 8px" }}>Sites</h3>
      <div style={{ display: "grid", gap: 8 }}>
        {sites.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No sites recorded yet.</div>}
        {sites.map(s => (
          <div key={s.id} style={{ ...row, opacity: s.active ? 1 : 0.6 }}>
            <span style={{ fontSize: 13, fontWeight: 600, minWidth: 160 }}>{s.name}</span>
            <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
              {SITE_TYPES.find(t => t[0] === s.siteType)?.[1] ?? s.siteType}{s.employeeCount != null ? ` · ${s.employeeCount} staff` : ""}{s.address ? ` · ${s.address}` : ""}{s.active ? "" : " · inactive"}
            </span>
            <span style={{ flex: 1 }} />
            {canManage && <button style={ghost} disabled={toggleSite.isPending} onClick={() => toggleSite.mutate({ id: s.id, active: !s.active })}>{s.active ? "Deactivate" : "Reactivate"}</button>}
          </div>
        ))}
      </div>
      {canManage && (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))", gap: 8, marginTop: 12 }}>
          <input style={input} placeholder="Site name" value={site.name} onChange={e => setSite({ ...site, name: e.target.value })} />
          <select style={input} value={site.siteType} onChange={e => setSite({ ...site, siteType: e.target.value })}>{SITE_TYPES.map(r => <option key={r[0]} value={r[0]}>{r[1]}</option>)}</select>
          <input style={input} placeholder="Address" value={site.address} onChange={e => setSite({ ...site, address: e.target.value })} />
          <input style={input} placeholder="Employees" inputMode="numeric" value={site.employeeCount} onChange={e => setSite({ ...site, employeeCount: e.target.value })} />
          <button style={{ ...button, opacity: siteProblem || addSite.isPending ? 0.5 : 1 }} disabled={!!siteProblem || addSite.isPending} onClick={() => addSite.mutate(site)}>Add site</button>
        </div>
      )}
    </div>
  )
}
