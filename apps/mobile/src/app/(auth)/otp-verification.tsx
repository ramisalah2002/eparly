import { useLocalSearchParams, useRouter } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { Image } from 'expo-image';
import React, { ElementRef, useCallback, useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Keyboard,
  NativeSyntheticEvent,
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

import { PastelBackground } from '@/components/onboarding/pastel-background';
import { BRAND_COLORS } from '@/constants/colors';
import { useAuth } from '@/context/auth-context';
import { resendVerificationApi } from '@/services/auth-api';

type TextInputRef = ElementRef<typeof TextInput>;

const OTP_LENGTH = 6;
const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

export default function OtpVerificationScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { height: screenHeight } = useWindowDimensions();
  const params = useLocalSearchParams<{ email?: string; code?: string }>();
  const email = params.email || '';

  const { verifyEmail } = useAuth();

  const [digits, setDigits] = useState<string[]>(Array(OTP_LENGTH).fill(''));
  const [focusedIndex, setFocusedIndex] = useState<number>(0);
  const [isVerifying, setIsVerifying] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [resendCooldown, setResendCooldown] = useState<number>(60);
  const [resendStatus, setResendStatus] = useState<string | null>(null);

  const inputRefs = useRef<(TextInputRef | null)[]>([]);
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

  // Cooldown countdown
  useEffect(() => {
    if (resendCooldown <= 0) return;
    const interval = setInterval(() => {
      setResendCooldown((prev) => (prev > 0 ? prev - 1 : 0));
    }, 1000);
    return () => clearInterval(interval);
  }, [resendCooldown]);

  const handleVerify = useCallback(
    async (codeToVerify?: string) => {
      const code = codeToVerify || digits.join('');
      if (code.length < OTP_LENGTH) {
        setError(`Please enter the complete ${OTP_LENGTH}-digit code.`);
        return;
      }

      setIsVerifying(true);
      setError(null);

      try {
        const res = await verifyEmail(code);
        if (res.success) {
          Alert.alert(
            'Email Verified',
            'Your email address has been verified! You can now sign in to your account.',
            [
              {
                text: 'Sign In',
                onPress: () => router.replace('/(auth)/sign-in'),
              },
            ]
          );
        } else {
          setError(res.error || 'Invalid or expired code. Please try again.');
        }
      } catch {
        setError('An unexpected error occurred. Please try again.');
      } finally {
        setIsVerifying(false);
      }
    },
    [digits, router, verifyEmail]
  );

  // Auto-focus first digit or auto-verify if code passed via deep link
  useEffect(() => {
    if (params.code && params.code.length === OTP_LENGTH) {
      const initialDigits = params.code.split('');
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setDigits(initialDigits);
      handleVerify(params.code);
      return;
    }

    const timer = setTimeout(() => {
      inputRefs.current[0]?.focus();
    }, 350);
    return () => clearTimeout(timer);
  }, [params.code, handleVerify]);

  const handleDigitChange = (text: string, index: number) => {
    setError(null);

    // Handle full paste (e.g. user pasted 6 digits into any cell)
    const sanitized = text.replace(/[^0-9]/g, '');
    if (sanitized.length >= OTP_LENGTH) {
      const newDigits = sanitized.slice(0, OTP_LENGTH).split('');
      setDigits(newDigits);
      inputRefs.current[OTP_LENGTH - 1]?.focus();
      handleVerify(sanitized.slice(0, OTP_LENGTH));
      return;
    }

    const singleChar = sanitized.slice(-1);
    const updated = [...digits];
    updated[index] = singleChar;
    setDigits(updated);

    if (singleChar) {
      if (index < OTP_LENGTH - 1) {
        inputRefs.current[index + 1]?.focus();
      } else {
        // Last digit entered, auto submit
        const fullCode = updated.join('');
        if (fullCode.length === OTP_LENGTH) {
          handleVerify(fullCode);
        }
      }
    }
  };

  const handleKeyPress = (
    event: NativeSyntheticEvent<{ key: string }>,
    index: number
  ) => {
    if (event.nativeEvent.key === 'Backspace') {
      if (!digits[index] && index > 0) {
        const updated = [...digits];
        updated[index - 1] = '';
        setDigits(updated);
        inputRefs.current[index - 1]?.focus();
      }
    }
  };

  const handleResend = async () => {
    if (resendCooldown > 0 || !email) return;

    setResendStatus(null);
    setError(null);

    const res = await resendVerificationApi(email);
    if (res.success) {
      setResendCooldown(60);
      setResendStatus('A new 6-digit code has been sent to your email.');
    } else {
      setError(res.error || 'Could not resend code. Please try again later.');
    }
  };

  // Responsive illustration height
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
              paddingTop: insets.top + 2,
              paddingBottom: insets.bottom + 8,
            },
          ]}>
          {/* Top Bar with Back Button */}
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
              <Text style={styles.title}>Verification Code</Text>
              <Text style={styles.subtitle}>
                {email
                  ? `Enter the 6-digit code sent to\n${email}`
                  : 'Enter the 6-digit code sent to your email'}
              </Text>
            </View>

            {/* Form Section */}
            <View style={styles.formSection}>
              {/* Error Alert */}
              {error && (
                <View style={styles.errorAlert} accessibilityRole="alert">
                  <Text style={styles.errorAlertText}>{error}</Text>
                </View>
              )}

              {/* Resend Status Alert */}
              {resendStatus && (
                <View style={styles.infoAlert} accessibilityRole="status">
                  <Text style={styles.infoAlertText}>{resendStatus}</Text>
                </View>
              )}

              {/* 6 Digit Input Boxes */}
              <View style={styles.otpRow}>
                {Array.from({ length: OTP_LENGTH }).map((_, index) => {
                  const isFocused = focusedIndex === index;
                  const hasValue = Boolean(digits[index]);
                  return (
                    <View
                      key={index}
                      style={[
                        styles.otpCell,
                        isFocused && styles.otpCellFocused,
                        hasValue && styles.otpCellFilled,
                        Boolean(error) && styles.otpCellError,
                      ]}>
                      <TextInput
                        ref={(ref) => {
                          inputRefs.current[index] = ref;
                        }}
                        value={digits[index]}
                        onChangeText={(txt) => handleDigitChange(txt, index)}
                        onKeyPress={(e) => handleKeyPress(e, index)}
                        onFocus={() => setFocusedIndex(index)}
                        onBlur={() => setFocusedIndex(-1)}
                        keyboardType="number-pad"
                        maxLength={index === 0 ? OTP_LENGTH : 1}
                        textAlign="center"
                        style={styles.otpInput}
                        accessible={true}
                        accessibilityLabel={`Digit ${index + 1}`}
                        selectTextOnFocus={true}
                      />
                    </View>
                  );
                })}
              </View>

              {/* Verify CTA Button */}
              <AnimatedPressable
                onPress={() => handleVerify()}
                onPressIn={handlePressIn}
                onPressOut={handlePressOut}
                disabled={isVerifying}
                accessible={true}
                accessibilityRole="button"
                accessibilityLabel="Verify Code"
                style={[styles.verifyBtnWrapper, animatedButtonStyle]}>
                <View style={[styles.verifyBtn, isVerifying && styles.verifyBtnDisabled]}>
                  {isVerifying ? (
                    <ActivityIndicator size="small" color={BRAND_COLORS.darkCharcoal} />
                  ) : (
                    <Text style={styles.verifyBtnText}>Verify Code</Text>
                  )}
                </View>
              </AnimatedPressable>

              {/* Resend Row */}
              <View style={styles.resendRow}>
                <Text style={styles.resendPrompt}>{"Didn't receive the code? "}</Text>
                {resendCooldown > 0 ? (
                  <Text style={styles.cooldownText}>
                    Resend in {resendCooldown}s
                  </Text>
                ) : (
                  <Pressable
                    onPress={handleResend}
                    hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
                    {({ pressed }) => (
                      <Text style={[styles.resendLink, pressed && styles.linkPressed]}>
                        Resend Code
                      </Text>
                    )}
                  </Pressable>
                )}
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
    marginTop: -80,
  },
  imageContainer: {
    width: '100%',
    maxWidth: 232,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 16,
  },
  illustration: {
    width: '100%',
    height: '100%',
  },
  headingSection: {
    alignItems: 'center',
    marginBottom: 24,
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
    fontFamily: Platform.select({
      ios: 'System',
      android: 'sans-serif',
      default: 'system-ui',
    }),
  },
  formSection: {
    width: '100%',
  },
  errorAlert: {
    backgroundColor: 'rgba(239, 68, 68, 0.08)',
    borderRadius: 12,
    paddingVertical: 8,
    paddingHorizontal: 14,
    borderWidth: 1,
    borderColor: 'rgba(239, 68, 68, 0.22)',
    marginBottom: 16,
  },
  errorAlertText: {
    color: '#B91C1C',
    fontSize: 12.5,
    fontWeight: '500',
    lineHeight: 17,
    textAlign: 'center',
  },
  infoAlert: {
    backgroundColor: 'rgba(168, 155, 190, 0.15)',
    borderRadius: 12,
    paddingVertical: 8,
    paddingHorizontal: 14,
    borderWidth: 1,
    borderColor: 'rgba(168, 155, 190, 0.35)',
    marginBottom: 16,
  },
  infoAlertText: {
    color: BRAND_COLORS.darkCharcoal,
    fontSize: 12.5,
    fontWeight: '500',
    lineHeight: 17,
    textAlign: 'center',
  },
  otpRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 24,
    gap: 8,
  },
  otpCell: {
    flex: 1,
    height: 56,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: BRAND_COLORS.borderLavender,
    backgroundColor: 'transparent',
    alignItems: 'center',
    justifyContent: 'center',
  },
  otpCellFocused: {
    borderColor: BRAND_COLORS.mutedLavender,
    borderWidth: 1.5,
    ...Platform.select({
      ios: {
        shadowColor: BRAND_COLORS.mutedLavender,
        shadowOffset: { width: 0, height: 2 },
        shadowOpacity: 0.2,
        shadowRadius: 4,
      },
      android: {
        elevation: 1,
      },
    }),
  },
  otpCellFilled: {
    borderColor: BRAND_COLORS.darkCharcoal,
  },
  otpCellError: {
    borderColor: '#DC2626',
  },
  otpInput: {
    width: '100%',
    height: '100%',
    fontSize: 22,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    textAlign: 'center',
  },
  verifyBtnWrapper: {
    width: '100%',
    marginBottom: 18,
  },
  verifyBtn: {
    height: 52,
    borderRadius: 12,
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
  verifyBtnDisabled: {
    opacity: 0.75,
  },
  verifyBtnText: {
    fontSize: 15.5,
    fontWeight: '600',
    color: BRAND_COLORS.darkCharcoal,
    letterSpacing: -0.2,
  },
  resendRow: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    paddingTop: 4,
  },
  resendPrompt: {
    fontSize: 13.5,
    color: BRAND_COLORS.secondaryText,
  },
  cooldownText: {
    fontSize: 13.5,
    fontWeight: '600',
    color: BRAND_COLORS.secondaryText,
  },
  resendLink: {
    fontSize: 13.5,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
  },
  linkPressed: {
    opacity: 0.65,
  },
});
