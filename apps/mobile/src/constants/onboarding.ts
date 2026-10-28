export const ONBOARDING_COLORS = {
  // Primary brand accents
  mutedLavender: '#A89BBE',
  mutedLavenderPressed: '#9587AB',
  warmSandBeige: '#D8C3A8',

  // Supporting background tones
  softMintBackground: '#E4F6F0',
  warmOffWhite: '#FAF9F6',
  offWhiteGradientEnd: '#EEF7F3',

  // Typography
  darkCharcoal: '#242535',
  secondaryText: '#737382',
  pureWhite: '#FFFFFF',

  // Pagination
  paginationInactive: 'rgba(168, 155, 190, 0.32)',
} as const;

export interface OnboardingSlideItem {
  id: string;
  headline: string;
  description: string;
  image: any;
  buttonLabel: string;
}

export const ONBOARDING_SLIDES: OnboardingSlideItem[] = [
  {
    id: 'expense-tracking',
    headline: 'Know where your money goes.',
    description:
      'Track your spending, understand your habits, and stay in control of every expense.',
    image: require('@/assets/images/onboarding/onboarding_img1.png'),
    buttonLabel: 'Continue',
  },
  {
    id: 'subscription-management',
    headline: 'Your subscriptions, under control.',
    description:
      'Keep track of recurring payments, see upcoming renewals, and take control of your subscriptions.',
    image: require('@/assets/images/onboarding/onboarding_img2.png'),
    buttonLabel: 'Continue',
  },
  {
    id: 'savings-optimization',
    headline: 'Spend smarter. Save more.',
    description:
      'Discover opportunities to reduce unnecessary spending and make progress toward your savings goals.',
    image: require('@/assets/images/onboarding/onboarding_img3.png'),
    buttonLabel: 'Get Started',
  },
];
