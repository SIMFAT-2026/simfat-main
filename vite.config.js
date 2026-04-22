import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            return undefined;
          }

          if (id.includes('react-leaflet') || id.includes('/leaflet/')) {
            return 'map-vendor';
          }

          if (id.includes('/recharts/')) {
            return 'charts-vendor';
          }

          if (id.includes('/react-router-dom/') || id.includes('/react-dom/') || id.includes('/react/')) {
            return 'react-core';
          }

          if (id.includes('/axios/')) {
            return 'network-vendor';
          }

          return undefined;
        }
      }
    }
  }
});
