// src/pages/agriculture/AgHealthEventsTab.tsx
//
// Shared animal/group health-event history — covers vaccination, treatment,
// illness/injury observation, and deworming via an eventType discriminator
// (no separate vaccination entity — see AgHealthEvent's own Javadoc). Full
// lifecycle confirmed via AgAnimalController/AgHealthEventService:
// create/list/get/update, PATCH .../complete, PATCH .../acknowledge
// (acknowledges a due-date reminder without recording a new event).
import type React from "react"
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Plus, HeartPulse, Check, BellOff } from "lucide-react"
import { apiClient } from "../../api/client"
import { AG_ACCENT, fmtMoney, statusBadge, type AgTargetType, targetBasePath } from "./constants"

interface HealthEventResponse {
  id: string; animalId: string | null; groupId: string | null
  eventType: string; eventDate: string; description: string
  productUsed: string | null; dosage: string | null
  administeredBy: string | null; administeredByName: string | null
  veterinarian: string | null; cost: number | null
  withdrawalPeriodDays: number | null; nextDueDate: string | null
  status: string; notes: string | null; createdAt: string; updatedAt: string
}
interface Page<T> { content: T[]; totalElements: number }

const EVENT_TYPES = ["VACCINATION", "TREATMENT", "ILLNESS", "INJURY", "DEWORMING", "OTHER"]

