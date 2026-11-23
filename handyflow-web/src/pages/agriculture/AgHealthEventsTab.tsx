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
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { useAgToastMutation } from "./agMutation"
import { Plus, HeartPulse, Check, BellOff } from "lucide-react"
import AgPager from "./AgPager"
import { PAGE_SIZE, rangeLabel } from "./agPaging.logic"
import { apiClient } from "../../api/client"
import { AG_ACCENT_TEXT, fmtMoney, statusBadge, type AgTargetType, targetBasePath, inp, btnPrimary, btnGhost } from "./constants"
import { Field, QueryError } from "./agUi"
import AgManage from "./AgManage"
import { usePermission } from "../../hooks/usePermission"
import { clearOn, formatDay, periodProblem, withdrawalNote, produceNote } from "./agCompliance.logic"
import { todayISO } from "./agCrops.logic"

interface HealthEventResponse {
  id: string; animalId: string | null; groupId: string | null
  eventType: string; eventDate: string; description: string
  productUsed: string | null; dosage: string | null
  administeredBy: string | null; administeredByName: string | null
  veterinarian: string | null; cost: number | null
  withdrawalPeriodDays: number | null; withdrawalClearOn?: string | null; milkWithdrawalDays?: number | null; eggWithdrawalDays?: number | null; milkClearOn?: string | null; eggClearOn?: string | null; nextDueDate: string | null
  status: string; notes: string | null; createdAt: string; updatedAt: string
}
interface Page<T> { content: T[]; totalElements: number }

const EVENT_TYPES = ["VACCINATION", "TREATMENT", "ILLNESS", "INJURY", "DEWORMING", "OTHER"]

