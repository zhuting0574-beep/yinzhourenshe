package com.wx.community.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.community.service.ImageCompressionService;
import com.wx.community.service.PlatformService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssetControllerActivityExportTest {
 @TempDir Path tempDir;

 @Test void participantExportIncludesCheckInColumnWithChineseValues()throws Exception{
  JdbcTemplate db=mock(JdbcTemplate.class);
  when(db.queryForMap(anyString(),eq(4L))).thenReturn(Map.of("title","测试活动"));
  when(db.queryForList(contains("case when p.status='CHECKED_IN'"),eq(4L))).thenReturn(List.of(
   Map.of("real_name","张三","checked_in","是"),
   Map.of("real_name","李四","checked_in","否")
  ));
  AssetController controller=new AssetController(tempDir.toString(),"http://localhost",db,mock(PlatformService.class),mock(ImageCompressionService.class),new ObjectMapper());

  byte[] bytes=controller.exportActivityParticipants(4L).getBody();
  try(XSSFWorkbook book=new XSSFWorkbook(new ByteArrayInputStream(bytes))){
   var sheet=book.getSheetAt(0);
   assertEquals("是否签到",sheet.getRow(0).getCell(6).getStringCellValue());
   assertEquals("是",sheet.getRow(1).getCell(6).getStringCellValue());
   assertEquals("否",sheet.getRow(2).getCell(6).getStringCellValue());
  }
 }
}
