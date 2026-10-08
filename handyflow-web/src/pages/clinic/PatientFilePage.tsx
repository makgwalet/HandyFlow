// src/pages/clinic/PatientFilePage.tsx
// Full-page patient file — 8 tabs + family management + account lifecycle
import { myPractitionerId } from "./currentPractitioner"
import { useAuthStore } from "../../store/auth.store"
import { useDialogs } from "./dialogs"
import { useState } from "react"
import { useNavigate } from "react-router-dom"
import { workspacePath } from "./workspace"
import { LabsTabEnhanced } from "./LabsTab"
import ConsentTab from "./ConsentTab"
import TimelineTab from "./TimelineTab"
import { PatientAlertBanner } from "./PatientNotes"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import PatientAccountTab from "./PatientAccountTab"
import PrescriptionsTab from "./PatientRxTab"
import DocumentsTab from "./PatientDocumentsTab"
import type { Appointment, Consultation, Patient, Practitioner } from "./patientFile.shared"
import { ACCOUNT_CFG, AMBER, BORDER, GRAY, GREEN_TEXT, LIGHT, NAVY, PURPLE_TEXT, RED_TEXT, TEAL, saId, unwrap } from "./patientFile.shared"
import { AlertCircle, Archive, Calendar, Clock, CreditCard, FileText, FlaskConical, Heart, Link, MoreVertical, Phone, Pill, ShieldCheck, Stethoscope, User, UserCheck, UserX, Users } from "lucide-react"
import OverviewTab from "./PatientOverviewTab"
import AppointmentsTab from "./PatientAppointmentsTab"
import PatientVisitsTab from "./PatientVisitsTab"
import RestrictedRecordGate from "./RestrictedRecordGate"
import GrowthTab from "./GrowthTab"
import { usePermission } from "../../hooks/usePermission"

// ── Main component ─────────────────────────────────────────────────────────────

type TabId = "growth"|"overview"|"appointments"|"consultation"|"account"|"rx"|"labs"|"documents"|"history"|"consent"

interface Props {
  patient: Patient; onClose: () => void; onNavigate: (tab:any)=>void; onOpenPatient?: (p:Patient)=>void
}

