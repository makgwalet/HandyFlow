// src/pages/agriculture/AgGroupsTab.tsx
//
// Farm-scoped batch/flock/herd groups — confirmed via AgGroupController.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { useAgToastMutation } from "./agMutation"
import { Users, Plus, ChevronRight, Search } from "lucide-react"
import { apiClient } from "../../api/client"
import { AG_ACCENT_TEXT, statusBadge, inp, btnPrimary, btnGhost } from "./constants"
import { Field, EmptyNote, QueryError } from "./agUi"
import { formGrid, rowButtonProps } from "./agUi.logic"
import AgManage from "./AgManage"
import { breedSuggestions, ownBreeds, withOwn } from "./agLookups"
import { useFarmSuggestions } from "./agLookups.api"
import AgPager from "./AgPager"
import { useDebounced } from "./useDebounced"
import { PAGE_SIZE, lastPage, rangeLabel, searchParam } from "./agPaging.logic"
import { todayISO } from "./agCrops.logic"

export interface GroupResponse {
  id: string; farmId: string; productionAreaId: string | null; enterpriseId: string | null
  speciesId: string; batchNumber: string; breed: string | null
  initialCount: number; currentCount: number; averageWeightKg: number | null
  originDate: string; acquisitionType: string; status: string; notes: string | null
  createdAt: string; updatedAt: string
}
interface SpeciesOption { id: string; name: string }
interface Page<T> { content: T[]; totalElements: number }

const ACQUISITION_TYPES = ["BORN_ON_FARM", "PURCHASED", "TRANSFERRED_IN"]

