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
  LockIcon,
  MailIcon,
  UserIcon,
} from '@/components/auth/auth-icons';
import { PastelBackground } from '@/components/onboarding/pastel-background';
import { BRAND_COLORS } from '@/constants/colors';

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

export default function SignUpScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { height: screenHeight } = useWindowDimensions();

  // Form states
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);

  const [nameFocused, setNameFocused] = useState(false);
  const [emailFocused, setEmailFocused] = useState(false);
  const [passwordFocused, setPasswordFocused] = useState(false);

  // Errors & Loading
  const [nameError, setNameError] = useState<string | null>(null);
  const [emailError, setEmailError] = useState<string | null>(null);
  const [passwordError, setPasswordError] = useState<string | null>(null);
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

  const validate = (): boolean => {
    let valid = true;
    setNameError(null);
    setEmailError(null);
    setPasswordError(null);

    if (!fullName.trim()) {
      setNameError('Full name is required');
      valid = false;
    }

    const trimmedEmail = email.trim();
    if (!trimmedEmail) {
      setEmailError('Email address is required');
      valid = false;
    } else if (!EMAIL_REGEX.test(trimmedEmail)) {
      setEmailError('Please enter a valid email address');
      valid = false;
    }

    if (!password) {
      setPasswordError('Password is required');
      valid = false;
    } else if (password.length < 6) {
      setPasswordError('Password must be at least 6 characters');
      valid = false;
    }

    return valid;
  };

  const handleCreateAccount = () => {
    if (isLoading) return;
    if (!validate()) return;

    setIsLoading(true);
    setTimeout(() => {
      setIsLoading(false);
      Alert.alert(
        'Account Registration',
        'Account creation will connect to your email verification endpoint in the next phase.',
        [{ text: 'OK' }]
      );
    }, 600);
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
              <Text style={styles.title}>Sign Up</Text>
            </View>

            {/* Form Section */}
            <View style={styles.formSection}>
              {/* Input 1: Full name with Person icon */}
              <View style={styles.inputGroup}>
                <View
                  style={[
                    styles.inputBox,
                    nameFocused && styles.inputBoxFocused,
                    Boolean(nameError) && styles.inputBoxError,
                  ]}>
                  <View style={styles.leftIconContainer}>
                    <UserIcon
                      color={nameFocused ? BRAND_COLORS.mutedLavender : '#9EA0AA'}
                      size={19}
                    />
                  </View>
                  <TextInput
                    value={fullName}
                    onChangeText={(val) => {
                      setFullName(val);
                      if (nameError) setNameError(null);
                    }}
                    onFocus={() => setNameFocused(true)}
                    onBlur={() => setNameFocused(false)}
                    placeholder="Full name"
                    placeholderTextColor="#9EA0AA"
                    autoCapitalize="words"
                    autoComplete="name"
                    textContentType="name"
                    style={styles.inputField}
                    accessible={true}
                    accessibilityLabel="Full name"
                  />
                </View>
                {nameError && (
                  <Text style={styles.fieldErrorText}>{nameError}</Text>
                )}
              </View>

              {/* Input 2: Email address with Envelope icon */}
              <View style={styles.inputGroup}>
                <View
                  style={[
                    styles.inputBox,
                    emailFocused && styles.inputBoxFocused,
                    Boolean(emailError) && styles.inputBoxError,
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
                      if (emailError) setEmailError(null);
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
                {emailError && (
                  <Text style={styles.fieldErrorText}>{emailError}</Text>
                )}
              </View>

              {/* Input 3: Password with Lock icon & Eye toggle */}
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
                    }}
                    onFocus={() => setPasswordFocused(true)}
                    onBlur={() => setPasswordFocused(false)}
                    placeholder="Password"
                    placeholderTextColor="#9EA0AA"
                    secureTextEntry={!showPassword}
                    autoCapitalize="none"
                    autoComplete="new-password"
                    textContentType="newPassword"
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

              {/* Terms and Privacy Policy Notice */}
              <View style={styles.termsContainer}>
                <Text style={styles.termsText}>
                  By signing up, you are agree to our{' '}
                  <Text style={styles.termsHighlight}>Terms & Conditions</Text>
                  {' and '}
                  <Text style={styles.termsHighlight}>Privacy Policy</Text>
                </Text>
              </View>

              {/* Large Create Account CTA Button */}
              <AnimatedPressable
                onPress={handleCreateAccount}
                onPressIn={handlePressIn}
                onPressOut={handlePressOut}
                disabled={isLoading}
                accessible={true}
                accessibilityRole="button"
                accessibilityLabel="Create Account"
                style={[styles.createBtnWrapper, animatedButtonStyle]}>
                <View style={[styles.createBtn, isLoading && styles.createBtnDisabled]}>
                  {isLoading ? (
                    <ActivityIndicator size="small" color={BRAND_COLORS.darkCharcoal} />
                  ) : (
                    <Text style={styles.createBtnText}>Create Account</Text>
                  )}
                </View>
              </AnimatedPressable>

              {/* Bottom Footer: Already have an Account? Sign in */}
              <View style={styles.footerRow}>
                <Text style={styles.footerPrompt}>Already have an Account? </Text>
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
  inputGroup: {
    marginBottom: 12,
  },
  inputBox: {
    height: 52,
    backgroundColor: 'transparent',
    borderRadius: 12,
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
  termsContainer: {
    paddingHorizontal: 12,
    marginVertical: 14,
    alignItems: 'center',
  },
  termsText: {
    fontSize: 12.5,
    color: BRAND_COLORS.secondaryText,
    textAlign: 'center',
    lineHeight: 18,
  },
  termsHighlight: {
    fontWeight: '700',
    color: BRAND_COLORS.darkCharcoal,
  },
  createBtnWrapper: {
    width: '100%',
    marginBottom: 16,
  },
  createBtn: {
    height: 52,
    borderRadius: 12,
    backgroundColor: BRAND_COLORS.mutedLavender,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: BRAND_COLORS.mutedLavender,
    shadowOffset: { width: 0, height: 3 },
    shadowOpacity: 0.22,
    shadowRadius: 6,
    elevation: 2,
  },
  createBtnDisabled: {
    opacity: 0.75,
  },
  createBtnText: {
    fontSize: 15.5,
    fontWeight: '600',
    color: BRAND_COLORS.darkCharcoal,
    letterSpacing: -0.2,
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
