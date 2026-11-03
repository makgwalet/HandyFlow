import { useEffect, useCallback } from 'react'
import { useSyncStore } from '@/stores/sync-store'
import { getSyncStats, getPendingSync, markSyncResolved, markSyncFailed } from '@/db/sync-queue'
import { db } from '@/db/clinic-db'

export function useSync() {
  const { isSyncing, lastSyncAt, pendingCount, failedCount, setSyncing, setLastSync, setCounts } = useSyncStore()

  const refreshCounts = useCallback(async () => {
    const stats = await getSyncStats()
    setCounts(stats.pending, stats.failed)
  }, [setCounts])

  const performSync = useCallback(async () => {
    if (isSyncing) return
    setSyncing(true)
    try {
      const pending = await getPendingSync()
      for (const item of pending) {
        await new Promise(r => setTimeout(r, 300))
        const table = (db as Record<string, unknown>)[item.entityType + 's']
        if (table && typeof table === 'object' && 'update' in table) {
          await (table as { update: (id: string, data: unknown) => Promise<void> }).update(item.entityId, { syncStatus: 'synced' })
        }
        await markSyncResolved(item.id)
      }
      const now = Date.now()
      await db.setLastSyncTimestamp(now)
      setLastSync(now)
      await refreshCounts()
    } catch (err) {
      console.error('Sync failed:', err)
    } finally {
      setSyncing(false)
    }
  }, [isSyncing, setSyncing, setLastSync, refreshCounts])

  useEffect(() => {
    refreshCounts()
    const interval = setInterval(refreshCounts, 5000)
    return () => clearInterval(interval)
  }, [refreshCounts])

  return {
    isSyncing,
    lastSyncAt,
    pendingCount,
    failedCount,
    sync: performSync,
    refreshCounts,
  }
}