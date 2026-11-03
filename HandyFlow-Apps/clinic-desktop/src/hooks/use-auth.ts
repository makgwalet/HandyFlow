import { useAuthStore } from '@/stores/auth-store'
import { seedDatabase } from '@/db/seed'
import type { User } from '@/types/clinic'

export function useAuth() {
  const { user, isAuthenticated, isOffline, login, logout, setOffline } = useAuthStore()

  const performLogin = async (email: string, password: string) => {
    const mockUser: User = {
      id: 'user-001',
      tenantId: 'tenant-001',
      email,
      firstName: 'Admin',
      lastName: 'User',
      permissions: [
        'CLINIC_READ', 'CLINIC_WRITE',
        'CLINIC_LAB_WRITE', 'CLINIC_PRESCRIPTION_WRITE',
        'CLINIC_BILLING_READ', 'CLINIC_BILLING_WRITE',
        'USER_READ', 'USER_UPDATE', 'SETTINGS_MANAGE'
      ],
      role: 'Practice Manager',
    }
    login(mockUser)
    await seedDatabase(mockUser.tenantId)
    return mockUser
  }

  const hasPermission = (permission: string): boolean => {
    return user?.permissions.includes(permission) ?? false
  }

  return {
    user,
    isAuthenticated,
    isOffline,
    login: performLogin,
    logout,
    setOffline,
    hasPermission,
  }
}