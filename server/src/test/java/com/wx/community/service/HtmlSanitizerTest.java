package com.wx.community.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HtmlSanitizerTest {
 @Test void keepsRichContentAndRemovesExecutableMarkup(){
  String cleaned=new HtmlSanitizer().clean("<p onclick=\"alert(1)\">介绍</p><script>alert(2)</script><img src=\"https://example.com/a.png\" onerror=\"alert(3)\">");
  assertTrue(cleaned.contains("介绍"));
  assertTrue(cleaned.contains("https://example.com/a.png"));
  assertTrue(cleaned.contains("max-width:100%"));
  assertFalse(cleaned.contains("onclick"));
  assertFalse(cleaned.contains("onerror"));
  assertFalse(cleaned.contains("<script"));
 }
}
