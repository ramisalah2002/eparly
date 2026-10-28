import { useRouter } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { Image } from 'expo-image';
import React, { useCallback, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
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

import {
  EyeToggleIcon,
  GoogleIcon,
  LockIcon,
  UserIcon,
} from '@/components/auth/auth-icons';
import { PastelBackground } from '@/components/onboarding/pastel-background';
import { BRAND_COLORS } from '@/constants/colors';
import { loginToApi } from '@/services/auth-api';

const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

export default function SignInScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { height: screenHeight } = useWindowDimensions();

  // Form states
  const [identifier, setIdentifier] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [identifierFocused, setIdentifierFocused] = useState(false);
  const [passwordFocused, setPasswordFocused] = useState(false);

  // Validation & Error states
  const [identifierError, setIdentifierError] = useState<string | null>(null);
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [serverError, setServerError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  // Button press feedback
  const loginButtonScale = useSharedValue(1);
  const googleButtonScale = useSharedValue(1);

  const animatedLoginButtonStyle = useAnimatedStyle(() => ({
    transform: [{ scale: loginButtonScale.value }],
  }));

  const animatedGoogleButtonStyle = useAnimatedStyle(() => ({
    transform: [{ scale: googleButtonScale.value }],
  }));

  const handleLoginPressIn = useCallback(() => {
    // eslint-disable-next-line react-hooks/immutability
    loginButtonScale.value = withTiming(0.982, { duration: 110 });
  }, [loginButtonScale]);

  const handleLoginPressOut = useCallback(() => {
    // eslint-disable-next-line react-hooks/immutability
    loginButtonScale.value = withTiming(1, { duration: 150 });
  }, [loginButtonScale]);

  const handleGooglePressIn = useCallback(() => {
    // eslint-disable-next-line react-hooks/immutability
    googleButtonScale.value = withTiming(0.982, { duration: 110 });
  }, [googleButtonScale]);

  const handleGooglePressOut = useCallback(() => {
    // eslint-disable-next-line react-hooks/immutability
    googleButtonScale.value = withTiming(1, { duration: 150 });
  }, [googleButtonScale]);

  // Validation
  const validateForm = (): boolean => {
    let isValid = true;
    setIdentifierError(null);
    setPasswordError(null);
    setServerError(null);

    const trimmed = identifier.trim();
    if (!trimmed) {
      setIdentifierError('User name or email is required');
      isValid = false;
    }

    if (!password) {
      setPasswordError('Password is required');
      isValid = false;
    } else if (password.length < 6) {
      setPasswordError('Password must be at least 6 characters');
      isValid = false;
    }

    return isValid;
  };

  // Handle Form Submission
  const handleSignIn = async () => {
    if (isLoading) return;
    if (!validateForm()) return;

    setIsLoading(true);
    setServerError(null);

    try {
      const result = await loginToApi({
        email: identifier.trim(),
        password,
      });

      if (result.success) {
        router.replace('/(tabs)');
      } else {
        setServerError(result.error);
      }
    } catch {
      setServerError('An unexpected error occurred. Please try again.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleGoogleSignIn = () => {
    Alert.alert(
      'Google Sign-In',
      'Google authentication will be configured with production OAuth credentials in the next milestone.',
      [{ text: 'OK' }]
    );
  };

  // Responsive illustration height: decreased by 20%
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
            {/* Centered Illustration */}
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
              <Text style={styles.title}>Sign In</Text>
            </View>

            {/* Form Section */}
            <View style={styles.formSection}>
              {/* Server Error Alert Banner */}
              {serverError && (
                <View style={styles.errorAlert} accessibilityRole="alert">
                  <Text style={styles.errorAlertText}>{serverError}</Text>
                </View>
              )}

              {/* Input 1: User name / Email with Person icon */}
              <View style={styles.inputGroup}>
                <View
                  style={[
                    styles.inputBox,
                    identifierFocused && styles.inputBoxFocused,
                    Boolean(identifierError) && styles.inputBoxError,
                  ]}>
                  <View style={styles.leftIconContainer}>
                    <UserIcon
                      color={identifierFocused ? BRAND_COLORS.mutedLavender : '#9EA0AA'}
                      size={19}
                    />
                  </View>
                  <TextInput
                    value={identifier}
                    onChangeText={(val) => {
                      setIdentifier(val);
                      if (identifierError) setIdentifierError(null);
                      if (serverError) setServerError(null);
                    }}
                    onFocus={() => setIdentifierFocused(true)}
                    onBlur={() => setIdentifierFocused(false)}
                    placeholder="User name"
                    placeholderTextColor="#9EA0AA"
                    keyboardType="email-address"
                    autoCapitalize="none"
                    autoComplete="username"
                    textContentType="username"
                    style={styles.inputField}
                    accessible={true}
                    accessibilityLabel="User name"
                  />
                </View>
                {identifierError && (
                  <Text style={styles.fieldErrorText}>{identifierError}</Text>
                )}
              </View>

              {/* Input 2: Password with Lock icon & Eyelash Eye Toggle icon */}
              <View style={styles.inputGroup}>
                <View
                  style={[
                    styles.inputBox,
                    passwordFocused && styles.inputBoxFocused,
                    Boolean(passwordError) && styles.inputBoxError,
                  ]}>
                  <View style={styles.leftIconContainer}>
                    <LockIcon
                      color={passwordFocused ? BRAND_COLORS.mutedLavender : '#9EA0AA'}
                      size={19}
                    />
                  </View>
                  <TextInput
                    value={password}
                    onChangeText={(val) => {
                      setPassword(val);
                      if (passwordError) setPasswordError(null);
                      if (serverError) setServerError(null);
                    }}
                    onFocus={() => setPasswordFocused(true)}
                    onBlur={() => setPasswordFocused(false)}
                    placeholder="Password"
                    placeholderTextColor="#9EA0AA"
                    secureTextEntry={!showPassword}
                    autoCapitalize="none"
                    autoComplete="password"
                    textContentType="password"
                    style={styles.inputField}
                    accessible={true}
                    accessibilityLabel="Password"
                  />
                  <Pressable
                    onPress={() => setShowPassword((prev) => !prev)}
                    hitSlop={{ top: 12, bottom: 12, left: 16, right: 16 }}
                    style={styles.eyeIconButton}
                    accessibilityRole="button"
                    accessibilityLabel={showPassword ? 'Hide password' : 'Show password'}>
                    <EyeToggleIcon
                      visible={showPassword}
                      color={showPassword ? BRAND_COLORS.darkCharcoal : '#9EA0AA'}
                      size={21}
                    />
                  </Pressable>
                </View>
                {passwordError && (
                  <Text style={styles.fieldErrorText}>{passwordError}</Text>
                )}
              </View>

              {/* Forget password link aligned right */}
              <View style={styles.forgotPasswordContainer}>
                <Pressable
                  onPress={() => router.push('/(auth)/forgot-password')}
                  hitSlop={{ top: 8, bottom: 8, left: 12, right: 12 }}
                  accessibilityRole="link"
                  accessibilityLabel="Forget password">
                  {({ pressed }) => (
                    <Text
                      style={[styles.forgotPasswordText, pressed && styles.linkPressed]}>
                      Forget password
                    </Text>
                  )}
                </Pressable>
              </View>

              {/* Large Login CTA Button */}
              <AnimatedPressable
                onPress={handleSignIn}
                onPressIn={handleLoginPressIn}
                onPressOut={handleLoginPressOut}
                disabled={isLoading}
                accessible={true}
                accessibilityRole="button"
                accessibilityLabel="Login"
                style={[styles.loginButtonWrapper, animatedLoginButtonStyle]}>
                <View style={[styles.loginButton, isLoading && styles.loginButtonDisabled]}>
                  {isLoading ? (
                    <ActivityIndicator size="small" color={BRAND_COLORS.darkCharcoal} />
                  ) : (
                    <Text style={styles.loginButtonText}>Login</Text>
                  )}
                </View>
              </AnimatedPressable>

              {/* "Or Continue with" Divider */}
              <View style={styles.dividerRow}>
                <View style={styles.dividerLine} />
                <Text style={styles.dividerText}>Or Continue with</Text>
                <View style={styles.dividerLine} />
              </View>

              {/* Only Google Social Button (No Facebook) */}
              <AnimatedPressable
                onPress={handleGoogleSignIn}
                onPressIn={handleGooglePressIn}
                onPressOut={handleGooglePressOut}
                accessible={true}
                accessibilityRole="button"
                accessibilityLabel="Continue with Google"
                style={[styles.googleButtonWrapper, animatedGoogleButtonStyle]}>
                <View style={styles.googleButton}>
                  <GoogleIcon size={19} />
                  <Text style={styles.googleButtonText}>Google</Text>
                </View>
              </AnimatedPressable>

              {/* Bottom Footer: Haven't any account? Sign up */}
              <View style={styles.footerRow}>
                <Text style={styles.footerPrompt}>{"Haven't any account? "}</Text>
                <Pressable
                  onPress={() => router.push('/(auth)/sign-up')}
                  hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
                  accessibilityRole="link"
                  accessibilityLabel="Sign up">
                  {({ pressed }) => (
                    <Text style={[styles.footerLink, pressed && styles.linkPressed]}>
                      Sign up
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
    marginTop: -100,
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
    marginTop: 4,
    lineHeight: 18,
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
    marginBottom: 10,
  },
  errorAlertText: {
    color: '#B91C1C',
    fontSize: 12.5,
    fontWeight: '500',
    lineHeight: 17,
    textAlign: 'center',
  },
  inputGroup: {
    marginBottom: 12,
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
  eyeIconButton: {
    padding: 6,
    alignItems: 'center',
    justifyContent: 'center',
  },
  fieldErrorText: {
    color: '#DC2626',
    fontSize: 11.5,
    marginTop: 4,
    marginLeft: 6,
    fontWeight: '500',
  },
  forgotPasswordContainer: {
    alignItems: 'flex-end',
    marginBottom: 14,
    marginTop: -2,
  },
  forgotPasswordText: {
    fontSize: 13,
    fontWeight: '500',
    color: BRAND_COLORS.secondaryText,
  },
  linkPressed: {
    opacity: 0.65,
  },
  loginButtonWrapper: {
    width: '100%',
    marginBottom: 14,
  },
  loginButton: {
    height: 52,
    borderRadius: 10,
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
  loginButtonDisabled: {
    opacity: 0.75,
  },
  loginButtonText: {
    fontSize: 15.5,
    fontWeight: '600',
    color: BRAND_COLORS.darkCharcoal,
    letterSpacing: -0.2,
  },
  dividerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 12,
  },
  dividerLine: {
    flex: 1,
    height: 1,
    backgroundColor: 'rgba(168, 155, 190, 0.25)',
  },
  dividerText: {
    paddingHorizontal: 12,
    fontSize: 12.5,
    fontWeight: '500',
    color: BRAND_COLORS.secondaryText,
  },
  googleButtonWrapper: {
    width: '100%',
    marginBottom: 16,
  },
  googleButton: {
    height: 52,
    borderRadius: 12,
    borderWidth: .5,
    backgroundColor: BRAND_COLORS.warmOffWhite,
    borderColor: BRAND_COLORS.borderLavender,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 9,
    ...Platform.select({
      ios: {
        shadowColor: '#242535',
        shadowOffset: { width: 0, height: 2 },
        shadowOpacity: 0.04,
        shadowRadius: 4,
      },
      android: {
        elevation: 1,
      },
    }),
  },
  googleButtonText: {
    fontSize: 14.5,
    fontWeight: '600',
    color: BRAND_COLORS.darkCharcoal,
  },
  footerRow: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    paddingTop: 2,
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
});
