// src/pages/clinic/BillingTab.tsx
// Billing & Reports — who paid, who owes, revenue by period/doctor, payment recording
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  BarChart2, TrendingUp, Users, AlertCircle, CheckCircle,
  CreditCard, Plus, X, Download, Filter,
} from "lucide-react"

// ── Types ─────────────────────────────────────────────────────────────────────

interface OutstandingBalance {
  patientId: string; patientName: string; phone?: string
  totalBilled: number; totalPaid: number; balance: number
  oldestUnpaid?: string; claimCount: number
}
interface Payment {
  id: string; patientId: string; patientName?: string
  method: string; amount: number; reference?: string
  recordedAt: string; notes?: string; recordedBy?: string
}
interface RevenuePoint { period: string; consultations: number; grossBilled: number; schemePaid: number; patientPaid: number }

// ── Tokens ────────────────────────────────────────────────────────────────────

const NAVY="#1B3A6B"; const TEAL="#0D9488"; const RED="#DC2626"
const GREEN="#166534"; const AMBER="#D97706"; const GRAY="#64748B"
const BORDER="#E2E8F0"; const LIGHT="#F8FAFC"

const fmtR  = (v?:number) => `R ${((v??0)).toLocaleString("en-ZA",{minimumFractionDigits:2})}`
const fmtDT = (iso?:string) => iso ? new Date(iso).toLocaleDateString("en-ZA",{day:"numeric",month:"short",year:"numeric"}) : "—"
const unwrap= (r:any) => { const p=r.data?.data??r.data; return Array.isArray(p)?p:(p?.content??[]) }

const PAYMENT_METHODS = ["CASH","EFT","CARD","MEDICAL_AID","FAMILY_ACCOUNT"]

// ── Component ─────────────────────────────────────────────────────────────────

