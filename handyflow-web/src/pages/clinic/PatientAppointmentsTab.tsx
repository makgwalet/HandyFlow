// src/pages/clinic/PatientAppointmentsTab.tsx
// Split out of PatientFilePage.tsx (W-4); behaviour unchanged.
import { useState } from "react"
import { useMutation } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import type { Appointment, Patient, Practitioner } from "./patientFile.shared"
import { BORDER, Empty, ErrBox, GRAY, Modal, ModalFooter, STATUS_CFG, STATUS_FLOW, btnPrimary, fmtDT, fmtTime, lbl, sinp } from "./patientFile.shared"
import { Calendar, Plus } from "lucide-react"

// ── APPOINTMENTS TAB ──────────────────────────────────────────────────────────

export default function AppointmentsTab({ patient, appointments, practitioners, qc, onStartSession }:
  {patient:Patient; appointments:Appointment[]; practitioners:Practitioner[]; qc:any; onStartSession:(appt:Appointment)=>void}) {
  const [showBook, setShowBook] = useState(false)
  const [form, setForm] = useState({practitionerId:"",scheduledAt:"",durationMinutes:"30",appointmentType:"CONSULTATION",reason:""})
  const [apiError, setApiError] = useState("")

  const doAction = useMutation({
    mutationFn: ({id,action}:{id:string;action:string})=>apiClient.post(`/api/v1/clinic/appointments/${id}/${action}`),
    onSuccess: (_res, vars)=>{
      qc.invalidateQueries({queryKey:["pf-appointments",patient.id]})
      qc.invalidateQueries({queryKey:["clinic-appts-dashboard"]})
      // When "start" action completes, launch the consultation session
      if (vars.action === "start") {
        const appt = appointments.find(a=>a.id===vars.id)
        if (appt) onStartSession(appt)
      }
    },
  })
  const book = useMutation({
    mutationFn: (body:any)=>apiClient.post("/api/v1/clinic/appointments",body),
    onSuccess: ()=>{ qc.invalidateQueries({queryKey:["pf-appointments",patient.id]}); setShowBook(false) },
    onError:(e:any)=>setApiError(e.response?.data?.message??"Failed"),
  })

  const sorted = [...appointments].sort((a,b)=>b.scheduledAt.localeCompare(a.scheduledAt))

  return (
    <div>
      <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:20 }}>
        <div style={{ fontSize:15, fontWeight:700, color:"var(--hf-text)" }}>
          {appointments.length} appointment{appointments.length!==1?"s":""}
        </div>
        <button onClick={()=>setShowBook(true)} style={btnPrimary}><Plus size={14}/> Book appointment</button>
      </div>

      {sorted.length===0 ? <Empty icon={Calendar} msg="No appointments yet"/> : (
        <div style={{ display:"flex", flexDirection:"column", gap:10 }}>
          {sorted.map((a:any)=>{
            const s=STATUS_CFG[a.status]??STATUS_CFG.SCHEDULED
            const actions=STATUS_FLOW[a.status]??[]
            return (
              <div key={a.id} style={{ border:`1px solid ${BORDER}`, borderLeft:`4px solid ${s.color}`,
                borderRadius:10, padding:"14px 18px", background:"var(--hf-surface)" }}>
                <div style={{ display:"flex", justifyContent:"space-between", alignItems:"flex-start" }}>
                  <div>
                    <div style={{ display:"flex", alignItems:"center", gap:8, marginBottom:4 }}>
                      <span style={{ fontWeight:700, fontSize:14, color:"var(--hf-text)" }}>{fmtDT(a.scheduledAt)} · {fmtTime(a.scheduledAt)}</span>
                      <span style={{ background:s.bg, color:s.color, padding:"1px 8px", borderRadius:20, fontSize:11, fontWeight:700 }}>{s.label}</span>
                      <span style={{ fontSize:11, color:GRAY }}>{a.appointmentType?.replace("_"," ")}</span>
                    </div>
                    <div style={{ fontSize:12, color:GRAY }}>
                      {a.practitionerName?`Dr. ${a.practitionerName}`:"No practitioner"}
                      {a.reason?` · ${a.reason}`:""}
                      {a.durationMinutes?` · ${a.durationMinutes}min`:""}
                    </div>
                  </div>
                  {actions.length>0 && (
                    <div style={{ display:"flex", gap:6 }}>
                      {actions.map((btn:any)=>(
                        <button key={btn.action} onClick={()=>doAction.mutate({id:a.id,action:btn.action})}
                          disabled={doAction.isPending}
                          style={{ padding:"5px 12px", border:"none", borderRadius:7, fontSize:12, fontWeight:600, cursor:"pointer", background:`color-mix(in srgb, ${btn.color} 9%, transparent)`, color:btn.color }}>
                          {btn.label}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      )}

      {showBook && (
        <Modal title={`Book appointment — ${patient.fullName}`} onClose={()=>setShowBook(false)}>
          <div style={{ display:"flex", flexDirection:"column", gap:14 }}>
            <div>
              <label style={lbl}>Practitioner</label>
              <select value={form.practitionerId} onChange={e=>setForm(f=>({...f,practitionerId:e.target.value}))} style={sinp}>
                <option value="">Any / unassigned</option>
                {practitioners.map(p=><option key={p.id} value={p.id}>{p.fullName} — {p.specialty}</option>)}
              </select>
            </div>
            <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:12 }}>
              <div><label style={lbl}>Date & time *</label><input type="datetime-local" value={form.scheduledAt} onChange={e=>setForm(f=>({...f,scheduledAt:e.target.value}))} style={sinp}/></div>
              <div><label style={lbl}>Duration (min)</label><input type="number" min="5" max="480" value={form.durationMinutes} onChange={e=>setForm(f=>({...f,durationMinutes:e.target.value}))} style={sinp}/></div>
              <div>
                <label style={lbl}>Type</label>
                <select value={form.appointmentType} onChange={e=>setForm(f=>({...f,appointmentType:e.target.value}))} style={sinp}>
                  {["CONSULTATION","FOLLOW_UP","PROCEDURE","EMERGENCY","CHECKUP","ANTENATAL","RESULTS_REVIEW"].map(t=><option key={t} value={t}>{t.replace("_"," ")}</option>)}
                </select>
              </div>
              <div><label style={lbl}>Reason</label><input value={form.reason} onChange={e=>setForm(f=>({...f,reason:e.target.value}))} placeholder="Optional" style={sinp}/></div>
            </div>
          </div>
          {apiError && <ErrBox msg={apiError}/>}
          <ModalFooter onCancel={()=>setShowBook(false)}
            onConfirm={()=>{ if(!form.scheduledAt) return; book.mutate({patientId:patient.id, practitionerId:form.practitionerId||null, scheduledAt:new Date(form.scheduledAt).toISOString(), durationMinutes:parseInt(form.durationMinutes)||30, appointmentType:form.appointmentType, reason:form.reason||null}) }}
            confirmLabel="Book appointment" loading={book.isPending}/>
        </Modal>
      )}
    </div>
  )
}
