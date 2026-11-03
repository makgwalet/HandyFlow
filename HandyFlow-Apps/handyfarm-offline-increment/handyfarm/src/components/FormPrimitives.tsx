import React from 'react';
import { Text, TextInput, View, StyleSheet, Pressable, ActivityIndicator } from 'react-native';
import { Control, Controller, FieldValues, Path } from 'react-hook-form';
import { useTheme } from '../design-system/ThemeProvider';

interface FormFieldProps<T extends FieldValues> {
  control: Control<T>;
  name: Path<T>;
  label: string;
  placeholder?: string;
  keyboardType?: 'default' | 'numeric' | 'decimal-pad';
  multiline?: boolean;
}

export function FormTextField<T extends FieldValues>({
  control,
  name,
  label,
  placeholder,
  keyboardType = 'default',
  multiline = false,
}: FormFieldProps<T>) {
  const { colors, spacing, typography, radius } = useTheme();
  return (
    <Controller
      control={control}
      name={name}
      render={({ field: { onChange, onBlur, value }, fieldState: { error } }) => (
        <View style={{ marginBottom: spacing.lg }}>
          <Text style={[typography.label, { color: colors.textSecondary, marginBottom: spacing.xs }]}>
            {label.toUpperCase()}
          </Text>
          <TextInput
            value={value !== undefined && value !== null ? String(value) : ''}
            onChangeText={onChange}
            onBlur={onBlur}
            placeholder={placeholder}
            placeholderTextColor={colors.textSecondary}
            keyboardType={keyboardType}
            multiline={multiline}
            style={[
              typography.body,
              {
                color: colors.textPrimary,
                backgroundColor: colors.surface,
                borderColor: error ? colors.danger : colors.border,
                borderWidth: StyleSheet.hairlineWidth,
                borderRadius: radius.md,
                padding: spacing.md,
                minHeight: multiline ? 88 : 48,
                textAlignVertical: multiline ? 'top' : 'center',
              },
            ]}
          />
          {error && (
            <Text style={[typography.caption, { color: colors.danger, marginTop: spacing.xs }]}>{error.message}</Text>
          )}
        </View>
      )}
    />
  );
}

export function PrimaryButton({
  label,
  onPress,
  loading,
  disabled,
}: {
  label: string;
  onPress: () => void;
  loading?: boolean;
  disabled?: boolean;
}) {
  const { colors, spacing, radius, typography } = useTheme();
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled || loading}
      accessibilityRole="button"
      style={({ pressed }) => [
        {
          backgroundColor: colors.accent,
          borderRadius: radius.md,
          paddingVertical: spacing.md,
          alignItems: 'center',
          opacity: disabled || loading ? 0.6 : pressed ? 0.85 : 1,
        },
      ]}
    >
      {loading ? (
        <ActivityIndicator color={colors.textInverse} />
      ) : (
        <Text style={[typography.bodyStrong, { color: colors.textInverse }]}>{label}</Text>
      )}
    </Pressable>
  );
}

export function ErrorBanner({ message }: { message: string }) {
  const { colors, spacing, radius, typography } = useTheme();
  return (
    <View
      style={{
        backgroundColor: colors.danger + '22',
        borderColor: colors.danger,
        borderWidth: StyleSheet.hairlineWidth,
        borderRadius: radius.md,
        padding: spacing.md,
        marginBottom: spacing.lg,
      }}
    >
      <Text style={[typography.body, { color: colors.danger }]}>{message}</Text>
    </View>
  );
}

export function LoadingView() {
  const { colors } = useTheme();
  return (
    <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.background }}>
      <ActivityIndicator color={colors.accent} />
    </View>
  );
}
