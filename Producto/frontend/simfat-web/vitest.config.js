import { defineConfig } from 'vitest/config';

// Pure-logic tests only: node environment, no jsdom, no component tests.
// Kept separate from vite.config.js so the production build is untouched.
export default defineConfig({
  test: {
    environment: 'node',
    include: ['src/**/*.test.js']
  }
});
