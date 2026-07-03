package sg.edu.nus.iss.wellness.chat.dto;

import jakarta.validation.constraints.NotBlank;

/**
 *
 * POST /api/chat 请求体：{"message": "我最近睡眠质量差，有什么建议？"}
 */
public record ChatRequest(
        @NotBlank(message = "message must not be blank")
        String message
) {}
