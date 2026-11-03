import { z } from 'zod';

// Mirrors RecordWeightRequest: recordedDate*, weightKg*, sampleSize?, notes?
export const recordWeightSchema = z.object({
  recordedDate: z.string().min(1, 'Date is required'),
  weightKg: z.coerce.number().positive('Weight must be greater than 0'),
  sampleSize: z.coerce.number().int().positive().optional(),
  notes: z.string().optional(),
});
export type RecordWeightFormValues = z.infer<typeof recordWeightSchema>;

// Mirrors CreateHealthEventRequest for the TREATMENT/VACCINATION quick-capture case.
export const recordTreatmentSchema = z.object({
  eventType: z.enum(['TREATMENT', 'VACCINATION', 'DEWORMING']),
  eventDate: z.string().min(1, 'Date is required'),
  description: z.string().min(1, 'Description is required'),
  productUsed: z.string().optional(),
  dosage: z.string().optional(),
  veterinarian: z.string().optional(),
  cost: z.coerce.number().nonnegative().optional(),
  withdrawalPeriodDays: z.coerce.number().int().nonnegative().optional(),
  nextDueDate: z.string().optional(),
  notes: z.string().optional(),
});
export type RecordTreatmentFormValues = z.infer<typeof recordTreatmentSchema>;

// Mirrors CreateMortalityRecordRequest.
export const recordMortalitySchema = z.object({
  mortalityDate: z.string().min(1, 'Date is required'),
  countLost: z.coerce.number().int().min(1, 'At least 1'),
  causeCategory: z.string().min(1, 'Cause is required'),
  causeDetail: z.string().optional(),
  estimatedValueLoss: z.coerce.number().nonnegative().optional(),
  notes: z.string().optional(),
});
export type RecordMortalityFormValues = z.infer<typeof recordMortalitySchema>;

// Mirrors CreateInputApplicationRequest.
export const recordInputApplicationSchema = z.object({
  applicationDate: z.string().min(1, 'Date is required'),
  inputType: z.enum(['FERTILISER', 'PESTICIDE', 'HERBICIDE', 'FUNGICIDE', 'IRRIGATION', 'OTHER']),
  productUsed: z.string().optional(),
  quantityApplied: z.coerce.number().positive('Quantity must be greater than 0'),
  unitOfMeasure: z.string().min(1, 'Unit is required'),
  applicationMethod: z.string().optional(),
  laborHours: z.coerce.number().nonnegative().optional(),
  cost: z.coerce.number().nonnegative().optional(),
  weatherConditions: z.string().optional(),
  notes: z.string().optional(),
});
export type RecordInputApplicationFormValues = z.infer<typeof recordInputApplicationSchema>;

// Mirrors CreateScoutingRecordRequest.
export const recordScoutingSchema = z.object({
  scoutingDate: z.string().min(1, 'Date is required'),
  observationType: z.enum(['PEST', 'DISEASE', 'WEED', 'NUTRIENT_DEFICIENCY', 'WEATHER_DAMAGE', 'GENERAL']),
  severity: z.enum(['LOW', 'MEDIUM', 'HIGH']).optional(),
  description: z.string().min(1, 'Description is required'),
  recommendedAction: z.string().optional(),
  followUpDate: z.string().optional(),
  notes: z.string().optional(),
});
export type RecordScoutingFormValues = z.infer<typeof recordScoutingSchema>;

// Mirrors CreateHarvestRecordRequest.
export const recordHarvestSchema = z.object({
  harvestDate: z.string().min(1, 'Date is required'),
  quantityHarvested: z.coerce.number().positive('Quantity must be greater than 0'),
  unitOfMeasure: z.string().min(1, 'Unit is required'),
  qualityGrade: z.string().optional(),
  moistureContent: z.coerce.number().nonnegative().optional(),
  storageLocation: z.string().optional(),
  laborHours: z.coerce.number().nonnegative().optional(),
  notes: z.string().optional(),
});
export type RecordHarvestFormValues = z.infer<typeof recordHarvestSchema>;
