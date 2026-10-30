import React from 'react';
import {
  ArrowLeftRight,
  Car,
  CreditCard,
  Gamepad2,
  HeartPulse,
  Home,
  Receipt,
  Shapes,
  ShoppingBag,
  Sparkles,
  TrendingUp,
  Tv,
  Utensils,
} from 'lucide-react-native';

import { BRAND_COLORS } from './colors';

export interface CategoryTheme {
  icon: React.ComponentType<{ size?: number; color?: string; strokeWidth?: number }>;
  color: string;
  backgroundColor: string;
}

const CATEGORY_THEMES: Record<string, CategoryTheme> = {
  restaurants: {
    icon: Utensils,
    color: '#E65100',
    backgroundColor: '#FFE0B2',
  },
  courses: {
    icon: ShoppingBag,
    color: '#00796B',
    backgroundColor: '#B2DFDB',
  },
  transport: {
    icon: Car,
    color: '#1565C0',
    backgroundColor: '#BBDEFB',
  },
  logement: {
    icon: Home,
    color: '#6A1B9A',
    backgroundColor: '#E1BEE7',
  },
  abonnements: {
    icon: Tv,
    color: '#C2185B',
    backgroundColor: '#F8BBD0',
  },
  shopping: {
    icon: Sparkles,
    color: '#D81B60',
    backgroundColor: '#FCE4EC',
  },
  santé: {
    icon: HeartPulse,
    color: '#00897B',
    backgroundColor: '#CCECE6',
  },
  sante: {
    icon: HeartPulse,
    color: '#00897B',
    backgroundColor: '#CCECE6',
  },
  loisirs: {
    icon: Gamepad2,
    color: '#D97706',
    backgroundColor: '#FEF3C7',
  },
  'frais bancaires': {
    icon: Receipt,
    color: '#4B5563',
    backgroundColor: '#E5E7EB',
  },
  revenus: {
    icon: TrendingUp,
    color: '#15803D',
    backgroundColor: '#DCFCE7',
  },
  virements: {
    icon: ArrowLeftRight,
    color: '#4F46E5',
    backgroundColor: '#E0E7FF',
  },
  'autres dépenses': {
    icon: Shapes,
    color: BRAND_COLORS.deepLavender,
    backgroundColor: BRAND_COLORS.veryLightLavender,
  },
  'autres depenses': {
    icon: Shapes,
    color: BRAND_COLORS.deepLavender,
    backgroundColor: BRAND_COLORS.veryLightLavender,
  },
};

/**
 * Returns icon, accent color, and background tint for a given category name or transaction direction.
 */
export function getCategoryTheme(
  categoryName?: string | null,
  isPositive?: boolean
): CategoryTheme {
  if (categoryName) {
    const key = categoryName.trim().toLowerCase();
    if (CATEGORY_THEMES[key]) {
      return CATEGORY_THEMES[key];
    }
  }

  // Fallbacks based on direction
  if (isPositive) {
    return {
      icon: TrendingUp,
      color: '#15803D',
      backgroundColor: '#DCFCE7',
    };
  }

  return {
    icon: CreditCard,
    color: BRAND_COLORS.deepLavender,
    backgroundColor: BRAND_COLORS.veryLightLavender,
  };
}
