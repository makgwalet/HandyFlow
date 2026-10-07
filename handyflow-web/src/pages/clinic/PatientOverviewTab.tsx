// src/pages/clinic/PatientOverviewTab.tsx
// Split out of PatientFilePage.tsx (W-4); behaviour unchanged.
import { useState } from "react"
import PatientNotesPanel from "./PatientNotes"
import ObservationMatrix from "./ObservationMatrix"
import PatientSummaryPrint from "./PatientSummaryPrint"
import { useMutation } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import ClinicalSummaryPanel from "./ClinicalSummaryPanel"
import type { Patient } from "./patientFile.shared"
import { BORDER, GRAY, GREEN_TEXT, LIGHT, Modal, ModalFooter, RED_TEXT, TEAL_TEXT, lbl, sinp } from "./patientFile.shared"
import { ArrowRight, Plus } from "lucide-react"

// ── OVERVIEW TAB ──────────────────────────────────────────────────────────────

export default function OverviewTab({ patient, idInfo, familyMembers, onOpenPatient, qc }: {
  patient:Patient; idInfo:any; familyMembers:Patient[]; onOpenPatient?:(p:Patient)=>void; qc:any
}) {
  const [showAddDep, setShowAddDep] = useState(false)
  const [depForm, setDepForm] = useState({ firstName:"", lastName:"", idNumber:"", dateOfBirth:"", gender:"", phone:"", relationship:"CHILD" })
  const [depError, setDepError] = useState("")

  const addDependant = useMutation({
    mutationFn: (body:any)=>apiClient.post("/api/v1/clinic/patients",body),
    onSuccess: ()=>{
      qc.invalidateQueries({queryKey:["pf-family"]})
      qc.invalidateQueries({queryKey:["clinic-patients"]})
      setShowAddDep(false)
      setDepForm({firstName:"",lastName:"",idNumber:"",dateOfBirth:"",gender:"",phone:"",relationship:"CHILD"})
    },
    onError:(e:any)=>setDepError(e.response?.data?.message??"Failed to add dependant"),
  })

  const principalId = patient.accountType==="PRINCIPAL" ? patient.id : patient.principalId

  return (
    <div style={{ display:"grid", gridTemplateColumns:"2fr 1fr", gap:20 }}>
      {/* Left — demographics */}
      <div>
        <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:12, marginBottom:16 }}>
          {[
            {label:"SA ID",             value:patient.idNumber||"—"},
            {label:"Date of birth",     value:idInfo?`${idInfo.dob} (${idInfo.age} yrs)`:patient.dateOfBirth||"—"},
            {label:"Gender",            value:patient.gender?.replace("_"," ")||"—"},
            {label:"Phone",             value:patient.phone||"—"},
            {label:"Email",             value:patient.email||"—"},
            {label:"Emergency contact", value:patient.emergencyContactName||"—"},
            {label:"Emergency phone",   value:patient.emergencyContactPhone||"—"},
          ].map(item=>(
            <div key={item.label} style={{ padding:"11px 14px", background:LIGHT,
              borderRadius:10, border:`1px solid ${BORDER}` }}>
              <div style={{ fontSize:10, fontWeight:700, color:GRAY, textTransform:"uppercase",
                letterSpacing:"0.06em", marginBottom:3 }}>{item.label}</div>
              <div style={{ fontSize:14, color:"var(--hf-text)", fontWeight:500, wordBreak:"break-all" }}>{item.value}</div>
            </div>
          ))}
        </div>

        {/* Allergies, conditions and medicines: structured records, editable by clinicians */}
        <ClinicalSummaryPanel patientId={patient.id} fallbackAllergies={patient.allergies} fallbackConditions={patient.chronicConditions} />
        <PatientNotesPanel patientId={patient.id} />
        <ObservationMatrix patientId={patient.id} />
        <div style={{ marginTop: 16 }}><PatientSummaryPrint patient={patient as any} /></div>

        {patient.notes && (
          <div style={{ padding:"12px 14px", background:LIGHT, borderRadius:10, border:`1px solid ${BORDER}` }}>
            <div style={{ fontSize:10, fontWeight:700, color:GRAY, marginBottom:4, textTransform:"uppercase", letterSpacing:"0.06em" }}>Notes</div>
            <div style={{ fontSize:13, color:"var(--hf-text-tertiary)", lineHeight:1.6 }}>{patient.notes}</div>
          </div>
        )}
      </div>

      {/* Right — family + status */}
      <div style={{ display:"flex", flexDirection:"column", gap:12 }}>
        {/* Account status */}
        <div style={{ padding:"14px 16px", background:LIGHT, border:`1px solid ${BORDER}`, borderRadius:12 }}>
          <div style={{ fontSize:11, fontWeight:700, color:GRAY, textTransform:"uppercase", letterSpacing:"0.06em", marginBottom:8 }}>Account status</div>
          <div style={{ display:"flex", gap:8, flexWrap:"wrap" }}>
            <span style={{ background:patient.active?"var(--hf-success-soft-strong)":"var(--hf-danger-soft)",
              color:patient.active?GREEN_TEXT:RED_TEXT, padding:"3px 10px", borderRadius:20,
              fontSize:12, fontWeight:700 }}>{patient.active?"ACTIVE":"INACTIVE"}</span>
            {patient.archivedAt && <span style={{ background:"var(--hf-surface-sunken)", color:GRAY, padding:"3px 10px", borderRadius:20, fontSize:12, fontWeight:700 }}>ARCHIVED</span>}
          </div>
        </div>

        {/* Family section — shown for PRINCIPAL or DEPENDANT */}
        {(patient.accountType==="PRINCIPAL" || patient.accountType==="DEPENDANT") && (
          <div style={{ padding:"14px 16px", background:LIGHT, border:`1px solid ${BORDER}`, borderRadius:12 }}>
            <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:10 }}>
              <div style={{ fontSize:11, fontWeight:700, color:GRAY, textTransform:"uppercase", letterSpacing:"0.06em" }}>
                Family account
              </div>
              {patient.accountType==="PRINCIPAL" && (
                <button onClick={()=>setShowAddDep(true)}
                  style={{ display:"flex", alignItems:"center", gap:4, padding:"4px 10px",
                    background:"var(--hf-info-soft)", color:"var(--hf-info-text)", border:"1px solid var(--hf-info-border)",
                    borderRadius:6, fontSize:11, fontWeight:600, cursor:"pointer" }}>
                  <Plus size={11}/> Add
                </button>
              )}
            </div>

            {/* Dependants list */}
            {(familyMembers as Patient[]).length===0 ? (
              <div style={{ fontSize:12, color:GRAY, fontStyle:"italic" }}>No dependants linked yet.</div>
            ) : (
              <div style={{ display:"flex", flexDirection:"column", gap:8 }}>
                {(familyMembers as Patient[]).map(m=>{
                  const isCurrentPatient = m.id===patient.id
                  return (
                    <div key={m.id}
                      onClick={()=>!isCurrentPatient && onOpenPatient && onOpenPatient(m)}
                      style={{ display:"flex", alignItems:"center", gap:10, padding:"8px 10px",
                        background:"var(--hf-surface)", border:`1px solid ${BORDER}`, borderRadius:8,
                        cursor:isCurrentPatient?"default":"pointer" }}
                      onMouseEnter={e=>{ if (!isCurrentPatient) (e.currentTarget as HTMLDivElement).style.background="var(--hf-success-soft)" }}
                      onMouseLeave={e=>{ (e.currentTarget as HTMLDivElement).style.background="var(--hf-surface)" }}>
                      <div style={{ width:28, height:28, borderRadius:"50%",
                        background:isCurrentPatient?"var(--hf-sky-soft-strong)":"var(--hf-success-soft)",
                        display:"flex", alignItems:"center", justifyContent:"center",
                        fontSize:11, fontWeight:700, color:isCurrentPatient?"var(--hf-sky-text-strong)":TEAL_TEXT, flexShrink:0 }}>
                        {m.firstName?.[0]}{m.lastName?.[0]}
                      </div>
                      <div style={{ flex:1, minWidth:0 }}>
                        <div style={{ fontSize:12, fontWeight:600, color:"var(--hf-text)",
                          overflow:"hidden", textOverflow:"ellipsis", whiteSpace:"nowrap" as const }}>
                          {m.fullName}
                          {isCurrentPatient && <span style={{ fontSize:10, color:GRAY, marginLeft:4 }}>(this patient)</span>}
                        </div>
                        {m.relationship && (
                          <div style={{ fontSize:10, color:GRAY }}>
                            {m.relationship.charAt(0)+m.relationship.slice(1).toLowerCase()}
                          </div>
                        )}
                      </div>
                      {!isCurrentPatient && <ArrowRight size={12} style={{ color: GRAY }}/>}
                    </div>
                  )
                })}
              </div>
            )}
          </div>
        )}
      </div>

      {/* Add dependant modal */}
      {showAddDep && (
        <Modal title="Add dependant to family account" onClose={()=>setShowAddDep(false)}>
          <div style={{ fontSize:12, color:GRAY, marginBottom:16, padding:"8px 12px", background:"var(--hf-info-soft)", borderRadius:8 }}>
            Principal: <strong>{patient.fullName}</strong> · Emergency contact will be auto-filled from principal.
          </div>
          <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:12 }}>
            <div>
              <label style={lbl}>First name *</label>
              <input autoFocus value={depForm.firstName} onChange={e=>setDepForm(f=>({...f,firstName:e.target.value}))} placeholder="Alex" style={sinp}/>
            </div>
            <div>
              <label style={lbl}>Last name *</label>
              <input value={depForm.lastName} onChange={e=>setDepForm(f=>({...f,lastName:e.target.value}))} placeholder="Smith" style={sinp}/>
            </div>
            <div style={{ gridColumn:"1/-1" }}>
              <label style={lbl}>SA ID number</label>
              <input value={depForm.idNumber}
                onChange={e=>{
                  const v=e.target.value.replace(/\D/g,"").slice(0,13)
                  const yy=+v.slice(0,2),mm=+v.slice(2,4),dd=+v.slice(4,6)
                  const yr=yy<=(new Date().getFullYear()%100)?2000+yy:1900+yy
                  setDepForm(f=>({...f, idNumber:v,
                    ...(v.length===13?{
                      dateOfBirth:`${yr}-${String(mm).padStart(2,"0")}-${String(dd).padStart(2,"0")}`,
                      gender:+v[6]>=5?"MALE":"FEMALE"
                    }:{})
                  }))
                }}
                placeholder="ID number" inputMode="numeric" style={sinp}/>
            </div>
            <div>
              <label style={lbl}>Relationship *</label>
              <select value={depForm.relationship} onChange={e=>setDepForm(f=>({...f,relationship:e.target.value}))} style={sinp}>
                {["CHILD","PARENT","GRANDPARENT","SPOUSE","SIBLING","OTHER"].map(r=>(
                  <option key={r} value={r}>{r.charAt(0)+r.slice(1).toLowerCase()}</option>
                ))}
              </select>
            </div>
            <div>
              <label style={lbl}>Date of birth</label>
              <input type="date" value={depForm.dateOfBirth} onChange={e=>setDepForm(f=>({...f,dateOfBirth:e.target.value}))} style={sinp}/>
            </div>
            <div>
              <label style={lbl}>Gender</label>
              <select value={depForm.gender} onChange={e=>setDepForm(f=>({...f,gender:e.target.value}))} style={sinp}>
                <option value="">Select...</option>
                {["MALE","FEMALE","NON_BINARY","PREFER_NOT_TO_SAY"].map(g=><option key={g} value={g}>{g.replace("_"," ")}</option>)}
              </select>
            </div>
            <div style={{ gridColumn:"1/-1" }}>
              <label style={lbl}>Phone</label>
              <input value={depForm.phone} onChange={e=>setDepForm(f=>({...f,phone:e.target.value}))} placeholder="+27 82 000 0000" style={sinp}/>
            </div>
          </div>
          {depError && <div style={{ marginTop:10, padding:"8px 12px", background:"var(--hf-danger-soft)", border:"1px solid var(--hf-danger-border)", borderRadius:8, fontSize:13, color:RED_TEXT }}>{depError}</div>}
          <ModalFooter onCancel={()=>setShowAddDep(false)}
            onConfirm={()=>{
              if (!depForm.firstName.trim()||!depForm.lastName.trim()) return
              addDependant.mutate({
                firstName:depForm.firstName, lastName:depForm.lastName,
                idNumber:depForm.idNumber||null, dateOfBirth:depForm.dateOfBirth||null,
                gender:depForm.gender||null, phone:depForm.phone||null,
                emergencyContactName:patient.fullName,
                emergencyContactPhone:patient.phone||null,
                accountType:"DEPENDANT",
                principalId:patient.id,
                relationship:depForm.relationship,
              })
            }}
            confirmLabel={addDependant.isPending?"Adding...":"Add dependant"} loading={addDependant.isPending}/>
        </Modal>
      )}
    </div>
  )
}
