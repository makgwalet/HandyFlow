// A small selector for the warehouse in use. It shows only when the business has more than one warehouse switched on.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { inputStyle } from "./whseUi"
import { pickWarehouse, showSelector, type WarehouseLite } from "./warehouses"

export function useWarehouses() {
  const { data } = useQuery<WarehouseLite[]>({ queryKey: ["whse-warehouses"], queryFn: async () => (await apiClient.get("/api/v1/warehousing/warehouses")).data })
  return Array.isArray(data) ? data : []
}

export default function WhseWarehouseSelect({ value, onChange }: { value: string | undefined; onChange: (id: string) => void }) {
  const list = useWarehouses()
  if (!showSelector(list)) return null
  const current = pickWarehouse(list, value)
  return (
    <label style={{ display: "inline-flex", alignItems: "center", gap: 8, fontSize: 12.5, marginBottom: 14, marginRight: 14 }}>
      <span style={{ fontWeight: 600, color: "var(--hf-text-secondary)" }}>Warehouse</span>
      <select aria-label="Warehouse" style={inputStyle} value={current} onChange={e => onChange(e.target.value)}>
        {list.filter(w => w.active).map(w => <option key={w.id} value={w.id}>{w.code} · {w.name}{w.isDefault ? " (default)" : ""}</option>)}
      </select>
    </label>
  )
}
