import { useMemo } from 'react';
import { useTransactionQueueStore } from '../store/transactionQueueStore';

export function useQueueCounts() {
  const items = useTransactionQueueStore((s) => s.items);
  return useMemo(() => {
    const pending = items.filter((i) => i.status === 'PENDING' || i.status === 'UPLOADING').length;
    const confirmed = items.filter((i) => i.status === 'CONFIRMED').length;
    const failed = items.filter((i) => i.status === 'CONFLICT').length;
    return { pending, confirmed, failed, total: items.length };
  }, [items]);
}
