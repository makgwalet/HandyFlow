import { useQuery } from '@tanstack/react-query';
import { getFarms, getSpecies } from '../../api/agriculture/farms';

export function useFarms(status?: string) {
  return useQuery({
    queryKey: ['farms', { status }],
    queryFn: () => getFarms({ status }),
  });
}

export function useSpecies(category?: string) {
  return useQuery({
    queryKey: ['species', { category }],
    queryFn: () => getSpecies({ category }),
    staleTime: 1000 * 60 * 10, // reference data — safe to cache longer
  });
}
