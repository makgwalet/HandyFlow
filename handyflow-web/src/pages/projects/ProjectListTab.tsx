// src/pages/projects/ProjectListTab.tsx
// Full project list — shown when the "Projects" top-level tab is active.
// Differs from ProjectDashboard (which shows active-only cards):
//   • Shows all statuses including Completed and Cancelled
//   • Table layout optimised for scanning many projects
//   • Client-side search + API-side status filter
//   • Create project button inline
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Plus, Search, FolderOpen, AlertTriangle } from 'lucide-react'
import { apiClient } from '../../api/client'
import { CreateProjectModal } from './CreateProjectModal'

interface Project {
  id: string; projectNumber: string; name: string; status: string; health: string
  clientName: string | null; projectManagerName: string | null
  budgetTotal: number; budgetSpent: number; endDate: string | null
  taskCount: number; completedTaskCount: number; openRiskCount: number
  createdAt: string
}

function unwrap<T>(r:any):T[] { const d=r?.data?.data??r?.data??[]; return Array.isArray(d)?d as T[]:d?.content??[] }

const fmtR   = (n:number) => `R ${Number(n??0).toLocaleString('en-ZA',{minimumFractionDigits:0,maximumFractionDigits:0})}`
const fmtDate = (d:string|null) => d ? new Date(d).toLocaleDateString('en-ZA') : '—'

const STATUS_OPTIONS = ['', 'PLANNING', 'ACTIVE', 'ON_HOLD', 'COMPLETED', 'CANCELLED'] as const
const STATUS_LABEL: Record<string,string> = {
  '':'All', PLANNING:'Planning', ACTIVE:'Active',
  ON_HOLD:'On Hold', COMPLETED:'Completed', CANCELLED:'Cancelled',
}
const STATUS_BADGE: Record<string,{bg:string;color:string}> = {
  PLANNING:  {bg:'#F1F5F9',color:'#475569'},
  ACTIVE:    {bg:'#DBEAFE',color:'#1D4ED8'},
  ON_HOLD:   {bg:'#FEF3C7',color:'#92400E'},
  COMPLETED: {bg:'#DCFCE7',color:'#166534'},
  CANCELLED: {bg:'#FEE2E2',color:'#DC2626'},
}
const HEALTH_DOT: Record<string,string> = { GREEN:'#16A34A', AMBER:'#D97706', RED:'#DC2626' }
const HEALTH_LABEL: Record<string,string> = { GREEN:'On Track', AMBER:'Watch', RED:'At Risk' }

