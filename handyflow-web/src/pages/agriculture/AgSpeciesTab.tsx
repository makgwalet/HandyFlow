// src/pages/agriculture/AgSpeciesTab.tsx
//
// Tenant-wide species catalogue — confirmed via AgSpeciesController (base
// path /api/v1/agriculture/species, inferred from every sibling
// controller's own @RequestMapping convention — the class-level annotation
// itself was truncated in the source read, so this ONE base path is
// UNVERIFIED; every method mapping under it (POST/PUT/{id}/deactivate/
// {id}/reactivate/DELETE) is directly confirmed).
//
// No separate AgBreed catalogue entity exists — breed is a free-text field
// directly on AgAnimal/AgGroup (see AgSpecies's own Javadoc: breed names
// vary too freely across species/regions for a pre-populated table to earn
// its keep in Increment 1).
import type React from "react"
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { useAgToastMutation } from "./agMutation"
import { PawPrint, Plus, Pencil, Ban, RotateCcw, Trash2 } from "lucide-react"
import { apiClient } from "../../api/client"
import { AG_ACCENT_TEXT, statusBadge, inp, btnPrimary, btnGhost } from "./constants"
import { agConfirm } from "./agConfirm"
import { Field, QueryError } from "./agUi"
import AgManage from "./AgManage"
import { toast } from "../../store/toast.store"

interface SeedResult { added: number; alreadyThere: number }

export interface SpeciesResponse {
  id: string
  name: string
  category: string
  defaultUnitOfMeasure: string
  trackingMode: "INDIVIDUAL" | "GROUP" | "BOTH"
  gestationDays: number | null
  maturityWeightKg: number | null
  status: string
  createdAt: string
}
interface Page<T> { content: T[]; totalElements: number }

const CATEGORIES = ["LIVESTOCK", "POULTRY", "AQUACULTURE"]
const TRACKING_MODES = ["INDIVIDUAL", "GROUP", "BOTH"]

type SpeciesValues = { name: string; category: string; defaultUnitOfMeasure: string; trackingMode: string; gestationDays: string; maturityWeightKg: string }

function SpeciesForm({ initial, onCancel, onSave, saving }: {
  initial?: Partial<SpeciesResponse>; onCancel: () => void; saving: boolean
  onSave: (v: SpeciesValues) => void
}) {
  const [name, setName] = useState(initial?.name ?? "")
  const [category, setCategory] = useState(initial?.category ?? "LIVESTOCK")
  const [uom, setUom] = useState(initial?.defaultUnitOfMeasure ?? "head")
  const [trackingMode, setTrackingMode] = useState(initial?.trackingMode ?? "BOTH")
  const [gestationDays, setGestationDays] = useState(initial?.gestationDays != null ? String(initial.gestationDays) : "")
  const [maturityWeightKg, setMaturityWeightKg] = useState(initial?.maturityWeightKg != null ? String(initial.maturityWeightKg) : "")

  return (
    <div style={{ background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 16 }}>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(170px, 1fr))", gap: 10, marginBottom: 10 }}>
        <Field label="Name"><input value={name} onChange={e => setName(e.target.value)} placeholder="e.g. Cattle" style={inp} /></Field>
        {!initial?.id && (
          <Field label="Category"><select value={category} onChange={e => setCategory(e.target.value)} style={inp}>
              {CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
            </select></Field>
        )}
        <Field label="Default unit"><input value={uom} onChange={e => setUom(e.target.value)} placeholder="head / kg / crate" style={inp} /></Field>
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(170px, 1fr))", gap: 10, marginBottom: 12 }}>
        <Field label="Tracking mode"><select value={trackingMode} onChange={e => setTrackingMode(e.target.value as "INDIVIDUAL" | "BOTH" | "GROUP")} style={inp}>
            {TRACKING_MODES.map(m => <option key={m} value={m}>{m}</option>)}
          </select></Field>
        <Field label="Gestation (days)"><input type="number" min={0} value={gestationDays} onChange={e => setGestationDays(e.target.value)} style={inp} /></Field>
        <Field label="Maturity weight (kg)"><input type="number" min={0} step="0.1" value={maturityWeightKg} onChange={e => setMaturityWeightKg(e.target.value)} style={inp} /></Field>
      </div>
      <div style={{ display: "flex", gap: 8 }}>
        <button disabled={saving || !name.trim()}
          onClick={() => onSave({ name, category, defaultUnitOfMeasure: uom, trackingMode, gestationDays, maturityWeightKg })}
          style={{ ...btnPrimary, opacity: saving || !name.trim() ? 0.6 : 1 }}>
          {saving ? "Saving…" : "Save"}
        </button>
        <button onClick={onCancel} style={btnGhost}>Cancel</button>
      </div>
    </div>
  )
}

