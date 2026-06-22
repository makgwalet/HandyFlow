// src/pages/clinic/PatientFilePage.tsx
// Full-page patient file — 8 tabs + family management + account lifecycle
import { useState, useRef, useEffect } from "react"
import ConsultationSession from "./ConsultationSession"
import { LabsTabEnhanced } from "./LabsTab"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  User, Calendar, Stethoscope, CreditCard, Pill, FlaskConical,
  FileText, Clock, Heart, AlertCircle, Phone, Mail, Plus, X,
  ChevronDown, ChevronUp, Activity, CheckCircle, PlayCircle,
  XCircle, Download, Mic, MicOff, Loader, Upload, Users, Link,
  MoreVertical, Archive, UserX, UserCheck, ArrowRight,
} from "lucide-react"

// ── Types ─────────────────────────────────────────────────────────────────────

interface Patient {
  id: string; firstName: string; lastName: string; fullName: string
  idNumber: string; dateOfBirth: string; gender: string
  phone: string; email: string; bloodType: string
  allergies: string[]; chronicConditions: string[]
  emergencyContactName: string; emergencyContactPhone: string
  notes: string; active: boolean
  accountType: "INDIVIDUAL" | "PRINCIPAL" | "DEPENDANT"
  principalId?: string; principalName?: string
  relationship?: string; lastVisitAt?: string; archivedAt?: string
}
interface Appointment {
  id: string; patientName: string; practitionerName: string
  scheduledAt: string; durationMinutes: number
  appointmentType: string; status: string; reason: string
}
interface Consultation {
  id: string; practitionerName: string; consultedAt: string
  weightKg: number; heightCm: number; bloodPressure: string
  pulseBpm: number; temperatureC: number; oxygenSatPct: number
  chiefComplaint: string; history: string; examination: string
  diagnosis: string; icd10Codes: string[]; treatmentPlan: string
  followUpDays: number | null; billed: boolean; billingAmount: number
}
interface Prescription {
  id: string; medicationName: string; dosage: string; frequency: string
  duration: string; quantity: number; repeats: number; instructions: string
  dispensed: boolean; prescribedAt: string; practitionerName?: string
}
interface Practitioner { id: string; fullName: string; specialty: string }
interface BillLine {
  id: string; type: "CONSULTATION"|"PROCEDURE"|"MEDICINE"|"CONSUMABLE"
  description: string; tariffCode?: string; nappiCode?: string
  quantity: number; unitPrice: number; gross: number
}

// ── Design tokens ─────────────────────────────────────────────────────────────

const NAVY="#1B3A6B"; const TEAL="#0D9488"; const RED="#DC2626"
const GREEN="#166534"; const AMBER="#D97706"; const PURPLE="#7C3AED"
const GRAY="#64748B"; const BORDER="#E2E8F0"; const LIGHT="#F8FAFC"

const STATUS_CFG: Record<string,{color:string;bg:string;label:string;icon:any}> = {
  SCHEDULED:   {color:"#1D4ED8",bg:"#EFF6FF",label:"Scheduled",  icon:Calendar},
  CONFIRMED:   {color:PURPLE,   bg:"#F5F3FF",label:"Confirmed",  icon:CheckCircle},
  IN_PROGRESS: {color:AMBER,    bg:"#FFFBEB",label:"In Progress",icon:PlayCircle},
  COMPLETED:   {color:GREEN,    bg:"#DCFCE7",label:"Completed",  icon:CheckCircle},
  CANCELLED:   {color:RED,      bg:"#FEF2F2",label:"Cancelled",  icon:XCircle},
  NO_SHOW:     {color:GRAY,     bg:LIGHT,    label:"No Show",    icon:User},
}
const STATUS_FLOW: Record<string,{action:string;label:string;color:string}[]> = {
  SCHEDULED:   [{action:"confirm",label:"Confirm",color:PURPLE},{action:"cancel",label:"Cancel",color:RED}],
  CONFIRMED:   [{action:"start",label:"Start",color:AMBER},{action:"no_show",label:"No Show",color:GRAY},{action:"cancel",label:"Cancel",color:RED}],
  IN_PROGRESS: [{action:"complete",label:"Complete",color:GREEN}],
}
const ACCOUNT_CFG: Record<string,{label:string;bg:string;color:string}> = {
  INDIVIDUAL: {label:"Individual",bg:"#EFF6FF",color:"#1D4ED8"},
  PRINCIPAL:  {label:"Principal", bg:"#F0FDF4",color:"#166534"},
  DEPENDANT:  {label:"Dependant", bg:"#F5F3FF",color:PURPLE},
}

const saId = (id?: string) => {
  const c=(id??"").replace(/\D/g,""); if (c.length!==13) return null
  const yy=+c.slice(0,2),mm=+c.slice(2,4),dd=+c.slice(4,6)
  const yr=yy<=(new Date().getFullYear()%100)?2000+yy:1900+yy
  const months=["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"]
  return { dob:`${String(dd).padStart(2,"0")} ${months[mm-1]} ${yr}`,
    age:Math.floor((Date.now()-new Date(yr,mm-1,dd).getTime())/(365.25*24*3600*1000)),
    gender:+c[6]>=5?"Male":"Female" }
}
const fmtDT  = (iso:string) => new Date(iso).toLocaleDateString("en-ZA",{day:"numeric",month:"short",year:"numeric"})
const fmtTime= (iso:string) => new Date(iso).toLocaleTimeString("en-ZA",{hour:"2-digit",minute:"2-digit"})
const fmtR   = (v:number)   => `R ${(v??0).toLocaleString("en-ZA",{minimumFractionDigits:2})}`
const unwrap = (r:any)      => { const p=r.data?.data??r.data; return Array.isArray(p)?p:(p?.content??[]) }

const downloadPdf = async (url:string, filename:string) => {
  const res = await apiClient.get(url,{responseType:"blob"})
  const link = document.createElement("a")
  link.href = URL.createObjectURL(new Blob([res.data],{type:"application/pdf"}))
  link.download = filename; link.click(); URL.revokeObjectURL(link.href)
}

// ── Main component ─────────────────────────────────────────────────────────────

type TabId = "overview"|"appointments"|"consultation"|"running-bill"|"rx"|"labs"|"documents"|"history"

interface Props {
  patient: Patient; onClose: () => void; onNavigate: (tab:any)=>void; onOpenPatient?: (p:Patient)=>void
  initialSession?: any; onSessionClear?: () => void
}

