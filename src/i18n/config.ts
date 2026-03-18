/**
 * FreeKiosk i18n Configuration
 * Internationalization support using i18next
 */

import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Platform, NativeModules } from 'react-native';

// Import translations
import en from './locales/en.json';
import zh from './locales/zh.json';
import es from './locales/es.json';
import fr from './locales/fr.json';
import de from './locales/de.json';
import ja from './locales/ja.json';
import ko from './locales/ko.json';
import pt from './locales/pt.json';
import ru from './locales/ru.json';
import ar from './locales/ar.json';

export const AVAILABLE_LANGUAGES: Record<string, string> = {
  en: 'English',
  zh: '中文',
  es: 'Español',
  fr: 'Français',
  de: 'Deutsch',
  ja: '日本語',
  ko: '한국어',
  pt: 'Português',
  ru: 'Русский',
  ar: 'العربية',
};

const RESOURCES = {
  en: { translation: en },
  zh: { translation: zh },
  es: { translation: es },
  fr: { translation: fr },
  de: { translation: de },
  ja: { translation: ja },
  ko: { translation: ko },
  pt: { translation: pt },
  ru: { translation: ru },
  ar: { translation: ar },
};

export type LanguageCode = keyof typeof AVAILABLE_LANGUAGES;

/**
 * Initialize i18n
 */
export const initI18n = async () => {
  try {
    // Try to get saved language
    let language: string = 'en';
    try {
      const savedLanguage = await AsyncStorage.getItem('app_language');
      if (savedLanguage && AVAILABLE_LANGUAGES[savedLanguage]) {
        language = savedLanguage;
      } else {
        // Detect from device
        const deviceLang = Platform.OS === 'ios'
          ? 'en' // iOS: could use NativeModules.SettingsManager
          : Platform.constants.locale?.split('-')[0] || 'en';
        if (AVAILABLE_LANGUAGES[deviceLang]) {
          language = deviceLang;
        }
      }
    } catch (e) {
      console.warn('i18n: Could not detect language', e);
    }

    i18n.use(initReactI18next).init({
      resources: RESOURCES,
      lng: language,
      fallbackLng: 'en',
      debug: false,
      interpolation: {
        escapeValue: false, // React already escapes
      },
      compatibilityJSON: 'v3',
      defaultNS: 'translation',
    });

    console.log(`i18n initialized with language: ${language}`);
  } catch (error) {
    console.error('i18n initialization error:', error);
  }
};

/**
 * Change language and save to storage
 */
export const changeLanguage = async (lang: LanguageCode) => {
  try {
    await i18n.changeLanguage(lang);
    await AsyncStorage.setItem('app_language', lang);
    console.log(`Language changed to: ${lang}`);
  } catch (error) {
    console.error('Error changing language:', error);
  }
};

/**
 * Get current language
 */
export const getCurrentLanguage = (): LanguageCode => {
  return (i18n.language as LanguageCode) || 'en';
};

export default i18n;
