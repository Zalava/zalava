import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  base: "/sea-chat/",
  plugins: [react()],
  build: {
    outDir: "../src/main/resources/static/sea-chat",
    emptyOutDir: true,
    rollupOptions: {
      output: {
        entryFileNames: "assets/sea-chat.js",
        chunkFileNames: "assets/sea-chat-[name].js",
        assetFileNames: (assetInfo) => {
          const names = assetInfo.names ?? [assetInfo.name ?? ""];
          return names.some((name) => name.endsWith(".css"))
            ? "assets/sea-chat.css"
            : "assets/sea-chat-[name][extname]";
        },
      },
    },
  },
});
