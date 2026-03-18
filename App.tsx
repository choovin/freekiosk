import React, { useEffect } from 'react';
import { StatusBar } from 'react-native';
import AppNavigator from './src/navigation/AppNavigator';
import { initI18n } from './src/i18n/config';

const App: React.FC = () => {
  useEffect(() => {
    initI18n();
  }, []);

  return (
    <>
      <StatusBar hidden={true} />
      <AppNavigator />
    </>
  );
};

export default App;
