// src/pages/Schedule.tsx
import { useState } from 'react'
import { useAppointments, useUpdateAppointmentStatus } from '@/hooks/use-appointments'
import { useWaitlist } from '@/hooks/use-waitlist'
import { Card, CardHeader } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { Modal } from '@/components/ui/Modal'
import { cn, formatDate, getInitials } from '@/lib/utils'

type View = 'day' | 'week' | 'month'

export function Schedule() {
  const [view, setView] = useState<View>('day')
  const [selectedDate, setSelectedDate] = useState(new Date())
  const { data: appointments, isLoading } = useAppointments({ date: selectedDate.toISOString().split('T')[0] })
  const { data: waitlist } = useWaitlist()
  const updateStatus = useUpdateAppointmentStatus()
  const [showBookingModal, setShowBookingModal] = useState(false)

  const practitioners = ['Dr. Smith', 'Dr. Patel']
  const hours = Array.from({ length: 13 }, (_, i) => 8 + i) // 08:00 - 20:00

  const getAppointment = (practitioner: string, hour: number) => {
    return appointments?.find(a => {
      const d = new Date(a.startTime)
      return a.practitionerName === practitioner && d.getHours() === hour
    })
  }

  const statusAction = (status: string) => {
    switch (status) {
      case 'booked': return 'confirm'
      case 'confirmed': return 'start'
      case 'in_progress': return 'complete'
      default: return null
    }
  }

  const statusColors: Record<string, string> = {
    booked: 'bg-slate-100 text-slate-600 border-slate-200',
    confirmed: 'bg-teal-50 text-teal-700 border-teal-200',
    in_progress: 'bg-amber-50 text-amber-700 border-amber-200 ring-1 ring-amber-300',
    completed: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    cancelled: 'bg-rose-50 text-rose-700 border-rose-200 line-through opacity-60',
    no_show: 'bg-slate-100 text-slate-400 border-slate-200 line-through',
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <h2 className="text-lg font-semibold text-slate-800">Schedule</h2>
          <div className="flex gap-1 bg-slate-100 rounded-lg p-0.5">
            {(['day', 'week', 'month'] as View[]).map((v) => (
              <button
                key={v}
                onClick={() => setView(v)}
                className={cn(
                  "px-3 py-1.5 rounded-md text-xs font-medium capitalize transition-all",
                  view === v ? "bg-white text-slate-700 shadow-sm" : "text-slate-500 hover:text-slate-700"
                )}
              >
                {v}
              </button>
            ))}
          </div>
        </div>
        <Button onClick={() => setShowBookingModal(true)}><PlusIcon className="mr-2 h-4 w-4" />Book Appointment</Button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
        {/* Calendar Grid */}
        <div className="lg:col-span-3">
          <Card className="overflow-hidden">
            <div className="grid grid-cols-[60px_1fr_1fr] border-b border-slate-100 bg-slate-50/50">
              <div className="p-3 text-xs font-medium text-slate-400 uppercase tracking-wider border-r border-slate-100">Time</div>
              {practitioners.map(p => (
                <div key={p} className="p-3 text-sm font-medium text-slate-700 flex items-center gap-2">
                  <span className={cn("h-2 w-2 rounded-full", p === 'Dr. Smith' ? 'bg-teal-500' : 'bg-indigo-500')} />
                  {p}
                </div>
              ))}
            </div>
            <div className="divide-y divide-slate-100 max-h-[600px] overflow-y-auto">
              {hours.map((hour) => (
                <div key={hour} className="grid grid-cols-[60px_1fr_1fr] min-h-[64px]">
                  <div className="p-3 text-xs font-medium text-slate-400 border-r border-slate-100 bg-slate-50/30">
                    {String(hour).padStart(2, '0')}:00
                  </div>
                  {practitioners.map((practitioner) => {
                    const apt = getAppointment(practitioner, hour)
                    return (
                      <div key={practitioner} className="p-1.5 border-r border-slate-100 last:border-r-0 relative group">
                        {apt ? (
                          <div className={cn("rounded-lg border px-3 py-2 h-full cursor-pointer hover:shadow-sm transition-all", statusColors[apt.status])}>
                            <div className="flex items-center justify-between mb-0.5">
                              <span className="text-sm font-medium truncate">{apt.patientName}</span>
                              <span className="text-[10px] uppercase font-bold opacity-60">{apt.status.replace('_', ' ')}</span>
                            </div>
                            <p className="text-xs opacity-70 truncate">{apt.type}</p>
                            {statusAction(apt.status) && (
                              <button
                                onClick={() => updateStatus.mutate({ id: apt.id, action: statusAction(apt.status)! })}
                                className="absolute top-1 right-1 opacity-0 group-hover:opacity-100 transition-opacity bg-white/80 rounded px-1.5 py-0.5 text-[10px] font-medium shadow-sm"
                              >
                                {statusAction(apt.status) === 'start' ? 'Start' : statusAction(apt.status) === 'complete' ? 'Done' : 'Confirm'}
                              </button>
                            )}
                          </div>
                        ) : (
                          <button
                            onClick={() => setShowBookingModal(true)}
                            className="w-full h-full rounded-lg border border-dashed border-slate-200 hover:border-teal-300 hover:bg-teal-50/30 transition-all flex items-center justify-center opacity-0 group-hover:opacity-100"
                          >
                            <PlusIcon className="h-4 w-4 text-teal-400" />
                          </button>
                        )}
                      </div>
                    )
                  })}
                </div>
              ))}
            </div>
          </Card>
        </div>

        {/* Waitlist Sidebar */}
        <div className="space-y-6">
          <Card>
            <CardHeader title="Waitlist" subtitle="Backfill for cancellations" action={
              <Badge variant="outline">{waitlist?.length ?? 0} waiting</Badge>
            } />
            <div className="divide-y divide-slate-100">
              {waitlist?.map((entry: any) => (
                <div key={entry.id} className="p-3 hover:bg-slate-50 transition-colors">
                  <div className="flex items-center justify-between mb-1">
                    <span className="text-sm font-medium text-slate-700">{entry.patientName}</span>
                    <span className="text-xs text-slate-400">{entry.preferredTime}</span>
                  </div>
                  <p className="text-xs text-slate-500 mb-2">{entry.reason}</p>
                  <div className="flex gap-2">
                    <Button size="sm" variant="ghost" className="h-7 text-xs">Contact</Button>
                    <Button size="sm" variant="secondary" className="h-7 text-xs">Schedule</Button>
                  </div>
                </div>
              ))}
              {(!waitlist || waitlist.length === 0) && (
                <p className="py-6 text-center text-sm text-slate-400">No patients on waitlist</p>
              )}
            </div>
          </Card>

          <Card>
            <CardHeader title="Recalls" subtitle="Follow-ups due" />
            <div className="divide-y divide-slate-100">
              {[
                { name: 'Thandi Mokoena', reason: 'Hypertension follow-up', days: -5 },
                { name: 'Peter van Wyk', reason: 'Diabetes check', days: 0 },
              ].map((r) => (
                <div key={r.name} className="p-3 hover:bg-slate-50 transition-colors flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium text-slate-700">{r.name}</p>
                    <p className="text-xs text-slate-500">{r.reason}</p>
                  </div>
                  <Badge variant={r.days < 0 ? 'danger' : r.days === 0 ? 'warning' : 'default'}>
                    {r.days < 0 ? `${Math.abs(r.days)}d overdue` : r.days === 0 ? 'Today' : `${r.days}d`}
                  </Badge>
                </div>
              ))}
            </div>
          </Card>
        </div>
      </div>

      {/* Booking Modal placeholder */}
      <Modal isOpen={showBookingModal} onClose={() => setShowBookingModal(false)} title="Book Appointment" size="lg">
        <p className="text-sm text-slate-500">Integrate with your existing CreateAppointmentRequest form here.</p>
      </Modal>
    </div>
  )
}