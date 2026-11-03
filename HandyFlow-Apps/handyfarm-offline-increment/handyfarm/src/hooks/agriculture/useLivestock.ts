import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createAnimalHealthEvent,
  createAnimalMortalityRecord,
  getAnimal,
  getAnimalHealthEvents,
  getAnimalWeightHistory,
  getAnimalsForFarm,
  recordAnimalWeight,
} from '../../api/agriculture/animals';
import {
  createGroupHealthEvent,
  createGroupMortalityRecord,
  getGroup,
  getGroupWeightHistory,
  getGroupsForFarm,
  recordGroupWeight,
} from '../../api/agriculture/groups';
import {
  CreateHealthEventRequest,
  CreateMortalityRecordRequest,
  RecordWeightRequest,
} from '../../domain-types/agriculture';

export function useAnimalsForFarm(farmId: string | undefined) {
  return useQuery({
    queryKey: ['animals', farmId],
    queryFn: () => getAnimalsForFarm(farmId as string),
    enabled: !!farmId,
  });
}

export function useGroupsForFarm(farmId: string | undefined) {
  return useQuery({
    queryKey: ['groups', farmId],
    queryFn: () => getGroupsForFarm(farmId as string),
    enabled: !!farmId,
  });
}

export function useGroup(id: string | undefined) {
  return useQuery({
    queryKey: ['group', id],
    queryFn: () => getGroup(id as string),
    enabled: !!id,
  });
}

export function useGroupWeightHistory(id: string | undefined) {
  return useQuery({
    queryKey: ['group-weight-history', id],
    queryFn: () => getGroupWeightHistory(id as string),
    enabled: !!id,
  });
}

export function useAnimal(id: string | undefined) {
  return useQuery({
    queryKey: ['animal', id],
    queryFn: () => getAnimal(id as string),
    enabled: !!id,
  });
}

export function useAnimalWeightHistory(id: string | undefined) {
  return useQuery({
    queryKey: ['animal-weight-history', id],
    queryFn: () => getAnimalWeightHistory(id as string),
    enabled: !!id,
  });
}

/** Records a weight for either an animal or a group — mirrors the backend's shared RecordWeightRequest. */
export function useRecordWeight(target: { animalId?: string; groupId?: string }) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: RecordWeightRequest) =>
      target.animalId ? recordAnimalWeight(target.animalId, body) : recordGroupWeight(target.groupId as string, body),
    onSuccess: (result) => {
      if (result.status !== 'synced') return; // queued offline — nothing to invalidate yet, see docs/agriculture/12-OFFLINE-ARCHITECTURE.md
      if (target.animalId) {
        queryClient.invalidateQueries({ queryKey: ['animal', target.animalId] });
        queryClient.invalidateQueries({ queryKey: ['animal-weight-history', target.animalId] });
      }
      if (target.groupId) {
        queryClient.invalidateQueries({ queryKey: ['groups'] });
        queryClient.invalidateQueries({ queryKey: ['group', target.groupId] });
        queryClient.invalidateQueries({ queryKey: ['group-weight-history', target.groupId] });
      }
    },
  });
}

export function useRecordHealthEvent(target: { animalId?: string; groupId?: string }) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Omit<CreateHealthEventRequest, 'animalId' | 'groupId'>) =>
      target.animalId
        ? createAnimalHealthEvent(target.animalId, body)
        : createGroupHealthEvent(target.groupId as string, body),
    onSuccess: () => {
      if (target.animalId) queryClient.invalidateQueries({ queryKey: ['animal', target.animalId] });
    },
  });
}

export function useRecordMortality(target: { animalId?: string; groupId?: string }) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Omit<CreateMortalityRecordRequest, 'animalId' | 'groupId'>) =>
      target.animalId
        ? createAnimalMortalityRecord(target.animalId, body)
        : createGroupMortalityRecord(target.groupId as string, body),
    onSuccess: (_data, _vars) => {
      if (target.animalId) queryClient.invalidateQueries({ queryKey: ['animal', target.animalId] });
      if (target.groupId) queryClient.invalidateQueries({ queryKey: ['groups'] });
    },
  });
}
