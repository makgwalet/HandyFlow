// src/pages/warehousing/WhseFloorMap.tsx
//
// The floor map: every placed location as a cell on the building grid, coloured by how full it is (or by held stock, or by expiry),
// with a panel for the levels of the selected bay. Racking is generated in blocks (aisles x bays x levels); locations that were created
// before the map existed show under "Not on the map" and can be given a cell. "Edit layout" lets a bay be dragged (or clicked, or typed) to
// another cell, its type/capacity/in-use state changed, levels added on top and bays added to the end of an aisle.
import { useMemo, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Plus, X } from "lucide-react"
import { apiClient } from "../../api/client"
import { WHSE_ACCENT, WHSE_ACCENT_TEXT } from "./constants"
import { Band, band, bounds, buildGeneratePayload, Cell, expiresSoon, FloorLocation, GenerateForm, groupCells, validateGenerate } from "./floorMap"

interface FloorMapData { locations: FloorLocation[]; placed: number; unplaced: number }
interface PreviewSlot { slot: { code: string; x: number; y: number; level: number }; conflict: string | null }

type Mode = "fill" | "held" | "expiry"

const BAND_BG: Record<Band, string> = {
  inactive: "var(--hf-surface-muted)", empty: "var(--hf-surface-sunken)", stocked: "var(--hf-info-soft)", low: "var(--hf-success-soft)",
  mid: "var(--hf-success-soft-strong)", high: "var(--hf-warning-soft-strong)", over: "var(--hf-danger-soft)",
}
const BAND_LABEL: Record<Band, string> = {
  inactive: "Inactive", empty: "Empty", stocked: "Stocked (no capacity set)", low: "Under 50% full", mid: "50–85% full", high: "85–100% full", over: "Over capacity",
}
const TYPES = ["BIN", "SHELF", "PALLET", "FLOOR", "DOCK", "STAGING"]
const CELL = 44

const inputStyle: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, boxSizing: "border-box", fontFamily: "inherit", background: "var(--hf-surface)", color: "var(--hf-text)" }
const labelStyle: React.CSSProperties = { fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4, display: "block" }
const errText = (e: unknown, fallback: string) => (e as any)?.response?.data?.message ?? fallback
const today = () => new Date().toLocaleDateString("en-CA")
const num = (n: number) => n.toLocaleString("en-ZA", { maximumFractionDigits: 1 })

function cellColour(c: Cell, mode: Mode): string {
  if (mode === "held") return c.held > 0 ? "var(--hf-warning-soft-strong)" : c.onHand > 0 ? "var(--hf-surface-muted)" : "var(--hf-surface-sunken)"
  if (mode === "expiry") return expiresSoon(c.earliestExpiry, today()) ? "var(--hf-danger-soft)" : c.onHand > 0 ? "var(--hf-surface-muted)" : "var(--hf-surface-sunken)"
  return BAND_BG[band(c)]
}

