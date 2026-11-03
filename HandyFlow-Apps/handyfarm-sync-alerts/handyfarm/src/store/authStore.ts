import { create } from 'zustand';
import * as SecureStore from 'expo-secure-store';
import type { AuthUser, Role } from '../types/domain';

/**
 * Auth store — Phase 1 foundation.
 *
 * There is no backend yet, so login/OTP are mocked locally. The shape of
 * this store already matches the spec's login architecture (phone entry →
 * OTP → device session), so wiring a real API later means swapping the
 * mocked bodies of `requestOtp` / `verifyOtp`, not the store shape.
 *
 * The persisted session key mirrors what Phase 0 backend work calls for:
 * device id, role, and a session — stored in SecureStore rather than
 * AsyncStorage per the spec's "platform's secure mobile storage" guidance.
 */

const SESSION_KEY = 'handyflow.session';

type AuthPhase = 'phone' | 'otp' | 'authenticated';

interface MockDirectoryEntry extends AuthUser {}

// Stand-in "directory" so QA can preview all three role experiences before
// a real Identity service exists. Any 6-digit OTP is accepted.
const MOCK_DIRECTORY: Record<Role, MockDirectoryEntry> = {
  FUEL_MANAGER: {
    id: 'usr-manager-1',
    name: 'Naledi Khumalo',
    role: 'FUEL_MANAGER',
    site: 'All Sites',
    phone: '',
    initials: 'NK',
  },
  OPERATOR: {
    id: 'usr-operator-1',
    name: 'Thabo Mokoena',
    role: 'OPERATOR',
    site: 'Matimba Mine',
    phone: '',
    initials: 'TM',
  },
  DRIVER: {
    id: 'usr-driver-1',
    name: 'Sipho Dlamini',
    role: 'DRIVER',
    site: 'Depot A',
    phone: '',
    initials: 'SD',
  },
};

interface AuthState {
  phase: AuthPhase;
  phone: string;
  selectedRole: Role;
  user: AuthUser | null;
  isRestoring: boolean;
  error: string | null;

  restoreSession: () => Promise<void>;
  setSelectedRole: (role: Role) => void;
  requestOtp: (phone: string) => Promise<void>;
  verifyOtp: (code: string) => Promise<void>;
  resendOtp: () => Promise<void>;
  logout: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  phase: 'phone',
  phone: '',
  selectedRole: 'OPERATOR',
  user: null,
  isRestoring: true,
  error: null,

  restoreSession: async () => {
    try {
      const raw = await SecureStore.getItemAsync(SESSION_KEY);
      if (raw) {
        const user = JSON.parse(raw) as AuthUser;
        set({ user, phase: 'authenticated', isRestoring: false });
        return;
      }
    } catch {
      // Corrupt or inaccessible session — fall through to login.
    }
    set({ isRestoring: false });
  },

  setSelectedRole: (role) => set({ selectedRole: role }),

  requestOtp: async (phone) => {
    set({ error: null, phone });
    // Mock network delay — real implementation calls POST /auth/otp.
    await new Promise((r) => setTimeout(r, 400));
    set({ phase: 'otp' });
  },

  verifyOtp: async (code) => {
    set({ error: null });
    if (code.length !== 6) {
      set({ error: 'Enter the 6-digit code' });
      return;
    }
    await new Promise((r) => setTimeout(r, 400));

    const { selectedRole, phone } = get();
    const user: AuthUser = { ...MOCK_DIRECTORY[selectedRole], phone };

    await SecureStore.setItemAsync(SESSION_KEY, JSON.stringify(user));
    set({ user, phase: 'authenticated' });
  },

  resendOtp: async () => {
    await new Promise((r) => setTimeout(r, 300));
  },

  logout: async () => {
    await SecureStore.deleteItemAsync(SESSION_KEY);
    set({ user: null, phase: 'phone', phone: '', error: null });
  },
}));
