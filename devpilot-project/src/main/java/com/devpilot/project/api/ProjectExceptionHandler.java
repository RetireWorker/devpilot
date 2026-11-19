package com.devpilot.project.api;

import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.DevpilotException;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.common.error.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.devpilot.project")
public class ProjectExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail notFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "资源不存在", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail conflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, "资源冲突", ex.getMessage());
    }

    @ExceptionHandler({ValidationException.class, MethodArgumentNotValidException.class})
    public ProblemDetail invalid(Exception ex) {
        var detail = ex instanceof MethodArgumentNotValidException manve
                ? manve.getBindingResult().getFieldErrors().stream()
                        .map(e -> e.getField() + ": " + e.getDefaultMessage())
                        .toList().toString()
                : ex.getMessage();
        return problem(HttpStatus.BAD_REQUEST, "参数校验失败", detail);
    }

    @ExceptionHandler(DevpilotException.class)
    public ProblemDetail domain(DevpilotException ex) {
        return problem(HttpStatus.BAD_REQUEST, "业务错误", ex.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail == null ? title : detail);
        problem.setTitle(title);
        return problem;
    }
}
