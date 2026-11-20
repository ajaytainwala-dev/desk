import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// BASE_PATH is set by the GitHub Pages workflow (e.g. "/desk/").
const base = process.env.BASE_PATH || '/'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  base,
})
