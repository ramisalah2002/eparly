import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  RefreshControl,
  ScrollView,
  StyleSheet,
  View,
} from 'react-native';
import { useRouter } from 'expo-router';
import { LinearGradient } from 'expo-linear-gradient';
import Animated, { FadeIn, FadeInDown } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import {
  CategoryBreakdown,
  DashboardHeader,
  DashboardSkeleton,
  FinancialOverviewCard,
  PastelBackground,
  QuickActionsSection,
  RecentTransactions,
  SpendingChart,
} from '@/components/dashboard';
import { ImportCsvModal } from '@/components/transactions/import-csv-modal';
import { BRAND_COLORS } from '@/constants/colors';
import { MaxContentWidth } from '@/constants/theme';
import { useAuth } from '@/context/auth-context';
import { DASHBOARD_DEMO_DATA } from '@/features/dashboard/demo/dashboard-demo-data';
import { getAvatarInitials, getUserFirstName } from '@/utils/formatters';

export default function HomeScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { user } = useAuth();
  const [isBalanceHidden, setIsBalanceHidden] = useState<boolean>(false);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isImportModalVisible, setIsImportModalVisible] = useState<boolean>(false);
  const [headerHeight, setHeaderHeight] = useState<number>(0);
  const [spendingLayoutY, setSpendingLayoutY] = useState<number>(0);
  const scrollViewRef = useRef<React.ElementRef<typeof ScrollView>>(null);

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

  // Compute dynamic header clearance so the first section is never obstructed
  const defaultHeaderHeight = insets.top + 84;
  const effectiveHeaderHeight = headerHeight > 0 ? headerHeight : defaultHeaderHeight;
  const contentPaddingTop = effectiveHeaderHeight + 14;
  const contentPaddingBottom = Math.max(insets.bottom, 20) + 96;

  const handleExploreSpending = () => {
    if (spendingLayoutY > 0 && scrollViewRef.current) {
      scrollViewRef.current.scrollTo({
        y: Math.max(spendingLayoutY - effectiveHeaderHeight - 10, 0),
        animated: true,
      });
    } else {
      router.push('/(tabs)/transactions');
    }
  };

  if (isLoading) {
    return <DashboardSkeleton />;
  }

  return (
    <Animated.View style={styles.screenContainer} entering={FadeIn.duration(350)}>
      {/* Full-screen pastel gradient background (positioned behind so it does not affect flex flow) */}
      <PastelBackground style={StyleSheet.absoluteFill} pointerEvents="none" />

      {/* FIXED STICKY HEADER — Sits at the very top of the screen */}
      <View
        onLayout={(e) => {
          const height = e.nativeEvent.layout.height;
          if (height > 0 && Math.abs(height - headerHeight) > 1) {
            setHeaderHeight(height);
          }
        }}
        style={[styles.fixedHeaderContainer, { paddingTop: insets.top }]}>
        <LinearGradient
          colors={['#FAF9F6', '#FAF7F4', 'rgba(250, 249, 246, 0.96)']}
          start={{ x: 0, y: 0 }}
          end={{ x: 1, y: 1 }}
          style={StyleSheet.absoluteFill}
          pointerEvents="none"
        />
        <View style={styles.headerPastelSurface}>
          <DashboardHeader
            userName={userName}
            userInitials={userInitials}
          />
        </View>
      </View>

      {/* INDEPENDENTLY SCROLLING DASHBOARD CONTENT */}
      <ScrollView
        ref={scrollViewRef}
        style={styles.scrollView}
        contentContainerStyle={[
          styles.contentContainer,
          {
            paddingTop: contentPaddingTop,
            paddingBottom: contentPaddingBottom,
          },
        ]}
        showsVerticalScrollIndicator={false}
        bounces={true}
        refreshControl={
          <RefreshControl
            refreshing={isRefreshing}
            onRefresh={handleRefresh}
            progressViewOffset={effectiveHeaderHeight}
            tintColor={BRAND_COLORS.deepLavender}
            colors={[BRAND_COLORS.deepLavender]}
            progressBackgroundColor={BRAND_COLORS.pureWhite}
          />
        }>
        <View style={styles.innerContent}>
          {/* SECTION 1: Main Balance Card with embedded Income & Expenses */}
          <Animated.View entering={FadeInDown.duration(380).delay(50)}>
            <FinancialOverviewCard
              balance={demoData.balance}
              cashFlow={demoData.cashFlow}
              isBalanceHidden={isBalanceHidden}
              onToggleBalanceVisibility={toggleBalanceVisibility}
            />
          </Animated.View>

          {/* SECTION 2: Signature 2x2 Quick Actions Grid */}
          <QuickActionsSection
            onImportTransactions={() => setIsImportModalVisible(true)}
            onManageSubscriptions={() => router.push('/(tabs)/subscriptions')}
            onExploreSpending={handleExploreSpending}
            onFindWaysToSave={() => router.push('/(tabs)/savings')}
          />

          {/* SECTION 3: Spending Overview Chart */}
          <Animated.View
            entering={FadeInDown.duration(420).delay(100)}
            onLayout={(e) => setSpendingLayoutY(e.nativeEvent.layout.y)}>
            <SpendingChart
              months={demoData.spendingOverview.months}
              periodTitle={demoData.spendingOverview.periodTitle}
              isBalanceHidden={isBalanceHidden}
            />
          </Animated.View>

          {/* SECTION 4: Category Breakdown */}
          <Animated.View entering={FadeInDown.duration(460).delay(150)}>
            <CategoryBreakdown
              title={demoData.categoryBreakdown.title}
              subtitle={demoData.categoryBreakdown.subtitle}
              totalExpensesMinor={demoData.categoryBreakdown.totalExpensesMinor}
              categories={demoData.categoryBreakdown.categories}
              isBalanceHidden={isBalanceHidden}
            />
          </Animated.View>

          {/* SECTION 5: Recent Transactions Activity Feed */}
          <Animated.View entering={FadeInDown.duration(500).delay(200)}>
            <RecentTransactions
              title={demoData.recentTransactions.title}
              transactions={demoData.recentTransactions.transactions}
              isBalanceHidden={isBalanceHidden}
            />
          </Animated.View>
        </View>
      </ScrollView>

      {/* Real Bank Import CSV Flow Modal */}
      <ImportCsvModal
        visible={isImportModalVisible}
        onClose={() => setIsImportModalVisible(false)}
        onImportSuccess={() => {
          setIsImportModalVisible(false);
        }}
      />
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screenContainer: {
    flex: 1,
    backgroundColor: BRAND_COLORS.warmOffWhite,
  },
  fixedHeaderContainer: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    zIndex: 99,
    overflow: 'hidden',
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(36, 37, 53, 0.05)',
    backgroundColor: '#FAF9F6',
    shadowColor: BRAND_COLORS.darkCharcoal,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.02,
    shadowRadius: 6,
    elevation: 1,
  },
  headerPastelSurface: {
    paddingHorizontal: 16,
    paddingTop: 8,
    paddingBottom: 10,
  },
  scrollView: {
    flex: 1,
  },
  contentContainer: {
    alignItems: 'center',
    paddingHorizontal: 16,
  },
  innerContent: {
    width: '100%',
    maxWidth: MaxContentWidth,
  },
});
