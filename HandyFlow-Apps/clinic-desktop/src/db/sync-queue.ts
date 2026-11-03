import { db } from './clinic-db'
import type { SyncQueueItem } from '@/types/clinic'
import { generateId } from '@/lib/utils'

export async function enqueueSync(
  entityType: string,
  entityId: string,
  operation: 'create' | 'update' | 'delete',
  payload: unknown
): Promise<void> {
  const item: SyncQueueItem = {
    id: generateId(),
    entityType,
    entityId,
    operation,
    payload: JSON.stringify(payload),
    createdAt: Date.now(),
    retryCount: 0,
    status: 'pending',
  }
  await db.syncQueue.put(item)
}

export async function getPendingSync(): Promise<SyncQueueItem[]> {
  return db.syncQueue.where('status').equals('pending').sortBy('createdAt')
}

export async function markSyncProcessing(id: string): Promise<void> {
  await db.syncQueue.update(id, { status: 'processing' })
}

export async function markSyncFailed(id: string, error: string): Promise<void> {
  const item = await db.syncQueue.get(id)
  if (!item) return
  const retryCount = item.retryCount + 1
  const status = retryCount >= 5 ? 'failed' : 'pending'
  await db.syncQueue.update(id, { status, retryCount, lastError: error })
}

export async function markSyncResolved(id: string): Promise<void> {
  await db.syncQueue.update(id, { status: 'resolved' })
}

export async function getSyncStats(): Promise<{ pending: number; failed: number; total: number }> {
  const [pending, failed, total] = await Promise.all([
    db.syncQueue.where('status').equals('pending').count(),
    db.syncQueue.where('status').equals('failed').count(),
    db.syncQueue.count(),
  ])
  return { pending, failed, total }
}

export async function clearResolvedSync(): Promise<number> {
  return db.syncQueue.where('status').equals('resolved').delete()
}