import { useRouter } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { Image } from 'expo-image';
import React, { ElementRef, useCallback, useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
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

import { EyeToggleIcon, LockIcon, MailIcon } from '@/components/auth/auth-icons';
import { PastelBackground } from '@/components/onboarding/pastel-background';
import { BRAND_COLORS } from '@/constants/colors';
import {
  forgotPasswordApi,
  resetPasswordApi,
  verifyResetCodeApi,
} from '@/services/auth-api';

type Step = 'EMAIL' | 'OTP' | 'PASSWORD' | 'SUCCESS';
type TextInputRef = ElementRef<typeof TextInput>;

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const OTP_LENGTH = 6;
const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

export default function ForgotPasswordScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { height: screenHeight } = useWindowDimensions();

  const [step, setStep] = useState<Step>('EMAIL');

  // Step 1: Email
  const [email, setEmail] = useState('');
  const [emailFocused, setEmailFocused] = useState(false);

  // Step 2: OTP
  const [digits, setDigits] = useState<string[]>(Array(OTP_LENGTH).fill(''));
  const [focusedOtpIndex, setFocusedOtpIndex] = useState<number>(-1);
  const [resendCooldown, setResendCooldown] = useState<number>(0);
  const [resendStatus, setResendStatus] = useState<string | null>(null);
  const otpInputRefs = useRef<(TextInputRef | null)[]>([]);

  // Step 3: New Password
  const [newPassword, setNewPassword] = useState('');
  const [newPasswordFocused, setNewPasswordFocused] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);

  const [confirmPassword, setConfirmPassword] = useState('');
  const [confirmPasswordFocused, setConfirmPasswordFocused] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  // Status & Error
  const [error, setError] = useState<string | null>(null);
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

  // Resend cooldown timer
  useEffect(() => {
    if (resendCooldown <= 0) return;
    const interval = setInterval(() => {
      setResendCooldown((prev) => (prev > 0 ? prev - 1 : 0));
    }, 1000);
    return () => clearInterval(interval);
  }, [resendCooldown]);

  // Focus first OTP cell when entering OTP step
  useEffect(() => {
    if (step === 'OTP') {
      const timer = setTimeout(() => {
        otpInputRefs.current[0]?.focus();
      }, 350);
      return () => clearTimeout(timer);
    }
  }, [step]);

  // Top bar back action
  const handleBack = () => {
    setError(null);
    if (step === 'OTP') {
      setStep('EMAIL');
    } else if (step === 'PASSWORD') {
      setStep('OTP');
    } else if (step === 'SUCCESS') {
      router.replace('/(auth)/sign-in');
    } else {
      router.back();
    }
  };

  // --- Step 1: Submit Email ---
  const handleSendEmail = async () => {
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
      const res = await forgotPasswordApi(trimmed);
      if (res.success) {
        setStep('OTP');
        setDigits(Array(OTP_LENGTH).fill(''));
        setResendCooldown(60);
      } else {
        setError(res.error || 'Unable to send reset code. Please try again.');
      }
    } catch {
      setError('Network error while requesting reset code.');
    } finally {
      setIsLoading(false);
    }
  };

  // --- Step 2: Handle OTP input & verification ---
  const handleVerifyOtp = useCallback(
    async (codeToVerify?: string) => {
      const code = (codeToVerify || digits.join('')).trim();
      if (code.length < OTP_LENGTH) {
        setError(`Please enter the complete ${OTP_LENGTH}-digit code.`);
        return;
      }

      setIsLoading(true);
      setError(null);

      try {
        const res = await verifyResetCodeApi(code);
        if (res.success) {
          setStep('PASSWORD');
        } else {
          setError(res.error || 'Invalid or expired code. Please try again.');
        }
      } catch {
        setError('Network error while verifying reset code.');
      } finally {
        setIsLoading(false);
      }
    },
    [digits]
  );

  const handleDigitChange = (text: string, index: number) => {
    setError(null);

    const sanitized = text.replace(/[^0-9]/g, '');
    if (sanitized.length >= OTP_LENGTH) {
      const newDigits = sanitized.slice(0, OTP_LENGTH).split('');
      setDigits(newDigits);
      otpInputRefs.current[OTP_LENGTH - 1]?.focus();
      handleVerifyOtp(sanitized.slice(0, OTP_LENGTH));
      return;
    }

    const singleChar = sanitized.slice(-1);
    const updated = [...digits];
    updated[index] = singleChar;
    setDigits(updated);

    if (singleChar) {
      if (index < OTP_LENGTH - 1) {
        otpInputRefs.current[index + 1]?.focus();
      } else {
        const fullCode = updated.join('');
        if (fullCode.length === OTP_LENGTH) {
          handleVerifyOtp(fullCode);
        }
      }
    }
  };

  const handleDigitKeyPress = (
    event: NativeSyntheticEvent<{ key: string }>,
    index: number
  ) => {
    if (event.nativeEvent.key === 'Backspace') {
      if (!digits[index] && index > 0) {
        const updated = [...digits];
        updated[index - 1] = '';
        setDigits(updated);
        otpInputRefs.current[index - 1]?.focus();
      }
    }
  };

  const handleResendCode = async () => {
    if (resendCooldown > 0 || !email) return;

    setResendStatus(null);
    setError(null);

    const res = await forgotPasswordApi(email);
    if (res.success) {
      setResendCooldown(60);
      setResendStatus('A new 6-digit code has been sent to your email.');
    } else {
      setError(res.error || 'Could not resend code. Please try again later.');
    }
  };

  // --- Step 3: Submit New Password ---
  const handleSavePassword = async () => {
    setError(null);

    if (!newPassword) {
      setError('Please enter a new password.');
      return;
    }
    if (newPassword.length < 8) {
      setError('Password must be at least 8 characters long.');
      return;
    }
    if (!confirmPassword) {
      setError('Please confirm your new password.');
      return;
    }
    if (newPassword !== confirmPassword) {
      setError('Passwords do not match. Please verify and try again.');
      return;
    }

    const code = digits.join('').trim();
    if (code.length < OTP_LENGTH) {
      setError('Session expired or code missing. Please restart the reset process.');
      setStep('EMAIL');
      return;
    }

    setIsLoading(true);
    try {
      const res = await resetPasswordApi(code, newPassword);
      if (res.success) {
        setStep('SUCCESS');
      } else {
        setError(res.error || 'Unable to reset password. Please try again.');
      }
    } catch {
      setError('Network error while resetting password.');
    } finally {
      setIsLoading(false);
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
              onPress={handleBack}
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

            {/* Heading & Subtitle depending on active step */}
            <View style={styles.headingSection}>
              {step === 'EMAIL' && (
                <>
                  <Text style={styles.title}>Forgot Password</Text>
                  <Text style={styles.subtitle}>
                    {"Don't worry it happens. Please enter the address associated with your account"}
                  </Text>
                </>
              )}

              {step === 'OTP' && (
                <>
                  <Text style={styles.title}>Verification Code</Text>
                  <Text style={styles.subtitle}>
                    {email
                      ? `Enter the 6-digit code sent to\n${email}`
                      : 'Enter the 6-digit code sent to your email'}
                  </Text>
                </>
              )}

              {step === 'PASSWORD' && (
                <>
                  <Text style={styles.title}>New Password</Text>
                  <Text style={styles.subtitle}>
                    Create a strong new password with at least 8 characters
                  </Text>
                </>
              )}

              {step === 'SUCCESS' && (
                <>
                  <Text style={styles.title}>Password Reset!</Text>
                  <Text style={styles.subtitle}>
                    Your password has been changed successfully. You can now sign in with your new credentials.
                  </Text>
                </>
              )}
            </View>

            {/* Dynamic Form Sections */}
            <View style={styles.formSection}>
              {/* Error Banner */}
              {error && (
                <View style={styles.errorAlert} accessibilityRole="alert">
                  <Text style={styles.errorAlertText}>{error}</Text>
                </View>
              )}

              {/* STEP 1: EMAIL */}
              {step === 'EMAIL' && (
                <>
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
                  </View>

                  <AnimatedPressable
                    onPress={handleSendEmail}
                    onPressIn={handlePressIn}
                    onPressOut={handlePressOut}
                    disabled={isLoading}
                    accessible={true}
                    accessibilityRole="button"
                    accessibilityLabel="Send OTP"
                    style={[styles.actionBtnWrapper, animatedButtonStyle]}>
                    <View style={[styles.actionBtn, isLoading && styles.actionBtnDisabled]}>
                      {isLoading ? (
                        <ActivityIndicator size="small" color={BRAND_COLORS.darkCharcoal} />
                      ) : (
                        <Text style={styles.actionBtnText}>Send OTP</Text>
                      )}
                    </View>
                  </AnimatedPressable>

                  <View style={styles.footerRow}>
                    <Text style={styles.footerPrompt}>You remember your password? </Text>
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
                </>
              )}

              {/* STEP 2: OTP */}
              {step === 'OTP' && (
                <>
                  {resendStatus && (
                    <View style={styles.infoAlert} accessibilityRole="status">
                      <Text style={styles.infoAlertText}>{resendStatus}</Text>
                    </View>
                  )}

                  {/* 6 Digit Cells */}
                  <View style={styles.otpRow}>
                    {Array.from({ length: OTP_LENGTH }).map((_, index) => {
                      const isFocused = focusedOtpIndex === index;
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
                              otpInputRefs.current[index] = ref;
                            }}
                            value={digits[index]}
                            onChangeText={(txt) => handleDigitChange(txt, index)}
                            onKeyPress={(e) => handleDigitKeyPress(e, index)}
                            onFocus={() => setFocusedOtpIndex(index)}
                            onBlur={() => setFocusedOtpIndex(-1)}
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

                  <AnimatedPressable
                    onPress={() => handleVerifyOtp()}
                    onPressIn={handlePressIn}
                    onPressOut={handlePressOut}
                    disabled={isLoading}
                    accessible={true}
                    accessibilityRole="button"
                    accessibilityLabel="Verify Code"
                    style={[styles.actionBtnWrapper, animatedButtonStyle]}>
                    <View style={[styles.actionBtn, isLoading && styles.actionBtnDisabled]}>
                      {isLoading ? (
                        <ActivityIndicator size="small" color={BRAND_COLORS.darkCharcoal} />
                      ) : (
                        <Text style={styles.actionBtnText}>Verify Code</Text>
                      )}
                    </View>
                  </AnimatedPressable>

                  {/* Resend Code Section */}
                  <View style={styles.resendSection}>
                    {resendCooldown > 0 ? (
                      <Text style={styles.cooldownText}>
                        Resend code in <Text style={styles.cooldownNumber}>{resendCooldown}s</Text>
                      </Text>
                    ) : (
                      <View style={styles.resendRow}>
                        <Text style={styles.resendPrompt}>Didn&apos;t receive code? </Text>
                        <Pressable
                          onPress={handleResendCode}
                          hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
                          accessibilityRole="button"
                          accessibilityLabel="Resend code">
                          {({ pressed }) => (
                            <Text style={[styles.resendLink, pressed && styles.linkPressed]}>
                              Resend
                            </Text>
                          )}
                        </Pressable>
                      </View>
                    )}
                  </View>
                </>
              )}

              {/* STEP 3: NEW PASSWORD */}
              {step === 'PASSWORD' && (
                <>
                  {/* New Password Input */}
                  <View style={styles.inputGroup}>
                    <View
                      style={[
                        styles.inputBox,
                        newPasswordFocused && styles.inputBoxFocused,
                        Boolean(error) && styles.inputBoxError,
                      ]}>
                      <View style={styles.leftIconContainer}>
                        <LockIcon
                          color={newPasswordFocused ? BRAND_COLORS.mutedLavender : '#9EA0AA'}
                          size={19}
                        />
                      </View>
                      <TextInput
                        value={newPassword}
                        onChangeText={(val) => {
                          setNewPassword(val);
                          if (error) setError(null);
                        }}
                        onFocus={() => setNewPasswordFocused(true)}
                        onBlur={() => setNewPasswordFocused(false)}
                        placeholder="New password (min 8 chars)"
                        placeholderTextColor="#9EA0AA"
                        secureTextEntry={!showNewPassword}
                        autoCapitalize="none"
                        autoComplete="new-password"
                        textContentType="newPassword"
                        style={styles.inputField}
                        accessible={true}
                        accessibilityLabel="New password"
                      />
                      <Pressable
                        onPress={() => setShowNewPassword((prev) => !prev)}
                        hitSlop={{ top: 12, bottom: 12, left: 16, right: 16 }}
                        style={styles.eyeIconButton}
                        accessibilityRole="button"
                        accessibilityLabel={showNewPassword ? 'Hide password' : 'Show password'}>
                        <EyeToggleIcon
                          visible={showNewPassword}
                          size={20}
                          color={showNewPassword ? BRAND_COLORS.mutedLavender : '#9EA0AA'}
                        />
                      </Pressable>
                    </View>
                  </View>

                  {/* Confirm Password Input */}
                  <View style={styles.inputGroup}>
                    <View
                      style={[
                        styles.inputBox,
                        confirmPasswordFocused && styles.inputBoxFocused,
                        Boolean(error) && styles.inputBoxError,
                      ]}>
                      <View style={styles.leftIconContainer}>
                        <LockIcon
                          color={confirmPasswordFocused ? BRAND_COLORS.mutedLavender : '#9EA0AA'}
                          size={19}
                        />
                      </View>
                      <TextInput
                        value={confirmPassword}
                        onChangeText={(val) => {
                          setConfirmPassword(val);
                          if (error) setError(null);
                        }}
                        onFocus={() => setNewPasswordFocused(false)}
                        onBlur={() => setConfirmPasswordFocused(false)}
                        placeholder="Confirm new password"
                        placeholderTextColor="#9EA0AA"
                        secureTextEntry={!showConfirmPassword}
                        autoCapitalize="none"
                        autoComplete="new-password"
                        textContentType="newPassword"
                        style={styles.inputField}
                        accessible={true}
                        accessibilityLabel="Confirm new password"
                      />
                      <Pressable
                        onPress={() => setShowConfirmPassword((prev) => !prev)}
                        hitSlop={{ top: 12, bottom: 12, left: 16, right: 16 }}
                        style={styles.eyeIconButton}
                        accessibilityRole="button"
                        accessibilityLabel={showConfirmPassword ? 'Hide password' : 'Show password'}>
                        <EyeToggleIcon
                          visible={showConfirmPassword}
                          size={20}
                          color={showConfirmPassword ? BRAND_COLORS.mutedLavender : '#9EA0AA'}
                        />
                      </Pressable>
                    </View>
                  </View>

                  <AnimatedPressable
                    onPress={handleSavePassword}
                    onPressIn={handlePressIn}
                    onPressOut={handlePressOut}
                    disabled={isLoading}
                    accessible={true}
                    accessibilityRole="button"
                    accessibilityLabel="Save New Password"
                    style={[styles.actionBtnWrapper, animatedButtonStyle]}>
                    <View style={[styles.actionBtn, isLoading && styles.actionBtnDisabled]}>
                      {isLoading ? (
                        <ActivityIndicator size="small" color={BRAND_COLORS.darkCharcoal} />
                      ) : (
                        <Text style={styles.actionBtnText}>Save New Password</Text>
                      )}
                    </View>
                  </AnimatedPressable>
                </>
              )}

              {/* STEP 4: SUCCESS */}
              {step === 'SUCCESS' && (
                <View style={styles.successCard}>
                  <Text style={styles.successTitle}>Password Changed!</Text>
                  <Text style={styles.successText}>
                    Your new password has been set. All existing sessions were safely terminated.
                  </Text>
                  <Pressable
                    onPress={() => router.replace('/(auth)/sign-in')}
                    style={styles.returnBtn}
                    accessibilityRole="button"
                    accessibilityLabel="Sign in with new password">
                    <Text style={styles.returnBtnText}>Sign In Now</Text>
                  </Pressable>
                </View>
              )}
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
  errorAlert: {
    backgroundColor: '#FEE2E2',
    borderWidth: 1,
    borderColor: '#F87171',
    borderRadius: 8,
    paddingVertical: 10,
    paddingHorizontal: 14,
    marginBottom: 14,
  },
  errorAlertText: {
    color: '#B91C1C',
    fontSize: 13,
    fontWeight: '500',
    textAlign: 'center',
    lineHeight: 18,
  },
  infoAlert: {
    backgroundColor: '#EEF2FF',
    borderWidth: 1,
    borderColor: '#C7D2FE',
    borderRadius: 8,
    paddingVertical: 10,
    paddingHorizontal: 14,
    marginBottom: 14,
  },
  infoAlertText: {
    color: '#3730A3',
    fontSize: 13,
    fontWeight: '500',
    textAlign: 'center',
    lineHeight: 18,
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
  eyeIconButton: {
    padding: 6,
    justifyContent: 'center',
    alignItems: 'center',
  },
  inputField: {
    flex: 1,
    height: '100%',
    fontSize: 14.5,
    color: BRAND_COLORS.darkCharcoal,
  },
  otpRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    width: '100%',
    marginBottom: 20,
    gap: 8,
  },
  otpCell: {
    flex: 1,
    height: 52,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: BRAND_COLORS.borderLavender,
    backgroundColor: 'transparent',
    alignItems: 'center',
    justifyContent: 'center',
  },
  otpCellFocused: {
    borderColor: BRAND_COLORS.mutedLavender,
    borderWidth: 1.5,
  },
  otpCellFilled: {
    borderColor: BRAND_COLORS.borderLavender,
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
  actionBtnWrapper: {
    width: '100%',
    marginBottom: 18,
  },
  actionBtn: {
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
  actionBtnDisabled: {
    opacity: 0.75,
  },
  actionBtnText: {
    fontSize: 15.5,
    fontWeight: '600',
    color: BRAND_COLORS.darkCharcoal,
    letterSpacing: -0.2,
  },
  resendSection: {
    alignItems: 'center',
    paddingVertical: 4,
  },
  cooldownText: {
    fontSize: 13.5,
    color: BRAND_COLORS.secondaryText,
  },
  cooldownNumber: {
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
  },
  resendRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  resendPrompt: {
    fontSize: 13.5,
    color: BRAND_COLORS.secondaryText,
  },
  resendLink: {
    fontSize: 13.5,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
  },
  successCard: {
    backgroundColor: BRAND_COLORS.white,
    padding: 24,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: BRAND_COLORS.borderLavender,
    alignItems: 'center',
    gap: 12,
    marginBottom: 20,
    ...Platform.select({
      ios: {
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 4 },
        shadowOpacity: 0.06,
        shadowRadius: 10,
      },
      android: {
        elevation: 2,
      },
    }),
  },
  successTitle: {
    fontSize: 19,
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
    textAlign: 'center',
  },
  successText: {
    fontSize: 13.5,
    color: BRAND_COLORS.secondaryText,
    textAlign: 'center',
    lineHeight: 20,
  },
  returnBtn: {
    marginTop: 8,
    paddingVertical: 12,
    paddingHorizontal: 24,
    backgroundColor: BRAND_COLORS.mutedLavender,
    borderRadius: 12,
  },
  returnBtnText: {
    fontSize: 14.5,
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