export default function PatientFilePage({ patient, onClose, onNavigate, onOpenPatient, initialSession, onSessionClear }: Props) {
  const qc = useQueryClient()
  const [activeTab, setActiveTab] = useState<TabId>("overview")
  const [billLines, setBillLines] = useState<BillLine[]>([])
  const [showActions, setShowActions] = useState(false)
  const [activeSession, setActiveSession] = useState<Appointment|null>(initialSession||null)
  const [sessionMinimised, setSessionMinimised] = useState(false)
  // Clear parent's initialSession ref once we've consumed it
  useEffect(() => { if (initialSession) onSessionClear?.() }, [])
  const pid = patient.id
  const idInfo = saId(patient.idNumber)

  const addToBill = (line: Omit<BillLine,"id">) =>
    setBillLines(b=>[...b,{...line,id:crypto.randomUUID()}])
  const removeBillLine = (id:string) =>
    setBillLines(b=>b.filter(l=>l.id!==id))

  const { data: appointments=[] } = useQuery<Appointment[]>({
    queryKey:["pf-appointments",pid],
    queryFn: async ()=>unwrap(await apiClient.get(`/api/v1/clinic/patients/${pid}/appointments`)),
  })
  const { data: consultations=[] } = useQuery<Consultation[]>({
    queryKey:["pf-consultations",pid],
    queryFn: async ()=>unwrap(await apiClient.get(`/api/v1/clinic/patients/${pid}/consultations`)),
  })
  const { data: practitioners=[] } = useQuery<Practitioner[]>({
    queryKey:["clinic-practitioners-list"],
    queryFn: async ()=>unwrap(await apiClient.get("/api/v1/clinic/practitioners/list")),
  })
  // Family members (dependants if principal, or siblings + principal if dependant)
  const { data: familyMembers=[] } = useQuery<Patient[]>({
    queryKey:["pf-family",pid],
    queryFn: async () => {
      if (patient.accountType==="INDIVIDUAL") return []
      return unwrap(await apiClient.get(`/api/v1/clinic/patients/${pid}/family`))
    },
    enabled: patient.accountType !== "INDIVIDUAL",
  })

  // Account lifecycle mutations
  const deactivate = useMutation({
    mutationFn: ()=>apiClient.patch(`/api/v1/clinic/patients/${pid}`,{active:false}),
    onSuccess: ()=>{ qc.invalidateQueries({queryKey:["clinic-patients"]}); setShowActions(false) },
  })
  const reactivate = useMutation({
    mutationFn: ()=>apiClient.patch(`/api/v1/clinic/patients/${pid}`,{active:true}),
    onSuccess: ()=>{ qc.invalidateQueries({queryKey:["clinic-patients"]}); setShowActions(false) },
  })
  const archive = useMutation({
    mutationFn: (reason:string)=>apiClient.patch(`/api/v1/clinic/patients/${pid}`,{archivedAt:new Date().toISOString(),archiveReason:reason}),
    onSuccess: ()=>{ qc.invalidateQueries({queryKey:["clinic-patients"]}); setShowActions(false) },
  })
  const convertToFamily = useMutation({
    mutationFn: ()=>apiClient.patch(`/api/v1/clinic/patients/${pid}`,{accountType:"PRINCIPAL"}),
    onSuccess: ()=>{ qc.invalidateQueries({queryKey:["clinic-patients"]}); qc.invalidateQueries({queryKey:["pf-family",pid]}); setShowActions(false) },
  })

  const pendingAppts = (appointments as Appointment[]).filter(a=>["SCHEDULED","CONFIRMED","IN_PROGRESS"].includes(a.status)).length

  const TABS: {id:TabId;label:string;icon:React.ElementType;badge?:number}[] = [
    {id:"overview",     label:"Overview",     icon:User},
    {id:"appointments", label:"Appointments", icon:Calendar,   badge:pendingAppts||undefined},
    {id:"consultation", label:"Consult",      icon:Stethoscope},
    {id:"running-bill", label:"Running bill", icon:CreditCard, badge:billLines.length||undefined},
    {id:"rx",           label:"Prescriptions",icon:Pill},
    {id:"labs",         label:"Lab results",  icon:FlaskConical},
    {id:"documents",    label:"Documents",    icon:FileText},
    {id:"history",      label:"History",      icon:Clock},
  ]

  const acctCfg = ACCOUNT_CFG[patient.accountType]??ACCOUNT_CFG.INDIVIDUAL
  const isArchived = !!patient.archivedAt

  return (
    <div style={{ fontFamily:"'Inter',system-ui,sans-serif", minHeight:600 }}>
      {/* ── Patient banner ──────────────────────────────────────────────── */}
      <div style={{ background:`linear-gradient(135deg,${NAVY} 0%,#0D2145 100%)`,
        borderRadius:12, marginBottom:24, padding:"24px 28px 0", overflow:"hidden" }}>
        <div style={{ display:"flex", justifyContent:"space-between", alignItems:"flex-start", marginBottom:16 }}>
          <div style={{ display:"flex", alignItems:"center", gap:16 }}>
            <div style={{ width:64, height:64, borderRadius:"50%", background:"rgba(255,255,255,0.15)",
              display:"flex", alignItems:"center", justifyContent:"center",
              fontSize:24, fontWeight:800, color:"#fff", flexShrink:0 }}>
              {patient.firstName?.[0]}{patient.lastName?.[0]}
            </div>
            <div>
              <div style={{ display:"flex", alignItems:"center", gap:10, marginBottom:4 }}>
                <h2 style={{ margin:0, fontSize:22, fontWeight:800, color:"#fff" }}>{patient.fullName}</h2>
                <span style={{ background:`${acctCfg.bg}25`, color:acctCfg.bg,
                  padding:"2px 10px", borderRadius:20, fontSize:11, fontWeight:700,
                  border:`1px solid ${acctCfg.bg}50` }}>
                  {acctCfg.label}
                </span>
                {isArchived && <span style={{ background:"rgba(255,255,255,0.15)", color:"rgba(255,255,255,0.7)", padding:"2px 8px", borderRadius:20, fontSize:11 }}>ARCHIVED</span>}
              </div>
              <div style={{ display:"flex", gap:12, flexWrap:"wrap", fontSize:13, color:"rgba(255,255,255,0.7)" }}>
                {idInfo && <span>{idInfo.dob} · {idInfo.age} yrs · {idInfo.gender}</span>}
                {patient.bloodType && <span style={{ background:"rgba(220,38,38,0.3)", color:"#FCA5A5", padding:"1px 8px", borderRadius:20, fontSize:12, fontWeight:700 }}>{patient.bloodType}</span>}
                {patient.phone && <span style={{ display:"flex", alignItems:"center", gap:4 }}><Phone size={11}/>{patient.phone}</span>}
              </div>
              {/* Family link */}
              {patient.accountType==="DEPENDANT" && patient.principalName && (
                <div style={{ display:"flex", alignItems:"center", gap:4, fontSize:12, color:"rgba(167,139,250,0.9)", marginTop:6 }}>
                  <Link size={11}/>
                  {patient.relationship?.toLowerCase()||"dependant"} of {patient.principalName}
                </div>
              )}
              {/* Alert badges */}
              <div style={{ display:"flex", gap:8, marginTop:8, flexWrap:"wrap" }}>
                {patient.allergies?.length > 0 && (
                  <span style={{ background:"rgba(220,38,38,0.25)", color:"#FCA5A5",
                    padding:"2px 8px", borderRadius:20, fontSize:11, fontWeight:600,
                    display:"flex", alignItems:"center", gap:4 }}>
                    <AlertCircle size={10}/> ⚠ {patient.allergies.length} allerg{patient.allergies.length===1?"y":"ies"}
                  </span>
                )}
                {patient.chronicConditions?.length > 0 && (
                  <span style={{ background:"rgba(217,119,6,0.25)", color:"#FCD34D",
                    padding:"2px 8px", borderRadius:20, fontSize:11, fontWeight:600,
                    display:"flex", alignItems:"center", gap:4 }}>
                    <Heart size={10}/> {patient.chronicConditions.length} chronic
                  </span>
                )}
              </div>
            </div>
          </div>

          {/* Actions menu */}
          <div style={{ position:"relative" }}>
            <button onClick={()=>setShowActions(v=>!v)}
              style={{ background:"rgba(255,255,255,0.1)", border:"none", borderRadius:8,
                cursor:"pointer", color:"#fff", padding:"8px 10px", display:"flex", alignItems:"center", gap:6, fontSize:13 }}>
              <MoreVertical size={16}/> Actions
            </button>
            {showActions && (
              <div style={{ position:"absolute", right:0, top:"100%", marginTop:6,
                background:"#fff", borderRadius:10, border:`1px solid ${BORDER}`,
                boxShadow:"0 8px 32px rgba(0,0,0,0.14)", minWidth:220, zIndex:50, overflow:"hidden" }}>
                {/* Convert individual → principal */}
                {patient.accountType==="INDIVIDUAL" && (
                  <ActionItem icon={Users} label="Convert to family account"
                    color={PURPLE} onClick={()=>convertToFamily.mutate()}
                    hint="Promotes patient to principal — add dependants after"/>
                )}
                {/* Deactivate / reactivate */}
                {patient.active ? (
                  <ActionItem icon={UserX} label="Deactivate account"
                    color={AMBER} onClick={()=>deactivate.mutate()}
                    hint="Patient hidden from active list"/>
                ) : (
                  <ActionItem icon={UserCheck} label="Reactivate account"
                    color={GREEN} onClick={()=>reactivate.mutate()}
                    hint="Restore to active status"/>
                )}
                {/* Archive */}
                {!isArchived && (
                  <ActionItem icon={Archive} label="Archive record"
                    color={RED} onClick={()=>{
                      const reason = window.prompt("Archive reason (HPCSA records retained 6 years):")
                      if (reason !== null) archive.mutate(reason)
                    }}
                    hint="Soft-archive — never permanently deleted"/>
                )}
                <div style={{ borderTop:`1px solid ${BORDER}`, margin:"4px 0" }}/>
                <button onClick={()=>setShowActions(false)}
                  style={{ width:"100%", padding:"10px 16px", border:"none", background:"none",
                    textAlign:"left" as const, fontSize:13, color:GRAY, cursor:"pointer" }}>
                  Cancel
                </button>
              </div>
            )}
          </div>
        </div>

        {/* Tab bar */}
        <div style={{ display:"flex", gap:1, overflowX:"auto", marginTop:4 }}>
          {TABS.map(t=>{
            const Icon=t.icon; const active=activeTab===t.id
            return (
              <button key={t.id} onClick={()=>setActiveTab(t.id)}
                style={{ display:"flex", alignItems:"center", gap:6, padding:"10px 14px",
                  background:active?"rgba(255,255,255,0.12)":"transparent", border:"none",
                  borderBottom:active?"2px solid #0D9488":"2px solid transparent",
                  color:active?"#fff":"rgba(255,255,255,0.6)",
                  fontWeight:active?600:400, fontSize:13, cursor:"pointer",
                  whiteSpace:"nowrap", marginBottom:-1 }}>
                <Icon size={13}/>{t.label}
                {t.badge ? (
                  <span style={{ background:TEAL, color:"#fff", borderRadius:"50%",
                    width:16, height:16, fontSize:10, fontWeight:700,
                    display:"flex", alignItems:"center", justifyContent:"center" }}>
                    {t.badge}
                  </span>
                ) : null}
              </button>
            )
          })}
        </div>
      </div>

      {/* ── Consultation session — full or minimised ─────────────────────── */}
      {activeSession && !sessionMinimised && (
        <div style={{position:"fixed",inset:0,background:"rgba(15,23,42,0.7)",zIndex:1300,
          display:"flex",alignItems:"center",justifyContent:"center",backdropFilter:"blur(4px)"}}>
          <div style={{background:"#fff",borderRadius:16,width:"min(1200px,96vw)",height:"92vh",
            padding:24,boxShadow:"0 32px 80px rgba(0,0,0,0.3)",display:"flex",flexDirection:"column"}}>
            <ConsultationSession
              patient={patient}
              appointment={activeSession}
              onMinimise={()=>setSessionMinimised(true)}
              onComplete={(_id)=>{
                setActiveSession(null); setSessionMinimised(false)
                setActiveTab("running-bill")
                qc.invalidateQueries({queryKey:["pf-appointments",pid]})
                qc.invalidateQueries({queryKey:["pf-consultations",pid]})
              }}
              onCancel={()=>{ setActiveSession(null); setSessionMinimised(false) }}
            />
          </div>
        </div>
      )}

      {/* ── Minimised session sticky bar ────────────────────────────────── */}
      {activeSession && sessionMinimised && (
        <div style={{position:"fixed",bottom:0,left:0,right:0,zIndex:1300,
          background:"#1B3A6B",borderTop:"3px solid #0D9488",
          padding:"10px 24px",display:"flex",alignItems:"center",gap:16,
          boxShadow:"0 -4px 24px rgba(0,0,0,0.25)"}}>
          <div style={{display:"flex",alignItems:"center",gap:8}}>
            <div style={{width:8,height:8,borderRadius:"50%",background:"#EF4444",animation:"pulse 1.5s infinite"}}/>
            <span style={{color:"#fff",fontWeight:700,fontSize:14}}>Session in progress</span>
            <span style={{color:"rgba(255,255,255,0.6)",fontSize:13}}>— {activeSession.patientName||patient.fullName}</span>
          </div>
          <div style={{flex:1}}/>
          <span style={{color:"rgba(255,255,255,0.5)",fontSize:12}}>Navigate freely — session is saved</span>
          <button onClick={()=>setSessionMinimised(false)}
            style={{background:"#0D9488",color:"#fff",border:"none",borderRadius:8,
              padding:"7px 16px",fontSize:13,fontWeight:700,cursor:"pointer",
              display:"flex",alignItems:"center",gap:6}}>
            ↑ Return to session
          </button>
          <button onClick={()=>{ setActiveSession(null); setSessionMinimised(false) }}
            style={{background:"rgba(255,255,255,0.1)",color:"rgba(255,255,255,0.7)",
              border:"none",borderRadius:8,padding:"7px 12px",fontSize:12,cursor:"pointer"}}>
            Discard session
          </button>
        </div>
      )}

      {/* ── Tab content ─────────────────────────────────────────────────── */}
      {activeTab==="overview"     && <OverviewTab patient={patient} idInfo={idInfo} familyMembers={familyMembers as Patient[]} onOpenPatient={onOpenPatient} qc={qc}/>}
      {activeTab==="appointments" && <AppointmentsTab patient={patient} appointments={appointments as Appointment[]} practitioners={practitioners as Practitioner[]} qc={qc} onStartSession={setActiveSession}/>}
      {activeTab==="consultation" && <ConsultationTab patient={patient} consultations={consultations as Consultation[]} practitioners={practitioners as Practitioner[]} qc={qc} addToBill={addToBill} onSwitchTab={setActiveTab}/>}
      {activeTab==="running-bill" && <RunningBillTab billLines={billLines} onRemove={removeBillLine} patient={patient}/>}
      {activeTab==="rx"           && <PrescriptionsTab patient={patient} consultations={consultations as Consultation[]}/>}
      {activeTab==="labs"         && <LabsTabEnhanced patient={patient}/>}
      {activeTab==="documents"    && <DocumentsTab patient={patient} consultations={consultations as Consultation[]}/>}
      {activeTab==="history"      && <HistoryTab appointments={appointments as Appointment[]} consultations={consultations as Consultation[]}/>}
    </div>
  )
}

