import { useCallback, useEffect, useState } from 'react';
import { getPendingCount, syncPendingMutations, SyncResult } from './queue';

export function useOfflineSync() {
  const [pendingCount, setPendingCount] = useState<number | null>(null);
  const [isSyncing, setIsSyncing] = useState(false);

  const refresh = useCallback(async () => {
    const count = await getPendingCount();
    setPendingCount(count);
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const syncNow = useCallback(async (): Promise<SyncResult> => {
    setIsSyncing(true);
    try {
      const result = await syncPendingMutations();
      await refresh();
      return result;
    } finally {
      setIsSyncing(false);
    }
  }, [refresh]);

  return { pendingCount, isSyncing, refresh, syncNow };
}
