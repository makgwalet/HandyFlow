// src/pages/agriculture/AgAnimalDetail.tsx
//
// Single animal's profile plus its six history sub-tabs (shared
// components, targetType="animal") and evidence. Move/status-change/edit/
// delete all confirmed via AgAnimalController.
//
// ⚠ UNVERIFIED: UpdateAnimalRequest's exact field set wasn't seen in
// source (only that PUT /animals/{id} exists) — inferred as
// {name, breed, notes} (identity fields like tagNumber/sex/species are
// assumed immutable after registration, matching how every other
// Increment-1 entity treats its own identity fields). Adjust if the
// backend expects more.
import type React from "react"
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { useAgToastMutation } from "./agMutation"
import { ArrowLeft, PawPrint, Pencil, Trash2 } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { AG_ACCENT, fmtMoney, statusBadge, inp, btnPrimary, btnGhost } from "./constants"
import { agConfirm } from "./agConfirm"
import { Field } from "./agUi"
import { AgTabs } from "./agUi"
import type { AnimalResponse } from "./AgAnimalsTab"
import AgWeightHistoryTab from "./AgWeightHistoryTab"
import AgHealthEventsTab from "./AgHealthEventsTab"
import AgBreedingRecordsTab from "./AgBreedingRecordsTab"
import AgMovementRecordsTab from "./AgMovementRecordsTab"
import AgMortalityRecordsTab from "./AgMortalityRecordsTab"
import AgFeedRecordsTab from "./AgFeedRecordsTab"
import AgEvidenceTab from "./AgEvidenceTab"
import { buildTextPatch } from "./agPatch"
import AgManage from "./AgManage"
import { breedSuggestions } from "./agLookups"
import { useSpeciesNames } from "./agLookups.api"
import { allowedAnimalStatuses, statusLabel } from "./agLifecycle.logic"
import AgWithdrawalNotice from "./AgWithdrawalNotice"
import AgProduceNotice from "./AgProduceNotice"
import { useWithdrawal } from "./agCompliance.api"
import { isMarketStatus, overrideNote, overrideProblem, overrideReasonToSend } from "./agCompliance.logic"
import { todayISO } from "./agCrops.logic"

type SubTab = "weight" | "health" | "breeding" | "movement" | "mortality" | "feed" | "evidence"
const SUB_TABS: { key: SubTab; label: string }[] = [
  { key: "weight", label: "Weight" }, { key: "health", label: "Health" }, { key: "breeding", label: "Breeding" },
  { key: "movement", label: "Movement" }, { key: "mortality", label: "Mortality" }, { key: "feed", label: "Feed" }, { key: "evidence", label: "Evidence" },
]

