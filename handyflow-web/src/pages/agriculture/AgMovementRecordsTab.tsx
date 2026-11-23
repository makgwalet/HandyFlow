// src/pages/agriculture/AgMovementRecordsTab.tsx
//
// Shared animal/group movement history. "Move to another area" really moves the animal (or the whole group) on the server,
// after checking the area is on the same farm; the area is chosen from the farm's own list. A farm transfer moves the animal
// or whole group to the chosen farm. A sale or transfer out takes the animal off the farm (status SOLD / TRANSFERRED_OUT) or
// takes the head off the group, with the reason kept in the group's count history. A group move of fewer head than the group
// has is recorded, but the group stays where it is (the screen says so).
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { useAgToastMutation } from "./agMutation"
import { Plus, ArrowRightLeft, FileText } from "lucide-react"
import AgPager from "./AgPager"
import { PAGE_SIZE, rangeLabel } from "./agPaging.logic"
import { apiClient } from "../../api/client"
import { AG_ACCENT_TEXT, type AgTargetType, targetBasePath, inp, btnPrimary, btnGhost } from "./constants"
import { Field, QueryError } from "./agUi"
import { useAreas, useActiveFarms } from "./agCrops.api"
import { farmChoices } from "./agLifecycle.logic"
import { MOVEMENT_TYPES, areaChoices, movementFormError, isPartGroupMove } from "./agPickers.logic"
import AgManage from "./AgManage"
import { downloadFile } from "./agDownload"
import AgWithdrawalNotice from "./AgWithdrawalNotice"
import { useWithdrawal } from "./agCompliance.api"
import { isMarketMovement, overrideNote, overrideProblem, overrideReasonToSend } from "./agCompliance.logic"
import { todayISO } from "./agCrops.logic"
import { useAuditBatch } from "./agLookups.api"
import { byText } from "./agAudit.logic"

interface MovementRecordResponse {
  id: string; animalId: string | null; groupId: string | null
  movementDate: string; movementType: string
  fromProductionAreaId: string | null; toProductionAreaId: string | null
  fromFarmId: string | null; toFarmId: string | null
  countMoved: number | null; reason: string | null; notes: string | null; createdAt: string
  buyerName?: string | null; destination?: string | null; vehicleRegistration?: string | null
  withdrawalOverrideReason?: string | null; withdrawalHeldUntil?: string | null
}
interface Page<T> { content: T[]; totalElements: number }

