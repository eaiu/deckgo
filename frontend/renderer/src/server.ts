import Fastify from "fastify";
import type { DeckSpec } from "@deckgo/deck-core";
import { renderDeckToFile } from "./renderDeck.js";

const app = Fastify({ logger: true });

app.get("/health", async () => ({
  status: "ok",
  service: "deckgo-renderer"
}));

app.post<{
  Body: {
    jobId: string;
    templateId: string;
    deckSpec: DeckSpec;
    outputPath: string;
  };
}>("/internal/render", async (request) => {
  const { deckSpec, outputPath } = request.body;
  await renderDeckToFile(deckSpec, outputPath);
  return {
    outputPath,
    slideCount: deckSpec.slides.length
  };
});

app.listen({ port: 4301, host: "0.0.0.0" }).catch((error) => {
  app.log.error(error);
  process.exit(1);
});
