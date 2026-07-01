// src/pages/security/PayrollTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { DollarSign, Plus, CheckCircle, Download, AlertTriangle, List } from "lucide-react"

interface PayrollPeriod {
  id: string; name: string; periodType: string
  periodStart: string; periodEnd: string
  status: "DRAFT" | "APPROVED" | "EXPORTED" | "PAID"
  totalHours: number | null; totalAmountCents: number | null; totalAmountZar: number | null
  lineItemCount: number; approvedBy: string | null; approvedAt: string | null
  exportedAt: string | null; exportFormat: string | null; createdAt: string
}
interface LineItem {
  id: string; guardId: string; lineType: "REGULAR" | "OVERTIME" | "ALLOWANCE" | "DEDUCTION"
  shiftStartAt: string; shiftEndAt: string
  hoursWorked: number; overtimeHours: number
  hourlyRateCents: number; overtimeRateCents: number; grossAmountCents: number
}
interface GradeRate {
  id: string; grade: string; hourlyRateCents: number
  standardHoursPerDay: number; effectiveFrom: string
}

const fmtDate  = (d: string | null) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const fmtZar   = (c: number | null) => c != null ? `R ${(c/100).toFixed(2)}` : "—"
const fmtHours = (h: number | null) => h != null ? `${Number(h).toFixed(1)} h` : "—"

const STATUS_CFG = {
  DRAFT:    { label: "Draft",    color: "#92400E", bg: "#FEF3C7" },
  APPROVED: { label: "Approved", color: "#166534", bg: "#DCFCE7" },
  EXPORTED: { label: "Exported", color: "#1D4ED8", bg: "#EFF6FF" },
  PAID:     { label: "Paid",     color: "#6B7280", bg: "#F3F4F6" },
}
const TYPE_CFG = {
  REGULAR:   { label: "Regular",   color: "#1D4ED8", bg: "#EFF6FF" },
  OVERTIME:  { label: "Overtime",  color: "#92400E", bg: "#FEF3C7" },
  ALLOWANCE: { label: "Allowance", color: "#166534", bg: "#DCFCE7" },
  DEDUCTION: { label: "Deduction", color: "#991B1B", bg: "#FEF2F2" },
}

const inp = { width:"100%", padding:"8px 12px", borderRadius:8, border:"1px solid #E2E8F0", fontSize:13, outline:"none", boxSizing:"border-box" as const }
const btn = (bg: string, color="white") => ({ padding:"8px 16px", borderRadius:8, border:"none", background:bg, color, fontSize:13, cursor:"pointer", fontWeight:600 })
const sbtn = { padding:"8px 16px", borderRadius:8, border:"1px solid #E2E8F0", background:"#fff", fontSize:13, cursor:"pointer", color:"#374151" }

