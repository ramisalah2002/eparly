import React, { useCallback, useEffect, useState } from 'react';
import {
  RefreshControl,
  ScrollView,
  StyleSheet,
  View,
} from 'react-native';
import Animated, { FadeIn, FadeInDown } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import {
  CategoryBreakdown,
  DashboardHeader,
  DashboardSkeleton,
  FinancialOverviewCard,
  PastelBackground,
  RecentTransactions,
  SpendingChart,
} from '@/components/dashboard';
import { BRAND_COLORS } from '@/constants/colors';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { useAuth } from '@/context/auth-context';
import { DASHBOARD_DEMO_DATA } from '@/features/dashboard/demo/dashboard-demo-data';
import { getAvatarInitials, getUserFirstName } from '@/utils/formatters';

export default function HomeScreen() {
  const insets = useSafeAreaInsets();
  const { user } = useAuth();
  const [isBalanceHidden, setIsBalanceHidden] = useState<boolean>(false);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  // Single static fixture for all financial demo values
  const demoData = DASHBOARD_DEMO_DATA;

  // Use authenticated user's name/initials if available, falling back to demo values
  const userName = getUserFirstName(user?.fullName) || demoData.user.name;
  const userInitials = getAvatarInitials(user?.fullName, user?.email) || demoData.user.initials;

  useEffect(() => {
    // Show animated skeleton loader on initial mount before revealing the dashboard
    const timer = setTimeout(() => {
      setIsLoading(false);
    }, 1200);

    return () => clearTimeout(timer);
  }, []);

  const toggleBalanceVisibility = () => {
    setIsBalanceHidden((prev) => !prev);
  };

  const handleRefresh = useCallback(() => {
    setIsRefreshing(true);
    // Smooth demo refresh cycle
    setTimeout(() => {
      setIsRefreshing(false);
    }, 1000);
  }, []);

  if (isLoading) {
    return <DashboardSkeleton />;
  }

  // Bottom padding ensures the last transaction card completely clears the tab bar
  const contentPaddingBottom = Math.max(insets.bottom, 20) + 96;

  return (
    <Animated.View style={styles.screenContainer} entering={FadeIn.duration(350)}>
      {/* Full-screen pastel gradient background (positioned behind so it does not affect flex flow) */}
      <PastelBackground style={StyleSheet.absoluteFill} pointerEvents="none" />

      {/* FIXED STICKY HEADER — Sits at the very top of the screen */}
      <View style={[styles.fixedHeaderContainer, { paddingTop: insets.top }]}>
        <View style={styles.headerPastelSurface}>
          <DashboardHeader
            userName={userName}
            userInitials={userInitials}
          />
        </View>
        <View style={styles.headerBottomBorder} />
      </View>

      {/* INDEPENDENTLY SCROLLING DASHBOARD CONTENT */}
      <ScrollView
        style={styles.scrollView}
        contentContainerStyle={[
          styles.contentContainer,
          {
            paddingTop: Spacing.three,
            paddingBottom: contentPaddingBottom,
          },
        ]}
        showsVerticalScrollIndicator={false}
        bounces={true}
        refreshControl={
          <RefreshControl
            refreshing={isRefreshing}
            onRefresh={handleRefresh}
            tintColor={BRAND_COLORS.deepLavender}
            colors={[BRAND_COLORS.deepLavender]}
            progressBackgroundColor={BRAND_COLORS.pureWhite}
          />
        }>
        <View style={styles.innerContent}>
          {/* SECTION B & C: Main Balance Card with embedded Income & Expenses */}
          <Animated.View entering={FadeInDown.duration(380).delay(50)}>
            <FinancialOverviewCard
              balance={demoData.balance}
              cashFlow={demoData.cashFlow}
              isBalanceHidden={isBalanceHidden}
              onToggleBalanceVisibility={toggleBalanceVisibility}
            />
          </Animated.View>

          {/* SECTION D: Spending Overview Chart */}
          <Animated.View entering={FadeInDown.duration(420).delay(100)}>
            <SpendingChart
              months={demoData.spendingOverview.months}
              periodTitle={demoData.spendingOverview.periodTitle}
              isBalanceHidden={isBalanceHidden}
            />
          </Animated.View>

          {/* SECTION E: Category Breakdown */}
          <Animated.View entering={FadeInDown.duration(460).delay(150)}>
            <CategoryBreakdown
              title={demoData.categoryBreakdown.title}
              subtitle={demoData.categoryBreakdown.subtitle}
              totalExpensesMinor={demoData.categoryBreakdown.totalExpensesMinor}
              categories={demoData.categoryBreakdown.categories}
              isBalanceHidden={isBalanceHidden}
            />
          </Animated.View>

          {/* SECTION F: Recent Transactions */}
          <Animated.View entering={FadeInDown.duration(500).delay(200)}>
            <RecentTransactions
              title={demoData.recentTransactions.title}
              transactions={demoData.recentTransactions.transactions}
              isBalanceHidden={isBalanceHidden}
            />
          </Animated.View>
        </View>
      </ScrollView>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screenContainer: {
    flex: 1,
    backgroundColor: BRAND_COLORS.warmOffWhite,
  },
  fixedHeaderContainer: {
    width: '100%',
    zIndex: 100,
    elevation: 4,
    backgroundColor: 'rgba(230, 247, 241, 0.95)',
  },
  headerPastelSurface: {
    width: '100%',
    alignItems: 'center',
  },
  headerBottomBorder: {
    height: 1,
    backgroundColor: 'rgba(168, 155, 190, 0.18)',
    width: '100%',
  },
  scrollView: {
    flex: 1,
  },
  contentContainer: {
    alignItems: 'center',
    paddingHorizontal: Spacing.three,
  },
  innerContent: {
    width: '100%',
    maxWidth: MaxContentWidth,
  },
});
