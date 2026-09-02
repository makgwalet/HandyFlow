// src/pages/warehousing/WhseLocationsTab.tsx
//
// The operator's OWN warehouse location/bin structure — not per-client
// (see WhseLocationController's own Javadoc, confirmed via source read).
// GET /api/v1/warehousing/locations is unpaginated (a List, not a Page).
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Plus, MapPin, X, Power, PowerOff, Trash2, Pencil } from "lucide-react"
import { apiClient } from "../../api/client"
import { WHSE_ACCENT } from "./constants"

interface LocationResponse {
  id: string; code: string; zone: string | null; description: string | null
  capacityUnits: number | null; active: boolean
}

const inputStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, boxSizing: "border-box", fontFamily: "inherit" }
const labelStyle: React.CSSProperties = { fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5, display: "block" }

function LocationFormModal({ initial, onClose }: { initial?: LocationResponse; onClose: () => void }) {
  const qc = useQueryClient()
  const [form, setForm] = useState({
    code: initial?.code ?? "", zone: initial?.zone ?? "", description: initial?.description ?? "",
    capacityUnits: initial?.capacityUnits?.toString() ?? "",
  })

  const save = useMutation({
    mutationFn: async () => {
      const body = {
        code: form.code, zone: form.zone || null, description: form.description || null,
        capacityUnits: form.capacityUnits.trim() === "" ? null : parseInt(form.capacityUnits, 10),
      }
      return initial
        ? apiClient.put(`/api/v1/warehousing/locations/${initial.id}`, body)
        : apiClient.post("/api/v1/warehousing/locations", body)
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["whse-locations"] }); onClose() },
  })

  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.4)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 50 }}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 420 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18 }}>
          <p style={{ fontSize: 15, fontWeight: 800, color: "#0F172A", margin: 0 }}>{initial ? "Edit location" : "Add a location"}</p>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={18} color="#94A3B8" /></button>
        </div>
        <div style={{ display: "grid", gap: 12 }}>
          <div><label style={labelStyle}>Code *</label><input style={inputStyle} value={form.code} onChange={e => setForm({ ...form, code: e.target.value })} placeholder="e.g. A-01-03" /></div>
          <div><label style={labelStyle}>Zone</label><input style={inputStyle} value={form.zone} onChange={e => setForm({ ...form, zone: e.target.value })} placeholder="e.g. Cold storage" /></div>
          <div><label style={labelStyle}>Description</label><input style={inputStyle} value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} /></div>
          <div><label style={labelStyle}>Capacity (units)</label><input type="number" style={inputStyle} value={form.capacityUnits} onChange={e => setForm({ ...form, capacityUnits: e.target.value })} /></div>
        </div>
        {save.isError && <p style={{ color: "#DC2626", fontSize: 12, marginTop: 12 }}>{(save.error as any)?.response?.data?.message ?? "Could not save this location"}</p>}
        <button onClick={() => save.mutate()} disabled={!form.code || save.isPending}
          style={{ marginTop: 18, width: "100%", padding: "11px", borderRadius: 8, border: "none", background: WHSE_ACCENT, color: "#fff", fontSize: 13.5, fontWeight: 700, cursor: "pointer", opacity: (!form.code || save.isPending) ? 0.6 : 1 }}>
          {save.isPending ? "Saving…" : initial ? "Save changes" : "Add location"}
        </button>
      </div>
    </div>
  )
}

export default function WhseLocationsTab() {
  const qc = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<LocationResponse | null>(null)

  const { data: locations = [], isLoading } = useQuery<LocationResponse[]>({
    queryKey: ["whse-locations"],
    queryFn: async () => (await apiClient.get("/api/v1/warehousing/locations")).data,
  })

  const deactivate = useMutation({
    mutationFn: async (id: string) => apiClient.post(`/api/v1/warehousing/locations/${id}/deactivate`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["whse-locations"] }),
  })
  const reactivate = useMutation({
    mutationFn: async (id: string) => apiClient.post(`/api/v1/warehousing/locations/${id}/reactivate`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["whse-locations"] }),
  })
  const remove = useMutation({
    mutationFn: async (id: string) => apiClient.delete(`/api/v1/warehousing/locations/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["whse-locations"] }),
  })

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <p style={{ fontSize: 13, color: "#94A3B8", margin: 0 }}>{locations.length} location{locations.length === 1 ? "" : "s"}</p>
        <button onClick={() => setShowForm(true)}
          style={{ display: "flex", alignItems: "center", gap: 6, background: WHSE_ACCENT, color: "#fff", border: "none", borderRadius: 8, padding: "9px 16px", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={15} /> Add location
        </button>
      </div>

      {isLoading ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p>
      ) : locations.length === 0 ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>No locations set up yet.</p>
      ) : (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))", gap: 10 }}>
          {locations.map(l => (
            <div key={l.id} style={{ border: "1px solid #E2E8F0", borderRadius: 12, padding: 14, opacity: l.active ? 1 : 0.55 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 6 }}>
                <MapPin size={14} color={WHSE_ACCENT} />
                <p style={{ fontSize: 13.5, fontWeight: 700, color: "#0F172A", margin: 0 }}>{l.code}</p>
              </div>
              {l.zone && <p style={{ fontSize: 11.5, color: "#64748B", margin: "0 0 2px" }}>{l.zone}</p>}
              {l.description && <p style={{ fontSize: 11, color: "#94A3B8", margin: "0 0 8px" }}>{l.description}</p>}
              {l.capacityUnits != null && <p style={{ fontSize: 11, color: "#94A3B8", margin: "0 0 8px" }}>Capacity: {l.capacityUnits} units</p>}
              <div style={{ display: "flex", gap: 6, marginTop: 8 }}>
                <button onClick={() => setEditing(l)} style={{ background: "none", border: "1px solid #E2E8F0", borderRadius: 6, padding: 5, cursor: "pointer" }}><Pencil size={12} color="#64748B" /></button>
                {l.active ? (
                  <button onClick={() => deactivate.mutate(l.id)} style={{ background: "none", border: "1px solid #E2E8F0", borderRadius: 6, padding: 5, cursor: "pointer" }}><PowerOff size={12} color="#94A3B8" /></button>
                ) : (
                  <button onClick={() => reactivate.mutate(l.id)} style={{ background: "none", border: "1px solid #E2E8F0", borderRadius: 6, padding: 5, cursor: "pointer" }}><Power size={12} color="#059669" /></button>
                )}
                <button onClick={() => { if (confirm(`Delete location ${l.code}?`)) remove.mutate(l.id) }} style={{ background: "none", border: "1px solid #E2E8F0", borderRadius: 6, padding: 5, cursor: "pointer" }}><Trash2 size={12} color="#DC2626" /></button>
              </div>
            </div>
          ))}
        </div>
      )}

      {showForm && <LocationFormModal onClose={() => setShowForm(false)} />}
      {editing && <LocationFormModal initial={editing} onClose={() => setEditing(null)} />}
    </div>
  )
}
