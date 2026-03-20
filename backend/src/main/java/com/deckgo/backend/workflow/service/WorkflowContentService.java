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

    public JsonNode generateBackgroundSummary(ProjectEntity project) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("summary", "当前主题是“" + project.getTopic() + "”。需要先解释它是什么、为什么值得讲，以及这份演示希望让听众带走什么。");
        root.put("topicUnderstanding", "这是一个需要先做背景介绍、再进入核心内容的主题。");
        ArrayNode sources = root.putArray("sources");
        sources.add(source("背景定义", "about:" + project.getTopic(), "基于主题生成的本地背景摘要。"));
        return root;
    }

    public JsonNode generateDiscoveryCard(ProjectEntity project, JsonNode backgroundSummary) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("title", "先确认几个关键问题");
        root.put("description", backgroundSummary.path("summary").asText("先补齐需求边界，再继续往大纲和页面策划推进。"));
        root.put("freeformHint", "如果你还有特别要求，可以继续补充，例如必须出现的内容、想突出哪一部分。");

        ArrayNode questions = root.putArray("questions");
        questions.add(question(
            "page-count",
            "你希望最终页数大概落在哪个区间？",
            List.of(
                option("count-5-10", "5-10", "适合短汇报或快速说明"),
                option("count-10-15", "10-15", "适合标准结构化介绍"),
                option("count-15-20", "15-20", "适合完整讲解和深入展开"),
                option("count-free", "自由发挥", "由系统按内容判断")
            )
        ));
        questions.add(question(
            "usage-scenario",
            "这份 PPT 主要会用在什么场景？",
            scenarioOptions(project.getTopic())
        ));
        return root;
    }

    public JsonNode generateOutline(ProjectEntity project, JsonNode backgroundSummary, JsonNode discoveryAnswers) {
        String title = deriveProjectTitle(project.getTopic());
        String pageCount = selectedDiscoveryValue(discoveryAnswers, "page-count");
        int targetPages = switch (pageCount) {
            case "count-5-10" -> 6;
            case "count-10-15" -> 10;
            case "count-15-20" -> 14;
            default -> 8;
        };

        ObjectNode root = objectMapper.createObjectNode();
        root.put("title", title);
        root.put("narrative", backgroundSummary.path("summary").asText("先介绍背景，再展开核心内容，最后给出结论。"));
        ArrayNode sections = root.putArray("sections");

        ArrayNode introPages = objectMapper.createArrayNode()
            .add(page("page-1", title, "用封面建立主题和语境"))
            .add(page("page-2", "这到底是什么", "用简明方式解释主题的基本背景"));
        sections.add(section("section-1", "背景与认知", introPages));

        ArrayNode corePages = objectMapper.createArrayNode()
            .add(page("page-3", "为什么值得关注", "说明它的价值、意义或当前变化"))
            .add(page("page-4", "核心结构与关键点", "拆出最重要的部分或机制"))
            .add(page("page-5", "关键细节展开", "展开一到两个最值得看的重点"));
        if (targetPages >= 10) {
            corePages.add(page("page-6", "进阶内容与案例", "补充案例、对比或更深入的说明"));
            corePages.add(page("page-7", "风险与限制", "补充边界、难点或现实限制"));
        }
        sections.add(section("section-2", "核心内容", corePages));

        ArrayNode closingPages = objectMapper.createArrayNode()
            .add(page("page-8", "总结与下一步", "收束前文并给出行动建议"));
        if (targetPages >= 14) {
            closingPages.add(page("page-9", "附录或延伸阅读", "放补充内容和延伸方向"));
        }
        sections.add(section("section-3", "收束", closingPages));
        return root;
    }

    public JsonNode reviseOutline(ProjectEntity project, JsonNode backgroundSummary, JsonNode discoveryAnswers, JsonNode currentOutline, String feedback) {
        ObjectNode revised = currentOutline.deepCopy();
        revised.put("narrative", currentOutline.path("narrative").asText("") + " 用户补充要求：" + feedback);
        ArrayNode sections = (ArrayNode) revised.withArray("sections");
        if (!sections.isEmpty() && sections.get(0).isObject()) {
            ((ObjectNode) sections.get(0)).put("revisionNote", feedback);
        }
        return revised;
    }

    public JsonNode generatePageResearch(ProjectEntity project, JsonNode outline) {
        ArrayNode pages = objectMapper.createArrayNode();
        for (JsonNode section : outline.path("sections")) {
            for (JsonNode page : section.path("pages")) {
                ObjectNode item = objectMapper.createObjectNode();
                item.put("pageId", page.path("id").asText(UUID.randomUUID().toString()));
                item.put("title", page.path("title").asText(""));
                item.put("needsSearch", !page.path("title").asText("").contains("封面") && !page.path("title").asText("").contains("总结"));
                item.put("searchIntent", "为这一页补充可靠背景、案例或事实支撑。");
                ArrayNode queries = item.putArray("queries");
                queries.add(page.path("title").asText(project.getTopic()));
                item.put("searchDepth", "basic");
                item.put("findings", "当前使用 fallback，后续可由检索结果补齐。");
                item.putArray("sources");
                pages.add(item);
            }
        }
        return pages;
    }

    public List<JsonNode> generatePagePlans(ProjectEntity project, JsonNode outline, JsonNode pageResearch) {
        List<JsonNode> pagePlans = new ArrayList<>();
        int index = 0;
        for (JsonNode pageResearchItem : pageResearch) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("pageId", pageResearchItem.path("pageId").asText(UUID.randomUUID().toString()));
            node.put("title", pageResearchItem.path("title").asText("未命名页面"));
            node.put("goal", pageResearchItem.path("findings").asText("解释当前页面的核心意图"));
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
            cards.add(card("card-1", "highlight", node.path("title").asText("核心标题"), node.path("goal").asText("页面目标"), null, null, null));
            cards.add(card("card-2", "text", "核心说明", "用一段正文承接这一页的关键内容。", null, null, null));

            if (pageResearchItem.path("title").asText("").contains("对比")) {
                cards.add(card("card-3", "comparison", "对比结构", "这一部分需要放对比块。", null, null, null));
            } else if (pageResearchItem.path("title").asText("").contains("时间")) {
                cards.add(card("card-3", "timeline", "时间线", "这一部分适合做时间线。", null, null, null));
            } else if (index % 3 == 0) {
                cards.add(card("card-3", "chart", "图表区域", "这里建议放一张图表来帮助理解。", "bar", null, null));
            } else if (index % 3 == 1) {
                cards.add(card("card-3", "table", "表格区域", "这里建议放一张表格来组织信息。", null, List.of("字段", "说明", "结论"), null));
            } else {
                cards.add(card("card-3", "image", "图片区域", "这里建议放一张说明性图片。", null, null, "表现主题的关键场景或对象"));
            }

            pagePlanSchemaService.validate(node);
            pagePlans.add(node);
            index++;
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
            if (card.hasNonNull("chartType")) {
                svg.append(text(frame.x() + 24, frame.y() + frame.h() - 24, 14, false, accent, "图表：" + escapeXml(card.path("chartType").asText())));
            }
            if (card.path("kind").asText("").equals("image") && card.hasNonNull("imageIntent")) {
                svg.append(text(frame.x() + 24, frame.y() + frame.h() - 24, 14, false, accent, "图片：" + escapeXml(card.path("imageIntent").asText())));
            }
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

    private List<ObjectNode> scenarioOptions(String topic) {
        String normalized = topic == null ? "" : topic.toLowerCase();
        if (normalized.contains("介绍") || normalized.contains("产品")) {
            return List.of(
                option("scene-presentation", "正式汇报", "用于管理层或客户介绍"),
                option("scene-demo", "演示讲解", "用于现场讲解和展示"),
                option("scene-training", "教学说明", "用于培训或教程说明")
            );
        }

        return List.of(
            option("scene-share", "主题分享", "适合分享和介绍"),
            option("scene-report", "结构汇报", "适合正式表达和汇报"),
            option("scene-explain", "说明讲解", "适合一步步解释内容")
        );
    }

    private String selectedDiscoveryValue(JsonNode discoveryAnswers, String questionId) {
        for (JsonNode selected : discoveryAnswers.path("selectedOptionIds")) {
            String value = selected.asText();
            if (value.startsWith(questionId)) {
                return value;
            }
        }
        return "";
    }

    private ObjectNode source(String title, String url, String content) {
        ObjectNode source = objectMapper.createObjectNode();
        source.put("title", title);
        source.put("url", url);
        source.put("content", content);
        return source;
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

    private ObjectNode section(String id, String title, ArrayNode pages) {
        ObjectNode section = objectMapper.createObjectNode();
        section.put("id", id);
        section.put("title", title);
        section.set("pages", pages);
        return section;
    }

    private ObjectNode page(String id, String title, String intent) {
        ObjectNode page = objectMapper.createObjectNode();
        page.put("id", id);
        page.put("title", title);
        page.put("intent", intent);
        return page;
    }

    private ObjectNode card(
        String id,
        String kind,
        String heading,
        String body,
        String chartType,
        List<String> tableHeaders,
        String imageIntent
    ) {
        ObjectNode card = objectMapper.createObjectNode();
        card.put("id", id);
        card.put("kind", kind);
        card.put("heading", heading);
        card.put("body", body);
        card.put("emphasis", "medium");
        if (chartType != null) {
            card.put("chartType", chartType);
        }
        if (tableHeaders != null && !tableHeaders.isEmpty()) {
            ArrayNode headers = card.putArray("tableHeaders");
            tableHeaders.forEach(headers::add);
        }
        if (imageIntent != null) {
            card.put("imageIntent", imageIntent);
        }
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
