import { useRouter } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { Image } from 'expo-image';
import React, { useCallback, useState } from 'react';
import {
  ActivityIndicator,
  Keyboard,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  TouchableWithoutFeedback,
  useWindowDimensions,
  View,
} from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { MailIcon } from '@/components/auth/auth-icons';
import { PastelBackground } from '@/components/onboarding/pastel-background';
import { BRAND_COLORS } from '@/constants/colors';

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

export default function ForgotPasswordScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { height: screenHeight } = useWindowDimensions();

  const [email, setEmail] = useState('');
  const [emailFocused, setEmailFocused] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitted, setSubmitted] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  // Press animation feedback
  const buttonScale = useSharedValue(1);

  const animatedButtonStyle = useAnimatedStyle(() => ({
    transform: [{ scale: buttonScale.value }],
  }));

  const handlePressIn = useCallback(() => {
    // eslint-disable-next-line react-hooks/immutability
    buttonScale.value = withTiming(0.982, { duration: 110 });
  }, [buttonScale]);

  const handlePressOut = useCallback(() => {
    // eslint-disable-next-line react-hooks/immutability
    buttonScale.value = withTiming(1, { duration: 150 });
  }, [buttonScale]);

  const handleSubmit = async () => {
    setError(null);

    const trimmed = email.trim();
    if (!trimmed) {
      setError('Please enter your email address.');
      return;
    }
    if (!EMAIL_REGEX.test(trimmed)) {
      setError('Please enter a valid email address.');
      return;
    }

    setIsLoading(true);
    try {
      const baseUrl =
        process.env.EXPO_PUBLIC_API_URL ||
        (Platform.OS === 'android' ? 'http://10.0.2.2:8080' : 'http://localhost:8080');
      await fetch(`${baseUrl}/v1/auth/forgot-password`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: trimmed }),
      }).catch(() => {});
    } catch {
      // Ignore network errors on neutral endpoint
    } finally {
      setIsLoading(false);
      setSubmitted(true);
    }
  };

  const illustrationHeight = Math.min(Math.max(screenHeight * 0.175, 116), 148);

  return (
    <View style={styles.container}>
      <StatusBar style="dark" />
      <PastelBackground />

      <TouchableWithoutFeedback onPress={Keyboard.dismiss} accessible={false}>
        <View
            style={[
              styles.content,
              {
                paddingTop: insets.top + 4,
                paddingBottom: insets.bottom + 12,
              },
            ]}>
            {/* Top Navigation Bar with Back Button */}
            <View style={styles.topBar}>
              <Pressable
                onPress={() => router.back()}
                hitSlop={{ top: 12, bottom: 12, left: 16, right: 16 }}
                style={({ pressed }) => [styles.backBtn, pressed && styles.backBtnPressed]}
                accessibilityRole="button"
                accessibilityLabel="Go back">
                <Text style={styles.backBtnText}>← Back</Text>
              </Pressable>
            </View>

            {/* Centered Main Card Container */}
            <View style={styles.cardContainer}>
              {/* Illustration */}
              <View style={[styles.imageContainer, { height: illustrationHeight }]}>
                <Image
                  source={require('@/components/welcome/money_img.svg')}
                  style={styles.illustration}
                  contentFit="contain"
                  priority="high"
                />
              </View>

              {/* Heading & Subtitle */}
              <View style={styles.headingSection}>
                <Text style={styles.title}>Forget Password</Text>
                <Text style={styles.subtitle}>
                  {"Don't worry it happens. Please enter the address associate with your account"}
                </Text>
              </View>

              {/* Form or Confirmation Card */}
              <View style={styles.formSection}>
                {submitted ? (
                  <View style={styles.successCard}>
                    <Text style={styles.successTitle}>Check Your Inbox</Text>
                    <Text style={styles.successText}>
                      We have sent instructions to {email.trim()} to reset your password.
                    </Text>
                    <Pressable
                      onPress={() => router.replace('/(auth)/sign-in')}
                      style={styles.returnBtn}>
                      <Text style={styles.returnBtnText}>Return to Sign In</Text>
                    </Pressable>
                  </View>
                ) : (
                  <>
                    {/* Input: Email address with Envelope icon */}
                    <View style={styles.inputGroup}>
                      <View
                        style={[
                          styles.inputBox,
                          emailFocused && styles.inputBoxFocused,
                          Boolean(error) && styles.inputBoxError,
                        ]}>
                        <View style={styles.leftIconContainer}>
                          <MailIcon
                            color={emailFocused ? BRAND_COLORS.mutedLavender : '#9EA0AA'}
                            size={19}
                          />
                        </View>
                        <TextInput
                          value={email}
                          onChangeText={(val) => {
                            setEmail(val);
                            if (error) setError(null);
                          }}
                          onFocus={() => setEmailFocused(true)}
                          onBlur={() => setEmailFocused(false)}
                          placeholder="Email address"
                          placeholderTextColor="#9EA0AA"
                          keyboardType="email-address"
                          autoCapitalize="none"
                          autoComplete="email"
                          textContentType="emailAddress"
                          style={styles.inputField}
                          accessible={true}
                          accessibilityLabel="Email address"
                        />
                      </View>
                      {error && (
                        <Text style={styles.fieldErrorText}>{error}</Text>
                      )}
                    </View>

                    {/* Send OTP CTA Button */}
                    <AnimatedPressable
                      onPress={handleSubmit}
                      onPressIn={handlePressIn}
                      onPressOut={handlePressOut}
                      disabled={isLoading}
                      accessible={true}
                      accessibilityRole="button"
                      accessibilityLabel="Send OTP"
                      style={[styles.sendBtnWrapper, animatedButtonStyle]}>
                      <View style={[styles.sendBtn, isLoading && styles.sendBtnDisabled]}>
                        {isLoading ? (
                          <ActivityIndicator size="small" color={BRAND_COLORS.darkCharcoal} />
                        ) : (
                          <Text style={styles.sendBtnText}>Send OTP</Text>
                        )}
                      </View>
                    </AnimatedPressable>
                  </>
                )}

                {/* Bottom Footer: You remember you password? Sign in */}
                <View style={styles.footerRow}>
                  <Text style={styles.footerPrompt}>You remember you password? </Text>
                  <Pressable
                    onPress={() => router.replace('/(auth)/sign-in')}
                    hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
                    accessibilityRole="link"
                    accessibilityLabel="Sign in">
                    {({ pressed }) => (
                      <Text style={[styles.footerLink, pressed && styles.linkPressed]}>
                        Sign in
                      </Text>
                    )}
                  </Pressable>
                </View>
              </View>
            </View>
          </View>
        </TouchableWithoutFeedback>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: BRAND_COLORS.warmOffWhite,
  },
  keyboardView: {
    flex: 1,
  },
  content: {
    flex: 1,
    paddingHorizontal: 24,
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  topBar: {
    width: '100%',
    height: 38,
    justifyContent: 'center',
    alignItems: 'flex-start',
  },
  backBtn: {
    paddingVertical: 4,
    paddingHorizontal: 6,
  },
  backBtnPressed: {
    opacity: 0.6,
  },
  backBtnText: {
    fontSize: 15,
    fontWeight: '600',
    color: BRAND_COLORS.darkCharcoal,
  },
  cardContainer: {
    width: '100%',
    maxWidth: 390,
    alignItems: 'center',
    flex: 1,
    justifyContent: 'center',
    marginTop: -40,
  },
  imageContainer: {
    width: '100%',
    maxWidth: 232,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 8,
  },
  illustration: {
    width: '100%',
    height: '100%',
  },
  headingSection: {
    alignItems: 'center',
    marginBottom: 20,
    paddingHorizontal: 16,
  },
  title: {
    fontSize: 28,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    textAlign: 'center',
    letterSpacing: -0.5,
    fontFamily: Platform.select({
      ios: 'System',
      android: 'sans-serif-medium',
      default: 'system-ui',
    }),
  },
  subtitle: {
    fontSize: 13.5,
    color: BRAND_COLORS.secondaryText,
    textAlign: 'center',
    marginTop: 6,
    lineHeight: 19,
    maxWidth: 300,
    fontFamily: Platform.select({
      ios: 'System',
      android: 'sans-serif',
      default: 'system-ui',
    }),
  },
  formSection: {
    width: '100%',
  },
  inputGroup: {
    marginBottom: 16,
  },
  inputBox: {
    height: 52,
    backgroundColor: 'transparent',
    borderRadius: 8,
    borderWidth: 1,
    borderColor: BRAND_COLORS.borderLavender,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 14,
  },
  inputBoxFocused: {
    borderColor: BRAND_COLORS.mutedLavender,
    backgroundColor: 'transparent',
  },
  inputBoxError: {
    borderColor: '#DC2626',
  },
  leftIconContainer: {
    width: 22,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 10,
  },
  inputField: {
    flex: 1,
    height: '100%',
    fontSize: 14.5,
    color: BRAND_COLORS.darkCharcoal,
  },
  fieldErrorText: {
    color: '#DC2626',
    fontSize: 11.5,
    marginTop: 4,
    marginLeft: 6,
    fontWeight: '500',
  },
  sendBtnWrapper: {
    width: '100%',
    marginBottom: 20,
  },
  sendBtn: {
    height: 52,
    borderRadius: 16,
    backgroundColor: BRAND_COLORS.mutedLavender,
    alignItems: 'center',
    justifyContent: 'center',
    ...Platform.select({
      ios: {
        shadowColor: BRAND_COLORS.mutedLavender,
        shadowOffset: { width: 0, height: 3 },
        shadowOpacity: 0.22,
        shadowRadius: 6,
      },
      android: {
        elevation: 2,
      },
    }),
  },
  sendBtnDisabled: {
    opacity: 0.75,
  },
  sendBtnText: {
    fontSize: 15.5,
    fontWeight: '600',
    color: BRAND_COLORS.darkCharcoal,
    letterSpacing: -0.2,
  },
  successCard: {
    backgroundColor: BRAND_COLORS.white,
    padding: 20,
    borderRadius: 16,
    borderWidth: 1.5,
    borderColor: BRAND_COLORS.borderLavender,
    alignItems: 'center',
    gap: 8,
    marginBottom: 20,
  },
  successTitle: {
    fontSize: 17,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
  },
  successText: {
    fontSize: 13.5,
    color: BRAND_COLORS.secondaryText,
    textAlign: 'center',
    lineHeight: 19,
  },
  returnBtn: {
    marginTop: 6,
    paddingVertical: 8,
    paddingHorizontal: 16,
    backgroundColor: BRAND_COLORS.veryLightLavender,
    borderRadius: 12,
  },
  returnBtnText: {
    fontSize: 13.5,
    fontWeight: '600',
    color: BRAND_COLORS.darkCharcoal,
  },
  footerRow: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    paddingTop: 4,
  },
  footerPrompt: {
    fontSize: 13.5,
    color: BRAND_COLORS.secondaryText,
  },
  footerLink: {
    fontSize: 13.5,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
  },
  linkPressed: {
    opacity: 0.65,
  },
});
