// src/pages/agriculture/AgAnimalsTab.tsx
//
// Farm-scoped, individually-tracked animals — confirmed via
// AgAnimalController. Species must exist in the tenant catalogue first
// (AgSpeciesTab); this tab fetches the active species list for the
// create-form picker.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { useAgToastMutation } from "./agMutation"
import { PawPrint, Plus, ChevronRight, Search } from "lucide-react"
import { apiClient } from "../../api/client"
import { AG_ACCENT_TEXT, statusBadge, inp, btnPrimary, btnGhost } from "./constants"
import { Field, EmptyNote, QueryError } from "./agUi"
import { formGrid, rowButtonProps } from "./agUi.logic"
import AgManage from "./AgManage"
import { breedSuggestions } from "./agLookups"
import AgPager from "./AgPager"
import { useDebounced } from "./useDebounced"
import { PAGE_SIZE, lastPage, rangeLabel, searchParam } from "./agPaging.logic"
import { todayISO } from "./agCrops.logic"

export interface AnimalResponse {
  id: string; farmId: string; productionAreaId: string | null; enterpriseId: string | null
  speciesId: string; tagNumber: string; name: string | null; breed: string | null
  sex: string; dateOfBirth: string | null; estimatedAge: boolean
  sireId: string | null; damId: string | null
  acquisitionType: string; acquisitionDate: string; acquisitionCost: number | null
  currentWeightKg: number | null; status: string; notes: string | null
  withdrawalOverrideReason?: string | null; withdrawalHeldUntil?: string | null
  breedingStock: boolean   // capital, not a production cost: its purchase price is left out of margins (ADR-001 decision 7)
  createdAt: string; updatedAt: string
}
interface SpeciesOption { id: string; name: string }
interface Page<T> { content: T[]; totalElements: number }
const SPECIES_PAGE = { size: 200 }   // the catalogue is short; animals and groups are what can run to hundreds

const SEXES = ["MALE", "FEMALE", "CASTRATED"]
const ACQUISITION_TYPES = ["BORN_ON_FARM", "PURCHASED", "TRANSFERRED_IN"]

