// Merge a duplicate record into this one (or this one into the other). Shows exactly what would move and where the two records
// disagree before anything is changed; the record merged away is archived, never deleted (patch 0188).
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import ModalShell from "./ModalShell"
import PatientPicker, { type PickerPatient } from "./PatientPicker"
import { apiClient } from "../../api/client"
import { BORDER, GRAY, RED_TEXT, lbl, sinp } from "./patientFile.shared"
import { primaryBtn, smallBtn } from "./OverviewCard"
import { canConfirm, cardLine, consequence, fieldLabel, type MergePreview } from "./mergeView"

interface Here { id: string; fullName: string; patientNumber?: string | null; dateOfBirth?: string | null }

export default function MergePatientModal({ here, onClose, onMerged }: { here: Here; onClose: () => void; onMerged: (survivorId: string) => void }) {
  const qc = useQueryClient()
  const [other, setOther] = useState<PickerPatient | null>(null)
  const [keepHere, setKeepHere] = useState(true)
  const [reason, setReason] = useState("")
  const [ack, setAck] = useState(false)
  const [error, setError] = useState("")
  const survivorId = other ? (keepHere ? here.id : other.id) : ""
  const mergedId = other ? (keepHere ? other.id : here.id) : ""

  const { data: preview, isFetching, isError } = useQuery<MergePreview>({
    queryKey: ["clinic-merge-preview", survivorId, mergedId], enabled: !!other && other.id !== here.id,
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/clinic/patients/merge-preview?survivorId=${survivorId}&mergedId=${mergedId}`)
      return (r.data?.data ?? r.data) as MergePreview
    },
  })
  const merge = useMutation({
    mutationFn: () => apiClient.post("/api/v1/clinic/patients/merge", { survivorId, mergedId, reason: reason.trim(), acknowledgeConflicts: ack }),
    onSuccess: () => { qc.invalidateQueries(); onMerged(survivorId) },
    onError: (e: any) => setError(e?.response?.data?.message ?? "Could not merge these records. Nothing was changed."),
  })
  const pick = (p: PickerPatient | null) => { setOther(p); setAck(false); setError("") }
  const ready = canConfirm(preview, reason, ack) && !merge.isPending

  return (
    <ModalShell title="Merge duplicate record" onClose={onClose} width={640}
      footer={<><button type="button" style={smallBtn} onClick={onClose}>Cancel</button>
        <button type="button" disabled={!ready} onClick={() => merge.mutate()} style={{ ...primaryBtn, padding: "9px 18px", fontSize: 14, opacity: ready ? 1 : 0.6 }}>
          {merge.isPending ? "Merging…" : "Merge records"}</button></>}>
      <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
        <div style={{ fontSize: 12, color: GRAY }}>Use this when the same person was registered twice. Everything on the record merged away (visits, medicines, claims, documents and more) moves to the record you keep. The other record is archived, not deleted.</div>
        <div style={{ fontSize: 13 }}><b>This record:</b> {cardLine({ id: here.id, patientNumber: here.patientNumber, fullName: here.fullName, dateOfBirth: here.dateOfBirth })}</div>
        <PatientPicker label="The other record" value={other} onChange={pick} placeholder="Search for the duplicate by name, ID or phone…" />
        {other && other.id === here.id && <div role="alert" style={{ fontSize: 13, color: RED_TEXT }}>That is this same record. Choose the duplicate.</div>}

        {other && other.id !== here.id && <>
          <fieldset style={{ border: `1px solid ${BORDER}`, borderRadius: 8, padding: "8px 12px" }}>
            <legend style={{ fontSize: 12, color: GRAY }}>Which record do you keep?</legend>
            <label style={{ display: "flex", gap: 8, fontSize: 13, padding: "3px 0" }}><input type="radio" name="keep" checked={keepHere} onChange={() => { setKeepHere(true); setAck(false) }} /> Keep this record: {here.fullName}</label>
            <label style={{ display: "flex", gap: 8, fontSize: 13, padding: "3px 0" }}><input type="radio" name="keep" checked={!keepHere} onChange={() => { setKeepHere(false); setAck(false) }} /> Keep the other record: {other.fullName}</label>
          </fieldset>

          {isFetching && !preview && <div style={{ fontSize: 13, color: GRAY }}>Checking what would move…</div>}
          {isError && <div role="alert" style={{ fontSize: 13, color: RED_TEXT }}>Could not check these records.</div>}
          {preview && <>
            {preview.blockers.length > 0 && <div role="alert" style={{ fontSize: 13, color: RED_TEXT }}>{preview.blockers.map(b => <div key={b}>{b}</div>)}</div>}
            <div style={{ fontSize: 13 }}><b>Kept:</b> {cardLine(preview.keep)}<br /><b>Merged away:</b> {cardLine(preview.mergeAway)}</div>
            {preview.moves.length > 0 && <div><div style={lbl}>What moves</div>
              <ul style={{ margin: "4px 0 0", paddingLeft: 18, fontSize: 13 }}>{preview.moves.map(m => <li key={m.table}>{m.label}: {m.rows}</li>)}</ul></div>}
            {preview.fills.length > 0 && <div style={{ fontSize: 13 }}><b>Filled in from the other record</b> (the kept record had nothing): {preview.fills.map(fieldLabel).join(", ")}.</div>}
            {preview.conflicts.length > 0 && <div><div style={lbl}>Where they disagree</div>
              <ul style={{ margin: "4px 0 0", paddingLeft: 18, fontSize: 13 }}>{preview.conflicts.map(c => <li key={c.field} style={{ color: c.blocking ? RED_TEXT : undefined }}>{c.text}{c.blocking ? " (check this)" : ""}</li>)}</ul></div>}
            {preview.staysBehind.length > 0 && <div style={{ fontSize: 12, color: GRAY }}>Stays with the archived record: {preview.staysBehind.join("; ")}.</div>}
            {preview.blockers.length === 0 && <>
              {preview.needsAcknowledgement && <label style={{ display: "flex", gap: 8, fontSize: 13 }}><input type="checkbox" checked={ack} onChange={e => setAck(e.target.checked)} /> I have checked and these are the same person.</label>}
              <div><label style={lbl} htmlFor="merge-reason">Why are these records being merged?</label>
                <input id="merge-reason" style={sinp} maxLength={500} value={reason} onChange={e => setReason(e.target.value)} placeholder="e.g. Registered twice at the front desk" /></div>
              <div style={{ fontSize: 12, color: GRAY }}>{consequence(preview)}</div>
            </>}
          </>}
        </>}
        {error && <div role="alert" style={{ fontSize: 13, color: RED_TEXT }}>{error}</div>}
      </div>
    </ModalShell>
  )
}
