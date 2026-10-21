// Search for a company by name and choose one. Used to pick the company of an opportunity and the parent of a branch.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"

interface Hit { id: string; name: string }
const field: React.CSSProperties = { padding: "6px 9px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13 }

export function CompanyPicker({ label, value, onChange, exclude = [] }: { label: string; value: string; onChange: (id: string, name: string) => void; exclude?: string[] }) {
  const [text, setText] = useState("")
  const q = text.trim()
  const { data = [], isFetching } = useQuery<Hit[]>({
    queryKey: ["crm", "company-picker", q], enabled: q.length >= 2,
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/crm/customers?search=${encodeURIComponent(q)}&size=20&sort=name,asc`)
      const o = r.data as { data?: unknown } | null, p = (o && !Array.isArray(o) && typeof o === "object" && "data" in o ? o.data : r.data) as Hit[] | { content?: Hit[] } | undefined
      return (Array.isArray(p) ? p : p?.content) ?? []
    },
  })
  const hits = data.filter(h => !exclude.includes(h.id))
  return (
    <div style={{ display: "grid", gap: 4 }}>
      <input aria-label={`Search ${label}`} placeholder={`Search ${label.toLowerCase()} by name`} style={{ ...field, width: "100%" }} value={text} onChange={e => setText(e.target.value)} />
      {q.length >= 2 && (
        <select aria-label={label} style={{ ...field, width: "100%" }} value={value} onChange={e => { const h = hits.find(x => x.id === e.target.value); onChange(e.target.value, h?.name ?? "") }}>
          <option value="">{isFetching ? "Searching…" : hits.length ? "Choose…" : "No company found"}</option>
          {hits.map(h => <option key={h.id} value={h.id}>{h.name}</option>)}
        </select>)}
    </div>
  )
}
