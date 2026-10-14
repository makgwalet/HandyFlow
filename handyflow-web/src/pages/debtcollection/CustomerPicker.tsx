import { useEffect, useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { Search, X } from "lucide-react"
import { apiClient } from "../../api/client"
import { rowsOf, searchReady, type Customer } from "./collections.logic"
import { inp, lbl } from "./ui"

/** Search customers by name, email or phone and pick one. Replaces typing a customer ID. */
export default function CustomerPicker({ value, onChange, label = "Customer", optional = false }: { value: Customer | null; onChange: (c: Customer | null) => void; label?: string; optional?: boolean }) {
  const [text, setText] = useState("")
  const [q, setQ] = useState("")
  useEffect(() => { const t = setTimeout(() => setQ(text), 250); return () => clearTimeout(t) }, [text])

  const { data: found = [], isFetching } = useQuery<Customer[]>({
    queryKey: ["dc-customer-search", q],
    enabled: !value && searchReady(q),
    queryFn: async () => rowsOf<Customer>(await apiClient.get(`/api/v1/crm/customers?size=8&search=${encodeURIComponent(q.trim())}`)),
  })

  if (value) {
    return (
      <div>
        <label style={lbl}>{label}</label>
        <div style={{ display: "flex", alignItems: "center", gap: 10, padding: "9px 12px", border: "1.5px solid var(--hf-orange-border)", background: "var(--hf-orange-soft)", borderRadius: 8 }}>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontWeight: 700, fontSize: 14, color: "var(--hf-text)" }}>{value.name}</div>
            <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{[value.email, value.phone].filter(Boolean).join(" · ") || "No contact details on file"}</div>
          </div>
          <button type="button" aria-label="Change customer" onClick={() => { onChange(null); setText(""); setQ("") }} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-muted)", display: "flex" }}><X size={16} /></button>
        </div>
      </div>
    )
  }
  return (
    <div>
      <label style={lbl} htmlFor="dc-customer-search">{label}{optional && <span style={{ fontWeight: 400, color: "var(--hf-text-faint)" }}> (optional)</span>}</label>
      <div style={{ position: "relative" }}>
        <Search size={15} style={{ position: "absolute", left: 11, top: 12, color: "var(--hf-text-faint)" }} />
        <input id="dc-customer-search" value={text} onChange={e => setText(e.target.value)} placeholder="Search by name, email or phone" style={{ ...inp, paddingLeft: 34 }} autoComplete="off" />
      </div>
      {searchReady(q) && (
        <div style={{ marginTop: 6, border: "1px solid var(--hf-border)", borderRadius: 8, maxHeight: 190, overflowY: "auto", background: "var(--hf-surface)" }}>
          {found.map(c => (
            <button type="button" key={c.id} onClick={() => onChange(c)} style={{ display: "block", width: "100%", textAlign: "left", padding: "9px 12px", background: "none", border: "none", borderBottom: "1px solid var(--hf-border)", cursor: "pointer" }}>
              <div style={{ fontWeight: 600, fontSize: 13, color: "var(--hf-text)" }}>{c.name}</div>
              <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{[c.email, c.phone].filter(Boolean).join(" · ")}</div>
            </button>
          ))}
          {found.length === 0 && <div style={{ padding: "10px 12px", fontSize: 13, color: "var(--hf-text-faint)" }}>{isFetching ? "Searching…" : "No customers match."}</div>}
        </div>
      )}
      {!searchReady(text) && <div style={{ fontSize: 12, color: "var(--hf-text-faint)", marginTop: 5 }}>Type at least {2} letters.</div>}
    </div>
  )
}
