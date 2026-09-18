import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
    sourcemap: false,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return
          // pdfjs worker + core — only loaded for grader routes
          if (id.includes('pdfjs-dist')) return 'pdf-engine'
          // jsPDF annotation flattener — same routes
          if (id.includes('jspdf')) return 'pdf-export'
          // React runtime — shared across all routes
          if (id.includes('/react/') || id.includes('/react-dom/') || id.includes('/react-router-dom/')) return 'react-vendor'
        }
      }
    }
  }
})
