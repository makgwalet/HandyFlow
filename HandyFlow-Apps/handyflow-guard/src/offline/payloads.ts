import type { OutboxItem, OutboxKind } from "./outbox";

export interface Position {
  latitude: number;
  longitude: number;
  /** GPS error in metres, if the phone reports it. */
  accuracy: number | null;
}

export interface Context {
  guardId: string;
  newId: () => string;
  now: () => Date;
}

/**
 * Wraps a payload with the two things that make a write safe to retry: the clientRef (also the outbox id, made once)
 * and recordedAt (when it happened on the phone, not when it is finally sent).
 */
function make(kind: OutboxKind, body: Record<string, unknown>, ctx: Context): OutboxItem {
  const id = ctx.newId();
  const at = ctx.now().toISOString();
  return {
    id,
    guardId: ctx.guardId,
    kind,
    payload: { ...body, clientRef: id, recordedAt: at },
    recordedAt: at,
    status: "PENDING",
    attempts: 0,
    lastError: null,
    createdAt: at,
  };
}

export function scanItem(
  input: { qrCode: string; shiftId: string | null; position: Position | null; deviceId: string },
  ctx: Context,
): OutboxItem {
  return make(
    "SCAN",
    {
      qrCode: input.qrCode,
      scanType: "QR",
      shiftId: input.shiftId,
      latitude: input.position?.latitude ?? null,
      longitude: input.position?.longitude ?? null,
      accuracy: input.position?.accuracy ?? null,
      deviceId: input.deviceId,
    },
    ctx,
  );
}

export const SEVERITIES = ["LOW", "MEDIUM", "HIGH", "CRITICAL"] as const;
export type Severity = (typeof SEVERITIES)[number];
export const INCIDENT_TYPES = ["THEFT", "TRESPASS", "MEDICAL", "FIRE", "VANDALISM", "ASSAULT", "SUSPICIOUS", "OTHER"] as const;
export type IncidentType = (typeof INCIDENT_TYPES)[number];

export function incidentItem(
  input: {
    siteId: string;
    shiftId: string | null;
    title: string;
    description: string;
    severity: Severity;
    type: IncidentType;
    position: Position | null;
  },
  ctx: Context,
): OutboxItem {
  return make(
    "INCIDENT",
    {
      siteId: input.siteId,
      shiftId: input.shiftId,
      title: input.title.trim(),
      description: input.description.trim() || null,
      severity: input.severity,
      type: input.type,
      latitude: input.position?.latitude ?? null,
      longitude: input.position?.longitude ?? null,
    },
    ctx,
  );
}

/**
 * A photo for an incident that may not have reached the server yet. `incidentRef` is the incident item's id (its
 * clientRef); the item's own id is the photo's id, so a retried upload is attached once.
 */
export function evidenceItem(
  input: { incidentRef: string; localUri: string; label: string | null },
  ctx: Context,
): OutboxItem {
  return make("EVIDENCE", { incidentRef: input.incidentRef, localUri: input.localUri, label: input.label }, ctx);
}

export function duressItem(input: { position: Position | null }, ctx: Context): OutboxItem {
  return make(
    "DURESS",
    { latitude: input.position?.latitude ?? null, longitude: input.position?.longitude ?? null },
    ctx,
  );
}

/**
 * "I have read this version", made while there was no signal. It names the version the guard actually saw, so the
 * server refuses it (409) if a newer one was published meanwhile instead of recording a version they never read.
 */
export function acknowledgeItem(input: { orderId: string; version: number; deviceHardwareId: string }, ctx: Context): OutboxItem {
  return make("ACK", { orderId: input.orderId, version: input.version, deviceHardwareId: input.deviceHardwareId }, ctx);
}

export interface GateArrivalInput {
  deviceHardwareId: string;
  accessPointId: string;
  entryType: string;
  personName: string;
  idNumber: string;
  company: string;
  hostName: string;
  purpose: string;
  vehicleRegistration: string;
}

const clean = (v: string) => v.trim() || null;

/**
 * A visitor logged while there was no signal. The item's id is the arrival's clientRef, so it can be named by a
 * departure or a photo saved before the arrival has reached the server, and a retry logs the visitor once.
 * `newId` should return the form's own clientRef so a retry after a lost reply is recognised.
 */
export function gateArrivalItem(input: GateArrivalInput, ctx: Context): OutboxItem {
  return make(
    "GATE_ARRIVAL",
    {
      deviceHardwareId: input.deviceHardwareId,
      accessPointId: input.accessPointId,
      entryType: input.entryType,
      personName: input.personName.trim(),
      idNumber: clean(input.idNumber),
      company: clean(input.company),
      hostName: clean(input.hostName),
      purpose: clean(input.purpose),
      vehicleRegistration: clean(input.vehicleRegistration)?.toUpperCase() ?? null,
      idScanConfidence: clean(input.idNumber) ? "MANUAL_ENTRY" : null,
    },
    ctx,
  );
}

/** Which gate entry something refers to: the server's id, or the clientRef of an arrival still waiting to be sent. */
export type EntryRef = { entryId: string; entryRef?: undefined } | { entryRef: string; entryId?: undefined };

export function gateExitItem(input: EntryRef & { deviceHardwareId: string }, ctx: Context): OutboxItem {
  return make("GATE_EXIT", { entryId: input.entryId ?? null, entryRef: input.entryRef ?? null, deviceHardwareId: input.deviceHardwareId }, ctx);
}

/** A gate photo kept on the phone. The item's id is the photo's id, so a retried upload is attached once. */
export function gatePhotoItem(input: EntryRef & { docType: string; localUri: string }, ctx: Context): OutboxItem {
  return make("GATE_PHOTO", { entryId: input.entryId ?? null, entryRef: input.entryRef ?? null, docType: input.docType, localUri: input.localUri }, ctx);
}
