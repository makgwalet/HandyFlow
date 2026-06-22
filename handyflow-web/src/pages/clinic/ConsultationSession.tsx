// src/pages/clinic/ConsultationSession.tsx
// Live consultation session — timer, SOAP notes, live bill, prescriptions
// Opened when a doctor starts a consultation from an appointment

import { useState, useEffect, useRef, useCallback } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  Mic, MicOff, Plus, X, Clock, Stethoscope, CreditCard, Pill,
  Syringe, FlaskConical, Scissors, ChevronDown, CheckCircle,
  AlertCircle, Loader, Sparkles, Search, Zap,
} from "lucide-react"

// ── Types ─────────────────────────────────────────────────────────────────────

interface Patient { id: string; fullName: string; bloodType?: string; allergies?: string[] }
interface Appointment {
  id: string; patientId: string; patientName: string
  practitionerId: string; practitionerName: string
  appointmentType: string; reason: string; scheduledAt: string
}
interface BillLine {
  id: string; type: "CONSULTATION"|"PROCEDURE"|"MEDICINE"|"CONSUMABLE"
  description: string; tariffCode?: string; nappiCode?: string
  quantity: number; unitPrice: number; gross: number
}
interface RxDraft {
  id: string; medicationName: string; nappiCode?: string
  dosage: string; frequency: string; duration: string
  quantity: number; instructions: string; fromBill: boolean
}

// ── Tokens ────────────────────────────────────────────────────────────────────

const NAVY="#1B3A6B"; const TEAL="#0D9488"; const RED="#DC2626"
const GREEN="#166534"; const AMBER="#D97706"; const PURPLE="#7C3AED"
const GRAY="#64748B"; const BORDER="#E2E8F0"; const LIGHT="#F8FAFC"

const QUICK_PROCEDURES = [
  { label:"Injection IM",    tariff:"0115", price: 85,  icon: Syringe,      type:"PROCEDURE" },
  { label:"Injection IV",    tariff:"0116", price:120,  icon: Syringe,      type:"PROCEDURE" },
  { label:"Blood draw",      tariff:"0301", price: 95,  icon: FlaskConical, type:"PROCEDURE" },
  { label:"Wound suture",    tariff:"0007", price:180,  icon: Scissors,     type:"PROCEDURE" },
  { label:"ECG 12-lead",     tariff:"4116", price:350,  icon: Zap,          type:"PROCEDURE" },
]

const fmtR = (v: number) => `R ${(v||0).toLocaleString("en-ZA",{minimumFractionDigits:2})}`

function padZero(n: number) { return String(n).padStart(2,"0") }
function fmtTimer(seconds: number) {
  const h = Math.floor(seconds/3600)
  const m = Math.floor((seconds%3600)/60)
  const s = seconds%60
  return h > 0 ? `${padZero(h)}:${padZero(m)}:${padZero(s)}` : `${padZero(m)}:${padZero(s)}`
}

const unwrap = (r:any) => { const p=r.data?.data??r.data; return Array.isArray(p)?p:(p?.content??[]) }

// ── Main Component ─────────────────────────────────────────────────────────────

interface Props {
  patient: Patient
  appointment: Appointment
  onComplete: (consultationId: string) => void
  onMinimise: () => void
  onCancel: () => void
}

