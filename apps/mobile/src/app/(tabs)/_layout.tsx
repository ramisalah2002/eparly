import { Redirect } from 'expo-router';
import React from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';

import AppTabs from '@/components/app-tabs';
import { BRAND_COLORS } from '@/constants/colors';
import { useAuth } from '@/context/auth-context';

export default function TabLayout() {
  const { isAuthenticated, isLoading } = useAuth();

  // While restoring or validating session, do not render protected content
  if (isLoading) {
    return (
      <View style={styles.loadingContainer} testID="tabs-loading-guard">
        <ActivityIndicator size="large" color={BRAND_COLORS.mutedLavender} />
      </View>
    );
  }

  // Once confirmed unauthenticated, redirect to Sign In
  if (!isAuthenticated) {
    return <Redirect href="/(auth)/sign-in" />;
  }

  // Authenticated: allow access to protected tab content
  return <AppTabs />;
}

const styles = StyleSheet.create({
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: BRAND_COLORS.warmOffWhite,
  },
});
