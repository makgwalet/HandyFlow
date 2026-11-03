import React from 'react';
import { useAuthStore } from '../store/authStore';
import { LoginScreen } from '../screens/auth/LoginScreen';
import { OTPScreen } from '../screens/auth/OTPScreen';

/**
 * Not a react-navigation stack on purpose: the phase transitions
 * (phone -> otp -> authenticated) are driven entirely by auth store
 * state, which will later also gate device-registration and PIN/biometric
 * steps without changing this switch's shape.
 */
export function AuthNavigator() {
  const phase = useAuthStore((s) => s.phase);

  if (phase === 'otp') return <OTPScreen />;
  return <LoginScreen />;
}