function GenerateModal({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient()
  const [f, setF] = useState<GenerateForm>({ zone: "", aisles: "A-C", bays: "10", levels: "3", locationType: "PALLET", originX: "0", originY: "0", aisleSpacing: "2", capacityUnits: "" })
  const [preview, setPreview] = useState<PreviewSlot[] | null>(null)
  const problem = validateGenerate(f)
  const set = (k: keyof GenerateForm, v: string) => { setF({ ...f, [k]: v }); setPreview(null) }

  const check = useMutation({
    mutationFn: async () => (await apiClient.post("/api/v1/warehousing/floor-map/generate/preview", buildGeneratePayload(f))).data as PreviewSlot[],
    onSuccess: setPreview,
  })
  const create = useMutation({
    mutationFn: async () => apiClient.post("/api/v1/warehousing/floor-map/generate", buildGeneratePayload(f)),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["whse-floor-map"] }); qc.invalidateQueries({ queryKey: ["whse-locations"] }); onClose() },
  })
  const clashes = preview?.filter(p => p.conflict) ?? []

  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.4)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 50 }}>
      <div style={{ background: "var(--hf-surface)", borderRadius: 16, padding: 24, width: 460, maxHeight: "90vh", overflowY: "auto" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
          <p style={{ fontSize: 15, fontWeight: 800, color: "var(--hf-text)", margin: 0 }}>Generate racking</p>
          <button onClick={onClose} aria-label="Close" style={{ background: "none", border: "none", cursor: "pointer" }}><X size={18} style={{ color: "var(--hf-text-faint)" }} /></button>
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
          <div style={{ gridColumn: "1 / -1" }}><label style={labelStyle}>Aisles *</label><input style={inputStyle} value={f.aisles} onChange={e => set("aisles", e.target.value)} placeholder="A-D, or A, B, C" /></div>
          <div><label style={labelStyle}>Bays per aisle *</label><input type="number" style={inputStyle} value={f.bays} onChange={e => set("bays", e.target.value)} /></div>
          <div><label style={labelStyle}>Levels *</label><input type="number" style={inputStyle} value={f.levels} onChange={e => set("levels", e.target.value)} /></div>
          <div><label style={labelStyle}>Zone</label><input style={inputStyle} value={f.zone} onChange={e => set("zone", e.target.value)} placeholder="e.g. Dry goods" /></div>
          <div><label style={labelStyle}>Type</label>
            <select style={inputStyle} value={f.locationType} onChange={e => set("locationType", e.target.value)}>{TYPES.map(t => <option key={t}>{t}</option>)}</select></div>
          <div><label style={labelStyle}>Start column</label><input type="number" style={inputStyle} value={f.originX} onChange={e => set("originX", e.target.value)} /></div>
          <div><label style={labelStyle}>Start row</label><input type="number" style={inputStyle} value={f.originY} onChange={e => set("originY", e.target.value)} /></div>
          <div><label style={labelStyle}>Rows between aisles</label><input type="number" style={inputStyle} value={f.aisleSpacing} onChange={e => set("aisleSpacing", e.target.value)} /></div>
          <div><label style={labelStyle}>Capacity per location (units)</label><input style={inputStyle} value={f.capacityUnits} onChange={e => set("capacityUnits", e.target.value)} /></div>
        </div>
        <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: "10px 0 0" }}>
          Codes look like A-01-01 (aisle, bay, level). The picking order snakes: the first aisle is walked from bay 1 to the end, the next one back.
        </p>
        {problem && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, marginTop: 10 }}>{problem}</p>}
        {check.isError && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, marginTop: 10 }}>{errText(check.error, "Could not check this layout")}</p>}
        {preview && (
          <p style={{ fontSize: 12.5, marginTop: 10, color: clashes.length ? "var(--hf-danger-text)" : "var(--hf-success-text-strong)" }}>
            {clashes.length === 0 ? `${preview.length} locations will be created (${preview[0]?.slot.code} to ${preview[preview.length - 1]?.slot.code}).`
              : `${clashes.length} of ${preview.length} clash with existing locations, so nothing can be created. ${clashes[0].conflict}`}
          </p>
        )}
        {create.isError && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, marginTop: 10 }}>{errText(create.error, "Could not create the locations")}</p>}
        <div style={{ display: "flex", gap: 8, marginTop: 16 }}>
          <button onClick={() => check.mutate()} disabled={!!problem || check.isPending}
            style={{ flex: 1, padding: 10, borderRadius: 8, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13, fontWeight: 600, cursor: "pointer", opacity: problem ? 0.5 : 1 }}>
            {check.isPending ? "Checking…" : "Preview"}
          </button>
          <button onClick={() => create.mutate()} disabled={!preview || clashes.length > 0 || create.isPending}
            style={{ flex: 1, padding: 10, borderRadius: 8, border: "none", background: WHSE_ACCENT, color: "var(--hf-text-on-solid)", fontSize: 13, fontWeight: 700, cursor: "pointer", opacity: !preview || clashes.length > 0 ? 0.5 : 1 }}>
            {create.isPending ? "Creating…" : "Create"}
          </button>
        </div>
      </div>
    </div>
  )
}

function PlaceRow({ l }: { l: FloorLocation }) {
  const qc = useQueryClient()
  const [x, setX] = useState(""), [y, setY] = useState("")
  const save = useMutation({
    mutationFn: async () => apiClient.put(`/api/v1/warehousing/floor-map/locations/${l.id}/layout`, {
      aisle: l.aisle, bay: l.bay, level: l.level, locationType: l.locationType, pickSequence: l.pickSequence, posX: Number(x), posY: Number(y),
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["whse-floor-map"] }); qc.invalidateQueries({ queryKey: ["whse-locations"] }) },
  })
  const valid = /^\d+$/.test(x) && /^\d+$/.test(y)
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 12.5, marginBottom: 6 }}>
      <span style={{ flex: 1, color: "var(--hf-text)", fontWeight: 600 }}>{l.code}</span>
      <input aria-label={`Column for ${l.code}`} style={{ ...inputStyle, width: 56 }} placeholder="col" value={x} onChange={e => setX(e.target.value)} />
      <input aria-label={`Row for ${l.code}`} style={{ ...inputStyle, width: 56 }} placeholder="row" value={y} onChange={e => setY(e.target.value)} />
      <button onClick={() => save.mutate()} disabled={!valid || save.isPending}
        style={{ border: "none", background: WHSE_ACCENT, color: "var(--hf-text-on-solid)", borderRadius: 6, padding: "6px 10px", fontSize: 12, fontWeight: 600, cursor: "pointer", opacity: valid ? 1 : 0.5 }}>Place</button>
      {save.isError && <span style={{ color: "var(--hf-danger-text)", fontSize: 11 }}>{errText(save.error, "Could not place")}</span>}
    </div>
  )
}

