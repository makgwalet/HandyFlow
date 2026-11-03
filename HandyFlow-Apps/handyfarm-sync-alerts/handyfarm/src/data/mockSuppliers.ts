export interface MockSupplier {
  id: string;
  name: string;
}

export const MOCK_SUPPLIERS: MockSupplier[] = [
  { id: 's1', name: 'Sasol Fuels' },
  { id: 's2', name: 'Engen Wholesale' },
  { id: 's3', name: 'TotalEnergies Bulk' },
  { id: 's4', name: 'Shell Fleet Solutions' },
];
