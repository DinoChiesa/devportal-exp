// Import Jest preset setup - This line is no longer needed with the new approach.
// import 'jest-preset-angular/setup-jest';

// Import and call the function to set up the Angular testing environment with Zone.js

import { setupZoneTestEnv } from 'jest-preset-angular/setup-env/zone';
//import { setupZoneTestEnv } from '@angular/core/testing';
setupZoneTestEnv();

// Add any other global setup needed for your tests here
// For example, mocking browser APIs:
// const mock = () => {
//   let storage: { [key: string]: string } = {};
//   return {
//     getItem: (key: string) => storage[key] || null,
//     setItem: (key: string, value: string) => storage[key] = value,
//     removeItem: (key: string) => delete storage[key],
//     clear: () => storage = {},
//   };
// };
// Object.defineProperty(window, 'localStorage', { value: mock() });
// Object.defineProperty(window, 'sessionStorage', { value: mock() });
// Object.defineProperty(window, 'getComputedStyle', {
//   value: () => ['-webkit-appearance']
// });
