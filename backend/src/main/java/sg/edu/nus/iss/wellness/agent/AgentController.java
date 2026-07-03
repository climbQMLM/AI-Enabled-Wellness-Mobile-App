package sg.edu.nus.iss.wellness.agent;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.iss.wellness.agent.dto.RecommendationResponse;
import sg.edu.nus.iss.wellness.user.User;

import java.time.LocalDate;
import java.util.List;

/**
 *
 * 对应 PROJECT_SPEC.md §4 Agentic 接口：
 *   POST /api/agent/run           — 手动触发 agent，生成并返回当天建议
 *   GET  /api/recommendations     — 查询历史建议（可选 from/to 过滤，默认近 30 天）
 */
@RestController
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    /**
     * 手动触发 agentic 工作流：
     *   - 读近 7 天 wellness_logs
     *   - 分析趋势、规则判断状态
     *   - 调 Ollama 生成个性化建议
     *   - 存入 recommendations 表，返回本次结果
     *
     * 请求体为空（POST 仅作为触发信号），返回生成的 recommendation。
     * Ollama 推理可能耗时较长（10-60s），前端建议展示 loading 状态。
     */
    @PostMapping("/api/agent/run")
    public RecommendationResponse run(@AuthenticationPrincipal User user) {
        return agentService.runForUser(user.getId());
    }

    /**
     * 查询当前用户的历史建议记录，按日期降序（最新的在前）。
     * from/to 均可选，默认返回近 30 天；两者都传时按指定区间查。
     */
    @GetMapping("/api/recommendations")
    public List<RecommendationResponse> list(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return agentService.list(user.getId(), from, to);
    }
}
