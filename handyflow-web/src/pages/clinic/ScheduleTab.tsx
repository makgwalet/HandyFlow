// src/pages/clinic/ScheduleTab.tsx
// Day / Week calendar view per doctor — click slot to book appointment
import { useState, useEffect, useRef } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  ChevronLeft, ChevronRight, Plus, X, Calendar, Clock,
  User, CheckCircle, PlayCircle, XCircle, AlertCircle, Mail, Video,
} from "lucide-react"

// ── Types ─────────────────────────────────────────────────────────────────────

interface Appointment {
  id: string; patientId: string; patientName: string
  practitionerId: string; practitionerName: string
  scheduledAt: string; durationMinutes: number
  appointmentType: string; status: string; reason: string
  videoRoomUrl?: string
}
interface Practitioner { id: string; fullName: string; specialty: string }
interface Patient { id: string; fullName: string }

// ── Design tokens ─────────────────────────────────────────────────────────────

const NAVY="#1B3A6B"; const TEAL="#0D9488"; const RED="#DC2626"
const GREEN="#166534"; const AMBER="#D97706"; const PURPLE="#7C3AED"
const GRAY="#64748B"; const BORDER="#E2E8F0"; const LIGHT="#F8FAFC"

const STATUS_CFG: Record<string,{color:string;bg:string;border:string;label:string}> = {
  SCHEDULED:   {color:"#1D4ED8",bg:"#EFF6FF",border:"#BFDBFE",label:"Scheduled"},
  CONFIRMED:   {color:PURPLE,   bg:"#F5F3FF",border:"#DDD6FE",label:"Confirmed"},
  IN_PROGRESS: {color:AMBER,    bg:"#FFFBEB",border:"#FDE68A",label:"In Progress"},
  COMPLETED:   {color:GREEN,    bg:"#DCFCE7",border:"#86EFAC",label:"Completed"},
  CANCELLED:   {color:RED,      bg:"#FEF2F2",border:"#FECACA",label:"Cancelled"},
  NO_SHOW:     {color:GRAY,     bg:LIGHT,    border:BORDER,   label:"No Show"},
}

const STATUS_FLOW: Record<string,{action:string;label:string;color:string}[]> = {
  SCHEDULED:   [{action:"confirm",label:"Confirm",color:PURPLE},{action:"cancel",label:"Cancel",color:RED}],
  CONFIRMED:   [{action:"start",label:"Start",color:AMBER},{action:"no_show",label:"No Show",color:GRAY},{action:"cancel",label:"Cancel",color:RED}],
  IN_PROGRESS: [{action:"complete",label:"Complete",color:GREEN}],
}

// ── Calendar helpers ──────────────────────────────────────────────────────────

const HOUR_HEIGHT = 64  // px per hour
const DAY_START   = 7   // 07:00
const DAY_END     = 19  // 19:00
const HOURS       = Array.from({length: DAY_END - DAY_START}, (_,i) => DAY_START + i)

function startOfWeek(d: Date) {
  const day = new Date(d); day.setDate(d.getDate() - d.getDay() + 1); // Mon
  day.setHours(0,0,0,0); return day
}
function addDays(d: Date, n: number) {
  const r = new Date(d); r.setDate(d.getDate() + n); return r
}
function isSameDay(a: Date, b: Date) {
  return a.getFullYear()===b.getFullYear() && a.getMonth()===b.getMonth() && a.getDate()===b.getDate()
}
function fmtDate(d: Date) {
  return d.toLocaleDateString("en-ZA",{day:"numeric",month:"short"})
}
function fmtDay(d: Date) {
  return d.toLocaleDateString("en-ZA",{weekday:"short"})
}
function fmtHour(h: number) {
  return `${String(h).padStart(2,"0")}:00`
}
function topOffset(iso: string) {
  const d = new Date(iso)
  return (d.getHours() + d.getMinutes()/60 - DAY_START) * HOUR_HEIGHT
}
function apptHeight(minutes: number) {
  return Math.max((minutes / 60) * HOUR_HEIGHT - 4, 20)
}

const unwrap = (r:any) => { const p=r.data?.data??r.data; return Array.isArray(p)?p:(p?.content??[]) }

