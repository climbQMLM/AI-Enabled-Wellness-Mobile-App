package sg.edu.nus.iss.wellness.common;

import org.springframework.http.HttpStatus;

/**
 *
 * 统一的业务异常基类。任何 Controller/Service 想返回一个带状态码的
 * 错误响应（400/401/404/409 ...），直接 throw new ApiException(status, "message")，
 * 剩下的交给 GlobalExceptionHandler 统一转成 {"error": "message"} 格式，
 * 不要在每个 Controller 里自己 try-catch 拼 ResponseEntity。
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    // 几个常用场景的快捷工厂方法，避免到处写 new ApiException(HttpStatus.XXX, ...)

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }
}
