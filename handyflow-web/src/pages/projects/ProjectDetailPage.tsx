// src/pages/projects/ProjectDetailPage.tsx
// Route-based detail page (e.g. /projects/:id).
// Project interface lives in ProjectDetailTab.tsx — import from there to avoid duplication.
import React, { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  HardHat, ChevronLeft, Play, Pause, CheckCircle,
  LayoutDashboard, GanttChart, ListTodo, Users, DollarSign,
  AlertTriangle, FileText, Hammer, ExternalLink,
} from 'lucide-react'
import { apiClient } from '../../api/client'
// Single source of truth for the Project type — re-exported from ProjectDetailTab
import type { Project } from './ProjectDetailTab'
import { OverviewTab }   from './tabs/OverviewTab'
import { GanttTab }      from './tabs/GanttTab'
import { TasksTab }      from './tabs/TasksTab'
import { ResourcesTab }  from './tabs/ResourcesTab'
import { BudgetTab }     from './tabs/BudgetTab'
import { RisksTab }      from './tabs/RisksTab'
import { DocumentsTab }  from './tabs/DocumentsTab'
import { FieldTab }      from './tabs/FieldTab'

type TabKey = 'overview'|'gantt'|'tasks'|'resources'|'budget'|'risks'|'documents'|'field'

const HEALTH_DOT: Record<string, string> = { GREEN:'#16A34A', AMBER:'#D97706', RED:'#DC2626' }
const STATUS_LABEL: Record<string, { bg:string; color:string }> = {
  PLANNING:{bg:'#F1F5F9',color:'#475569'}, ACTIVE:{bg:'#DBEAFE',color:'#1D4ED8'},
  ON_HOLD:{bg:'#FEF3C7',color:'#92400E'}, COMPLETED:{bg:'#DCFCE7',color:'#166534'},
  CANCELLED:{bg:'#FEF2F2',color:'#DC2626'},
}

