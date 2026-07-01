// src/pages/security/BranchesTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { GitBranch, Plus, Pencil, Trash2, AlertTriangle, MapPin } from "lucide-react"

interface Branch {
  id: string; tenantId: string; name: string
  region: string | null; description: string | null
  active: boolean; createdAt: string; updatedAt: string
}

const inp = { width:"100%", padding:"8px 12px", borderRadius:8, border:"1px solid #E2E8F0", fontSize:13, outline:"none", boxSizing:"border-box" as const }
const btn = (bg: string, color="white") => ({ padding:"8px 16px", borderRadius:8, border:"none", background:bg, color, fontSize:13, cursor:"pointer", fontWeight:600 as const })
const sbtn = { padding:"8px 16px", borderRadius:8, border:"1px solid #E2E8F0", background:"#fff", fontSize:13, cursor:"pointer", color:"#374151" as const }

const ZA_REGIONS = ["Gauteng","Western Cape","KwaZulu-Natal","Eastern Cape","Limpopo","Mpumalanga","North West","Free State","Northern Cape"]

export default function BranchesTab() {
  const qc = useQueryClient()
  const [view, setView] = useState<"list"|"create"|"edit">("list")
  const [editing, setEditing] = useState<Branch | null>(null)
  const [err, setErr] = useState("")
  const [form, setForm] = useState({ name:"", region:"", description:"" })

  const { data: branches = [], isLoading } = useQuery<Branch[]>({
    queryKey: ["branches"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/security/branches")
      return (r.data?.data ?? r.data) as Branch[]
    },
  })

  const createMut = useMutation({
    mutationFn: (b: object) => apiClient.post("/api/v1/security/branches", b),
    onSuccess: () => { qc.invalidateQueries({queryKey:["branches"]}); setView("list"); setErr("") },
    onError: (e: any) => setErr(e.response?.data?.message ?? "Failed to create branch"),
  })
  const updateMut = useMutation({
    mutationFn: ({id, ...b}: any) => apiClient.put(`/api/v1/security/branches/${id}`, b),
    onSuccess: () => { qc.invalidateQueries({queryKey:["branches"]}); setView("list"); setErr("") },
    onError: (e: any) => setErr(e.response?.data?.message ?? "Failed to update branch"),
  })
  const deleteMut = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/security/branches/${id}`),
    onSuccess: () => { qc.invalidateQueries({queryKey:["branches"]}); setErr("") },
    onError: (e: any) => setErr(e.response?.data?.message ?? "Failed to deactivate branch"),
  })

  function openCreate() { setForm({name:"",region:"",description:""}); setView("create"); setErr("") }
  function openEdit(b: Branch) { setEditing(b); setForm({name:b.name,region:b.region??"",description:b.description??""}); setView("edit"); setErr("") }
  function submit() {
    if (view==="edit" && editing) updateMut.mutate({id:editing.id,...form})
    else createMut.mutate(form)
  }

  const th = (l: string) => (
    <th key={l} style={{textAlign:"left",padding:"9px 14px",fontSize:11,fontWeight:700,color:"#94A3B8",textTransform:"uppercase" as const,letterSpacing:"0.5px"}}>{l}</th>
  )

  const REGION_COLORS: Record<string,string> = {
    "Gauteng":"#1D4ED8","Western Cape":"#166534","KwaZulu-Natal":"#7C3AED",
    "Eastern Cape":"#92400E","Limpopo":"#065F46","Mpumalanga":"#B45309",
    "North West":"#0F172A","Free State":"#374151","Northern Cape":"#6B7280",
  }

  return (
    <div style={{fontFamily:"'Inter',system-ui,sans-serif"}}>
      <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginBottom:20}}>
        <div style={{display:"flex",alignItems:"center",gap:10}}>
          <div style={{background:"#2563EB",borderRadius:10,padding:8}}><GitBranch size={18} color="#fff"/></div>
          <div>
            <h2 style={{margin:0,fontSize:18,fontWeight:700,color:"#0F172A"}}>Branches</h2>
            <p style={{margin:0,fontSize:12,color:"#94A3B8"}}>Regional / operational subdivisions — assign sites and guards per branch</p>
          </div>
        </div>
        <button style={{...btn("#2563EB"),display:"flex",alignItems:"center",gap:6}} onClick={openCreate}>
          <Plus size={14}/> New Branch
        </button>
      </div>

      {err && (
        <div style={{background:"#FEF2F2",border:"1px solid #FECACA",borderRadius:8,padding:"10px 14px",marginBottom:16,fontSize:13,color:"#991B1B",display:"flex",alignItems:"center",gap:8}}>
          <AlertTriangle size={14}/> {err}
        </div>
      )}

      {/* FORM */}
      {(view==="create"||view==="edit") && (
        <div style={{background:"#fff",border:"1px solid #E2E8F0",borderRadius:12,padding:24,marginBottom:20}}>
          <h3 style={{margin:"0 0 20px",fontSize:16,fontWeight:700,color:"#0F172A"}}>{view==="edit"?"Edit Branch":"New Branch"}</h3>
          <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:16,marginBottom:16}}>
            <div>
              <label style={{display:"block",fontSize:12,fontWeight:600,color:"#374151",marginBottom:5}}>Branch Name *</label>
              <input placeholder="e.g. Gauteng Region, VIP/CP Division" value={form.name} onChange={e=>setForm(p=>({...p,name:e.target.value}))} style={inp}/>
            </div>
            <div>
              <label style={{display:"block",fontSize:12,fontWeight:600,color:"#374151",marginBottom:5}}>Region</label>
              <select value={form.region} onChange={e=>setForm(p=>({...p,region:e.target.value}))} style={{...inp,background:"#fff"}}>
                <option value="">— Select region —</option>
                {ZA_REGIONS.map(r=><option key={r} value={r}>{r}</option>)}
              </select>
            </div>
            <div style={{gridColumn:"1/-1"}}>
              <label style={{display:"block",fontSize:12,fontWeight:600,color:"#374151",marginBottom:5}}>Description</label>
              <input placeholder="Optional — describe the branch scope" value={form.description} onChange={e=>setForm(p=>({...p,description:e.target.value}))} style={inp}/>
            </div>
          </div>
          <div style={{display:"flex",gap:8}}>
            <button style={sbtn} onClick={()=>setView("list")}>Cancel</button>
            <button style={{...btn("#2563EB"),opacity:!form.name?0.5:1}} disabled={!form.name} onClick={submit}>
              {view==="edit"?"Save Changes":"Create Branch"}
            </button>
          </div>
        </div>
      )}

      {/* LIST */}
      {isLoading
        ? <div style={{textAlign:"center",padding:"40px 0",color:"#94A3B8"}}>Loading…</div>
        : branches.length===0
          ? <div style={{textAlign:"center",padding:"60px 0",color:"#94A3B8"}}>
              <GitBranch size={32} strokeWidth={1.5} style={{margin:"0 auto 12px",display:"block"}}/>
              <p style={{margin:"0 0 4px",fontWeight:600,color:"#374151"}}>No branches yet</p>
              <p style={{margin:0,fontSize:13}}>Create branches to scope regional managers, payroll, and site assignments.</p>
            </div>
          : <div style={{background:"#fff",border:"1px solid #E2E8F0",borderRadius:12,overflow:"hidden"}}>
              <table style={{width:"100%",borderCollapse:"collapse",fontSize:13}}>
                <thead><tr style={{borderBottom:"2px solid #E2E8F0",background:"#F8FAFC"}}>
                  {["Branch Name","Region","Description","Status","Actions"].map(th)}
                </tr></thead>
                <tbody>{branches.map(b=>(
                  <tr key={b.id} style={{borderBottom:"1px solid #F1F5F9"}}>
                    <td style={{padding:"11px 14px"}}>
                      <div style={{display:"flex",alignItems:"center",gap:8}}>
                        <div style={{width:32,height:32,borderRadius:8,background:"#EFF6FF",display:"flex",alignItems:"center",justifyContent:"center"}}>
                          <GitBranch size={14} color="#2563EB"/>
                        </div>
                        <span style={{fontWeight:600,color:"#0F172A"}}>{b.name}</span>
                      </div>
                    </td>
                    <td style={{padding:"11px 14px"}}>
                      {b.region
                        ? <span style={{display:"flex",alignItems:"center",gap:4,fontSize:12,color:REGION_COLORS[b.region]??"#374151",fontWeight:600}}>
                            <MapPin size={11}/> {b.region}
                          </span>
                        : <span style={{color:"#CBD5E1",fontSize:12}}>—</span>
                      }
                    </td>
                    <td style={{padding:"11px 14px",color:"#64748B",fontSize:12,maxWidth:260}}>
                      <span style={{display:"block",overflow:"hidden",textOverflow:"ellipsis",whiteSpace:"nowrap" as const}}>{b.description ?? "—"}</span>
                    </td>
                    <td style={{padding:"11px 14px"}}>
                      <span style={{background:b.active?"#DCFCE7":"#F3F4F6",color:b.active?"#166534":"#6B7280",borderRadius:6,padding:"2px 8px",fontSize:11,fontWeight:700}}>
                        {b.active?"Active":"Inactive"}
                      </span>
                    </td>
                    <td style={{padding:"11px 14px"}}>
                      <div style={{display:"flex",gap:6}}>
                        <button style={{display:"flex",alignItems:"center",gap:4,padding:"5px 10px",borderRadius:7,border:"1px solid #E2E8F0",background:"#F8FAFC",fontSize:12,cursor:"pointer",color:"#374151"}}
                          onClick={()=>openEdit(b)}>
                          <Pencil size={11}/> Edit
                        </button>
                        {b.active && (
                          <button style={{display:"flex",alignItems:"center",gap:4,padding:"5px 10px",borderRadius:7,border:"1px solid #FECACA",background:"#FEF2F2",fontSize:12,cursor:"pointer",color:"#991B1B"}}
                            onClick={()=>{ if(confirm(`Deactivate branch "${b.name}"?`)) deleteMut.mutate(b.id) }}>
                            <Trash2 size={11}/> Deactivate
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}</tbody>
              </table>
            </div>
      }

      <div style={{marginTop:20,padding:"14px 16px",background:"#F8FAFC",border:"1px solid #E2E8F0",borderRadius:10,fontSize:12,color:"#64748B"}}>
        <strong style={{color:"#374151"}}>Note:</strong> Assigning sites and guards to branches is done via their respective edit forms (Sites tab → edit site → Branch, Guards tab → edit guard → Primary Branch).
        Branch-level query scoping for regional managers is a follow-on enforcement step — all current views return tenant-wide data regardless of branch.
      </div>
    </div>
  )
}
