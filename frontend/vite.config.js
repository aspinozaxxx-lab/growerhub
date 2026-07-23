import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (
            id.includes('/node_modules/react/')
            || id.includes('/node_modules/react-dom/')
            || id.includes('/node_modules/react-router/')
            || id.includes('/node_modules/react-router-dom/')
            || id.includes('/node_modules/i18next/')
            || id.includes('/node_modules/react-i18next/')
          ) {
            return 'public-vendor'
          }
          return undefined
        },
      },
    },
  },
})
