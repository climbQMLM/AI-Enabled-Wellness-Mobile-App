package sg.edu.nus.iss.wellness.common;

/**
 *
 * 统一错误响应体：{"error": "message"}，对应 PROJECT_SPEC.md §4 通用约定。
 * 用 record 是因为这个类只是个不可变的数据容器，没有别的行为。
 */
public record ErrorResponse(String error) {
}
