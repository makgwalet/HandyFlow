// src/pages/Dashboard.tsx
import { useNavigate } from 'react-router-dom'
import { useAppointments } from '@/hooks/use-appointments'
import { useLabResults } from '@/hooks/use-lab'
import { usePatients } from '@/hooks/use-patients'
import { useSyncStore } from '@/stores/sync-store'
import { Card } from '@/components/ui/Card'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { formatDate, calculateAge, getInitials } from '@/lib/utils'

export function Dashboard() {
  const navigate = useNavigate()
  const { data: appointments } = useAppointments({ date: new Date().toISOString().split('T')[0] })
  const { data: labResults } = useLabResults('unreviewed')
  const { data: patients } = usePatients()
  const { pendingCount } = useSyncStore()

  const checkedIn = appointments?.filter(a => a.status === 'checked_in').length ?? 0
  const inProgress = appointments?.filter(a => a.status === 'in_progress').length ?? 0
  const urgentLabs = labResults?.filter(l => l.priority === 'urgent').length ?? 0

  const outstanding = [
    { name: 'John Doe', initials: 'JD', amount: 2450, days: 14 },
    { name: 'Sarah Miller', initials: 'SM', amount: 1800, days: 3 },
    { name: 'Bongani Nkosi', initials: 'BN', amount: 4200, days: 21, overdue: true },
  ]

  const schedule = [
    { time: '08:00', smith: { name: 'John Doe', type: 'Follow-up' }, patel: { name: 'Thandi M.', type: 'New patient' } },
    { time: '08:30', smith: { name: 'Sarah Miller', type: 'Check-up' }, patel: null },
    { time: '09:00', smith: null, patel: { name: 'Bongani N.', type: 'Lab review' } },
    { time: '09:30', smith: { name: 'Peter van Wyk', type: 'Diabetes' }, patel: { name: 'Lerato D.', type: 'Post-natal' } },
    { time: '10:00', smith: { name: '— IN PROGRESS —', type: 'Consultation', highlight: true }, patel: null },
    { time: '10:30', smith: null, patel: null },
    { time: '11:00', smith: { name: 'Mark Johnson', type: 'Hypertension' }, patel: { name: 'Grace M.', type: 'Vaccination' } },
  ]

  return (
    <div className="space-y-6">
      {/* Stats */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <StatCard
          label="Today's Appointments"
          value={appointments?.length ?? 14}
          subtext={`${checkedIn} checked in · ${inProgress} in progress`}
          icon={CalendarIcon}
          iconColor="text-teal-600"
          iconBg="bg-teal-50"
          onClick={() => navigate('/schedule')}
        />
        <StatCard
          label="Recalls Due"
          value={3}
          subtext="1 overdue · 2 due this week"
          icon={BellIcon}
          iconColor="text-amber-600"
          iconBg="bg-amber-50"
        />
        <StatCard
          label="Lab Alerts"
          value={labResults?.length ?? 2}
          subtext={`${urgentLabs} urgent · ${(labResults?.length ?? 2) - urgentLabs} unreviewed`}
          icon={FlaskIcon}
          iconColor="text-rose-600"
          iconBg="bg-rose-50"
          onClick={() => navigate('/lab')}
        />
      </div>

      {/* Quick Actions */}
      <div className="flex gap-3 overflow-x-auto pb-1">
        <Button onClick={() => navigate('/patients?new=true')}>
          <PlusIcon className="mr-2 h-4 w-4" />New Patient
        </Button>
        <Button variant="secondary" onClick={() => navigate('/schedule?new=true')}>
          <CalendarIcon className="mr-2 h-4 w-4" />Book Appointment
        </Button>
        <Button variant="secondary" onClick={() => navigate('/billing?action=payment')}>
          <BanknoteIcon className="mr-2 h-4 w-4" />Record Payment
        </Button>
        <Button variant="secondary" onClick={() => navigate('/lab?upload=true')}>
          <UploadIcon className="mr-2 h-4 w-4" />Upload Lab PDF
        </Button>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">
        {/* Left Column (2/3) */}
        <div className="xl:col-span-2 space-y-6">
          {/* Schedule */}
          <Card>
            <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between">
              <h3 className="font-semibold text-slate-800">Today's Schedule</h3>
              <div className="flex gap-1 bg-slate-100 rounded-lg p-0.5">
                {['Day', 'Week', 'Month'].map((v, i) => (
                  <button key={v} className={cn(
                    "px-3 py-1 rounded-md text-xs font-medium transition-all",
                    i === 0 ? "bg-white text-slate-700 shadow-sm" : "text-slate-500 hover:text-slate-700"
                  )}>{v}</button>
                ))}
              </div>
            </div>
            <div className="p-5">
              <div className="grid grid-cols-[60px_1fr_1fr] gap-3 text-xs font-medium text-slate-400 mb-3 uppercase tracking-wider">
                <span>Time</span>
                <span className="flex items-center gap-1.5">
                  <span className="h-2 w-2 rounded-full bg-teal-500" />Dr. Smith
                </span>
                <span className="flex items-center gap-1.5">
                  <span className="h-2 w-2 rounded-full bg-indigo-500" />Dr. Patel
                </span>
              </div>
              <div className="space-y-2">
                {schedule.map((row) => (
                  <div key={row.time} className="grid grid-cols-[60px_1fr_1fr] gap-3 items-center">
                    <span className="text-xs font-medium text-slate-400">{row.time}</span>
                    <Slot slot={row.smith} color="teal" />
                    <Slot slot={row.patel} color="indigo" />
                  </div>
                ))}
              </div>
            </div>
          </Card>

          {/* Outstanding */}
          <Card>
            <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between">
              <div>
                <h3 className="font-semibold text-slate-800">Outstanding Balances</h3>
                <p className="text-xs text-slate-400 mt-0.5">23 patients · Total R 45,230</p>
              </div>
              <button onClick={() => navigate('/billing')} className="text-xs font-medium text-teal-600 hover:text-teal-700">
                View all
              </button>
            </div>
            <div className="divide-y divide-slate-100">
              {outstanding.map((item) => (
                <div key={item.name} className="px-5 py-3 flex items-center justify-between hover:bg-slate-50 transition-colors cursor-pointer">
                  <div className="flex items-center gap-3">
                    <div className="h-8 w-8 rounded-full bg-slate-100 flex items-center justify-center text-xs font-bold text-slate-600">
                      {item.initials}
                    </div>
                    <div>
                      <p className="text-sm font-medium text-slate-700">{item.name}</p>
                      <p className="text-xs text-slate-400">
                        {item.overdue ? `Overdue by ${item.days} days` : `Last payment ${item.days} days ago`}
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center gap-4">
                    <span className={cn("text-sm font-semibold", item.overdue ? "text-rose-600" : "text-slate-700")}>
                      R {item.amount.toLocaleString()}
                    </span>
                    <Button size="sm" variant="ghost" className="text-teal-700 bg-teal-50 hover:bg-teal-100">
                      Record
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          </Card>
        </div>

        {/* Right Column (1/3) */}
        <div className="space-y-6">
          {/* Lab Results */}
          <Card>
            <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between">
              <h3 className="font-semibold text-slate-800">Recent Lab Results</h3>
              <span className="text-xs text-slate-400">Last 48h</span>
            </div>
            <div className="divide-y divide-slate-100">
              {[
                { priority: 'urgent', label: 'Urgent', color: 'rose', test: 'HIV Viral Load', patient: 'John Doe', source: 'Lancet', date: '28 Jul' },
                { priority: 'unreviewed', label: 'Unreviewed', color: 'amber', test: 'CBC & Differential', patient: 'Sarah Miller', source: 'Pathcare', date: '28 Jul' },
                { priority: 'reviewed', label: 'Reviewed', color: 'emerald', test: 'Lipid Panel', patient: 'Dr. Smith', source: 'Pathcare', date: '27 Jul' },
              ].map((lab) => (
                <div key={lab.test} onClick={() => navigate('/lab')} className="px-5 py-3 hover:bg-slate-50 transition-colors cursor-pointer">
                  <div className="flex items-center justify-between mb-1">
                    <Badge variant={lab.color as any}>{lab.label}</Badge>
                    <span className="text-xs text-slate-400">{lab.date}</span>
                  </div>
                  <p className="text-sm font-medium text-slate-700">{lab.test}</p>
                  <p className="text-xs text-slate-500">{lab.patient} · {lab.source}</p>
                </div>
              ))}
            </div>
          </Card>

          {/* Recalls */}
          <Card>
            <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between">
              <h3 className="font-semibold text-slate-800">Recalls Due</h3>
              <button onClick={() => navigate('/schedule?tab=recalls')} className="text-xs font-medium text-teal-600 hover:text-teal-700">
                View all
              </button>
            </div>
            <div className="divide-y divide-slate-100">
              {[
                { status: 'overdue', label: 'Overdue', dot: 'bg-rose-500', name: 'Thandi Mokoena', detail: 'Hypertension follow-up · 5 days late' },
                { status: 'due', label: 'Due today', dot: 'bg-amber-500', name: 'Peter van Wyk', detail: 'Diabetes check · HbA1c due' },
                { status: 'upcoming', label: 'Due in 2 days', dot: 'bg-amber-500', name: 'Lerato Dlamini', detail: 'Post-natal check' },
              ].map((r) => (
                <div key={r.name} className="px-5 py-3 hover:bg-slate-50 transition-colors cursor-pointer">
                  <div className="flex items-center gap-2 mb-1">
                    <span className={cn("h-2 w-2 rounded-full", r.dot)} />
                    <span className={cn("text-xs font-medium", r.status === 'overdue' ? 'text-rose-600' : 'text-amber-600')}>{r.label}</span>
                  </div>
                  <p className="text-sm font-medium text-slate-700">{r.name}</p>
                  <p className="text-xs text-slate-500">{r.detail}</p>
                </div>
              ))}
            </div>
          </Card>

          {/* Sync Mini Panel */}
          <Card>
            <div className="px-5 py-4 border-b border-slate-100">
              <h3 className="font-semibold text-slate-800">Sync Status</h3>
            </div>
            <div className="p-5 space-y-3">
              <SyncBar label="Patients" value={1247} max={1247} color="emerald" />
              <SyncBar label="Appointments" value={89} max={89} color="emerald" />
              <SyncBar label="Lab results" value={42} max={45} color="amber" pending={3} />
              <div className="pt-2 border-t border-slate-100">
                <Button variant="secondary" size="sm" className="w-full" onClick={() => {}}>
                  Open Outbox {pendingCount > 0 && `(${pendingCount})`}
                </Button>
              </div>
            </div>
          </Card>
        </div>
      </div>
    </div>
  )
}

// --- Subcomponents ---

function StatCard({ label, value, subtext, icon: Icon, iconColor, iconBg, onClick }: any) {
  return (
    <div onClick={onClick} className="bg-white rounded-xl border border-slate-200 p-5 hover:shadow-sm transition-shadow cursor-pointer group">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-sm font-medium text-slate-500">{label}</p>
          <p className="text-3xl font-bold text-slate-800 mt-1">{value}</p>
          <p className="text-xs text-slate-500 mt-1 font-medium">{subtext}</p>
        </div>
        <div className={cn("h-10 w-10 rounded-lg flex items-center justify-center transition-colors", iconBg, iconColor)}>
          <Icon className="h-5 w-5" />
        </div>
      </div>
    </div>
  )
}

function Slot({ slot, color }: { slot: any, color: 'teal' | 'indigo' }) {
  if (!slot) {
    return (
      <div className="rounded-lg border border-dashed border-slate-200 min-h-[44px] flex items-center justify-center">
        <span className="text-xs text-slate-300">Free</span>
      </div>
    )
  }
  const isHighlight = slot.highlight
  return (
    <div className={cn(
      "rounded-lg border px-3 py-2 cursor-pointer hover:shadow-sm transition-all",
      isHighlight
        ? "border-amber-300 bg-amber-50 ring-1 ring-amber-200"
        : color === 'teal'
          ? "border-teal-200 bg-teal-50/50"
          : "border-indigo-200 bg-indigo-50/50"
    )}>
      <div className="flex items-center justify-between">
        <p className={cn("text-sm font-medium", isHighlight ? "text-amber-800" : `text-${color}-800`)}>
          {slot.name}
        </p>
        {isHighlight && <span className="h-2 w-2 rounded-full bg-amber-500 animate-pulse" />}
      </div>
      <p className={cn("text-xs", isHighlight ? "text-amber-600" : `text-${color}-600/70`)}>{slot.type}</p>
    </div>
  )
}

function SyncBar({ label, value, max, color, pending }: any) {
  const pct = Math.round((value / max) * 100)
  return (
    <>
      <div className="flex items-center justify-between text-sm">
        <span className="text-slate-500">{label}</span>
        <span className={cn("font-medium", pending ? "text-amber-600" : "text-emerald-600")}>
          {value.toLocaleString()} synced{pending ? ` · ${pending} pending` : ''}
        </span>
      </div>
      <div className="w-full bg-slate-100 rounded-full h-1.5">
        <div className={cn("h-1.5 rounded-full", `bg-${color}-500`)} style={{ width: `${pct}%` }} />
      </div>
    </>
  )
}