export default function BillingTab() {
  const qc = useQueryClient()
  const [activeView, setActiveView] = useState<"outstanding"|"payments"|"revenue">("outstanding")
  const [period, setPeriod]         = useState("month")
  const [showPayment, setShowPayment] = useState(false)
  const [payForm, setPayForm]       = useState({ patientId:"", method:"EFT", amount:"", reference:"", notes:"" })
  const [payError, setPayError]     = useState("")
  const [searchOut, setSearchOut]   = useState("")

  // Outstanding balances
  const { data: outstanding=[], isLoading: loadingOut } = useQuery<OutstandingBalance[]>({
    queryKey: ["billing-outstanding"],
    queryFn: async () => {
      try { return unwrap(await apiClient.get("/api/v1/clinic/billing/outstanding")) } catch { return [] }
    },
  })

  // Recent payments
  const { data: payments=[], isLoading: loadingPay } = useQuery<Payment[]>({
    queryKey: ["billing-payments", period],
    queryFn: async () => {
      try { return unwrap(await apiClient.get(`/api/v1/clinic/billing/payments?period=${period}`)) } catch { return [] }
    },
  })

  // Revenue data
  const { data: revenue=[], isLoading: loadingRev } = useQuery<RevenuePoint[]>({
    queryKey: ["billing-revenue", period],
    queryFn: async () => {
      try { return unwrap(await apiClient.get(`/api/v1/clinic/billing/revenue?period=${period}`)) } catch { return [] }
    },
  })

  const { data: patients=[] } = useQuery({
    queryKey: ["clinic-patients-list"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/clinic/patients?size=200")),
  })

  const recordPayment = useMutation({
    mutationFn: (body:any) => apiClient.post("/api/v1/clinic/billing/payments", body),
    onSuccess: () => {
      qc.invalidateQueries({queryKey:["billing-outstanding"]})
      qc.invalidateQueries({queryKey:["billing-payments"]})
      setShowPayment(false)
      setPayForm({patientId:"",method:"EFT",amount:"",reference:"",notes:""})
      setPayError("")
    },
    onError: (e:any) => setPayError(e.response?.data?.message ?? "Failed to record payment"),
  })

  // Summary KPIs
  const outstandingList = outstanding as OutstandingBalance[]
  const paymentsList    = payments as Payment[]
  const revenueList     = revenue as RevenuePoint[]

  const totalOutstanding = outstandingList.reduce((s,b)=>s+b.balance,0)
  const totalCollected   = paymentsList.reduce((s,p)=>s+p.amount,0)
  const totalBilled      = revenueList.reduce((s,r)=>s+r.grossBilled,0)
  const totalConsults    = revenueList.reduce((s,r)=>s+r.consultations,0)

  const filteredOut = outstandingList.filter(b =>
    !searchOut || b.patientName.toLowerCase().includes(searchOut.toLowerCase())
  )

  // Simple bar chart — pure CSS
  const maxBilled = Math.max(...revenueList.map(r=>r.grossBilled), 1)

  const VIEWS = [
    {id:"outstanding",label:"Who owes",      icon:AlertCircle},
    {id:"payments",   label:"Payments",      icon:CheckCircle},
    {id:"revenue",    label:"Revenue",       icon:BarChart2},
  ] as const

  return (
    <div>
      {/* ── KPI strip ───────────────────────────────────────────────────── */}
      <div style={{display:"grid",gridTemplateColumns:"repeat(4,1fr)",gap:12,marginBottom:24}}>
        {[
          {label:"Outstanding",     value:totalOutstanding, color:RED,   fmt:"rand"},
          {label:"Collected",       value:totalCollected,   color:GREEN, fmt:"rand"},
          {label:"Billed",          value:totalBilled,      color:NAVY,  fmt:"rand"},
          {label:"Consultations",   value:totalConsults,    color:TEAL,  fmt:"num"},
        ].map(k=>(
          <div key={k.label} style={{background:LIGHT,border:`1px solid ${BORDER}`,borderRadius:12,padding:"14px 18px"}}>
            <div style={{fontSize:11,fontWeight:700,color:k.color,textTransform:"uppercase",letterSpacing:"0.06em",marginBottom:6}}>{k.label}</div>
            <div style={{fontSize:22,fontWeight:800,color:k.color}}>
              {k.fmt==="rand" ? fmtR(k.value as number) : k.value}
            </div>
          </div>
        ))}
      </div>

      {/* ── View tabs + controls ─────────────────────────────────────────── */}
      <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginBottom:20,flexWrap:"wrap",gap:10}}>
        <div style={{display:"flex",border:`1px solid ${BORDER}`,borderRadius:9,overflow:"hidden"}}>
          {VIEWS.map(v=>{
            const Icon=v.icon; const active=activeView===v.id
            return (
              <button key={v.id} onClick={()=>setActiveView(v.id)}
                style={{display:"flex",alignItems:"center",gap:6,padding:"8px 16px",border:"none",fontSize:13,cursor:"pointer",
                  background:active?NAVY:"#fff",color:active?"#fff":GRAY,fontWeight:active?600:400}}>
                <Icon size={14}/>{v.label}
              </button>
            )
          })}
        </div>

        <div style={{display:"flex",gap:8,alignItems:"center"}}>
          {activeView!=="outstanding" && (
            <select value={period} onChange={e=>setPeriod(e.target.value)}
              style={{padding:"7px 12px",border:`1px solid ${BORDER}`,borderRadius:8,fontSize:13,outline:"none",background:"#fff"}}>
              <option value="week">This week</option>
              <option value="month">This month</option>
              <option value="quarter">This quarter</option>
              <option value="year">This year</option>
            </select>
          )}
          <button onClick={()=>{setShowPayment(true);setPayError("")}}
            style={{display:"flex",alignItems:"center",gap:6,background:TEAL,color:"#fff",border:"none",borderRadius:8,padding:"8px 14px",fontSize:13,fontWeight:600,cursor:"pointer"}}>
            <Plus size={14}/> Record payment
          </button>
        </div>
      </div>

      {/* ── OUTSTANDING VIEW ─────────────────────────────────────────────── */}
      {activeView==="outstanding" && (
        <div>
          {/* Search */}
          <input value={searchOut} onChange={e=>setSearchOut(e.target.value)}
            placeholder="Search patient..."
            style={{width:"100%",padding:"9px 12px",boxSizing:"border-box" as const,border:`1px solid ${BORDER}`,borderRadius:9,fontSize:13,outline:"none",marginBottom:14}}/>

          {loadingOut ? <Loading/> : filteredOut.length===0 ? (
            <Empty icon={CheckCircle} msg={searchOut?"No matches":"No outstanding balances"} color={GREEN}/>
          ) : (
            <div style={{border:`1px solid ${BORDER}`,borderRadius:12,overflow:"hidden"}}>
              <table style={{width:"100%",borderCollapse:"collapse"}}>
                <thead>
                  <tr style={{background:LIGHT,borderBottom:`1px solid ${BORDER}`}}>
                    {["Patient","Contact","Billed","Paid","Outstanding","Claims","Action"].map(h=>(
                      <th key={h} style={{padding:"10px 16px",textAlign:"left",fontSize:11,fontWeight:700,color:GRAY,letterSpacing:"0.05em"}}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {filteredOut.sort((a,b)=>b.balance-a.balance).map((b,i)=>(
                    <tr key={b.patientId} style={{borderBottom:i<filteredOut.length-1?`1px solid #F1F5F9`:"none"}}>
                      <td style={{padding:"12px 16px"}}>
                        <div style={{fontWeight:700,fontSize:14,color:"#0F172A"}}>{b.patientName}</div>
                        {b.oldestUnpaid && <div style={{fontSize:11,color:AMBER}}>Since {fmtDT(b.oldestUnpaid)}</div>}
                      </td>
                      <td style={{padding:"12px 16px",fontSize:13,color:GRAY}}>{b.phone||"—"}</td>
                      <td style={{padding:"12px 16px",fontSize:13,color:"#0F172A"}}>{fmtR(b.totalBilled)}</td>
                      <td style={{padding:"12px 16px",fontSize:13,color:GREEN,fontWeight:600}}>{fmtR(b.totalPaid)}</td>
                      <td style={{padding:"12px 16px"}}>
                        <span style={{fontSize:14,fontWeight:800,color:b.balance>0?RED:GREEN}}>{fmtR(b.balance)}</span>
                      </td>
                      <td style={{padding:"12px 16px",fontSize:13,color:GRAY}}>{b.claimCount}</td>
                      <td style={{padding:"12px 16px"}}>
                        <div style={{display:"flex",gap:6}}>
                          <button onClick={()=>{setPayForm(f=>({...f,patientId:b.patientId}));setShowPayment(true)}}
                            style={{padding:"5px 12px",border:`1px solid ${TEAL}`,borderRadius:7,background:"#F0FDF4",color:TEAL,fontSize:12,fontWeight:600,cursor:"pointer"}}>
                            Pay
                          </button>
                          {/* FIX: "no patient statement of account" gap. */}
                          <button onClick={async()=>{
                            try {
                              const res = await apiClient.get(`/api/v1/clinic/billing/patients/${b.patientId}/statement-pdf`, { responseType: "blob" } as any)
                              const url = URL.createObjectURL(new Blob([res.data], { type: "application/pdf" }))
                              const link = document.createElement("a")
                              link.href = url; link.download = `statement-${b.patientId}.pdf`; link.click()
                              setTimeout(() => URL.revokeObjectURL(url), 60_000)
                            } catch (e) { console.error("Failed to download statement", e) }
                          }}
                            style={{display:"flex",alignItems:"center",gap:4,padding:"5px 12px",border:`1px solid ${BORDER}`,borderRadius:7,background:"#fff",color:"#1B3A6B",fontSize:12,fontWeight:600,cursor:"pointer"}}>
                            <Download size={12}/> Statement
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* ── PAYMENTS VIEW ────────────────────────────────────────────────── */}
      {activeView==="payments" && (
        <div>
          {loadingPay ? <Loading/> : paymentsList.length===0 ? <Empty icon={CreditCard} msg="No payments recorded yet"/> : (
            <div style={{border:`1px solid ${BORDER}`,borderRadius:12,overflow:"hidden"}}>
              <table style={{width:"100%",borderCollapse:"collapse"}}>
                <thead>
                  <tr style={{background:LIGHT,borderBottom:`1px solid ${BORDER}`}}>
                    {["Date","Patient","Method","Amount","Reference","Notes"].map(h=>(
                      <th key={h} style={{padding:"10px 16px",textAlign:"left",fontSize:11,fontWeight:700,color:GRAY,letterSpacing:"0.05em"}}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {paymentsList.sort((a,b)=>b.recordedAt.localeCompare(a.recordedAt)).map((p,i)=>{
                    const methodColor: Record<string,string> = {CASH:"#166534",EFT:"#1D4ED8",CARD:"#7C3AED",MEDICAL_AID:"#D97706",FAMILY_ACCOUNT:"#0D9488"}
                    const color = methodColor[p.method]??GRAY
                    return (
                      <tr key={p.id} style={{borderBottom:i<paymentsList.length-1?`1px solid #F1F5F9`:"none"}}>
                        <td style={{padding:"11px 16px",fontSize:13,color:GRAY}}>{fmtDT(p.recordedAt)}</td>
                        <td style={{padding:"11px 16px",fontWeight:600,fontSize:14,color:"#0F172A"}}>{p.patientName||"—"}</td>
                        <td style={{padding:"11px 16px"}}>
                          <span style={{background:`${color}14`,color,padding:"2px 8px",borderRadius:20,fontSize:11,fontWeight:700}}>
                            {p.method.replace("_"," ")}
                          </span>
                        </td>
                        <td style={{padding:"11px 16px",fontSize:14,fontWeight:700,color:GREEN}}>{fmtR(p.amount)}</td>
                        <td style={{padding:"11px 16px",fontSize:12,color:GRAY}}>{p.reference||"—"}</td>
                        <td style={{padding:"11px 16px",fontSize:12,color:GRAY}}>{p.notes||"—"}</td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
              {/* Total row */}
              <div style={{padding:"12px 16px",background:LIGHT,borderTop:`1px solid ${BORDER}`,display:"flex",justifyContent:"space-between",alignItems:"center"}}>
                <span style={{fontSize:13,fontWeight:600,color:GRAY}}>{paymentsList.length} payments</span>
                <span style={{fontSize:16,fontWeight:800,color:GREEN}}>{fmtR(totalCollected)}</span>
              </div>
            </div>
          )}
        </div>
      )}

      {/* ── REVENUE VIEW ─────────────────────────────────────────────────── */}
      {activeView==="revenue" && (
        <div>
          {loadingRev ? <Loading/> : revenueList.length===0 ? <Empty icon={BarChart2} msg="No revenue data for this period"/> : (
            <>
              {/* Bar chart */}
              <div style={{border:`1px solid ${BORDER}`,borderRadius:12,padding:24,marginBottom:20}}>
                <div style={{fontSize:13,fontWeight:700,color:"#0F172A",marginBottom:16}}>Revenue by period</div>
                {/*
                  FIX: "revenue chart overlapping" — with up to 30 daily
                  points (this codebase's getRevenue() buckets "month" as a
                  trailing 30-day window, not 6-12 monthly points), the
                  original flex:1/no-min-width columns got squeezed
                  illegibly narrow, and every single bar printed its own
                  amount + date + consult-count label with no thinning,
                  guaranteeing overlap at this density. Fix: horizontal
                  scroll + a real minimum bar width so bars stay legible at
                  any count, the redundant per-bar amount label is dropped
                  (the table below already shows exact figures per period),
                  and the date/consult labels only render on a thinned-out
                  subset of bars once there are more than ~10 points.
                */}
                <div style={{overflowX:"auto",paddingBottom:4}}>
                  <div style={{display:"flex",gap:6,alignItems:"flex-end",height:140,minWidth:revenueList.length*38}}>
                    {revenueList.map((r,i)=>{
                      const h = Math.max((r.grossBilled/maxBilled)*120, 4)
                      const sh = Math.max((r.schemePaid/maxBilled)*120, 0)
                      const ph = Math.max((r.patientPaid/maxBilled)*120, 0)
                      // Thin labels once there are more than ~10 bars, so text
                      // never has less room than it needs to render legibly —
                      // the bars themselves still render for every point.
                      const labelEvery = revenueList.length > 20 ? 5 : revenueList.length > 10 ? 3 : 1
                      const showLabel = i % labelEvery === 0 || i === revenueList.length - 1
                      return (
                        <div key={r.period} title={`${r.period} · ${fmtR(r.grossBilled)} · ${r.consultations} consults`}
                          style={{flex:"0 0 32px",display:"flex",flexDirection:"column",alignItems:"center",gap:4}}>
                          <div style={{width:"100%",display:"flex",gap:2,alignItems:"flex-end",height:120}}>
                            <div style={{flex:1,height:h,background:NAVY,borderRadius:"3px 3px 0 0",minHeight:2}}/>
                            <div style={{flex:1,height:sh,background:TEAL,borderRadius:"3px 3px 0 0",minHeight:2}}/>
                            <div style={{flex:1,height:ph,background:AMBER,borderRadius:"3px 3px 0 0",minHeight:2}}/>
                          </div>
                          {showLabel && (
                            <div style={{fontSize:9,color:GRAY,textAlign:"center" as const,whiteSpace:"nowrap"}}>{r.period}</div>
                          )}
                        </div>
                      )
                    })}
                  </div>
                </div>
                <div style={{fontSize:10,color:GRAY,marginTop:4}}>Hover a bar for exact figures · full breakdown in the table below</div>
                <div style={{display:"flex",gap:16,marginTop:12,justifyContent:"center"}}>
                  {[{color:NAVY,label:"Gross billed"},{color:TEAL,label:"Scheme paid"},{color:AMBER,label:"Patient paid"}].map(l=>(
                    <div key={l.label} style={{display:"flex",alignItems:"center",gap:4}}>
                      <div style={{width:10,height:10,borderRadius:2,background:l.color}}/>
                      <span style={{fontSize:11,color:GRAY}}>{l.label}</span>
                    </div>
                  ))}
                </div>
              </div>

              {/* Revenue table */}
              <div style={{border:`1px solid ${BORDER}`,borderRadius:12,overflow:"hidden"}}>
                <table style={{width:"100%",borderCollapse:"collapse"}}>
                  <thead>
                    <tr style={{background:LIGHT,borderBottom:`1px solid ${BORDER}`}}>
                      {["Period","Consultations","Gross billed","Scheme paid","Patient paid","Collection rate"].map(h=>(
                        <th key={h} style={{padding:"10px 16px",textAlign:"left",fontSize:11,fontWeight:700,color:GRAY,letterSpacing:"0.05em"}}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {revenueList.map((r,i)=>{
                      const rate = r.grossBilled > 0 ? ((r.schemePaid+r.patientPaid)/r.grossBilled*100).toFixed(0) : "0"
                      return (
                        <tr key={r.period} style={{borderBottom:i<revenueList.length-1?`1px solid #F1F5F9`:"none"}}>
                          <td style={{padding:"11px 16px",fontWeight:600,fontSize:14,color:"#0F172A"}}>{r.period}</td>
                          <td style={{padding:"11px 16px",fontSize:13,color:GRAY}}>{r.consultations}</td>
                          <td style={{padding:"11px 16px",fontSize:13,fontWeight:700,color:"#0F172A"}}>{fmtR(r.grossBilled)}</td>
                          <td style={{padding:"11px 16px",fontSize:13,color:TEAL,fontWeight:600}}>{fmtR(r.schemePaid)}</td>
                          <td style={{padding:"11px 16px",fontSize:13,color:AMBER,fontWeight:600}}>{fmtR(r.patientPaid)}</td>
                          <td style={{padding:"11px 16px"}}>
                            <div style={{display:"flex",alignItems:"center",gap:8}}>
                              <div style={{flex:1,height:6,background:"#F1F5F9",borderRadius:3}}>
                                <div style={{width:`${rate}%`,height:"100%",background:+rate>=80?GREEN:+rate>=50?AMBER:RED,borderRadius:3}}/>
                              </div>
                              <span style={{fontSize:12,fontWeight:700,color:+rate>=80?GREEN:+rate>=50?AMBER:RED}}>{rate}%</span>
                            </div>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                  {/* Totals */}
                  <tfoot>
                    <tr style={{borderTop:`2px solid ${BORDER}`,background:LIGHT}}>
                      <td style={{padding:"11px 16px",fontWeight:700,fontSize:13,color:"#0F172A"}}>Total</td>
                      <td style={{padding:"11px 16px",fontSize:13,fontWeight:600}}>{totalConsults}</td>
                      <td style={{padding:"11px 16px",fontSize:13,fontWeight:800,color:"#0F172A"}}>{fmtR(totalBilled)}</td>
                      <td style={{padding:"11px 16px",fontSize:13,fontWeight:700,color:TEAL}}>{fmtR(revenueList.reduce((s,r)=>s+r.schemePaid,0))}</td>
                      <td style={{padding:"11px 16px",fontSize:13,fontWeight:700,color:AMBER}}>{fmtR(revenueList.reduce((s,r)=>s+r.patientPaid,0))}</td>
                      <td style={{padding:"11px 16px"}}/>
                    </tr>
                  </tfoot>
                </table>
              </div>
            </>
          )}
        </div>
      )}

      {/* ── Record payment modal ─────────────────────────────────────────── */}
      {showPayment && (
        <div style={{position:"fixed",inset:0,background:"rgba(15,23,42,0.55)",display:"flex",alignItems:"center",justifyContent:"center",zIndex:1000,backdropFilter:"blur(3px)"}}>
          <div style={{background:"#fff",borderRadius:16,padding:28,width:460,boxShadow:"0 24px 64px rgba(0,0,0,0.22)"}}>
            <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginBottom:22}}>
              <h3 style={{margin:0,fontSize:17,fontWeight:700,color:"#0F172A"}}>Record payment</h3>
              <button onClick={()=>setShowPayment(false)} style={{background:"none",border:"none",cursor:"pointer",color:GRAY,display:"flex"}}><X size={20}/></button>
            </div>
            <div style={{display:"flex",flexDirection:"column",gap:14}}>
              <div>
                <label style={lbl}>Patient *</label>
                <select value={payForm.patientId} onChange={e=>setPayForm(f=>({...f,patientId:e.target.value}))} style={sinp}>
                  <option value="">Select patient...</option>
                  {(patients as any[]).map((p:any)=><option key={p.id} value={p.id}>{p.fullName}</option>)}
                </select>
              </div>
              <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:12}}>
                <div>
                  <label style={lbl}>Payment method *</label>
                  <select value={payForm.method} onChange={e=>setPayForm(f=>({...f,method:e.target.value}))} style={sinp}>
                    {PAYMENT_METHODS.map(m=><option key={m} value={m}>{m.replace("_"," ")}</option>)}
                  </select>
                </div>
                <div>
                  <label style={lbl}>Amount (R) *</label>
                  <input type="number" step="0.01" value={payForm.amount} onChange={e=>setPayForm(f=>({...f,amount:e.target.value}))} placeholder="0.00" style={sinp}/>
                </div>
              </div>
              <div>
                <label style={lbl}>Reference / receipt number</label>
                <input value={payForm.reference} onChange={e=>setPayForm(f=>({...f,reference:e.target.value}))} placeholder="e.g. EFT123456" style={sinp}/>
              </div>
              <div>
                <label style={lbl}>Notes</label>
                <input value={payForm.notes} onChange={e=>setPayForm(f=>({...f,notes:e.target.value}))} placeholder="Optional" style={sinp}/>
              </div>
              {/* Quick method badges */}
              <div style={{display:"flex",gap:6,flexWrap:"wrap"}}>
                {PAYMENT_METHODS.map(m=>{
                  const colors:Record<string,string> = {CASH:GREEN,EFT:"#1D4ED8",CARD:"#7C3AED",MEDICAL_AID:AMBER,FAMILY_ACCOUNT:TEAL}
                  const c=colors[m]??GRAY
                  return (
                    <button key={m} onClick={()=>setPayForm(f=>({...f,method:m}))}
                      style={{padding:"4px 12px",borderRadius:20,border:`1.5px solid ${payForm.method===m?c:BORDER}`,
                        background:payForm.method===m?`${c}14`:"#fff",color:payForm.method===m?c:GRAY,
                        fontSize:12,fontWeight:payForm.method===m?700:400,cursor:"pointer"}}>
                      {m.replace("_"," ")}
                    </button>
                  )
                })}
              </div>
            </div>
            {payError && <div style={{marginTop:12,padding:"8px 12px",background:"#FEF2F2",border:"1px solid #FECACA",borderRadius:8,fontSize:13,color:RED,display:"flex",alignItems:"center",gap:8}}><AlertCircle size={13}/>{payError}</div>}
            <div style={{display:"flex",gap:10,justifyContent:"flex-end",marginTop:20}}>
              <button onClick={()=>setShowPayment(false)} style={btnCancel}>Cancel</button>
              <button onClick={()=>{
                if (!payForm.patientId||!payForm.amount) { setPayError("Patient and amount are required"); return }
                recordPayment.mutate({...payForm, amount:parseFloat(payForm.amount)})
              }} disabled={recordPayment.isPending} style={{...btnPrimary,background:TEAL}}>
                {recordPayment.isPending ? "Recording..." : "Record payment"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

// ── Shared ────────────────────────────────────────────────────────────────────

function Loading() { return <div style={{textAlign:"center",padding:40,color:GRAY}}>Loading...</div> }
function Empty({icon:Icon,msg,color=GRAY}:{icon:React.ElementType;msg:string;color?:string}) {
  return <div style={{textAlign:"center",padding:"60px 20px",color:GRAY,border:`1px dashed ${BORDER}`,borderRadius:12}}><Icon size={36} color={color} style={{marginBottom:12,opacity:0.4}}/><div style={{fontWeight:600,color:"#475569",fontSize:15}}>{msg}</div></div>
}
const lbl:React.CSSProperties      = {display:"block",fontSize:13,fontWeight:600,color:"#374151",marginBottom:5}
const sinp:React.CSSProperties     = {width:"100%",padding:"9px 12px",boxSizing:"border-box" as const,border:`1.5px solid ${BORDER}`,borderRadius:8,fontSize:14,outline:"none",background:"#fff"}
const btnPrimary:React.CSSProperties = {background:NAVY,color:"#fff",border:"none",borderRadius:9,padding:"9px 20px",fontSize:13,fontWeight:600,cursor:"pointer"}
const btnCancel:React.CSSProperties  = {padding:"9px 18px",border:`1px solid ${BORDER}`,borderRadius:9,background:"#fff",fontSize:13,cursor:"pointer",color:"#374151"}