export default function ConsultationSession({ patient, appointment, onComplete, onMinimise, onCancel }: Props) {
  const qc = useQueryClient()

  // ── Timer ──────────────────────────────────────────────────────────────────
  const startTimeRef = useRef(Date.now())
  const [elapsed, setElapsed] = useState(0)
  useEffect(() => {
    const id = setInterval(() => setElapsed(Math.floor((Date.now()-startTimeRef.current)/1000)), 1000)
    return () => clearInterval(id)
  }, [])
  const durationMinutes = Math.max(1, Math.round(elapsed/60))

  // ── SOAP state ────────────────────────────────────────────────────────────
  const [soap, setSoap] = useState({
    chiefComplaint: appointment.reason || "",
    history:"", examination:"", diagnosis:"", icd10Codes:"", treatmentPlan:"", followUpDays:"",
    weightKg:"", heightCm:"", bloodPressure:"", pulseBpm:"", temperatureC:"", oxygenSatPct:""
  })
  const sf = (k: keyof typeof soap, v: string) => setSoap(p=>({...p,[k]:v}))

  // ── Voice recording ───────────────────────────────────────────────────────
  const [isRecording, setIsRecording] = useState(false)
  const [transcript, setTranscript]   = useState("")
  const [extracting, setExtracting]   = useState(false)
  const recRef = useRef<any>(null)

  const startRec = () => {
    const SR=(window as any).SpeechRecognition||(window as any).webkitSpeechRecognition
    if (!SR) { alert("Speech recognition requires Chrome or Edge"); return }
    const r=new SR(); r.continuous=true; r.interimResults=true; r.lang="en-ZA"
    r.onresult=(e:any)=>{
      let final=""
      for (let i=0;i<e.results.length;i++) if(e.results[i].isFinal) final+=e.results[i][0].transcript+" "
      setTranscript(t=>t+final)
    }
    r.onerror=()=>setIsRecording(false); r.onend=()=>setIsRecording(false)
    recRef.current=r; r.start(); setIsRecording(true)
  }
  const stopRec = () => { recRef.current?.stop(); setIsRecording(false) }

  const extractSOAP = async () => {
    if (!transcript.trim()) return
    setExtracting(true)
    try {
      const res = await fetch("https://api.anthropic.com/v1/messages",{
        method:"POST", headers:{"Content-Type":"application/json"},
        body:JSON.stringify({model:"claude-sonnet-4-6",max_tokens:1200,messages:[{role:"user",
          content:`You are a medical scribe. Extract a SOAP note and any procedures/medications mentioned.
Return ONLY valid JSON:
{
  "chiefComplaint": "string",
  "history": "string",
  "examination": "string", 
  "diagnosis": "string",
  "icd10Codes": "comma-separated string",
  "treatmentPlan": "string",
  "followUpDays": number or null,
  "procedures": [{"description": "string", "tariffCode": "string or null"}],
  "medications": [{"name": "string", "dosage": "string", "frequency": "string", "duration": "string"}]
}

Transcript: ${transcript}`}]})
      })
      const data = await res.json()
      const text = data.content?.[0]?.text??""
      const parsed = JSON.parse(text.replace(/```json|```/g,"").trim())

      setSoap(p=>({...p,
        chiefComplaint: parsed.chiefComplaint||p.chiefComplaint,
        history:        parsed.history||p.history,
        examination:    parsed.examination||p.examination,
        diagnosis:      parsed.diagnosis||p.diagnosis,
        icd10Codes:     parsed.icd10Codes||p.icd10Codes,
        treatmentPlan:  parsed.treatmentPlan||p.treatmentPlan,
        followUpDays:   parsed.followUpDays!=null?String(parsed.followUpDays):p.followUpDays,
      }))

      // Auto-add extracted procedures to bill
      if (parsed.procedures?.length) {
        parsed.procedures.forEach((proc:any) => {
          addBillLine({
            type:"PROCEDURE", description:proc.description,
            tariffCode:proc.tariffCode||undefined,
            quantity:1, unitPrice:0, gross:0
          })
        })
      }
      // Auto-add extracted medications to bill + Rx
      if (parsed.medications?.length) {
        parsed.medications.forEach((med:any) => {
          addMedicationToBillAndRx({
            medicationName: med.name,
            dosage: med.dosage||"",
            frequency: med.frequency||"",
            duration: med.duration||"",
            unitPrice: 0
          })
        })
      }
    } catch(e){ console.error("SOAP extraction failed",e) }
    setExtracting(false)
  }

  // ── Bill lines ────────────────────────────────────────────────────────────
  const [billLines, setBillLines] = useState<BillLine[]>([
    // Auto-add consultation tariff on session start
    { id:"consult-0191", type:"CONSULTATION", description:"Consultation — intermediate",
      tariffCode:"0191", quantity:1, unitPrice:520, gross:520 }
  ])
  const billTotal = billLines.reduce((s,l)=>s+l.gross,0)

  const addBillLine = useCallback((line: Omit<BillLine,"id">) => {
    setBillLines(b=>[...b,{...line, id:crypto.randomUUID()}])
  },[])

  const removeBillLine = (id: string) => setBillLines(b=>b.filter(l=>l.id!==id))

  const addQuickProcedure = (proc: typeof QUICK_PROCEDURES[0]) => {
    addBillLine({ type:"PROCEDURE", description:proc.label,
      tariffCode:proc.tariff, quantity:1, unitPrice:proc.price, gross:proc.price })
  }

  // ── Rx drafts ─────────────────────────────────────────────────────────────
  const [rxDrafts, setRxDrafts] = useState<RxDraft[]>([])
  const updateRx = (id: string, k: keyof RxDraft, v: any) =>
    setRxDrafts(d=>d.map(x=>x.id===id?{...x,[k]:v}:x))
  const removeRx = (id: string) => setRxDrafts(d=>d.filter(x=>x.id!==id))

  // ── Med search + dual add ─────────────────────────────────────────────────
  const [medSearch, setMedSearch]   = useState("")
  const [showMedSearch, setShowMedSearch] = useState(false)
  const [medResults, setMedResults] = useState<any[]>([])
  const [medLoading, setMedLoading] = useState(false)

  useEffect(() => {
    if (!medSearch.trim() || medSearch.length < 2) { setMedResults([]); return }
    const t = setTimeout(async () => {
      setMedLoading(true)
      try {
        const r = await apiClient.get(`/api/v1/clinic/medications?search=${encodeURIComponent(medSearch)}`)
        setMedResults(unwrap(r).slice(0,8))
      } catch { setMedResults([]) }
      setMedLoading(false)
    }, 300)
    return () => clearTimeout(t)
  }, [medSearch])

  const addMedicationToBillAndRx = (med: {
    medicationName: string; nappiCode?: string
    dosage?: string; frequency?: string; duration?: string; unitPrice: number
  }) => {
    // Add to bill
    addBillLine({
      type:"MEDICINE", description:med.medicationName,
      nappiCode:med.nappiCode,
      quantity:1, unitPrice:med.unitPrice, gross:med.unitPrice
    })
    // Add to Rx drafts
    setRxDrafts(d=>[...d,{
      id:crypto.randomUUID(),
      medicationName:med.medicationName, nappiCode:med.nappiCode,
      dosage:med.dosage||"", frequency:med.frequency||"", duration:med.duration||"",
      quantity:30, instructions:"", fromBill:true
    }])
    setMedSearch(""); setMedResults([]); setShowMedSearch(false)
  }

  // ── Custom bill line ──────────────────────────────────────────────────────
  const [showCustom, setShowCustom] = useState(false)
  const [customLine, setCustomLine] = useState({type:"CONSUMABLE",description:"",quantity:"1",unitPrice:""})

  // ── Complete consultation ─────────────────────────────────────────────────
  const [showComplete, setShowComplete] = useState(false)
  const [completing, setCompleting] = useState(false)
  const [completeError, setCompleteError] = useState("")

  const complete = useMutation({
    mutationFn: async () => {
      // 1. Save consultation
      const consultRes = await apiClient.post(
        `/api/v1/clinic/patients/${patient.id}/consultations`,
        {
          appointmentId:   appointment.id,
          practitionerId:  appointment.practitionerId||null,
          chiefComplaint:  soap.chiefComplaint||"Consultation",
          weightKg:        parseFloat(soap.weightKg)||null,
          heightCm:        parseFloat(soap.heightCm)||null,
          bloodPressure:   soap.bloodPressure||null,
          pulseBpm:        parseInt(soap.pulseBpm)||null,
          temperatureC:    parseFloat(soap.temperatureC)||null,
          oxygenSatPct:    parseFloat(soap.oxygenSatPct)||null,
          history:         soap.history||null,
          examination:     soap.examination||null,
          diagnosis:       soap.diagnosis||null,
          icd10Codes:      soap.icd10Codes?soap.icd10Codes.split(",").map((s:string)=>s.trim()).filter(Boolean):[],
          treatmentPlan:   soap.treatmentPlan||null,
          followUpDays:    parseInt(soap.followUpDays)||null,
          durationMinutes, // from timer
        }
      )
      const consult = consultRes.data?.data ?? consultRes.data

      // 2. Save prescriptions
      for (const rx of rxDrafts) {
        if (!rx.medicationName.trim()) continue
        await apiClient.post(`/api/v1/clinic/consultations/${consult.id}/prescriptions`,{
          medicationName: rx.medicationName,
          nappiCode:      rx.nappiCode||null,
          dosage:         rx.dosage||null,
          frequency:      rx.frequency||null,
          duration:       rx.duration||null,
          quantity:       rx.quantity||30,
          repeats:        0,
          instructions:   rx.instructions||null,
        })
      }

      // 3. Complete the appointment
      await apiClient.post(`/api/v1/clinic/appointments/${appointment.id}/complete`)

      return consult.id
    },
    onSuccess: (consultationId) => {
      qc.invalidateQueries({queryKey:["pf-appointments"]})
      qc.invalidateQueries({queryKey:["pf-consultations"]})
      qc.invalidateQueries({queryKey:["clinic-appts-dashboard"]})
      qc.invalidateQueries({queryKey:["schedule-appts"]})
      qc.invalidateQueries({queryKey:["clinic-patients"]})
      onComplete(consultationId)
    },
    onError: (e:any) => setCompleteError(e.response?.data?.message??"Failed to complete consultation"),
  })

  // ── Active panel toggle (mobile-friendly) ─────────────────────────────────
  const [activePanel, setActivePanel] = useState<"soap"|"bill"|"rx">("soap")

  return (
    <div style={{ fontFamily:"'Inter',system-ui,sans-serif", height:"100%", display:"flex", flexDirection:"column" }}>

      {/* ── Session header ─────────────────────────────────────────────── */}
      <div style={{ background:`linear-gradient(135deg,${NAVY} 0%,#0D2145 100%)`,
        borderRadius:12, padding:"16px 24px", marginBottom:16,
        display:"flex", justifyContent:"space-between", alignItems:"center", flexWrap:"wrap", gap:12 }}>
        <div style={{ display:"flex", alignItems:"center", gap:16 }}>
          <div style={{ width:48, height:48, borderRadius:"50%",
            background:"rgba(255,255,255,0.15)", display:"flex", alignItems:"center",
            justifyContent:"center", fontSize:18, fontWeight:800, color:"#fff" }}>
            {patient.fullName.split(" ").map(n=>n[0]).join("").slice(0,2)}
          </div>
          <div>
            <div style={{ display:"flex", alignItems:"center", gap:8, marginBottom:2 }}>
              <span style={{ fontSize:18, fontWeight:800, color:"#fff" }}>{patient.fullName}</span>
              <span style={{ background:"rgba(13,148,136,0.3)", color:"#5EEAD4",
                padding:"2px 8px", borderRadius:20, fontSize:11, fontWeight:700 }}>
                {appointment.appointmentType?.replace("_"," ")}
              </span>
            </div>
            <div style={{ fontSize:13, color:"rgba(255,255,255,0.6)" }}>
              {appointment.practitionerName && `Dr. ${appointment.practitionerName}`}
              {appointment.reason && ` · ${appointment.reason}`}
            </div>
            {patient.allergies && patient.allergies.length > 0 && (
              <div style={{ display:"flex", alignItems:"center", gap:4, marginTop:4 }}>
                <AlertCircle size={11} color="#FCA5A5"/>
                <span style={{ fontSize:11, color:"#FCA5A5", fontWeight:600 }}>
                  ⚠ {patient.allergies.join(", ")}
                </span>
              </div>
            )}
          </div>
        </div>

        <div style={{ display:"flex", alignItems:"center", gap:16 }}>
          {/* Live timer */}
          <div style={{ textAlign:"center" }}>
            <div style={{ display:"flex", alignItems:"center", gap:6,
              background:"rgba(255,255,255,0.1)", borderRadius:10, padding:"8px 16px" }}>
              <div style={{ width:8, height:8, borderRadius:"50%", background:RED,
                animation:"pulse 1.5s infinite" }}/>
              <span style={{ fontSize:22, fontWeight:800, color:"#fff", fontVariantNumeric:"tabular-nums" }}>
                {fmtTimer(elapsed)}
              </span>
            </div>
            <div style={{ fontSize:10, color:"rgba(255,255,255,0.5)", marginTop:2 }}>
              {durationMinutes} min
            </div>
          </div>

          {/* Bill total */}
          <div style={{ textAlign:"center",
            background:"rgba(255,255,255,0.1)", borderRadius:10, padding:"8px 16px" }}>
            <div style={{ fontSize:11, color:"rgba(255,255,255,0.6)" }}>Running bill</div>
            <div style={{ fontSize:18, fontWeight:800, color:"#fff" }}>{fmtR(billTotal)}</div>
          </div>

          <button onClick={() => { setShowComplete(true); setCompleteError("") }}
            style={{ display:"flex", alignItems:"center", gap:8, padding:"10px 20px",
              background:TEAL, color:"#fff", border:"none", borderRadius:10,
              fontSize:14, fontWeight:700, cursor:"pointer" }}>
            <CheckCircle size={16}/> Complete
          </button>
          <button onClick={onMinimise}
            title="Minimise — navigate tabs freely"
            style={{ background:"rgba(255,255,255,0.1)", border:"none", borderRadius:8,
              cursor:"pointer", color:"rgba(255,255,255,0.7)", padding:"8px 12px",
              fontSize:12, fontWeight:600, display:"flex", alignItems:"center", gap:4 }}>
            ↓ Minimise
          </button>
          <button onClick={onCancel}
            title="Discard session"
            style={{ background:"rgba(255,255,255,0.1)", border:"none", borderRadius:8,
              cursor:"pointer", color:"rgba(255,255,255,0.7)", padding:8, display:"flex" }}>
            <X size={18}/>
          </button>
        </div>
      </div>

      {/* ── Panel tabs (mobile) ────────────────────────────────────────── */}
      <div style={{ display:"flex", gap:4, marginBottom:12 }}>
        {[
          {id:"soap",label:"📋 Clinical notes",   count:0},
          {id:"bill",label:"💰 Running bill",     count:billLines.length},
          {id:"rx",  label:"💊 Prescriptions",   count:rxDrafts.length},
        ].map(p=>(
          <button key={p.id} onClick={()=>setActivePanel(p.id as any)}
            style={{ flex:1, padding:"8px 12px", borderRadius:8, border:"none",
              background:activePanel===p.id?NAVY:LIGHT,
              color:activePanel===p.id?"#fff":GRAY,
              fontWeight:activePanel===p.id?600:400, fontSize:13, cursor:"pointer",
              display:"flex", alignItems:"center", justifyContent:"center", gap:6 }}>
            {p.label}
            {p.count>0 && (
              <span style={{ background:TEAL, color:"#fff", borderRadius:"50%",
                width:18, height:18, fontSize:11, fontWeight:700,
                display:"inline-flex", alignItems:"center", justifyContent:"center" }}>
                {p.count}
              </span>
            )}
          </button>
        ))}
      </div>

      {/* ── Three-column session panels ────────────────────────────────── */}
      <div style={{ display:"flex", gap:14, flex:1, minHeight:0 }}>

        {/* ── LEFT: SOAP Notes ────────────────────────────────────────── */}
        <div style={{ flex:1.4, display:"flex", flexDirection:"column", gap:10,
          display: "flex" }}>

          {/* Voice panel */}
          <div style={{ padding:"12px 14px", background:"#F5F3FF", border:"1px solid #DDD6FE",
            borderRadius:10 }}>
            <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:8 }}>
              <span style={{ fontSize:12, fontWeight:700, color:PURPLE, display:"flex", alignItems:"center", gap:5 }}>
                <Mic size={13}/> Voice-to-notes
              </span>
              <div style={{ display:"flex", gap:6 }}>
                {!isRecording
                  ? <button onClick={startRec} style={voiceBtn(PURPLE)}>
                      <Mic size={11}/> Record
                    </button>
                  : <button onClick={stopRec} style={voiceBtn(RED)}>
                      <MicOff size={11}/> Stop
                    </button>
                }
                {transcript && (
                  <>
                    <button onClick={extractSOAP} disabled={extracting} style={voiceBtn(extracting?"#94A3B8":TEAL)}>
                      {extracting ? <><Loader size={11}/> Extracting</> : <><Sparkles size={11}/> Extract SOAP</>}
                    </button>
                    <button onClick={()=>setTranscript("")} style={voiceBtn(GRAY)}>Clear</button>
                  </>
                )}
              </div>
            </div>
            {isRecording && (
              <div style={{ display:"flex", alignItems:"center", gap:5, fontSize:11, color:RED, marginBottom:4 }}>
                <div style={{ width:6, height:6, borderRadius:"50%", background:RED }}/>
                Recording
              </div>
            )}
            <textarea value={transcript} onChange={e=>setTranscript(e.target.value)} rows={2}
              style={{ ...sinp, fontSize:11, resize:"vertical" as const,
                background:"rgba(255,255,255,0.7)", color:"#475569" }}
              placeholder="Speak or type transcript here, then Extract SOAP…"/>
          </div>

          {/* Vitals */}
          <div style={{ padding:"12px 14px", background:"#fff", border:`1px solid ${BORDER}`, borderRadius:10 }}>
            <div style={sectionLabel}>Vitals</div>
            <div style={{ display:"grid", gridTemplateColumns:"repeat(3,1fr)", gap:8 }}>
              {[
                {k:"weightKg",     label:"Weight (kg)", placeholder:"82"},
                {k:"heightCm",     label:"Height (cm)", placeholder:"175"},
                {k:"bloodPressure",label:"BP",          placeholder:"120/80"},
                {k:"pulseBpm",     label:"Pulse (bpm)", placeholder:"72"},
                {k:"temperatureC", label:"Temp (°C)",   placeholder:"36.6"},
                {k:"oxygenSatPct", label:"SpO₂ (%)",   placeholder:"98"},
              ].map(f=>(
                <div key={f.k}>
                  <label style={lbl}>{f.label}</label>
                  <input value={(soap as any)[f.k]}
                    onChange={e=>sf(f.k as keyof typeof soap,e.target.value)}
                    placeholder={f.placeholder} style={{...sinp,padding:"6px 8px",fontSize:12}}/>
                </div>
              ))}
            </div>
            {soap.weightKg && soap.heightCm && (
              <div style={{ marginTop:6, fontSize:11, color:GRAY }}>
                BMI: {(parseFloat(soap.weightKg)/Math.pow(parseFloat(soap.heightCm)/100,2)).toFixed(1)}
              </div>
            )}
          </div>

          {/* SOAP fields */}
          <div style={{ flex:1, overflowY:"auto", display:"flex", flexDirection:"column", gap:8 }}>
            {([
              {k:"chiefComplaint", label:"Chief complaint *", rows:1, ph:"Main reason for visit"},
              {k:"history",        label:"History (S)",       rows:2, ph:"Subjective — patient history"},
              {k:"examination",    label:"Examination (O)",   rows:2, ph:"Objective — physical findings"},
              {k:"diagnosis",      label:"Diagnosis (A)",     rows:2, ph:"Assessment — working diagnosis"},
              {k:"icd10Codes",     label:"ICD-10 codes",      rows:1, ph:"J06.9, Z00.0"},
              {k:"treatmentPlan",  label:"Treatment plan (P)",rows:2, ph:"Plan — management and treatment"},
              {k:"followUpDays",   label:"Follow-up (days)",  rows:1, ph:"7"},
            ] as any[]).map((f:any)=>(
              <div key={f.k}>
                <label style={lbl}>{f.label}</label>
                {f.rows===1
                  ? <input value={(soap as any)[f.k]} onChange={e=>sf(f.k,e.target.value)}
                      placeholder={f.ph} style={sinp}/>
                  : <textarea value={(soap as any)[f.k]} onChange={e=>sf(f.k,e.target.value)}
                      rows={f.rows} placeholder={f.ph}
                      style={{...sinp,resize:"vertical" as const}}/>
                }
              </div>
            ))}
          </div>
        </div>

        {/* ── MIDDLE: Live Bill ───────────────────────────────────────── */}
        <div style={{ flex:1, display:"flex", flexDirection:"column", gap:10,
          display: "flex" }}>

          {/* Quick-add procedures */}
          <div style={{ padding:"12px 14px", background:"#fff", border:`1px solid ${BORDER}`, borderRadius:10 }}>
            <div style={sectionLabel}>Quick add — procedures</div>
            <div style={{ display:"flex", gap:6, flexWrap:"wrap" as const }}>
              {QUICK_PROCEDURES.map(proc=>(
                <button key={proc.tariff} onClick={()=>addQuickProcedure(proc)}
                  style={{ display:"flex", alignItems:"center", gap:5, padding:"5px 10px",
                    background:LIGHT, border:`1px solid ${BORDER}`, borderRadius:7,
                    fontSize:11, fontWeight:600, color:NAVY, cursor:"pointer" }}
                  onMouseEnter={e=>(e.currentTarget.style.borderColor=TEAL)}
                  onMouseLeave={e=>(e.currentTarget.style.borderColor=BORDER)}>
                  <proc.icon size={11}/>{proc.label} <span style={{color:GRAY}}>R{proc.price}</span>
                </button>
              ))}
            </div>
          </div>

          {/* Medication search — adds to bill + Rx */}
          <div style={{ padding:"12px 14px", background:"#fff", border:`1px solid ${BORDER}`, borderRadius:10, position:"relative" }}>
            <div style={sectionLabel}>Add medication (bill + Rx)</div>
            <div style={{ position:"relative" }}>
              <Search size={13} style={{ position:"absolute", left:8, top:"50%", transform:"translateY(-50%)", color:GRAY }}/>
              <input value={medSearch} onChange={e=>{setMedSearch(e.target.value);setShowMedSearch(true)}}
                onFocus={()=>setShowMedSearch(true)}
                placeholder="Search NAPPI catalogue…"
                style={{...sinp, paddingLeft:28}}/>
            </div>
            {showMedSearch && (medLoading || medResults.length>0) && (
              <div style={{ position:"absolute", left:14, right:14, zIndex:50,
                background:"#fff", border:`1px solid ${BORDER}`, borderRadius:8,
                boxShadow:"0 8px 32px rgba(0,0,0,0.12)", maxHeight:240, overflowY:"auto" }}>
                {medLoading && <div style={{padding:"10px 14px",fontSize:12,color:GRAY}}>Searching…</div>}
                {medResults.map((med:any)=>(
                  <div key={med.id||med.nappiCode}
                    onClick={()=>addMedicationToBillAndRx({
                      medicationName:`${med.genericName} ${med.strength||""}`.trim(),
                      nappiCode:med.nappiCode,
                      unitPrice:parseFloat(med.singleExitPrice)||0
                    })}
                    style={{ padding:"8px 14px", cursor:"pointer", borderBottom:`1px solid #F1F5F9` }}
                    onMouseEnter={e=>(e.currentTarget.style.background=LIGHT)}
                    onMouseLeave={e=>(e.currentTarget.style.background="#fff")}>
                    <div style={{fontWeight:600,fontSize:13,color:"#0F172A"}}>{med.genericName} <span style={{color:GRAY,fontWeight:400}}>{med.strength}</span></div>
                    <div style={{fontSize:11,color:GRAY}}>{med.brandName} · NAPPI: {med.nappiCode} · SEP: {fmtR(parseFloat(med.singleExitPrice)||0)}</div>
                  </div>
                ))}
                <div onClick={()=>setShowMedSearch(false)}
                  style={{padding:"6px 14px",fontSize:11,color:GRAY,cursor:"pointer",borderTop:`1px solid #F1F5F9`,textAlign:"center" as const}}>
                  Close
                </div>
              </div>
            )}
          </div>

          {/* Bill lines */}
          <div style={{ flex:1, overflowY:"auto", display:"flex", flexDirection:"column", gap:6 }}>
            {billLines.map((line,i)=>{
              const typeColor:Record<string,string> = {
                CONSULTATION:TEAL, PROCEDURE:NAVY, MEDICINE:GREEN, CONSUMABLE:AMBER
              }
              const col = typeColor[line.type]??GRAY
              return (
                <div key={line.id} style={{ display:"flex", alignItems:"center", gap:8,
                  padding:"8px 12px", background:"#fff", border:`1px solid ${BORDER}`,
                  borderLeft:`3px solid ${col}`, borderRadius:8 }}>
                  <div style={{ flex:1, minWidth:0 }}>
                    <div style={{ fontSize:12, fontWeight:600, color:"#0F172A",
                      overflow:"hidden", textOverflow:"ellipsis", whiteSpace:"nowrap" as const }}>
                      {line.description}
                    </div>
                    <div style={{ fontSize:10, color:GRAY }}>
                      {line.tariffCode||line.nappiCode||""} · qty {line.quantity}
                    </div>
                  </div>
                  <div style={{ fontSize:13, fontWeight:700, color:"#0F172A", flexShrink:0 }}>
                    {fmtR(line.gross)}
                  </div>
                  {line.id!=="consult-0191" && (
                    <button onClick={()=>removeBillLine(line.id)}
                      style={{background:"none",border:"none",cursor:"pointer",color:RED,display:"flex",padding:2}}>
                      <X size={12}/>
                    </button>
                  )}
                </div>
              )
            })}

            {/* Add custom line */}
            {!showCustom ? (
              <button onClick={()=>setShowCustom(true)}
                style={{display:"flex",alignItems:"center",gap:5,padding:"7px 12px",
                  border:`1px dashed ${BORDER}`,borderRadius:8,background:LIGHT,
                  color:GRAY,fontSize:12,cursor:"pointer"}}>
                <Plus size={12}/> Add custom item
              </button>
            ) : (
              <div style={{padding:"10px 12px",background:LIGHT,border:`1px solid ${BORDER}`,borderRadius:8}}>
                <div style={{display:"grid",gridTemplateColumns:"2fr 1fr 1fr",gap:8,marginBottom:8}}>
                  <input value={customLine.description} onChange={e=>setCustomLine(f=>({...f,description:e.target.value}))}
                    placeholder="Description" style={{...sinp,padding:"6px 8px",fontSize:12}} autoFocus/>
                  <input type="number" value={customLine.quantity} onChange={e=>setCustomLine(f=>({...f,quantity:e.target.value}))}
                    placeholder="Qty" style={{...sinp,padding:"6px 8px",fontSize:12}}/>
                  <input type="number" step="0.01" value={customLine.unitPrice} onChange={e=>setCustomLine(f=>({...f,unitPrice:e.target.value}))}
                    placeholder="R price" style={{...sinp,padding:"6px 8px",fontSize:12}}/>
                </div>
                <div style={{display:"flex",gap:6,justifyContent:"flex-end"}}>
                  <button onClick={()=>setShowCustom(false)} style={{...cancelBtn,padding:"4px 10px",fontSize:11}}>Cancel</button>
                  <button onClick={()=>{
                    const qty=parseFloat((customLine.quantity as any)||"1")
                    const price=parseFloat((customLine.unitPrice as any)||"0")
                    addBillLine({type:"CONSUMABLE",description:customLine.description,quantity:qty,unitPrice:price,gross:qty*price})
                    setCustomLine({type:"CONSUMABLE",description:"",quantity:"1",unitPrice:""})
                    setShowCustom(false)
                  }} style={{...primaryBtn,padding:"4px 10px",fontSize:11}}>Add</button>
                </div>
              </div>
            )}
          </div>

          {/* Bill total */}
          <div style={{padding:"12px 16px",background:NAVY,borderRadius:10,display:"flex",justifyContent:"space-between",alignItems:"center"}}>
            <span style={{fontSize:12,color:"rgba(255,255,255,0.6)"}}>Total · {billLines.length} items</span>
            <span style={{fontSize:20,fontWeight:800,color:"#fff"}}>{fmtR(billTotal)}</span>
          </div>
        </div>

        {/* ── RIGHT: Prescriptions ─────────────────────────────────────── */}
        <div style={{ flex:1, display:"flex", flexDirection:"column", gap:10,
          display: "flex" }}>

          <div style={{padding:"12px 14px",background:"#fff",border:`1px solid ${BORDER}`,borderRadius:10}}>
            <div style={{...sectionLabel,marginBottom:8}}>Prescriptions ({rxDrafts.length})</div>
            <div style={{fontSize:11,color:GRAY}}>
              Medications added via search auto-appear here. Complete dosage details before finishing.
            </div>
          </div>

          <div style={{flex:1,overflowY:"auto",display:"flex",flexDirection:"column",gap:10}}>
            {rxDrafts.length===0 ? (
              <div style={{textAlign:"center",padding:"40px 20px",color:GRAY,
                border:`1px dashed ${BORDER}`,borderRadius:10,fontSize:13}}>
                <Pill size={28} style={{marginBottom:8,opacity:0.4}}/>
                <div>Medications added during the consultation appear here.</div>
              </div>
            ) : rxDrafts.map(rx=>(
              <div key={rx.id} style={{padding:"12px 14px",background:rx.fromBill?"#F0FDF4":"#fff",
                border:`1px solid ${rx.fromBill?"#86EFAC":BORDER}`,borderRadius:10,position:"relative"}}>
                {rx.fromBill && (
                  <div style={{position:"absolute",top:8,right:8,fontSize:10,fontWeight:700,
                    color:GREEN,background:"#DCFCE7",padding:"1px 6px",borderRadius:20}}>
                    Added to bill
                  </div>
                )}
                <div style={{fontWeight:700,fontSize:13,color:"#0F172A",marginBottom:8,paddingRight:70}}>
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
                    <input type="number" value={rx.quantity} onChange={e=>updateRx(rx.id,"quantity",parseInt(e.target.value))}
                      style={{...sinp,padding:"5px 8px",fontSize:12}}/>
                  </div>
                  <div style={{gridColumn:"1/-1"}}>
                    <label style={lbl}>Instructions</label>
                    <input value={rx.instructions} onChange={e=>updateRx(rx.id,"instructions",e.target.value)}
                      placeholder="Take with food" style={{...sinp,padding:"5px 8px",fontSize:12}}/>
                  </div>
                </div>
                <button onClick={()=>removeRx(rx.id)}
                  style={{position:"absolute",bottom:8,right:8,background:"none",border:"none",
                    cursor:"pointer",color:RED,fontSize:11,display:"flex",alignItems:"center",gap:3}}>
                  <X size={10}/> Remove
                </button>
              </div>
            ))}

            {/* Manual add Rx */}
            <button onClick={()=>setRxDrafts(d=>[...d,{id:crypto.randomUUID(),
              medicationName:"",dosage:"",frequency:"",duration:"",quantity:30,instructions:"",fromBill:false}])}
              style={{display:"flex",alignItems:"center",gap:5,padding:"7px 12px",
                border:`1px dashed ${BORDER}`,borderRadius:8,background:LIGHT,
                color:GRAY,fontSize:12,cursor:"pointer"}}>
              <Plus size={12}/> Add prescription manually
            </button>
          </div>
        </div>
      </div>

      {/* ── Complete modal ─────────────────────────────────────────────────── */}
      {showComplete && (
        <div style={{position:"fixed",inset:0,background:"rgba(15,23,42,0.6)",
          display:"flex",alignItems:"center",justifyContent:"center",zIndex:1400,backdropFilter:"blur(4px)"}}>
          <div style={{background:"#fff",borderRadius:16,padding:28,width:520,
            boxShadow:"0 24px 64px rgba(0,0,0,0.25)"}}>
            <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginBottom:20}}>
              <h3 style={{margin:0,fontSize:18,fontWeight:700,color:"#0F172A"}}>Complete consultation</h3>
              <button onClick={()=>setShowComplete(false)} style={{background:"none",border:"none",cursor:"pointer",color:GRAY}}><X size={18}/></button>
            </div>

            <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:12,marginBottom:16}}>
              {[
                {label:"Duration",    value:`${durationMinutes} minutes`, color:NAVY},
                {label:"Bill total",  value:fmtR(billTotal),              color:GREEN},
                {label:"Bill items",  value:`${billLines.length} lines`,  color:TEAL},
                {label:"Prescriptions",value:`${rxDrafts.length} items`,  color:PURPLE},
              ].map(s=>(
                <div key={s.label} style={{padding:"10px 14px",background:LIGHT,borderRadius:8}}>
                  <div style={{fontSize:10,fontWeight:700,color:GRAY,textTransform:"uppercase",letterSpacing:"0.05em",marginBottom:2}}>{s.label}</div>
                  <div style={{fontSize:16,fontWeight:800,color:s.color}}>{s.value}</div>
                </div>
              ))}
            </div>

            {!soap.chiefComplaint.trim() && (
              <div style={{marginBottom:12,padding:"8px 12px",background:"#FFFBEB",border:"1px solid #FDE68A",borderRadius:8,fontSize:12,color:AMBER}}>
                ⚠ Chief complaint is empty — add a reason for the visit before completing.
              </div>
            )}
            {rxDrafts.some(rx=>rx.medicationName&&!rx.dosage) && (
              <div style={{marginBottom:12,padding:"8px 12px",background:"#EFF6FF",border:"1px solid #BFDBFE",borderRadius:8,fontSize:12,color:"#1D4ED8"}}>
                ℹ Some prescriptions are missing dosage details — they will still be saved.
              </div>
            )}

            {completeError && (
              <div style={{marginBottom:12,padding:"8px 12px",background:"#FEF2F2",border:"1px solid #FECACA",borderRadius:8,fontSize:12,color:RED}}>
                {completeError}
              </div>
            )}

            <div style={{display:"flex",gap:10,justifyContent:"flex-end"}}>
              <button onClick={()=>setShowComplete(false)} style={cancelBtn}>Back to session</button>
              <button onClick={()=>complete.mutate()} disabled={complete.isPending}
                style={{...primaryBtn,background:TEAL,display:"flex",alignItems:"center",gap:7}}>
                {complete.isPending
                  ? <><Loader size={14}/> Completing…</>
                  : <><CheckCircle size={14}/> Complete & save</>}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

