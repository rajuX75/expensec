/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        background: "#0f0f13",
        surface: "#1a1a24",
        surface2: "#22222f",
        border: "#2e2e40",
        primary: "#6366f1",
        primaryHover: "#4f46e5",
        textMain: "#f1f1f5",
        textMuted: "#8b8ba8",
        success: "#10b981",
        warning: "#f59e0b",
        error: "#ef4444",
      },
      animation: {
        'pulse-slow': 'pulse 3s cubic-bezier(0.4, 0, 0.6, 1) infinite',
      }
    },
  },
  plugins: [],
}
