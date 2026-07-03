package sg.edu.nus.iss.wellness.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 *
 * 全局异常处理：把各种异常统一转成 {"error": "message"} 格式返回给前端，
 * 这样 Android 端只需要写一套错误解析逻辑，不用对每个接口特殊处理。
 *
 * 处理顺序很重要：Spring 会按"最具体的异常类型优先匹配"，
 * 所以 ApiException（我们自己抛的，已经带了正确状态码）放最前面。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 我们自己业务代码里主动抛出的异常，直接按它自带的状态码返回 */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(new ErrorResponse(ex.getMessage()));
    }

    /**
     * @Valid 校验请求体失败时 Spring 抛的异常（比如 email 格式不对、password 太短）。
     * 取第一条校验错误信息返回给前端，避免一次性堆一堆错误信息不好展示。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getField() + " " + fieldError.getDefaultMessage())
                .orElse("validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(message));
    }

    /** Spring Security 在 AuthenticationManager.authenticate() 校验密码失败时抛的异常 */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("invalid email or password"));
    }

    /**
     * 请求路径不存在任何 Controller 也不是已知静态资源时，Spring 会抛这个异常。
     * 在 RESTful API 项目里所有请求都应该落到 Controller 上，所以这基本等价于
     * "客户端调用了一个不存在的接口"，统一返回 404 比默认的"静态资源未找到"更准确。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("endpoint not found"));
    }

    /**
     * 兜底：任何没被上面几个 handler 捕获的异常都会走到这里，
     * 防止给前端返回 Spring 默认的、暴露堆栈信息的错误页面。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("internal server error: " + ex.getMessage()));
    }
}