// ── Style helpers ─────────────────────────────────────────────────────────────
const lbl:React.CSSProperties         = {display:"block",fontSize:11,fontWeight:600,color:"#374151",marginBottom:3}
const sectionLabel:React.CSSProperties = {fontSize:10,fontWeight:700,color:GRAY,textTransform:"uppercase",letterSpacing:"0.06em",marginBottom:8}
const sinp:React.CSSProperties        = {width:"100%",padding:"8px 10px",boxSizing:"border-box" as const,border:`1.5px solid ${BORDER}`,borderRadius:7,fontSize:13,outline:"none",background:"#fff"}
const primaryBtn:React.CSSProperties  = {background:NAVY,color:"#fff",border:"none",borderRadius:9,padding:"9px 18px",fontSize:13,fontWeight:600,cursor:"pointer"}
const cancelBtn:React.CSSProperties   = {padding:"9px 16px",border:`1px solid ${BORDER}`,borderRadius:9,background:"#fff",fontSize:13,cursor:"pointer",color:"#374151"}
const voiceBtn = (bg:string):React.CSSProperties => ({display:"flex",alignItems:"center",gap:4,padding:"4px 10px",background:bg,color:"#fff",border:"none",borderRadius:6,fontSize:11,fontWeight:600,cursor:"pointer"})

