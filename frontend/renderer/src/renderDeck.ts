import fs from "node:fs/promises";
import path from "node:path";
import PptxGenJS from "pptxgenjs";
import type { BlockSpec, DeckSpec, SlideSpec } from "@deckgo/deck-core";
import { findTemplate } from "@deckgo/template-kit";

export async function renderDeckToFile(deck: DeckSpec, outputPath: string) {
  const pptx = new PptxGenJS();
  const template = findTemplate(deck.templateId);

  pptx.layout = "LAYOUT_WIDE";
  pptx.author = "DeckGo Renderer";
  pptx.company = "DeckGo";
  pptx.subject = deck.title;
  pptx.title = deck.title;
  pptx.theme = {
    headFontFace: "Aptos",
    bodyFontFace: "Aptos"
  };

  for (const slideSpec of deck.slides) {
    const slide = pptx.addSlide();
    slide.background = { color: normalizeColor(template?.defaultTheme.background ?? deck.theme.palette.background) };
    slide.addText(slideSpec.title, {
      x: 0.5,
      y: 0.2,
      w: 11.5,
      h: 0.5,
      fontSize: 24,
      bold: true,
      color: normalizeColor(template?.defaultTheme.primary ?? deck.theme.palette.primary)
    });

    renderSlide(slide, slideSpec, deck);
  }

  await fs.mkdir(path.dirname(outputPath), { recursive: true });
  await pptx.writeFile({ fileName: outputPath });
}

function renderSlide(slide: PptxGenJS.Slide, slideSpec: SlideSpec, deck: DeckSpec) {
  for (const block of slideSpec.blocks) {
    renderBlock(slide, block, deck);
  }
}

function renderBlock(slide: PptxGenJS.Slide, block: BlockSpec, deck: DeckSpec) {
  const { x, y, w, h } = block.frame;

  if (block.kind === "text") {
    slide.addText(block.content.text, {
      x,
      y,
      w,
      h,
      fontSize: block.content.level === "h1" ? 28 : block.content.level === "h2" ? 20 : 14,
      bold: block.content.level === "h1" || block.content.level === "h2",
      color: normalizeColor(deck.theme.palette.text),
      valign: "mid"
    });
    return;
  }

  if (block.kind === "shape") {
    slide.addShape(shapeType(block.content.shape), {
      x,
      y,
      w,
      h,
      line: { color: normalizeColor(deck.theme.palette.primary), pt: 1.2 },
      fill: { color: normalizeColor(deck.theme.palette.background), transparency: 15 }
    });
    slide.addText(block.content.label, {
      x: x + 0.1,
      y: y + 0.1,
      w: Math.max(w - 0.2, 0.3),
      h: Math.max(h - 0.2, 0.3),
      fontSize: 14,
      color: normalizeColor(deck.theme.palette.text),
      align: "center",
      valign: "mid"
    });
    return;
  }

  if (block.kind === "image") {
    slide.addImage({
      path: block.content.src,
      x,
      y,
      w,
      h
    });
    return;
  }

  slide.addText(`[${block.kind}]`, {
    x,
    y,
    w,
    h,
    fontSize: 12,
    color: normalizeColor(deck.theme.palette.secondary),
    align: "center",
    valign: "mid"
  });
}

function shapeType(shape: string): string {
  switch (shape) {
    case "rounded_rect":
      return "roundRect";
    case "line":
      return "line";
    case "arrow":
      return "chevron";
    default:
      return "rect";
  }
}

function normalizeColor(color: string) {
  return color.replace("#", "").toUpperCase();
}