const FLOOR = "/api/v1/warehousing/floor-map"

/** Edits the selected bay: move, type, capacity, in-use, add levels, extend its aisle. Every change is one call and refreshes the map. */
function BayEditor({ cell, onMoved }: { cell: Cell; onMoved: (x: number, y: number) => void }) {
  const qc = useQueryClient()
  const first = cell.locations[0]
  const [type, setType] = useState(first.locationType)
  const [capacity, setCapacity] = useState("")
  const [col, setCol] = useState(""), [row, setRow] = useState("")
  const [levels, setLevels] = useState("1"), [bays, setBays] = useState("1")
  const [done, setDone] = useState<string | null>(null)
  const refresh = () => { qc.invalidateQueries({ queryKey: ["whse-floor-map"] }); qc.invalidateQueries({ queryKey: ["whse-locations"] }) }
  const call = useMutation({
    mutationFn: async (a: { path: string; body: object; message: string; after?: () => void }) => { await apiClient.post(`${FLOOR}${a.path}`, a.body); return a },
    onSuccess: a => { setDone(a.message); refresh(); a.after?.() },
    onError: () => setDone(null),
  })
  const run = (path: string, body: object, message: string, after?: () => void) => { setDone(null); call.mutate({ path, body, message, after }) }
  const at = { x: cell.x, y: cell.y }
  const goodCell = /^\d+$/.test(col) && /^\d+$/.test(row)
  const btn: React.CSSProperties = { border: "none", background: WHSE_ACCENT, color: "var(--hf-text-on-solid)", borderRadius: 6, padding: "7px 12px", fontSize: 12, fontWeight: 600, cursor: "pointer" }
  const ghost: React.CSSProperties = { border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text)", borderRadius: 6, padding: "7px 12px", fontSize: 12, fontWeight: 600, cursor: "pointer" }
  const inUse = cell.locations.some(l => l.active)

  return (
    <div style={{ borderTop: "1px solid var(--hf-border-subtle)", marginTop: 10, paddingTop: 10, display: "grid", gap: 12 }}>
      <p style={{ fontSize: 12.5, fontWeight: 700, margin: 0, color: "var(--hf-text)" }}>Edit this bay</p>
      <div>
        <label style={labelStyle} htmlFor="bay-move-col">Move to column and row</label>
        <div style={{ display: "flex", gap: 6 }}>
          <input id="bay-move-col" aria-label="Move to column" style={{ ...inputStyle, width: 64 }} placeholder="col" value={col} onChange={e => setCol(e.target.value)} />
          <input aria-label="Move to row" style={{ ...inputStyle, width: 64 }} placeholder="row" value={row} onChange={e => setRow(e.target.value)} />
          <button style={{ ...btn, opacity: goodCell ? 1 : 0.5 }} disabled={!goodCell || call.isPending}
            onClick={() => run("/cells/move", { fromX: cell.x, fromY: cell.y, toX: Number(col), toY: Number(row) }, "Bay moved", () => onMoved(Number(col), Number(row)))}>Move bay</button>
        </div>
        <p style={{ fontSize: 11, color: "var(--hf-text-faint)", margin: "4px 0 0" }}>Or drag the bay onto an empty square, or click an empty square.</p>
      </div>
      <div>
        <label style={labelStyle} htmlFor="bay-type">Type (all levels)</label>
        <div style={{ display: "flex", gap: 6 }}>
          <select id="bay-type" style={inputStyle} value={type} onChange={e => setType(e.target.value)}>{TYPES.map(t => <option key={t}>{t}</option>)}</select>
          <button style={btn} disabled={call.isPending || type === first.locationType} onClick={() => run("/cells/edit", { ...at, locationType: type }, "Type saved")}>Save type</button>
        </div>
      </div>
      <div>
        <label style={labelStyle} htmlFor="bay-cap">Capacity per level (units)</label>
        <div style={{ display: "flex", gap: 6 }}>
          <input id="bay-cap" style={inputStyle} value={capacity} onChange={e => setCapacity(e.target.value)} placeholder={first.capacityUnits != null ? String(first.capacityUnits) : "not set"} />
          <button style={{ ...btn, opacity: /^\d+(\.\d+)?$/.test(capacity) ? 1 : 0.5 }} disabled={!/^\d+(\.\d+)?$/.test(capacity) || call.isPending}
            onClick={() => run("/cells/edit", { ...at, capacityUnits: Number(capacity) }, "Capacity saved", () => setCapacity(""))}>Save capacity</button>
          <button style={ghost} disabled={call.isPending} onClick={() => run("/cells/edit", { ...at, clearCapacity: true }, "Capacity cleared")}>Clear</button>
        </div>
      </div>
      <div>
        <label style={labelStyle} htmlFor="bay-levels">Add levels on top</label>
        <div style={{ display: "flex", gap: 6 }}>
          <input id="bay-levels" type="number" min={1} max={10} style={{ ...inputStyle, width: 70 }} value={levels} onChange={e => setLevels(e.target.value)} />
          <button style={btn} disabled={call.isPending || !(Number(levels) >= 1)} onClick={() => run("/cells/add-levels", { ...at, count: Number(levels) }, `${levels} level(s) added`)}>Add levels</button>
        </div>
      </div>
      {first.aisle && (
        <div>
          <label style={labelStyle} htmlFor="bay-extend">Add bays to the end of aisle {first.aisle}</label>
          <div style={{ display: "flex", gap: 6 }}>
            <input id="bay-extend" type="number" min={1} max={99} style={{ ...inputStyle, width: 70 }} value={bays} onChange={e => setBays(e.target.value)} />
            <button style={btn} disabled={call.isPending || !(Number(bays) >= 1)} onClick={() => run("/aisles/extend", { aisle: first.aisle, bays: Number(bays) }, `${bays} bay(s) added to aisle ${first.aisle}`)}>Add bays</button>
          </div>
          <p style={{ fontSize: 11, color: "var(--hf-text-faint)", margin: "4px 0 0" }}>Copies this aisle's last bay (levels, type, zone) and continues on the same row. New bays are picked last.</p>
        </div>
      )}
      <div>
        <button style={ghost} disabled={call.isPending} onClick={() => run("/cells/edit", { ...at, active: !inUse }, inUse ? "Bay switched off" : "Bay switched on")}>
          {inUse ? "Switch bay off" : "Switch bay on"}
        </button>
      </div>
      {call.isError && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: 0 }}>{errText(call.error, "Could not save this change")}</p>}
      {done && !call.isError && <p role="status" style={{ color: "var(--hf-success-text-strong)", fontSize: 12, margin: 0 }}>{done}</p>}
    </div>
  )
}

