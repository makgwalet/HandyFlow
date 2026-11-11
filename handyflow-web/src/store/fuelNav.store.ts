// Which Fuel divisions are active for the company, for navigation only. Set by FuelPage after it loads the operating profile;
// null means "not known yet", in which case nothing is hidden. The server enforces divisions regardless.
import { create } from 'zustand'

interface FuelNavState {
  divisions: string[] | null
  setDivisions: (d: string[] | null) => void
}

export const useFuelNavStore = create<FuelNavState>(set => ({
  divisions: null,
  setDivisions: divisions => set({ divisions }),
}))
