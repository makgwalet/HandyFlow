// src/pages/security/GuardHrLinkCard.tsx
//
// The guard's HR employee record, if linked: shown on the Overview tab, with a search to link or a button to unlink.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Link2, Unlink } from "lucide-react"
import { apiClient } from "../../api/client"
import { linkLine, searchReady, type EmployeeOption, type HrLink } from "./hrLink.logic"

const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16 }
const input: React.CSSProperties = { padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text-primary)", flex: "1 1 220px", minWidth: 0, boxSizing: "border-box" }
const btn: React.CSSProperties = { display: "inline-flex", alignItems: "center", gap: 6, padding: "7px 11px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const errText = (e: any) => e?.response?.data?.message ?? "That did not work. Please try again."

export default function GuardHrLinkCard({ guardId, canManage }: { guardId: string; canManage: boolean }) {
  const qc = useQueryClient()
  const [q, setQ] = useState("")
  const [err, setErr] = useState("")
  const { data, isError } = useQuery<HrLink>({
    queryKey: ["guard-hr-link", guardId],
    queryFn: async () => { const r = await apiClient.get(`/api/v1/security/guards/${guardId}/hr-link`); return r.data?.data ?? r.data },
    retry: false,
  })
  const { data: options = [] } = useQuery<EmployeeOption[]>({
    queryKey: ["guard-hr-employees", q.trim()],
    queryFn: async () => { const r = await apiClient.get("/api/v1/security/guards/hr-employees", { params: { q: q.trim() } }); const d = r.data?.data ?? r.data; return Array.isArray(d) ? d : [] },
    enabled: canManage && !!data && !data.linked && searchReady(q),
  })
  const done = (r: any) => { setErr(""); setQ(""); qc.setQueryData(["guard-hr-link", guardId], r.data?.data ?? r.data); qc.invalidateQueries({ queryKey: ["complaint"] }) }
  const link = useMutation({ mutationFn: (employeeId: string) => apiClient.put(`/api/v1/security/guards/${guardId}/hr-link`, { employeeId }), onSuccess: done, onError: e => setErr(errText(e)) })
  const unlink = useMutation({ mutationFn: () => apiClient.delete(`/api/v1/security/guards/${guardId}/hr-link`), onSuccess: done, onError: e => setErr(errText(e)) })

  if (isError || !data || typeof data.linked !== "boolean") return null
  return (
    <div style={card}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
        <div>
          <div style={{ fontWeight: 800, marginBottom: 4 }}>HR employee record</div>
          <div style={{ fontSize: 13, color: data.linked ? "var(--hf-text-primary)" : "var(--hf-text-muted)" }}>{linkLine(data)}</div>
        </div>
        {data.linked && canManage && <button style={btn} onClick={() => unlink.mutate()} disabled={unlink.isPending}><Unlink size={14} /> Unlink</button>}
      </div>
      {!data.linked && canManage && (
        <div style={{ marginTop: 10 }}>
          <input aria-label="Search HR employees" placeholder="Search HR employees by name, number or ID" value={q} onChange={e => setQ(e.target.value)} style={{ ...input, width: "100%" }} />
          {searchReady(q) && options.length === 0 && <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 6 }}>No HR employee matches.</div>}
          <ul style={{ listStyle: "none", margin: "8px 0 0", padding: 0, display: "grid", gap: 6 }}>
            {options.map(o => (
              <li key={o.id} style={{ display: "flex", gap: 8, alignItems: "center", justifyContent: "space-between", flexWrap: "wrap", fontSize: 13 }}>
                <span>{o.fullName}{o.employeeNumber ? ` · ${o.employeeNumber}` : ""}{o.jobTitle ? ` · ${o.jobTitle}` : ""}</span>
                <button style={btn} onClick={() => link.mutate(o.id)} disabled={link.isPending}><Link2 size={14} /> Link</button>
              </li>
            ))}
          </ul>
        </div>
      )}
      {err && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13, marginTop: 8 }}>{err}</div>}
      <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 8 }}>The employee record stays in HR. This only connects the two, so a substantiated complaint can be referred to HR.</div>
    </div>
  )
}
