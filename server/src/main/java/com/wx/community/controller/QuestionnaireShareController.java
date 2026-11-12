package com.wx.community.controller;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.wx.community.api.BusinessException;
import com.wx.community.service.WechatQuestionnaireLinkService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;

@RestController
public class QuestionnaireShareController {
 private final WechatQuestionnaireLinkService links;
 public QuestionnaireShareController(WechatQuestionnaireLinkService links){this.links=links;}
 @GetMapping(value="/api/admin/questionnaires/{id}/share-qr.png",produces=MediaType.IMAGE_PNG_VALUE) public byte[] qr(@PathVariable Long id)throws Exception{BitMatrix matrix=new MultiFormatWriter().encode(links.publicUrl(id),BarcodeFormat.QR_CODE,420,420);ByteArrayOutputStream out=new ByteArrayOutputStream();MatrixToImageWriter.writeToStream(matrix,"PNG",out);return out.toByteArray();}
 @GetMapping("/api/mini/questionnaires/{id}/open") public ResponseEntity<?> open(@PathVariable Long id){try{return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION,links.miniProgramUrl(id)).build();}catch(BusinessException e){if(!e.code.startsWith("WECHAT_")&&!"QUESTIONNAIRE_LINK_FAILED".equals(e.code))throw e;String html="<!doctype html><html lang='zh-CN'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>调查问卷</title><body style='font-family:sans-serif;text-align:center;padding:64px 24px;color:#334155'><h2>小程序暂无法打开</h2><p>请稍后重试，或在微信中扫描二维码。</p></body></html>";return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);}}
}
