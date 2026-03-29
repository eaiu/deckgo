package com.deckgo.backend.ai.service;

import com.deckgo.backend.common.config.DeckGoProperties;
import com.deckgo.backend.template.service.TemplateCatalogService;
import com.deckgo.backend.studio.service.WorkflowContentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class SvgDesignAgentService extends AbstractWorkflowAgentService {

    private static final Pattern SVG_PATTERN = Pattern.compile("(?s)<svg\\b.*?</svg>");
    private static final Pattern VIEW_BOX_PATTERN = Pattern.compile("viewBox\\s*=\\s*['\"]0\\s+0\\s+1280\\s+720['\"]");

    private static final String SYSTEM_PROMPT = """
        你是一位精通信息架构与 SVG 编码的演示设计专家。

        ## 任务
        将页面策划稿升级为最终设计稿 SVG。在草稿的布局和文字基础上，添加：
        1. 真实图表渲染（柱状图带具体数值、刻度、网格线）
        2. 图片区域用渐变 + 装饰性几何图形组合，创造视觉丰富的占位
        3. 视觉精修：卡片阴影、更精致的排版、色彩层次

        ## SVG 规范
        - viewBox 必须是 0 0 1280 720
        - font-family="system-ui, 'Noto Sans SC', sans-serif"
        - 全局背景 fill=#F8FAFC

        ## 便当网格 (Bento Grid) 布局
        灵活的卡片网格，布局由信息结构驱动：
        - 卡片数量 2-6 个
        - 卡片间距 20px，页面边距 40px
        - 布局组合：两栏非对称(65%/35%)、四宫格(2×2)、上下分层、混合自由组合
        - 卡片样式：fill=#FFFFFF stroke=1px #E2E8F0 rx=12
        - 用 <filter> 添加 drop-shadow(0 2px 8px rgba(0,0,0,0.06))
        - 强调卡片可用深色背景 #1E293B + 白字 + 彩色要点

        ## 图表精修 (chart 类型)
        - bar: 用 <rect> 画柱状图，每根柱子标注实际数值（如 85%、92%）
          - Y 轴有刻度线和标签，柱子用主色 #2563EB 填充
          - 数值标注在柱子顶部
        - line: <polyline> 折线 + 数据点圆圈
        - pie: <circle> + stroke-dasharray 扇形

        ## 图片精修 (image 类型)
        - 用渐变矩形 + 装饰性几何图形组合代替灰色占位
        - 或用 clip-path + 抽象图形创造视觉丰富区域

        ## 流程/架构图
        - <rect> 画模块框 + <text> 填标签 + <line>/<path> 画箭头
        - 高亮模块: fill=#2563EB + 白字
        - 箭头: stroke=#94A3B8 stroke-width=1.5 marker-end
        - 连接线保持水平/垂直，不要斜线

        ## 文字排版
        - 页面主标题: font-size=28 font-weight=bold fill=#1E293B，左侧加 4px 宽蓝色竖线
        - 卡片标题: font-size=22 font-weight=bold fill=#1E293B
        - 正文: font-size=16 fill=#475569 行间距 dy=24
        - 强调: fill=#2563EB
        - 要点列表: 前置 r=4 彩色圆点，标题粗体 + 说明细体
        - 数据指标: 数字 font-size=36 bold fill=#2563EB
        - 中文每行不超过 28 字，用 <tspan x=... dy=24> 换行

        ## 输出
        - 只输出 <svg>...</svg>
        - 不要 Markdown 或解释
        - 所有文字必须完整呈现
        """;

    private static final String DRAFT_SYSTEM_PROMPT = """
        你是一位精通信息架构与 SVG 编码的演示设计专家。

        ## 任务
        将页面策划稿转化为一张内容完备的 SVG 演示草稿页。
        草稿 = 与最终版完全相同的布局结构 + 全部真实文字 + 简化的图表草图和图片占位。
        目标：审阅者能看到最终页面的完整信息架构和文字内容，只差图片素材和图表精修。

        ## SVG 规范
        - viewBox 必须是 0 0 1280 720
        - font-family="system-ui, 'Noto Sans SC', sans-serif"

        ## 便当网格 (Bento Grid) 布局
        用灵活的卡片网格组织内容，布局由信息结构驱动：
        - 卡片数量 2-6 个
        - 卡片间距 20px，页面边距 40px
        - 布局组合：
          - 两栏非对称 (约 65%/35%): 主内容 + 辅助信息
          - 四宫格 (2×2): 四个并列主题
          - 上下分层: 顶部大卡 + 底部 2-3 个小卡
          - 混合: 自由组合不同尺寸

        ## 卡片样式
        - 普通卡片: fill=#FFFFFF stroke=1px #E2E8F0 rx=12，内边距 24px
        - 强调卡片: fill=#1E293B (深色背景) + fill=#FFFFFF 白字 + 彩色要点指示器
        - 高亮信息框: fill=#F1F5F9 rx=8 内嵌在卡片中，用于突出关键结论

        ## 文字排版（关键：所有文字必须完整呈现）
        - 页面主标题: font-size=28 font-weight=bold fill=#1E293B
          - 标题左侧画一个 4px 宽 28px 高的蓝色竖线装饰 (fill=#2563EB)
        - 卡片标题: font-size=20 font-weight=bold fill=#1E293B
        - 正文段落: font-size=15 fill=#475569
          - 中文排版：每行不超过 28 个字符
          - 用 <tspan x=[卡片x+24] dy=22> 实现换行
        - 强调文字: fill=#2563EB font-weight=bold
        - 辅助标签/副标题: font-size=13 fill=#94A3B8

        ## 流程/架构图 (comparison/timeline 类型)
        当内容描述流程或架构时，用 SVG 图形绘制：
        - 模块框: <rect> rx=8 fill=#FFFFFF stroke=1px #E2E8F0 (普通) 或 fill=#2563EB (高亮)
        - 模块标签: <text> 居中
        - 箭头连线: <line> stroke=#CBD5E1 stroke-width=1.5
          - 箭头用 <defs><marker id="arrow"><path d="M0,0 L8,4 L0,8" fill=#CBD5E1/></marker></defs>
          - 保持水平或垂直连接，不要斜线

        ## 图表草图 (chart 类型)
        画简化的图表草图，传达数据趋势：
        - bar chart: 画 4-6 根矩形柱子，高度按相对比例
          - 柱子 fill=#2563EB（主柱）或 fill=#E2E8F0（辅柱）
          - 下方标注分类名 font-size=12
          - 上方标注数值（如 85%）font-size=12 font-weight=bold
        - 不需要精确刻度线，只需视觉表意

        ## 图片占位 (image 类型)
        - 画矩形: fill=#F1F5F9 stroke=#E2E8F0 stroke-dasharray="6 4" rx=8
        - 居中标注: font-size=13 fill=#94A3B8 写明 imageIntent 内容

        ## 要点列表
        - 每个要点前画 r=4 的圆形指示器
          - 用不同颜色区分：#2563EB（蓝）、#10B981（绿）、#EF4444（红）
        - 要点标题: font-size=15 font-weight=bold fill=#1E293B
        - 要点说明: font-size=13 fill=#64748B

        ## 数据指标 (metric 类型)
        - 数字: font-size=36 font-weight=bold fill=#2563EB
        - 单位/说明: font-size=13 fill=#64748B

        ## 绝对禁止
        - 不允许省略或截断任何文字内容
        - 不允许用 "..." 或 "（省略）" 替代真实文字
        - 不允许输出 Markdown、注释或解释文字

        ## 输出
        只输出一个完整的 <svg>...</svg>
        """;

    private final WorkflowContentService workflowContentService;
    private final TemplateCatalogTools templateCatalogTools;
    private final TemplateCatalogService templateCatalogService;

    public SvgDesignAgentService(
        WorkflowAgentClientFactory workflowAgentClientFactory,
        DeckGoProperties properties,
        ObjectMapper objectMapper,
        WorkflowContentService workflowContentService,
        TemplateCatalogTools templateCatalogTools,
        TemplateCatalogService templateCatalogService
    ) {
        super(workflowAgentClientFactory, properties, objectMapper);
        this.workflowContentService = workflowContentService;
        this.templateCatalogTools = templateCatalogTools;
        this.templateCatalogService = templateCatalogService;
    }

    public String generateDraftSvg(JsonNode pagePlan, JsonNode pageResearch, String templateId) {
        return useAgentOrFallback(
            "DraftSvgAgent",
            properties.getAi().getWorkflow().getSvgDesign(),
            chatClient -> {
                String response = chatClient.prompt()
                    .system(DRAFT_SYSTEM_PROMPT)
                    .user("""
                        页面策划稿：
                        %s

                        当前页 research：
                        %s

                        模板样式 token：
                        %s

                        请输出内容完备的草稿 SVG。所有文字必须完整呈现，不允许占位符。
                        """.formatted(asJson(pagePlan), asJson(pageResearch), asJson(templateCatalogService.getTemplate(templateId))))
                    .tools(templateCatalogTools)
                    .call()
                    .content();

                String svg = extractSvg(response);
                if (!VIEW_BOX_PATTERN.matcher(svg).find()) {
                    throw new IllegalStateException("SVG 缺少固定 viewBox");
                }
                return svg;
            },
            () -> workflowContentService.renderDraftSvg(pagePlan, templateId)
        );
    }

    public String generateFinalSvg(JsonNode pagePlan, JsonNode pageResearch, String templateId) {
        return useAgentOrFallback(
            "SvgDesignAgent",
            properties.getAi().getWorkflow().getSvgDesign(),
            chatClient -> {
                String response = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user("""
                        页面策划稿：
                        %s

                        当前页 research：
                        %s

                        模板样式 token：
                        %s

                        请输出最终 SVG。
                        """.formatted(asJson(pagePlan), asJson(pageResearch), asJson(templateCatalogService.getTemplate(templateId))))
                    .tools(templateCatalogTools)
                    .call()
                    .content();

                String svg = extractSvg(response);
                if (!VIEW_BOX_PATTERN.matcher(svg).find()) {
                    throw new IllegalStateException("SVG 缺少固定 viewBox");
                }
                return svg;
            },
            () -> workflowContentService.renderFinalSvg(pagePlan, templateId)
        );
    }

    private String extractSvg(String response) {
        if (response == null || response.isBlank()) {
            throw new IllegalStateException("SVG 输出为空");
        }

        Matcher matcher = SVG_PATTERN.matcher(response);
        if (!matcher.find()) {
            throw new IllegalStateException("未提取到合法 SVG");
        }
        return matcher.group();
    }
}
