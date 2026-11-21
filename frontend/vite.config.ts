import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Dev server proxies /api to the Spring Boot backend so the browser only talks to one origin.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5180,
    host: true,
    proxy: {
      '/api': { target: process.env.VITE_API_PROXY || 'http://localhost:8188', changeOrigin: true },
    },
  },
})