export default function AgAnimalDetail({ animal, onBack }: { animal: AnimalResponse; onBack: () => void }) {
  const qc = useQueryClient()
  const speciesNames = useSpeciesNames()
  const [sub, setSub] = useState<SubTab>("weight")
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState(animal.name ?? "")
  const [breed, setBreed] = useState(animal.breed ?? "")
  const [notes, setNotes] = useState(animal.notes ?? "")

  const { data: current } = useQuery<AnimalResponse>({
    queryKey: ["ag-animal", animal.id],
    queryFn: async () => (await apiClient.get(`/api/v1/agriculture/animals/${animal.id}`)).data,
    initialData: animal,
  })

  const invalidate = () => qc.invalidateQueries({ queryKey: ["ag-animal", animal.id] })
  const updateMut = useAgToastMutation({
    mutationFn: async () => {
      const patch = buildTextPatch({ name: current?.name, breed: current?.breed, notes: current?.notes }, { name, breed, notes })
      if (patch) await apiClient.put(`/api/v1/agriculture/animals/${animal.id}`, patch)
    },
    onSuccess: () => { invalidate(); setEditing(false) },
  })
  // Selling, culling or transferring out an animal that is in its withdrawal period needs a written reason
  const [pendingStatus, setPendingStatus] = useState<string | null>(null)
  const [overrideReason, setOverrideReason] = useState("")
  const withdrawal = useWithdrawal("animal", animal.id, todayISO(), true)
  const held = !!withdrawal.data?.held
  const statusMut = useAgToastMutation({
    mutationFn: (v: { status: string; reason?: string | null }) =>
      apiClient.patch(`/api/v1/agriculture/animals/${animal.id}/status`, { status: v.status, withdrawalOverrideReason: v.reason ?? null }),
    onSuccess: () => { setPendingStatus(null); setOverrideReason(""); invalidate() },
  })
  const chooseStatus = (status: string) => {
    if (held && isMarketStatus(status)) { setPendingStatus(status); return }
    statusMut.mutate({ status })
  }
  const canManage = usePermission("AGRICULTURE_MANAGE")
  const [breedingError, setBreedingError] = useState("")
  const breedingMut = useAgToastMutation({
    mutationFn: (breedingStock: boolean) => apiClient.patch(`/api/v1/agriculture/animals/${animal.id}/breeding-stock`, { breedingStock }),
    onSuccess: () => { setBreedingError(""); invalidate(); qc.invalidateQueries({ queryKey: ["ag", "profitability"] }) },
    onError: () => setBreedingError("Couldn't update breeding stock. Try again."),
  })
  const deleteMut = useAgToastMutation({
    mutationFn: () => apiClient.delete(`/api/v1/agriculture/animals/${animal.id}`),
    onSuccess: onBack,
  })

  const a = current ?? animal

  return (
    <div>
      <button onClick={onBack} style={backBtn}><ArrowLeft size={13} style={{ marginRight: 5, verticalAlign: -2 }} />Back to animals</button>

      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 16, marginTop: 10 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <div style={{ width: 40, height: 40, borderRadius: 10, background: AG_ACCENT, display: "flex", alignItems: "center", justifyContent: "center" }}>
            <PawPrint size={19} style={{ color: 'var(--hf-text-on-solid)' }} />
          </div>
          <div>
            <h2 style={{ fontSize: 17, fontWeight: 800, color: "var(--hf-text)", margin: 0 }}>{a.tagNumber}{a.name ? ` — ${a.name}` : ""}</h2>
            <p style={{ fontSize: 12, color: "var(--hf-text-faint)", margin: 0 }}>{a.breed ?? "Breed unknown"} · {a.sex}{a.currentWeightKg ? ` · ${a.currentWeightKg} kg` : ""}{a.acquisitionCost ? ` · Acquired ${fmtMoney(a.acquisitionCost)}` : ""}</p>
            {isMarketStatus(a.status) && overrideNote(a.withdrawalOverrideReason, a.withdrawalHeldUntil) && <p style={{ fontSize: 11.5, color: "var(--hf-warning-text)", margin: "2px 0 0" }}>{overrideNote(a.withdrawalOverrideReason, a.withdrawalHeldUntil)}</p>}
          </div>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <select aria-label="Animal status" value={a.status} disabled={!canManage || allowedAnimalStatuses(a.status).length === 0} onChange={e => chooseStatus(e.target.value)} style={{ ...statusBadge(a.status), border: "none", cursor: canManage ? "pointer" : "default" }}>
            {[a.status, ...allowedAnimalStatuses(a.status)].map(s => <option key={s} value={s}>{s === a.status ? s : `Change to ${statusLabel(s)}`}</option>)}
          </select>
          <AgManage>
          <button onClick={() => setEditing(v => !v)} title="Edit" style={iconBtn}><Pencil size={13} /></button>
          <button onClick={async () => { if (await agConfirm({ title: `Delete animal ${a.tagNumber}?`, confirmLabel: "Delete", danger: true })) deleteMut.mutate() }} title="Delete" style={{ ...iconBtn, color: "var(--hf-danger-text)" }}><Trash2 size={13} /></button>
          </AgManage>
        </div>
      </div>

      {pendingStatus && (
        <div style={{ marginBottom: 14, maxWidth: 520 }}>
          <AgWithdrawalNotice status={withdrawal.data} kind="withdrawal period" reason={overrideReason} onReason={setOverrideReason} />
          <p role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text)", margin: "0 0 6px" }}>{overrideProblem(held, overrideReason)}</p>
          <button disabled={statusMut.isPending || !!overrideProblem(held, overrideReason)} onClick={() => statusMut.mutate({ status: pendingStatus, reason: overrideReasonToSend(held, overrideReason) })}
            style={{ padding: "7px 12px", borderRadius: 7, border: "none", background: AG_ACCENT, color: "var(--hf-text-on-solid)", fontSize: 12, fontWeight: 700, cursor: "pointer", marginRight: 8, opacity: statusMut.isPending || overrideProblem(held, overrideReason) ? 0.6 : 1 }}>
            Change to {statusLabel(pendingStatus)}
          </button>
          <button onClick={() => { setPendingStatus(null); setOverrideReason("") }} style={{ padding: "7px 12px", borderRadius: 7, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text-muted)", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>Cancel</button>
        </div>
      )}

      <div style={{ marginBottom: 14, fontSize: 12.5 }}>
        <label style={{ display: "inline-flex", alignItems: "center", gap: 8, fontWeight: 600, color: "var(--hf-text)", cursor: canManage ? "pointer" : "default" }}>
          <input type="checkbox" checked={!!a.breedingStock} disabled={!canManage || breedingMut.isPending} onChange={e => { if (canManage) breedingMut.mutate(e.target.checked) }} />
          Breeding stock
        </label>
        <span style={{ marginLeft: 10, color: "var(--hf-text-muted)" }}>Its purchase price is capital, not a cost of production, so margins leave it out. Its running costs and any sales still count.</span>
        {!canManage && <span style={{ marginLeft: 10, color: "var(--hf-text-faint)" }}>You can see this but not change it.</span>}
        {breedingError && <div role="alert" style={{ color: "var(--hf-danger-text)", marginTop: 4 }}>{breedingError}</div>}
      </div>

      {editing && (
        <div style={{ background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 16 }}>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(170px, 1fr))", gap: 10, marginBottom: 12 }}>
            <Field label="Name"><input value={name} onChange={e => setName(e.target.value)} style={inp} /></Field>
            <><Field label="Breed"><input list="ag-breed-options" value={breed} onChange={e => setBreed(e.target.value)} style={inp} /></Field><datalist id="ag-breed-options">{breedSuggestions(speciesNames[a.speciesId]).map(b => <option key={b} value={b} />)}</datalist></>
            <Field label="Notes"><input value={notes} onChange={e => setNotes(e.target.value)} style={inp} /></Field>
          </div>
          <div style={{ display: "flex", gap: 8 }}>
            <button disabled={updateMut.isPending} onClick={() => updateMut.mutate()} style={{ ...btnPrimary, opacity: updateMut.isPending ? 0.6 : 1 }}>Save</button>
            <button onClick={() => setEditing(false)} style={btnGhost}>Cancel</button>
          </div>
        </div>
      )}

      <AgProduceNotice kind="animal" id={animal.id} />
      <AgTabs label="Animal records" tabs={SUB_TABS} active={sub} onChange={setSub} size="sm" spacing={18} />

      {sub === "weight" && <AgWeightHistoryTab targetType="animal" targetId={a.id} />}
      {sub === "health" && <AgHealthEventsTab targetType="animal" targetId={a.id} />}
      {sub === "breeding" && <AgBreedingRecordsTab targetType="animal" targetId={a.id} />}
      {sub === "movement" && <AgMovementRecordsTab targetType="animal" targetId={a.id} farmId={a.farmId} currentAreaId={a.productionAreaId} />}
      {sub === "mortality" && <AgMortalityRecordsTab targetType="animal" targetId={a.id} />}
      {sub === "feed" && <AgFeedRecordsTab targetType="animal" targetId={a.id} farmId={a.farmId} speciesName={speciesNames[a.speciesId]} />}
      {sub === "evidence" && <AgEvidenceTab targetType="animal" targetId={a.id} />}
    </div>
  )
}

const iconBtn: React.CSSProperties = { display: "flex", alignItems: "center", justifyContent: "center", width: 28, height: 28, borderRadius: 7, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text-muted)", cursor: "pointer" }
const backBtn: React.CSSProperties = { display: "inline-flex", alignItems: "center", background: "none", border: "none", color: "var(--hf-text-muted)", fontSize: 12.5, fontWeight: 600, cursor: "pointer", padding: 0 }
