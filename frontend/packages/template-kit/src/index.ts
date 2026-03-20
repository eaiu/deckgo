import type { TemplateSummary } from "@deckgo/deck-core";

export const TEMPLATE_CATALOG: TemplateSummary[] = [
  {
    id: "clarity-blue",
    name: "Clarity Blue",
    description: "适合通用汇报的清晰蓝色商务模板。",
    slideKinds: ["title", "agenda", "content", "summary"],
    defaultTheme: {
      primary: "#0F5FFF",
      secondary: "#1A2A52",
      accent: "#00C2A8",
      background: "#F7F9FC",
      text: "#172033"
    }
  },
  {
    id: "paper-grid",
    name: "Paper Grid",
    description: "适合教学与学习型文稿的纸面网格模板。",
    slideKinds: ["title", "content", "comparison", "summary"],
    defaultTheme: {
      primary: "#335C67",
      secondary: "#FFF3B0",
      accent: "#E09F3E",
      background: "#FFFCF2",
      text: "#2B2D42"
    }
  },
  {
    id: "studio-dark",
    name: "Studio Dark",
    description: "适合产品演示和路线图展示的深色模板。",
    slideKinds: ["title", "timeline", "content", "summary"],
    defaultTheme: {
      primary: "#F95738",
      secondary: "#EE964B",
      accent: "#FAF0CA",
      background: "#0D1321",
      text: "#F5F7FA"
    }
  }
];

export function findTemplate(templateId: string): TemplateSummary | undefined {
  return TEMPLATE_CATALOG.find((template) => template.id === templateId);
}
