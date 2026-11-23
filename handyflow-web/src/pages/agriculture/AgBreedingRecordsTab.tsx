// src/pages/agriculture/AgBreedingRecordsTab.tsx
//
// Shared animal/group breeding history — full lifecycle confirmed via
// AgAnimalController/AgBreedingRecordService: create/list/get, then
// PATCH .../confirm-pregnant, .../not-pregnant, .../record-birth,
// .../aborted, .../failed. All PATCH endpoints are addressed by the
// record's own id alone (not nested under /animals/ or /groups/) — see
// AgAnimalController's own class Javadoc for why.
//
// Buttons follow the record's outcome (see agBreeding.logic.ts, which mirrors AgBreedingRules on the server):
// a new record is PENDING and offers every action; a confirmed one offers birth/aborted/failed; final ones offer none.
// A birth records how many were born alive and how many stillborn.
import type React from "react"
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Plus, Baby, Check, X as XIcon, Ban } from "lucide-react"
import AgPager from "./AgPager"
import { PAGE_SIZE, rangeLabel } from "./agPaging.logic"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { AG_ACCENT_TEXT, statusBadge, type AgTargetType, targetBasePath, inp, btnPrimary, btnGhost } from "./constants"
import { Field, QueryError } from "./agUi"
import { apiErrorMessage } from "./agErrors"
import { BREEDING_TYPES, breedingActions, breedingTypeLabel, outcomeLabel, birthFormError, birthPayload } from "./agBreeding.logic"

interface BreedingRecordResponse {
  id: string; animalId: string | null; groupId: string | null
  breedingType: string; matingDate: string; sireId: string | null; sireDescription: string | null
  expectedDueDate: string | null; actualBirthDate: string | null
  outcome: string | null; offspringCount: number | null; stillbornCount: number | null
  notes: string | null; createdAt: string; updatedAt: string
}
interface Page<T> { content: T[]; totalElements: number }