export default function AgHealthEventsTab({ targetType, targetId }: { targetType: AgTargetType; targetId: string }) {
  const qc = useQueryClient()
  const base = targetBasePath(targetType, targetId)
  const [showCreate, setShowCreate] = useState(false)
  const [eventType, setEventType] = useState("TREATMENT")
  const [eventDate, setEventDate] = useState(new Date().toISOString().slice(0, 10))
  const [description, setDescription] = useState("")
  const [productUsed, setProductUsed] = useState("")
  const [dosage, setDosage] = useState("")
  const [veterinarian, setVeterinarian] = useState("")
  const [cost, setCost] = useState("")
  const [withdrawalPeriodDays, setWithdrawalPeriodDays] = useState("")
  const [nextDueDate, setNextDueDate] = useState("")

  const queryKey = ["ag-health-events", targetType, targetId]
  const { data, isLoading } = useQuery<Page<HealthEventResponse>>({
    queryKey,
    queryFn: async () => (await apiClient.get(`${base}/health-events`, { params: { size: 100 } })).data,
  })
  const invalidate = () => qc.invalidateQueries({ queryKey })

  const createMut = useMutation({
    mutationFn: () => apiClient.post(`${base}/health-events`, {
      [targetType === "animal" ? "animalId" : "groupId"]: targetId,
      eventType, eventDate, description, productUsed: productUsed || null, dosage: dosage || null,
      administeredBy: null, veterinarian: veterinarian || null, cost: cost ? Number(cost) : null,
      withdrawalPeriodDays: withdrawalPeriodDays ? Number(withdrawalPeriodDays) : null,
      nextDueDate: nextDueDate || null, status: null, notes: null,
    }),
    onSuccess: () => {
      invalidate(); setShowCreate(false)
      setDescription(""); setProductUsed(""); setDosage(""); setVeterinarian(""); setCost(""); setWithdrawalPeriodDays(""); setNextDueDate("")
    },
  })
  const completeMut = useMutation({ mutationFn: (id: string) => apiClient.patch(`/api/v1/agriculture/health-events/${id}/complete`), onSuccess: invalidate })
  const acknowledgeMut = useMutation({ mutationFn: (id: string) => apiClient.patch(`/api/v1/agriculture/health-events/${id}/acknowledge`), onSuccess: invalidate })

  const events = data?.content ?? []

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
        <p style={{ fontSize: 12.5, color: "#64748B", margin: 0 }}>Vaccinations, treatments, illness/injury and deworming — one history, one due-date sweep.</p>
        {!showCreate && <button onClick={() => setShowCreate(true)} style={btnPrimary}><Plus size={13} style={{ marginRight: 4, verticalAlign: -2 }} />Record event</button>}
      </div>

      {showCreate && (
        <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: 14, marginBottom: 14 }}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 2fr", gap: 8, marginBottom: 8 }}>
            <div><label style={lbl}>Type</label><select value={eventType} onChange={e => setEventType(e.target.value)} style={inp}>{EVENT_TYPES.map(t => <option key={t} value={t}>{t}</option>)}</select></div>
            <div><label style={lbl}>Date</label><input type="date" value={eventDate} onChange={e => setEventDate(e.target.value)} style={inp} /></div>
            <div><label style={lbl}>Description</label><input value={description} onChange={e => setDescription(e.target.value)} style={inp} /></div>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 8, marginBottom: 8 }}>
            <div><label style={lbl}>Product used</label><input value={productUsed} onChange={e => setProductUsed(e.target.value)} style={inp} /></div>
            <div><label style={lbl}>Dosage</label><input value={dosage} onChange={e => setDosage(e.target.value)} style={inp} /></div>
            <div><label style={lbl}>Veterinarian</label><input value={veterinarian} onChange={e => setVeterinarian(e.target.value)} style={inp} /></div>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 8, marginBottom: 10 }}>
            <div><label style={lbl}>Cost (R)</label><input type="number" min={0} step="0.01" value={cost} onChange={e => setCost(e.target.value)} style={inp} /></div>
            <div><label style={lbl}>Withdrawal period (days)</label><input type="number" min={0} value={withdrawalPeriodDays} onChange={e => setWithdrawalPeriodDays(e.target.value)} style={inp} /></div>
            <div><label style={lbl}>Next due date</label><input type="date" value={nextDueDate} onChange={e => setNextDueDate(e.target.value)} style={inp} /></div>
          </div>
          <div style={{ display: "flex", gap: 8 }}>
            <button disabled={createMut.isPending || !description.trim()} onClick={() => createMut.mutate()}
              style={{ ...btnPrimary, opacity: createMut.isPending || !description.trim() ? 0.6 : 1 }}>{createMut.isPending ? "Saving…" : "Save"}</button>
            <button onClick={() => setShowCreate(false)} style={btnGhost}>Cancel</button>
          </div>
        </div>
      )}

      {isLoading ? <p style={{ color: "#94A3B8", fontSize: 12.5 }}>Loading…</p> :
        events.length === 0 ? <p style={{ color: "#94A3B8", fontSize: 12.5 }}>No health events recorded yet.</p> : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 10, overflow: "hidden" }}>
          {events.map((e, i) => (
            <div key={e.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "10px 14px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <HeartPulse size={13} color={AG_ACCENT} />
                <div>
                  <p style={{ fontSize: 12.5, color: "#0F172A", fontWeight: 600, margin: 0 }}>{e.eventType} — {e.description}</p>
                  <p style={{ fontSize: 11, color: "#94A3B8", margin: 0 }}>
                    {e.eventDate}{e.veterinarian ? ` · ${e.veterinarian}` : ""}{e.cost != null ? ` · ${fmtMoney(e.cost)}` : ""}{e.nextDueDate ? ` · Next due ${e.nextDueDate}` : ""}
                  </p>
                </div>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                <span style={statusBadge(e.status)}>{e.status}</span>
                {e.status !== "COMPLETED" && (
                  <button onClick={() => completeMut.mutate(e.id)} title="Mark completed" style={iconBtn}><Check size={12} /></button>
                )}
                {e.nextDueDate && (
                  <button onClick={() => acknowledgeMut.mutate(e.id)} title="Acknowledge reminder" style={iconBtn}><BellOff size={12} /></button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

const lbl: React.CSSProperties = { fontSize: 10.5, fontWeight: 600, color: "#374151", marginBottom: 3, display: "block" }
const inp: React.CSSProperties = { width: "100%", padding: "7px 9px", border: "1px solid #E2E8F0", borderRadius: 6, fontSize: 12, boxSizing: "border-box" }
const btnPrimary: React.CSSProperties = { display: "inline-flex", alignItems: "center", padding: "7px 12px", borderRadius: 7, border: "none", background: AG_ACCENT, color: "#fff", fontSize: 12, fontWeight: 700, cursor: "pointer" }
const btnGhost: React.CSSProperties = { padding: "7px 12px", borderRadius: 7, border: "1px solid #E2E8F0", background: "#fff", color: "#64748B", fontSize: 12, fontWeight: 600, cursor: "pointer" }
const iconBtn: React.CSSProperties = { display: "flex", alignItems: "center", justifyContent: "center", width: 24, height: 24, borderRadius: 6, border: "1px solid #E2E8F0", background: "#fff", color: "#64748B", cursor: "pointer" }
