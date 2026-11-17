import { z } from "zod";

const num = z.coerce.number();

export const summarySchema = z.object({
  today: z.string(),
  monthLitres: num,
  monthDispatches: z.number(),
  monthReceipts: z.number(),
  dailyLitres: z.array(num).default([]),
  negativeVarianceReadings: z.number(),
  openDeliveries: z.number(),
  inTransitDeliveries: z.number(),
  overdueDeliveries: z.number(),
  nextDeliveries: z.array(z.object({
    id: z.string(),
    status: z.string(),
    fuelType: z.string().nullable().optional(),
    litresOrdered: num.nullable().optional(),
    scheduledAt: z.string().nullable().optional(),
  })).default([]),
});
export type Summary = z.infer<typeof summarySchema>;

export const litres = (n: number | null | undefined): string => `${Math.round(n ?? 0).toLocaleString("en-ZA")} L`;

export interface Attention { label: string; tone: "danger" | "warn" }

/** What needs someone's eye, worst first. Empty when nothing does. */
export function attention(s: Summary): Attention[] {
  const out: Attention[] = [];
  if (s.overdueDeliveries > 0) out.push({ label: `${s.overdueDeliveries} ${s.overdueDeliveries === 1 ? "delivery is" : "deliveries are"} overdue: scheduled over a day ago and not on the road`, tone: "danger" });
  if (s.negativeVarianceReadings > 0) out.push({ label: `${s.negativeVarianceReadings} dip ${s.negativeVarianceReadings === 1 ? "reading is" : "readings are"} more than 5 L below the book figure this month`, tone: "warn" });
  return out;
}
