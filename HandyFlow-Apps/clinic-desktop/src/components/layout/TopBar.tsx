import { useUIStore } from '@/stores/ui-store'
import { useSync } from '@/hooks/use-sync'
import { useAuthStore } from '@/stores/auth-store'
import { cn } from '@/lib/utils'
import { Wifi, WifiOff, RefreshCw, AlertTriangle } from 'lucide-react'

export function TopBar() {
  const { currentPage } = useUIStore()
  const { isSyncing, pendingCount, failedCount, sync } = useSync()
  const { isOffline } = useAuthStore()

  return (
    <header className="flex h-16 items-center justify-between border-b border-medical-border bg-white px-6">
      <h1 className="text-xl font-semibold text-medical-text">{currentPage}</h1>
      <div className="flex items-center gap-4">
        <div className="flex items-center gap-2">
          {pendingCount > 0 && (
            <span className="rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-medium text-amber-700">{pendingCount} pending</span>
          )}
          {failedCount > 0 && (
            <span className="flex items-center gap-1 rounded-full bg-rose-100 px-2.5 py-0.5 text-xs font-medium text-rose-700">
              <AlertTriangle className="h-3 w-3" />{failedCount} failed
            </span>
          )}
          <button onClick={sync} disabled={isSyncing || isOffline} className={cn('flex items-center gap-1.5 rounded-lg border border-medical-border px-3 py-1.5 text-xs font-medium transition-colors', isSyncing ? 'bg-slate-50 text-primary-600' : 'bg-white text-medical-muted hover:bg-slate-50')}>
            <RefreshCw className={cn('h-3.5 w-3.5', isSyncing && 'animate-spin')} />
            {isSyncing ? 'Syncing...' : 'Sync now'}
          </button>
        </div>
        <div className={cn('flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-medium', isOffline ? 'bg-rose-50 text-rose-600' : 'bg-emerald-50 text-emerald-600')}>
          {isOffline ? <WifiOff className="h-3.5 w-3.5" /> : <Wifi className="h-3.5 w-3.5" />}
          {isOffline ? 'Offline' : 'Online'}
        </div>
      </div>
    </header>
  )
}