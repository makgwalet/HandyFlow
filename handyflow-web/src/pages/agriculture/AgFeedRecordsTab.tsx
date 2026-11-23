// src/pages/agriculture/AgFeedRecordsTab.tsx
//
// Shared animal/group feed history. Feed can be drawn from one of the farm's stock items, chosen from a list: the kilograms
// fed are taken off the item (converted into its own unit), the feed name and cost per kg are filled in from the item, and
// the screen warns before saving if there is not enough on hand. A bag, bale or sack item can be used once it has a kg per
// bag (set in Stock): the feed can then be typed in kg or in bags, and the other is shown beside it.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { useAgToastMutation } from "./agMutation"
import { Plus, Wheat } from "lucide-react"
import AgPager from "./AgPager"
import { PAGE_SIZE, rangeLabel } from "./agPaging.logic"
import { apiClient } from "../../api/client"
import { AG_ACCENT_TEXT, fmtMoney, type AgTargetType, targetBasePath, inp, btnPrimary, btnGhost } from "./constants"
import { Field, QueryError } from "./agUi"
import { useInventory } from "./agCrops.api"
import { feedChoices, feedEquivalent, feedPrefill, feedQuantityPayload, feedStockProblem, bagsToKg, hasPackSize, unitSingular, type FeedEntry } from "./agPickers.logic"
import AgManage from "./AgManage"
import { feedSuggestions, withOwn } from "./agLookups"
import { useAuditBatch, useFarmSuggestions } from "./agLookups.api"
import { byText } from "./agAudit.logic"

interface FeedRecordResponse {
  id: string; animalId: string | null; groupId: string | null
  feedDate: string; inventoryItemId: string | null; feedType: string
  quantityKg: number; costPerKg: number | null; totalCost: number | null
  notes: string | null; createdAt: string
}
interface Page<T> { content: T[]; totalElements: number }

