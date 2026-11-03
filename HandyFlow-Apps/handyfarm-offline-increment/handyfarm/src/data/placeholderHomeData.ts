/**
 * PLACEHOLDER DATA — NOT A FAKE API.
 *
 * Per project rule "never fake APIs": this file exists only to drive the
 * Home screen prototype while the Java backend has not yet been inspected.
 * It must be replaced by a real `useHomeSummary()` hook backed by TanStack
 * Query + an API service once /docs/agriculture/10-API-MAP.md exists and
 * confirms the corresponding endpoint (or the endpoint is logged as an
 * API GAP in /docs/agriculture/11-API-GAPS.md).
 */

export type AttentionSeverity = 'critical' | 'action' | 'upcoming' | 'info';

export interface AttentionItem {
  id: string;
  severity: AttentionSeverity;
  title: string;
}

export interface QuickCaptureAction {
  id: string;
  label: string;
  icon: string; // emoji placeholder until icon set is chosen
}

export interface FarmStat {
  id: string;
  icon: string;
  value: string;
  label: string;
}

export interface HomeSummary {
  farmName: string;
  greeting: string;
  stats: FarmStat[];
  attention: AttentionItem[];
  quickCapture: QuickCaptureAction[];
  pendingSyncCount: number;
}

export const placeholderHomeSummary: HomeSummary = {
  farmName: 'Verena Farm',
  greeting: 'Good morning',
  // Poultry stats (eggs/birds) removed — Poultry has no backend yet
  // (see docs/agriculture/11-API-GAPS.md, GAP 1). Livestock/Crops stats
  // are still placeholder pending the aggregate summary endpoint from
  // GAP 4; the real per-farm counts are one tap away via the Production tab.
  stats: [
    { id: 'cattle', icon: '🐄', value: '342', label: 'cattle' },
    { id: 'hectares', icon: '🌾', value: '284 ha', label: 'planted' },
  ],
  attention: [
    { id: 'a1', severity: 'critical', title: 'Broiler mortality trending up' },
    { id: 'a2', severity: 'action', title: 'Feed stock below minimum' },
    { id: 'a3', severity: 'upcoming', title: 'Vaccinations due tomorrow' },
    { id: 'a4', severity: 'info', title: 'Field 12 pest detected' },
  ],
  // Only actions the Java backend actually supports today are wired to a real
  // flow (see docs/agriculture/10-API-MAP.md). Eggs/Harvest/Photo/Task are
  // shown per the product brief's IA but intentionally do nothing yet —
  // Poultry doesn't exist on the backend (API GAP 1), Harvest exists for
  // crops but has no screen yet, and Photo/Task have no backend support
  // (API GAP 6). Tapping them explains why instead of pretending to work.
  quickCapture: [
    { id: 'weight', label: 'Weight', icon: '⚖️' },
    { id: 'treatment', label: 'Treatment', icon: '💉' },
    { id: 'mortality', label: 'Mortality', icon: '⚠️' },
    { id: 'eggs', label: 'Eggs', icon: '🥚' },
    { id: 'harvest', label: 'Harvest', icon: '🌾' },
    { id: 'photo', label: 'Photo', icon: '📷' },
  ],
  pendingSyncCount: 7,
};
