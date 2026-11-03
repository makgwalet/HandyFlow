import React, { useMemo, useRef, useState } from 'react';
import { View, PanResponder, StyleSheet } from 'react-native';
import { colors, radii } from '../theme/tokens';
import type { SignatureStroke } from '../types/delivery';

interface SignaturePadProps {
  strokes: SignatureStroke[];
  onChange: (strokes: SignatureStroke[]) => void;
  height?: number;
}

/**
 * Signature is stored as vector points (strokes: {x,y}[][]), not a
 * rendered image — there's no react-native-view-shot dependency to
 * export a PNG, and no evidence-upload endpoint to send one to anyway
 * (see BACKEND_GAPS.md). Storing/replaying strokes is a reasonable
 * interim; production likely wants a PNG snapshot uploaded the same way
 * as the gauge/evidence photos once that pipeline exists.
 */
export function SignaturePad({ strokes, onChange, height = 180 }: SignaturePadProps) {
  const currentStroke = useRef<SignatureStroke>([]);
  const [, forceRender] = useState(0);

  const panResponder = useMemo(
    () =>
      PanResponder.create({
        onStartShouldSetPanResponder: () => true,
        onMoveShouldSetPanResponder: () => true,
        onPanResponderGrant: (e) => {
          currentStroke.current = [
            { x: e.nativeEvent.locationX, y: e.nativeEvent.locationY },
          ];
          forceRender((n) => n + 1);
        },
        onPanResponderMove: (e) => {
          currentStroke.current = [
            ...currentStroke.current,
            { x: e.nativeEvent.locationX, y: e.nativeEvent.locationY },
          ];
          forceRender((n) => n + 1);
        },
        onPanResponderRelease: () => {
          if (currentStroke.current.length > 1) {
            onChange([...strokes, currentStroke.current]);
          }
          currentStroke.current = [];
          forceRender((n) => n + 1);
        },
      }),
    [strokes, onChange]
  );

  const allStrokes = currentStroke.current.length > 1 ? [...strokes, currentStroke.current] : strokes;

  return (
    <View
      style={[styles.pad, { height }]}
      {...panResponder.panHandlers}
    >
      {allStrokes.map((stroke, sIdx) =>
        stroke.slice(1).map((point, i) => {
          const prev = stroke[i];
          const dx = point.x - prev.x;
          const dy = point.y - prev.y;
          const length = Math.sqrt(dx * dx + dy * dy);
          const angle = (Math.atan2(dy, dx) * 180) / Math.PI;
          return (
            <View
              key={`${sIdx}-${i}`}
              style={{
                position: 'absolute',
                left: prev.x,
                top: prev.y - 1,
                width: length,
                height: 2.5,
                backgroundColor: colors.textPrimary,
                borderRadius: 2,
                transform: [{ rotate: `${angle}deg` }],
                transformOrigin: '0 50%' as any,
              }}
            />
          );
        })
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  pad: {
    width: '100%',
    borderRadius: radii.lg,
    borderWidth: 1.5,
    borderColor: colors.border,
    backgroundColor: colors.surface,
    overflow: 'hidden',
  },
});
