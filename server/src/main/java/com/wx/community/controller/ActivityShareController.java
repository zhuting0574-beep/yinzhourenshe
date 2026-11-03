package com.wx.community.controller;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.wx.community.api.ApiResponse;
import com.wx.community.api.BusinessException;
import com.wx.community.service.WechatActivityLinkService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.Map;

@RestController
public class ActivityShareController {
 private final WechatActivityLinkService links;
 public ActivityShareController(WechatActivityLinkService links){this.links=links;}

 @GetMapping("/api/admin/activities/{id}/share-link") public ApiResponse<?> shareLink(@PathVariable Long id){return ApiResponse.ok(Map.of("url",links.publicActivityUrl(id)));}
 @GetMapping(value="/api/admin/activities/{id}/share-qr.png",produces=MediaType.IMAGE_PNG_VALUE) public byte[] shareQr(@PathVariable Long id)throws Exception{
  BitMatrix matrix=new MultiFormatWriter().encode(links.publicActivityUrl(id),BarcodeFormat.QR_CODE,420,420);
  ByteArrayOutputStream out=new ByteArrayOutputStream();MatrixToImageWriter.writeToStream(matrix,"PNG",out);return out.toByteArray();
 }
 @GetMapping("/api/mini/activities/{id}/open") public ResponseEntity<?> open(@PathVariable Long id){
  try{return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION,links.miniProgramUrl(id)).build();}
  catch(BusinessException e){
   if(!e.code.startsWith("WECHAT_")&&!"ACTIVITY_LINK_FAILED".equals(e.code))throw e;
   String html="""
    <!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><title>活动详情</title>
    <style>body{margin:0;background:#f5f7fa;color:#1f2937;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}.page{min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px;box-sizing:border-box}.panel{width:min(420px,100%);background:#fff;border:1px solid #e5e7eb;border-radius:8px;padding:32px 24px;text-align:center;box-sizing:border-box}.mark{width:52px;height:52px;margin:0 auto 20px;border-radius:50%;background:#eef6ff;color:#1677ff;display:grid;place-items:center;font-size:28px;font-weight:600}h1{font-size:20px;margin:0 0 12px}p{font-size:15px;line-height:1.7;color:#64748b;margin:0 0 24px}button{width:100%;height:44px;border:0;border-radius:6px;background:#1677ff;color:#fff;font-size:16px}</style></head>
    <body><main class="page"><section class="panel"><div class="mark">···</div><h1>小程序暂未发布</h1><p>当前还没有可打开的微信小程序正式版本。请等待发布后重新扫码或点击下方按钮重试。</p><button onclick="location.reload()">重新尝试</button></section></main></body></html>
    """;
   return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
  }
 }
}
