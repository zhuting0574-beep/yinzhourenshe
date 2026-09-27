package com.wx.community.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.community.service.ImageCompressionService;
import com.wx.community.service.PlatformService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssetControllerQuestionnaireExportTest {
 @TempDir Path tempDir;

 @Test void exportsQuestionnaireSummaryAndResponseDetails() throws Exception{
  JdbcTemplate db=mock(JdbcTemplate.class);
  when(db.queryForMap(anyString(),any(Object[].class))).thenReturn(Map.of("id",4L,"name","社区问卷"));
  when(db.queryForList(contains("questionnaire_questions"),any(Object[].class))).thenReturn(List.of(
    Map.of("id",1L,"title","满意度","question_type","SINGLE","options_json","[\"好\",\"一般\"]")));
  when(db.queryForList(contains("questionnaire_responses"),any(Object[].class))).thenReturn(List.of(
    Map.of("id",10L,"submitted_at",Timestamp.valueOf(LocalDateTime.of(2026,9,1,10,0)),"answers_json","{\"1\":\"好\"}","nickname","测试用户","phone","13800000000")));
  AssetController controller=new AssetController(tempDir.toString(),"http://localhost",db,mock(PlatformService.class),mock(ImageCompressionService.class),new ObjectMapper());

  var response=controller.exportQuestionnaire(4L);

  assertEquals(200,response.getStatusCode().value());
  assertEquals("attachment; filename=questionnaire-4-statistics.xlsx",response.getHeaders().getFirst("Content-Disposition"));
  try(XSSFWorkbook workbook=new XSSFWorkbook(new ByteArrayInputStream(response.getBody()))){
   assertEquals("统计汇总",workbook.getSheetAt(0).getSheetName());
   assertEquals("答卷明细",workbook.getSheetAt(1).getSheetName());
   assertEquals("好",workbook.getSheetAt(0).getRow(1).getCell(2).getStringCellValue());
   assertEquals("1",workbook.getSheetAt(0).getRow(1).getCell(3).getStringCellValue());
   assertEquals("100.00%",workbook.getSheetAt(0).getRow(1).getCell(4).getStringCellValue());
   assertEquals("测试用户",workbook.getSheetAt(1).getRow(1).getCell(1).getStringCellValue());
   assertEquals("好",workbook.getSheetAt(1).getRow(1).getCell(3).getStringCellValue());
  }
 }

 @Test void unknownQuestionnaireIsRejected() throws Exception{
  JdbcTemplate db=mock(JdbcTemplate.class);
  when(db.queryForMap(anyString(),any(Object[].class))).thenThrow(new EmptyResultDataAccessException(1));
  AssetController controller=new AssetController(tempDir.toString(),"http://localhost",db,mock(PlatformService.class),mock(ImageCompressionService.class),new ObjectMapper());

  var error=assertThrows(com.wx.community.api.BusinessException.class,()->controller.exportQuestionnaire(404L));
  assertEquals("NOT_FOUND",error.code);
 }
}
