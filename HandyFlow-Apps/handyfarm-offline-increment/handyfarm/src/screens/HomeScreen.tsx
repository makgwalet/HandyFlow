import React from 'react';
import { ScrollView, View, Text, StyleSheet, Alert } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useFocusEffect, useNavigation } from '@react-navigation/native';
import { BottomTabNavigationProp } from '@react-navigation/bottom-tabs';
import { useTheme } from '../design-system/ThemeProvider';
import { placeholderHomeSummary, FarmStat, QuickCaptureAction } from '../data/placeholderHomeData';
import { AttentionRow } from '../components/AttentionRow';
import { QuickCaptureGrid } from '../components/QuickCaptureGrid';
import { SyncStatusBar } from '../components/SyncStatusBar';
import { RootTabParamList } from '../navigation/RootNavigator';
import { useOfflineSync } from '../offline/useOfflineSync';

// Actions with a real backend + screens today (see docs/agriculture/10-API-MAP.md).
// Everything else in the grid explains the gap instead of pretending to work.
const REAL_ACTIONS = new Set(['weight', 'treatment', 'mortality', 'harvest']);
const NOT_YET_AVAILABLE_REASON: Record<string, string> = {
  eggs: "Poultry isn't built on the backend yet — see API GAP 1 in the project docs.",
  photo: "Evidence photo capture isn't wired up in the mobile app yet.",
};

function SectionHeader({ children }: { children: string }) {
  const { colors, typography, spacing } = useTheme();
  return (
    <Text style={[typography.label, { color: colors.textSecondary, marginBottom: spacing.sm, marginTop: spacing.xl }]}>
      {children.toUpperCase()}
    </Text>
  );
}

function StatChip({ stat }: { stat: FarmStat }) {
  const { colors, typography, spacing, radius } = useTheme();
  return (
    <View
      style={[
        styles.statChip,
        { backgroundColor: colors.surface, borderColor: colors.border, borderRadius: radius.md, padding: spacing.md },
      ]}
    >
      <Text style={{ fontSize: 20 }}>{stat.icon}</Text>
      <Text style={[typography.h3, { color: colors.textPrimary, marginTop: 4 }]}>{stat.value}</Text>
      <Text style={[typography.caption, { color: colors.textSecondary }]}>{stat.label}</Text>
    </View>
  );
}

export function HomeScreen() {
  const { colors, spacing, typography } = useTheme();
  const summary = placeholderHomeSummary; // TODO: replace with useHomeSummary() once API map exists
  const navigation = useNavigation<BottomTabNavigationProp<RootTabParamList>>();
  const { pendingCount, isSyncing, syncNow, refresh } = useOfflineSync();

  // The queue changes from screens deep in the Production stack (recording
  // a weight while offline), so refresh whenever Home regains focus rather
  // than only once on mount.
  useFocusEffect(
    React.useCallback(() => {
      refresh();
    }, [refresh])
  );

  const handleSyncPress = async () => {
    if (pendingCount === 0 || pendingCount === null) return;
    const result = await syncNow();
    if (result.synced > 0 && result.stillFailed === 0) {
      Alert.alert('Synced', `${result.synced} record${result.synced === 1 ? '' : 's'} synced successfully.`);
    } else if (result.stillFailed > 0) {
      Alert.alert(
        'Still offline',
        `${result.synced} synced, ${result.stillFailed} still waiting. Will try again next time you're online.`
      );
    }
  };

  const handleQuickCapture = (action: QuickCaptureAction) => {
    if (REAL_ACTIONS.has(action.id)) {
      // No scan/search entry point yet, so route through farm -> hub
      // selection first, same as tapping the Production tab would.
      navigation.navigate('Production', { screen: 'FarmList' });
      return;
    }
    Alert.alert(
      'Not available yet',
      NOT_YET_AVAILABLE_REASON[action.id] ?? "This quick capture isn't wired up yet."
    );
  };

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.background }} edges={['top']}>
      <ScrollView contentContainerStyle={{ padding: spacing.lg, paddingBottom: spacing.xxxl }}>
        <View style={styles.headerRow}>
          <View>
            <Text style={[typography.h2, { color: colors.textSecondary }]}>{summary.greeting} 👋</Text>
            <Text style={[typography.display, { color: colors.textPrimary }]}>{summary.farmName}</Text>
          </View>
          <SyncStatusBar pendingCount={pendingCount} isSyncing={isSyncing} onPress={handleSyncPress} />
        </View>

        <SectionHeader>Today</SectionHeader>
        <View style={styles.statsRow}>
          {summary.stats.map((stat) => (
            <StatChip key={stat.id} stat={stat} />
          ))}
        </View>

        {summary.attention.length > 0 && (
          <>
            <SectionHeader>{`${summary.attention.length} things need attention`}</SectionHeader>
            {summary.attention.map((item) => (
              <AttentionRow key={item.id} item={item} />
            ))}
          </>
        )}

        <SectionHeader>What do you need to record?</SectionHeader>
        <QuickCaptureGrid actions={summary.quickCapture} onSelect={handleQuickCapture} />
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  headerRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' },
  statsRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 12 },
  statChip: { width: '47%', borderWidth: StyleSheet.hairlineWidth },
});
