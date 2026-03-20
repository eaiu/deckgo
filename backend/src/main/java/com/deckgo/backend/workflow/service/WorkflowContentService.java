package com.deckgo.backend.workflow.service;

import com.deckgo.backend.project.entity.ProjectEntity;
import com.deckgo.backend.template.dto.TemplateSummary;
import com.deckgo.backend.template.service.TemplateCatalogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WorkflowContentService {

    private static final String DEFAULT_TEMPLATE_ID = "clarity-blue";

    private final ObjectMapper objectMapper;
    private final TemplateCatalogService templateCatalogService;
    private final PagePlanSchemaService pagePlanSchemaService;

    public WorkflowContentService(
        ObjectMapper objectMapper,
        TemplateCatalogService templateCatalogService,
        PagePlanSchemaService pagePlanSchemaService
    ) {
        this.objectMapper = objectMapper;
        this.templateCatalogService = templateCatalogService;
        this.pagePlanSchemaService = pagePlanSchemaService;
    }

    public String deriveTemplateId(String prompt) {
        String normalized = prompt == null ? "" : prompt.toLowerCase();
        if (normalized.contains("教学") || normalized.contains("课程") || normalized.contains("培训")) {
            return "paper-grid";
        }
        if (normalized.contains("产品") || normalized.contains("路线图") || normalized.contains("roadmap") || normalized.contains("发布")) {
            return "studio-dark";
        }
        return DEFAULT_TEMPLATE_ID;
    }

    public String deriveProjectTitle(String prompt) {
        String cleaned = prompt == null ? "新的演示文稿" : prompt.trim().replaceAll("\\s+", " ");
        if (cleaned.isBlank()) {
            return "新的演示文稿";
        }
        return cleaned.length() > 28 ? cleaned.substring(0, 28) : cleaned;
    }

    public JsonNode generateDiscoveryCard(ProjectEntity project) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("title", "先确认几个关键问题");
        root.put("description", "我会先补齐需求边界，再继续往大纲和页面策划推进。");
        root.put("freeformHint", "如果你还有特别要求，可以继续补充，例如页数、必须出现的内容、避免的内容。");

        ArrayNode questions = root.putArray("questions");
        questions.add(question(
            "audience-focus",
            "这份 PPT 更主要是给谁看？",
            List.of(
                option("management", "管理层", "偏结论和决策"),
                option("customer", "客户/外部对象", "偏价值和说服"),
                option("team", "内部团队", "偏执行和协作")
            )
        ));
        questions.add(question(
            "comparison",
            "是否需要加入竞品或对比分析？",
            List.of(
                option("comparison-yes", "需要对比", "加入竞品或替代方案视角"),
                option("comparison-lite", "少量对比", "保留一页或一段对比"),
                option("comparison-no", "不需要", "聚焦自身内容")
            )
        ));
        questions.add(question(
            "tone",
            "你更希望整体表达风格偏哪一类？",
            List.of(
                option("formal", "正式商务", "偏清晰、稳重"),
                option("teaching", "教学说明", "偏结构化和解释"),
                option("storytelling", "故事化提案", "偏说服和节奏")
            )
        ));
        return root;
    }

    public JsonNode generateResearchSummary(ProjectEntity project, JsonNode discoveryAnswers) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("audience", inferAudience(discoveryAnswers));
        root.put("summary", "这份演示文稿将围绕“" + project.getTopic() + "”展开，先建立问题背景，再呈现核心观点，最后给出可执行结论。");
        root.put("suggestedTemplateId", deriveTemplateId(project.getTopic()));
        root.put("titleSuggestion", deriveProjectTitle(project.getTopic()));

        ArrayNode assumptions = root.putArray("assumptions");
        assumptions.add("当前阶段不接真实外部搜索，资料整理以用户输入和上下文推导为主。");
        assumptions.add("演示目标是让受众快速理解主题，并给出下一步行动建议。");

        ArrayNode comparisonPoints = root.putArray("comparisonPoints");
        if (containsSelection(discoveryAnswers, "comparison-yes") || containsSelection(discoveryAnswers, "comparison-lite")) {
            comparisonPoints.add("加入竞品或替代方案对比");
            comparisonPoints.add("明确自身优势与取舍");
        } else {
            comparisonPoints.add("不以竞品对比为主，突出自身主张");
        }

        ArrayNode keyFindings = root.putArray("keyFindings");
        keyFindings.add("需要先讲清楚问题背景，再进入方案内容。");
        keyFindings.add("适合使用章节化叙事和卡片式信息组织。");
        keyFindings.add("结尾页需要明确收束并给出下一步动作。");
        return root;
    }

    public JsonNode generateOutline(ProjectEntity project, JsonNode researchSummary) {
        ObjectNode root = objectMapper.createObjectNode();
        String title = researchSummary.path("titleSuggestion").asText(deriveProjectTitle(project.getTopic()));
        root.put("title", title);
        root.put("narrative", "先建立背景，再给出核心观点，最后落到行动建议。");

        ArrayNode sections = root.putArray("sections");
        sections.add(section("section-1", "背景与目标", List.of(
            page("page-1", title, "用封面快速建立主题和语境"),
            page("page-2", "为什么现在要关注这个问题", "说明背景、机会或痛点")
        )));
        sections.add(section("section-2", "核心内容", List.of(
            page("page-3", "关键观点与结构", "用结构化页面解释核心逻辑"),
            page("page-4", "重点内容展开", "展示支撑观点的要点或对比")
        )));
        sections.add(section("section-3", "总结与下一步", List.of(
            page("page-5", "结论与行动建议", "收束前文并给出下一步")
        )));
        return root;
    }

    public JsonNode reviseOutline(ProjectEntity project, JsonNode currentOutline, String feedback) {
        ObjectNode revised = currentOutline.deepCopy();
        revised.put("narrative", currentOutline.path("narrative").asText("") + " 用户补充要求：" + feedback);
        ArrayNode sections = (ArrayNode) revised.withArray("sections");
        if (!sections.isEmpty() && sections.get(0).isObject()) {
            ((ObjectNode) sections.get(0)).put("revisionNote", feedback);
        }
        return revised;
    }

    public List<JsonNode> generatePagePlans(ProjectEntity project, JsonNode outline) {
        List<JsonNode> pagePlans = new ArrayList<>();
        int index = 0;
        for (JsonNode section : outline.path("sections")) {
            for (JsonNode page : section.path("pages")) {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("pageId", page.path("id").asText(UUID.randomUUID().toString()));
                node.put("title", page.path("title").asText("未命名页面"));
                node.put("goal", page.path("intent").asText("解释当前页面的核心意图"));
                node.put("layout", switch (index % 5) {
                    case 0 -> "hero";
                    case 1 -> "two-column";
                    case 2 -> "bento-grid";
                    case 3 -> "comparison";
                    default -> "summary";
                });
                node.put("visualTone", "clean");
                node.put("speakerNotes", "围绕主题“" + project.getTopic() + "”展开。");

                ArrayNode cards = node.putArray("cards");
                cards.add(card("card-1", "highlight", page.path("title").asText("核心标题"), page.path("intent").asText("解释当前页面的核心意图")));
                cards.add(card("card-2", "text", "关键点一", "用一句完整的话补充这页最重要的说明。"));
                if (index % 5 != 0) {
                    cards.add(card("card-3", "text", "关键点二", "用第二个卡片承载补充信息、数据或对比。"));
                }

                pagePlanSchemaService.validate(node);
                pagePlans.add(node);
                index++;
            }
        }
        return pagePlans;
    }

    public String renderDraftSvg(JsonNode pagePlan, String templateId) {
        TemplateSummary template = templateCatalogService.getTemplate(templateId);
        return renderPageSvg(pagePlan, template.defaultTheme(), false);
    }

    public String renderFinalSvg(JsonNode pagePlan, String templateId) {
        TemplateSummary template = templateCatalogService.getTemplate(templateId);
        return renderPageSvg(pagePlan, template.defaultTheme(), true);
    }

    private String renderPageSvg(JsonNode pagePlan, JsonNode palette, boolean polished) {
        String background = polished ? "#FFFFFF" : "#FAFBFC";
        String surface = "#FFFFFF";
        String border = polished ? "#D7DDE5" : "#111827";
        String accent = palette.path("primary").asText("#2563EB");
        String ink = "#111827";
        String muted = "#667085";

        List<Frame> frames = computeFrames(pagePlan.path("layout").asText("bento-grid"), pagePlan.path("cards").size());
        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1280 720\" width=\"1280\" height=\"720\">");
        svg.append(rect(0, 0, 1280, 720, 0, background, "none", 0));
        svg.append(rect(40, 34, 1200, 652, 34, surface, border, polished ? 1.2 : 2.4));
        svg.append(text(88, 108, 44, true, ink, escapeXml(pagePlan.path("title").asText("未命名页面"))));
        svg.append(text(88, 150, 18, false, muted, escapeXml(pagePlan.path("goal").asText(""))));

        ArrayNode cards = (ArrayNode) pagePlan.path("cards");
        for (int index = 0; index < cards.size(); index++) {
            JsonNode card = cards.get(index);
            Frame frame = frames.get(Math.min(index, frames.size() - 1));
            svg.append(rect(frame.x(), frame.y(), frame.w(), frame.h(), 24, polished && index == 0 ? "#F7FAFF" : "#FFFFFF", border, polished ? 1.2 : 2.0));
            if (polished && index == 0) {
                svg.append(rect(frame.x(), frame.y(), frame.w(), 8, 24, accent, "none", 0));
            }
            svg.append(text(frame.x() + 24, frame.y() + 42, 24, true, ink, escapeXml(card.path("heading").asText("卡片"))));
            svg.append(multilineText(frame.x() + 24, frame.y() + 82, frame.w() - 48, 16, muted, card.path("body").asText("")));
        }

        if (polished) {
            svg.append(rect(88, 620, 180, 12, 6, accent, "none", 0));
            svg.append(text(88, 612, 14, false, muted, "DeckGo SVG-first"));
        }

        svg.append("</svg>");
        return svg.toString();
    }

    private List<Frame> computeFrames(String layout, int count) {
        List<Frame> frames = new ArrayList<>();
        switch (layout) {
            case "hero" -> frames.add(new Frame(88, 198, 1104, 394));
            case "two-column" -> {
                frames.add(new Frame(88, 198, 528, 394));
                frames.add(new Frame(664, 198, 528, 394));
            }
            case "three-column", "comparison" -> {
                frames.add(new Frame(88, 198, 344, 394));
                frames.add(new Frame(468, 198, 344, 394));
                frames.add(new Frame(848, 198, 344, 394));
            }
            case "summary" -> {
                frames.add(new Frame(88, 198, 720, 394));
                frames.add(new Frame(844, 198, 348, 184));
                frames.add(new Frame(844, 408, 348, 184));
            }
            default -> {
                frames.add(new Frame(88, 198, 720, 184));
                frames.add(new Frame(844, 198, 348, 184));
                frames.add(new Frame(88, 408, 528, 184));
                frames.add(new Frame(652, 408, 540, 184));
            }
        }

        while (frames.size() < Math.max(1, count)) {
            frames.add(frames.get(frames.size() - 1));
        }
        return frames;
    }

    private String rect(int x, int y, int w, int h, int radius, String fill, String stroke, double strokeWidth) {
        StringBuilder builder = new StringBuilder();
        builder.append("<rect x=\"").append(x).append("\" y=\"").append(y).append("\" width=\"").append(w).append("\" height=\"").append(h).append("\"");
        if (radius > 0) {
            builder.append(" rx=\"").append(radius).append("\" ry=\"").append(radius).append("\"");
        }
        builder.append(" fill=\"").append(fill).append("\"");
        if (stroke != null && !stroke.isBlank() && strokeWidth > 0) {
            builder.append(" stroke=\"").append(stroke).append("\" stroke-width=\"").append(strokeWidth).append("\"");
        }
        builder.append("/>");
        return builder.toString();
    }

    private String text(int x, int y, int size, boolean bold, String color, String value) {
        return "<text x=\"" + x + "\" y=\"" + y + "\" font-family=\"'IBM Plex Sans','Noto Sans SC',sans-serif\" font-size=\"" + size
            + "\" font-weight=\"" + (bold ? "700" : "400") + "\" fill=\"" + color + "\">" + value + "</text>";
    }

    private String multilineText(int x, int y, int width, int lineHeight, String color, String value) {
        String[] parts = wrapText(value, Math.max(width / 14, 12));
        StringBuilder builder = new StringBuilder();
        builder.append("<text x=\"").append(x).append("\" y=\"").append(y).append("\" font-family=\"'IBM Plex Sans','Noto Sans SC',sans-serif\" font-size=\"16\" fill=\"").append(color).append("\">");
        for (int i = 0; i < parts.length; i++) {
            builder.append("<tspan x=\"").append(x).append("\" dy=\"").append(i == 0 ? 0 : lineHeight).append("\">")
                .append(escapeXml(parts[i]))
                .append("</tspan>");
        }
        builder.append("</text>");
        return builder.toString();
    }

    private String[] wrapText(String value, int chunkLength) {
        String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            return new String[] {" "};
        }
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String token : normalized.split(" ")) {
            if (current.length() + token.length() + 1 > chunkLength && current.length() > 0) {
                lines.add(current.toString());
                current = new StringBuilder(token);
            } else {
                if (current.length() > 0) {
                    current.append(' ');
                }
                current.append(token);
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines.toArray(String[]::new);
    }

    private boolean containsSelection(JsonNode discoveryAnswers, String value) {
        for (JsonNode selected : discoveryAnswers.path("selectedOptionIds")) {
            if (value.equals(selected.asText())) {
                return true;
            }
        }
        return false;
    }

    private String inferAudience(JsonNode discoveryAnswers) {
        if (containsSelection(discoveryAnswers, "management")) {
            return "管理层";
        }
        if (containsSelection(discoveryAnswers, "customer")) {
            return "客户";
        }
        if (containsSelection(discoveryAnswers, "team")) {
            return "内部团队";
        }
        return "待确认";
    }

    private ObjectNode question(String id, String prompt, List<ObjectNode> options) {
        ObjectNode question = objectMapper.createObjectNode();
        question.put("id", id);
        question.put("prompt", prompt);
        ArrayNode array = question.putArray("options");
        options.forEach(array::add);
        return question;
    }

    private ObjectNode option(String id, String label, String description) {
        ObjectNode option = objectMapper.createObjectNode();
        option.put("id", id);
        option.put("label", label);
        option.put("description", description);
        return option;
    }

    private ObjectNode section(String id, String title, List<ObjectNode> pages) {
        ObjectNode section = objectMapper.createObjectNode();
        section.put("id", id);
        section.put("title", title);
        ArrayNode pageArray = section.putArray("pages");
        pages.forEach(pageArray::add);
        return section;
    }

    private ObjectNode page(String id, String title, String intent) {
        ObjectNode page = objectMapper.createObjectNode();
        page.put("id", id);
        page.put("title", title);
        page.put("intent", intent);
        return page;
    }

    private ObjectNode card(String id, String kind, String heading, String body) {
        ObjectNode card = objectMapper.createObjectNode();
        card.put("id", id);
        card.put("kind", kind);
        card.put("heading", heading);
        card.put("body", body);
        card.put("emphasis", "medium");
        return card;
    }

    private String escapeXml(String value) {
        return value == null ? "" : value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }

    private record Frame(int x, int y, int w, int h) {
    }
}
