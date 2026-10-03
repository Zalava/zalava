import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  base: "/zalava-chat/",
  plugins: [react()],
  build: {
    outDir: "../src/main/resources/static/zalava-chat",
    emptyOutDir: true,
    rollupOptions: {
      output: {
        entryFileNames: "assets/zalava-chat.js",
        chunkFileNames: "assets/zalava-chat-[name].js",
        assetFileNames: (assetInfo) => {
          const names = assetInfo.names ?? [assetInfo.name ?? ""];
          return names.some((name) => name.endsWith(".css"))
            ? "assets/zalava-chat.css"
            : "assets/zalava-chat-[name][extname]";
        },
      },
    },
  },
});
