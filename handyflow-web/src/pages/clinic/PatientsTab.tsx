// src/pages/clinic/PatientsTab.tsx
// Paginated patient list — server search, family account registration,
// dependant search, account type badges, no medical columns for reception
import { useState, useEffect, useRef } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  Plus, X, Search, User, Users, ChevronRight, AlertCircle,
  Phone, Mail, ChevronLeft, Link,
} from "lucide-react"

// ── Types ─────────────────────────────────────────────────────────────────────

interface Patient {
  id: string; firstName: string; lastName: string; fullName: string
  idNumber: string; dateOfBirth: string; gender: string
  phone: string; email: string; bloodType: string
  allergies: string[]; chronicConditions: string[]
  emergencyContactName: string; emergencyContactPhone: string
  notes: string; active: boolean; createdAt: string
  accountType: "INDIVIDUAL" | "PRINCIPAL" | "DEPENDANT"
  principalId?: string; principalName?: string
  relationship?: string; lastVisitAt?: string; archivedAt?: string
}
interface Props { onOpenPatient: (p: Patient) => void }

// ── Helpers ───────────────────────────────────────────────────────────────────

function saIdInfo(id?: string) {
  const c = (id ?? "").replace(/\D/g, "")
  if (c.length !== 13) return null
  const yy=+c.slice(0,2), mm=+c.slice(2,4), dd=+c.slice(4,6)
  const yr = yy <= (new Date().getFullYear()%100) ? 2000+yy : 1900+yy
  const months = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"]
  const age = Math.floor((Date.now()-new Date(yr,mm-1,dd).getTime())/(365.25*24*3600*1000))
  return { dob:`${String(dd).padStart(2,"0")} ${months[mm-1]} ${yr}`, age, gender:+c[6]>=5?"Male":"Female" }
}

function autofillFromId(idNumber: string) {
  const c = idNumber.replace(/\D/g,"")
  if (c.length !== 13) return null
  const yy=+c.slice(0,2), mm=+c.slice(2,4), dd=+c.slice(4,6)
  const yr = yy <= (new Date().getFullYear()%100) ? 2000+yy : 1900+yy
  return {
    dateOfBirth: `${yr}-${String(mm).padStart(2,"0")}-${String(dd).padStart(2,"0")}`,
    gender: +c[6]>=5?"MALE":"FEMALE"
  }
}

const fmtDT = (iso?: string) => iso
  ? new Date(iso).toLocaleDateString("en-ZA",{day:"numeric",month:"short",year:"numeric"})
  : "—"

const ACCOUNT_BADGE: Record<string,{label:string;bg:string;color:string}> = {
  INDIVIDUAL: {label:"Individual", bg:"#EFF6FF", color:"#1D4ED8"},
  PRINCIPAL:  {label:"Principal",  bg:"#F0FDF4", color:"#166534"},
  DEPENDANT:  {label:"Dependant",  bg:"#F5F3FF", color:"#7C3AED"},
}

const GENDERS = ["MALE","FEMALE","NON_BINARY","PREFER_NOT_TO_SAY"]
const RELATIONSHIPS = ["CHILD","PARENT","GRANDPARENT","SPOUSE","SIBLING","OTHER"]
const PAGE_SIZE = 20

interface PersonForm {
  firstName: string; lastName: string; idNumber: string; dateOfBirth: string
  gender: string; phone: string; email: string
  emergencyContactName: string; emergencyContactPhone: string
}
interface DepForm extends PersonForm { relationship: string }

const EMPTY: PersonForm = {
  firstName:"", lastName:"", idNumber:"", dateOfBirth:"", gender:"",
  phone:"", email:"", emergencyContactName:"", emergencyContactPhone:""
}
const EMPTY_DEP = (): DepForm => ({ ...EMPTY, relationship:"CHILD" })

// ── Component ─────────────────────────────────────────────────────────────────

