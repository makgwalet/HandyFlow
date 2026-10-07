// src/pages/clinic/RxDraftsPanel.tsx
// The prescription drafts of the live consultation session (right-hand column).
// Moved out of ConsultationSession.tsx; clearing the quantity box no longer shows "NaN".

import { Pill, Plus, X } from "lucide-react"
import { AllergyWarning } from "./PrescriptionAllergyCheck"
import {
  BORDER, GRAY_TEXT, GREEN_TEXT, LIGHT, RED_TEXT, lbl, sectionLabel, sinp, type RxDraft,
} from "./consultationSession.shared"

interface Props {
  rxDrafts: RxDraft[]
  allergyResults: Record<string, any>
  updateRx: (id: string, k: keyof RxDraft, v: any) => void
  removeRx: (id: string) => void
  addBlankRx: () => void
}

export default function RxDraftsPanel({ rxDrafts, allergyResults, updateRx, removeRx, addBlankRx }: Props) {
  return (
    <>
          <div style={{padding:"12px 14px",background:"var(--hf-surface)",border:`1px solid ${BORDER}`,borderRadius:10}}>
            <div style={{...sectionLabel,marginBottom:8}}>Prescriptions ({rxDrafts.length})</div>
            <div style={{fontSize:11,color:GRAY_TEXT}}>
              Medications added via search auto-appear here. Complete dosage details before finishing.
            </div>
          </div>

          <div style={{flex:1,overflowY:"auto",display:"flex",flexDirection:"column",gap:10}}>
            {rxDrafts.length===0 ? (
              <div style={{textAlign:"center",padding:"40px 20px",color:GRAY_TEXT,
                border:`1px dashed ${BORDER}`,borderRadius:10,fontSize:13}}>
                <Pill size={28} style={{marginBottom:8,opacity:0.4}}/>
                <div>Medications added during the consultation appear here.</div>
              </div>
            ) : rxDrafts.map(rx=>(
              <div key={rx.id} style={{padding:"12px 14px",background:rx.fromBill?"var(--hf-success-soft)":"var(--hf-surface)",
                border:`1px solid ${rx.fromBill?"var(--hf-success-border)":BORDER}`,borderRadius:10,position:"relative"}}>
                {rx.fromBill && (
                  <div style={{position:"absolute",top:8,right:8,fontSize:10,fontWeight:700,
                    color:GREEN_TEXT,background:"var(--hf-success-soft-strong)",padding:"1px 6px",borderRadius:20}}>
                    Added to bill
                  </div>
                )}
                <div style={{fontWeight:700,fontSize:13,color:"var(--hf-text)",marginBottom:8,paddingRight:70}}>
                  {rx.medicationName}
                </div>
                <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:8}}>
                  <div>
                    <label style={lbl}>Dosage</label>
                    <input value={rx.dosage} onChange={e=>updateRx(rx.id,"dosage",e.target.value)}
                      placeholder="500mg" style={{...sinp,padding:"5px 8px",fontSize:12}}/>
                  </div>
                  <div>
                    <label style={lbl}>Frequency</label>
                    <input value={rx.frequency} onChange={e=>updateRx(rx.id,"frequency",e.target.value)}
                      placeholder="3× daily" style={{...sinp,padding:"5px 8px",fontSize:12}}/>
                  </div>
                  <div>
                    <label style={lbl}>Duration</label>
                    <input value={rx.duration} onChange={e=>updateRx(rx.id,"duration",e.target.value)}
                      placeholder="7 days" style={{...sinp,padding:"5px 8px",fontSize:12}}/>
                  </div>
                  <div>
                    <label style={lbl}>Qty</label>
                    <input type="number" value={Number.isNaN(rx.quantity)?"":rx.quantity} onChange={e=>updateRx(rx.id,"quantity",parseInt(e.target.value))}
                      style={{...sinp,padding:"5px 8px",fontSize:12}}/>
                  </div>
                  <div style={{gridColumn:"1/-1"}}>
                    <label style={lbl}>Instructions</label>
                    <input value={rx.instructions} onChange={e=>updateRx(rx.id,"instructions",e.target.value)}
                      placeholder="Take with food" style={{...sinp,padding:"5px 8px",fontSize:12}}/>
                  </div>
                </div>
                <AllergyWarning result={allergyResults[rx.id]} reason={rx.allergyReason??""}
                  onReason={v=>updateRx(rx.id,"allergyReason",v)} />
                <button onClick={()=>removeRx(rx.id)}
                  style={{position:"absolute",bottom:8,right:8,background:"none",border:"none",
                    cursor:"pointer",color:RED_TEXT,fontSize:11,display:"flex",alignItems:"center",gap:3}}>
                  <X size={10}/> Remove
                </button>
              </div>
            ))}

            {/* Manual add Rx */}
            <button onClick={addBlankRx}
              style={{display:"flex",alignItems:"center",gap:5,padding:"7px 12px",
                border:`1px dashed ${BORDER}`,borderRadius:8,background:LIGHT,
                color:GRAY_TEXT,fontSize:12,cursor:"pointer"}}>
              <Plus size={12}/> Add prescription manually
            </button>
          </div>
    </>
  )
}
