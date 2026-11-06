import React from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { BRAND_COLORS } from '@/constants/colors';
import { FONT_FAMILY } from '@/constants/typography';

export interface SettingsSectionProps {
  title?: string;
  caption?: string;
  children: React.ReactNode;
}

/**
 * Editorial Settings Section Container.
 * Groups related settings rows into an elevated Pure White card
 * with hairline dividers and Space Grotesk section titles.
 */
export function SettingsSection({
  title,
  caption,
  children,
}: SettingsSectionProps) {
  const childrenArray = React.Children.toArray(children).filter(Boolean);

  return (
    <View style={styles.sectionContainer}>
      {title && <Text style={styles.sectionTitle}>{title}</Text>}

      <View style={styles.card}>
        {childrenArray.map((child, index) => (
          <React.Fragment key={index}>
            {child}
            {index < childrenArray.length - 1 && <View style={styles.divider} />}
          </React.Fragment>
        ))}
      </View>

      {caption && <Text style={styles.sectionCaption}>{caption}</Text>}
    </View>
  );
}

const styles = StyleSheet.create({
  sectionContainer: {
    marginBottom: 18,
  },
  sectionTitle: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 11.5,
    fontWeight: '700',
    color: BRAND_COLORS.secondaryText,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: 8,
    marginLeft: 4,
  },
  card: {
    backgroundColor: BRAND_COLORS.pureWhite,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.20)',
    shadowColor: BRAND_COLORS.darkCharcoal,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.03,
    shadowRadius: 6,
    elevation: 2,
    overflow: 'hidden',
  },
  divider: {
    height: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.04)',
    marginLeft: 62, // Aligns cleanly with row text start (past icon)
  },
  sectionCaption: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 11.5,
    color: BRAND_COLORS.secondaryText,
    marginTop: 6,
    marginLeft: 4,
    lineHeight: 16,
  },
});

