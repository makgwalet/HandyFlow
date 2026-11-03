import React from 'react';
import { Ionicons } from '@expo/vector-icons';
import { TodayScreen } from '../screens/home/TodayScreen';
import { PlaceholderScreen } from '../screens/placeholder/PlaceholderScreen';
import { MoreScreen } from '../screens/placeholder/MoreScreen';
import { DeliveryListScreen } from '../screens/delivery/DeliveryListScreen';
import { AlertsScreen } from '../screens/alerts/AlertsScreen';
import type { Role } from '../types/domain';

export interface TabDef {
  name: string;
  label: string;
  icon: keyof typeof Ionicons.glyphMap;
  component: React.ComponentType<any>;
}

/**
 * "The navigation should therefore be role-driven, not hard-coded."
 * — spec section 6. Each role gets a distinct tab bar built from the same
 * screen primitives; Today/Dashboard is the only screen fully built in
 * this phase, everything else is a labeled placeholder that later phases
 * fill in one at a time.
 */
export const ROLE_TABS: Record<Role, TabDef[]> = {
  OPERATOR: [
    { name: 'Today', label: 'Today', icon: 'home-outline', component: TodayScreen },
    {
      name: 'Fuel',
      label: 'Fuel',
      icon: 'water-outline',
      component: () => <PlaceholderScreen title="Fuel" icon="water-outline" />,
    },
    {
      name: 'Deliveries',
      label: 'Deliveries',
      icon: 'car-outline',
      component: DeliveryListScreen,
    },
    {
      name: 'Alerts',
      label: 'Alerts',
      icon: 'notifications-outline',
      component: AlertsScreen,
    },
    { name: 'More', label: 'More', icon: 'ellipsis-horizontal', component: MoreScreen },
  ],
  DRIVER: [
    { name: 'Today', label: 'Today', icon: 'home-outline', component: TodayScreen },
    {
      name: 'Deliveries',
      label: 'Deliveries',
      icon: 'car-outline',
      component: DeliveryListScreen,
    },
    {
      name: 'Vehicle',
      label: 'Vehicle',
      icon: 'construct-outline',
      component: () => <PlaceholderScreen title="My Vehicle" icon="construct-outline" />,
    },
    {
      name: 'Alerts',
      label: 'Alerts',
      icon: 'notifications-outline',
      component: AlertsScreen,
    },
    { name: 'More', label: 'More', icon: 'ellipsis-horizontal', component: MoreScreen },
  ],
  FUEL_MANAGER: [
    { name: 'Dashboard', label: 'Dashboard', icon: 'grid-outline', component: TodayScreen },
    {
      name: 'Tanks',
      label: 'Tanks',
      icon: 'water-outline',
      component: () => <PlaceholderScreen title="Tanks" icon="water-outline" />,
    },
    {
      name: 'Operations',
      label: 'Operations',
      icon: 'business-outline',
      component: () => <PlaceholderScreen title="Operations" icon="business-outline" />,
    },
    {
      name: 'Alerts',
      label: 'Alerts',
      icon: 'notifications-outline',
      component: AlertsScreen,
    },
    { name: 'More', label: 'More', icon: 'ellipsis-horizontal', component: MoreScreen },
  ],
};