// ── ACTION MENU ITEM ──────────────────────────────────────────────────────────

function ActionItem({ icon:Icon, label, color, onClick, hint }: {
  icon:React.ElementType; label:string; color:string; onClick:()=>void; hint:string
}) {
  return (
    <button onClick={onClick}
      style={{ width:"100%", padding:"10px 16px", border:"none", background:"none",
        textAlign:"left" as const, cursor:"pointer", display:"flex", alignItems:"flex-start", gap:10 }}
      onMouseEnter={e=>(e.currentTarget.style.background=LIGHT)}
      onMouseLeave={e=>(e.currentTarget.style.background="none")}>
      <Icon size={15} color={color} style={{ marginTop:2, flexShrink:0 }}/>
      <div>
        <div style={{ fontSize:13, fontWeight:600, color:"#0F172A" }}>{label}</div>
        <div style={{ fontSize:11, color:GRAY, marginTop:1 }}>{hint}</div>
      </div>
    </button>
  )
}

// ── OVERVIEW TAB ──────────────────────────────────────────────────────────────

function OverviewTab({ patient, idInfo, familyMembers, onOpenPatient, qc }: {
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
              <div style={{ fontSize:14, color:"#0F172A", fontWeight:500, wordBreak:"break-all" }}>{item.value}</div>
            </div>
          ))}
        </div>

        {/* Allergies */}
        {patient.allergies?.length > 0 && (
          <div style={{ marginBottom:12, padding:"14px 16px", background:"#FEF2F2",
            border:"1px solid #FECACA", borderRadius:12 }}>
            <div style={{ display:"flex", alignItems:"center", gap:6, marginBottom:8 }}>
              <AlertCircle size={13} color={RED}/>
              <span style={{ fontSize:11, fontWeight:700, color:RED, textTransform:"uppercase", letterSpacing:"0.06em" }}>⚠ Allergies</span>
            </div>
            <div style={{ display:"flex", gap:6, flexWrap:"wrap" }}>
              {patient.allergies.map(a=>(
                <span key={a} style={{ background:"#fff", color:RED, padding:"3px 10px",
                  borderRadius:6, fontSize:13, fontWeight:600, border:"1px solid #FECACA" }}>{a}</span>
              ))}
            </div>
          </div>
        )}

        {/* Chronic conditions */}
        {patient.chronicConditions?.length > 0 && (
          <div style={{ marginBottom:12, padding:"14px 16px", background:"#FFFBEB",
            border:"1px solid #FDE68A", borderRadius:12 }}>
            <div style={{ display:"flex", alignItems:"center", gap:6, marginBottom:8 }}>
              <Heart size={13} color={AMBER}/>
              <span style={{ fontSize:11, fontWeight:700, color:AMBER, textTransform:"uppercase", letterSpacing:"0.06em" }}>Chronic conditions</span>
            </div>
            <div style={{ display:"flex", gap:6, flexWrap:"wrap" }}>
              {patient.chronicConditions.map(c=>(
                <span key={c} style={{ background:"#fff", color:AMBER, padding:"3px 10px",
                  borderRadius:6, fontSize:13, fontWeight:600, border:"1px solid #FDE68A" }}>{c}</span>
              ))}
            </div>
          </div>
        )}

        {patient.notes && (
          <div style={{ padding:"12px 14px", background:LIGHT, borderRadius:10, border:`1px solid ${BORDER}` }}>
            <div style={{ fontSize:10, fontWeight:700, color:GRAY, marginBottom:4, textTransform:"uppercase", letterSpacing:"0.06em" }}>Notes</div>
            <div style={{ fontSize:13, color:"#475569", lineHeight:1.6 }}>{patient.notes}</div>
          </div>
        )}
      </div>

      {/* Right — family + status */}
      <div style={{ display:"flex", flexDirection:"column", gap:12 }}>
        {/* Account status */}
        <div style={{ padding:"14px 16px", background:LIGHT, border:`1px solid ${BORDER}`, borderRadius:12 }}>
          <div style={{ fontSize:11, fontWeight:700, color:GRAY, textTransform:"uppercase", letterSpacing:"0.06em", marginBottom:8 }}>Account status</div>
          <div style={{ display:"flex", gap:8, flexWrap:"wrap" }}>
            <span style={{ background:patient.active?"#DCFCE7":"#FEF2F2",
              color:patient.active?GREEN:RED, padding:"3px 10px", borderRadius:20,
              fontSize:12, fontWeight:700 }}>{patient.active?"ACTIVE":"INACTIVE"}</span>
            {patient.archivedAt && <span style={{ background:"#F1F5F9", color:GRAY, padding:"3px 10px", borderRadius:20, fontSize:12, fontWeight:700 }}>ARCHIVED</span>}
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
                    background:"#EFF6FF", color:"#1D4ED8", border:"1px solid #BFDBFE",
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
                        background:"#fff", border:`1px solid ${BORDER}`, borderRadius:8,
                        cursor:isCurrentPatient?"default":"pointer" }}
                      onMouseEnter={e=>{ if (!isCurrentPatient) (e.currentTarget as HTMLDivElement).style.background="#F0FDF4" }}
                      onMouseLeave={e=>{ (e.currentTarget as HTMLDivElement).style.background="#fff" }}>
                      <div style={{ width:28, height:28, borderRadius:"50%",
                        background:isCurrentPatient?"#E0F2FE":"#F0FDF4",
                        display:"flex", alignItems:"center", justifyContent:"center",
                        fontSize:11, fontWeight:700, color:isCurrentPatient?"#0369A1":TEAL, flexShrink:0 }}>
                        {m.firstName?.[0]}{m.lastName?.[0]}
                      </div>
                      <div style={{ flex:1, minWidth:0 }}>
                        <div style={{ fontSize:12, fontWeight:600, color:"#0F172A",
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
                      {!isCurrentPatient && <ArrowRight size={12} color={GRAY}/>}
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
          <div style={{ fontSize:12, color:GRAY, marginBottom:16, padding:"8px 12px", background:"#EFF6FF", borderRadius:8 }}>
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
          {depError && <div style={{ marginTop:10, padding:"8px 12px", background:"#FEF2F2", border:"1px solid #FECACA", borderRadius:8, fontSize:13, color:RED }}>{depError}</div>}
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

// ── APPOINTMENTS TAB ──────────────────────────────────────────────────────────

function AppointmentsTab({ patient, appointments, practitioners, qc, onStartSession }:
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
        <div style={{ fontSize:15, fontWeight:700, color:"#0F172A" }}>
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
                borderRadius:10, padding:"14px 18px", background:"#fff" }}>
                <div style={{ display:"flex", justifyContent:"space-between", alignItems:"flex-start" }}>
                  <div>
                    <div style={{ display:"flex", alignItems:"center", gap:8, marginBottom:4 }}>
                      <span style={{ fontWeight:700, fontSize:14, color:"#0F172A" }}>{fmtDT(a.scheduledAt)} · {fmtTime(a.scheduledAt)}</span>
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
                          style={{ padding:"5px 12px", border:"none", borderRadius:7, fontSize:12, fontWeight:600, cursor:"pointer", background:`${btn.color}18`, color:btn.color }}>
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
                  {["CONSULTATION","FOLLOW_UP","PROCEDURE","EMERGENCY","CHECKUP"].map(t=><option key={t} value={t}>{t.replace("_"," ")}</option>)}
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

// ── CONSULTATION TAB (speech + Claude SOAP) ───────────────────────────────────

function ConsultationTab({ patient, consultations, practitioners, qc, addToBill, onSwitchTab }:
  {patient:Patient; consultations:Consultation[]; practitioners:Practitioner[]; qc:any; addToBill:(l:any)=>void; onSwitchTab:(t:any)=>void}) {
  const [expanded, setExpanded] = useState<string|null>(null)
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
  const [extracting, setExtracting]   = useState(false)
  const recognitionRef = useRef<any>(null)

  const EMPTY = { practitionerId:"",chiefComplaint:"",weightKg:"",heightCm:"",bloodPressure:"",pulseBpm:"",temperatureC:"",oxygenSatPct:"",history:"",examination:"",diagnosis:"",icd10Codes:"",treatmentPlan:"",followUpDays:"" }
  const [form, setForm] = useState({...EMPTY})
  const f = (k:keyof typeof EMPTY, v:string) => setForm(p=>({...p,[k]:v}))

  const [rxForm, setRxForm] = useState({medicationName:"",dosage:"",frequency:"",duration:"",quantity:"30",repeats:"0",instructions:""})

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
  const addRx = useMutation({
    mutationFn: ({cid,body}:{cid:string;body:any})=>apiClient.post(`/api/v1/clinic/consultations/${cid}/prescriptions`,body),
    onSuccess: ()=>qc.invalidateQueries({queryKey:["pf-rx",showRx]}),
  })

  const startRecording = () => {
    const SR=(window as any).SpeechRecognition||(window as any).webkitSpeechRecognition
    if (!SR) { alert("Speech recognition requires Chrome or Edge."); return }
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
    if (!transcript.trim()) return; setExtracting(true)
    try {
      const res=await fetch("https://api.anthropic.com/v1/messages",{
        method:"POST", headers:{"Content-Type":"application/json"},
        body:JSON.stringify({model:"claude-sonnet-4-6",max_tokens:1000,messages:[{role:"user",content:`You are a medical scribe. Extract a SOAP note from this consultation transcript. Return ONLY valid JSON with these fields: chiefComplaint, history, examination, diagnosis, icd10Codes (comma-separated), treatmentPlan, followUpDays (number or null).\n\nTranscript:\n${transcript}`}]})
      })
      const data=await res.json()
      const text=data.content?.[0]?.text??""
      const parsed=JSON.parse(text.replace(/```json|```/g,"").trim())
      setForm(p=>({...p,...Object.fromEntries(Object.entries(parsed).filter(([,v])=>v!=null).map(([k,v])=>[k,String(v)]))}))
    } catch(e){console.error("SOAP extraction failed",e)}
    setExtracting(false)
  }

  const sorted=[...consultations].sort((a,b)=>b.consultedAt.localeCompare(a.consultedAt))

  return (
    <div>
      <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:20 }}>
        <div style={{ fontSize:15, fontWeight:700, color:"#0F172A" }}>{consultations.length} consultation{consultations.length!==1?"s":""}</div>
        <button onClick={()=>{setShowNew(true);setForm({...EMPTY});setTranscript("");setApiError("")}} style={btnPrimary}><Plus size={14}/> Record consultation</button>
      </div>

      {sorted.length===0 ? <Empty icon={Stethoscope} msg="No consultations recorded"/> : (
        <div style={{ display:"flex", flexDirection:"column", gap:10 }}>
          {sorted.map((c:any)=>{
            const isOpen=expanded===c.id
            return (
              <div key={c.id} style={{ border:`1px solid ${BORDER}`, borderRadius:12, overflow:"hidden" }}>
                <div onClick={()=>setExpanded(isOpen?null:c.id)}
                  style={{ display:"flex", justifyContent:"space-between", alignItems:"center", padding:"14px 18px", cursor:"pointer", background:isOpen?LIGHT:"#fff" }}>
                  <div style={{ display:"flex", alignItems:"center", gap:12 }}>
                    <div style={{ width:36, height:36, borderRadius:8, background:"#F0FDF4", border:"1px solid #86EFAC", display:"flex", alignItems:"center", justifyContent:"center" }}>
                      <Activity size={16} color={TEAL}/>
                    </div>
                    <div>
                      <div style={{ fontWeight:700, fontSize:14, color:"#0F172A", marginBottom:2 }}>{c.chiefComplaint}</div>
                      <div style={{ fontSize:12, color:GRAY }}>{fmtDT(c.consultedAt)}{c.practitionerName&&` · Dr. ${c.practitionerName}`}{c.diagnosis&&` · ${c.diagnosis}`}</div>
                    </div>
                  </div>
                  <div style={{ display:"flex", alignItems:"center", gap:8 }}>
                    <button onClick={e=>{e.stopPropagation();openEdit(c)}} style={{ display:"flex", alignItems:"center", gap:4, padding:"4px 10px", background:"#EFF6FF", color:"#1D4ED8", border:"1px solid #BFDBFE", borderRadius:6, fontSize:12, cursor:"pointer", fontWeight:600 }}>✏ Edit</button>
                    <button onClick={e=>{e.stopPropagation();setShowRx(c.id)}} style={{ display:"flex", alignItems:"center", gap:4, padding:"4px 10px", background:"#F0FDF4", color:GREEN, border:"1px solid #86EFAC", borderRadius:6, fontSize:12, cursor:"pointer", fontWeight:600 }}><Pill size={11}/> Rx</button>
                    <button onClick={e=>{e.stopPropagation();downloadPdf(`/api/v1/clinic/consultations/${c.id}/prescription-pdf`,`rx-${c.id}.pdf`)}} style={{ display:"flex", alignItems:"center", gap:4, padding:"4px 10px", background:"#EFF6FF", color:"#1D4ED8", border:"1px solid #BFDBFE", borderRadius:6, fontSize:12, cursor:"pointer", fontWeight:600 }}>Rx PDF</button>
                    {c.followUpDays&&<span style={{ fontSize:11, color:AMBER, background:"#FFFBEB", padding:"2px 8px", borderRadius:20, border:"1px solid #FDE68A" }}>F/U {c.followUpDays}d</span>}
                    {isOpen?<ChevronUp size={16} color={GRAY}/>:<ChevronDown size={16} color={GRAY}/>}
                  </div>
                </div>
                {isOpen&&(
                  <div style={{ borderTop:`1px solid ${BORDER}`, padding:"18px 20px", background:"#FAFAFA" }}>
                    {(c.weightKg||c.bloodPressure||c.pulseBpm||c.temperatureC)&&(
                      <div style={{ marginBottom:16 }}>
                        <div style={{ fontSize:10, fontWeight:700, color:GRAY, letterSpacing:"0.06em", marginBottom:8 }}>VITALS</div>
                        <div style={{ display:"flex", gap:10, flexWrap:"wrap" }}>
                          {[{l:"Weight",v:c.weightKg?`${c.weightKg} kg`:null},{l:"Height",v:c.heightCm?`${c.heightCm} cm`:null},{l:"BP",v:c.bloodPressure},{l:"Pulse",v:c.pulseBpm?`${c.pulseBpm} bpm`:null},{l:"Temp",v:c.temperatureC?`${c.temperatureC}°C`:null},{l:"SpO₂",v:c.oxygenSatPct?`${c.oxygenSatPct}%`:null}].filter(x=>x.v).map(({l,v})=>(
                            <div key={l} style={{ background:"#fff", border:`1px solid ${BORDER}`, borderRadius:8, padding:"8px 14px", textAlign:"center" }}>
                              <div style={{ fontSize:10, color:GRAY, marginBottom:2 }}>{l}</div>
                              <div style={{ fontSize:14, fontWeight:700, color:"#0F172A" }}>{v}</div>
                            </div>
                          ))}
                          {c.weightKg&&c.heightCm&&<div style={{ background:"#fff", border:`1px solid ${BORDER}`, borderRadius:8, padding:"8px 14px", textAlign:"center" }}><div style={{ fontSize:10, color:GRAY, marginBottom:2 }}>BMI</div><div style={{ fontSize:14, fontWeight:700, color:"#0F172A" }}>{(c.weightKg/Math.pow(c.heightCm/100,2)).toFixed(1)}</div></div>}
                        </div>
                      </div>
                    )}
                    <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:12 }}>
                      {[{l:"History",v:c.history},{l:"Examination",v:c.examination},{l:"Diagnosis",v:c.diagnosis},{l:"Treatment plan",v:c.treatmentPlan}].filter(x=>x.v).map(({l,v})=>(
                        <div key={l}><div style={{ fontSize:10, fontWeight:700, color:GRAY, textTransform:"uppercase", letterSpacing:"0.06em", marginBottom:3 }}>{l}</div><div style={{ fontSize:13, color:"#0F172A", lineHeight:1.5 }}>{v}</div></div>
                      ))}
                    </div>
                    {c.icd10Codes?.length>0&&(<div style={{ marginTop:12 }}><div style={{ fontSize:10, fontWeight:700, color:GRAY, textTransform:"uppercase", letterSpacing:"0.06em", marginBottom:5 }}>ICD-10 codes</div><div style={{ display:"flex", gap:6, flexWrap:"wrap" }}>{c.icd10Codes.map((code:string)=>(<span key={code} style={{ background:"#EFF6FF", color:"#1D4ED8", padding:"2px 8px", borderRadius:4, fontSize:12, fontWeight:600, border:"1px solid #BFDBFE" }}>{code}</span>))}</div></div>)}
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
                <div key={rx.id} style={{ border:`1px solid ${BORDER}`, borderRadius:10, padding:"12px 16px", background:rx.dispensed?"#F0FDF4":"#fff" }}>
                  <div style={{ fontWeight:700, fontSize:14, color:"#0F172A", marginBottom:3 }}>{rx.medicationName}</div>
                  <div style={{ fontSize:12, color:GRAY }}>{[rx.dosage,rx.frequency,rx.duration].filter(Boolean).join(" · ")}{rx.quantity?` · Qty: ${rx.quantity}`:""}{rx.repeats>0?` · Repeats: ${rx.repeats}`:""}</div>
                  {rx.instructions&&<div style={{ fontSize:12, color:"#475569", marginTop:4, fontStyle:"italic" }}>{rx.instructions}</div>}
                  {rx.dispensed&&<span style={{ marginTop:6, display:"inline-block", background:"#DCFCE7", color:GREEN, padding:"1px 8px", borderRadius:20, fontSize:11, fontWeight:700 }}>DISPENSED</span>}
                </div>
              ))}
            </div>
          )}
          <div style={{ borderTop:`1px solid ${BORDER}`, paddingTop:16 }}>
            <div style={{ fontSize:13, fontWeight:700, color:"#0F172A", marginBottom:12 }}>Add prescription</div>
            <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:10 }}>
              <div style={{ gridColumn:"1/-1" }}><label style={lbl}>Medication *</label><input value={rxForm.medicationName} onChange={e=>setRxForm(f=>({...f,medicationName:e.target.value}))} placeholder="Amoxicillin 500mg" style={sinp}/></div>
              <div><label style={lbl}>Dosage</label><input value={rxForm.dosage} onChange={e=>setRxForm(f=>({...f,dosage:e.target.value}))} placeholder="500mg" style={sinp}/></div>
              <div><label style={lbl}>Frequency</label><input value={rxForm.frequency} onChange={e=>setRxForm(f=>({...f,frequency:e.target.value}))} placeholder="3× daily" style={sinp}/></div>
              <div><label style={lbl}>Duration</label><input value={rxForm.duration} onChange={e=>setRxForm(f=>({...f,duration:e.target.value}))} placeholder="7 days" style={sinp}/></div>
              <div><label style={lbl}>Qty</label><input type="number" value={rxForm.quantity} onChange={e=>setRxForm(f=>({...f,quantity:e.target.value}))} style={sinp}/></div>
              <div style={{ gridColumn:"1/-1" }}><label style={lbl}>Instructions</label><input value={rxForm.instructions} onChange={e=>setRxForm(f=>({...f,instructions:e.target.value}))} placeholder="Take with food" style={sinp}/></div>
            </div>
            <div style={{ display:"flex", justifyContent:"flex-end", marginTop:14 }}>
              <button onClick={()=>addRx.mutate({cid:showRx!,body:{...rxForm,quantity:parseInt(rxForm.quantity),repeats:parseInt(rxForm.repeats)}})} disabled={!rxForm.medicationName||addRx.isPending} style={btnPrimary}>{addRx.isPending?"Adding...":"Add prescription"}</button>
            </div>
          </div>
        </Modal>
      )}


      {/* ── Edit consultation modal ──────────────────────────────────────── */}
      {editingId && (
        <Modal title="Edit consultation" onClose={()=>setEditingId(null)} wide>
          <div style={{marginBottom:14,padding:"8px 12px",background:"#EFF6FF",border:"1px solid #BFDBFE",borderRadius:8,fontSize:12,color:"#1D4ED8"}}>
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
          <div style={{ marginBottom:20, padding:"16px 18px", background:"#F5F3FF", border:"1px solid #DDD6FE", borderRadius:12 }}>
            <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:10 }}>
              <div style={{ fontSize:13, fontWeight:700, color:PURPLE, display:"flex", alignItems:"center", gap:6 }}><Mic size={14}/> Voice-to-notes</div>
              <div style={{ display:"flex", gap:8 }}>
                {!isRecording
                  ? <button onClick={startRecording} style={{ display:"flex", alignItems:"center", gap:6, padding:"6px 12px", background:PURPLE, color:"#fff", border:"none", borderRadius:7, fontSize:12, fontWeight:600, cursor:"pointer" }}><Mic size={12}/> Start recording</button>
                  : <button onClick={stopRecording} style={{ display:"flex", alignItems:"center", gap:6, padding:"6px 12px", background:RED, color:"#fff", border:"none", borderRadius:7, fontSize:12, fontWeight:600, cursor:"pointer" }}><MicOff size={12}/> Stop recording</button>
                }
                {transcript&&<button onClick={extractSOAP} disabled={extracting} style={{ display:"flex", alignItems:"center", gap:6, padding:"6px 12px", background:extracting?"#E2E8F0":TEAL, color:extracting?GRAY:"#fff", border:"none", borderRadius:7, fontSize:12, fontWeight:600, cursor:extracting?"wait":"pointer" }}>{extracting?<><Loader size={12}/> Extracting...</>:<>✨ Extract SOAP</>}</button>}
                {transcript&&<button onClick={()=>setTranscript("")} style={{ padding:"6px 10px", background:"none", border:`1px solid ${BORDER}`, borderRadius:7, fontSize:12, cursor:"pointer", color:GRAY }}>Clear</button>}
              </div>
            </div>
            {isRecording&&<div style={{ display:"flex", alignItems:"center", gap:6, fontSize:12, color:RED, marginBottom:6 }}><span style={{ width:7, height:7, borderRadius:"50%", background:RED }}/> Recording — speak clearly</div>}
            <textarea value={transcript} onChange={e=>setTranscript(e.target.value)} rows={3}
              style={{ ...sinp, fontSize:12, color:"#475569", background:"rgba(255,255,255,0.7)", resize:"vertical" as const }}
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