// ── Component ─────────────────────────────────────────────────────────────────

interface ScheduleTabProps { onStartSession?: (appointment: any, patient: any) => void }
export default function ScheduleTab({ onStartSession }: ScheduleTabProps = {}) {
  const qc = useQueryClient()
  const [view, setView]                 = useState<"day"|"week">("week")
  const [anchor, setAnchor]             = useState(() => { const d=new Date(); d.setHours(0,0,0,0); return d })
  const [doctorFilter, setDoctorFilter] = useState<string>("all")
  const [selected, setSelected]         = useState<Appointment|null>(null)
  const [showBook, setShowBook]         = useState(false)
  const [bookSlot, setBookSlot]         = useState<{date:Date;hour:number}|null>(null)
  const [apiError, setApiError]         = useState("")
  const nowRef = useRef<HTMLDivElement>(null)

  // Book form
  const [bookForm, setBookForm] = useState({
    patientId:"", practitionerId:"", scheduledAt:"",
    durationMinutes:"30", appointmentType:"CONSULTATION", reason:""
  })

  const weekStart = view==="week" ? startOfWeek(anchor) : anchor
  const days = view==="week"
    ? Array.from({length:7}, (_,i) => addDays(weekStart, i))
    : [anchor]

  // Date range for API query
  const rangeStart = new Date(days[0]); rangeStart.setHours(0,0,0,0)
  const rangeEnd   = new Date(days[days.length-1]); rangeEnd.setHours(23,59,59,999)

  const { data: appointments=[] } = useQuery<Appointment[]>({
    queryKey: ["schedule-appts", rangeStart.toISOString(), rangeEnd.toISOString()],
    queryFn: async () => {
      const p = new URLSearchParams({ size:"200" })
      return unwrap(await apiClient.get(`/api/v1/clinic/appointments?${p}`))
    },
    refetchInterval: 60_000,
  })

  const { data: practitioners=[] } = useQuery<Practitioner[]>({
    queryKey: ["clinic-practitioners-list"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/clinic/practitioners/list")),
  })

  const { data: patients=[] } = useQuery<Patient[]>({
    queryKey: ["clinic-patients-list"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/clinic/patients?size=200")),
  })

  const doAction = useMutation({
    mutationFn: ({id,action}:{id:string;action:string}) => apiClient.post(`/api/v1/clinic/appointments/${id}/${action}`),
    onSuccess: (res, vars: any) => {
      qc.invalidateQueries({queryKey:["schedule-appts"]})
      qc.invalidateQueries({queryKey:["clinic-appts-dashboard"]})
      const updated = res.data?.data ?? res.data
      setSelected(updated)
      // When appointment is started, open the consultation session via parent
      if (vars.action === "start" && onStartSession) {
        onStartSession(updated, { id: updated.patientId, fullName: updated.patientName })
      }
    },
    onError: (e:any) => setApiError(e.response?.data?.message ?? "Action failed"),
  })

  // FIX: "no appointment reminder UI" gap — ScheduleTab had booking and
  // status changes but no "send reminder" action.
  const [reminderSent, setReminderSent] = useState<string|null>(null)
  const sendReminder = useMutation({
    mutationFn: (id:string) => apiClient.post(`/api/v1/clinic/appointments/${id}/send-reminder`),
    onSuccess: (_res, id) => { setReminderSent(id); setTimeout(()=>setReminderSent(null), 4000) },
    onError: (e:any) => setApiError(e.response?.data?.message ?? "Failed to send reminder"),
  })

  // FIX: "no telehealth/video consultation option" gap.
  const joinVideoCall = useMutation({
    mutationFn: (id:string) => apiClient.post(`/api/v1/clinic/appointments/${id}/video-room`),
    onSuccess: (res:any) => {
      const url = res.data?.videoRoomUrl ?? res.videoRoomUrl
      if (url) window.open(url, "_blank", "noopener,noreferrer")
      qc.invalidateQueries({queryKey:["schedule-appts"]})
    },
    onError: (e:any) => setApiError(e.response?.data?.message ?? "Failed to start video call"),
  })

  const book = useMutation({
    mutationFn: (body:any) => apiClient.post("/api/v1/clinic/appointments", body),
    onSuccess: () => {
      qc.invalidateQueries({queryKey:["schedule-appts"]})
      qc.invalidateQueries({queryKey:["clinic-appts-dashboard"]})
      setShowBook(false)
      setBookForm({patientId:"",practitionerId:"",scheduledAt:"",durationMinutes:"30",appointmentType:"CONSULTATION",reason:""})
      setApiError("")
    },
    onError: (e:any) => setApiError(e.response?.data?.message ?? "Booking failed"),
  })

  // Scroll to current time on mount
  useEffect(() => {
    setTimeout(() => nowRef.current?.scrollIntoView({behavior:"smooth",block:"center"}), 200)
  }, [])

  // Filter appointments
  const filtered = (appointments as Appointment[]).filter(a => {
    const d = new Date(a.scheduledAt)
    const inRange = days.some(day => isSameDay(d, day))
    const byDoc = doctorFilter==="all" || a.practitionerId===doctorFilter
    return inRange && byDoc
  })

  const getAppts = (day: Date) =>
    filtered.filter(a => isSameDay(new Date(a.scheduledAt), day))
      .sort((a,b) => a.scheduledAt.localeCompare(b.scheduledAt))

  const today = new Date(); today.setHours(0,0,0,0)
  const nowMinutes = new Date().getHours()*60 + new Date().getMinutes()
  const nowTop = ((nowMinutes/60) - DAY_START) * HOUR_HEIGHT

  const handleSlotClick = (day: Date, hour: number) => {
    const dt = new Date(day)
    dt.setHours(hour, 0, 0, 0)
    const iso = `${dt.getFullYear()}-${String(dt.getMonth()+1).padStart(2,"0")}-${String(dt.getDate()).padStart(2,"0")}T${String(hour).padStart(2,"0")}:00`
    setBookSlot({date:day, hour})
    setBookForm(f => ({...f, scheduledAt:iso, practitionerId: doctorFilter==="all"?"":doctorFilter}))
    setShowBook(true)
    setApiError("")
  }

  return (
    <div style={{fontFamily:"'Inter',system-ui,sans-serif"}}>
      {/* ── Toolbar ─────────────────────────────────────────────────────── */}
      <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginBottom:20,gap:12,flexWrap:"wrap"}}>
        {/* Navigation */}
        <div style={{display:"flex",alignItems:"center",gap:8}}>
          <button onClick={()=>setAnchor(d=>{const n=new Date(d);n.setDate(d.getDate()-(view==="week"?7:1));return n})}
            style={navBtn}><ChevronLeft size={16}/></button>
          <button onClick={()=>setAnchor(new Date())}
            style={{...navBtn, fontWeight:600, padding:"6px 14px", fontSize:13}}>Today</button>
          <button onClick={()=>setAnchor(d=>{const n=new Date(d);n.setDate(d.getDate()+(view==="week"?7:1));return n})}
            style={navBtn}><ChevronRight size={16}/></button>
          <div style={{fontSize:15,fontWeight:700,color:"#0F172A",marginLeft:6}}>
            {view==="week"
              ? `${fmtDate(days[0])} — ${fmtDate(days[6])}`
              : anchor.toLocaleDateString("en-ZA",{weekday:"long",day:"numeric",month:"long",year:"numeric"})}
          </div>
        </div>

        {/* Controls */}
        <div style={{display:"flex",gap:8,alignItems:"center",flexWrap:"wrap"}}>
          {/* Doctor filter */}
          <select value={doctorFilter} onChange={e=>setDoctorFilter(e.target.value)}
            style={{padding:"7px 12px",border:`1px solid ${BORDER}`,borderRadius:8,fontSize:13,outline:"none",background:"#fff",maxWidth:220}}>
            <option value="all">All practitioners</option>
            {(practitioners as Practitioner[]).map(p=>(
              <option key={p.id} value={p.id}>Dr. {p.fullName}</option>
            ))}
          </select>

          {/* Day / Week toggle */}
          <div style={{display:"flex",border:`1px solid ${BORDER}`,borderRadius:8,overflow:"hidden"}}>
            {(["day","week"] as const).map(v=>(
              <button key={v} onClick={()=>setView(v)}
                style={{padding:"7px 14px",border:"none",fontSize:13,cursor:"pointer",
                  background:view===v?NAVY:"#fff", color:view===v?"#fff":GRAY, fontWeight:view===v?600:400}}>
                {v.charAt(0).toUpperCase()+v.slice(1)}
              </button>
            ))}
          </div>

          <button onClick={()=>{setShowBook(true);setBookForm(f=>({...f,scheduledAt:"",practitionerId:doctorFilter==="all"?"":doctorFilter}));setApiError("")}}
            style={{display:"flex",alignItems:"center",gap:6,background:NAVY,color:"#fff",border:"none",borderRadius:8,padding:"8px 16px",fontSize:13,fontWeight:600,cursor:"pointer"}}>
            <Plus size={14}/> Book appointment
          </button>
        </div>
      </div>

      {/* ── Calendar grid ───────────────────────────────────────────────── */}
      <div style={{border:`1px solid ${BORDER}`,borderRadius:12,overflow:"hidden",background:"#fff"}}>
        {/* Day headers */}
        <div style={{display:"grid",gridTemplateColumns:`64px repeat(${days.length},1fr)`,borderBottom:`1px solid ${BORDER}`}}>
          <div style={{padding:"12px 8px",background:LIGHT}}/>
          {days.map(day=>{
            const isToday = isSameDay(day, today)
            return (
              <div key={day.toISOString()} style={{padding:"12px 8px",textAlign:"center",background:isToday?"#EFF6FF":LIGHT,borderLeft:`1px solid ${BORDER}`}}>
                <div style={{fontSize:11,fontWeight:600,color:isToday?"#1D4ED8":GRAY,textTransform:"uppercase",letterSpacing:"0.06em"}}>{fmtDay(day)}</div>
                <div style={{fontSize:isToday?20:16,fontWeight:isToday?800:600,color:isToday?"#1D4ED8":"#0F172A",marginTop:2,
                  ...(isToday?{width:32,height:32,borderRadius:"50%",background:"#1D4ED8",color:"#fff",display:"inline-flex",alignItems:"center",justifyContent:"center"}:{})}}>
                  {day.getDate()}
                </div>
              </div>
            )
          })}
        </div>

        {/* Time grid */}
        <div style={{display:"grid",gridTemplateColumns:`64px repeat(${days.length},1fr)`,overflowY:"auto",maxHeight:"calc(100vh - 300px)",position:"relative"}}>
          {/* Hour labels */}
          <div style={{gridColumn:"1",gridRow:"1"}}>
            {HOURS.map(h=>(
              <div key={h} style={{height:HOUR_HEIGHT,borderBottom:`1px solid #F1F5F9`,padding:"4px 8px",display:"flex",alignItems:"flex-start"}}>
                <span style={{fontSize:11,color:GRAY,fontWeight:500}}>{fmtHour(h)}</span>
              </div>
            ))}
          </div>

          {/* Day columns */}
          {days.map((day, di)=>{
            const dayAppts = getAppts(day)
            const isToday  = isSameDay(day, today)
            return (
              <div key={day.toISOString()}
                style={{gridColumn:`${di+2}`,gridRow:"1",borderLeft:`1px solid ${BORDER}`,position:"relative",minHeight:HOURS.length*HOUR_HEIGHT}}>
                {/* Hour slots — clickable */}
                {HOURS.map(h=>(
                  <div key={h}
                    onClick={()=>handleSlotClick(day,h)}
                    style={{height:HOUR_HEIGHT,borderBottom:`1px solid #F1F5F9`,cursor:"pointer"}}
                    onMouseEnter={e=>(e.currentTarget.style.background="#F0FDF4")}
                    onMouseLeave={e=>(e.currentTarget.style.background="")}
                  />
                ))}

                {/* Current time indicator */}
                {isToday && nowTop > 0 && nowTop < HOURS.length*HOUR_HEIGHT && (
                  <div ref={nowRef} style={{position:"absolute",left:0,right:0,top:nowTop,pointerEvents:"none",zIndex:10}}>
                    <div style={{height:2,background:RED,position:"relative"}}>
                      <div style={{width:8,height:8,borderRadius:"50%",background:RED,position:"absolute",left:-4,top:-3}}/>
                    </div>
                  </div>
                )}

                {/* Appointments */}
                {dayAppts.map(appt=>{
                  const top  = topOffset(appt.scheduledAt)
                  const h    = apptHeight(appt.durationMinutes)
                  const s    = STATUS_CFG[appt.status] ?? STATUS_CFG.SCHEDULED
                  if (top < 0 || top > HOURS.length*HOUR_HEIGHT) return null
                  return (
                    <div key={appt.id}
                      onClick={e=>{e.stopPropagation(); setSelected(appt); setApiError("")}}
                      style={{position:"absolute",left:3,right:3,top,height:h,
                        background:s.bg, border:`1px solid ${s.border}`, borderLeft:`3px solid ${s.color}`,
                        borderRadius:6, padding:"3px 6px", cursor:"pointer", overflow:"hidden",
                        zIndex:5, boxSizing:"border-box"}}>
                      <div style={{fontSize:11,fontWeight:700,color:s.color,overflow:"hidden",whiteSpace:"nowrap",textOverflow:"ellipsis"}}>
                        {new Date(appt.scheduledAt).toLocaleTimeString("en-ZA",{hour:"2-digit",minute:"2-digit"})} {appt.patientName}
                      </div>
                      {h > 28 && <div style={{fontSize:10,color:s.color,opacity:0.8,overflow:"hidden",whiteSpace:"nowrap",textOverflow:"ellipsis"}}>{appt.appointmentType?.replace("_"," ")} {appt.reason?`· ${appt.reason}`:""}</div>}
                    </div>
                  )
                })}
              </div>
            )
          })}
        </div>
      </div>

      {/* ── Appointment detail modal ─────────────────────────────────────── */}
      {selected && (() => {
        const s = STATUS_CFG[selected.status] ?? STATUS_CFG.SCHEDULED
        const actions = STATUS_FLOW[selected.status] ?? []
        return (
          <Modal title={selected.patientName} onClose={()=>setSelected(null)}>
            <div style={{display:"flex",alignItems:"center",gap:8,marginBottom:20}}>
              <span style={{background:s.bg,color:s.color,padding:"4px 12px",borderRadius:20,fontSize:12,fontWeight:700,border:`1px solid ${s.border}`}}>{s.label}</span>
              <span style={{fontSize:13,color:GRAY}}>{selected.appointmentType?.replace("_"," ")}</span>
            </div>
            <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:10,marginBottom:20}}>
              {[
                ["Date", new Date(selected.scheduledAt).toLocaleDateString("en-ZA",{weekday:"long",day:"numeric",month:"long",year:"numeric"})],
                ["Time", new Date(selected.scheduledAt).toLocaleTimeString("en-ZA",{hour:"2-digit",minute:"2-digit"})],
                ["Duration", `${selected.durationMinutes} minutes`],
                ["Practitioner", selected.practitionerName ? `Dr. ${selected.practitionerName}` : "—"],
                ["Reason", selected.reason || "—"],
              ].map(([label,value])=>(
                <div key={label as string} style={{padding:"9px 12px",background:LIGHT,borderRadius:8}}>
                  <div style={{fontSize:10,fontWeight:700,color:GRAY,textTransform:"uppercase",letterSpacing:"0.06em",marginBottom:2}}>{label}</div>
                  <div style={{fontSize:13,color:"#0F172A",fontWeight:500}}>{value}</div>
                </div>
              ))}
            </div>
            {apiError && <ErrBox msg={apiError}/>}
            <div style={{display:"flex",gap:8,justifyContent:"space-between",alignItems:"center",flexWrap:"wrap"}}>
              <div style={{display:"flex",gap:8}}>
                {["SCHEDULED","CONFIRMED"].includes(selected.status) && (
                  <button onClick={()=>sendReminder.mutate(selected.id)} disabled={sendReminder.isPending}
                    style={{display:"flex",alignItems:"center",gap:6,padding:"8px 14px",border:`1px solid ${BORDER}`,borderRadius:8,fontSize:13,fontWeight:600,cursor:"pointer",background:"#fff",color:reminderSent===selected.id?GREEN:TEAL}}>
                    {reminderSent===selected.id ? <><CheckCircle size={14}/> Reminder sent</> : <><Mail size={14}/> {sendReminder.isPending?"Sending...":"Send reminder"}</>}
                  </button>
                )}
                {selected.appointmentType==="TELEHEALTH" && ["SCHEDULED","CONFIRMED","IN_PROGRESS"].includes(selected.status) && (
                  <button onClick={()=>joinVideoCall.mutate(selected.id)} disabled={joinVideoCall.isPending}
                    style={{display:"flex",alignItems:"center",gap:6,padding:"8px 14px",border:"none",borderRadius:8,fontSize:13,fontWeight:600,cursor:"pointer",background:PURPLE,color:"#fff"}}>
                    <Video size={14}/> {joinVideoCall.isPending?"Starting...":"Join video call"}
                  </button>
                )}
              </div>
              {actions.length > 0 && (
                <div style={{display:"flex",gap:8}}>
                  {actions.map(btn=>(
                    <button key={btn.action} onClick={()=>doAction.mutate({id:selected.id,action:btn.action})}
                      disabled={doAction.isPending}
                      style={{padding:"8px 18px",border:"none",borderRadius:8,fontSize:13,fontWeight:600,cursor:"pointer",background:`${btn.color}18`,color:btn.color}}>
                      {btn.label}
                    </button>
                  ))}
                </div>
              )}
            </div>
          </Modal>
        )
      })()}

      {/* ── Book appointment modal ────────────────────────────────────────── */}
      {showBook && (
        <Modal title="Book appointment" onClose={()=>setShowBook(false)}>
          <div style={{display:"flex",flexDirection:"column",gap:14}}>
            <div>
              <label style={lbl}>Patient *</label>
              <select value={bookForm.patientId} onChange={e=>setBookForm(f=>({...f,patientId:e.target.value}))} style={sinp}>
                <option value="">Select patient...</option>
                {(patients as Patient[]).map(p=><option key={p.id} value={p.id}>{p.fullName}</option>)}
              </select>
            </div>
            <div>
              <label style={lbl}>Practitioner</label>
              <select value={bookForm.practitionerId} onChange={e=>setBookForm(f=>({...f,practitionerId:e.target.value}))} style={sinp}>
                <option value="">Any / unassigned</option>
                {(practitioners as Practitioner[]).map(p=><option key={p.id} value={p.id}>Dr. {p.fullName} — {p.specialty}</option>)}
              </select>
            </div>
            <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:12}}>
              <div>
                <label style={lbl}>Date & time *</label>
                <input type="datetime-local" value={bookForm.scheduledAt}
                  min={new Date(Date.now() - new Date().getTimezoneOffset()*60000).toISOString().slice(0,16)}
                  onChange={e=>setBookForm(f=>({...f,scheduledAt:e.target.value}))} style={sinp}/>
              </div>
              <div>
                <label style={lbl}>Duration (min)</label>
                <input type="number" min="5" max="480" value={bookForm.durationMinutes}
                  onChange={e=>setBookForm(f=>({...f,durationMinutes:e.target.value}))} style={sinp}/>
              </div>
              <div>
                <label style={lbl}>Type</label>
                <select value={bookForm.appointmentType} onChange={e=>setBookForm(f=>({...f,appointmentType:e.target.value}))} style={sinp}>
                  {["CONSULTATION","FOLLOW_UP","PROCEDURE","EMERGENCY","CHECKUP","TELEHEALTH"].map(t=>(
                    <option key={t} value={t}>{t.replace("_"," ")}</option>
                  ))}
                </select>
              </div>
              <div>
                <label style={lbl}>Reason</label>
                <input value={bookForm.reason} onChange={e=>setBookForm(f=>({...f,reason:e.target.value}))} placeholder="Optional" style={sinp}/>
              </div>
            </div>
            {bookForm.scheduledAt && bookForm.durationMinutes && (
              <div style={{padding:"8px 12px",background:"#F0FDF4",border:"1px solid #86EFAC",borderRadius:8,fontSize:13,color:GREEN}}>
                ✓ {new Date(bookForm.scheduledAt).toLocaleString("en-ZA",{dateStyle:"medium",timeStyle:"short"})} · {bookForm.durationMinutes} min
              </div>
            )}
            {apiError && <ErrBox msg={apiError}/>}
          </div>
          <ModalFooter
            onCancel={()=>setShowBook(false)}
            onConfirm={()=>{
              if (!bookForm.patientId || !bookForm.scheduledAt) {
                setApiError("Patient and date/time are required"); return
              }
              // FIX: browsers don't reliably block a past datetime-local
              // value entered by typing/paste even with min= set — belt
              // and braces client-side check. Real enforcement still
              // belongs server-side (see ClinicService.createAppointment),
              // this only improves the UX for the common case.
              if (new Date(bookForm.scheduledAt).getTime() < Date.now()) {
                setApiError("Cannot book an appointment in the past"); return
              }
              book.mutate({
                patientId: bookForm.patientId,
                practitionerId: bookForm.practitionerId || null,
                scheduledAt: new Date(bookForm.scheduledAt).toISOString(),
                durationMinutes: parseInt(bookForm.durationMinutes) || 30,
                appointmentType: bookForm.appointmentType,
                reason: bookForm.reason || null,
              })
            }}
            confirmLabel="Book appointment"
            loading={book.isPending}/>
        </Modal>
      )}
    </div>
  )
}

