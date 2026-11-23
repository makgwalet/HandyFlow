// src/pages/security/SiteAccessOverviewTab.tsx
//
// Everyone whose access is limited to some branches, clients or sites, how they are limited, and how many sites that leaves them.
// Opens the same panel as Settings > Team to change someone. SECURITY_ADMIN only, and only for someone with the whole company
// (the server enforces both). People who are not listed can see the whole company.
import { useState } from "react"
import { Link } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import { X } from "lucide-react"
import { apiClient } from "../../api/client"
import Chip from "../../components/ui/Chip"
import { usePermission } from "../../hooks/usePermission"
import { useAuthStore } from "../../store/auth.store"
import SiteAccessPanel from "./SiteAccessPanel"
import { accessErrorText } from "./siteScopeLogic"
import { filterUsers, limitText, reachText, seesNothing, statusNote, summariseUsers, type LimitedUser } from "./limitedUsersLogic"

const muted: React.CSSProperties = { fontSize: 13, color: "var(--hf-text-muted)" }
const field: React.CSSProperties = { padding: "6px 8px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13, minWidth: 0 }
const btn: React.CSSProperties = { padding: "6px 10px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", cursor: "pointer", fontSize: 13 }

export default function SiteAccessOverviewTab() {
  const canAdmin = usePermission("SECURITY_ADMIN")
  const myId = useAuthStore(s => s.user?.userId)
  const [text, setText] = useState("")
  const [editing, setEditing] = useState<LimitedUser | null>(null)
  const { data, isLoading, error } = useQuery<LimitedUser[]>({
    queryKey: ["site-access-overview"], enabled: canAdmin,
    queryFn: async () => { const r = await apiClient.get("/api/v1/security/site-access"); const p = r.data?.data ?? r.data; return Array.isArray(p) ? p as LimitedUser[] : [] },
  })
  if (!canAdmin) return <div style={muted}>Site access is for administrators.</div>
  const shown = data ? filterUsers(data, text) : []
  const sum = data ? summariseUsers(data) : null

  return (
    <div>
      <div style={muted}>People limited to some branches, clients or sites. Everyone not listed can see the whole company. To limit someone new, open Site access on their row in <Link to="/settings">Settings &gt; Team</Link>.</div>
      {isLoading && <div style={{ ...muted, marginTop: 12 }}>Loading…</div>}
      {error && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)", marginTop: 12 }}>{accessErrorText(error, "The list could not be loaded.")}</div>}
      {data && sum && (
        <>
          <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "center", margin: "12px 0" }}>
            <strong data-testid="limited-count">{sum.total} limited {sum.total === 1 ? "user" : "users"}</strong>
            {sum.seeingNothing > 0 && <Chip tone="bad">{sum.seeingNothing} can see no sites</Chip>}
            <input aria-label="Search people" placeholder="Search by name or email" style={{ ...field, marginLeft: "auto", minWidth: 200 }} value={text} onChange={e => setText(e.target.value)} />
          </div>
          {data.length === 0 && <div style={muted}>Nobody is limited. Everyone can see the whole company.</div>}
          {data.length > 0 && shown.length === 0 && <div style={muted}>Nobody matches that search.</div>}
          {shown.map(u => {
            const note = statusNote(u.status)
            return (
              <div key={u.userId} style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center", padding: "10px 0", borderTop: "1px solid var(--hf-border-subtle)", fontSize: 13 }}>
                <div style={{ flex: 1, minWidth: 200 }}>
                  <strong>{u.name}</strong> <span style={muted}>{u.email}</span>
                  <div style={muted}>{limitText(u)}</div>
                </div>
                {note && <Chip tone="neutral">{note}</Chip>}
                <Chip tone={seesNothing(u) ? "bad" : "info"}>{reachText(u)}</Chip>
                <button type="button" style={btn} aria-label={`Edit access for ${u.name}`} onClick={() => setEditing(u)}>Edit access</button>
              </div>
            )
          })}
        </>
      )}
      {editing && (
        <div role="dialog" aria-modal="true" aria-label={`Site access for ${editing.name}`} style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.4)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 50, padding: 16 }}
          onClick={e => { if (e.target === e.currentTarget) setEditing(null) }}>
          <div style={{ background: "var(--hf-surface)", borderRadius: 14, padding: 20, width: "min(560px, 100%)", maxHeight: "90vh", overflowY: "auto" }}>
            <div style={{ display: "flex", alignItems: "center", marginBottom: 8 }}>
              <strong style={{ flex: 1 }}>Site access: {editing.name}</strong>
              <button type="button" aria-label="Close" style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-muted)", display: "flex" }} onClick={() => setEditing(null)}><X size={16} /></button>
            </div>
            <SiteAccessPanel userId={editing.userId} userName={editing.name} actorId={myId} onClose={() => setEditing(null)} />
          </div>
        </div>
      )}
    </div>
  )
}
