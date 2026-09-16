package com.wx.community.controller;
import com.wx.community.api.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice public class ApiExceptionHandler {
 @ExceptionHandler(BusinessException.class) public ResponseEntity<ApiResponse<Void>> business(BusinessException e){return ResponseEntity.badRequest().body(ApiResponse.fail(e.code,e.getMessage()));}
 @ExceptionHandler(DuplicateKeyException.class) public ResponseEntity<ApiResponse<Void>> duplicate(){return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.fail("DUPLICATE_OPERATION","请勿重复操作"));}
 @ExceptionHandler(Exception.class) public ResponseEntity<ApiResponse<Void>> handle(Exception e){return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail("INTERNAL_ERROR","服务器开小差了，请稍后重试"));}
}
