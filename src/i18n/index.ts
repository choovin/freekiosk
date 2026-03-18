/**
 * FreeKiosk i18n Configuration
 * Internationalization support using i18next
 */

import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Platform } from 'react-native';

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

export const AVAILABLE_LANGUAGES = {
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

export const LANGUAGE_DETECTOR = {
  type: 'languageDetector',
  detect: async () => {
    try {
      const savedLanguage = await AsyncStorage.getItem('app_language');
      if (savedLanguage) {
        return savedLanguage;
      }
      // Detect from device
      const deviceLang = Platform.OS === 'ios'
        ? require('react-native/Libraries/Core/Devtools/getDevTools').language
        : Platform.constants.locale?.split('-')[0] || 'en';
      return AVAILABLE_LANGUAGES[deviceLang as keyof typeof AVAILABLE_LANGUAGES] ? deviceLang : 'en';
    } catch {
      return 'en';
    }
  },
  init: () => {},
  cacheUserLanguage: () => {},
};

i18n
  .use(initReactI18next)
  .init({
    resources: {
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
    },
    fallbackLng: 'en',
    debug: false,
    interpolation: {
      escapeValue: false,
    },
    compatibilityJSON: 'v3',
    defaultNS: 'translation',
  });

export default i18n;
