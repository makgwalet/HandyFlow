// src/pages/clinic/PatientConsultationTab.tsx
// Split out of PatientFilePage.tsx (W-4); behaviour unchanged.
import { useDialogs } from "./dialogs"
import { useRef, useState } from "react"
import { useMutation, useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import PrescriptionForm from "./PrescriptionForm"
import type { Consultation, Patient, Practitioner, Prescription } from "./patientFile.shared"
import { AMBER, BORDER, Empty, ErrBox, FSect, GRAY, GREEN_TEXT, LIGHT, Modal, ModalFooter, PURPLE, PURPLE_TEXT, RED, RED_TEXT, TEAL, TEAL_TEXT, btnPrimary, downloadPdf, fmtDT, lbl, sinp, unwrap } from "./patientFile.shared"
import { Activity, ChevronDown, ChevronUp, Loader, Mic, MicOff, Pill, Plus, Stethoscope } from "lucide-react"

// ── CONSULTATION TAB (speech + Claude SOAP) ───────────────────────────────────

export default function ConsultationTab({ patient, consultations, practitioners, qc, addToBill, onSwitchTab }:
  {patient:Patient; consultations:Consultation[]; practitioners:Practitioner[]; qc:any; addToBill:(l:any)=>void; onSwitchTab:(t:any)=>void}) {
  const [expanded, setExpanded] = useState<string|null>(null)
  const { notify, dialogs } = useDialogs()
  const [showNew, setShowNew]   = useState(false)
  const [showRx, setShowRx]     = useState<string|null>(null)
  const [editingId, setEditingId] = useState<string|null>(null)
  const [editForm, setEditForm] = useState<any>({})
  const [apiError, setApiError] = useState("")

  const saveEdit = useMutation({
    mutationFn: (body:any) => apiClient.patch(`/api/v1/clinic/consultations/${editingId}`, body),
    onSuccess: () => { qc.invalidateQueries({queryKey:["pf-consultations",patient.id]}); setEditingId(null) },
    onError: (e:any) => setApiError(e.response?.data?.message??"Failed to save"),
  })

  const openEdit = (c: Consultation) => {
    setEditForm({
      chiefComplaint: c.chiefComplaint||"",
      weightKg:       c.weightKg?String(c.weightKg):"",
      heightCm:       c.heightCm?String(c.heightCm):"",
      bloodPressure:  c.bloodPressure||"",
      pulseBpm:       c.pulseBpm?String(c.pulseBpm):"",
      temperatureC:   c.temperatureC?String(c.temperatureC):"",
      oxygenSatPct:   c.oxygenSatPct?String(c.oxygenSatPct):"",
      history:        c.history||"",
      examination:    c.examination||"",
      diagnosis:      c.diagnosis||"",
      icd10Codes:     c.icd10Codes?.join(", ")||"",
      treatmentPlan:  c.treatmentPlan||"",
      followUpDays:   c.followUpDays?String(c.followUpDays):"",
    })
    setEditingId(c.id)
    setApiError("")
  }
  const [isRecording, setIsRecording] = useState(false)
  const [transcript, setTranscript]   = useState("")
  const [extracting]   = useState(false)
  const recognitionRef = useRef<any>(null)

  const EMPTY = { practitionerId:"",chiefComplaint:"",weightKg:"",heightCm:"",bloodPressure:"",pulseBpm:"",temperatureC:"",oxygenSatPct:"",history:"",examination:"",diagnosis:"",icd10Codes:"",treatmentPlan:"",followUpDays:"" }
  const [form, setForm] = useState({...EMPTY})
  const f = (k:keyof typeof EMPTY, v:string) => setForm(p=>({...p,[k]:v}))


  const { data: prescriptions=[] } = useQuery({
    queryKey:["pf-rx",showRx],
    queryFn: async ()=>showRx?unwrap(await apiClient.get(`/api/v1/clinic/consultations/${showRx}/prescriptions`)):[],
    enabled:!!showRx,
  })

  const createConsult = useMutation({
    mutationFn: (body:any)=>apiClient.post(`/api/v1/clinic/patients/${patient.id}/consultations`,body),
    onSuccess: ()=>{
      qc.invalidateQueries({queryKey:["pf-consultations",patient.id]})
      setShowNew(false); setForm({...EMPTY}); setTranscript(""); setApiError("")
      addToBill({type:"CONSULTATION",description:`Consultation — ${form.chiefComplaint||"General"}`,tariffCode:"0191",quantity:1,unitPrice:520,gross:520})
      onSwitchTab("running-bill")
    },
    onError:(e:any)=>setApiError(e.response?.data?.message??"Failed"),
  })
  const [rxError, setRxError] = useState("")
  const addRx = useMutation({
    mutationFn: ({cid,body}:{cid:string;body:any})=>apiClient.post(`/api/v1/clinic/consultations/${cid}/prescriptions`,body),
    onSuccess: ()=>{
      qc.invalidateQueries({queryKey:["pf-rx",showRx]})
      setRxError("")
    },
    onError:(e:any)=>setRxError(e.response?.data?.message??"Could not add the prescription"),
  })

  const startRecording = () => {
    const SR=(window as any).SpeechRecognition||(window as any).webkitSpeechRecognition
    if (!SR) { void notify({ title: "Voice notes unavailable", body: "Speech recognition needs Chrome or Edge." }); return }
    const r=new SR(); r.continuous=true; r.interimResults=true; r.lang="en-ZA"
    r.onresult=(e:any)=>{
      let final=""
      for (let i=0;i<e.results.length;i++) if (e.results[i].isFinal) final+=e.results[i][0].transcript+" "
      setTranscript(t=>t+final)
    }
    r.onerror=()=>setIsRecording(false); r.onend=()=>setIsRecording(false)
    recognitionRef.current=r; r.start(); setIsRecording(true)
  }
  const stopRecording=()=>{ recognitionRef.current?.stop(); setIsRecording(false) }

  const extractSOAP=async()=>{
    // DISABLED (audit F-03): this used to POST the transcript (patient health information)
    // straight from the browser to a third-party API with no auth, consent or audit.
    // It returns once a backend scribe endpoint exists (consent gate, then draft, then
    // clinician review, then sign). Nothing is sent anywhere from here.
    await notify({ title: "AI notes are switched off", body: "AI note extraction stays off until the secure, consent-gated scribe is available. Your transcript was not sent anywhere." })
  }

  const sorted=[...consultations].sort((a,b)=>b.consultedAt.localeCompare(a.consultedAt))

  return (
    <div>
      {dialogs}
      <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:20 }}>
        <div style={{ fontSize:15, fontWeight:700, color:"var(--hf-text)" }}>{consultations.length} consultation{consultations.length!==1?"s":""}</div>
        <button onClick={()=>{setShowNew(true);setForm({...EMPTY});setTranscript("");setApiError("")}} style={btnPrimary}><Plus size={14}/> Record consultation</button>
      </div>

      {sorted.length===0 ? <Empty icon={Stethoscope} msg="No consultations recorded"/> : (
        <div style={{ display:"flex", flexDirection:"column", gap:10 }}>
          {sorted.map((c:any)=>{
            const isOpen=expanded===c.id
            return (
              <div key={c.id} style={{ border:`1px solid ${BORDER}`, borderRadius:12, overflow:"hidden" }}>
                <div onClick={()=>setExpanded(isOpen?null:c.id)}
                  style={{ display:"flex", justifyContent:"space-between", alignItems:"center", padding:"14px 18px", cursor:"pointer", background:isOpen?LIGHT:"var(--hf-surface)" }}>
                  <div style={{ display:"flex", alignItems:"center", gap:12 }}>
                    <div style={{ width:36, height:36, borderRadius:8, background:"var(--hf-success-soft)", border:"1px solid var(--hf-success-border)", display:"flex", alignItems:"center", justifyContent:"center" }}>
                      <Activity size={16} style={{ color: TEAL_TEXT }}/>
                    </div>
                    <div>
                      <div style={{ fontWeight:700, fontSize:14, color:"var(--hf-text)", marginBottom:2 }}>{c.chiefComplaint}</div>
                      <div style={{ fontSize:12, color:GRAY }}>{fmtDT(c.consultedAt)}{c.practitionerName&&` · Dr. ${c.practitionerName}`}{c.diagnosis&&` · ${c.diagnosis}`}</div>
                    </div>
                  </div>
                  <div style={{ display:"flex", alignItems:"center", gap:8 }}>
                    <button onClick={e=>{e.stopPropagation();openEdit(c)}} style={{ display:"flex", alignItems:"center", gap:4, padding:"4px 10px", background:"var(--hf-info-soft)", color:"var(--hf-info-text)", border:"1px solid var(--hf-info-border)", borderRadius:6, fontSize:12, cursor:"pointer", fontWeight:600 }}>✏ Edit</button>
                    <button onClick={e=>{e.stopPropagation();setShowRx(c.id)}} style={{ display:"flex", alignItems:"center", gap:4, padding:"4px 10px", background:"var(--hf-success-soft)", color:GREEN_TEXT, border:"1px solid var(--hf-success-border)", borderRadius:6, fontSize:12, cursor:"pointer", fontWeight:600 }}><Pill size={11}/> Rx</button>
                    <button onClick={e=>{e.stopPropagation();downloadPdf(`/api/v1/clinic/consultations/${c.id}/prescription-pdf`,`rx-${c.id}.pdf`)}} style={{ display:"flex", alignItems:"center", gap:4, padding:"4px 10px", background:"var(--hf-info-soft)", color:"var(--hf-info-text)", border:"1px solid var(--hf-info-border)", borderRadius:6, fontSize:12, cursor:"pointer", fontWeight:600 }}>Rx PDF</button>
                    {c.followUpDays&&<span style={{ fontSize:11, color:AMBER, background:"var(--hf-warning-soft)", padding:"2px 8px", borderRadius:20, border:"1px solid var(--hf-warning-border)" }}>F/U {c.followUpDays}d</span>}
                    {isOpen?<ChevronUp size={16} style={{ color: GRAY }}/>:<ChevronDown size={16} style={{ color: GRAY }}/>}
                  </div>
                </div>
                {isOpen&&(
                  <div style={{ borderTop:`1px solid ${BORDER}`, padding:"18px 20px", background:"var(--hf-surface-muted)" }}>
                    {(c.weightKg||c.bloodPressure||c.pulseBpm||c.temperatureC)&&(
                      <div style={{ marginBottom:16 }}>
                        <div style={{ fontSize:10, fontWeight:700, color:GRAY, letterSpacing:"0.06em", marginBottom:8 }}>VITALS</div>
                        <div style={{ display:"flex", gap:10, flexWrap:"wrap" }}>
                          {[{l:"Weight",v:c.weightKg?`${c.weightKg} kg`:null},{l:"Height",v:c.heightCm?`${c.heightCm} cm`:null},{l:"BP",v:c.bloodPressure},{l:"Pulse",v:c.pulseBpm?`${c.pulseBpm} bpm`:null},{l:"Temp",v:c.temperatureC?`${c.temperatureC}°C`:null},{l:"SpO₂",v:c.oxygenSatPct?`${c.oxygenSatPct}%`:null}].filter(x=>x.v).map(({l,v})=>(
                            <div key={l} style={{ background:"var(--hf-surface)", border:`1px solid ${BORDER}`, borderRadius:8, padding:"8px 14px", textAlign:"center" }}>
                              <div style={{ fontSize:10, color:GRAY, marginBottom:2 }}>{l}</div>
                              <div style={{ fontSize:14, fontWeight:700, color:"var(--hf-text)" }}>{v}</div>
                            </div>
                          ))}
                          {c.weightKg&&c.heightCm&&<div style={{ background:"var(--hf-surface)", border:`1px solid ${BORDER}`, borderRadius:8, padding:"8px 14px", textAlign:"center" }}><div style={{ fontSize:10, color:GRAY, marginBottom:2 }}>BMI</div><div style={{ fontSize:14, fontWeight:700, color:"var(--hf-text)" }}>{(c.weightKg/Math.pow(c.heightCm/100,2)).toFixed(1)}</div></div>}
                        </div>
                      </div>
                    )}
                    <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:12 }}>
                      {[{l:"History",v:c.history},{l:"Examination",v:c.examination},{l:"Diagnosis",v:c.diagnosis},{l:"Treatment plan",v:c.treatmentPlan}].filter(x=>x.v).map(({l,v})=>(
                        <div key={l}><div style={{ fontSize:10, fontWeight:700, color:GRAY, textTransform:"uppercase", letterSpacing:"0.06em", marginBottom:3 }}>{l}</div><div style={{ fontSize:13, color:"var(--hf-text)", lineHeight:1.5 }}>{v}</div></div>
                      ))}
                    </div>
                    {c.icd10Codes?.length>0&&(<div style={{ marginTop:12 }}><div style={{ fontSize:10, fontWeight:700, color:GRAY, textTransform:"uppercase", letterSpacing:"0.06em", marginBottom:5 }}>ICD-10 codes</div><div style={{ display:"flex", gap:6, flexWrap:"wrap" }}>{c.icd10Codes.map((code:string)=>(<span key={code} style={{ background:"var(--hf-info-soft)", color:"var(--hf-info-text)", padding:"2px 8px", borderRadius:4, fontSize:12, fontWeight:600, border:"1px solid var(--hf-info-border)" }}>{code}</span>))}</div></div>)}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {/* Prescriptions modal */}
      {showRx&&(
        <Modal title="Prescriptions" onClose={()=>setShowRx(null)}>
          {(prescriptions as Prescription[]).length===0?<p style={{color:GRAY,fontSize:13}}>No prescriptions for this consultation.</p>:(
            <div style={{ display:"flex", flexDirection:"column", gap:10, marginBottom:20 }}>
              {(prescriptions as Prescription[]).map(rx=>(
                <div key={rx.id} style={{ border:`1px solid ${BORDER}`, borderRadius:10, padding:"12px 16px", background:rx.dispensed?"var(--hf-success-soft)":"var(--hf-surface)" }}>
                  <div style={{ fontWeight:700, fontSize:14, color:"var(--hf-text)", marginBottom:3 }}>{rx.medicationName}</div>
                  <div style={{ fontSize:12, color:GRAY }}>{[rx.dosage,rx.frequency,rx.duration].filter(Boolean).join(" · ")}{rx.quantity?` · Qty: ${rx.quantity}`:""}{rx.repeats>0?` · Repeats: ${rx.repeats}`:""}</div>
                  {rx.instructions&&<div style={{ fontSize:12, color:"var(--hf-text-tertiary)", marginTop:4, fontStyle:"italic" }}>{rx.instructions}</div>}
                  {rx.dispensed&&<span style={{ marginTop:6, display:"inline-block", background:"var(--hf-success-soft-strong)", color:GREEN_TEXT, padding:"1px 8px", borderRadius:20, fontSize:11, fontWeight:700 }}>DISPENSED</span>}
                </div>
              ))}
            </div>
          )}
          <div style={{ borderTop:`1px solid ${BORDER}`, paddingTop:16 }}>
            <PrescriptionForm consultationId={showRx} busy={addRx.isPending} error={rxError}
              onSubmit={body=>{ setRxError(""); return addRx.mutateAsync({cid:showRx!,body}) }} />
          </div>
        </Modal>
      )}


      {/* ── Edit consultation modal ──────────────────────────────────────── */}
      {editingId && (
        <Modal title="Edit consultation" onClose={()=>setEditingId(null)} wide>
          <div style={{marginBottom:14,padding:"8px 12px",background:"var(--hf-info-soft)",border:"1px solid var(--hf-info-border)",borderRadius:8,fontSize:12,color:"var(--hf-info-text)"}}>
            ℹ Editing saves immediately. Consultation date and practitioner cannot be changed.
          </div>
          <FSect title="Vitals">
            <div style={{display:"grid",gridTemplateColumns:"repeat(3,1fr)",gap:12}}>
              {[
                {k:"weightKg",     l:"Weight (kg)",  p:"82"},
                {k:"heightCm",     l:"Height (cm)",  p:"175"},
                {k:"bloodPressure",l:"BP",            p:"120/80"},
                {k:"pulseBpm",     l:"Pulse (bpm)",  p:"72"},
                {k:"temperatureC", l:"Temp (°C)",    p:"36.6"},
                {k:"oxygenSatPct", l:"SpO₂ (%)",    p:"98"},
              ].map(f=>(
                <div key={f.k}>
                  <label style={lbl}>{f.l}</label>
                  <input value={editForm[f.k]||""} onChange={e=>setEditForm((x:any)=>({...x,[f.k]:e.target.value}))}
                    placeholder={f.p} style={sinp}/>
                </div>
              ))}
            </div>
          </FSect>
          <FSect title="SOAP notes">
            <div style={{display:"flex",flexDirection:"column",gap:12}}>
              {[
                {k:"chiefComplaint",l:"Chief complaint *",rows:1,p:"Main reason for visit"},
                {k:"history",       l:"History (S)",      rows:2,p:"Subjective"},
                {k:"examination",   l:"Examination (O)",  rows:2,p:"Objective findings"},
                {k:"diagnosis",     l:"Diagnosis (A)",    rows:2,p:"Assessment"},
                {k:"icd10Codes",    l:"ICD-10 codes",     rows:1,p:"J06.9, Z00.0"},
                {k:"treatmentPlan", l:"Treatment plan (P)",rows:2,p:"Management plan"},
                {k:"followUpDays",  l:"Follow-up (days)", rows:1,p:"7"},
              ].map((f:any)=>(
                <div key={f.k}>
                  <label style={lbl}>{f.l}</label>
                  {f.rows===1
                    ? <input value={editForm[f.k]||""} onChange={e=>setEditForm((x:any)=>({...x,[f.k]:e.target.value}))} placeholder={f.p} style={sinp}/>
                    : <textarea value={editForm[f.k]||""} onChange={e=>setEditForm((x:any)=>({...x,[f.k]:e.target.value}))} rows={f.rows} placeholder={f.p} style={{...sinp,resize:"vertical" as const}}/>
                  }
                </div>
              ))}
            </div>
          </FSect>
          {apiError && <ErrBox msg={apiError}/>}
          <ModalFooter
            onCancel={()=>setEditingId(null)}
            onConfirm={()=>saveEdit.mutate({
              chiefComplaint: editForm.chiefComplaint||null,
              weightKg:       parseFloat(editForm.weightKg)||null,
              heightCm:       parseFloat(editForm.heightCm)||null,
              bloodPressure:  editForm.bloodPressure||null,
              pulseBpm:       parseInt(editForm.pulseBpm)||null,
              temperatureC:   parseFloat(editForm.temperatureC)||null,
              oxygenSatPct:   parseFloat(editForm.oxygenSatPct)||null,
              history:        editForm.history||null,
              examination:    editForm.examination||null,
              diagnosis:      editForm.diagnosis||null,
              icd10Codes:     editForm.icd10Codes?editForm.icd10Codes.split(",").map((s:string)=>s.trim()).filter(Boolean):[],
              treatmentPlan:  editForm.treatmentPlan||null,
              followUpDays:   parseInt(editForm.followUpDays)||null,
            })}
            confirmLabel={saveEdit.isPending?"Saving…":"Save changes"}
            loading={saveEdit.isPending}/>
        </Modal>
      )}

      {/* New consultation modal */}
      {showNew&&(
        <Modal title={`Record consultation — ${patient.fullName}`} onClose={()=>setShowNew(false)} wide>
          {/* Speech panel */}
          <div style={{ marginBottom:20, padding:"16px 18px", background:"var(--hf-violet-soft)", border:"1px solid var(--hf-violet-border)", borderRadius:12 }}>
            <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:10 }}>
              <div style={{ fontSize:13, fontWeight:700, color:PURPLE_TEXT, display:"flex", alignItems:"center", gap:6 }}><Mic size={14}/> Voice-to-notes</div>
              <div style={{ display:"flex", gap:8 }}>
                {!isRecording
                  ? <button onClick={startRecording} style={{ display:"flex", alignItems:"center", gap:6, padding:"6px 12px", background:PURPLE, color:"var(--hf-text-on-solid)", border:"none", borderRadius:7, fontSize:12, fontWeight:600, cursor:"pointer" }}><Mic size={12}/> Start recording</button>
                  : <button onClick={stopRecording} style={{ display:"flex", alignItems:"center", gap:6, padding:"6px 12px", background:RED, color:"var(--hf-text-on-solid)", border:"none", borderRadius:7, fontSize:12, fontWeight:600, cursor:"pointer" }}><MicOff size={12}/> Stop recording</button>
                }
                {transcript&&<button onClick={extractSOAP} disabled={extracting} style={{ display:"flex", alignItems:"center", gap:6, padding:"6px 12px", background:extracting?"var(--hf-surface-strong)":TEAL, color:extracting?GRAY:"var(--hf-text-on-solid)", border:"none", borderRadius:7, fontSize:12, fontWeight:600, cursor:extracting?"wait":"pointer" }}>{extracting?<><Loader size={12}/> Extracting...</>:<>✨ Extract SOAP</>}</button>}
                {transcript&&<button onClick={()=>setTranscript("")} style={{ padding:"6px 10px", background:"none", border:`1px solid ${BORDER}`, borderRadius:7, fontSize:12, cursor:"pointer", color:GRAY }}>Clear</button>}
              </div>
            </div>
            {isRecording&&<div style={{ display:"flex", alignItems:"center", gap:6, fontSize:12, color:RED_TEXT, marginBottom:6 }}><span style={{ width:7, height:7, borderRadius:"50%", background:RED }}/> Recording — speak clearly</div>}
            <textarea value={transcript} onChange={e=>setTranscript(e.target.value)} rows={3}
              style={{ ...sinp, fontSize:12, color:"var(--hf-text-tertiary)", background:"rgba(255,255,255,0.7)", resize:"vertical" as const }}
              placeholder="Transcript appears here after recording. Then click Extract SOAP to fill the form below using Claude AI."/>
          </div>

          <FSect title="Practitioner & chief complaint">
            <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:12 }}>
              <div><label style={lbl}>Practitioner</label><select value={form.practitionerId} onChange={e=>f("practitionerId",e.target.value)} style={sinp}><option value="">Select...</option>{practitioners.map(p=><option key={p.id} value={p.id}>{p.fullName}</option>)}</select></div>
              <div><label style={lbl}>Chief complaint *</label><input value={form.chiefComplaint} onChange={e=>f("chiefComplaint",e.target.value)} placeholder="Main reason for visit" style={sinp} autoFocus/></div>
            </div>
          </FSect>

          <FSect title="Vitals">
            <div style={{ display:"grid", gridTemplateColumns:"repeat(3,1fr)", gap:12 }}>
              <div><label style={lbl}>Weight (kg)</label><input type="number" step="0.1" value={form.weightKg} onChange={e=>f("weightKg",e.target.value)} placeholder="70.5" style={sinp}/></div>
              <div><label style={lbl}>Height (cm)</label><input type="number" value={form.heightCm} onChange={e=>f("heightCm",e.target.value)} placeholder="175" style={sinp}/></div>
              <div><label style={lbl}>Blood pressure</label><input value={form.bloodPressure} onChange={e=>f("bloodPressure",e.target.value)} placeholder="120/80" style={sinp}/></div>
              <div><label style={lbl}>Pulse (bpm)</label><input type="number" value={form.pulseBpm} onChange={e=>f("pulseBpm",e.target.value)} placeholder="72" style={sinp}/></div>
              <div><label style={lbl}>Temp (°C)</label><input type="number" step="0.1" value={form.temperatureC} onChange={e=>f("temperatureC",e.target.value)} placeholder="36.6" style={sinp}/></div>
              <div><label style={lbl}>SpO₂ (%)</label><input type="number" value={form.oxygenSatPct} onChange={e=>f("oxygenSatPct",e.target.value)} placeholder="98" style={sinp}/></div>
            </div>
            {form.weightKg&&form.heightCm&&<div style={{ marginTop:8, fontSize:12, color:GRAY, background:LIGHT, padding:"5px 12px", borderRadius:6, display:"inline-block" }}>BMI: {(parseFloat(form.weightKg)/Math.pow(parseFloat(form.heightCm)/100,2)).toFixed(1)}</div>}
          </FSect>

          <FSect title="SOAP notes">
            <div style={{ display:"flex", flexDirection:"column", gap:12 }}>
              <div><label style={lbl}>History (S)</label><textarea rows={2} value={form.history} onChange={e=>f("history",e.target.value)} placeholder="Subjective — patient history" style={{ ...sinp, resize:"vertical" as const }}/></div>
              <div><label style={lbl}>Examination (O)</label><textarea rows={2} value={form.examination} onChange={e=>f("examination",e.target.value)} placeholder="Objective — physical findings" style={{ ...sinp, resize:"vertical" as const }}/></div>
              <div><label style={lbl}>Diagnosis (A)</label><textarea rows={2} value={form.diagnosis} onChange={e=>f("diagnosis",e.target.value)} placeholder="Assessment — working diagnosis" style={{ ...sinp, resize:"vertical" as const }}/></div>
              <div><label style={lbl}>ICD-10 codes <span style={{ fontWeight:400, color:GRAY }}>(comma separated)</span></label><input value={form.icd10Codes} onChange={e=>f("icd10Codes",e.target.value)} placeholder="J06.9, Z00.0" style={sinp}/></div>
              <div><label style={lbl}>Treatment plan (P)</label><textarea rows={2} value={form.treatmentPlan} onChange={e=>f("treatmentPlan",e.target.value)} placeholder="Plan — management and treatment" style={{ ...sinp, resize:"vertical" as const }}/></div>
              <div><label style={lbl}>Follow-up (days)</label><input type="number" value={form.followUpDays} onChange={e=>f("followUpDays",e.target.value)} placeholder="7" style={{ ...sinp, width:120 }}/></div>
            </div>
          </FSect>

          {apiError&&<ErrBox msg={apiError}/>}
          <ModalFooter onCancel={()=>setShowNew(false)}
            onConfirm={()=>{ if(!form.chiefComplaint.trim()) return; createConsult.mutate({ practitionerId:form.practitionerId||null, chiefComplaint:form.chiefComplaint, weightKg:parseFloat(form.weightKg)||null, heightCm:parseFloat(form.heightCm)||null, bloodPressure:form.bloodPressure||null, pulseBpm:parseInt(form.pulseBpm)||null, temperatureC:parseFloat(form.temperatureC)||null, oxygenSatPct:parseFloat(form.oxygenSatPct)||null, history:form.history||null, examination:form.examination||null, diagnosis:form.diagnosis||null, icd10Codes:form.icd10Codes?form.icd10Codes.split(",").map((s:string)=>s.trim()).filter(Boolean):[], treatmentPlan:form.treatmentPlan||null, followUpDays:parseInt(form.followUpDays)||null }) }}
            confirmLabel="Save & go to running bill" loading={createConsult.isPending}/>
        </Modal>
      )}
    </div>
  )
}
