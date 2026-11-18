import { z } from "zod";

/** The alerts a person can switch off for themselves. The server decides who is eligible to get each one; this only mutes. */
export const PUSH_TYPES: { type: string; label: string; hint: string }[] = [
  { type: "OVERDUE_DELIVERY", label: "A delivery is overdue", hint: "Drivers are told first; dispatch is told later if it is still open." },
  { type: "DIP_VARIANCE", label: "A tank dip is off", hint: "The dip does not match the book stock." },
  { type: "SHIFT_OUTSIDE_TOLERANCE", label: "A shift closed outside tolerance", hint: "The takings differ from the meters by more than allowed." },
  { type: "ARRIVAL_CHECK_BLOCKED", label: "A driver's arrival check is blocked", hint: "A load cannot be offloaded until it is sorted." },
];

export const prefsSchema = z.record(z.string(), z.boolean());
export type Prefs = z.infer<typeof prefsSchema>;

/** A type the server did not list is on: that is the default. */
export const isOn = (p: Prefs, type: string): boolean => p[type] !== false;
