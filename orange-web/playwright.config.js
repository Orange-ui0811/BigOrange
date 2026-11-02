import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? 'github' : 'list',
  use: {
    baseURL: process.env.ORANGEVC_E2E_BASE_URL || 'http://127.0.0.1:15173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: process.env.ORANGEVC_E2E_EXTERNAL ? undefined : [
    {
      command: 'node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 15173',
      url: 'http://127.0.0.1:15173',
      reuseExistingServer: !process.env.CI,
    },
    {
      command: 'node ../orange-admin/node_modules/vite/bin/vite.js ../orange-admin --host 127.0.0.1 --port 15174',
      url: 'http://127.0.0.1:15174',
      reuseExistingServer: !process.env.CI,
    },
  ],
})