export default function AgHealthEventsTab({ targetType, targetId }: { targetType: AgTargetType; targetId: string }) {
  const canManage = usePermission("AGRICULTURE_MANAGE")
  const qc = useQueryClient()
  const base = targetBasePath(targetType, targetId)
  const [showCreate, setShowCreate] = useState(false)
  const [eventType, setEventType] = useState("TREATMENT")
  const [eventDate, setEventDate] = useState(todayISO())
  const [description, setDescription] = useState("")
  const [productUsed, setProductUsed] = useState("")
  const [dosage, setDosage] = useState("")
  const [veterinarian, setVeterinarian] = useState("")
  const [cost, setCost] = useState("")
  const [withdrawalPeriodDays, setWithdrawalPeriodDays] = useState("")
  const [milkDays, setMilkDays] = useState("")
  const [eggDays, setEggDays] = useState("")
  const [nextDueDate, setNextDueDate] = useState("")

  const queryKey = ["ag-health-events", targetType, targetId]
  // 50 a page, newest first; the server never sends more than 200 at a time
  const [page, setPage] = useState(0)
  const { data, isLoading, isError, refetch } = useQuery<Page<HealthEventResponse>>({
    queryKey: [...queryKey, page],
    placeholderData: prev => prev,
    queryFn: async () => (await apiClient.get(`${base}/health-events`, { params: { page, size: PAGE_SIZE } })).data,
  })
  const total = data?.totalElements ?? 0
  const invalidate = () => qc.invalidateQueries({ queryKey })

  const createMut = useAgToastMutation({
    mutationFn: () => apiClient.post(`${base}/health-events`, {
      [targetType === "animal" ? "animalId" : "groupId"]: targetId,
      eventType, eventDate, description, productUsed: productUsed || null, dosage: dosage || null,
      administeredBy: null, veterinarian: veterinarian || null, cost: cost ? Number(cost) : null,
      withdrawalPeriodDays: withdrawalPeriodDays ? Number(withdrawalPeriodDays) : null,
      milkWithdrawalDays: milkDays ? Number(milkDays) : null, eggWithdrawalDays: eggDays ? Number(eggDays) : null,
      nextDueDate: nextDueDate || null, status: null, notes: null,
    }),
    onSuccess: () => {
      invalidate(); setShowCreate(false)
      setDescription(""); setProductUsed(""); setDosage(""); setVeterinarian(""); setCost(""); setWithdrawalPeriodDays(""); setMilkDays(""); setEggDays(""); setNextDueDate("")
    },
  })
  const completeMut = useAgToastMutation({ mutationFn: (id: string) => apiClient.patch(`/api/v1/agriculture/health-events/${id}/complete`), onSuccess: invalidate })
  const acknowledgeMut = useAgToastMutation({ mutationFn: (id: string) => apiClient.patch(`/api/v1/agriculture/health-events/${id}/acknowledge`), onSuccess: invalidate })

  const events = data?.content ?? []

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
        <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0 }}>Vaccinations, treatments, illness/injury and deworming — one history, one due-date sweep.</p>
        {!showCreate && <AgManage><button onClick={() => setShowCreate(true)} style={btnPrimary}><Plus size={13} style={{ marginRight: 4, verticalAlign: -2 }} />Record event</button></AgManage>}
      </div>

      {showCreate && (
        <div style={{ background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 10, padding: 14, marginBottom: 14 }}>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(170px, 1fr))", gap: 8, marginBottom: 8 }}>
            <Field label="Type"><select value={eventType} onChange={e => setEventType(e.target.value)} style={inp}>{EVENT_TYPES.map(t => <option key={t} value={t}>{t}</option>)}</select></Field>
            <Field label="Date"><input type="date" value={eventDate} onChange={e => setEventDate(e.target.value)} style={inp} /></Field>
            <Field label="Description"><input value={description} onChange={e => setDescription(e.target.value)} style={inp} /></Field>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(170px, 1fr))", gap: 8, marginBottom: 8 }}>
            <Field label="Product used"><input value={productUsed} onChange={e => setProductUsed(e.target.value)} style={inp} /></Field>
            <Field label="Dosage"><input value={dosage} onChange={e => setDosage(e.target.value)} style={inp} /></Field>
            <Field label="Veterinarian"><input value={veterinarian} onChange={e => setVeterinarian(e.target.value)} style={inp} /></Field>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(170px, 1fr))", gap: 8, marginBottom: 10 }}>
            <Field label="Cost (R)"><input type="number" min={0} step="0.01" value={cost} onChange={e => setCost(e.target.value)} style={inp} /></Field>
            <Field label="Withdrawal period (days)" error={periodProblem(withdrawalPeriodDays, "Withdrawal period")}
              hint={clearOn(eventDate, withdrawalPeriodDays) ? `Not for sale or slaughter until ${formatDay(clearOn(eventDate, withdrawalPeriodDays))}` : undefined}>
              <input type="number" min={0} max={730} value={withdrawalPeriodDays} onChange={e => setWithdrawalPeriodDays(e.target.value)} style={inp} />
            </Field>
            <Field label="Milk withdrawal (days)" error={periodProblem(milkDays, "Milk withdrawal")}
              hint={clearOn(eventDate, milkDays) ? `Milk not for sale until ${formatDay(clearOn(eventDate, milkDays))}` : "Dairy animals only"}>
              <input type="number" min={0} max={730} value={milkDays} onChange={e => setMilkDays(e.target.value)} style={inp} />
            </Field>
            <Field label="Egg withdrawal (days)" error={periodProblem(eggDays, "Egg withdrawal")}
              hint={clearOn(eventDate, eggDays) ? `Eggs not for sale until ${formatDay(clearOn(eventDate, eggDays))}` : "Laying birds only"}>
              <input type="number" min={0} max={730} value={eggDays} onChange={e => setEggDays(e.target.value)} style={inp} />
            </Field>
            <Field label="Next due date"><input type="date" value={nextDueDate} onChange={e => setNextDueDate(e.target.value)} style={inp} /></Field>
          </div>
          <div style={{ display: "flex", gap: 8 }}>
            <button disabled={createMut.isPending || !description.trim() || !!(periodProblem(withdrawalPeriodDays, "Withdrawal period") || periodProblem(milkDays, "Milk withdrawal") || periodProblem(eggDays, "Egg withdrawal"))} onClick={() => createMut.mutate()}
              style={{ ...btnPrimary, opacity: createMut.isPending || !description.trim() || periodProblem(withdrawalPeriodDays, "Withdrawal period") || periodProblem(milkDays, "Milk withdrawal") || periodProblem(eggDays, "Egg withdrawal") ? 0.6 : 1 }}>{createMut.isPending ? "Saving…" : "Save"}</button>
            <button onClick={() => setShowCreate(false)} style={btnGhost}>Cancel</button>
          </div>
        </div>
      )}

      {isLoading ? <p style={{ color: "var(--hf-text-faint)", fontSize: 12.5 }}>Loading…</p> : isError ? <QueryError onRetry={() => void refetch()} /> :
        events.length === 0 ? <p style={{ color: "var(--hf-text-faint)", fontSize: 12.5 }}>No health events recorded yet.</p> : (
        <div style={{ border: "1px solid var(--hf-border)", borderRadius: 10, overflow: "hidden" }}>
          {events.map((e, i) => (
            <div key={e.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "10px 14px", borderTop: i === 0 ? "none" : "1px solid var(--hf-border-subtle)" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <HeartPulse size={13} style={{ color: AG_ACCENT_TEXT }} />
                <div>
                  <p style={{ fontSize: 12.5, color: "var(--hf-text)", fontWeight: 600, margin: 0 }}>{e.eventType} — {e.description}</p>
                  <p style={{ fontSize: 11, color: "var(--hf-text-faint)", margin: 0 }}>
                    {e.eventDate}{e.veterinarian ? ` · ${e.veterinarian}` : ""}{e.cost != null ? ` · ${fmtMoney(e.cost)}` : ""}{e.nextDueDate ? ` · Next due ${e.nextDueDate}` : ""}{withdrawalNote(e.withdrawalClearOn, todayISO()) ? ` · ${withdrawalNote(e.withdrawalClearOn, todayISO())}` : ""}{produceNote(e.milkClearOn, e.eggClearOn, todayISO()) ? ` · ${produceNote(e.milkClearOn, e.eggClearOn, todayISO())}` : ""}
                  </p>
                </div>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                <span style={statusBadge(e.status)}>{e.status}</span>
                {canManage && e.status !== "COMPLETED" && (
                  <button onClick={() => completeMut.mutate(e.id)} title="Mark completed" style={iconBtn}><Check size={12} /></button>
                )}
                {canManage && e.nextDueDate && (
                  <button onClick={() => acknowledgeMut.mutate(e.id)} title="Acknowledge reminder" style={iconBtn}><BellOff size={12} /></button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
      {total > PAGE_SIZE && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "10px 0 0" }}>{rangeLabel(page, total, "health event")}</p>}
      <AgPager page={page} total={total} size={PAGE_SIZE} onPage={setPage} />
    </div>
  )
}

const iconBtn: React.CSSProperties = { display: "flex", alignItems: "center", justifyContent: "center", width: 24, height: 24, borderRadius: 6, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text-muted)", cursor: "pointer" }
