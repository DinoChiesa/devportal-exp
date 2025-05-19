// This file can be replaced during build by using the `fileReplacements` array.
// `ng build` replaces `environment.ts` with `environment.prod.ts`.
// The list of file replacements can be found in `angular.json`.

// Get this from the Firebase console.
//
// • Go to your Firebase project online (https://console.firebase.google.com/).
//
// • Click the Gear icon (Project settings) next to "Project Overview".
//
// • In the "General" tab, scroll down to the "Your apps" section.
//
// • If you haven't added your web app yet, click "Add app" and select the Web
//   platform (</>). Follow the steps to register your app (you'll give it a
//   nickname).
//
// • If you have already added your web app, click on it in the list.
//
// • Look for the "Firebase SDK snippet" section and select the "Config" option.
//
// • The configuration object shown there will contain your apiKey, authDomain,
//   projectId, storageBucket, messagingSenderId, and the appId. Copy the appId
//   value from there into your environment.ts and environment.prod.ts files.

export const environment = {
  production: false,
  firebase: {
    apiKey: process.env['FB_API_KEY'],
    authDomain: process.env['FB_AUTH_DOMAIN'],
    projectId: process.env['FB_PROJECT_ID'],
    storageBucket: process.env['FB_STORAGE_BUCKET'],
    messagingSenderId: process.env['FB_MSGING_SENDER_ID'],
    appId: process.env['FB_APP_ID']
  }
};
