import { useSync } from '@/hooks/use-sync'
import { useAuthStore } from '@/stores/auth-store'
import { Card, CardHeader } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { db } from '@/db/clinic-db'
import { useLiveQuery } from 'dexie-react-hooks'
import { RefreshCw, Database, Wifi, WifiOff, Trash2 } from 'lucide-react'

export function Settings() {
  const { isSyncing, pendingCount, failedCount, sync, lastSyncAt } = useSync()
  const { isOffline, setOffline } = useAuthStore()

  const stats = useLiveQuery(async () => {
    const tenantId = await db.getTenantId()
    const [patients, appointments, consultations, labResults] = await Promise.all([
      db.patients.where({ tenantId }).count(),
      db.appointments.where({ tenantId }).count(),
      db.consultations.where({ tenantId }).count(),
      db.labResults.where({ tenantId }).count(),
    ])
    return { patients, appointments, consultations, labResults }
  })

  return (
    <div className="space-y-6 max-w-2xl">
      <Card>
        <CardHeader title="Sync & Connectivity" />
        <div className="space-y-4">
          <div className="flex items-center justify-between rounded-lg border border-medical-border p-4">
            <div className="flex items-center gap-3">
              {isOffline ? <WifiOff className="h-5 w-5 text-rose-500" /> : <Wifi className="h-5 w-5 text-emerald-500" />}
              <div>
                <p className="font-medium text-medical-text">{isOffline ? 'Offline Mode' : 'Online'}</p>
                <p className="text-sm text-medical-muted">{isOffline ? 'Working locally. Changes will sync when reconnected.' : 'Connected to server'}</p>
              </div>
            </div>
            <Button variant="secondary" size="sm" onClick={() => setOffline(!isOffline)}>{isOffline ? 'Go Online' : 'Simulate Offline'}</Button>
          </div>
          <div className="flex items-center justify-between rounded-lg border border-medical-border p-4">
            <div className="flex items-center gap-3">
              <RefreshCw className={`h-5 w-5 text-primary-600 ${isSyncing ? 'animate-spin' : ''}`} />
              <div>
                <p className="font-medium text-medical-text">Sync Status</p>
                <p className="text-sm text-medical-muted">{lastSyncAt ? `Last synced: ${new Date(lastSyncAt).toLocaleString()}` : 'Never synced'}</p>
              </div>
            </div>
            <div className="flex items-center gap-2">
              {pendingCount > 0 && <Badge variant="warning">{pendingCount} pending</Badge>}
              {failedCount > 0 && <Badge variant="danger">{failedCount} failed</Badge>}
              <Button size="sm" onClick={sync} disabled={isSyncing || isOffline}>{isSyncing ? 'Syncing...' : 'Sync Now'}</Button>
            </div>
          </div>
        </div>
      </Card>

      <Card>
        <CardHeader title="Local Database" />
        <div className="grid grid-cols-2 gap-4">
          <div className="rounded-lg border border-medical-border p-4 text-center">
            <Database className="mx-auto h-6 w-6 text-medical-muted" />
            <p className="mt-2 text-2xl font-bold text-medical-text">{stats?.patients || 0}</p>
            <p className="text-sm text-medical-muted">Patients</p>
          </div>
          <div className="rounded-lg border border-medical-border p-4 text-center">
            <Database className="mx-auto h-6 w-6 text-medical-muted" />
            <p className="mt-2 text-2xl font-bold text-medical-text">{stats?.appointments || 0}</p>
            <p className="text-sm text-medical-muted">Appointments</p>
          </div>
          <div className="rounded-lg border border-medical-border p-4 text-center">
            <Database className="mx-auto h-6 w-6 text-medical-muted" />
            <p className="mt-2 text-2xl font-bold text-medical-text">{stats?.consultations || 0}</p>
            <p className="text-sm text-medical-muted">Consultations</p>
          </div>
          <div className="rounded-lg border border-medical-border p-4 text-center">
            <Database className="mx-auto h-6 w-6 text-medical-muted" />
            <p className="mt-2 text-2xl font-bold text-medical-text">{stats?.labResults || 0}</p>
            <p className="text-sm text-medical-muted">Lab Results</p>
          </div>
        </div>
      </Card>

      <Card>
        <CardHeader title="Danger Zone" />
        <div className="rounded-lg border border-rose-200 bg-rose-50 p-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="font-medium text-rose-700">Clear Local Data</p>
              <p className="text-sm text-rose-600">This will delete all local data. Use with caution.</p>
            </div>
            <Button variant="danger" size="sm"><Trash2 className="mr-2 h-4 w-4" />Clear Data</Button>
          </div>
        </div>
      </Card>
    </div>
  )
}