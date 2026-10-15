// src/pages/earthmoving/InspectionsTab.tsx
//
// Inspections: the walk-round before a machine is used, and the condition check when it is handed to a customer or comes back. Every item is answered OK, Defect or Unsafe.
// A defect or unsafe finding needs a note. One unsafe finding takes the machine out of service as a critical breakdown;
// it comes back only through the normal release, which needs release authority. A defect is tracked as an incident and
// does not stop the machine.
import { useState } from "react"
import { ClipboardCheck } from "lucide-react"
import { apiMessage, fmtDateTime } from "./shared/format"
import { Overlay, ModalHead, ModalFoot, ErrBanner, lbl, inputStyle } from "./shared/Modal"
import { useEarthPerms } from "./shared/useEarthPerms"
import { useAssets, useChecklist, useInspections, useCreateInspection, useAssignableUsers } from "./shared/hooks"
import type { Inspection } from "./shared/types"

const btn = (primary = false): React.CSSProperties => ({
  padding: "6px 12px", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer",
  border: primary ? "none" : "1px solid var(--hf-border)",
  background: primary ? "var(--hf-primary)" : "var(--hf-surface)",
  color: primary ? "var(--hf-text-on-solid)" : "var(--hf-text-secondary)",
})
const card: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 10, padding: "14px 18px", background: "var(--hf-surface)" }

const RESULT_LABEL: Record<string, string> = { PASS: "Passed", DEFECTS: "Passed with defects", UNSAFE: "Unsafe: taken out of service" }
export const TYPE_LABEL: Record<string, string> = { PRE_USE: "Pre-use", HANDOVER: "Handover to customer", RETURN: "Return from hire" }
const OUTCOMES = [["OK", "OK"], ["DEFECT", "Defect"], ["UNSAFE", "Unsafe"]] as const

type Answer = { outcome: string; comment: string }

