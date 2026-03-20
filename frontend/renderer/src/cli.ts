import fs from "node:fs/promises";
import path from "node:path";
import { parseDeckSpec } from "@deckgo/deck-core";
import { renderDeckToFile } from "./renderDeck.js";

const [, , inputArg, outputArg] = process.argv;

if (!inputArg || !outputArg) {
  console.error("用法: npm run render -- <input.json> <output.pptx>");
  process.exit(1);
}

const inputPath = path.resolve(process.cwd(), inputArg);
const outputPath = path.resolve(process.cwd(), outputArg);
const source = await fs.readFile(inputPath, "utf-8");
const deck = parseDeckSpec(source);

await renderDeckToFile(deck, outputPath);
console.log(`Rendered ${deck.title} -> ${outputPath}`);
