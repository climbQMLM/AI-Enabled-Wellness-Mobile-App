package sg.edu.nus.iss.wellness.agent.dto;

import sg.edu.nus.iss.wellness.agent.Recommendation;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 *
 * GET /api/recommendations 每条记录的响应体。
 */
public record RecommendationResponse(
        Long id,
        LocalDate recDate,
        String type,
        String createdBy,
        String content,
        LocalDateTime createdAt
) {
    public static RecommendationResponse from(Recommendation r) {
        return new RecommendationResponse(
                r.getId(),
                r.getRecDate(),
                r.getType().name(),
                r.getCreatedBy().name(),
                r.getContent(),
                r.getCreatedAt()
        );
    }
}
