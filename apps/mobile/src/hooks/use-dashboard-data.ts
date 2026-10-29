import { useCallback, useEffect, useState } from 'react';

import {
  DashboardData,
  fetchFullDashboardData,
} from '@/services/dashboard-api';

export interface UseDashboardDataResult {
  data: DashboardData | null;
  isLoading: boolean;
  isRefreshing: boolean;
  error: string | null;
  isBalanceHidden: boolean;
  toggleBalanceVisibility: () => void;
  refresh: () => Promise<void>;
  retry: () => Promise<void>;
}

export function useDashboardData(): UseDashboardDataResult {
  const [data, setData] = useState<DashboardData | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [isBalanceHidden, setIsBalanceHidden] = useState<boolean>(false);

  const loadData = useCallback(async (isPullRefresh = false) => {
    if (isPullRefresh) {
      setIsRefreshing(true);
    } else {
      setIsLoading(true);
    }
    setError(null);

    const res = await fetchFullDashboardData();

    if (res.success && res.data) {
      setData(res.data);
      setError(null);
    } else {
      setError(res.error || 'Impossible de récupérer vos données financières');
    }

    if (isPullRefresh) {
      setIsRefreshing(false);
    } else {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadData(false);
  }, [loadData]);

  const refresh = useCallback(async () => {
    await loadData(true);
  }, [loadData]);

  const retry = useCallback(async () => {
    await loadData(false);
  }, [loadData]);

  const toggleBalanceVisibility = useCallback(() => {
    setIsBalanceHidden((prev) => !prev);
  }, []);

  return {
    data,
    isLoading,
    isRefreshing,
    error,
    isBalanceHidden,
    toggleBalanceVisibility,
    refresh,
    retry,
  };
}
