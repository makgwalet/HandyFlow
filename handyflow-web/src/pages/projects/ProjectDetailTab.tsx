// src/pages/projects/ProjectDetailTab.tsx
// 9-tab detail view. Added: RFI tab, PDF export links on Budget/Field/Risks tabs.
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { ChevronLeft, Play, Pause, CheckCircle, ExternalLink, Download } from 'lucide-react'
import { OverviewTab }      from './tabs/OverviewTab'
import { GanttTab }         from './tabs/GanttTab'
import { TasksTab }         from './tabs/TasksTab'
import { ResourcesTab }     from './tabs/ResourcesTab'
import { BudgetTab }        from './tabs/BudgetTab'
import { RisksTab }         from './tabs/RisksTab'
import { DocumentsTab }     from './tabs/DocumentsTab'
import { FieldTab }         from './tabs/FieldTab'
import { RfiTab }           from './tabs/RfiTab'
import { TabErrorBoundary } from './TabErrorBoundary'

export interface Project {
  id: string; projectNumber: string; name: string; description: string | null
  projectType: string; status: string; health: string
  clientName: string | null; siteAddress: string | null
  startDate: string | null; endDate: string | null
  baselineStart: string | null; baselineEnd: string | null
  budgetTotal: number; budgetSpent: number; budgetCommitted: number; budgetVariance: number
  contractValue: number | null; contractRef: string | null; retentionPct: number
  cidbGrade: string | null; nhbrcNumber: string | null
  projectManagerName: string | null; clientPortalToken: string | null; notes: string | null
  taskCount: number; completedTaskCount: number; openRiskCount: number
}

type TabKey = 'overview'|'gantt'|'tasks'|'resources'|'budget'|'risks'|'documents'|'field'|'rfi'

const ACCENT = '#1B3A6B'
const HEALTH_DOT: Record<string,string> = { GREEN:'#16A34A', AMBER:'#D97706', RED:'#DC2626' }
const STATUS_BADGE: Record<string,{bg:string;color:string}> = {
  PLANNING:{bg:'var(--hf-surface-sunken)',color:'var(--hf-text-tertiary)'}, ACTIVE:{bg:'var(--hf-info-soft-strong)',color:'var(--hf-info-text)'},
  ON_HOLD:{bg:'var(--hf-warning-soft-strong)',color:'var(--hf-warning-text-deep)'}, COMPLETED:{bg:'var(--hf-success-soft-strong)',color:'var(--hf-success-text-strong)'},
  CANCELLED:{bg:'var(--hf-danger-soft-strong)',color:'var(--hf-danger-text)'},
}

const TABS: { key:TabKey; label:string }[] = [
  {key:'overview',  label:'Overview'},
  {key:'gantt',     label:'Schedule'},
  {key:'tasks',     label:'Tasks'},
  {key:'resources', label:'Resources'},
  {key:'budget',    label:'Budget'},
  {key:'risks',     label:'Risks'},
  {key:'documents', label:'Documents'},
  {key:'field',     label:'Field'},
  {key:'rfi',       label:'RFI'},
]

// Download a PDF export from the given endpoint
const downloadPdf = async (url: string, filename: string) => {
  const r = await apiClient.get(url, { responseType: 'blob' })
  const href = URL.createObjectURL(new Blob([r.data], { type: 'application/pdf' }))
  const a = document.createElement('a')
  a.href = href; a.download = filename; a.click()
  URL.revokeObjectURL(href)
}

