// src/pages/agriculture/AgEnterprisesTab.tsx
//
// Farm-scoped enterprises — business lines within a farm (e.g. "Beef
// Cattle", "Dairy Herd"). Confirmed via AgEnterpriseController: only
// list (GET) and create (POST) endpoints were seen in this research pass
// — no update/deactivate/delete route was confirmed, so this tab is
// list + create only, matching that. Revisit once the full controller is
// read, if the backend does expose more.
import type React from "react"
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Building2, Plus } from "lucide-react"
import { apiClient } from "../../api/client"
import { AG_ACCENT, statusBadge } from "./constants"

export interface EnterpriseResponse {
  id: string
  farmId: string
  name: string
  enterpriseType: string
  speciesFocus: string | null
  startDate: string | null
  status: string
  notes: string | null
  createdAt: string
  updatedAt: string
}
interface Page<T> { content: T[]; totalElements: number }

export default function AgEnterprisesTab({ farmId }: { farmId: string }) {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [name, setName] = useState("")
  const [enterpriseType, setEnterpriseType] = useState("")
  const [speciesFocus, setSpeciesFocus] = useState("")
  const [startDate, setStartDate] = useState("")

  const { data, isLoading } = useQuery<Page<EnterpriseResponse>>({
    queryKey: ["ag-enterprises", farmId],
    queryFn: async () => (await apiClient.get(`/api/v1/agriculture/farms/${farmId}/enterprises`, { params: { size: 200 } })).data,
  })

  const createMut = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/agriculture/farms/${farmId}/enterprises`, {
      farmId, name, enterpriseType, speciesFocus: speciesFocus || null, startDate: startDate || null,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["ag-enterprises", farmId] })
      setShowCreate(false); setName(""); setEnterpriseType(""); setSpeciesFocus(""); setStartDate("")
    },
  })

  const enterprises = data?.content ?? []

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
        <p style={{ fontSize: 13, color: "#64748B", margin: 0 }}>{enterprises.length} enterprise{enterprises.length === 1 ? "" : "s"} on this farm.</p>
        {!showCreate && <button onClick={() => setShowCreate(true)} style={btnPrimary}><Plus size={14} style={{ marginRight: 5, verticalAlign: -2 }} />Add enterprise</button>}
      </div>

      {showCreate && (
        <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 12, padding: 16, marginBottom: 16 }}>
          <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr 1fr 1fr", gap: 10, marginBottom: 12 }}>
            <div><label style={lbl}>Name</label><input value={name} onChange={e => setName(e.target.value)} placeholder="e.g. Beef Cattle" style={inp} /></div>
            <div><label style={lbl}>Enterprise type</label><input value={enterpriseType} onChange={e => setEnterpriseType(e.target.value)} placeholder="LIVESTOCK / CROP" style={inp} /></div>
            <div><label style={lbl}>Species focus</label><input value={speciesFocus} onChange={e => setSpeciesFocus(e.target.value)} style={inp} /></div>
            <div><label style={lbl}>Start date</label><input type="date" value={startDate} onChange={e => setStartDate(e.target.value)} style={inp} /></div>
          </div>
          <div style={{ display: "flex", gap: 8 }}>
            <button disabled={createMut.isPending || !name.trim() || !enterpriseType.trim()} onClick={() => createMut.mutate()}
              style={{ ...btnPrimary, opacity: createMut.isPending || !name.trim() || !enterpriseType.trim() ? 0.6 : 1 }}>
              {createMut.isPending ? "Saving…" : "Save"}
            </button>
            <button onClick={() => setShowCreate(false)} style={btnGhost}>Cancel</button>
          </div>
        </div>
      )}

      {isLoading ? <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p> :
        enterprises.length === 0 ? <p style={{ color: "#94A3B8", fontSize: 13 }}>No enterprises defined yet.</p> : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          {enterprises.map((e, i) => (
            <div key={e.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <Building2 size={15} color={AG_ACCENT} />
                <div>
                  <p style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", margin: 0 }}>{e.name}</p>
                  <p style={{ fontSize: 11, color: "#94A3B8", margin: 0 }}>{e.enterpriseType}{e.speciesFocus ? ` · ${e.speciesFocus}` : ""}{e.startDate ? ` · since ${e.startDate}` : ""}</p>
                </div>
              </div>
              <span style={statusBadge(e.status)}>{e.status}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

const lbl: React.CSSProperties = { fontSize: 11, fontWeight: 600, color: "#374151", marginBottom: 4, display: "block" }
const inp: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 12.5, boxSizing: "border-box" }
const btnPrimary: React.CSSProperties = { display: "inline-flex", alignItems: "center", padding: "8px 14px", borderRadius: 8, border: "none", background: AG_ACCENT, color: "#fff", fontSize: 12.5, fontWeight: 700, cursor: "pointer" }
const btnGhost: React.CSSProperties = { padding: "8px 14px", borderRadius: 8, border: "1px solid #E2E8F0", background: "#fff", color: "#64748B", fontSize: 12.5, fontWeight: 600, cursor: "pointer" }
