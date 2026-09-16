package com.wx.community.api;
public class BusinessException extends RuntimeException { public final String code; public BusinessException(String code,String message){super(message);this.code=code;} }