export default function PatientFilePage({ patient, onClose, onNavigate, onOpenPatient }: Props) {
  const qc = useQueryClient()
  const { prompt, dialogs } = useDialogs()
  const navigate = useNavigate()
  const userEmail = useAuthStore(st => st.user?.email)
  const canGrowth = usePermission("CLINIC_GROWTH_READ")
  const [activeTab, setActiveTab] = useState<TabId>("overview")
  const [showActions, setShowActions] = useState(false)
  // A consultation is a full-screen workspace of its own; starting one leaves the file and opens it.
  const startSession = (appt: { id: string }) => navigate(workspacePath(appt.id))
  const pid = patient.id
  const idInfo = saId(patient.idNumber)

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

  const pendingAppts = (appointments as Appointment[]).filter(a=>["SCHEDULED","CONFIRMED","CHECKED_IN","TRIAGED","IN_PROGRESS"].includes(a.status)).length

  const TABS: {id:TabId;label:string;icon:React.ElementType;badge?:number}[] = [
    {id:"overview",     label:"Overview",     icon:User},
    {id:"appointments", label:"Appointments", icon:Calendar,   badge:pendingAppts||undefined},
    {id:"consultation", label:"Visits",       icon:Stethoscope},
    {id:"account", label:"Account", icon:CreditCard},
    {id:"rx",           label:"Prescriptions",icon:Pill},
    {id:"labs",         label:"Lab results",  icon:FlaskConical},
    ...(canGrowth ? [{id:"growth" as TabId, label:"Growth", icon:Heart}] : []),
    {id:"documents",    label:"Documents",    icon:FileText},
    {id:"history",      label:"Timeline",      icon:Clock},
    {id:"consent",      label:"Consent",      icon:ShieldCheck},
  ]

  const acctCfg = ACCOUNT_CFG[patient.accountType]??ACCOUNT_CFG.INDIVIDUAL
  const isArchived = !!patient.archivedAt

  return (
    <div style={{ fontFamily:"'Inter',system-ui,sans-serif", minHeight:600 }}>
      {dialogs}
      {/* ── Patient banner ──────────────────────────────────────────────── */}
      <div style={{ background:`linear-gradient(135deg,${NAVY} 0%,var(--hf-primary-deep) 100%)`,
        borderRadius:12, marginBottom:16, padding:"14px 20px 0", overflow:"hidden" }}>
        <div style={{ display:"flex", justifyContent:"space-between", alignItems:"flex-start", marginBottom:8 }}>
          <div style={{ display:"flex", alignItems:"center", gap:16 }}>
            <div style={{ width:52, height:52, borderRadius:"50%", background:"rgba(255,255,255,0.15)",
              display:"flex", alignItems:"center", justifyContent:"center",
              fontSize:20, fontWeight:800, color:"var(--hf-text-on-solid)", flexShrink:0 }}>
              {patient.firstName?.[0]}{patient.lastName?.[0]}
            </div>
            <div>
              <div style={{ display:"flex", alignItems:"center", gap:10, marginBottom:4 }}>
                <h2 style={{ margin:0, fontSize:22, fontWeight:800, color:"var(--hf-text-on-solid)" }}>{patient.fullName}</h2>
                <span style={{ background:`color-mix(in srgb, ${acctCfg.bg} 15%, transparent)`, color:acctCfg.bg,
                  padding:"2px 10px", borderRadius:20, fontSize:11, fontWeight:700,
                  border:`1px solid color-mix(in srgb, ${acctCfg.bg} 31%, transparent)` }}>
                  {acctCfg.label}
                </span>
                {isArchived && <span style={{ background:"rgba(255,255,255,0.15)", color:"rgba(255,255,255,0.7)", padding:"2px 8px", borderRadius:20, fontSize:11 }}>ARCHIVED</span>}
              </div>
              <div style={{ display:"flex", gap:12, flexWrap:"wrap", fontSize:13, color:"rgba(255,255,255,0.7)" }}>
                {idInfo && <span>{idInfo.dob} · {idInfo.age} yrs · {idInfo.gender}</span>}
                {patient.bloodType && <span style={{ background:"rgba(220,38,38,0.3)", color:"var(--hf-danger-on-brand)", padding:"1px 8px", borderRadius:20, fontSize:12, fontWeight:700 }}>{patient.bloodType}</span>}
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
                  <span style={{ background:"rgba(220,38,38,0.25)", color:"var(--hf-danger-on-brand)",
                    padding:"2px 8px", borderRadius:20, fontSize:11, fontWeight:600,
                    display:"flex", alignItems:"center", gap:4 }}>
                    <AlertCircle size={10}/> ⚠ {patient.allergies.length} allerg{patient.allergies.length===1?"y":"ies"}
                  </span>
                )}
                {patient.chronicConditions?.length > 0 && (
                  <span style={{ background:"rgba(217,119,6,0.25)", color:"var(--hf-warning-on-brand)",
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
                cursor:"pointer", color:"var(--hf-text-on-solid)", padding:"8px 10px", display:"flex", alignItems:"center", gap:6, fontSize:13 }}>
              <MoreVertical size={16}/> Actions
            </button>
            {showActions && (
              <div style={{ position:"absolute", right:0, top:"100%", marginTop:6,
                background:"var(--hf-surface)", borderRadius:10, border:`1px solid ${BORDER}`,
                boxShadow:"0 8px 32px rgba(0,0,0,0.14)", minWidth:220, zIndex:50, overflow:"hidden" }}>
                {/* Convert individual → principal */}
                {patient.accountType==="INDIVIDUAL" && (
                  <ActionItem icon={Users} label="Convert to family account"
                    color={PURPLE_TEXT} onClick={()=>convertToFamily.mutate()}
                    hint="Promotes patient to principal — add dependants after"/>
                )}
                {/* Deactivate / reactivate */}
                {patient.active ? (
                  <ActionItem icon={UserX} label="Deactivate account"
                    color={AMBER} onClick={()=>deactivate.mutate()}
                    hint="Patient hidden from active list"/>
                ) : (
                  <ActionItem icon={UserCheck} label="Reactivate account"
                    color={GREEN_TEXT} onClick={()=>reactivate.mutate()}
                    hint="Restore to active status"/>
                )}
                {/* Archive */}
                {!isArchived && (
                  <ActionItem icon={Archive} label="Archive record"
                    color={RED_TEXT} onClick={async ()=>{
                      setShowActions(false)
                      const reason = await prompt({ title: "Archive this record", body: "HPCSA records are retained for 6 years, so the record is archived, never deleted.", label: "Reason", confirmLabel: "Archive", danger: true })
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
        <div style={{ display:"flex", gap:1, overflowX:"auto", marginTop:0 }}>
          {TABS.map(t=>{
            const Icon=t.icon; const active=activeTab===t.id
            return (
              <button key={t.id} onClick={()=>setActiveTab(t.id)}
                style={{ display:"flex", alignItems:"center", gap:6, padding:"10px 14px",
                  background:active?"rgba(255,255,255,0.12)":"transparent", border:"none",
                  borderBottom:active?"2px solid var(--hf-accent)":"2px solid transparent",
                  color:active?"var(--hf-text-on-solid)":"rgba(255,255,255,0.6)",
                  fontWeight:active?600:400, fontSize:13, cursor:"pointer",
                  whiteSpace:"nowrap", marginBottom:-1 }}>
                <Icon size={13}/>{t.label}
                {t.badge ? (
                  <span style={{ background:TEAL, color:"var(--hf-text-on-solid)", borderRadius:"50%",
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

      {/* ── Tab content ─────────────────────────────────────────────────── */}
      <RestrictedRecordGate patientId={patient.id}>
      <PatientAlertBanner patientId={patient.id}/>
      {activeTab==="overview"     && <OverviewTab patient={patient} idInfo={idInfo} familyMembers={familyMembers as Patient[]} onOpenPatient={onOpenPatient} qc={qc}
        appointments={appointments as any[]} consultations={consultations as any[]} defaultPractitionerId={myPractitionerId(practitioners as any[], userEmail)}
        onStartSession={startSession} onOpenTab={setActiveTab}/>}
      {activeTab==="appointments" && <AppointmentsTab patient={patient} appointments={appointments as Appointment[]} practitioners={practitioners as Practitioner[]} qc={qc} onStartSession={startSession}/>}
      {activeTab==="consultation" && <PatientVisitsTab patientId={patient.id} appointments={appointments as any[]} defaultPractitionerId={myPractitionerId(practitioners as any[], userEmail)} onStartSession={startSession}/>}
      {activeTab==="account" && <PatientAccountTab patientId={pid}/>}
      {activeTab==="rx"           && <PrescriptionsTab patient={patient} consultations={consultations as Consultation[]}/>}
      {activeTab==="labs"         && <LabsTabEnhanced patient={patient}/>}
      {activeTab==="growth"       && <GrowthTab patientId={patient.id}/>}
      {activeTab==="documents"    && <DocumentsTab patient={patient} consultations={consultations as Consultation[]}/>}
      {activeTab==="history"      && <TimelineTab patientId={patient.id}/>}
      {activeTab==="consent"      && <ConsentTab patient={patient}/>}
      </RestrictedRecordGate>
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
      <Icon size={15} style={{ color, marginTop:2, flexShrink:0 }}/>
      <div>
        <div style={{ fontSize:13, fontWeight:600, color:"var(--hf-text)" }}>{label}</div>
        <div style={{ fontSize:11, color:GRAY, marginTop:1 }}>{hint}</div>
      </div>
    </button>
  )
}

