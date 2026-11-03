import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  completeHarvest,
  createHarvestRecord,
  createInputApplication,
  createScoutingRecord,
  getCropCycle,
  getCropCyclesForFarm,
  getCropTypes,
  getHarvestRecords,
  getInputApplications,
  getScoutingRecords,
  markGrowing,
  recordPlanting,
  startHarvest,
} from '../../api/agriculture/cropCycles';
import {
  CreateHarvestRecordRequest,
  CreateInputApplicationRequest,
  CreateScoutingRecordRequest,
} from '../../domain-types/agriculture';

export function useCropTypes(category?: string) {
  return useQuery({
    queryKey: ['crop-types', { category }],
    queryFn: () => getCropTypes({ category }),
    staleTime: 1000 * 60 * 10,
  });
}

export function useCropCyclesForFarm(farmId: string | undefined) {
  return useQuery({
    queryKey: ['crop-cycles', farmId],
    queryFn: () => getCropCyclesForFarm(farmId as string),
    enabled: !!farmId,
  });
}

export function useCropCycle(id: string | undefined) {
  return useQuery({
    queryKey: ['crop-cycle', id],
    queryFn: () => getCropCycle(id as string),
    enabled: !!id,
  });
}

export function useInputApplications(cropCycleId: string | undefined) {
  return useQuery({
    queryKey: ['input-applications', cropCycleId],
    queryFn: () => getInputApplications(cropCycleId as string),
    enabled: !!cropCycleId,
  });
}

export function useScoutingRecords(cropCycleId: string | undefined) {
  return useQuery({
    queryKey: ['scouting-records', cropCycleId],
    queryFn: () => getScoutingRecords(cropCycleId as string),
    enabled: !!cropCycleId,
  });
}

export function useHarvestRecords(cropCycleId: string | undefined) {
  return useQuery({
    queryKey: ['harvest-records', cropCycleId],
    queryFn: () => getHarvestRecords(cropCycleId as string),
    enabled: !!cropCycleId,
  });
}

export function useRecordInputApplication(cropCycleId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateInputApplicationRequest) => createInputApplication(cropCycleId, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['input-applications', cropCycleId] }),
  });
}

export function useRecordScouting(cropCycleId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateScoutingRecordRequest) => createScoutingRecord(cropCycleId, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['scouting-records', cropCycleId] }),
  });
}

export function useRecordHarvest(cropCycleId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateHarvestRecordRequest) => createHarvestRecord(cropCycleId, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['harvest-records', cropCycleId] });
      queryClient.invalidateQueries({ queryKey: ['crop-cycle', cropCycleId] });
    },
  });
}

// ── State-machine transitions ───────────────────────────────────────────

function useCropCycleTransition(cropCycleId: string, fn: (id: string) => Promise<unknown>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => fn(cropCycleId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['crop-cycle', cropCycleId] }),
  });
}

export function useRecordPlanting(cropCycleId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { plantingDate: string; seedQuantity?: number; seedSource?: string }) =>
      recordPlanting(cropCycleId, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['crop-cycle', cropCycleId] }),
  });
}

export function useMarkGrowing(cropCycleId: string) {
  return useCropCycleTransition(cropCycleId, markGrowing);
}

export function useStartHarvest(cropCycleId: string) {
  return useCropCycleTransition(cropCycleId, startHarvest);
}

export function useCompleteHarvest(cropCycleId: string) {
  return useCropCycleTransition(cropCycleId, completeHarvest);
}
