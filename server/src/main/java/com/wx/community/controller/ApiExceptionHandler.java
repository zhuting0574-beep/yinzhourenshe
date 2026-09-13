package com.wx.community.controller;
import org.springframework.web.bind.annotation.*; import java.util.*;
@RestControllerAdvice public class ApiExceptionHandler { @ExceptionHandler(Exception.class) @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST) public Map<String,Object> handle(Exception e){return Map.of("success",false,"message",e.getMessage()==null?"请求失败":e.getMessage());} }