export default function PatientsTab({ onOpenPatient }: Props) {
  const qc = useQueryClient()
  const [search, setSearch]         = useState("")
  const [debouncedSearch, setDS]    = useState("")
  const [page, setPage]             = useState(0)
  const [showArchived, setShowArchived] = useState(false)
  const [showCreate, setShowCreate] = useState(false)
  const [regType, setRegType]       = useState<"individual"|"family">("individual")
  const [form, setForm]             = useState<PersonForm>({...EMPTY})
  const [dependants, setDependants] = useState<DepForm[]>([])
  const [fieldErrors, setFieldErrors] = useState<Record<string,string>>({})
  const [apiError, setApiError]     = useState("")
  const debounceRef = useRef<ReturnType<typeof setTimeout>>()

  // Debounce search
  useEffect(() => {
    clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(() => { setDS(search); setPage(0) }, 320)
    return () => clearTimeout(debounceRef.current)
  }, [search])

  const f = (k: keyof PersonForm, v: string) => {
    setForm(p => ({ ...p, [k]:v }))
    if (k === "idNumber") {
      const info = autofillFromId(v)
      if (info) setForm(p => ({ ...p, idNumber:v, ...info }))
    }
    setFieldErrors(e => { const n={...e}; delete n[k]; return n })
  }

  const updateDep = (idx: number, k: keyof DepForm, v: string) =>
    setDependants(d => d.map((x,i) => i===idx ? {...x,[k]:v} : x))

  const validate = () => {
    const errs: Record<string,string> = {}
    if (!form.firstName.trim()) errs.firstName = "Required"
    if (!form.lastName.trim())  errs.lastName  = "Required"
    if (form.phone && !/^(\+|0)[\d\s\-]{7,}$/.test(form.phone)) errs.phone = "Start with + or 0"
    if (form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) errs.email = "Invalid email"
    setFieldErrors(errs)
    return Object.keys(errs).length === 0
  }

  const { data, isLoading, isPreviousData } = useQuery({
    queryKey: ["clinic-patients", debouncedSearch, page, showArchived],
    queryFn: async () => {
      const p = new URLSearchParams({ size:String(PAGE_SIZE), page:String(page) })
      if (debouncedSearch.trim()) p.set("search", debouncedSearch.trim())
      if (showArchived) p.set("includeArchived","true")
      const r = await apiClient.get(`/api/v1/clinic/patients?${p}`)
      return r.data?.data ?? r.data
    },
    keepPreviousData: true,
  })

  const createPatient = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/clinic/patients", body),
    onError: (e: any) => {
      const d = e.response?.data
      if (d?.errors) setFieldErrors(d.errors)
      else setApiError(d?.message ?? "Registration failed")
    },
  })

  const handleRegister = async () => {
    if (!validate()) return
    try {
      const principalRes = await createPatient.mutateAsync({
        firstName: form.firstName, lastName: form.lastName,
        idNumber: form.idNumber||null, dateOfBirth: form.dateOfBirth||null,
        gender: form.gender||null, phone: form.phone||null, email: form.email||null,
        emergencyContactName: form.emergencyContactName||null,
        emergencyContactPhone: form.emergencyContactPhone||null,
        accountType: regType==="family" ? "PRINCIPAL" : "INDIVIDUAL",
      })
      const principal = principalRes.data?.data ?? principalRes.data

      // Register dependants linked to principal
      for (const dep of dependants) {
        if (!dep.firstName.trim() || !dep.lastName.trim()) continue
        await apiClient.post("/api/v1/clinic/patients", {
          firstName: dep.firstName, lastName: dep.lastName,
          idNumber: dep.idNumber||null, dateOfBirth: dep.dateOfBirth||null,
          gender: dep.gender||null, phone: dep.phone||null,
          emergencyContactName: `${form.firstName} ${form.lastName}`,
          emergencyContactPhone: form.phone||null,
          accountType: "DEPENDANT",
          principalId: principal?.id,
          relationship: dep.relationship,
        })
      }

      qc.invalidateQueries({ queryKey: ["clinic-patients"] })
      qc.invalidateQueries({ queryKey: ["clinic-patients-dashboard"] })
      setShowCreate(false)
      setForm({...EMPTY})
      setDependants([])
      setApiError("")
      if (principal?.id) onOpenPatient(principal)
    } catch { /* errors handled by mutation */ }
  }

  const patients: Patient[] = data?.content ?? []
  const totalPages   = data?.totalPages ?? 0
  const totalElements = data?.totalElements ?? patients.length
  const idInfo = saIdInfo(form.idNumber)

  const inp = (key: string): React.CSSProperties => ({
    width:"100%", padding:"9px 12px", boxSizing:"border-box" as const,
    border:`1.5px solid ${fieldErrors[key]?"#DC2626":"#E2E8F0"}`,
    borderRadius:8, fontSize:14, background:fieldErrors[key]?"#FFF5F5":"#fff", outline:"none",
  })
  const FErr = ({ k }: { k: string }) => fieldErrors[k] ? (
    <div style={{ display:"flex", alignItems:"center", gap:4, fontSize:12, color:"#DC2626", marginTop:4 }}>
      <AlertCircle size={12}/>{fieldErrors[k]}
    </div>
  ) : null

  return (
    <div>
      {/* ── Toolbar ─────────────────────────────────────────────────────── */}
      <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:16, gap:10 }}>
        <div style={{ position:"relative", flex:1, maxWidth:440 }}>
          <Search size={14} style={{ position:"absolute", left:10, top:"50%", transform:"translateY(-50%)", color:"#94A3B8" }}/>
          <input value={search} onChange={e => setSearch(e.target.value)}
            placeholder="Search name, ID number, phone — finds dependants too..."
            style={{ padding:"9px 12px 9px 34px", border:"1px solid #E2E8F0", borderRadius:9,
              fontSize:13, width:"100%", outline:"none", boxSizing:"border-box" as const }}/>
        </div>
        <div style={{ display:"flex", gap:8 }}>
          <label style={{ display:"flex", alignItems:"center", gap:6, fontSize:13, color:"#64748B", cursor:"pointer", userSelect:"none" }}>
            <input type="checkbox" checked={showArchived} onChange={e=>setShowArchived(e.target.checked)}
              style={{ accentColor:"#1B3A6B" }}/>
            Show archived
          </label>
          <button onClick={() => { setShowCreate(true); setForm({...EMPTY}); setDependants([]); setFieldErrors({}); setApiError("") }}
            style={{ display:"flex", alignItems:"center", gap:7, background:"#1B3A6B", color:"#fff",
              border:"none", borderRadius:9, padding:"9px 18px", fontSize:14, fontWeight:600,
              cursor:"pointer", whiteSpace:"nowrap" }}>
            <Plus size={15}/> Register patient
          </button>
        </div>
      </div>

      {/* ── Stats ───────────────────────────────────────────────────────── */}
      <div style={{ display:"flex", gap:12, marginBottom:16 }}>
        <div style={{ background:"#F8FAFC", border:"1px solid #E2E8F0", borderRadius:10, padding:"8px 18px" }}>
          <div style={{ fontSize:18, fontWeight:700, color:"#1B3A6B" }}>{totalElements}</div>
          <div style={{ fontSize:11, color:"#64748B" }}>Total patients</div>
        </div>
        <div style={{ background:"#F8FAFC", border:"1px solid #E2E8F0", borderRadius:10, padding:"8px 18px" }}>
          <div style={{ fontSize:18, fontWeight:700, color:"#0D9488" }}>{patients.length}</div>
          <div style={{ fontSize:11, color:"#64748B" }}>This page</div>
        </div>
        <div style={{ flex:1 }}/>
        <div style={{ fontSize:12, color:"#94A3B8", alignSelf:"center" }}>
          Click any row to open patient file
        </div>
      </div>

      {/* ── Table ───────────────────────────────────────────────────────── */}
      {isLoading ? (
        <div style={{ textAlign:"center", padding:40, color:"#94A3B8" }}>Loading patients...</div>
      ) : patients.length === 0 ? (
        <div style={{ textAlign:"center", padding:"60px 20px", color:"#94A3B8",
          border:"1px dashed #E2E8F0", borderRadius:12 }}>
          <User size={36} style={{ marginBottom:12, opacity:0.4 }}/>
          <div style={{ fontWeight:600, color:"#475569" }}>
            {debouncedSearch ? `No patients matching "${debouncedSearch}"` : "No patients registered yet"}
          </div>
        </div>
      ) : (
        <div style={{ border:"1px solid #E2E8F0", borderRadius:12, overflow:"hidden",
          opacity: isPreviousData ? 0.6 : 1, transition:"opacity 0.15s" }}>
          <table style={{ width:"100%", borderCollapse:"collapse" }}>
            <thead>
              <tr style={{ background:"#F8FAFC", borderBottom:"1px solid #E2E8F0" }}>
                {["Patient","DOB / Age","Contact","Account","Last visit",""].map(h => (
                  <th key={h} style={{ padding:"10px 16px", textAlign:"left", fontSize:11,
                    fontWeight:700, color:"#64748B", letterSpacing:"0.05em" }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {patients.map((p, i) => {
                const info = saIdInfo(p.idNumber)
                const badge = ACCOUNT_BADGE[p.accountType] ?? ACCOUNT_BADGE.INDIVIDUAL
                const isArchived = !!p.archivedAt
                return (
                  <tr key={p.id}
                    onClick={() => onOpenPatient(p)}
                    style={{ borderBottom: i<patients.length-1?"1px solid #F1F5F9":"none",
                      cursor:"pointer", opacity: isArchived ? 0.55 : 1 }}
                    onMouseEnter={e => (e.currentTarget.style.background="#F0FDF4")}
                    onMouseLeave={e => (e.currentTarget.style.background="")}>

                    {/* Patient name + ID */}
                    <td style={{ padding:"11px 16px" }}>
                      <div style={{ display:"flex", alignItems:"center", gap:10 }}>
                        <div style={{ width:34, height:34, borderRadius:"50%",
                          background: p.accountType==="PRINCIPAL"?"#DCFCE7":p.accountType==="DEPENDANT"?"#F5F3FF":"#F0FDF4",
                          border:`2px solid ${p.accountType==="PRINCIPAL"?"#86EFAC":p.accountType==="DEPENDANT"?"#DDD6FE":"#86EFAC"}`,
                          display:"flex", alignItems:"center", justifyContent:"center", flexShrink:0 }}>
                          <span style={{ fontSize:12, fontWeight:700, color:badge.color }}>
                            {p.firstName?.[0]}{p.lastName?.[0]}
                          </span>
                        </div>
                        <div>
                          <div style={{ display:"flex", alignItems:"center", gap:6 }}>
                            <span style={{ fontWeight:700, fontSize:14, color:"#0F172A" }}>{p.fullName}</span>
                            {isArchived && <span style={{ fontSize:10, fontWeight:700, background:"#F1F5F9", color:"#64748B", padding:"1px 6px", borderRadius:20 }}>ARCHIVED</span>}
                          </div>
                          {/* Dependant link shown below name */}
                          {p.accountType==="DEPENDANT" && p.principalName && (
                            <div style={{ display:"flex", alignItems:"center", gap:4, fontSize:11, color:"#7C3AED", marginTop:1 }}>
                              <Link size={10}/>
                              {p.relationship?.toLowerCase()||"dependant"} of {p.principalName}
                            </div>
                          )}
                          {p.idNumber && (
                            <div style={{ fontSize:11, color:"#94A3B8" }}>{p.idNumber}</div>
                          )}
                        </div>
                      </div>
                    </td>

                    {/* DOB */}
                    <td style={{ padding:"11px 16px", fontSize:13 }}>
                      <div style={{ color:"#475569" }}>{info?.dob ?? (p.dateOfBirth ?? "—")}</div>
                      {info && <div style={{ fontSize:11, color:"#94A3B8" }}>{info.age} yrs · {info.gender}</div>}
                    </td>

                    {/* Contact */}
                    <td style={{ padding:"11px 16px" }}>
                      {p.phone && <div style={{ display:"flex", alignItems:"center", gap:4, fontSize:12, color:"#64748B" }}><Phone size={11} color="#94A3B8"/>{p.phone}</div>}
                      {p.email && <div style={{ display:"flex", alignItems:"center", gap:4, fontSize:11, color:"#94A3B8" }}><Mail size={11} color="#CBD5E1"/>{p.email}</div>}
                      {!p.phone && !p.email && <span style={{ color:"#CBD5E1" }}>—</span>}
                    </td>

                    {/* Account type badge */}
                    <td style={{ padding:"11px 16px" }}>
                      <span style={{ background:badge.bg, color:badge.color, padding:"3px 10px",
                        borderRadius:20, fontSize:11, fontWeight:700 }}>{badge.label}</span>
                    </td>

                    {/* Last visit */}
                    <td style={{ padding:"11px 16px", fontSize:12, color:"#64748B" }}>
                      {p.lastVisitAt ? fmtDT(p.lastVisitAt) : <span style={{ color:"#CBD5E1" }}>No visits</span>}
                    </td>

                    <td style={{ padding:"11px 16px" }}><ChevronRight size={16} color="#CBD5E1"/></td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* ── Pagination ──────────────────────────────────────────────────── */}
      {totalPages > 1 && (
        <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginTop:16 }}>
          <div style={{ fontSize:13, color:"#64748B" }}>
            Page {page+1} of {totalPages} · {totalElements} patients
          </div>
          <div style={{ display:"flex", gap:6, flexWrap:"wrap" }}>
            <button onClick={() => setPage(p=>Math.max(0,p-1))} disabled={page===0}
              style={{ padding:"6px 12px", border:"1px solid #E2E8F0", borderRadius:7, background:"#fff",
                cursor:page===0?"not-allowed":"pointer", color:page===0?"#CBD5E1":"#374151",
                display:"flex", alignItems:"center", gap:4, fontSize:13 }}>
              <ChevronLeft size={14}/> Prev
            </button>
            {(() => {
              const total = Math.min(totalPages, 7)
              const start = totalPages<=7 ? 0 : page<4 ? 0 : page>totalPages-5 ? totalPages-7 : page-3
              return Array.from({ length: total }).map((_, i) => {
                const pg = start + i
                return (
                  <button key={pg} onClick={() => setPage(pg)}
                    style={{ width:34, height:34, border:"1px solid #E2E8F0", borderRadius:7,
                      background:pg===page?"#1B3A6B":"#fff", color:pg===page?"#fff":"#374151",
                      cursor:"pointer", fontSize:13, fontWeight:pg===page?700:400 }}>
                    {pg+1}
                  </button>
                )
              })
            })()}
            <button onClick={() => setPage(p=>Math.min(totalPages-1,p+1))} disabled={page>=totalPages-1}
              style={{ padding:"6px 12px", border:"1px solid #E2E8F0", borderRadius:7, background:"#fff",
                cursor:page>=totalPages-1?"not-allowed":"pointer", color:page>=totalPages-1?"#CBD5E1":"#374151",
                display:"flex", alignItems:"center", gap:4, fontSize:13 }}>
              Next <ChevronRight size={14}/>
            </button>
          </div>
        </div>
      )}

      {/* ── Register Patient modal ──────────────────────────────────────── */}
      {showCreate && (
        <div style={{ position:"fixed", inset:0, background:"rgba(15,23,42,0.55)",
          display:"flex", alignItems:"center", justifyContent:"center",
          zIndex:1000, backdropFilter:"blur(3px)" }}>
          <div style={{ background:"#fff", borderRadius:16, width:660, maxHeight:"92vh",
            overflowY:"auto", boxShadow:"0 24px 64px rgba(0,0,0,0.22)" }}>

            {/* Header */}
            <div style={{ padding:"24px 28px 0", borderBottom:"1px solid #F1F5F9" }}>
              <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:14 }}>
                <div>
                  <h3 style={{ margin:"0 0 3px", fontSize:18, fontWeight:700, color:"#0F172A" }}>Register patient</h3>
                  <p style={{ margin:0, fontSize:12, color:"#94A3B8" }}>SA ID auto-fills DOB, age and gender</p>
                </div>
                <button onClick={() => setShowCreate(false)}
                  style={{ background:"none", border:"none", cursor:"pointer", color:"#94A3B8", display:"flex" }}>
                  <X size={20}/>
                </button>
              </div>
              {/* Account type toggle */}
              <div style={{ display:"flex", gap:6, marginBottom:20 }}>
                {(["individual","family"] as const).map(t => (
                  <button key={t} onClick={() => setRegType(t)}
                    style={{ display:"flex", alignItems:"center", gap:6, padding:"7px 16px",
                      borderRadius:8, border:`2px solid ${regType===t?"#1B3A6B":"#E2E8F0"}`,
                      background:regType===t?"#EFF6FF":"#fff",
                      color:regType===t?"#1B3A6B":"#64748B",
                      fontWeight:regType===t?600:400, fontSize:13, cursor:"pointer" }}>
                    {t==="individual" ? <User size={14}/> : <Users size={14}/>}
                    {t==="individual" ? "Individual account" : "Family account"}
                  </button>
                ))}
              </div>
            </div>

            <div style={{ padding:"20px 28px" }}>
              {/* Principal form */}
              <Sect title={regType==="family" ? "Principal member" : "Personal information"}>
                <PersonForm form={form} onChange={f} errors={fieldErrors} idInfo={idInfo} FErr={FErr} inp={inp}/>
              </Sect>

              {/* Dependants */}
              {regType==="family" && (
                <Sect title="Dependants — children, parents, grandparents, spouse">
                  {dependants.map((dep, idx) => (
                    <div key={idx} style={{ marginBottom:14, padding:"14px 16px",
                      background:"#F8FAFC", border:"1px solid #E2E8F0", borderRadius:10, position:"relative" }}>
                      <button onClick={() => setDependants(d=>d.filter((_,i)=>i!==idx))}
                        style={{ position:"absolute", top:10, right:10, background:"none",
                          border:"none", cursor:"pointer", color:"#94A3B8", display:"flex" }}>
                        <X size={14}/>
                      </button>
                      <div style={{ fontSize:12, fontWeight:600, color:"#64748B", marginBottom:10 }}>
                        Dependant {idx+1}
                      </div>
                      <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:10 }}>
                        <div>
                          <label style={lbl}>First name *</label>
                          <input value={dep.firstName} onChange={e=>updateDep(idx,"firstName",e.target.value)} placeholder="Alex" style={flatInp}/>
                        </div>
                        <div>
                          <label style={lbl}>Last name *</label>
                          <input value={dep.lastName} onChange={e=>updateDep(idx,"lastName",e.target.value)} placeholder="Smith" style={flatInp}/>
                        </div>
                        <div>
                          <label style={lbl}>SA ID number</label>
                          <input value={dep.idNumber}
                            onChange={e=>{
                              const v=e.target.value.replace(/\D/g,"").slice(0,13)
                              updateDep(idx,"idNumber",v)
                              const info=autofillFromId(v)
                              if (info) { updateDep(idx,"dateOfBirth",info.dateOfBirth); updateDep(idx,"gender",info.gender) }
                            }}
                            placeholder="ID number" inputMode="numeric" style={flatInp}/>
                        </div>
                        <div>
                          <label style={lbl}>Relationship to principal</label>
                          <select value={dep.relationship} onChange={e=>updateDep(idx,"relationship",e.target.value)}
                            style={{ ...flatInp, background:"#fff" }}>
                            {RELATIONSHIPS.map(r=><option key={r} value={r}>{r.charAt(0)+r.slice(1).toLowerCase()}</option>)}
                          </select>
                        </div>
                        <div>
                          <label style={lbl}>Date of birth</label>
                          <input type="date" value={dep.dateOfBirth} onChange={e=>updateDep(idx,"dateOfBirth",e.target.value)} style={flatInp}/>
                        </div>
                        <div>
                          <label style={lbl}>Gender</label>
                          <select value={dep.gender} onChange={e=>updateDep(idx,"gender",e.target.value)} style={{ ...flatInp, background:"#fff" }}>
                            <option value="">Select...</option>
                            {GENDERS.map(g=><option key={g} value={g}>{g.replace("_"," ")}</option>)}
                          </select>
                        </div>
                        <div style={{ gridColumn:"1/-1" }}>
                          <label style={lbl}>Phone</label>
                          <input value={dep.phone} onChange={e=>updateDep(idx,"phone",e.target.value)} placeholder="+27 82 000 0000" style={flatInp}/>
                        </div>
                      </div>
                    </div>
                  ))}
                  <button onClick={() => setDependants(d=>[...d, EMPTY_DEP()])}
                    style={{ display:"flex", alignItems:"center", gap:6, padding:"8px 14px",
                      border:"1px dashed #CBD5E1", borderRadius:8, background:"#F8FAFC",
                      color:"#64748B", fontSize:13, cursor:"pointer" }}>
                    <Plus size={14}/> Add dependant
                  </button>
                </Sect>
              )}

              {apiError && (
                <div style={{ marginBottom:14, padding:"10px 12px", background:"#FEF2F2",
                  border:"1px solid #FECACA", borderRadius:8, fontSize:13, color:"#DC2626",
                  display:"flex", alignItems:"center", gap:8 }}>
                  <AlertCircle size={14}/>{apiError}
                </div>
              )}

              <div style={{ display:"flex", gap:10, justifyContent:"flex-end" }}>
                <button onClick={() => setShowCreate(false)}
                  style={{ padding:"9px 18px", border:"1px solid #E2E8F0", borderRadius:9,
                    background:"#fff", fontSize:14, cursor:"pointer", color:"#374151" }}>
                  Cancel
                </button>
                <button onClick={handleRegister} disabled={createPatient.isPending}
                  style={{ display:"flex", alignItems:"center", gap:7, background:"#1B3A6B",
                    color:"#fff", border:"none", borderRadius:9, padding:"9px 20px",
                    fontSize:14, fontWeight:600, cursor:"pointer" }}>
                  {createPatient.isPending ? "Registering..." :
                    regType==="family" && dependants.length>0
                      ? `Register family (${1+dependants.length} accounts)`
                      : "Register patient"}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

// ── Shared sub-components ─────────────────────────────────────────────────────

function PersonForm({ form, onChange, errors, idInfo, FErr, inp }: { form: PersonForm; onChange: (k: string, v: string) => void; errors: Record<string,string>; idInfo: any; FErr: any; inp: any }) {
  const f = (k: string) => (v: string) => onChange(k, v)
  return (
    <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:14 }}>
      <div>
        <label style={lbl}>First name *</label>
        <input autoFocus value={form.firstName} onChange={e=>onChange("firstName",e.target.value)} placeholder="Jane" style={inp("firstName")}/>
        <FErr k="firstName"/>
      </div>
      <div>
        <label style={lbl}>Last name *</label>
        <input value={form.lastName} onChange={e=>onChange("lastName",e.target.value)} placeholder="Smith" style={inp("lastName")}/>
        <FErr k="lastName"/>
      </div>
      <div style={{ gridColumn:"1/-1" }}>
        <label style={lbl}>SA ID number</label>
        <input value={form.idNumber}
          onChange={e=>onChange("idNumber",e.target.value.replace(/\D/g,"").slice(0,13))}
          placeholder="8501015026083" inputMode="numeric" style={inp("idNumber")}/>
        {form.idNumber?.length===13 && idInfo && (
          <div style={{ marginTop:6, padding:"7px 12px", background:"#F0FDF4",
            border:"1px solid #86EFAC", borderRadius:7, fontSize:12, color:"#166534",
            display:"flex", gap:16 }}>
            <span>✓ Valid</span><span>DOB: {idInfo.dob}</span>
            <span>Age: {idInfo.age}</span><span>{idInfo.gender}</span>
          </div>
        )}
      </div>
      <div>
        <label style={lbl}>Date of birth</label>
        <input type="date" value={form.dateOfBirth} onChange={e=>onChange("dateOfBirth",e.target.value)} style={inp("dateOfBirth")}/>
      </div>
      <div>
        <label style={lbl}>Gender</label>
        <select value={form.gender} onChange={e=>onChange("gender",e.target.value)} style={{ ...inp("gender"), background:"#fff" }}>
          <option value="">Select...</option>
          {GENDERS.map(g=><option key={g} value={g}>{g.replace("_"," ")}</option>)}
        </select>
      </div>
      <div>
        <label style={lbl}>Phone</label>
        <input value={form.phone} onChange={e=>onChange("phone",e.target.value)} placeholder="+27 82 123 4567" style={inp("phone")}/>
        <FErr k="phone"/>
      </div>
      <div>
        <label style={lbl}>Email</label>
        <input value={form.email} onChange={e=>onChange("email",e.target.value)} placeholder="jane@example.com" style={inp("email")}/>
        <FErr k="email"/>
      </div>
      <div>
        <label style={lbl}>Emergency contact name</label>
        <input value={form.emergencyContactName} onChange={e=>onChange("emergencyContactName",e.target.value)} placeholder="John Smith" style={inp("emergencyContactName")}/>
      </div>
      <div>
        <label style={lbl}>Emergency contact phone</label>
        <input value={form.emergencyContactPhone} onChange={e=>onChange("emergencyContactPhone",e.target.value)} placeholder="+27 82 987 6543" style={inp("emergencyContactPhone")}/>
      </div>
    </div>
  )
}

function Sect({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom:20 }}>
      <div style={{ fontSize:10, fontWeight:700, color:"#94A3B8", letterSpacing:"0.07em",
        textTransform:"uppercase" as const, marginBottom:12, paddingBottom:8,
        borderBottom:"1px solid #F1F5F9" }}>{title}</div>
      {children}
    </div>
  )
}

const lbl: React.CSSProperties = { display:"block", fontSize:13, fontWeight:600, color:"#374151", marginBottom:5 }
const flatInp: React.CSSProperties = { width:"100%", padding:"8px 11px", boxSizing:"border-box" as const, border:"1.5px solid #E2E8F0", borderRadius:8, fontSize:13, outline:"none", background:"#fff" }