export default function AgAnimalsTab({ farmId, onSelectAnimal }: { farmId: string; onSelectAnimal: (a: AnimalResponse) => void }) {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [statusFilter, setStatusFilter] = useState("ACTIVE")
  const [search, setSearch] = useState("")
  const [page, setPage] = useState(0)
  const q = searchParam(useDebounced(search))

  const [speciesId, setSpeciesId] = useState("")
  const [tagNumber, setTagNumber] = useState("")
  const [name, setName] = useState("")
  const [breed, setBreed] = useState("")
  const [sex, setSex] = useState("FEMALE")
  const [dateOfBirth, setDateOfBirth] = useState("")
  const [acquisitionType, setAcquisitionType] = useState("BORN_ON_FARM")
  const [acquisitionDate, setAcquisitionDate] = useState(todayISO())
  const [acquisitionCost, setAcquisitionCost] = useState("")

  const { data: speciesData } = useQuery<Page<SpeciesOption>>({
    queryKey: ["ag-species", "picker"],
    queryFn: async () => (await apiClient.get("/api/v1/agriculture/species", { params: SPECIES_PAGE })).data,
  })
  const { data, isLoading, isError, refetch, isFetching } = useQuery<Page<AnimalResponse>>({
    queryKey: ["ag-animals", farmId, statusFilter, q ?? "", page],
    placeholderData: prev => prev,
    queryFn: async () => (await apiClient.get(`/api/v1/agriculture/farms/${farmId}/animals`, { params: { status: statusFilter || undefined, q, page, size: PAGE_SIZE } })).data,
  })
  const total = data?.totalElements ?? 0
  const pastEnd = !!data && total > 0 && page > lastPage(total)   // the rows on this page were all deleted or moved

  const createMut = useAgToastMutation({
    mutationFn: () => apiClient.post(`/api/v1/agriculture/farms/${farmId}/animals`, {
      farmId, productionAreaId: null, enterpriseId: null, speciesId, tagNumber, name: name || null,
      breed: breed || null, sex, dateOfBirth: dateOfBirth || null, estimatedAge: !dateOfBirth,
      sireId: null, damId: null, acquisitionType, acquisitionDate,
      acquisitionCost: acquisitionCost ? Number(acquisitionCost) : null,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["ag-animals", farmId] })
      setShowCreate(false); setTagNumber(""); setName(""); setBreed(""); setDateOfBirth(""); setAcquisitionCost("")
    },
  })

  const species = speciesData?.content ?? []
  const animals = data?.content ?? []

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14, flexWrap: "wrap", gap: 10 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
          <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: 0 }}>{data ? rangeLabel(page, total, "animal", q) : "Loading…"}</p>
          <div style={{ position: "relative" }}>
            <Search size={13} style={{ position: "absolute", left: 9, top: 10, color: "var(--hf-text-faint)" }} />
            <input type="search" value={search} maxLength={60} placeholder="Tag, name or breed" aria-label="Search animals"
              onChange={e => { setSearch(e.target.value); setPage(0) }} style={{ ...inp, width: 200, paddingLeft: 28 }} />
          </div>
          <select value={statusFilter} onChange={e => { setStatusFilter(e.target.value); setPage(0) }} style={{ ...inp, width: 140 }}>
            <option value="">All statuses</option>
            <option value="ACTIVE">ACTIVE</option><option value="SOLD">SOLD</option>
            <option value="DECEASED">DECEASED</option><option value="CULLED">CULLED</option>
          </select>
        </div>
        {!showCreate && <AgManage><button onClick={() => setShowCreate(true)} style={btnPrimary}><Plus size={14} style={{ marginRight: 5, verticalAlign: -2 }} />Register animal</button></AgManage>}
      </div>

      {showCreate && (
        <div style={{ background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 16 }}>
          <div style={{ ...formGrid, marginBottom: 10 }}>
            <Field label="Species"><select value={speciesId} onChange={e => setSpeciesId(e.target.value)} style={inp}>
                <option value="">Select…</option>{species.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select></Field>
            <Field label="Tag number"><input value={tagNumber} onChange={e => setTagNumber(e.target.value)} style={inp} /></Field>
            <Field label="Name (optional)"><input value={name} onChange={e => setName(e.target.value)} style={inp} /></Field>
            <><Field label="Breed"><input list="ag-breed-options" value={breed} onChange={e => setBreed(e.target.value)} style={inp} /></Field><datalist id="ag-breed-options">{breedSuggestions(species.find(s => s.id === speciesId)?.name).map(b => <option key={b} value={b} />)}</datalist></>
          </div>
          <div style={{ ...formGrid, marginBottom: 12 }}>
            <Field label="Sex"><select value={sex} onChange={e => setSex(e.target.value)} style={inp}>{SEXES.map(s => <option key={s} value={s}>{s}</option>)}</select></Field>
            <Field label="Date of birth"><input type="date" value={dateOfBirth} onChange={e => setDateOfBirth(e.target.value)} style={inp} /></Field>
            <Field label="Acquisition"><select value={acquisitionType} onChange={e => setAcquisitionType(e.target.value)} style={inp}>{ACQUISITION_TYPES.map(t => <option key={t} value={t}>{t}</option>)}</select></Field>
            <Field label="Acquisition date"><input type="date" value={acquisitionDate} onChange={e => setAcquisitionDate(e.target.value)} style={inp} /></Field>
            <Field label="Cost (R)"><input type="number" min={0} step="0.01" value={acquisitionCost} onChange={e => setAcquisitionCost(e.target.value)} style={inp} /></Field>
          </div>
          <div style={{ display: "flex", gap: 8 }}>
            <button disabled={createMut.isPending || !speciesId || !tagNumber.trim()} onClick={() => createMut.mutate()}
              style={{ ...btnPrimary, opacity: createMut.isPending || !speciesId || !tagNumber.trim() ? 0.6 : 1 }}>{createMut.isPending ? "Saving…" : "Save"}</button>
            <button onClick={() => setShowCreate(false)} style={btnGhost}>Cancel</button>
          </div>
        </div>
      )}

      {isLoading ? <EmptyNote>Loading…</EmptyNote> : isError ? <QueryError onRetry={() => void refetch()} /> :
        animals.length === 0 ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>{pastEnd ? <>This page no longer has any animals. <button type="button" onClick={() => setPage(lastPage(total))} style={{ ...btnGhost, padding: "4px 10px" }}>Go to the last page</button></> : q || statusFilter !== "ACTIVE" ? "Nothing matches. Clear the search or change the status." : "No animals registered yet."}</p> : (
        <div style={{ border: "1px solid var(--hf-border)", borderRadius: 12, overflow: "hidden", opacity: isFetching ? 0.7 : 1 }}>
          {animals.map((a, i) => (
            <div key={a.id} {...rowButtonProps(() => onSelectAnimal(a), `Open animal ${a.tagNumber}`)}
              style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid var(--hf-border-subtle)", cursor: "pointer" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <PawPrint size={15} style={{ color: AG_ACCENT_TEXT }} />
                <div>
                  <p style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", margin: 0 }}>{a.tagNumber}{a.name ? ` — ${a.name}` : ""}</p>
                  <p style={{ fontSize: 11, color: "var(--hf-text-faint)", margin: 0 }}>{a.breed ?? "—"} · {a.sex}{a.currentWeightKg ? ` · ${a.currentWeightKg} kg` : ""}</p>
                </div>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <span style={statusBadge(a.status)}>{a.status}</span>
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