export default function AgGroupsTab({ farmId, onSelectGroup }: { farmId: string; onSelectGroup: (g: GroupResponse) => void }) {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [statusFilter, setStatusFilter] = useState("ACTIVE")
  const [search, setSearch] = useState("")
  const [page, setPage] = useState(0)
  const q = searchParam(useDebounced(search))

  const [speciesId, setSpeciesId] = useState("")
  const [batchNumber, setBatchNumber] = useState("")
  const [breed, setBreed] = useState("")
  const [initialCount, setInitialCount] = useState("")
  const [originDate, setOriginDate] = useState(todayISO())
  const [acquisitionType, setAcquisitionType] = useState("BORN_ON_FARM")

  const { data: speciesData } = useQuery<Page<SpeciesOption>>({
    queryKey: ["ag-species", "picker"],
    queryFn: async () => (await apiClient.get("/api/v1/agriculture/species", { params: { size: 200 } })).data,
  })
  const { data, isLoading, isError, refetch, isFetching } = useQuery<Page<GroupResponse>>({
    queryKey: ["ag-groups", farmId, statusFilter, q ?? "", page],
    placeholderData: prev => prev,
    queryFn: async () => (await apiClient.get(`/api/v1/agriculture/farms/${farmId}/groups`, { params: { status: statusFilter || undefined, q, page, size: PAGE_SIZE } })).data,
  })
  const total = data?.totalElements ?? 0
  const pastEnd = !!data && total > 0 && page > lastPage(total)   // the rows on this page were all deleted or moved

  const createMut = useAgToastMutation({
    mutationFn: () => apiClient.post(`/api/v1/agriculture/farms/${farmId}/groups`, {
      farmId, productionAreaId: null, enterpriseId: null, speciesId, batchNumber, breed: breed || null,
      initialCount: Number(initialCount), originDate, acquisitionType,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["ag-groups", farmId] })
      setShowCreate(false); setBatchNumber(""); setBreed(""); setInitialCount("")
    },
  })

  const species = speciesData?.content ?? []
  const groups = data?.content ?? []
  const farmSuggestions = useFarmSuggestions(farmId)

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14, flexWrap: "wrap", gap: 10 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
          <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: 0 }}>{data ? rangeLabel(page, total, "group", q) : "Loading…"}</p>
          <div style={{ position: "relative" }}>
            <Search size={13} style={{ position: "absolute", left: 9, top: 10, color: "var(--hf-text-faint)" }} />
            <input type="search" value={search} maxLength={60} placeholder="Batch number or breed" aria-label="Search groups"
              onChange={e => { setSearch(e.target.value); setPage(0) }} style={{ ...inp, width: 200, paddingLeft: 28 }} />
          </div>
          <select value={statusFilter} onChange={e => { setStatusFilter(e.target.value); setPage(0) }} style={{ ...inp, width: 140 }}>
            <option value="">All statuses</option><option value="ACTIVE">ACTIVE</option><option value="CLOSED">CLOSED</option>
          </select>
        </div>
        {!showCreate && <AgManage><button onClick={() => setShowCreate(true)} style={btnPrimary}><Plus size={14} style={{ marginRight: 5, verticalAlign: -2 }} />Register group</button></AgManage>}
      </div>

      {showCreate && (
        <div style={{ background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 16 }}>
          <div style={{ ...formGrid, marginBottom: 10 }}>
            <Field label="Species"><select value={speciesId} onChange={e => setSpeciesId(e.target.value)} style={inp}>
                <option value="">Select…</option>{species.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select></Field>
            <Field label="Batch number"><input value={batchNumber} onChange={e => setBatchNumber(e.target.value)} style={inp} /></Field>
            <><Field label="Breed"><input list="ag-breed-options" value={breed} onChange={e => setBreed(e.target.value)} style={inp} /></Field><datalist id="ag-breed-options">{withOwn(breedSuggestions(species.find(s => s.id === speciesId)?.name), [...ownBreeds(farmSuggestions, speciesId), ...groups.filter(g => g.speciesId === speciesId).map(g => g.breed)]).map(b => <option key={b} value={b} />)}</datalist></>
          </div>
          <div style={{ ...formGrid, marginBottom: 12 }}>
            <Field label="Initial count"><input type="number" min={1} value={initialCount} onChange={e => setInitialCount(e.target.value)} style={inp} /></Field>
            <Field label="Origin date"><input type="date" value={originDate} onChange={e => setOriginDate(e.target.value)} style={inp} /></Field>
            <Field label="Acquisition"><select value={acquisitionType} onChange={e => setAcquisitionType(e.target.value)} style={inp}>{ACQUISITION_TYPES.map(t => <option key={t} value={t}>{t}</option>)}</select></Field>
          </div>
          <div style={{ display: "flex", gap: 8 }}>
            <button disabled={createMut.isPending || !speciesId || !batchNumber.trim() || !initialCount} onClick={() => createMut.mutate()}
              style={{ ...btnPrimary, opacity: createMut.isPending || !speciesId || !batchNumber.trim() || !initialCount ? 0.6 : 1 }}>{createMut.isPending ? "Saving…" : "Save"}</button>
            <button onClick={() => setShowCreate(false)} style={btnGhost}>Cancel</button>
          </div>
        </div>
      )}

      {isLoading ? <EmptyNote>Loading…</EmptyNote> : isError ? <QueryError onRetry={() => void refetch()} /> :
        groups.length === 0 ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>{pastEnd ? <>This page no longer has any groups. <button type="button" onClick={() => setPage(lastPage(total))} style={{ ...btnGhost, padding: "4px 10px" }}>Go to the last page</button></> : q || statusFilter !== "ACTIVE" ? "Nothing matches. Clear the search or change the status." : "No groups registered yet."}</p> : (
        <div style={{ border: "1px solid var(--hf-border)", borderRadius: 12, overflow: "hidden", opacity: isFetching ? 0.7 : 1 }}>
          {groups.map((g, i) => (
            <div key={g.id} {...rowButtonProps(() => onSelectGroup(g), `Open group ${g.batchNumber}`)}
              style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid var(--hf-border-subtle)", cursor: "pointer" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <Users size={15} style={{ color: AG_ACCENT_TEXT }} />
                <div>
                  <p style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", margin: 0 }}>{g.batchNumber}</p>
                  <p style={{ fontSize: 11, color: "var(--hf-text-faint)", margin: 0 }}>{g.breed ?? "—"} · {g.currentCount}/{g.initialCount} head{g.averageWeightKg ? ` · avg ${g.averageWeightKg} kg` : ""}</p>
                </div>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <span style={statusBadge(g.status)}>{g.status}</span>
                <ChevronRight size={14} style={{ color: 'var(--hf-text-faint)' }} />
              </div>
            </div>
          ))}
        </div>
      )}
      <AgPager page={page} total={total} size={PAGE_SIZE} onPage={setPage} />
    </div>
  )
}

