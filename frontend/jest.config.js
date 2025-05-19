module.exports = {
  preset: 'jest-preset-angular',
  setupFilesAfterEnv: ['<rootDir>/src/setup-jest.ts'],
  testPathIgnorePatterns: ['<rootDir>/node_modules/', '<rootDir>/dist/'],
  moduleNameMapper: {
    // Add any module path mappings here if needed (e.g., for aliases in tsconfig.json)
    // Example: '^@app/(.*)$': '<rootDir>/src/app/$1',
  },
  // Optional: Configure coverage reporting
  // coverageReporters: ['html', 'text-summary'],
  // collectCoverageFrom: [
  //   'src/app/**/*.ts',
  //   '!src/app/**/*.module.ts', // Exclude module files
  //   '!src/app/**/*.routes.ts', // Exclude routing files
  //   '!src/main.ts',
  //   '!src/environments/**',
  //   '!src/setup-jest.ts',
  // ],
};
