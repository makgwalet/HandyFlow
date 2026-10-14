// The practice's bookkeeping clients: add, edit, switch off and back on.
import { useState } from "react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useNavigate } from "react-router-dom"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { errorOf, type Client } from "./bookkeeping.logic"
import { useClients } from "./ClientScope"
import { btn, card, danger, field, muted, primary, td, th } from "./ui"

const blank = { tradingName: "", registrationNumber: "", vatNumber: "", contactName: "", contactEmail: "", contactPhone: "", address: "" }

export default function ClientsTab() {
  const qc = useQueryClient()
  const navigate = useNavigate()
  const canManage = usePermission("BOOKKEEPING_MANAGE") || usePermission("BOOKKEEPING_ADMIN")
  const { data: clients = [], isLoading } = useClients()
  const [form, setForm] = useState(blank)
  const [editing, setEditing] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState("")
  const set = (k: keyof typeof blank, v: string) => setForm(f => ({ ...f, [k]: v }))
  const done = () => { setError(null); setForm(blank); setEditing(null); qc.invalidateQueries({ queryKey: ["bk-clients"] }); qc.invalidateQueries({ queryKey: ["bk-work-queue"] }) }
  const body = () => Object.fromEntries(Object.entries(form).map(([k, v]) => [k, typeof v === "string" && v.trim() === "" && k !== "tradingName" ? null : (v as string).trim()]))
  const save = useMutation({
    mutationFn: () => editing ? apiClient.put(`/api/v1/bookkeeping/clients/${editing}`, body()) : apiClient.post("/api/v1/bookkeeping/clients", body()),
    onSuccess: done, onError: e => setError(errorOf(e)),
  })
  const toggle = useMutation({
    mutationFn: (c: Client) => apiClient.post(`/api/v1/bookkeeping/clients/${c.id}/${c.status === "ACTIVE" ? "deactivate" : "reactivate"}`),
    onSuccess: done, onError: e => setError(errorOf(e)),
  })
  const edit = (c: Client) => { setEditing(c.id); setForm({ tradingName: c.tradingName, registrationNumber: c.registrationNumber ?? "", vatNumber: c.vatNumber ?? "", contactName: c.contactName ?? "", contactEmail: c.contactEmail ?? "", contactPhone: c.contactPhone ?? "", address: c.address ?? "" }) }
  const shown = clients.filter(c => !search.trim() || `${c.tradingName} ${c.clientCode} ${c.vatNumber ?? ""}`.toLowerCase().includes(search.trim().toLowerCase()))

  return (
    <div>
      {canManage && (
        <div style={card}>
          <h3 style={{ marginTop: 0 }}>{editing ? "Edit client" : "New client"}</h3>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <input aria-label="Trading name" placeholder="Trading name" value={form.tradingName} onChange={e => set("tradingName", e.target.value)} style={{ ...field, width: 220 }} />
            <input aria-label="Registration number" placeholder="Registration number" value={form.registrationNumber} onChange={e => set("registrationNumber", e.target.value)} style={{ ...field, width: 150 }} />
            <input aria-label="VAT number" placeholder="VAT number" value={form.vatNumber} onChange={e => set("vatNumber", e.target.value)} style={{ ...field, width: 120 }} />
            <input aria-label="Contact name" placeholder="Contact name" value={form.contactName} onChange={e => set("contactName", e.target.value)} style={{ ...field, width: 150 }} />
            <input aria-label="Contact email" placeholder="Contact email" value={form.contactEmail} onChange={e => set("contactEmail", e.target.value)} style={{ ...field, width: 190 }} />
            <input aria-label="Contact phone" placeholder="Contact phone" value={form.contactPhone} onChange={e => set("contactPhone", e.target.value)} style={{ ...field, width: 130 }} />
            <input aria-label="Address" placeholder="Address" value={form.address} onChange={e => set("address", e.target.value)} style={{ ...field, width: 260 }} />
            <button style={primary} disabled={!form.tradingName.trim() || save.isPending} onClick={() => save.mutate()}>{editing ? "Save" : "Add client"}</button>
            {editing && <button style={btn} onClick={() => { setEditing(null); setForm(blank) }}>Cancel</button>}
          </div>
          {error && <div role="alert" style={{ ...danger, marginTop: 6 }}>{error}</div>}
        </div>
      )}
      <div style={card}>
        <input aria-label="Search clients" placeholder="Search clients" value={search} onChange={e => setSearch(e.target.value)} style={{ ...field, width: 240, marginBottom: 10 }} />
        {isLoading && <p style={{ fontSize: 13 }}>Loading…</p>}
        {!isLoading && shown.length === 0 && <p style={muted}>{clients.length === 0 ? "No clients yet." : "No client matches that search."}</p>}
        {shown.length > 0 && (
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
            <thead><tr><th style={th}>Code</th><th style={th}>Client</th><th style={th}>Contact</th><th style={th}>VAT</th><th style={th}>Status</th><th /></tr></thead>
            <tbody>{shown.map(c => (
              <tr key={c.id} style={{ borderTop: "1px solid var(--hf-border)", opacity: c.status === "ACTIVE" ? 1 : 0.6 }}>
                <td style={{ ...td, fontFamily: "monospace" }}>{c.clientCode}</td><td style={td}><b>{c.tradingName}</b></td>
                <td style={td}>{[c.contactName, c.contactEmail].filter(Boolean).join(" · ") || "—"}</td><td style={td}>{c.vatNumber || "—"}</td>
                <td style={td}>{c.status === "ACTIVE" ? "Active" : "Inactive"}</td>
                <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}>
                  <button style={btn} onClick={() => navigate(`/bookkeeping/bank?client=${c.id}`)}>Open</button>{" "}
                  {canManage && <><button style={btn} onClick={() => edit(c)}>Edit</button>{" "}
                    <button style={btn} onClick={() => toggle.mutate(c)}>{c.status === "ACTIVE" ? "Switch off" : "Switch on"}</button></>}
                </td>
              </tr>))}</tbody>
          </table>
        )}
      </div>
    </div>
  )
}
