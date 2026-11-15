package com.wx.community.controller;
import com.wx.community.api.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice public class ApiExceptionHandler {
 private static final Logger log=LoggerFactory.getLogger(ApiExceptionHandler.class);
 @ExceptionHandler(BusinessException.class) public ResponseEntity<ApiResponse<Void>> business(BusinessException e){return ResponseEntity.badRequest().body(ApiResponse.fail(e.code,e.getMessage()));}
 @ExceptionHandler(DuplicateKeyException.class) public ResponseEntity<ApiResponse<Void>> duplicate(){return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.fail("DUPLICATE_OPERATION","请勿重复操作"));}
 @ExceptionHandler({MaxUploadSizeExceededException.class,MultipartException.class}) public ResponseEntity<ApiResponse<Void>> uploadTooLarge(Exception e){log.warn("Upload rejected: {}",e.getMessage());return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(ApiResponse.fail("UPLOAD_TOO_LARGE","图片不能超过30MB"));}
 @ExceptionHandler(Exception.class) public ResponseEntity<ApiResponse<Void>> handle(Exception e){log.error("Unhandled API exception",e);return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail("INTERNAL_ERROR","服务器开小差了，请稍后重试"));}
}
