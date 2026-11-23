// src/pages/agriculture/AgEnterprisesTab.tsx
//
// Farm-scoped enterprises — business lines within a farm (e.g. "Beef
// Cattle", "Dairy Herd"). Confirmed via AgEnterpriseController: only
// list (GET) and create (POST) endpoints were seen in this research pass
// — no update/deactivate/delete route was confirmed, so this tab is
// list + create only, matching that. Revisit once the full controller is
// read, if the backend does expose more.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { useAgToastMutation } from "./agMutation"
import { Building2, Plus } from "lucide-react"
import { apiClient } from "../../api/client"
import { AG_ACCENT_TEXT, statusBadge, inp, btnPrimary, btnGhost } from "./constants"
import { Field, QueryError } from "./agUi"
import AgManage from "./AgManage"

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

  const { data, isLoading, isError, refetch } = useQuery<Page<EnterpriseResponse>>({
    queryKey: ["ag-enterprises", farmId],
    queryFn: async () => (await apiClient.get(`/api/v1/agriculture/farms/${farmId}/enterprises`, { params: { size: 200 } })).data,
  })

  const createMut = useAgToastMutation({
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
        <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: 0 }}>{enterprises.length} enterprise{enterprises.length === 1 ? "" : "s"} on this farm.</p>
        {!showCreate && <AgManage><button onClick={() => setShowCreate(true)} style={btnPrimary}><Plus size={14} style={{ marginRight: 5, verticalAlign: -2 }} />Add enterprise</button></AgManage>}
      </div>

      {showCreate && (
        <div style={{ background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 16 }}>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(170px, 1fr))", gap: 10, marginBottom: 12 }}>
            <Field label="Name"><input value={name} onChange={e => setName(e.target.value)} placeholder="e.g. Beef Cattle" style={inp} /></Field>
            <Field label="Enterprise type"><input value={enterpriseType} onChange={e => setEnterpriseType(e.target.value)} placeholder="LIVESTOCK / CROP" style={inp} /></Field>
            <Field label="Species focus"><input value={speciesFocus} onChange={e => setSpeciesFocus(e.target.value)} style={inp} /></Field>
            <Field label="Start date"><input type="date" value={startDate} onChange={e => setStartDate(e.target.value)} style={inp} /></Field>
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

      {isLoading ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>Loading…</p> : isError ? <QueryError onRetry={() => void refetch()} /> :
        enterprises.length === 0 ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>No enterprises defined yet.</p> : (
        <div style={{ border: "1px solid var(--hf-border)", borderRadius: 12, overflow: "hidden" }}>
          {enterprises.map((e, i) => (
            <div key={e.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid var(--hf-border-subtle)" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <Building2 size={15} style={{ color: AG_ACCENT_TEXT }} />
                <div>
                  <p style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", margin: 0 }}>{e.name}</p>
                  <p style={{ fontSize: 11, color: "var(--hf-text-faint)", margin: 0 }}>{e.enterpriseType}{e.speciesFocus ? ` · ${e.speciesFocus}` : ""}{e.startDate ? ` · since ${e.startDate}` : ""}</p>
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