export default function PayrollTab() {
  const qc = useQueryClient()
  const [view, setView] = useState<"periods"|"create"|"lines"|"grades"|"add-grade">("periods")
  const [selected, setSelected] = useState<PayrollPeriod | null>(null)
  const [err, setErr] = useState("")
  const [form, setForm] = useState({ name:"", periodType:"MONTHLY", periodStart:"", periodEnd:"" })
  const [gForm, setGForm] = useState({ grade:"D", hourlyRateCents:"", standardHoursPerDay:"9", effectiveFrom:new Date().toISOString().slice(0,10) })

  const { data: periods = [], isLoading } = useQuery<PayrollPeriod[]>({
    queryKey: ["payroll-periods"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/security/payroll/periods?size=50&sort=periodStart,desc")
      const p = r.data?.data ?? r.data; return (p?.content ?? p) as PayrollPeriod[]
    },
  })
  const { data: lines = [] } = useQuery<LineItem[]>({
    queryKey: ["payroll-lines", selected?.id],
    enabled: !!selected && view === "lines",
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/security/payroll/periods/${selected!.id}/lines`)
      return (r.data?.data ?? r.data) as LineItem[]
    },
  })
  const { data: gradeRates = [] } = useQuery<GradeRate[]>({
    queryKey: ["grade-rates"],
    enabled: view === "grades" || view === "add-grade",
    queryFn: async () => { const r = await apiClient.get("/api/v1/security/payroll/grade-rates"); return (r.data?.data ?? r.data) as GradeRate[] },
  })

  const createMut = useMutation({
    mutationFn: (b: object) => apiClient.post("/api/v1/security/payroll/periods", b),
    onSuccess: () => { qc.invalidateQueries({queryKey:["payroll-periods"]}); setView("periods"); setErr("") },
    onError:   (e: any) => setErr(e.response?.data?.message ?? "Failed to create period"),
  })
  const approveMut = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/security/payroll/periods/${id}/approve`),
    onSuccess: () => { qc.invalidateQueries({queryKey:["payroll-periods"]}); setErr("") },
    onError:   (e: any) => setErr(e.response?.data?.message ?? "Approval failed — ensure grade rates are configured and shifts are COMPLETED"),
  })
  const paidMut = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/security/payroll/periods/${id}/mark-paid`),
    onSuccess: () => { qc.invalidateQueries({queryKey:["payroll-periods"]}); setErr("") },
    onError:   (e: any) => setErr(e.response?.data?.message ?? "Failed"),
  })
  const gradeMut = useMutation({
    mutationFn: (b: object) => apiClient.post("/api/v1/security/payroll/grade-rates", b),
    onSuccess: () => { qc.invalidateQueries({queryKey:["grade-rates"]}); setView("grades"); setErr("") },
    onError:   (e: any) => setErr(e.response?.data?.message ?? "Failed"),
  })

  async function downloadCsv(p: PayrollPeriod) {
    try {
      const r = await apiClient.get(`/api/v1/security/payroll/periods/${p.id}/export/csv`, { responseType:"blob" })
      const url = URL.createObjectURL(new Blob([r.data])); const a = document.createElement("a")
      a.href = url; a.download = `payroll-${p.name.replace(/\s+/g,"-")}.csv`; a.click(); URL.revokeObjectURL(url)
      qc.invalidateQueries({queryKey:["payroll-periods"]})
    } catch (e: any) { setErr(e.response?.data?.message ?? "Export failed") }
  }

  const th = (label: string) => (
    <th key={label} style={{textAlign:"left",padding:"9px 14px",fontSize:11,fontWeight:700,color:"#94A3B8",textTransform:"uppercase" as const,letterSpacing:"0.5px"}}>{label}</th>
  )

  return (
    <div style={{fontFamily:"'Inter',system-ui,sans-serif"}}>
      {/* Header */}
      <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginBottom:20}}>
        <div style={{display:"flex",alignItems:"center",gap:10}}>
          <div style={{background:"#1B3A6B",borderRadius:10,padding:8}}><DollarSign size={18} color="#fff"/></div>
          <div>
            <h2 style={{margin:0,fontSize:18,fontWeight:700,color:"#0F172A"}}>Payroll Export</h2>
            <p style={{margin:0,fontSize:12,color:"#94A3B8"}}>Periods · Approvals · CSV for Sage / VIP Payroll</p>
          </div>
        </div>
        <div style={{display:"flex",gap:8}}>
          <button style={sbtn} onClick={()=>{setView("grades");setErr("")}}>Grade Rates</button>
          <button style={{...btn("#1B3A6B"),display:"flex",alignItems:"center",gap:6}} onClick={()=>{setView("create");setErr("")}}>
            <Plus size={14}/> New Period
          </button>
        </div>
      </div>

      {err && (
        <div style={{background:"#FEF2F2",border:"1px solid #FECACA",borderRadius:8,padding:"10px 14px",marginBottom:16,fontSize:13,color:"#991B1B",display:"flex",alignItems:"center",gap:8}}>
          <AlertTriangle size={14}/> {err}
        </div>
      )}

      {/* CREATE */}
      {view === "create" && (
        <div style={{background:"#fff",border:"1px solid #E2E8F0",borderRadius:12,padding:24}}>
          <h3 style={{margin:"0 0 20px",fontSize:16,fontWeight:700,color:"#0F172A"}}>New Payroll Period</h3>
          <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:16,marginBottom:20}}>
            {([
              {label:"Period Name",      key:"name",        type:"text",   ph:"e.g. June 2026 – Week 2"},
              {label:"Period Type",      key:"periodType",  type:"select", opts:["WEEKLY","BIWEEKLY","MONTHLY"]},
              {label:"Period Start",     key:"periodStart", type:"date",   ph:""},
              {label:"Period End",       key:"periodEnd",   type:"date",   ph:""},
            ] as any[]).map(f => (
              <div key={f.key}>
                <label style={{display:"block",fontSize:12,fontWeight:600,color:"#374151",marginBottom:5}}>{f.label}</label>
                {f.type === "select"
                  ? <select value={(form as any)[f.key]} onChange={e=>setForm(p=>({...p,[f.key]:e.target.value}))} style={{...inp,background:"#fff"}}>
                      {f.opts.map((o: string)=><option key={o} value={o}>{o.charAt(0)+o.slice(1).toLowerCase()}</option>)}
                    </select>
                  : <input type={f.type} placeholder={f.ph} value={(form as any)[f.key]} onChange={e=>setForm(p=>({...p,[f.key]:e.target.value}))} style={inp}/>
                }
              </div>
            ))}
          </div>
          <div style={{display:"flex",gap:8}}>
            <button style={sbtn} onClick={()=>setView("periods")}>Cancel</button>
            <button style={{...btn("#1B3A6B"),opacity:(!form.name||!form.periodStart||!form.periodEnd)?0.5:1}}
              disabled={!form.name||!form.periodStart||!form.periodEnd}
              onClick={()=>createMut.mutate(form)}>Create Period</button>
          </div>
        </div>
      )}

      {/* GRADE RATES */}
      {(view === "grades" || view === "add-grade") && (
        <div style={{background:"#fff",border:"1px solid #E2E8F0",borderRadius:12,padding:24}}>
          <div style={{display:"flex",justifyContent:"space-between",alignItems:"flex-start",marginBottom:20}}>
            <div>
              <h3 style={{margin:0,fontSize:16,fontWeight:700,color:"#0F172A"}}>Grade Rates</h3>
              <p style={{margin:"2px 0 0",fontSize:12,color:"#94A3B8"}}>PSiRA grade default hourly rate — fallback when no per-guard override is set</p>
            </div>
            <div style={{display:"flex",gap:8}}>
              <button style={sbtn} onClick={()=>setView("periods")}>← Back</button>
              <button style={btn("#1B3A6B")} onClick={()=>setView("add-grade")}>Set Rate</button>
            </div>
          </div>
          {view === "add-grade" && (
            <div style={{background:"#F8FAFC",border:"1px solid #E2E8F0",borderRadius:10,padding:20,marginBottom:20}}>
              <h4 style={{margin:"0 0 14px",fontSize:14,fontWeight:700,color:"#0F172A"}}>New Grade Rate</h4>
              <div style={{display:"grid",gridTemplateColumns:"repeat(4,1fr)",gap:12,marginBottom:14}}>
                {([
                  {label:"Grade",            key:"grade",               type:"select", opts:["A","B","C","D","E"]},
                  {label:"Rate (ZAR cents)",  key:"hourlyRateCents",    type:"number", ph:"3500 = R35.00"},
                  {label:"Std Hours / Day",  key:"standardHoursPerDay", type:"number", ph:"9"},
                  {label:"Effective From",   key:"effectiveFrom",       type:"date",   ph:""},
                ] as any[]).map(f => (
                  <div key={f.key}>
                    <label style={{display:"block",fontSize:12,fontWeight:600,color:"#374151",marginBottom:5}}>{f.label}</label>
                    {f.type==="select"
                      ? <select value={(gForm as any)[f.key]} onChange={e=>setGForm(p=>({...p,[f.key]:e.target.value}))} style={{...inp,background:"#fff"}}>
                          {f.opts.map((o:string)=><option key={o} value={o}>Grade {o}</option>)}
                        </select>
                      : <input type={f.type} placeholder={f.ph} value={(gForm as any)[f.key]} onChange={e=>setGForm(p=>({...p,[f.key]:e.target.value}))} style={inp}/>
                    }
                  </div>
                ))}
              </div>
              <div style={{display:"flex",gap:8}}>
                <button style={sbtn} onClick={()=>setView("grades")}>Cancel</button>
                <button style={{...btn("#166534"),opacity:!gForm.hourlyRateCents?0.5:1}} disabled={!gForm.hourlyRateCents}
                  onClick={()=>gradeMut.mutate({...gForm,hourlyRateCents:Number(gForm.hourlyRateCents),standardHoursPerDay:Number(gForm.standardHoursPerDay)})}>
                  Save Rate
                </button>
              </div>
            </div>
          )}
          {gradeRates.length===0
            ? <p style={{textAlign:"center",color:"#94A3B8",padding:"24px 0",fontSize:13}}>No rates yet — set at least one before approving a period.</p>
            : <table style={{width:"100%",borderCollapse:"collapse",fontSize:13}}>
                <thead><tr style={{borderBottom:"2px solid #E2E8F0",background:"#F8FAFC"}}>
                  {["Grade","Hourly Rate","Std Hours / Day","Effective From"].map(th)}
                </tr></thead>
                <tbody>{gradeRates.map(r=>(
                  <tr key={r.id} style={{borderBottom:"1px solid #F1F5F9"}}>
                    <td style={{padding:"10px 14px",fontWeight:700,color:"#0F172A"}}>Grade {r.grade}</td>
                    <td style={{padding:"10px 14px",fontWeight:600,color:"#166534"}}>{fmtZar(r.hourlyRateCents)} / hr</td>
                    <td style={{padding:"10px 14px",color:"#374151"}}>{r.standardHoursPerDay} h</td>
                    <td style={{padding:"10px 14px",color:"#64748B"}}>{fmtDate(r.effectiveFrom)}</td>
                  </tr>
                ))}</tbody>
              </table>
          }
        </div>
      )}

      {/* LINE ITEMS */}
      {view==="lines" && selected && (
        <div>
          <div style={{display:"flex",alignItems:"center",gap:10,marginBottom:20,flexWrap:"wrap" as const}}>
            <button style={sbtn} onClick={()=>setView("periods")}>← Periods</button>
            <div style={{flex:1}}>
              <h3 style={{margin:0,fontSize:16,fontWeight:700,color:"#0F172A"}}>{selected.name}</h3>
              <p style={{margin:0,fontSize:12,color:"#64748B"}}>{fmtDate(selected.periodStart)} – {fmtDate(selected.periodEnd)} · {lines.length} line items · {fmtZar(selected.totalAmountCents)} total</p>
            </div>
            {(selected.status==="APPROVED"||selected.status==="EXPORTED") && (
              <button style={{...btn("#166534"),display:"flex",alignItems:"center",gap:6}} onClick={()=>downloadCsv(selected)}>
                <Download size={14}/> Export CSV
              </button>
            )}
            {selected.status==="EXPORTED" && (
              <button style={btn("#1D4ED8")} onClick={()=>paidMut.mutate(selected.id)}>Mark Paid</button>
            )}
          </div>
          {lines.length===0
            ? <p style={{textAlign:"center",color:"#94A3B8",padding:"40px 0",fontSize:13}}>No line items — approve the period first to compute them.</p>
            : <div style={{background:"#fff",border:"1px solid #E2E8F0",borderRadius:12,overflow:"hidden"}}>
                <table style={{width:"100%",borderCollapse:"collapse",fontSize:13}}>
                  <thead><tr style={{borderBottom:"2px solid #E2E8F0",background:"#F8FAFC"}}>
                    {["Type","Shift Date","Reg Hours","OT Hours","Rate / hr","OT Rate / hr","Gross"].map(th)}
                  </tr></thead>
                  <tbody>{lines.map(li=>{
                    const tc = TYPE_CFG[li.lineType] ?? TYPE_CFG.REGULAR
                    return (
                      <tr key={li.id} style={{borderBottom:"1px solid #F1F5F9"}}>
                        <td style={{padding:"10px 14px"}}>
                          <span style={{background:tc.bg,color:tc.color,borderRadius:6,padding:"2px 8px",fontSize:11,fontWeight:700}}>{tc.label}</span>
                        </td>
                        <td style={{padding:"10px 14px",color:"#374151"}}>{fmtDate(li.shiftStartAt)}</td>
                        <td style={{padding:"10px 14px",color:"#374151"}}>{Number(li.hoursWorked).toFixed(2)}</td>
                        <td style={{padding:"10px 14px",color:"#374151"}}>{Number(li.overtimeHours).toFixed(2)}</td>
                        <td style={{padding:"10px 14px",color:"#374151"}}>{fmtZar(li.hourlyRateCents)}</td>
                        <td style={{padding:"10px 14px",color:"#374151"}}>{li.overtimeRateCents ? fmtZar(li.overtimeRateCents) : "—"}</td>
                        <td style={{padding:"10px 14px",fontWeight:700,color:"#0F172A"}}>{fmtZar(li.grossAmountCents)}</td>
                      </tr>
                    )
                  })}</tbody>
                </table>
              </div>
          }
        </div>
      )}

      {/* PERIODS LIST */}
      {view==="periods" && (
        isLoading
          ? <div style={{textAlign:"center",padding:"40px 0",color:"#94A3B8"}}>Loading…</div>
          : periods.length===0
            ? <div style={{textAlign:"center",padding:"60px 0",color:"#94A3B8"}}>
                <DollarSign size={32} strokeWidth={1.5} style={{margin:"0 auto 12px",display:"block"}}/>
                <p style={{margin:"0 0 4px",fontWeight:600,color:"#374151"}}>No payroll periods yet</p>
                <p style={{margin:0,fontSize:13}}>Create a period, approve it to compute line items, then export to CSV.</p>
              </div>
            : <div style={{background:"#fff",border:"1px solid #E2E8F0",borderRadius:12,overflow:"hidden"}}>
                <table style={{width:"100%",borderCollapse:"collapse",fontSize:13}}>
                  <thead><tr style={{borderBottom:"2px solid #E2E8F0",background:"#F8FAFC"}}>
                    {["Period","Type","Date Range","Status","Hours","Total","Lines","Actions"].map(th)}
                  </tr></thead>
                  <tbody>{periods.map(p=>{
                    const sc = STATUS_CFG[p.status]
                    return (
                      <tr key={p.id} style={{borderBottom:"1px solid #F1F5F9"}}>
                        <td style={{padding:"11px 14px",fontWeight:600,color:"#0F172A"}}>{p.name}</td>
                        <td style={{padding:"11px 14px",color:"#64748B",fontSize:12}}>{p.periodType}</td>
                        <td style={{padding:"11px 14px",color:"#64748B",fontSize:12,whiteSpace:"nowrap" as const}}>{fmtDate(p.periodStart)} – {fmtDate(p.periodEnd)}</td>
                        <td style={{padding:"11px 14px"}}>
                          <span style={{background:sc.bg,color:sc.color,borderRadius:6,padding:"2px 8px",fontSize:11,fontWeight:700}}>{sc.label}</span>
                        </td>
                        <td style={{padding:"11px 14px",color:"#374151"}}>{fmtHours(p.totalHours)}</td>
                        <td style={{padding:"11px 14px",fontWeight:700,color:"#0F172A"}}>{fmtZar(p.totalAmountCents)}</td>
                        <td style={{padding:"11px 14px",color:"#64748B"}}>{p.lineItemCount}</td>
                        <td style={{padding:"11px 14px"}}>
                          <div style={{display:"flex",gap:5,flexWrap:"wrap" as const}}>
                            {p.status==="DRAFT" && (
                              <button disabled={approveMut.isPending}
                                style={{display:"flex",alignItems:"center",gap:4,padding:"5px 10px",borderRadius:7,border:"none",background:"#166534",color:"#fff",fontSize:12,cursor:"pointer",fontWeight:600}}
                                onClick={()=>{setErr("");approveMut.mutate(p.id)}}>
                                <CheckCircle size={12}/> Approve
                              </button>
                            )}
                            {(p.status==="APPROVED"||p.status==="EXPORTED") && (
                              <button style={{display:"flex",alignItems:"center",gap:4,padding:"5px 10px",borderRadius:7,border:"none",background:"#1D4ED8",color:"#fff",fontSize:12,cursor:"pointer",fontWeight:600}}
                                onClick={()=>downloadCsv(p)}>
                                <Download size={12}/> CSV
                              </button>
                            )}
                            {p.status==="EXPORTED" && (
                              <button style={{padding:"5px 10px",borderRadius:7,border:"1px solid #E2E8F0",background:"#fff",fontSize:12,cursor:"pointer",color:"#374151"}}
                                onClick={()=>paidMut.mutate(p.id)}>Mark Paid</button>
                            )}
                            {p.lineItemCount>0 && (
                              <button style={{display:"flex",alignItems:"center",gap:4,padding:"5px 10px",borderRadius:7,border:"1px solid #E2E8F0",background:"#F8FAFC",fontSize:12,cursor:"pointer",color:"#374151"}}
                                onClick={()=>{setSelected(p);setView("lines")}}>
                                <List size={12}/> Lines
                              </button>
                            )}
                          </div>
                        </td>
                      </tr>
                    )
                  })}</tbody>
                </table>
              </div>
      )}
    </div>
  )
}
