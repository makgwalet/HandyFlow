import { z } from "zod";

export const supplierSchema = z.object({ id: z.string(), name: z.string(), approvalStatus: z.string().nullish() });
export type Supplier = z.infer<typeof supplierSchema>;
export const supplierListSchema = z.array(supplierSchema);

const num = z.coerce.number();
export const purchaseOrderSchema = z.object({
  id: z.string(),
  poNumber: z.string(),
  supplierId: z.string(),
  supplierName: z.string().nullish(),
  fuelType: z.string(),
  litresOutstanding: num.nullish(),
  status: z.string(),
});
export type PurchaseOrder = z.infer<typeof purchaseOrderSchema>;
export const purchaseOrderListSchema = z.array(purchaseOrderSchema);

/** The server only takes fuel from an approved supplier. */
export const usableSuppliers = (all: Supplier[]): Supplier[] =>
  all.filter((s) => (s.approvalStatus ?? "APPROVED") === "APPROVED").sort((a, b) => a.name.localeCompare(b.name));

/**
 * Orders this delivery can be booked against: placed with the supplier (ORDERED or PART_RECEIVED), for this supplier and
 * this tank's fuel. The server checks all of it again, including how many litres the order can still take.
 */
export function ordersFor(orders: PurchaseOrder[], supplierId: string | null, tankFuelType: string): PurchaseOrder[] {
  if (!supplierId) return [];
  return orders.filter((o) => o.supplierId === supplierId && o.fuelType.toLowerCase() === tankFuelType.toLowerCase() && (o.status === "ORDERED" || o.status === "PART_RECEIVED"));
}
