import AsyncStorage from '@react-native-async-storage/async-storage';

const ONBOARDING_STORAGE_KEY = '@eparly:has_completed_onboarding';

/**
 * Checks if the user has completed or skipped the initial onboarding flow.
 */
export async function getHasCompletedOnboarding(): Promise<boolean> {
  try {
    const value = await AsyncStorage.getItem(ONBOARDING_STORAGE_KEY);
    return value === 'true';
  } catch (error) {
    console.warn('[OnboardingStorage] Failed to read onboarding state:', error);
    return false;
  }
}

/**
 * Marks the onboarding flow as completed.
 */
export async function setHasCompletedOnboarding(completed = true): Promise<void> {
  try {
    await AsyncStorage.setItem(ONBOARDING_STORAGE_KEY, completed ? 'true' : 'false');
  } catch (error) {
    console.warn('[OnboardingStorage] Failed to write onboarding state:', error);
  }
}

/**
 * Resets the onboarding flag for development and testing purposes.
 */
export async function resetOnboarding(): Promise<void> {
  try {
    await AsyncStorage.removeItem(ONBOARDING_STORAGE_KEY);
  } catch (error) {
    console.warn('[OnboardingStorage] Failed to reset onboarding state:', error);
  }
}
