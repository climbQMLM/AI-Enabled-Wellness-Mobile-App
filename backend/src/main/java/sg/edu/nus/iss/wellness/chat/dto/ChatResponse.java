package sg.edu.nus.iss.wellness.chat.dto;

/**
 *
 * POST /api/chat 响应体：{"reply": "建议你..."}
 */
public record ChatResponse(String reply) {}
