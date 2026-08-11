import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'https://linkcute.duckdns.org',
        changeOrigin: true,
        secure: true,
        configure(proxy) {
          proxy.on('proxyReq', (proxyRequest) => {
            // The browser adds localhost as Origin for POST requests. Because
            // this is a development-only server-side proxy, do not forward
            // that browser origin to the production Spring CORS filter.
            proxyRequest.removeHeader('origin')
          })
        },
      },
      '/ws': {
        target: 'https://linkcute.duckdns.org',
        changeOrigin: true,
        secure: true,
        ws: true,
        configure(proxy) {
          proxy.on('proxyReqWs', (proxyRequest) => {
            proxyRequest.removeHeader('origin')
          })
        },
      },
    },
  },
})
