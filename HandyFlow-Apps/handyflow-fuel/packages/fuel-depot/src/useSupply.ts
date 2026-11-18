import { useQuery } from "@tanstack/react-query";
import { api, ApiError } from "@/api/client";
import { useAuth } from "@/auth/AuthContext";
import { useOutbox } from "@/offline/OutboxProvider";
import { purchaseOrderListSchema, supplierListSchema, type PurchaseOrder, type Supplier } from "./supply";

interface Saved { suppliers: Supplier[]; orders: PurchaseOrder[] }

/** Suppliers and open orders for the Receive fuel form. Kept on the phone, so a depot with no signal can still pick them. */
export function useSupply() {
  const { session } = useAuth();
  const { kv } = useOutbox();
  const key = `depot.supply.${session?.userId ?? ""}`;
  const q = useQuery({
    queryKey: ["fuel-depot-supply", session?.userId],
    enabled: !!session && !!kv,
    retry: false,
    queryFn: async (): Promise<Saved> => {
      try {
        const suppliers = supplierListSchema.parse(await api<unknown>("/api/v1/fuel/suppliers"));
        // Orders are a convenience: a person without purchasing access still gets the suppliers.
        const orders = await api<unknown>("/api/v1/fuel/purchase-orders").then((r) => purchaseOrderListSchema.parse(r)).catch((e) => {
          if (e instanceof ApiError && e.isNetwork) throw e;
          return [] as PurchaseOrder[];
        });
        const saved = { suppliers, orders };
        await kv?.set(key, JSON.stringify(saved));
        return saved;
      } catch (e) {
        if (e instanceof ApiError && e.isNetwork) {
          const raw = await kv?.get(key);
          if (raw) { try { return JSON.parse(raw) as Saved; } catch { /* fall through */ } }
        }
        throw e;
      }
    },
  });
  return { suppliers: q.data?.suppliers ?? [], orders: q.data?.orders ?? [], isLoading: q.isLoading, unavailable: !!q.error };
}