export default function AgFeedRecordsTab({ targetType, targetId, farmId, speciesName }: { targetType: AgTargetType; targetId: string; farmId: string; speciesName?: string }) {
  const qc = useQueryClient()
  const base = targetBasePath(targetType, targetId)
  const [showCreate, setShowCreate] = useState(false)
  const [feedDate, setFeedDate] = useState(new Date().toISOString().slice(0, 10))
  const [inventoryItemId, setInventoryItemId] = useState("")
  const [feedType, setFeedType] = useState("")
  const [quantityKg, setQuantityKg] = useState("")
  const [costPerKg, setCostPerKg] = useState("")
  const [entry, setEntry] = useState<FeedEntry>("kg")
  const { data: stock = [], isLoading: stockLoading } = useInventory(farmId)
  const choices = feedChoices(stock)
  const chosen = stock.find(i => i.id === inventoryItemId) ?? null
  const bagItem = chosen != null && hasPackSize(chosen)
  const entryNow: FeedEntry = bagItem ? entry : "kg"
  // what the stock check compares: the kilograms, worked out from bags when bags were typed
  const kgForCheck = chosen && bagItem && entryNow === "bags" ? bagsToKg(Number(quantityKg), chosen.packSizeKg as number) : Number(quantityKg)
  const stockProblem = chosen ? feedStockProblem(chosen, kgForCheck) : null
  const pickItem = (id: string) => {
    setInventoryItemId(id)
    setEntry("kg")
    const item = stock.find(i => i.id === id)
    if (item) {
      const p = feedPrefill(item)
      setFeedType(p.feedType)
      setCostPerKg(p.costPerKg == null ? "" : String(p.costPerKg))
    }
  }

  const queryKey = ["ag-feed-records", targetType, targetId]
  // 50 a page, newest first; the server never sends more than 200 at a time
  const [page, setPage] = useState(0)
  const { data, isLoading, isError, refetch } = useQuery<Page<FeedRecordResponse>>({
    queryKey: [...queryKey, page],
    placeholderData: prev => prev,
    queryFn: async () => (await apiClient.get(`${base}/feed-records`, { params: { page, size: PAGE_SIZE } })).data,
  })
  const total = data?.totalElements ?? 0

  const createMut = useAgToastMutation({
    mutationFn: () => apiClient.post(`${base}/feed-records`, {
      [targetType === "animal" ? "animalId" : "groupId"]: targetId,
      feedDate, inventoryItemId: inventoryItemId || null, feedType,
      ...feedQuantityPayload(entryNow, quantityKg), costPerKg: costPerKg ? Number(costPerKg) : null, notes: null,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey })
      // stock was drawn down
      qc.invalidateQueries({ queryKey: ["ag-inventory"] })
      qc.invalidateQueries({ queryKey: ["ag", "inventory", farmId] })
      setShowCreate(false); setInventoryItemId(""); setFeedType(""); setQuantityKg(""); setCostPerKg("")
    },
  })

  const records = data?.content ?? []
  const added = useAuditBatch("feed-records", records.map(r => r.id))
  const farmSuggestions = useFarmSuggestions(farmId)

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
        <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0 }}>Feed given. Choosing a stock item takes the feed off that item.</p>
        {!showCreate && <AgManage><button onClick={() => setShowCreate(true)} style={btnPrimary}><Plus size={13} style={{ marginRight: 4, verticalAlign: -2 }} />Record feed</button></AgManage>}
      </div>

      {showCreate && (
        <div style={{ background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 10, padding: 14, marginBottom: 14 }}>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(170px, 1fr))", gap: 8, marginBottom: 8 }}>
            <Field label="Date"><input type="date" value={feedDate} onChange={e => setFeedDate(e.target.value)} style={inp} /></Field>
            <div style={{ gridColumn: "1 / -1" }}>
              <Field label="From stock (optional)">
                <select value={inventoryItemId} onChange={e => pickItem(e.target.value)} style={inp} disabled={stockLoading}>
                  <option value="">{stockLoading ? "Loading stock…" : "Not from stock (type the feed below)"}</option>
                  {choices.map(c => <option key={c.id} value={c.id} disabled={c.disabled}>{c.label}</option>)}
                </select>
              </Field>
            </div>
            <><Field label="Feed type"><input list="ag-feed-options" value={feedType} onChange={e => setFeedType(e.target.value)} placeholder="e.g. Broiler starter" style={inp} /></Field><datalist id="ag-feed-options">{withOwn(feedSuggestions(speciesName), [...farmSuggestions.feedTypes, ...records.map(r => r.feedType)]).map(f => <option key={f} value={f} />)}</datalist></>
            {bagItem && chosen && (
              <Field label="Enter amount in">
                <select value={entryNow} onChange={e => { setEntry(e.target.value as FeedEntry); setQuantityKg("") }} style={inp}>
                  <option value="kg">kg</option>
                  <option value="bags">{unitSingular(chosen.unitOfMeasure)}s</option>
                </select>
              </Field>
            )}
            <Field label={entryNow === "bags" && chosen ? `Quantity (${unitSingular(chosen.unitOfMeasure)}s)` : "Quantity (kg)"}>
              <input type="number" min={0} step={entryNow === "bags" ? "0.01" : "0.1"} value={quantityKg} onChange={e => setQuantityKg(e.target.value)} style={inp} />
            </Field>
            <Field label="Cost/kg (R)"><input type="number" min={0} step="0.01" value={costPerKg} onChange={e => setCostPerKg(e.target.value)} placeholder={chosen ? "from stock cost" : ""} style={inp} /></Field>
          </div>
          {chosen && feedEquivalent(chosen, entryNow, quantityKg) && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 8px" }}>{quantityKg} {entryNow === "bags" ? unitSingular(chosen.unitOfMeasure) + "s" : "kg"} {feedEquivalent(chosen, entryNow, quantityKg)}</p>}
          {stockProblem && <p role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)", margin: "0 0 8px" }}>{stockProblem}</p>}
          <div style={{ display: "flex", gap: 8 }}>
            <button disabled={createMut.isPending || !feedType.trim() || !quantityKg || !!stockProblem} onClick={() => createMut.mutate()}
              style={{ ...btnPrimary, opacity: createMut.isPending || !feedType.trim() || !quantityKg || stockProblem ? 0.6 : 1 }}>{createMut.isPending ? "Saving…" : "Save"}</button>
            <button onClick={() => setShowCreate(false)} style={btnGhost}>Cancel</button>
          </div>
        </div>
      )}

      {isLoading ? <p style={{ color: "var(--hf-text-faint)", fontSize: 12.5 }}>Loading…</p> : isError ? <QueryError onRetry={() => void refetch()} /> :
        records.length === 0 ? <p style={{ color: "var(--hf-text-faint)", fontSize: 12.5 }}>No feed records yet.</p> : (
        <div style={{ border: "1px solid var(--hf-border)", borderRadius: 10, overflow: "hidden" }}>
          {records.map((r, i) => (
            <div key={r.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "10px 14px", borderTop: i === 0 ? "none" : "1px solid var(--hf-border-subtle)" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <Wheat size={13} style={{ color: AG_ACCENT_TEXT }} />
                <div>
                  <p style={{ fontSize: 12.5, color: "var(--hf-text)", fontWeight: 600, margin: 0 }}>{r.feedType} — {r.quantityKg} kg</p>
                  <p style={{ fontSize: 11, color: "var(--hf-text-faint)", margin: 0 }}>{r.feedDate}{r.totalCost != null ? ` · ${fmtMoney(r.totalCost)}` : ""}{byText(added[r.id])}</p>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
      {total > PAGE_SIZE && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "10px 0 0" }}>{rangeLabel(page, total, "feed record")}</p>}
      <AgPager page={page} total={total} size={PAGE_SIZE} onPage={setPage} />
    </div>
  )
}