export function ProjectDetailPage() {
  const { id } = useParams<{ id: string }>()
  const nav = useNavigate()
  const qc = useQueryClient()
  const [tab, setTab] = useState<TabKey>('overview')

  const { data: project, isLoading } = useQuery<Project>({
    queryKey: ['pm-project', id],
    queryFn: async () => { const r = await apiClient.get(`/api/v1/projects/${id}`); return r.data?.data ?? r.data },
    staleTime: 30_000,
    enabled: !!id,
  })

  const actionMut = useMutation({
    mutationFn: (action: string) => apiClient.post(`/api/v1/projects/${id}/${action}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['pm-project', id] }),
  })

  if (isLoading) return <div style={{ padding: 60, textAlign: 'center', color: '#94A3B8', fontFamily: "'Inter',sans-serif" }}>Loading…</div>
  if (!project) return <div style={{ padding: 60, textAlign: 'center', color: '#DC2626', fontFamily: "'Inter',sans-serif" }}>Project not found</div>

  const st = STATUS_LABEL[project.status] ?? STATUS_LABEL.PLANNING

  const TABS: { key: TabKey; label: string; icon: React.ElementType }[] = [
    { key: 'overview',   label: 'Overview',   icon: LayoutDashboard },
    { key: 'gantt',      label: 'Schedule',   icon: GanttChart },
    { key: 'tasks',      label: 'Tasks',      icon: ListTodo },
    { key: 'resources',  label: 'Resources',  icon: Users },
    { key: 'budget',     label: 'Budget',     icon: DollarSign },
    { key: 'risks',      label: 'Risks',      icon: AlertTriangle },
    { key: 'documents',  label: 'Documents',  icon: FileText },
    { key: 'field',      label: 'Field',      icon: Hammer },
  ]

  return (
    <div style={{ fontFamily: "'Inter',system-ui,sans-serif" }}>
      {/* Project header */}
      <div style={{ marginBottom: 20 }}>
        <button onClick={() => nav('/projects')} style={{ display: 'flex', alignItems: 'center', gap: 4, background: 'none', border: 'none', cursor: 'pointer', color: '#64748B', fontSize: 13, marginBottom: 10, padding: 0 }}>
          <ChevronLeft size={15} /> All Projects
        </button>
        <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 16 }}>
          <div style={{ flex: 1 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 6 }}>
              <span style={{ fontSize: 11, color: '#94A3B8', fontWeight: 600 }}>{project.projectNumber}</span>
              <span style={{ background: st.bg, color: st.color, fontSize: 11, fontWeight: 700, padding: '3px 10px', borderRadius: 20 }}>{project.status.replace('_',' ')}</span>
              <span style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 11, fontWeight: 700, color: HEALTH_DOT[project.health] }}>
                <span style={{ width: 8, height: 8, borderRadius: '50%', background: HEALTH_DOT[project.health], display: 'inline-block' }} />
                {project.health}
              </span>
            </div>
            <h1 style={{ fontSize: 24, fontWeight: 800, color: '#0F172A', margin: '0 0 4px' }}>{project.name}</h1>
            <div style={{ fontSize: 13, color: '#64748B' }}>
              {project.clientName && <span>{project.clientName} · </span>}
              {project.projectManagerName && <span>PM: {project.projectManagerName} · </span>}
              {project.siteAddress && <span>{project.siteAddress}</span>}
            </div>
          </div>
          {/* Action buttons */}
          <div style={{ display: 'flex', gap: 8, flexShrink: 0 }}>
            {project.status === 'PLANNING' && (
              <ActionBtn color="#166534" bg="#DCFCE7" border="#86EFAC" onClick={() => actionMut.mutate('activate')} icon={Play}>Activate</ActionBtn>
            )}
            {project.status === 'ACTIVE' && (
              <ActionBtn color="#92400E" bg="#FEF3C7" border="#FCD34D" onClick={() => actionMut.mutate('hold')} icon={Pause}>Hold</ActionBtn>
            )}
            {project.status === 'ON_HOLD' && (
              <ActionBtn color="#166534" bg="#DCFCE7" border="#86EFAC" onClick={() => actionMut.mutate('activate')} icon={Play}>Resume</ActionBtn>
            )}
            {['PLANNING','ACTIVE','ON_HOLD'].includes(project.status) && (
              <ActionBtn color="#166534" bg="#F0FDF4" border="#BBF7D0" onClick={() => actionMut.mutate('complete')} icon={CheckCircle}>Complete</ActionBtn>
            )}
            {project.clientPortalToken && (
              <button onClick={() => window.open(`/projects/portal/${project.clientPortalToken}`, '_blank')}
                style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '7px 12px', background: '#F8FAFC', border: '1px solid #E2E8F0', borderRadius: 8, fontSize: 12, cursor: 'pointer', color: '#64748B' }}>
                <ExternalLink size={13} /> Client Portal
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div style={{ display: 'flex', gap: 2, borderBottom: '1px solid #E2E8F0', marginBottom: 24 }}>
        {TABS.map(t => (
          <button key={t.key} onClick={() => setTab(t.key)}
            style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '9px 14px', border: 'none', background: 'none', cursor: 'pointer', fontSize: 13, fontWeight: tab === t.key ? 700 : 500, color: tab === t.key ? '#1B3A6B' : '#64748B', borderBottom: tab === t.key ? '2px solid #1B3A6B' : '2px solid transparent', marginBottom: -1 }}>
            <t.icon size={14} />{t.label}
            {t.key === 'risks' && project.openRiskCount > 0 && (
              <span style={{ marginLeft: 4, background: '#FEF2F2', color: '#DC2626', fontSize: 10, fontWeight: 700, padding: '1px 5px', borderRadius: 10 }}>{project.openRiskCount}</span>
            )}
          </button>
        ))}
      </div>

      {/* Tab panels */}
      {tab === 'overview'   && <OverviewTab   project={project} />}
      {tab === 'gantt'      && <GanttTab      projectId={project.id} />}
      {tab === 'tasks'      && <TasksTab      projectId={project.id} />}
      {tab === 'resources'  && <ResourcesTab  projectId={project.id} />}
      {tab === 'budget'     && <BudgetTab     projectId={project.id} project={project} />}
      {tab === 'risks'      && <RisksTab      projectId={project.id} />}
      {tab === 'documents'  && <DocumentsTab  projectId={project.id} />}
      {tab === 'field'      && <FieldTab      projectId={project.id} />}
    </div>
  )
}

function ActionBtn({ color, bg, border, onClick, icon: Icon, children }: any) {
  return (
    <button onClick={onClick} style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '7px 12px', background: bg, color, border: `1px solid ${border}`, borderRadius: 8, fontSize: 12, fontWeight: 600, cursor: 'pointer' }}>
      <Icon size={13} />{children}
    </button>
  )
}