// ── RUNNING BILL TAB ──────────────────────────────────────────────────────────

function RunningBillTab({ billLines, onRemove, patient }:
  {billLines:BillLine[]; onRemove:(id:string)=>void; patient:Patient}) {
  const [showAdd, setShowAdd]   = useState(false)
  const [addType, setAddType]   = useState<"PROCEDURE"|"MEDICINE"|"CONSUMABLE">("PROCEDURE")
  const [addForm, setAddForm]   = useState({description:"",tariffCode:"",nappiCode:"",quantity:"1",unitPrice:""})
  const [extraLines, setExtra]  = useState<BillLine[]>([])

  const allLines=[...billLines,...extraLines]
  const total=allLines.reduce((s,l)=>s+l.gross,0)

  const addLine=()=>{
    if (!addForm.description||!addForm.unitPrice) return
    const qty=parseFloat(addForm.quantity)||1, price=parseFloat(addForm.unitPrice)||0
    setExtra(l=>[...l,{id:crypto.randomUUID(),type:addType,description:addForm.description,tariffCode:addForm.tariffCode||undefined,nappiCode:addForm.nappiCode||undefined,quantity:qty,unitPrice:price,gross:qty*price}])
    setAddForm({description:"",tariffCode:"",nappiCode:"",quantity:"1",unitPrice:""}); setShowAdd(false)
  }

  const TYPE_CFG:Record<string,{color:string;bg:string;label:string}> = {
    CONSULTATION:{color:TEAL,bg:"#F0FDF4",label:"Consultation"},
    PROCEDURE:   {color:NAVY,bg:"#EFF6FF",label:"Procedure"},
    MEDICINE:    {color:GREEN,bg:"#DCFCE7",label:"Medicine"},
    CONSUMABLE:  {color:AMBER,bg:"#FFFBEB",label:"Consumable"},
  }

  return (
    <div>
      <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:20 }}>
        <div>
          <div style={{ fontSize:15, fontWeight:700, color:"#0F172A" }}>Running bill</div>
          <div style={{ fontSize:12, color:GRAY, marginTop:2 }}>Items accumulate as the consultation progresses.</div>
        </div>
        <button onClick={()=>setShowAdd(true)} style={btnPrimary}><Plus size={14}/> Add item</button>
      </div>

      {allLines.length===0 ? (
        <Empty icon={CreditCard} msg="No items yet">
          <div style={{ fontSize:13, color:GRAY, marginTop:4 }}>Items are added when you record a consultation, or manually here.</div>
        </Empty>
      ) : (
        <>
          <div style={{ border:`1px solid ${BORDER}`, borderRadius:12, overflow:"hidden", marginBottom:16 }}>
            <table style={{ width:"100%", borderCollapse:"collapse" }}>
              <thead>
                <tr style={{ background:LIGHT, borderBottom:`1px solid ${BORDER}` }}>
                  {["Type","Description","Code","Qty","Unit price","Total",""].map(h=>(
                    <th key={h} style={{ padding:"10px 14px", textAlign:"left", fontSize:11, fontWeight:700, color:GRAY, letterSpacing:"0.04em" }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {allLines.map((l,i)=>{
                  const cfg=TYPE_CFG[l.type]??TYPE_CFG.CONSULTATION
                  const isExtra=!billLines.find(b=>b.id===l.id)
                  return (
                    <tr key={l.id} style={{ borderBottom:i<allLines.length-1?`1px solid #F1F5F9`:"none" }}>
                      <td style={{ padding:"11px 14px" }}><span style={{ background:cfg.bg, color:cfg.color, padding:"2px 8px", borderRadius:20, fontSize:11, fontWeight:700 }}>{cfg.label}</span></td>
                      <td style={{ padding:"11px 14px", fontSize:13, fontWeight:600, color:"#0F172A" }}>{l.description}</td>
                      <td style={{ padding:"11px 14px", fontSize:12, color:GRAY }}>{l.tariffCode||l.nappiCode||"—"}</td>
                      <td style={{ padding:"11px 14px", fontSize:13, color:"#0F172A" }}>{l.quantity}</td>
                      <td style={{ padding:"11px 14px", fontSize:13, color:"#0F172A" }}>{fmtR(l.unitPrice)}</td>
                      <td style={{ padding:"11px 14px", fontSize:13, fontWeight:700, color:"#0F172A" }}>{fmtR(l.gross)}</td>
                      <td style={{ padding:"11px 14px" }}><button onClick={()=>isExtra?setExtra(e=>e.filter(x=>x.id!==l.id)):onRemove(l.id)} style={{ background:"none", border:"none", cursor:"pointer", color:RED, display:"flex" }}><X size={14}/></button></td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          <div style={{ display:"flex", justifyContent:"space-between", alignItems:"flex-end" }}>
            <div style={{ display:"flex", gap:10 }}>
              <button style={{ ...btnOutline, color:TEAL, borderColor:TEAL }}>Generate claim (medical aid)</button>
              <button style={{ ...btnOutline, color:NAVY, borderColor:NAVY }}>Record payment</button>
            </div>
            <div style={{ padding:"16px 24px", background:NAVY, borderRadius:12, textAlign:"right" as const }}>
              <div style={{ fontSize:11, fontWeight:700, color:"rgba(255,255,255,0.6)", textTransform:"uppercase", letterSpacing:"0.06em" }}>Total</div>
              <div style={{ fontSize:26, fontWeight:800, color:"#fff" }}>{fmtR(total)}</div>
              <div style={{ fontSize:10, color:"rgba(255,255,255,0.4)" }}>excl. VAT</div>
            </div>
          </div>
        </>
      )}

      {showAdd&&(
        <Modal title="Add billing item" onClose={()=>setShowAdd(false)}>
          <div style={{ display:"flex", flexDirection:"column", gap:14 }}>
            <div>
              <label style={lbl}>Item type</label>
              <div style={{ display:"flex", gap:6 }}>
                {(["PROCEDURE","MEDICINE","CONSUMABLE"] as const).map(t=>(
                  <button key={t} onClick={()=>setAddType(t)} style={{ padding:"6px 14px", borderRadius:8, border:`2px solid ${addType===t?NAVY:BORDER}`, background:addType===t?"#EFF6FF":"#fff", color:addType===t?NAVY:GRAY, fontSize:12, fontWeight:addType===t?600:400, cursor:"pointer" }}>
                    {t.charAt(0)+t.slice(1).toLowerCase()}
                  </button>
                ))}
              </div>
            </div>
            <div><label style={lbl}>Description *</label><input value={addForm.description} onChange={e=>setAddForm(f=>({...f,description:e.target.value}))} placeholder="e.g. Wound suture — simple" style={sinp} autoFocus/></div>
            <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:12 }}>
              {addType==="PROCEDURE"&&<div><label style={lbl}>Tariff code</label><input value={addForm.tariffCode} onChange={e=>setAddForm(f=>({...f,tariffCode:e.target.value}))} placeholder="0007" style={sinp}/></div>}
              {addType==="MEDICINE"&&<div><label style={lbl}>NAPPI code</label><input value={addForm.nappiCode} onChange={e=>setAddForm(f=>({...f,nappiCode:e.target.value}))} placeholder="701408001" style={sinp}/></div>}
              <div><label style={lbl}>Quantity</label><input type="number" step="0.5" value={addForm.quantity} onChange={e=>setAddForm(f=>({...f,quantity:e.target.value}))} style={sinp}/></div>
              <div><label style={lbl}>Unit price (R) *</label><input type="number" step="0.01" value={addForm.unitPrice} onChange={e=>setAddForm(f=>({...f,unitPrice:e.target.value}))} placeholder="0.00" style={sinp}/></div>
            </div>
            {addForm.quantity&&addForm.unitPrice&&<div style={{ padding:"8px 12px", background:"#F0FDF4", border:"1px solid #86EFAC", borderRadius:8, fontSize:13, color:GREEN, fontWeight:600 }}>Line total: {fmtR(parseFloat(addForm.quantity)*parseFloat(addForm.unitPrice))}</div>}
          </div>
          <ModalFooter onCancel={()=>setShowAdd(false)} onConfirm={addLine} confirmLabel="Add to bill"/>
        </Modal>
      )}
    </div>
  )
}

// ── PRESCRIPTIONS TAB ─────────────────────────────────────────────────────────

function PrescriptionsTab({ patient, consultations }:
  {patient:Patient; consultations:Consultation[]}) {
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
            <button key={f} onClick={()=>setFilter(f)} style={{ padding:"6px 14px", borderRadius:20, border:"none", fontSize:12, fontWeight:filter===f?600:400, background:filter===f?NAVY:"#F1F5F9", color:filter===f?"#fff":GRAY, cursor:"pointer" }}>
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
            <div key={rx.id} style={{ border:`1px solid ${rx.dispensed?"#86EFAC":BORDER}`, borderLeft:`4px solid ${rx.dispensed?GREEN:TEAL}`, borderRadius:10, padding:"14px 18px", background:rx.dispensed?"#F0FDF4":"#fff" }}>
              <div style={{ display:"flex", justifyContent:"space-between", alignItems:"flex-start" }}>
                <div style={{ flex:1 }}>
                  <div style={{ fontWeight:700, fontSize:15, color:"#0F172A", marginBottom:4 }}>{rx.medicationName}</div>
                  <div style={{ fontSize:13, color:GRAY, marginBottom:4 }}>{[rx.dosage,rx.frequency,rx.duration].filter(Boolean).join(" · ")}{rx.quantity?` · Qty: ${rx.quantity}`:""}{rx.repeats>0?` · Repeats: ${rx.repeats}`:""}</div>
                  {rx.instructions&&<div style={{ fontSize:12, color:"#475569", fontStyle:"italic", marginBottom:4 }}>{rx.instructions}</div>}
                  <div style={{ fontSize:11, color:GRAY }}>Prescribed {fmtDT(rx.prescribedAt)}{rx.practitionerName&&` · Dr. ${rx.practitionerName}`}</div>
                </div>
                <div style={{ flexShrink:0, marginLeft:12 }}>
                  {rx.dispensed?<span style={{ background:"#DCFCE7",color:GREEN,padding:"3px 10px",borderRadius:20,fontSize:12,fontWeight:700 }}>DISPENSED</span>:<span style={{ background:"#FFF7ED",color:AMBER,padding:"3px 10px",borderRadius:20,fontSize:12,fontWeight:700 }}>ACTIVE</span>}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

// ── LABS TAB ──────────────────────────────────────────────────────────────────

function LabsTab({ patient }:{patient:Patient}) {
  const qc=useQueryClient()
  const [showUpload, setShowUpload]=useState(false)
  const [uploadForm, setUploadForm]=useState({source:"MANUAL",labReference:"",pdfFilename:"",notes:""})
  const [uploading, setUploading]=useState(false)
  const fileRef=useRef<HTMLInputElement>(null)

  const { data: labs=[], isLoading } = useQuery({
    queryKey:["pf-labs",patient.id],
    queryFn: async ()=>unwrap(await apiClient.get(`/api/v1/clinic/lab/patients/${patient.id}/results`)),
  })
  const STATUS_LAB:Record<string,{color:string;bg:string}> = {
    UNREVIEWED:{color:RED,bg:"#FEF2F2"}, REVIEWED:{color:AMBER,bg:"#FFFBEB"},
    FILED:{color:GREEN,bg:"#DCFCE7"},   REJECTED:{color:GRAY,bg:LIGHT},
  }

  return (
    <div>
      <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:20 }}>
        <div style={{ fontSize:15, fontWeight:700, color:"#0F172A" }}>Lab results</div>
        <button onClick={()=>setShowUpload(true)} style={btnPrimary}><Upload size={14}/> Upload result</button>
      </div>
      {isLoading?<div style={{textAlign:"center",padding:40,color:GRAY}}>Loading...</div>
      :(labs as any[]).length===0?<Empty icon={FlaskConical} msg="No lab results on file"><div style={{fontSize:13,color:GRAY,marginTop:4}}>Upload Ampath, Lancet, Pathcare results to attach them to this patient's record.</div></Empty>:(
        <div style={{ display:"flex", flexDirection:"column", gap:10 }}>
          {(labs as any[]).map((lab:any)=>{
            const cfg=STATUS_LAB[lab.status]??STATUS_LAB.UNREVIEWED
            return (
              <div key={lab.id} style={{ border:`1px solid ${BORDER}`, borderLeft:`4px solid ${cfg.color}`, borderRadius:10, padding:"14px 18px", background:"#fff" }}>
                <div style={{ display:"flex", justifyContent:"space-between", alignItems:"flex-start" }}>
                  <div>
                    <div style={{ fontWeight:700, fontSize:14, color:"#0F172A", marginBottom:4 }}>{lab.pdfFilename||"Lab result"}</div>
                    <div style={{ fontSize:12, color:GRAY }}>{lab.source} · Received {fmtDT(lab.receivedAt)}{lab.labReference&&` · Ref: ${lab.labReference}`}</div>
                    {lab.interpretation&&<div style={{ marginTop:8, padding:"8px 12px", background:"#F0FDF4", border:"1px solid #86EFAC", borderRadius:8, fontSize:12, color:"#0F172A" }}><span style={{ fontWeight:700, color:TEAL }}>AI interpretation: </span>{lab.interpretation}</div>}
                  </div>
                  <div style={{ display:"flex", flexDirection:"column", alignItems:"flex-end", gap:6, flexShrink:0, marginLeft:12 }}>
                    <span style={{ background:cfg.bg, color:cfg.color, padding:"2px 8px", borderRadius:20, fontSize:11, fontWeight:700 }}>{lab.status}</span>
                    {lab.pdfUrl&&<button onClick={()=>downloadPdf(lab.pdfUrl,lab.pdfFilename||"result.pdf")} style={{ display:"flex", alignItems:"center", gap:4, padding:"4px 10px", background:LIGHT, color:NAVY, border:`1px solid ${BORDER}`, borderRadius:6, fontSize:12, cursor:"pointer" }}><Download size={11}/> PDF</button>}
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      )}
      {showUpload&&(
        <Modal title="Upload lab result" onClose={()=>setShowUpload(false)}>
          <div style={{ display:"flex", flexDirection:"column", gap:14 }}>
            <div><label style={lbl}>Lab / source</label><select value={uploadForm.source} onChange={e=>setUploadForm(f=>({...f,source:e.target.value}))} style={sinp}>{["AMPATH","LANCET","PATHCARE","VERMAAK","EMAIL","MANUAL"].map(s=><option key={s} value={s}>{s}</option>)}</select></div>
            <div><label style={lbl}>Lab reference number</label><input value={uploadForm.labReference} onChange={e=>setUploadForm(f=>({...f,labReference:e.target.value}))} placeholder="e.g. AMP-2026-001234" style={sinp}/></div>
            <div>
              <label style={lbl}>PDF file</label>
              <div style={{ border:`2px dashed ${BORDER}`, borderRadius:10, padding:"24px", textAlign:"center", cursor:"pointer", background:LIGHT }} onClick={()=>fileRef.current?.click()}>
                <Upload size={24} color={GRAY} style={{ marginBottom:8 }}/>
                <div style={{ fontSize:13, color:GRAY }}>Click to select PDF</div>
                <div style={{ fontSize:11, color:"#94A3B8", marginTop:4 }}>PDF only · max 10MB</div>
                <input ref={fileRef} type="file" accept=".pdf" style={{ display:"none" }} onChange={e=>{ const file=e.target.files?.[0]; if (file) setUploadForm(f=>({...f,pdfFilename:file.name})) }}/>
              </div>
              {uploadForm.pdfFilename&&<div style={{ marginTop:6, fontSize:13, color:GREEN, display:"flex", alignItems:"center", gap:6 }}><CheckCircle size={13}/> {uploadForm.pdfFilename}</div>}
            </div>
          </div>
          <ModalFooter onCancel={()=>setShowUpload(false)}
            onConfirm={async()=>{
              setUploading(true)
              try { await apiClient.post(`/api/v1/clinic/lab/results`,{source:uploadForm.source,labReference:uploadForm.labReference||null,pdfFilename:uploadForm.pdfFilename||null,patientNameRaw:patient.fullName}); qc.invalidateQueries({queryKey:["pf-labs",patient.id]}); setShowUpload(false) }
              finally { setUploading(false) }
            }}
            confirmLabel={uploading?"Uploading...":"Upload result"} loading={uploading}/>
        </Modal>
      )}
    </div>
  )
}

// ── DOCUMENTS TAB ─────────────────────────────────────────────────────────────

function DocumentsTab({ patient, consultations }:
  {patient:Patient; consultations:Consultation[]}) {
  const [showCert, setShowCert]=useState(false)
  const [certForm, setCertForm]=useState({consultationId:"",unfitFrom:"",unfitTo:"",notes:""})
  const [loading, setLoading]=useState(false)
  const docs=[
    {icon:FileText, label:"Medical certificate", desc:"Generate sick note — select consultation + dates", color:PURPLE, action:()=>setShowCert(true)},
    {icon:Pill,     label:"Prescription PDF",    desc:"Download prescription from latest consultation",  color:TEAL,   action:()=>{ if (consultations[0]) downloadPdf(`/api/v1/clinic/consultations/${consultations[0].id}/prescription-pdf`,`rx-${patient.id}.pdf`) }},
    {icon:Download, label:"Patient summary",     desc:"Full record export for referral or transfer",     color:NAVY,   action:()=>{}},
  ]
  return (
    <div>
      <div style={{ display:"grid", gridTemplateColumns:"repeat(3,1fr)", gap:14, marginBottom:24 }}>
        {docs.map(d=>(
          <button key={d.label} onClick={d.action} style={{ display:"flex", alignItems:"flex-start", gap:14, padding:"18px 20px", background:"#fff", border:`1px solid ${BORDER}`, borderRadius:12, cursor:"pointer", textAlign:"left" as const }}
            onMouseEnter={e=>{ (e.currentTarget as HTMLButtonElement).style.borderColor=d.color; (e.currentTarget as HTMLButtonElement).style.background=LIGHT }}
            onMouseLeave={e=>{ (e.currentTarget as HTMLButtonElement).style.borderColor=BORDER; (e.currentTarget as HTMLButtonElement).style.background="#fff" }}>
            <div style={{ width:40, height:40, borderRadius:10, background:`${d.color}14`, display:"flex", alignItems:"center", justifyContent:"center", flexShrink:0 }}><d.icon size={18} color={d.color}/></div>
            <div><div style={{ fontSize:14, fontWeight:700, color:"#0F172A", marginBottom:3 }}>{d.label}</div><div style={{ fontSize:12, color:GRAY }}>{d.desc}</div></div>
          </button>
        ))}
      </div>
      {showCert&&(
        <Modal title="Medical certificate" onClose={()=>setShowCert(false)}>
          <div style={{ display:"flex", flexDirection:"column", gap:14 }}>
            <div><label style={lbl}>Consultation *</label><select value={certForm.consultationId} onChange={e=>setCertForm(f=>({...f,consultationId:e.target.value}))} style={sinp}><option value="">Select consultation...</option>{consultations.map(c=><option key={c.id} value={c.id}>{fmtDT(c.consultedAt)} — {c.chiefComplaint}</option>)}</select></div>
            <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:12 }}>
              <div><label style={lbl}>Unfit from</label><input type="date" value={certForm.unfitFrom} onChange={e=>setCertForm(f=>({...f,unfitFrom:e.target.value}))} style={sinp}/></div>
              <div><label style={lbl}>Unfit until</label><input type="date" value={certForm.unfitTo} onChange={e=>setCertForm(f=>({...f,unfitTo:e.target.value}))} style={sinp}/></div>
            </div>
            <div><label style={lbl}>Notes</label><textarea rows={2} value={certForm.notes} onChange={e=>setCertForm(f=>({...f,notes:e.target.value}))} placeholder="Additional notes..." style={{ ...sinp, resize:"vertical" as const }}/></div>
          </div>
          <ModalFooter onCancel={()=>setShowCert(false)}
            onConfirm={async()=>{ if (!certForm.consultationId) return; setLoading(true); try { const p=new URLSearchParams(); if(certForm.unfitFrom)p.set("unfitFrom",certForm.unfitFrom); if(certForm.unfitTo)p.set("unfitTo",certForm.unfitTo); if(certForm.notes)p.set("notes",certForm.notes); await downloadPdf(`/api/v1/clinic/consultations/${certForm.consultationId}/medical-certificate?${p}`,`med-cert-${patient.id}.pdf`); setShowCert(false) } finally { setLoading(false) } }}
            confirmLabel={loading?"Generating...":"Download certificate"} loading={loading}/>
        </Modal>
      )}
    </div>
  )
}

// ── HISTORY TAB ───────────────────────────────────────────────────────────────

function HistoryTab({ appointments, consultations }:
  {appointments:Appointment[]; consultations:Consultation[]}) {
  const timeline=[
    ...appointments.map((a:any)=>({...a,_type:"appt",_date:a.scheduledAt})),
    ...consultations.map((c:any)=>({...c,_type:"consult",_date:c.consultedAt})),
  ].sort((a,b)=>b._date.localeCompare(a._date))

  return timeline.length===0?<Empty icon={Clock} msg="No visit history yet"/>:(
    <div>
      <div style={{ fontSize:13, color:GRAY, marginBottom:20 }}>{timeline.length} events — appointments and consultations</div>
      <div style={{ display:"flex", flexDirection:"column" }}>
        {timeline.map((item:any,i:number)=>{
          const isAppt=item._type==="appt"; const s=isAppt?(STATUS_CFG[item.status]??STATUS_CFG.SCHEDULED):null
          return (
            <div key={item.id} style={{ display:"flex", gap:16, alignItems:"flex-start" }}>
              <div style={{ display:"flex", flexDirection:"column", alignItems:"center", flexShrink:0 }}>
                <div style={{ width:36, height:36, borderRadius:"50%", background:isAppt?"#EFF6FF":"#F0FDF4", border:`2px solid ${isAppt?"#BFDBFE":"#86EFAC"}`, display:"flex", alignItems:"center", justifyContent:"center" }}>
                  {isAppt?<Calendar size={14} color="#1D4ED8"/>:<Stethoscope size={14} color={TEAL}/>}
                </div>
                {i<timeline.length-1&&<div style={{ width:2, height:32, background:BORDER, marginTop:4 }}/>}
              </div>
              <div style={{ flex:1, paddingBottom:20 }}>
                <div style={{ padding:"12px 16px", background:"#fff", border:`1px solid ${BORDER}`, borderRadius:10 }}>
                  <div style={{ display:"flex", justifyContent:"space-between", alignItems:"flex-start" }}>
                    <div>
                      <div style={{ fontWeight:600, fontSize:13, color:"#0F172A", marginBottom:3 }}>{isAppt?(item.appointmentType?.replace("_"," ")||"Appointment"):(item.chiefComplaint||"Consultation")}</div>
                      <div style={{ fontSize:12, color:GRAY }}>{isAppt&&item.practitionerName&&`Dr. ${item.practitionerName} · `}{isAppt&&(item.reason||"")}{!isAppt&&item.practitionerName&&`Dr. ${item.practitionerName}`}{!isAppt&&item.diagnosis&&` · Dx: ${item.diagnosis}`}</div>
                      {!isAppt&&item.icd10Codes?.length>0&&<div style={{ display:"flex", gap:4, marginTop:4, flexWrap:"wrap" }}>{item.icd10Codes.map((c:string)=>(<span key={c} style={{ background:"#EFF6FF", color:"#1D4ED8", padding:"1px 6px", borderRadius:4, fontSize:11, fontWeight:600 }}>{c}</span>))}</div>}
                    </div>
                    <div style={{ textAlign:"right", flexShrink:0, marginLeft:12 }}>
                      <div style={{ fontSize:12, color:GRAY }}>{fmtDT(item._date)}{isAppt&&` · ${fmtTime(item._date)}`}</div>
                      {isAppt&&s&&<span style={{ fontSize:10, fontWeight:700, background:s.bg, color:s.color, padding:"1px 7px", borderRadius:20, marginTop:3, display:"inline-block" }}>{s.label}</span>}
                      {!isAppt&&item.followUpDays&&<span style={{ fontSize:10, color:AMBER, background:"#FFFBEB", padding:"1px 7px", borderRadius:20, marginTop:3, display:"inline-block" }}>F/U {item.followUpDays}d</span>}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}

// ── Shared helpers ─────────────────────────────────────────────────────────────

function Modal({ title, onClose, children, wide }:{title:string;onClose:()=>void;children:React.ReactNode;wide?:boolean}) {
  return (
    <div style={{ position:"fixed", inset:0, background:"rgba(15,23,42,0.55)", display:"flex", alignItems:"center", justifyContent:"center", zIndex:1200, backdropFilter:"blur(3px)" }}>
      <div style={{ background:"#fff", borderRadius:16, padding:28, width:wide?740:500, maxHeight:"92vh", overflowY:"auto", boxShadow:"0 24px 64px rgba(0,0,0,0.22)" }}>
        <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:20 }}>
          <h3 style={{ margin:0, fontSize:17, fontWeight:700, color:"#0F172A" }}>{title}</h3>
          <button onClick={onClose} style={{ background:"none", border:"none", cursor:"pointer", color:GRAY, display:"flex" }}><X size={20}/></button>
        </div>
        {children}
      </div>
    </div>
  )
}
function ModalFooter({ onCancel, onConfirm, confirmLabel, loading }:{onCancel:()=>void;onConfirm:()=>void;confirmLabel:string;loading?:boolean}) {
  return <div style={{ display:"flex", gap:10, justifyContent:"flex-end", marginTop:20 }}><button onClick={onCancel} style={btnCancel}>Cancel</button><button onClick={onConfirm} disabled={loading} style={btnPrimary}>{loading?<><Loader size={13}/> {confirmLabel}</>:confirmLabel}</button></div>
}
function Empty({ icon:Icon, msg, children }:{icon:React.ElementType;msg:string;children?:React.ReactNode}) {
  return <div style={{ textAlign:"center", padding:"60px 20px", color:GRAY, border:`1px dashed ${BORDER}`, borderRadius:12 }}><Icon size={36} style={{ marginBottom:12, opacity:0.4 }}/><div style={{ fontWeight:600, color:"#475569", fontSize:15 }}>{msg}</div>{children}</div>
}
function ErrBox({ msg }:{msg:string}) {
  return <div style={{ padding:"10px 12px", background:"#FEF2F2", border:"1px solid #FECACA", borderRadius:8, fontSize:13, color:RED, display:"flex", alignItems:"center", gap:8 }}><AlertCircle size={14}/>{msg}</div>
}
function FSect({ title, children }:{title:string;children:React.ReactNode}) {
  return <div style={{ marginBottom:20 }}><div style={{ fontSize:10, fontWeight:700, color:GRAY, letterSpacing:"0.07em", textTransform:"uppercase", marginBottom:12, paddingBottom:8, borderBottom:`1px solid ${BORDER}` }}>{title}</div>{children}</div>
}

const lbl:React.CSSProperties     = {display:"block",fontSize:13,fontWeight:600,color:"#374151",marginBottom:5}
const sinp:React.CSSProperties    = {width:"100%",padding:"9px 12px",boxSizing:"border-box" as const,border:`1.5px solid ${BORDER}`,borderRadius:8,fontSize:14,outline:"none",background:"#fff"}
const btnPrimary:React.CSSProperties = {display:"flex",alignItems:"center",gap:7,background:NAVY,color:"#fff",border:"none",borderRadius:9,padding:"9px 20px",fontSize:13,fontWeight:600,cursor:"pointer"}
const btnCancel:React.CSSProperties  = {padding:"9px 18px",border:`1px solid ${BORDER}`,borderRadius:9,background:"#fff",fontSize:13,cursor:"pointer",color:"#374151"}
const btnOutline:React.CSSProperties = {padding:"9px 18px",border:"1.5px solid",borderRadius:9,background:"#fff",fontSize:13,fontWeight:600,cursor:"pointer"}