export default function InspectionsTab() {
  const perms = useEarthPerms()
  const [assetId, setAssetId] = useState("")
  const [open, setOpen] = useState(false)
  const [formAsset, setFormAsset] = useState("")
  const [answers, setAnswers] = useState<Record<string, Answer>>({})
  const [type, setType] = useState("PRE_USE")
  const [meter, setMeter] = useState("")
  const [notes, setNotes] = useState("")
  const [err, setErr] = useState("")
  const [notice, setNotice] = useState("")

  const { data: assets = [] } = useAssets()
  const { data: checklist = [] } = useChecklist()
  const { data: inspections = [], isLoading } = useInspections(assetId)
  const { data: users = [] } = useAssignableUsers()
  const create = useCreateInspection(i => {
    setOpen(false)
    setNotice(i.result === "UNSAFE" ? `${i.machine} was found unsafe and has been taken out of service. A work order and alert were raised.`
      : i.result === "DEFECTS" ? `Inspection of ${i.machine} saved. Its defects are being tracked as incidents.` : `Inspection of ${i.machine} saved: all clear.`)
  })

  const usable = assets.filter(a => a.status !== "RETIRED")
  const answer = (item: string): Answer => answers[item] ?? { outcome: "OK", comment: "" }
  const setAnswer = (item: string, patch: Partial<Answer>) => setAnswers(a => ({ ...a, [item]: { ...answer(item), ...patch } }))
  const missingNote = checklist.filter(i => answer(i).outcome !== "OK" && answer(i).comment.trim().length < 5)
  const unsafeCount = checklist.filter(i => answer(i).outcome === "UNSAFE").length

  const openForm = () => { setFormAsset(assetId); setAnswers({}); setType("PRE_USE"); setMeter(""); setNotes(""); setErr(""); setNotice(""); setOpen(true) }
  const submit = () => {
    setErr("")
    create.mutate({
      assetId: formAsset, inspectionType: type, meterReading: meter.trim() === "" ? null : Number(meter), notes: notes.trim() || null,
      items: checklist.map(i => ({ item: i, outcome: answer(i).outcome, comment: answer(i).comment.trim() || null })),
    }, { onError: (e: unknown) => setErr(apiMessage(e, "Could not save the inspection")) })
  }
  const who = (id: string | null) => (id ? (users.find(u => u.id === id)?.name ?? "Another user") : "Unknown")

  const renderInspection = (i: Inspection) => {
    const findings = i.items.filter(x => x.outcome !== "OK")
    return (
      <div key={i.id} style={card}>
        <div style={{ display: "flex", justifyContent: "space-between", gap: 12 }}>
          <div style={{ fontWeight: 700, color: "var(--hf-text)" }}>{i.machine} <span style={{ fontWeight: 500, fontSize: 12, color: "var(--hf-text-muted)" }}>· {TYPE_LABEL[i.inspectionType] ?? "Pre-use"}</span></div>
          <div style={{ fontSize: 13, fontWeight: 600, color: i.result === "UNSAFE" ? "var(--hf-danger-text)" : "var(--hf-text-secondary)" }}>{RESULT_LABEL[i.result] ?? i.result}</div>
        </div>
        <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 2 }}>{fmtDateTime(i.performedAt)} · {who(i.inspectorUserId)}</div>
        {findings.length > 0 && (
          <ul style={{ margin: "8px 0 0", paddingLeft: 18, fontSize: 13, color: "var(--hf-text-secondary)" }}>
            {findings.map(f => <li key={f.item}><strong>{f.item}</strong> ({f.outcome === "UNSAFE" ? "unsafe" : "defect"}): {f.comment}</li>)}
          </ul>
        )}
        {i.notes && <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 6 }}>{i.notes}</div>}
      </div>
    )
  }

  const renderItem = (item: string, n: number) => {
    const a = answer(item)
    return (
      <div key={item} style={{ borderTop: n === 0 ? "none" : "1px solid var(--hf-border)", padding: "8px 0" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12 }}>
          <span style={{ fontSize: 13, color: "var(--hf-text)" }}>{item}</span>
          <span role="group" aria-label={item} style={{ display: "flex", gap: 6 }}>
            {OUTCOMES.map(([v, label]) => (
              <button key={v} type="button" aria-pressed={a.outcome === v} aria-label={`${item}: ${label}`} style={btn(a.outcome === v)}
                onClick={() => setAnswer(item, { outcome: v })}>{label}</button>
            ))}
          </span>
        </div>
        {a.outcome !== "OK" && (
          <input aria-label={`What was seen: ${item}`} placeholder="What was seen (at least 5 characters)" value={a.comment}
            onChange={e => setAnswer(item, { comment: e.target.value })} style={{ ...inputStyle(), marginTop: 6 }} />
        )}
      </div>
    )
  }

  const renderForm = () => (
    <Overlay onClose={() => setOpen(false)} width={620}>
      <ModalHead title={`${TYPE_LABEL[type]} inspection`} onClose={() => setOpen(false)} />
      <div style={{ marginBottom: 10 }}><label htmlFor="in-type" style={lbl}>Type of inspection</label>
        <select id="in-type" value={type} onChange={e => setType(e.target.value)} style={inputStyle()}>
          {Object.entries(TYPE_LABEL).map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select></div>
      <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr", gap: 12, marginBottom: 10 }}>
        <div><label htmlFor="in-asset" style={lbl}>Machine *</label>
          <select id="in-asset" value={formAsset} onChange={e => setFormAsset(e.target.value)} style={inputStyle()}>
            <option value="">Choose…</option>{usable.map(a => <option key={a.id} value={a.id}>{a.fleetNumber ? `${a.fleetNumber} — ` : ""}{a.name}</option>)}</select></div>
        <div><label htmlFor="in-meter" style={lbl}>Meter reading{type === "PRE_USE" ? "" : " *"}</label>
          <input id="in-meter" type="number" min="0" value={meter} onChange={e => setMeter(e.target.value)} style={inputStyle()} /></div>
      </div>
      <div style={{ maxHeight: 340, overflowY: "auto", paddingRight: 4 }}>{checklist.map(renderItem)}</div>
      <label htmlFor="in-notes" style={{ ...lbl, marginTop: 10 }}>Notes</label>
      <input id="in-notes" value={notes} onChange={e => setNotes(e.target.value)} style={inputStyle()} />
      {unsafeCount > 0 && (
        <div role="alert" style={{ ...card, marginTop: 10, fontSize: 13, color: "var(--hf-danger-text)" }}>
          {unsafeCount} unsafe finding{unsafeCount === 1 ? "" : "s"}: saving will take this machine out of service and raise a work order. Do not use it.
        </div>
      )}
      {err && <ErrBanner msg={err} />}
      <ModalFoot onCancel={() => setOpen(false)} onSubmit={submit} loading={create.isPending} label="Save inspection"
        disabled={!formAsset || checklist.length === 0 || missingNote.length > 0 || (type !== "PRE_USE" && meter.trim() === "")} />
    </Overlay>
  )

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, marginBottom: 14, flexWrap: "wrap" }}>
        <select aria-label="Machine" value={assetId} onChange={e => setAssetId(e.target.value)} style={{ ...inputStyle(), maxWidth: 320 }}>
          <option value="">All machines</option>{usable.map(a => <option key={a.id} value={a.id}>{a.fleetNumber ? `${a.fleetNumber} — ` : ""}{a.name}</option>)}</select>
        {perms.canWrite && <button style={btn(true)} onClick={openForm}>New inspection</button>}
      </div>
      {notice && <div role="status" style={{ ...card, marginBottom: 12 }}>{notice}</div>}
      {isLoading ? <div style={{ color: "var(--hf-text-muted)" }}>Loading…</div>
        : inspections.length === 0 ? (
          <div style={{ textAlign: "center", padding: "50px 20px", border: "1px dashed var(--hf-border)", borderRadius: 12, color: "var(--hf-text-faint)" }}>
            <ClipboardCheck size={34} style={{ marginBottom: 10, opacity: 0.4 }} />
            <div style={{ fontWeight: 600 }}>No inspections yet</div>
            <div style={{ fontSize: 13, marginTop: 4 }}>Record the walk-round before a machine is used. Unsafe findings stop the machine.</div>
          </div>
        ) : <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>{inspections.map(renderInspection)}</div>}
      {open && renderForm()}
    </div>
  )
}