export default function AgBreedingRecordsTab({ targetType, targetId }: { targetType: AgTargetType; targetId: string }) {
  const qc = useQueryClient()
  const base = targetBasePath(targetType, targetId)
  const [showCreate, setShowCreate] = useState(false)
  const [breedingType, setBreedingType] = useState("NATURAL")
  const [matingDate, setMatingDate] = useState(new Date().toISOString().slice(0, 10))
  const [sireDescription, setSireDescription] = useState("")
  const [expectedDueDate, setExpectedDueDate] = useState("")

  const [birthing, setBirthing] = useState<BreedingRecordResponse | null>(null)
  const [actualBirthDate, setActualBirthDate] = useState(new Date().toISOString().slice(0, 10))
  const [liveCount, setLiveCount] = useState("1")
  const [stillbornCount, setStillbornCount] = useState("")
  const [error, setError] = useState("")
  const canManage = usePermission("AGRICULTURE_MANAGE")
  const today = new Date().toISOString().slice(0, 10)

  const queryKey = ["ag-breeding-records", targetType, targetId]
  // 50 a page, newest first; the server never sends more than 200 at a time
  const [page, setPage] = useState(0)
  const { data, isLoading, isError, refetch } = useQuery<Page<BreedingRecordResponse>>({
    queryKey: [...queryKey, page],
    placeholderData: prev => prev,
    queryFn: async () => (await apiClient.get(`${base}/breeding-records`, { params: { page, size: PAGE_SIZE } })).data,
  })
  const total = data?.totalElements ?? 0
  const invalidate = () => qc.invalidateQueries({ queryKey })
  const onError = (e: unknown) => setError(apiErrorMessage(e))
  const done = () => { setError(""); invalidate() }

  const createMut = useMutation({
    mutationFn: () => apiClient.post(`${base}/breeding-records`, {
      [targetType === "animal" ? "animalId" : "groupId"]: targetId,
      breedingType, matingDate, sireId: null, sireDescription: sireDescription || null,
      expectedDueDate: expectedDueDate || null, notes: null,
    }),
    onSuccess: () => { done(); setShowCreate(false); setSireDescription(""); setExpectedDueDate("") },
    onError,
  })
  const confirmPregnantMut = useMutation({
    mutationFn: (v: { id: string; expectedDueDate: string | null }) =>
      apiClient.patch(`/api/v1/agriculture/breeding-records/${v.id}/confirm-pregnant`, { expectedDueDate: v.expectedDueDate }),
    onSuccess: done, onError,
  })
  const notPregnantMut = useMutation({ mutationFn: (id: string) => apiClient.patch(`/api/v1/agriculture/breeding-records/${id}/not-pregnant`), onSuccess: done, onError })
  const abortedMut = useMutation({ mutationFn: (id: string) => apiClient.patch(`/api/v1/agriculture/breeding-records/${id}/aborted`), onSuccess: done, onError })
  const failedMut = useMutation({ mutationFn: (id: string) => apiClient.patch(`/api/v1/agriculture/breeding-records/${id}/failed`), onSuccess: done, onError })
  const recordBirthMut = useMutation({
    mutationFn: () => apiClient.patch(`/api/v1/agriculture/breeding-records/${birthing!.id}/record-birth`, {
      ...birthPayload({ date: actualBirthDate, live: liveCount, stillborn: stillbornCount }),
    }),
    onSuccess: () => { done(); setBirthing(null) },
    onError,
  })

  const records = data?.content ?? []
  const submitBirth = () => {
    if (!birthing) return
    const problem = birthFormError({ date: actualBirthDate, live: liveCount, stillborn: stillbornCount }, birthing.matingDate, today)
    if (problem) { setError(problem); return }
    setError("")
    recordBirthMut.mutate()
  }

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
        <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0 }}>Mating → pregnancy confirmation → birth outcome.</p>
        {canManage && !showCreate && <button onClick={() => setShowCreate(true)} style={btnPrimary}><Plus size={13} style={{ marginRight: 4, verticalAlign: -2 }} />Record mating</button>}
      </div>

      {error && <p role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)", background: "var(--hf-danger-soft)", borderRadius: 8, padding: "8px 12px", margin: "0 0 12px" }}>{error}</p>}

      {showCreate && (
        <div style={{ background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 10, padding: 14, marginBottom: 14 }}>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(170px, 1fr))", gap: 8, marginBottom: 10 }}>
            <Field label="Type"><select value={breedingType} onChange={e => setBreedingType(e.target.value)} style={inp}>{BREEDING_TYPES.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}</select></Field>
            <Field label="Mating date"><input type="date" max={today} value={matingDate} onChange={e => setMatingDate(e.target.value)} style={inp} /></Field>
            <Field label="Sire"><input value={sireDescription} onChange={e => setSireDescription(e.target.value)} placeholder="description or tag" style={inp} /></Field>
            <Field label="Expected due date"><input type="date" min={matingDate} value={expectedDueDate} onChange={e => setExpectedDueDate(e.target.value)} style={inp} /></Field>
          </div>
          <div style={{ display: "flex", gap: 8 }}>
            <button disabled={createMut.isPending} onClick={() => createMut.mutate()} style={{ ...btnPrimary, opacity: createMut.isPending ? 0.6 : 1 }}>{createMut.isPending ? "Saving…" : "Save"}</button>
            <button onClick={() => setShowCreate(false)} style={btnGhost}>Cancel</button>
          </div>
        </div>
      )}

      {isLoading ? <p style={{ color: "var(--hf-text-faint)", fontSize: 12.5 }}>Loading…</p> : isError ? <QueryError onRetry={() => void refetch()} /> :
        records.length === 0 ? <p style={{ color: "var(--hf-text-faint)", fontSize: 12.5 }}>No breeding records yet.</p> : (
        <div style={{ border: "1px solid var(--hf-border)", borderRadius: 10, overflow: "hidden" }}>
          {records.map((r, i) => (
            <div key={r.id}>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "10px 14px", borderTop: i === 0 ? "none" : "1px solid var(--hf-border-subtle)" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                  <Baby size={13} style={{ color: AG_ACCENT_TEXT }} />
                  <div>
                    <p style={{ fontSize: 12.5, color: "var(--hf-text)", fontWeight: 600, margin: 0 }}>{breedingTypeLabel(r.breedingType)}{r.sireDescription ? ` · ${r.sireDescription}` : ""}</p>
                    <p style={{ fontSize: 11, color: "var(--hf-text-faint)", margin: 0 }}>
                      Mated {r.matingDate}{r.expectedDueDate ? ` · Due ${r.expectedDueDate}` : ""}
                      {r.actualBirthDate ? ` · Born ${r.actualBirthDate}: ${r.offspringCount ?? 0} alive${r.stillbornCount ? `, ${r.stillbornCount} stillborn` : ""}` : ""}
                    </p>
                  </div>
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                  <span style={statusBadge(r.outcome ?? "PENDING")}>{outcomeLabel(r.outcome)}</span>
                  {canManage && (() => {
                    const act = breedingActions(r.outcome)
                    return (
                      <>
                        {act.confirm && <button onClick={() => confirmPregnantMut.mutate({ id: r.id, expectedDueDate: r.expectedDueDate })} title="Confirm pregnant" aria-label="Confirm pregnant" style={iconBtn}><Check size={12} /></button>}
                        {act.notPregnant && <button onClick={() => notPregnantMut.mutate(r.id)} title="Not pregnant" aria-label="Not pregnant" style={iconBtn}><XIcon size={12} /></button>}
                        {act.birth && <button onClick={() => { setBirthing(r); setError("") }} title="Record birth" aria-label="Record birth" style={iconBtn}><Baby size={12} /></button>}
                        {act.aborted && <button onClick={() => abortedMut.mutate(r.id)} title="Mark aborted" aria-label="Mark aborted" style={iconBtn}><Ban size={12} /></button>}
                        {act.failed && <button onClick={() => failedMut.mutate(r.id)} title="Mark failed" aria-label="Mark failed" style={{ ...iconBtn, color: "var(--hf-danger-text)" }}><XIcon size={12} /></button>}
                      </>
                    )
                  })()}
                </div>
              </div>
              {birthing?.id === r.id && (
                <div style={{ padding: "0 14px 14px", display: "flex", gap: 8, alignItems: "flex-end" }}>
                  <Field label="Birth date"><input type="date" min={birthing.matingDate} max={today} value={actualBirthDate} onChange={e => setActualBirthDate(e.target.value)} style={inp} /></Field>
                  <Field label="Born alive"><input type="number" min={0} max={100} value={liveCount} onChange={e => setLiveCount(e.target.value)} style={{ ...inp, width: 90 }} /></Field>
                  <Field label="Stillborn"><input type="number" min={0} max={100} value={stillbornCount} onChange={e => setStillbornCount(e.target.value)} style={{ ...inp, width: 90 }} /></Field>
                  <button disabled={recordBirthMut.isPending} onClick={() => submitBirth()} style={{ ...btnPrimary, opacity: recordBirthMut.isPending ? 0.6 : 1 }}>Save</button>
                  <button onClick={() => setBirthing(null)} style={btnGhost}>Cancel</button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
      {total > PAGE_SIZE && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "10px 0 0" }}>{rangeLabel(page, total, "breeding record")}</p>}
      <AgPager page={page} total={total} size={PAGE_SIZE} onPage={setPage} />
    </div>
  )
}

const iconBtn: React.CSSProperties = { display: "flex", alignItems: "center", justifyContent: "center", width: 24, height: 24, borderRadius: 6, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text-muted)", cursor: "pointer" }