export default function AgSpeciesTab() {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [editing, setEditing] = useState<SpeciesResponse | null>(null)

  const { data, isLoading, isError, refetch } = useQuery<Page<SpeciesResponse>>({
    queryKey: ["ag-species"],
    queryFn: async () => (await apiClient.get("/api/v1/agriculture/species", { params: { size: 200 } })).data,
  })

  const invalidate = () => qc.invalidateQueries({ queryKey: ["ag-species"] })

  const createMut = useAgToastMutation({
    mutationFn: async (v: SpeciesValues) => apiClient.post("/api/v1/agriculture/species", {
      name: v.name, category: v.category, defaultUnitOfMeasure: v.defaultUnitOfMeasure || "head",
      trackingMode: v.trackingMode, gestationDays: v.gestationDays ? Number(v.gestationDays) : null,
      maturityWeightKg: v.maturityWeightKg ? Number(v.maturityWeightKg) : null,
    }),
    onSuccess: () => { invalidate(); setShowCreate(false) },
  })
  const updateMut = useAgToastMutation({
    mutationFn: async (v: SpeciesValues) => apiClient.put(`/api/v1/agriculture/species/${editing!.id}`, {
      name: v.name, defaultUnitOfMeasure: v.defaultUnitOfMeasure || "head", trackingMode: v.trackingMode,
      gestationDays: v.gestationDays ? Number(v.gestationDays) : null,
      maturityWeightKg: v.maturityWeightKg ? Number(v.maturityWeightKg) : null,
    }),
    onSuccess: () => { invalidate(); setEditing(null) },
  })
  const deactivateMut = useAgToastMutation({ mutationFn: (id: string) => apiClient.patch(`/api/v1/agriculture/species/${id}/deactivate`), onSuccess: invalidate })
  const reactivateMut = useAgToastMutation({ mutationFn: (id: string) => apiClient.patch(`/api/v1/agriculture/species/${id}/reactivate`), onSuccess: invalidate })
  const deleteMut = useAgToastMutation({ mutationFn: (id: string) => apiClient.delete(`/api/v1/agriculture/species/${id}`), onSuccess: invalidate })

  const addStandardMut = useAgToastMutation({
    mutationFn: async () => (await apiClient.post<SeedResult>("/api/v1/agriculture/species/defaults")).data,
    onSuccess: r => { invalidate(); qc.invalidateQueries({ queryKey: ["ag-species", "picker"] }); toast.success(r.added === 0 ? "You already have all the standard species." : `Added ${r.added} standard species.`) },
  })
  const species = data?.content ?? []

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
        <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: 0 }}>Tenant-wide catalogue used when registering animals and groups on any farm.</p>
        {!showCreate && (
          <AgManage>
            <button onClick={() => addStandardMut.mutate()} disabled={addStandardMut.isPending} title="Adds cattle, sheep, goats, pigs, poultry and more. Anything you already have is kept." style={{ ...btnGhost, marginRight: 8, opacity: addStandardMut.isPending ? 0.6 : 1 }}>
              {addStandardMut.isPending ? "Adding…" : "Add standard list"}
            </button>
            <button onClick={() => setShowCreate(true)} style={btnPrimary}>
              <Plus size={14} style={{ marginRight: 5, verticalAlign: -2 }} />Add species
            </button>
          </AgManage>
        )}
      </div>

      {showCreate && (
        <SpeciesForm saving={createMut.isPending} onCancel={() => setShowCreate(false)} onSave={v => createMut.mutate(v)} />
      )}

      {isLoading ? (
        <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>Loading…</p>
      ) : isError ? (
        <QueryError onRetry={() => void refetch()} what="the species list" />
      ) : species.length === 0 ? (
        <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>No species in the catalogue yet. Use "Add standard list" for cattle, sheep, goats, pigs, poultry and more, or add your own.</p>
      ) : (
        <div style={{ border: "1px solid var(--hf-border)", borderRadius: 12, overflow: "hidden" }}>
          {species.map((s, i) => (
            <div key={s.id}>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid var(--hf-border-subtle)" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                  <PawPrint size={16} style={{ color: AG_ACCENT_TEXT }} />
                  <div>
                    <p style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", margin: 0 }}>{s.name}</p>
                    <p style={{ fontSize: 11, color: "var(--hf-text-faint)", margin: 0 }}>{s.category} · {s.defaultUnitOfMeasure} · Tracking: {s.trackingMode}{s.gestationDays ? ` · Gestation ${s.gestationDays}d` : ""}</p>
                  </div>
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                  <span style={statusBadge(s.status)}>{s.status}</span>
                  <AgManage>
                  <button onClick={() => setEditing(s)} title="Edit" style={iconBtn}><Pencil size={13} /></button>
                  {s.status === "ACTIVE" ? (
                    <button onClick={() => deactivateMut.mutate(s.id)} title="Deactivate" style={iconBtn}><Ban size={13} /></button>
                  ) : (
                    <button onClick={() => reactivateMut.mutate(s.id)} title="Reactivate" style={iconBtn}><RotateCcw size={13} /></button>
                  )}
                  <button onClick={async () => { if (await agConfirm({ title: `Delete species "${s.name}"?`, confirmLabel: "Delete", danger: true })) deleteMut.mutate(s.id) }} title="Delete" style={{ ...iconBtn, color: "var(--hf-danger-text)" }}><Trash2 size={13} /></button>
                  </AgManage>
                </div>
              </div>
              {editing?.id === s.id && (
                <div style={{ padding: "0 16px 16px" }}>
                  <SpeciesForm initial={editing} saving={updateMut.isPending} onCancel={() => setEditing(null)} onSave={v => updateMut.mutate(v)} />
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

const iconBtn: React.CSSProperties = { display: "flex", alignItems: "center", justifyContent: "center", width: 28, height: 28, borderRadius: 7, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text-muted)", cursor: "pointer" }