// ── Shared ────────────────────────────────────────────────────────────────────

function Modal({title,onClose,children}:{title:string;onClose:()=>void;children:React.ReactNode}) {
  return (
    <div style={{position:"fixed",inset:0,background:"rgba(15,23,42,0.5)",display:"flex",alignItems:"center",justifyContent:"center",zIndex:1000,backdropFilter:"blur(3px)"}}>
      <div style={{background:"#fff",borderRadius:16,padding:28,width:480,maxHeight:"90vh",overflowY:"auto",boxShadow:"0 20px 60px rgba(0,0,0,0.2)"}}>
        <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginBottom:20}}>
          <h3 style={{margin:0,fontSize:17,fontWeight:700,color:"#0F172A"}}>{title}</h3>
          <button onClick={onClose} style={{background:"none",border:"none",cursor:"pointer",color:GRAY,display:"flex"}}><X size={20}/></button>
        </div>
        {children}
      </div>
    </div>
  )
}
function ModalFooter({onCancel,onConfirm,confirmLabel,loading}:{onCancel:()=>void;onConfirm:()=>void;confirmLabel:string;loading?:boolean}) {
  return <div style={{display:"flex",gap:10,justifyContent:"flex-end",marginTop:20}}><button onClick={onCancel} style={btnCancel}>Cancel</button><button onClick={onConfirm} disabled={loading} style={btnPrimary}>{loading?"Saving...":confirmLabel}</button></div>
}
function ErrBox({msg}:{msg:string}) {
  return <div style={{marginTop:10,padding:"8px 12px",background:"#FEF2F2",border:"1px solid #FECACA",borderRadius:8,fontSize:13,color:RED,display:"flex",alignItems:"center",gap:8}}><AlertCircle size={13}/>{msg}</div>
}

const lbl:React.CSSProperties      = {display:"block",fontSize:13,fontWeight:600,color:"#374151",marginBottom:5}
const sinp:React.CSSProperties     = {width:"100%",padding:"9px 12px",boxSizing:"border-box" as const,border:"1.5px solid #E2E8F0",borderRadius:8,fontSize:14,outline:"none",background:"#fff"}
const btnPrimary:React.CSSProperties = {background:NAVY,color:"#fff",border:"none",borderRadius:9,padding:"9px 20px",fontSize:13,fontWeight:600,cursor:"pointer"}
const btnCancel:React.CSSProperties  = {padding:"9px 18px",border:"1px solid #E2E8F0",borderRadius:9,background:"#fff",fontSize:13,cursor:"pointer",color:"#374151"}
const navBtn:React.CSSProperties    = {display:"flex",alignItems:"center",justifyContent:"center",padding:"6px 10px",border:"1px solid #E2E8F0",borderRadius:8,background:"#fff",cursor:"pointer",color:GRAY}