export default function AgMovementRecordsTab({ targetType, targetId, farmId, currentAreaId, currentCount }: {
  targetType: AgTargetType; targetId: string; farmId: string; currentAreaId: string | null; currentCount?: number | null
}) {
  const qc = useQueryClient()
  const base = targetBasePath(targetType, targetId)
  const [showCreate, setShowCreate] = useState(false)
  const [movementDate, setMovementDate] = useState(todayISO())
  const [movementType, setMovementType] = useState("AREA_TRANSFER")
  const [toProductionAreaId, setToProductionAreaId] = useState("")
  const [toFarmId, setToFarmId] = useState("")
  const [countMoved, setCountMoved] = useState("")
  const [reason, setReason] = useState("")
  const [formError, setFormError] = useState("")
  const [overrideReason, setOverrideReason] = useState("")
  // who a sale or transfer out went to; printed on the document of identification
  const [buyer, setBuyer] = useState({ name: "", idNumber: "", address: "", destination: "", vehicle: "" })
  const [docBusy, setDocBusy] = useState<string | null>(null)
  const { data: areas = [], isLoading: areasLoading } = useAreas(farmId)
  const choices = areaChoices(areas, currentAreaId)
  const { data: farms = [], isLoading: farmsLoading } = useActiveFarms()
  const farmOptions = farmChoices(farms, farmId)
  const leavesFarm = isMarketMovement(movementType)
  const withdrawal = useWithdrawal(targetType, targetId, movementDate, showCreate && leavesFarm)
  const held = leavesFarm && !!withdrawal.data?.held
  const form = { type: movementType, toAreaId: toProductionAreaId, targetType, countMoved, currentCount: currentCount ?? null, toFarmId, reason }

  const queryKey = ["ag-movement-records", targetType, targetId]
  // 50 a page, newest first; the server never sends more than 200 at a time
  const [page, setPage] = useState(0)
  const { data, isLoading, isError, refetch } = useQuery<Page<MovementRecordResponse>>({
    queryKey: [...queryKey, page],
    placeholderData: prev => prev,
    queryFn: async () => (await apiClient.get(`${base}/movement-records`, { params: { page, size: PAGE_SIZE } })).data,
  })
  const total = data?.totalElements ?? 0

  const createMut = useAgToastMutation({
    mutationFn: () => apiClient.post(`${base}/movement-records`, {
      [targetType === "animal" ? "animalId" : "groupId"]: targetId,
      movementDate, movementType, fromProductionAreaId: null,
      toProductionAreaId: movementType === "AREA_TRANSFER" ? toProductionAreaId : null, fromFarmId: null, toFarmId: movementType === "FARM_TRANSFER" ? toFarmId : null,
      countMoved: countMoved ? Number(countMoved) : null, reason: reason || null, notes: null,
      withdrawalOverrideReason: overrideReasonToSend(held, overrideReason),
      ...(leavesFarm ? { buyerName: buyer.name.trim() || null, buyerIdNumber: buyer.idNumber.trim() || null, buyerAddress: buyer.address.trim() || null, destination: buyer.destination.trim() || null, vehicleRegistration: buyer.vehicle.trim() || null } : {}),
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey })
      // the animal or group has moved: refresh it and the lists it appears in
      qc.invalidateQueries({ queryKey: [targetType === "animal" ? "ag-animal" : "ag-group", targetId] })
      qc.invalidateQueries({ queryKey: [targetType === "animal" ? "ag-animals" : "ag-groups"] })
      setFormError(""); setShowCreate(false); setToProductionAreaId(""); setToFarmId(""); setCountMoved(""); setReason(""); setOverrideReason(""); setBuyer({ name: "", idNumber: "", address: "", destination: "", vehicle: "" })
    },
  })

  const records = data?.content ?? []
  const added = useAuditBatch("movements", records.map(r => r.id))
  const areaName = (id: string | null) => (id ? areas.find(a => a.id === id)?.name ?? "another area" : null)
  const submit = () => {
    const problem = movementFormError(form) || overrideProblem(held, overrideReason)
    if (problem) { setFormError(problem); return }
    setFormError(""); createMut.mutate()
  }

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
        <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0 }}>Transfers between production areas, farms, sales and other movements out.</p>
        {!showCreate && <AgManage><button onClick={() => setShowCreate(true)} style={btnPrimary}><Plus size={13} style={{ marginRight: 4, verticalAlign: -2 }} />Record movement</button></AgManage>}
      </div>

      {showCreate && (
        <div style={{ background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 10, padding: 14, marginBottom: 14 }}>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(170px, 1fr))", gap: 8, marginBottom: 8 }}>
            <Field label="Date"><input type="date" value={movementDate} onChange={e => setMovementDate(e.target.value)} style={inp} /></Field>
            <Field label="Type"><select value={movementType} onChange={e => setMovementType(e.target.value)} style={inp}>{MOVEMENT_TYPES.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}</select></Field>
            {movementType === "AREA_TRANSFER" && (
              <Field label="Move to"><select value={toProductionAreaId} onChange={e => setToProductionAreaId(e.target.value)} style={inp} disabled={areasLoading}>
                  <option value="">{areasLoading ? "Loading areas…" : choices.length === 0 ? "No other areas on this farm" : "Choose an area…"}</option>
                  {choices.map(c => <option key={c.id} value={c.id}>{c.label}</option>)}
                </select></Field>
            )}
            {movementType === "FARM_TRANSFER" && (
              <Field label="Transfer to farm"><select value={toFarmId} onChange={e => setToFarmId(e.target.value)} style={inp} disabled={farmsLoading}>
                  <option value="">{farmsLoading ? "Loading farms…" : farmOptions.length === 0 ? "No other active farms" : "Choose a farm…"}</option>
                  {farmOptions.map(c => <option key={c.id} value={c.id}>{c.label}</option>)}
                </select></Field>
            )}
            {targetType === "group" && movementType !== "FARM_TRANSFER" && <Field label={movementType === "SALE" || movementType === "TRANSFER_OUT" ? "Head leaving (empty = whole group)" : "Count moved (empty = whole group)"}><input type="number" min={1} value={countMoved} onChange={e => setCountMoved(e.target.value)} style={inp} /></Field>}
          </div>
          {isPartGroupMove(form) && <p style={{ fontSize: 12, color: "var(--hf-warning-text)", margin: "0 0 8px" }}>Only part of the group is moving, so this is recorded but the group's own area stays the same.</p>}
          {(movementType === "SALE" || movementType === "TRANSFER_OUT") && (
            <p style={{ fontSize: 12, color: "var(--hf-warning-text)", margin: "0 0 8px" }}>
              {targetType === "animal" ? `This animal will be marked ${movementType === "SALE" ? "sold" : "transferred out"} and will no longer be on the farm.` : "This takes the head off the group. If none are left the group is closed."}
            </p>
          )}
          {movementType === "FARM_TRANSFER" && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 8px" }}>The {targetType} moves to the other farm and leaves its current production area.</p>}
          {leavesFarm && (
            <div style={{ marginBottom: 10 }}>
              <p style={{ fontSize: 11.5, color: "var(--hf-text-muted)", margin: "0 0 6px" }}>Buyer and transport. These go on the document of identification you can print after saving. Anything left empty is printed as a blank line to fill in by hand.</p>
              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(170px, 1fr))", gap: 8, marginBottom: 8 }}>
                <Field label="Buyer name"><input value={buyer.name} maxLength={200} onChange={e => setBuyer({ ...buyer, name: e.target.value })} style={inp} /></Field>
                <Field label="Buyer ID / registration no."><input value={buyer.idNumber} maxLength={60} onChange={e => setBuyer({ ...buyer, idNumber: e.target.value })} style={inp} /></Field>
                <Field label="Vehicle registration"><input value={buyer.vehicle} maxLength={30} onChange={e => setBuyer({ ...buyer, vehicle: e.target.value })} style={inp} /></Field>
              </div>
              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(170px, 1fr))", gap: 8 }}>
                <Field label="Buyer address"><input value={buyer.address} maxLength={300} onChange={e => setBuyer({ ...buyer, address: e.target.value })} style={inp} /></Field>
                <Field label="Destination"><input value={buyer.destination} maxLength={300} onChange={e => setBuyer({ ...buyer, destination: e.target.value })} style={inp} /></Field>
              </div>
            </div>
          )}
          {leavesFarm && <AgWithdrawalNotice status={withdrawal.data} kind="withdrawal period" reason={overrideReason} onReason={setOverrideReason} />}
          {formError && <p role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)", margin: "0 0 8px" }}>{formError}</p>}
          <div style={{ marginBottom: 10 }}><Field label={targetType === "group" && (movementType === "SALE" || movementType === "TRANSFER_OUT") ? "Reason (required, e.g. buyer)" : "Reason"}><input value={reason} onChange={e => setReason(e.target.value)} style={inp} /></Field></div>
          <div style={{ display: "flex", gap: 8 }}>
            <button disabled={createMut.isPending} onClick={submit} style={{ ...btnPrimary, opacity: createMut.isPending ? 0.6 : 1 }}>{createMut.isPending ? "Saving…" : "Save"}</button>
            <button onClick={() => setShowCreate(false)} style={btnGhost}>Cancel</button>
          </div>
        </div>
      )}

      {isLoading ? <p style={{ color: "var(--hf-text-faint)", fontSize: 12.5 }}>Loading…</p> : isError ? <QueryError onRetry={() => void refetch()} /> :
        records.length === 0 ? <p style={{ color: "var(--hf-text-faint)", fontSize: 12.5 }}>No movements recorded yet.</p> : (
        <div style={{ border: "1px solid var(--hf-border)", borderRadius: 10, overflow: "hidden" }}>
          {records.map((r, i) => (
            <div key={r.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "10px 14px", borderTop: i === 0 ? "none" : "1px solid var(--hf-border-subtle)" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <ArrowRightLeft size={13} style={{ color: AG_ACCENT_TEXT, flexShrink: 0 }} />
                <div>
                  <p style={{ fontSize: 12.5, color: "var(--hf-text)", fontWeight: 600, margin: 0 }}>{r.movementType.replace(/_/g, " ")}{r.countMoved ? ` — ${r.countMoved} moved` : ""}</p>
                  <p style={{ fontSize: 11, color: "var(--hf-text-faint)", margin: 0 }}>{r.movementDate}{r.toProductionAreaId ? ` · to ${areaName(r.toProductionAreaId)}` : ""}{r.reason ? ` · ${r.reason}` : ""}{r.buyerName ? ` · Buyer: ${r.buyerName}` : ""}{r.destination ? ` · ${r.destination}` : ""}{byText(added[r.id])}</p>
                  {overrideNote(r.withdrawalOverrideReason, r.withdrawalHeldUntil) && <p style={{ fontSize: 11, color: "var(--hf-warning-text)", margin: "2px 0 0" }}>{overrideNote(r.withdrawalOverrideReason, r.withdrawalHeldUntil)}</p>}
                </div>
              </div>
              {isMarketMovement(r.movementType) && (
                <button type="button" disabled={docBusy === r.id} title="Document of identification (PDF)" style={btnGhost}
                  onClick={async () => { setDocBusy(r.id); await downloadFile(`/api/v1/agriculture/movement-records/${r.id}/document`, {}, "document-of-identification.pdf"); setDocBusy(null) }}>
                  <FileText size={13} style={{ marginRight: 4, verticalAlign: -2 }} />{docBusy === r.id ? "Preparing…" : "Document"}
                </button>
              )}
            </div>
          ))}
        </div>
      )}
      {total > PAGE_SIZE && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "10px 0 0" }}>{rangeLabel(page, total, "movement")}</p>}
      <AgPager page={page} total={total} size={PAGE_SIZE} onPage={setPage} />
    </div>
  )
}