export function ProjectListTab({ onOpen }: { onOpen:(id:string)=>void }) {
  const [statusFilter, setStatusFilter] = useState('')
  const [search, setSearch]             = useState('')
  const [showCreate, setShowCreate]     = useState(false)

  // API-side status filter. No filter = all non-cancelled (backend findActive).
  // Selecting CANCELLED makes a separate call with status=CANCELLED.
  const queryParam = statusFilter ? `?status=${statusFilter}&size=200` : '?size=200'

  const { data:projects=[], isLoading, isError } = useQuery<Project[]>({
    queryKey: ['pm-projects-list', statusFilter],
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/projects${queryParam}`)
      return unwrap<Project>(r)
    },
    staleTime: 30_000,
  })

  // Client-side search over the already-fetched page
  const filtered = search.trim()
    ? projects.filter(p =>
        p.name.toLowerCase().includes(search.toLowerCase()) ||
        p.projectNumber.toLowerCase().includes(search.toLowerCase()) ||
        (p.clientName ?? '').toLowerCase().includes(search.toLowerCase())
      )
    : projects

  return (
    <div>
      {/* Toolbar */}
      <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:16,gap:12,flexWrap:'wrap'}}>

        {/* Status filter pills */}
        <div style={{display:'flex',gap:6,flexWrap:'wrap'}}>
          {STATUS_OPTIONS.map(s => (
            <button key={s} onClick={()=>setStatusFilter(s)}
              style={{
                padding:'5px 13px', borderRadius:20, fontSize:12, cursor:'pointer',
                fontWeight:  statusFilter===s ? 700 : 400,
                border:      statusFilter===s ? '1.5px solid #1B3A6B' : '1px solid #E2E8F0',
                background:  statusFilter===s ? '#EFF6FF' : '#fff',
                color:       statusFilter===s ? '#1B3A6B' : '#64748B',
              }}>
              {STATUS_LABEL[s]}
            </button>
          ))}
        </div>

        {/* Search + create */}
        <div style={{display:'flex',gap:8,alignItems:'center'}}>
          <div style={{position:'relative'}}>
            <Search size={13} color="#94A3B8" style={{position:'absolute',left:10,top:'50%',transform:'translateY(-50%)'}}/>
            <input
              value={search} onChange={e=>setSearch(e.target.value)}
              placeholder="Search projects…"
              style={{
                padding:'7px 12px 7px 30px', border:'1px solid #E2E8F0', borderRadius:8,
                fontSize:13, outline:'none', background:'#fff', width:200,
              }}
            />
          </div>
          <button onClick={()=>setShowCreate(true)}
            style={{display:'flex',alignItems:'center',gap:5,padding:'7px 14px',background:'#1B3A6B',color:'#fff',border:'none',borderRadius:8,fontSize:13,fontWeight:600,cursor:'pointer',whiteSpace:'nowrap'}}>
            <Plus size={14}/> New Project
          </button>
        </div>
      </div>

      {/* Table */}
      {isLoading ? (
        <div style={{padding:40,textAlign:'center',color:'#94A3B8'}}>Loading…</div>
      ) : isError ? (
        <div style={{padding:40,textAlign:'center',color:'#DC2626',fontSize:13}}>Failed to load projects</div>
      ) : filtered.length === 0 ? (
        <div style={{textAlign:'center',padding:'60px 20px',color:'#94A3B8'}}>
          <FolderOpen size={40} style={{marginBottom:12,opacity:.3}}/>
          <div style={{fontWeight:600,color:'#475569',marginBottom:4}}>
            {search ? `No projects matching "${search}"` : 'No projects'}
          </div>
          <div style={{fontSize:13}}>
            {search ? 'Try a different search term' : 'Create your first project to get started'}
          </div>
        </div>
      ) : (
        <div style={{border:'1px solid #E2E8F0',borderRadius:12,overflow:'hidden'}}>
          <table style={{width:'100%',borderCollapse:'collapse'}}>
            <thead>
              <tr style={{background:'#F8FAFC'}}>
                {['#','Project','Client','Status','Health','Budget','End Date','Tasks','Risks'].map(h=>(
                  <th key={h} style={{
                    padding:'10px 14px', textAlign:'left', fontSize:11, fontWeight:700,
                    color:'#94A3B8', textTransform:'uppercase', letterSpacing:'0.04em',
                    whiteSpace:'nowrap',
                  }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {filtered.map((p, i) => {
                const st = STATUS_BADGE[p.status] ?? STATUS_BADGE.PLANNING
                const hDot = HEALTH_DOT[p.health] ?? '#16A34A'
                const spentPct = p.budgetTotal > 0 ? Math.min(100,(p.budgetSpent/p.budgetTotal)*100) : 0
                const taskPct  = p.taskCount   > 0 ? Math.round((p.completedTaskCount/p.taskCount)*100) : 0

                return (
                  <tr key={p.id}
                    onClick={()=>onOpen(p.id)}
                    style={{
                      borderTop:'1px solid #F1F5F9',
                      background: i%2===0 ? '#fff' : '#FAFAFA',
                      cursor:'pointer',
                      transition:'background 0.1s',
                    }}
                    onMouseEnter={e=>(e.currentTarget.style.background='#F0F7FF')}
                    onMouseLeave={e=>(e.currentTarget.style.background=i%2===0?'#fff':'#FAFAFA')}
                  >
                    {/* Project number */}
                    <td style={{padding:'11px 14px',fontSize:11,color:'#94A3B8',fontWeight:600,whiteSpace:'nowrap'}}>
                      {p.projectNumber}
                    </td>

                    {/* Name */}
                    <td style={{padding:'11px 14px',maxWidth:240}}>
                      <div style={{fontSize:13,fontWeight:600,color:'#0F172A',overflow:'hidden',textOverflow:'ellipsis',whiteSpace:'nowrap'}}>
                        {p.name}
                      </div>
                      {p.projectManagerName && (
                        <div style={{fontSize:11,color:'#94A3B8',marginTop:2}}>PM: {p.projectManagerName}</div>
                      )}
                    </td>

                    {/* Client */}
                    <td style={{padding:'11px 14px',fontSize:12,color:'#64748B',maxWidth:160,overflow:'hidden',textOverflow:'ellipsis',whiteSpace:'nowrap'}}>
                      {p.clientName ?? '—'}
                    </td>

                    {/* Status badge */}
                    <td style={{padding:'11px 14px'}}>
                      <span style={{background:st.bg,color:st.color,fontSize:10,fontWeight:700,padding:'2px 8px',borderRadius:20,whiteSpace:'nowrap'}}>
                        {p.status.replace('_',' ')}
                      </span>
                    </td>

                    {/* Health dot + label */}
                    <td style={{padding:'11px 14px'}}>
                      <div style={{display:'flex',alignItems:'center',gap:5}}>
                        <span style={{width:7,height:7,borderRadius:'50%',background:hDot,flexShrink:0,display:'inline-block'}}/>
                        <span style={{fontSize:11,fontWeight:600,color:hDot,whiteSpace:'nowrap'}}>{HEALTH_LABEL[p.health]??p.health}</span>
                      </div>
                    </td>

                    {/* Budget with utilisation bar */}
                    <td style={{padding:'11px 14px',minWidth:140}}>
                      <div style={{fontSize:12,color:'#0F172A',fontWeight:600,marginBottom:4,whiteSpace:'nowrap'}}>
                        {fmtR(p.budgetSpent)} <span style={{color:'#94A3B8',fontWeight:400}}>/ {fmtR(p.budgetTotal)}</span>
                      </div>
                      <div style={{height:4,background:'#F1F5F9',borderRadius:2}}>
                        <div style={{
                          height:'100%',borderRadius:2,
                          width:`${spentPct}%`,
                          background: spentPct>100?'#EF4444':spentPct>85?'#F59E0B':'#22C55E',
                        }}/>
                      </div>
                    </td>

                    {/* End date */}
                    <td style={{padding:'11px 14px',fontSize:12,color:'#64748B',whiteSpace:'nowrap'}}>
                      {fmtDate(p.endDate)}
                    </td>

                    {/* Tasks progress */}
                    <td style={{padding:'11px 14px',fontSize:12,color:'#64748B',whiteSpace:'nowrap'}}>
                      {p.completedTaskCount}/{p.taskCount}
                      <span style={{color:'#94A3B8',marginLeft:4}}>({taskPct}%)</span>
                    </td>

                    {/* Open risks */}
                    <td style={{padding:'11px 14px'}}>
                      {p.openRiskCount > 0 ? (
                        <div style={{display:'flex',alignItems:'center',gap:4,color:'#DC2626',fontSize:12,fontWeight:600}}>
                          <AlertTriangle size={12}/>{p.openRiskCount}
                        </div>
                      ) : (
                        <span style={{fontSize:12,color:'#94A3B8'}}>—</span>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>

          {/* Footer count */}
          <div style={{padding:'10px 16px',borderTop:'1px solid #F1F5F9',background:'#F8FAFC',fontSize:12,color:'#94A3B8'}}>
            {filtered.length} project{filtered.length!==1?'s':''}
            {search && ` matching "${search}"`}
          </div>
        </div>
      )}

      {showCreate && (
        <CreateProjectModal
          onClose={()=>setShowCreate(false)}
          onCreated={(id)=>{ setShowCreate(false); onOpen(id) }}
        />
      )}
    </div>
  )
}
