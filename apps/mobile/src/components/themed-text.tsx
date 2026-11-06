import { Platform, StyleSheet, Text, type TextProps } from 'react-native';

import { Fonts, ThemeColor } from '@/constants/theme';
import { FONT_FAMILY } from '@/constants/typography';
import { useTheme } from '@/hooks/use-theme';

export type ThemedTextProps = TextProps & {
  type?: 'default' | 'title' | 'small' | 'smallBold' | 'subtitle' | 'link' | 'linkPrimary' | 'code' | 'heading' | 'caption';
  themeColor?: ThemeColor;
};

export function ThemedText({ style, type = 'default', themeColor, ...rest }: ThemedTextProps) {
  const theme = useTheme();

  return <Text style={[{ color: theme[themeColor ?? 'text'] }, styles[type], style]} {...rest} />;
}

const styles = StyleSheet.create({
  small: {
    fontFamily: FONT_FAMILY.regular,
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '400',
  },
  smallBold: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '600',
  },
  caption: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 11.5,
    lineHeight: 16,
    fontWeight: '500',
    letterSpacing: 0.1,
  },
  default: {
    fontFamily: FONT_FAMILY.medium,
    fontSize: 15,
    lineHeight: 22,
    fontWeight: '500',
  },
  heading: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 22,
    lineHeight: 28,
    fontWeight: '700',
    letterSpacing: -0.3,
  },
  title: {
    fontFamily: FONT_FAMILY.bold,
    fontSize: 34,
    fontWeight: '700',
    lineHeight: 40,
    letterSpacing: -0.8,
  },
  subtitle: {
    fontFamily: FONT_FAMILY.semiBold,
    fontSize: 24,
    lineHeight: 30,
    fontWeight: '600',
    letterSpacing: -0.4,
  },
  link: {
    fontFamily: FONT_FAMILY.medium,
    lineHeight: 22,
    fontSize: 14,
  },
  linkPrimary: {
    fontFamily: FONT_FAMILY.semiBold,
    lineHeight: 22,
    fontSize: 14,
    color: '#8067A1',
  },
  code: {
    fontFamily: Fonts.mono,
    fontWeight: Platform.select({ android: '700' }) ?? '500',
    fontSize: 12,
  },
});