export function ProjectDetailTab({ projectId, onBack }: { projectId:string; onBack:()=>void }) {
  const qc = useQueryClient()
  const [tab, setTab] = useState<TabKey>('overview')

  const { data:project, isLoading } = useQuery<Project>({
    queryKey: ['pm-project', projectId],
    queryFn: async () => { const r = await apiClient.get(`/api/v1/projects/${projectId}`); return r.data?.data ?? r.data },
    staleTime: 30_000, enabled: !!projectId,
  })

  const actionMut = useMutation({
    mutationFn: (action:string) => apiClient.post(`/api/v1/projects/${projectId}/${action}`),
    onSuccess: () => qc.invalidateQueries({ queryKey:['pm-project', projectId] }),
  })

  if (isLoading) return <div style={{padding:'40px 0',textAlign:'center',color:'var(--hf-text-faint)',fontSize:13}}>Loading project…</div>
  if (!project)  return <div style={{padding:'40px 0',textAlign:'center',color:'var(--hf-danger-text)', fontSize:13}}>Project not found</div>

  const st   = STATUS_BADGE[project.status] ?? STATUS_BADGE.PLANNING
  const hDot = HEALTH_DOT[project.health]   ?? '#16A34A'

  return (
    <div>
      {/* Header */}
      <div style={{marginBottom:20}}>
        <button onClick={onBack}
          style={{display:'flex',alignItems:'center',gap:4,background:'none',border:'none',cursor:'pointer',color:'var(--hf-text-muted)',fontSize:13,marginBottom:12,padding:0}}>
          <ChevronLeft size={15}/> All Projects
        </button>
        <div style={{display:'flex',alignItems:'flex-start',justifyContent:'space-between',gap:16}}>
          <div style={{flex:1}}>
            <div style={{display:'flex',alignItems:'center',gap:8,marginBottom:6}}>
              <span style={{fontSize:11,color:'var(--hf-text-faint)',fontWeight:500}}>{project.projectNumber}</span>
              <span style={{background:st.bg,color:st.color,fontSize:11,fontWeight:700,padding:'2px 9px',borderRadius:20}}>{project.status.replace('_',' ')}</span>
              <span style={{display:'flex',alignItems:'center',gap:4}}>
                <span style={{width:7,height:7,borderRadius:'50%',background:hDot,display:'inline-block'}}/>
                <span style={{fontSize:11,fontWeight:700,color:hDot}}>{project.health}</span>
              </span>
            </div>
            <h2 style={{fontSize:20,fontWeight:800,color:'var(--hf-text)',margin:'0 0 4px'}}>{project.name}</h2>
            <div style={{fontSize:13,color:'var(--hf-text-muted)'}}>
              {project.clientName        && <span>{project.clientName} · </span>}
              {project.projectManagerName && <span>PM: {project.projectManagerName}</span>}
              {project.siteAddress        && <span> · {project.siteAddress}</span>}
            </div>
          </div>
          <div style={{display:'flex',gap:8,flexShrink:0,flexWrap:'wrap',justifyContent:'flex-end'}}>
            {project.status==='PLANNING' && <Btn onClick={()=>actionMut.mutate('activate')} color="#166534" bg="#DCFCE7" border="#86EFAC" icon={Play}>Activate</Btn>}
            {project.status==='ACTIVE'   && <Btn onClick={()=>actionMut.mutate('hold')}     color="#92400E" bg="#FEF3C7" border="#FCD34D" icon={Pause}>Hold</Btn>}
            {project.status==='ON_HOLD'  && <Btn onClick={()=>actionMut.mutate('activate')} color="#166534" bg="#DCFCE7" border="#86EFAC" icon={Play}>Resume</Btn>}
            {['PLANNING','ACTIVE','ON_HOLD'].includes(project.status) && (
              <Btn onClick={()=>actionMut.mutate('complete')} color="#166534" bg="#F0FDF4" border="#BBF7D0" icon={CheckCircle}>Complete</Btn>
            )}
            {project.clientPortalToken && (
              <button onClick={()=>window.open(`/projects/portal/${project.clientPortalToken}`,'_blank')}
                style={{display:'flex',alignItems:'center',gap:5,padding:'6px 12px',background:'var(--hf-surface-muted)',border:'1px solid var(--hf-border)',borderRadius:8,fontSize:12,cursor:'pointer',color:'var(--hf-text-muted)'}}>
                <ExternalLink size={13}/> Client Portal
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Tab bar */}
      <div style={{display:'flex',gap:2,borderBottom:'1px solid var(--hf-border)',marginBottom:16,overflowX:'auto'}}>
        {TABS.map(t=>(
          <button key={t.key} onClick={()=>setTab(t.key)}
            style={{
              padding:'8px 14px',background:'none',border:'none',cursor:'pointer',
              fontSize:13,whiteSpace:'nowrap',
              fontWeight:   tab===t.key ? 600 : 400,
              color:        tab===t.key ? ACCENT : 'var(--hf-text-muted)',
              borderBottom: tab===t.key ? `2px solid ${ACCENT}` : '2px solid transparent',
              marginBottom:-1,
            }}>
            {t.label}
            {t.key==='risks' && project.openRiskCount > 0 && (
              <span style={{marginLeft:5,background:'var(--hf-danger-soft)',color:'var(--hf-danger-text)',fontSize:10,fontWeight:700,padding:'1px 5px',borderRadius:10}}>
                {project.openRiskCount}
              </span>
            )}
          </button>
        ))}
      </div>

      {/* Per-tab export buttons */}
      {(tab==='risks' || tab==='field' || tab==='budget') && (
        <div style={{display:'flex',justifyContent:'flex-end',marginBottom:12}}>
          {tab==='risks' && (
            <button onClick={()=>downloadPdf(`/api/v1/projects/${project.id}/export/risk-register`, `risk-register-${project.projectNumber}.pdf`)}
              style={{display:'flex',alignItems:'center',gap:5,padding:'6px 12px',background:'var(--hf-surface-muted)',border:'1px solid var(--hf-border)',borderRadius:8,fontSize:12,cursor:'pointer',color:'var(--hf-text-muted)'}}>
              <Download size={12}/> Export PDF (OHSA)
            </button>
          )}
          {tab==='field' && (
            <button onClick={()=>downloadPdf(`/api/v1/projects/${project.id}/export/snag-list`, `snag-list-${project.projectNumber}.pdf`)}
              style={{display:'flex',alignItems:'center',gap:5,padding:'6px 12px',background:'var(--hf-surface-muted)',border:'1px solid var(--hf-border)',borderRadius:8,fontSize:12,cursor:'pointer',color:'var(--hf-text-muted)'}}>
              <Download size={12}/> Export Snag List PDF
            </button>
          )}
        </div>
      )}

      {/* Tab panels — each isolated in an error boundary */}
      <TabErrorBoundary tab={tab}>
        {tab==='overview'   && <OverviewTab   project={project}/>}
        {tab==='gantt'      && <GanttTab      projectId={project.id}/>}
        {tab==='tasks'      && <TasksTab      projectId={project.id}/>}
        {tab==='resources'  && <ResourcesTab  projectId={project.id}/>}
        {tab==='budget'     && <BudgetTab     projectId={project.id} project={project}/>}
        {tab==='risks'      && <RisksTab      projectId={project.id}/>}
        {tab==='documents'  && <DocumentsTab  projectId={project.id}/>}
        {tab==='field'      && <FieldTab      projectId={project.id}/>}
        {tab==='rfi'        && <RfiTab        projectId={project.id}/>}
      </TabErrorBoundary>
    </div>
  )
}

function Btn({ onClick, color, bg, border, icon:Icon, children }:any) {
  return (
    <button onClick={onClick}
      style={{display:'flex',alignItems:'center',gap:5,padding:'6px 12px',background:bg,color,border:`1px solid ${border}`,borderRadius:8,fontSize:12,fontWeight:600,cursor:'pointer'}}>
      <Icon size={13}/>{children}
    </button>
  )
}
