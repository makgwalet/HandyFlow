// src/pages/security/SiteAccessPanel.tsx
//
// Which clients and sites one user may see. No entries means the whole company. Opened from the team list by a SECURITY_ADMIN.
// Branch access is set in the Branches part of the panel, saved separately. Only someone with the whole company can change anything here.
import { useEffect, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { X } from "lucide-react"
import { apiClient } from "../../api/client"
import Chip from "../../components/ui/Chip"
import BranchAccessEditor from "./BranchAccessEditor"
import RegionAccessEditor from "./RegionAccessEditor"
import {
  accessErrorText, editBlock, overLimit, saveWarning, scopeBody, scopeChanged, scopeSummary, toggleId,
  type ScopeNamed, type ScopeView,
} from "./siteScopeLogic"

const muted: React.CSSProperties = { fontSize: 13, color: "var(--hf-text-muted)" }
const field: React.CSSProperties = { padding: "6px 8px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13, minWidth: 0 }
const btn: React.CSSProperties = { padding: "6px 12px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", cursor: "pointer", fontSize: 13 }
const sub: React.CSSProperties = { fontSize: 11, color: "var(--hf-text-muted)", fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.4, margin: "14px 0 6px" }
const rows = <T,>(r: { data?: { data?: unknown } & Record<string, unknown> }): T[] => {
  const p = (r?.data?.data ?? r?.data) as unknown
  return Array.isArray(p) ? p as T[] : ((p as { content?: T[] } | undefined)?.content ?? [])
}

export default function SiteAccessPanel({ userId, userName, actorId, onClose }: { userId: string; userName: string; actorId?: string; onClose: () => void }) {
  const qc = useQueryClient()
  const key = ["user-site-scope", userId]
  const target = useQuery<ScopeView>({ queryKey: key, queryFn: async () => { const r = await apiClient.get(`/api/v1/security/users/${userId}/site-scope`); return (r.data?.data ?? r.data) as ScopeView } })
  const mine = useQuery<ScopeView>({ queryKey: ["my-site-scope"], queryFn: async () => { const r = await apiClient.get("/api/v1/security/my-site-scope"); return (r.data?.data ?? r.data) as ScopeView } })
  const sites = useQuery<ScopeNamed[]>({ queryKey: ["scope-site-choices"], queryFn: async () => rows<ScopeNamed>(await apiClient.get("/api/v1/security/sites?size=100")) })
  // What the person has changed so far; until they change something the form shows what the server has.
  const [draft, setDraft] = useState<{ clients: ScopeNamed[]; siteIds: string[] } | null>(null)
  const [text, setText] = useState("")
  const [q, setQ] = useState("")
  const [problem, setProblem] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  useEffect(() => { const t = setTimeout(() => setQ(text.trim()), 250); return () => clearTimeout(t) }, [text])
  const found = useQuery<ScopeNamed[]>({
    queryKey: ["scope-client-search", q], enabled: q.length >= 2,
    queryFn: async () => rows<ScopeNamed>(await apiClient.get(`/api/v1/crm/customers?size=8&search=${encodeURIComponent(q)}`)),
  })
  const save = useMutation({
    mutationFn: async () => apiClient.put(`/api/v1/security/users/${userId}/site-scope`, scopeBody(clients.map(c => c.id), siteIds)),
    onSuccess: r => {
      setProblem(null); setSaved(true); setDraft(null)
      const fresh = (r?.data?.data ?? r?.data) as ScopeView | undefined
      if (fresh && Array.isArray(fresh.sites)) qc.setQueryData(key, fresh)
      qc.invalidateQueries({ queryKey: key }); qc.invalidateQueries({ queryKey: ["my-site-scope"] }); qc.invalidateQueries({ queryKey: ["site-access-overview"] })
    },
    onError: e => { setSaved(false); setProblem(accessErrorText(e, "Site access could not be saved.")) },
  })

  const view = target.data
  const clients = draft?.clients ?? view?.clients ?? []
  const siteIds = draft?.siteIds ?? view?.sites.map(s => s.id) ?? []
  const setClients = (f: (p: ScopeNamed[]) => ScopeNamed[]) => setDraft({ clients: f(clients), siteIds })
  const setSiteIds = (f: (p: string[]) => string[]) => setDraft({ clients, siteIds: f(siteIds) })
  const clientIds = clients.map(c => c.id)
  const block = view && mine.data ? editBlock(actorId, userId, mine.data) : null
  const limit = overLimit(clientIds, siteIds)
  const warning = view ? saveWarning(view, clientIds, siteIds) : null
  const dirty = !!view && scopeChanged(view, clientIds, siteIds)
  const loading = target.isLoading || mine.isLoading
  const failed = target.error || mine.error

  return (
    <div aria-label={`Site access for ${userName}`}>
      {loading && <div style={muted}>Loading…</div>}
      {failed && <div role="alert" style={{ ...muted, color: "var(--hf-danger-text)" }}>{accessErrorText(failed, "Site access could not be loaded.")}</div>}
      {view && (
        <>
          <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
            <Chip tone={view.restricted ? "warn" : "ok"}>{view.restricted ? "Limited" : "Whole company"}</Chip>
            <span style={muted}>{scopeSummary(view)}</span>
          </div>
          {block && <div role="status" style={{ ...muted, marginTop: 8 }}>{block}</div>}

          <BranchAccessEditor userId={userId} view={view} block={block} />
          <RegionAccessEditor userId={userId} view={view} block={block} />

          <div style={sub}>Clients (every site of the client)</div>
          <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginBottom: 6 }}>
            {clients.length === 0 && <span style={muted}>None chosen.</span>}
            {clients.map(c => (
              <span key={c.id} style={{ display: "inline-flex", alignItems: "center", gap: 4 }}>
                <Chip tone="info">{c.name}</Chip>
                <button type="button" aria-label={`Remove ${c.name}`} disabled={!!block} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-muted)", display: "flex" }} onClick={() => setClients(p => p.filter(x => x.id !== c.id))}><X size={13} /></button>
              </span>
            ))}
          </div>
          <input aria-label="Search clients" placeholder="Search clients to add" style={{ ...field, width: "100%", maxWidth: 320 }} disabled={!!block} value={text} onChange={e => setText(e.target.value)} />
          {q.length >= 2 && !block && (
            <div style={{ marginTop: 4 }}>
              {found.isFetching && <div style={muted}>Searching…</div>}
              {found.data?.filter(c => !clientIds.includes(c.id)).map(c => (
                <div key={c.id}><button type="button" style={{ ...btn, marginTop: 4 }} onClick={() => { setClients(p => [...p, { id: c.id, name: c.name }]); setText("") }}>Add {c.name}</button></div>
              ))}
              {found.data && found.data.filter(c => !clientIds.includes(c.id)).length === 0 && !found.isFetching && <div style={muted}>No other clients match.</div>}
            </div>
          )}

          <div style={sub}>Sites</div>
          {sites.isLoading && <div style={muted}>Loading sites…</div>}
          {sites.error && <div role="alert" style={{ ...muted, color: "var(--hf-danger-text)" }}>The sites could not be loaded.</div>}
          <div style={{ maxHeight: 220, overflowY: "auto", border: "1px solid var(--hf-border-subtle)", borderRadius: 8, padding: "4px 10px" }}>
            {sites.data?.length === 0 && <div style={muted}>No sites yet.</div>}
            {sites.data?.map(s => (
              <label key={s.id} style={{ display: "flex", gap: 8, alignItems: "center", padding: "4px 0", fontSize: 13 }}>
                <input type="checkbox" disabled={!!block} checked={siteIds.includes(s.id)} onChange={() => setSiteIds(p => toggleId(p, s.id))} /> {s.name}
              </label>
            ))}
          </div>

          {limit && <div role="alert" style={{ ...muted, color: "var(--hf-danger-text)", marginTop: 8 }}>{limit}</div>}
          {warning && dirty && <div role="status" style={{ ...muted, color: "var(--hf-warning-text)", marginTop: 8 }}>{warning}</div>}
          {problem && <div role="alert" style={{ ...muted, color: "var(--hf-danger-text)", marginTop: 8 }}>{problem}</div>}
          {saved && !dirty && <div role="status" style={{ ...muted, color: "var(--hf-success-text-strong)", marginTop: 8 }}>Saved. {scopeSummary(view)}</div>}

          <div style={{ display: "flex", gap: 8, justifyContent: "flex-end", marginTop: 14 }}>
            <button type="button" style={btn} onClick={onClose}>Close</button>
            <button type="button" style={{ ...btn, opacity: block || !dirty || limit ? 0.5 : 1 }} disabled={!!block || !dirty || !!limit || save.isPending} onClick={() => { setSaved(false); save.mutate() }}>Save site access</button>
          </div>
        </>
      )}
    </div>
  )
}
