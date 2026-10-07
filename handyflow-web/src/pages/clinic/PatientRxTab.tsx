// src/pages/clinic/PatientRxTab.tsx
// Split out of PatientFilePage.tsx (W-4); behaviour unchanged.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import RxFillControl from "./RxFillControl"
import { Pill } from "lucide-react"
import type { Consultation, Patient, Prescription } from "./patientFile.shared"
import { AMBER, BORDER, Empty, GRAY, GREEN, GREEN_TEXT, NAVY, TEAL, fmtDT, unwrap } from "./patientFile.shared"

// ── PRESCRIPTIONS TAB ─────────────────────────────────────────────────────────

export default function PrescriptionsTab({ patient, consultations }:
  {patient:Patient; consultations:Consultation[]}) {
  const qc = useQueryClient()
  const [filter, setFilter] = useState<"active"|"all">("active")
  const { data: allRx=[], isLoading } = useQuery({
    queryKey:["pf-all-rx",patient.id,consultations.length],
    queryFn: async () => {
      const results: Prescription[]=[]
      for (const c of consultations.slice(0,20)) {
        try { const r=await apiClient.get(`/api/v1/clinic/consultations/${c.id}/prescriptions`); results.push(...(unwrap(r) as Prescription[]).map((rx:Prescription)=>({...rx,practitionerName:(c as any).practitionerName}))) } catch {}
      }
      return results
    },
    enabled: consultations.length>0,
  })
  const displayed = filter==="active" ? allRx.filter((rx:Prescription)=>!rx.dispensed) : allRx
  return (
    <div>
      <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:20 }}>
        <div style={{ display:"flex", gap:6 }}>
          {(["active","all"] as const).map(f=>(
            <button key={f} onClick={()=>setFilter(f)} style={{ padding:"6px 14px", borderRadius:20, border:"none", fontSize:12, fontWeight:filter===f?600:400, background:filter===f?NAVY:"var(--hf-surface-sunken)", color:filter===f?"var(--hf-text-on-solid)":GRAY, cursor:"pointer" }}>
              {f==="active"?"Active prescriptions":"All history"}
            </button>
          ))}
        </div>
        <div style={{ fontSize:13, color:GRAY }}>{displayed.length} prescription{displayed.length!==1?"s":""}</div>
      </div>
      {isLoading?<div style={{textAlign:"center",padding:40,color:GRAY}}>Loading...</div>
      :displayed.length===0?<Empty icon={Pill} msg={filter==="active"?"No active prescriptions":"No prescription history"}/>:(
        <div style={{ display:"flex", flexDirection:"column", gap:10 }}>
          {[...displayed].sort((a:any,b:any)=>b.prescribedAt.localeCompare(a.prescribedAt)).map((rx:Prescription)=>(
            <div key={rx.id} style={{ border:`1px solid ${rx.dispensed?"var(--hf-success-border)":BORDER}`, borderLeft:`4px solid ${rx.dispensed?GREEN:TEAL}`, borderRadius:10, padding:"14px 18px", background:rx.dispensed?"var(--hf-success-soft)":"var(--hf-surface)" }}>
              <div style={{ display:"flex", justifyContent:"space-between", alignItems:"flex-start" }}>
                <div style={{ flex:1 }}>
                  <div style={{ fontWeight:700, fontSize:15, color:"var(--hf-text)", marginBottom:4 }}>{rx.medicationName}</div>
                  <div style={{ fontSize:13, color:GRAY, marginBottom:4 }}>{[rx.dosage,rx.frequency,rx.duration].filter(Boolean).join(" · ")}{rx.quantity?` · Qty: ${rx.quantity}`:""}{rx.repeats>0?` · Repeats: ${rx.repeats}`:""}</div>
                  {rx.instructions&&<div style={{ fontSize:12, color:"var(--hf-text-tertiary)", fontStyle:"italic", marginBottom:4 }}>{rx.instructions}</div>}
                  <div style={{ fontSize:11, color:GRAY }}>Prescribed {fmtDT(rx.prescribedAt)}{rx.practitionerName&&` · Dr. ${rx.practitionerName}`}</div>
                  <RxFillControl rx={rx} onRecorded={()=>qc.invalidateQueries({queryKey:["pf-all-rx",patient.id]})} />
                </div>
                <div style={{ flexShrink:0, marginLeft:12 }}>
                  {rx.dispensed?<span style={{ background:"var(--hf-success-soft-strong)",color:GREEN_TEXT,padding:"3px 10px",borderRadius:20,fontSize:12,fontWeight:700 }}>DISPENSED</span>:<span style={{ background:"var(--hf-orange-soft)",color:AMBER,padding:"3px 10px",borderRadius:20,fontSize:12,fontWeight:700 }}>ACTIVE</span>}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