export default function WhseFloorMap() {
  const [mode, setMode] = useState<Mode>("fill")
  const [zone, setZone] = useState("")
  const [selected, setSelected] = useState<string | null>(null)
  const [showGenerate, setShowGenerate] = useState(false)
  const [editing, setEditing] = useState(false)
  const qc = useQueryClient()
  const move = useMutation({
    mutationFn: async (m: { fromX: number; fromY: number; toX: number; toY: number }) => apiClient.post(`${FLOOR}/cells/move`, m),
    onSuccess: (_r, m) => { setSelected(`${m.toX}:${m.toY}`); qc.invalidateQueries({ queryKey: ["whse-floor-map"] }) },
  })

  const { data, isLoading } = useQuery<FloorMapData>({
    queryKey: ["whse-floor-map"],
    queryFn: async () => (await apiClient.get("/api/v1/warehousing/floor-map")).data,
  })
  const locations = data?.locations ?? []
  const zones = useMemo(() => [...new Set(locations.map(l => l.zone).filter((z): z is string => !!z))].sort(), [locations])
  const cells = useMemo(() => groupCells(locations, zone || null), [locations, zone])
  const raw = bounds(cells)
  // While editing the grid grows by one square on every side so a bay can be dropped just outside the current edge.
  const box = raw && editing ? { minX: Math.max(0, raw.minX - 1), minY: Math.max(0, raw.minY - 1), maxX: raw.maxX + 1, maxY: raw.maxY + 1 } : raw
  const occupied = useMemo(() => new Set(cells.map(c => `${c.x}:${c.y}`)), [cells])
  const emptySlots = useMemo(() => {
    if (!box || !editing || (box.maxX - box.minX + 1) * (box.maxY - box.minY + 1) > 2500) return []
    const out: { x: number; y: number }[] = []
    for (let y = box.minY; y <= box.maxY; y++) for (let x = box.minX; x <= box.maxX; x++) if (!occupied.has(`${x}:${y}`)) out.push({ x, y })
    return out
  }, [box, editing, occupied])
  const moveSelectedTo = (x: number, y: number) => { if (current) move.mutate({ fromX: current.x, fromY: current.y, toX: x, toY: y }) }
  const current = cells.find(c => `${c.x}:${c.y}` === selected) ?? null
  const unplaced = locations.filter(l => l.posX == null)

  return (
    <div>
      <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap", marginBottom: 14 }}>
        <select aria-label="Zone" style={{ ...inputStyle, width: 170 }} value={zone} onChange={e => setZone(e.target.value)}>
          <option value="">All zones</option>{zones.map(z => <option key={z}>{z}</option>)}
        </select>
        <select aria-label="Colour by" style={{ ...inputStyle, width: 190 }} value={mode} onChange={e => setMode(e.target.value as Mode)}>
          <option value="fill">Colour: how full</option><option value="held">Colour: held or damaged stock</option><option value="expiry">Colour: expiring within 30 days</option>
        </select>
        <span style={{ flex: 1 }} />
        <button aria-pressed={editing} onClick={() => { setEditing(!editing); move.reset() }}
          style={{ background: editing ? WHSE_ACCENT_TEXT : "var(--hf-surface)", color: editing ? "var(--hf-text-on-solid)" : "var(--hf-text)", border: "1px solid var(--hf-border)", borderRadius: 8, padding: "9px 16px", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          {editing ? "Done editing" : "Edit layout"}
        </button>
        <button onClick={() => setShowGenerate(true)}
          style={{ display: "flex", alignItems: "center", gap: 6, background: WHSE_ACCENT, color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, padding: "9px 16px", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={15} /> Generate racking
        </button>
      </div>

      {isLoading ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>Loading…</p>
        : !box ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>Nothing is on the map yet. Generate racking, or place your existing locations below.</p>
        : (
          <div style={{ display: "flex", gap: 16, alignItems: "flex-start", flexWrap: "wrap" }}>
            <div style={{ overflow: "auto", maxWidth: "100%", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 12, background: "var(--hf-surface)" }}>
              <div role="grid" aria-label="Warehouse floor map" style={{
                display: "grid", gap: 3,
                gridTemplateColumns: `repeat(${box.maxX - box.minX + 1}, ${CELL}px)`, gridAutoRows: `${CELL}px`,
              }}>
                {cells.map(c => {
                  const label = c.locations[0].aisle && c.locations[0].bay != null ? `${c.locations[0].aisle}${String(c.locations[0].bay).padStart(2, "0")}` : c.locations[0].code
                  const active = `${c.x}:${c.y}` === selected
                  return (
                    <button key={`${c.x}:${c.y}`} role="gridcell" onClick={() => setSelected(active ? null : `${c.x}:${c.y}`)}
                      draggable={editing} onDragStart={e => { e.dataTransfer.setData("text/plain", `${c.x}:${c.y}`); e.dataTransfer.effectAllowed = "move" }}
                      title={`${c.locations.map(l => l.code).join(", ")}\n${num(c.onHand)} on hand${c.held ? `, ${num(c.held)} held` : ""}${c.utilisationPct != null ? `, ${c.utilisationPct}% full` : ""}`}
                      style={{
                        gridColumn: c.x - box.minX + 1, gridRow: c.y - box.minY + 1, background: cellColour(c, mode), color: "var(--hf-text)",
                        border: active ? `2px solid ${WHSE_ACCENT}` : "1px solid var(--hf-border)", borderRadius: 6, fontSize: 10, fontWeight: 600, cursor: "pointer", padding: 0,
                        display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", lineHeight: 1.2, opacity: c.active ? 1 : 0.6,
                      }}>
                      <span>{label}</span>
                      {c.onHand > 0 && <span style={{ fontWeight: 400, color: "var(--hf-text-muted)" }}>{num(c.onHand)}</span>}
                    </button>
                  )
                })}
                {emptySlots.map(sl => (
                  <div key={`empty-${sl.x}:${sl.y}`} role="gridcell" aria-label={`Empty square column ${sl.x}, row ${sl.y}`}
                    onClick={() => moveSelectedTo(sl.x, sl.y)}
                    onDragOver={e => e.preventDefault()}
                    onDrop={e => {
                      e.preventDefault()
                      const [fx, fy] = e.dataTransfer.getData("text/plain").split(":").map(Number)
                      if (Number.isInteger(fx) && Number.isInteger(fy)) move.mutate({ fromX: fx, fromY: fy, toX: sl.x, toY: sl.y })
                    }}
                    style={{ gridColumn: sl.x - box.minX + 1, gridRow: sl.y - box.minY + 1, border: "1px dashed var(--hf-border)", borderRadius: 6, cursor: current ? "copy" : "default" }} />
                ))}
              </div>
              {editing && <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: "10px 0 0" }}>Drag a bay onto a dashed square to move it, or select a bay and click a dashed square.</p>}
              {move.isError && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: "8px 0 0" }}>{errText(move.error, "Could not move the bay")}</p>}
              {mode === "fill" && (
                <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginTop: 12 }}>
                  {(Object.keys(BAND_BG) as Band[]).map(b => (
                    <span key={b} style={{ display: "flex", alignItems: "center", gap: 5, fontSize: 11, color: "var(--hf-text-muted)" }}>
                      <span style={{ width: 12, height: 12, borderRadius: 3, background: BAND_BG[b], border: "1px solid var(--hf-border)" }} />{BAND_LABEL[b]}
                    </span>
                  ))}
                </div>
              )}
            </div>

            {current && (
              <div style={{ minWidth: 280, flex: 1, border: "1px solid var(--hf-border)", borderRadius: 12, padding: 14, background: "var(--hf-surface)" }}>
                <p style={{ fontSize: 13.5, fontWeight: 800, margin: "0 0 2px", color: WHSE_ACCENT_TEXT }}>{current.locations[0].aisle ?? ""} bay {current.locations[0].bay ?? "–"}</p>
                <p style={{ fontSize: 11.5, color: "var(--hf-text-muted)", margin: "0 0 10px" }}>{current.zone ?? "No zone"} · column {current.x}, row {current.y}</p>
                {[...current.locations].reverse().map(l => (
                  <div key={l.id} style={{ borderTop: "1px solid var(--hf-border-subtle)", padding: "8px 0", fontSize: 12.5, opacity: l.active ? 1 : 0.6 }}>
                    <div style={{ display: "flex", justifyContent: "space-between" }}>
                      <strong>{l.code}</strong><span style={{ color: "var(--hf-text-faint)" }}>{l.locationType.toLowerCase()}{l.level ? ` · level ${l.level}` : ""}</span>
                    </div>
                    <div style={{ color: "var(--hf-text-muted)" }}>
                      {l.onHand > 0 ? `${num(l.onHand)} on hand` : "Empty"}{l.allocated > 0 ? `, ${num(l.allocated)} allocated` : ""}{l.held > 0 ? `, ${num(l.held)} held` : ""}
                      {l.skuCount > 0 ? ` · ${l.skuCount} SKU${l.skuCount === 1 ? "" : "s"}` : ""}{l.utilisationPct != null ? ` · ${l.utilisationPct}% full` : ""}
                    </div>
                    {l.earliestExpiry && <div style={{ color: expiresSoon(l.earliestExpiry, today()) ? "var(--hf-danger-text)" : "var(--hf-text-faint)" }}>Earliest expiry {l.earliestExpiry}</div>}
                  </div>
                ))}
                {editing && <BayEditor key={`${current.x}:${current.y}:${current.locations.length}`} cell={current} onMoved={(x, y) => setSelected(`${x}:${y}`)} />}
              </div>
            )}
          </div>
        )}

      {unplaced.length > 0 && (
        <div style={{ marginTop: 20, border: "1px solid var(--hf-border)", borderRadius: 12, padding: 14, maxWidth: 520 }}>
          <p style={{ fontSize: 13, fontWeight: 700, margin: "0 0 4px", color: "var(--hf-text)" }}>Not on the map ({unplaced.length})</p>
          <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: "0 0 10px" }}>Give each a column and row to show it on the floor.</p>
          {unplaced.slice(0, 30).map(l => <PlaceRow key={l.id} l={l} />)}
          {unplaced.length > 30 && <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)" }}>…and {unplaced.length - 30} more.</p>}
        </div>
      )}

      {showGenerate && <GenerateModal onClose={() => setShowGenerate(false)} />}
    </div>
  )
}
