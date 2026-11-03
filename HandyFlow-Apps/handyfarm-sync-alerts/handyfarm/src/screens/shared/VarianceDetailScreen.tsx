import React from 'react';
import { View, Text, Pressable, ScrollView, Alert, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Card } from '../../components/Card';
import { colors, radii, spacing, typography } from '../../theme/tokens';
import { useVarianceStore, type VarianceStatus } from '../../store/varianceStore';
import { useAuthStore } from '../../store/authStore';
import { MOCK_TANKS } from '../../data/mockData';

const STATUS_CONFIG: Record<VarianceStatus, { label: string; color: string }> = {
  OPEN: { label: 'Open', color: colors.danger },
  ACKNOWLEDGED: { label: 'Acknowledged', color: colors.warning },
  INVESTIGATING: { label: 'Investigating', color: colors.info },
  RESOLVED: { label: 'Resolved', color: colors.success },
};

export function VarianceDetailScreen({ route, navigation }: any) {
  const { varianceId } = route.params ?? {};
  const record = useVarianceStore((s) => s.records.find((r) => r.id === varianceId));
  const user = useAuthStore((s) => s.user);

  if (!record) return null;

  const tank = MOCK_TANKS.find((t) => t.id === record.tankId);
  const status = STATUS_CONFIG[record.status];
  const comingSoon = () =>
    Alert.alert(
      'Coming in a later phase',
      'The backend has no acknowledgment/investigation workflow yet — see BACKEND_GAPS.md.'
    );

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <Pressable onPress={() => navigation.getParent()?.goBack()} hitSlop={12}>
        <Text style={styles.back}>← Close</Text>
      </Pressable>

      <Text style={styles.title}>
        {Math.abs(record.varianceLitres).toLocaleString()} L Variance
      </Text>
      <Text style={styles.subtitle}>
        {tank?.name ?? record.tankId} · {new Date(record.createdAt).toLocaleDateString()}
      </Text>

      <View style={styles.statusRow}>
        <View style={[styles.statusDot, { backgroundColor: status.color }]} />
        <Text style={[styles.statusLabel, { color: status.color }]}>{status.label}</Text>
      </View>

      <Card style={styles.card}>
        <Text style={styles.cardLabel}>Evidence</Text>
        <View style={styles.evidenceRow}>
          <Ionicons name="camera-outline" size={16} color={colors.textSecondary} />
          <Text style={styles.evidenceText}>
            {record.photoUri ? 'Gauge photo' : 'No photo'}
          </Text>
        </View>
        <View style={styles.evidenceRow}>
          <Ionicons name="location-outline" size={16} color={colors.textSecondary} />
          <Text style={styles.evidenceText}>GPS</Text>
        </View>
        <View style={styles.evidenceRow}>
          <Ionicons name="person-outline" size={16} color={colors.textSecondary} />
          <Text style={styles.evidenceText}>{user?.name ?? 'Operator'}</Text>
        </View>
      </Card>

      <Card style={styles.card}>
        <Text style={styles.cardLabel}>Reason</Text>
        <Text style={styles.reasonText}>{record.reason || '—'}</Text>
      </Card>

      <Card style={styles.card}>
        <Text style={styles.cardLabel}>Timeline</Text>
        {record.timeline.map((event, i) => (
          <View key={i} style={styles.timelineRow}>
            <View style={styles.timelineDot} />
            <Text style={styles.timelineLabel}>{event.label}</Text>
            <Text style={styles.timelineTime}>
              {new Date(event.at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
            </Text>
          </View>
        ))}
      </Card>

      <View style={styles.actionsRow}>
        <Pressable onPress={comingSoon} style={styles.actionButton}>
          <Text style={styles.actionButtonText}>Add Note</Text>
        </Pressable>
        <Pressable onPress={comingSoon} style={styles.actionButton}>
          <Text style={styles.actionButtonText}>Add Evidence</Text>
        </Pressable>
      </View>
      <Pressable onPress={comingSoon} style={styles.resolveButton}>
        <Text style={styles.resolveButtonText}>Resolve</Text>
      </Pressable>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  content: { padding: spacing.lg, paddingBottom: spacing.xxxl },
  back: { ...typography.bodyStrong, color: colors.primaryDark, marginBottom: spacing.lg },
  title: { ...typography.h2 },
  subtitle: { ...typography.caption, marginBottom: spacing.md },
  statusRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.xs, marginBottom: spacing.lg },
  statusDot: { width: 10, height: 10, borderRadius: radii.pill },
  statusLabel: { fontWeight: '700', fontSize: 14 },
  card: { marginBottom: spacing.md },
  cardLabel: { ...typography.label, textTransform: 'uppercase', marginBottom: spacing.sm },
  evidenceRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm, paddingVertical: 4 },
  evidenceText: { ...typography.body },
  reasonText: { ...typography.body },
  timelineRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm, paddingVertical: 4 },
  timelineDot: { width: 6, height: 6, borderRadius: radii.pill, backgroundColor: colors.primary },
  timelineLabel: { ...typography.body, flex: 1 },
  timelineTime: { ...typography.caption },
  actionsRow: { flexDirection: 'row', gap: spacing.sm, marginTop: spacing.sm, marginBottom: spacing.sm },
  actionButton: {
    flex: 1,
    borderRadius: radii.md,
    borderWidth: 1,
    borderColor: colors.border,
    paddingVertical: spacing.md,
    alignItems: 'center',
    backgroundColor: colors.surface,
  },
  actionButtonText: { ...typography.bodyStrong, color: colors.textSecondary },
  resolveButton: {
    borderRadius: radii.md,
    backgroundColor: colors.primaryDark,
    paddingVertical: spacing.md,
    alignItems: 'center',
  },
  resolveButtonText: { color: colors.textOnDark, fontWeight: '700', fontSize: 16 },
});
