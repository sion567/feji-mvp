package com.feiji.cloud.common.exception;

import com.feiji.cloud.cloudapi.wayline.WaylineParseException;
import com.feiji.cloud.common.api.HttpResult;
import com.feiji.cloud.common.api.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public HttpResult<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return HttpResult.error(e.getResultCode().getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public HttpResult<Void> handleValidation(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError == null ? ResultCode.PARAM_ERROR.getMessage() : fieldError.getDefaultMessage();
        return HttpResult.error(ResultCode.PARAM_ERROR.getCode(), message);
    }

    @ExceptionHandler(WaylineParseException.class)
    public HttpResult<Void> handleWaylineParse(WaylineParseException e) {
        log.warn("航线文件解析失败: {}", e.getMessage());
        return HttpResult.error(ResultCode.PARAM_ERROR.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public HttpResult<Void> handleUnknown(Exception e) {
        log.error("系统异常", e);
        return HttpResult.error(ResultCode.ERROR);
    }
}
