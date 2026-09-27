package com.wx.community.controller;
import com.fasterxml.jackson.core.type.TypeReference; import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.*; import com.google.zxing.client.j2se.MatrixToImageWriter; import com.google.zxing.common.BitMatrix; import com.wx.community.api.*; import com.wx.community.service.ImageCompressionService; import com.wx.community.service.PlatformService;
import org.springframework.beans.factory.annotation.Value; import org.springframework.core.io.*; import org.springframework.http.*; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.web.bind.annotation.*; import org.springframework.web.multipart.MultipartFile;
import java.io.*; import java.nio.file.*; import java.util.*;
@RestController @RequestMapping("/api/admin") public class AssetController {
 private final Path root; private final String base; private final JdbcTemplate db; private final PlatformService platform; private final ImageCompressionService imageCompression; private final ObjectMapper json;
 public AssetController(@Value("${app.upload-dir:./uploads}")String root,@Value("${app.public-base-url:http://localhost:8081}")String base,JdbcTemplate db,PlatformService platform,ImageCompressionService imageCompression,ObjectMapper json)throws IOException{this.root=Path.of(root).toAbsolutePath().normalize();this.base=base;this.db=db;this.platform=platform;this.imageCompression=imageCompression;this.json=json;Files.createDirectories(this.root);}
 @PostMapping(value="/uploads",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public ApiResponse<?> upload(@RequestPart("file")MultipartFile file)throws IOException{
  if(file.isEmpty())throw new BusinessException("EMPTY_FILE","请选择文件");
  byte[] originalBytes=file.getBytes();
  ImageCompressionService.ProcessedImage processed=imageCompression.process(originalBytes,file.getContentType());
  String extension=processed.compressed()?"."+processed.extension():originalExtension(file.getOriginalFilename());
  String key=UUID.randomUUID()+extension;
  Path target=root.resolve(key);
  Files.write(target,processed.bytes(),StandardOpenOption.CREATE_NEW);
  String url=base+"/uploads/"+key;
  db.update("insert into file_assets(original_name,storage_key,public_url,content_type,size_bytes) values(?,?,?,?,?)",file.getOriginalFilename(),key,url,processed.contentType(),processed.bytes().length);
  Map<String,Object> response=new LinkedHashMap<>();
  response.put("url",url); response.put("name",file.getOriginalFilename());
  response.put("compressed",processed.compressed()); response.put("originalSize",(long)originalBytes.length); response.put("sizeBytes",(long)processed.bytes().length);
  return ApiResponse.ok(response);
 }
 private String originalExtension(String filename){
  if(filename==null)return "";
  int dot=filename.lastIndexOf('.');
  if(dot<0||dot==filename.length()-1)return "";
  String extension=filename.substring(dot).replaceAll("[^.A-Za-z0-9]","");
  return extension.length()>1?extension:"";
 }
 @GetMapping(value="/activities/{id}/qr.png",produces=MediaType.IMAGE_PNG_VALUE) public byte[] qr(@PathVariable Long id)throws Exception{String token=platform.qrToken(id);BitMatrix matrix=new MultiFormatWriter().encode(token,BarcodeFormat.QR_CODE,420,420);ByteArrayOutputStream out=new ByteArrayOutputStream();MatrixToImageWriter.writeToStream(matrix,"PNG",out);return out.toByteArray();}
 @GetMapping("/exports/questionnaires/{id}") public ResponseEntity<byte[]> exportQuestionnaire(@PathVariable Long id)throws IOException{
  Map<String,Object> questionnaire;
  try{questionnaire=db.queryForMap("select id,name from questionnaires where id=?",id);}catch(org.springframework.dao.EmptyResultDataAccessException e){throw new BusinessException("NOT_FOUND","问卷不存在");}
  List<Map<String,Object>> questions=db.queryForList("select id,title,question_type,options_json from questionnaire_questions where questionnaire_id=? order by sort_order,id",id);
  List<Map<String,Object>> responses=db.queryForList("select r.id,r.submitted_at,r.answers_json,u.nickname,u.phone from questionnaire_responses r left join users u on u.id=r.user_id where r.questionnaire_id=? order by r.submitted_at desc",id);
  List<Map<String,Object>> answers=responses.stream().map(row->{Map<String,Object> copy=new LinkedHashMap<>(row);copy.put("parsed_answers",parseAnswers(row.get("answers_json")));return copy;}).toList();
  org.apache.poi.xssf.usermodel.XSSFWorkbook book=new org.apache.poi.xssf.usermodel.XSSFWorkbook();
  try{
   var summary=book.createSheet("统计汇总");
   writeRow(summary,0,List.of("题目","题型","选项","回答数","比例"));
   int rowIndex=1;
   for(Map<String,Object> question:questions){
    String type=String.valueOf(question.getOrDefault("question_type",""));
    List<String> options=questionOptions(question.get("options_json"));
    if("TEXT".equalsIgnoreCase(type)||options.isEmpty()){
     int answered=0;for(Map<String,Object> response:answers)if(!answerValues(((Map<?,?>)response.get("parsed_answers")).get(String.valueOf(question.get("id")))).isEmpty())answered++;
     writeRow(summary,rowIndex++,List.of(String.valueOf(question.getOrDefault("title","")),type,"",String.valueOf(answered),""));
    }else{
     int denominator=0;for(Map<String,Object> response:answers)if(!answerValues(((Map<?,?>)response.get("parsed_answers")).get(String.valueOf(question.get("id")))).isEmpty())denominator++;
     for(String option:options){int count=0;for(Map<String,Object> response:answers){List<String> selected=answerValues(((Map<?,?>)response.get("parsed_answers")).get(String.valueOf(question.get("id"))));if(selected.contains(option))count++;}String ratio=denominator==0?"0%":String.format(Locale.ROOT,"%.2f%%",count*100.0/denominator);writeRow(summary,rowIndex++,List.of(String.valueOf(question.getOrDefault("title","")),type,option,String.valueOf(count),ratio));}
    }
   }
   var detail=book.createSheet("答卷明细");
   List<String> detailHeaders=new ArrayList<>(List.of("提交时间","用户昵称","手机号"));for(Map<String,Object> question:questions)detailHeaders.add(String.valueOf(question.getOrDefault("title","")));writeRow(detail,0,detailHeaders);
   for(int i=0;i<answers.size();i++){Map<String,Object> response=answers.get(i);List<String> values=new ArrayList<>();values.add(String.valueOf(response.getOrDefault("submitted_at","")));values.add(String.valueOf(response.getOrDefault("nickname","")));values.add(String.valueOf(response.getOrDefault("phone","")));Map<?,?> parsed=(Map<?,?>)response.get("parsed_answers");for(Map<String,Object> question:questions)values.add(String.join("、",answerValues(parsed.get(String.valueOf(question.get("id"))))));writeRow(detail,i+1,values);}
   for(var sheet:List.of(summary,detail)){sheet.createFreezePane(0,1);for(int i=0;i<sheet.getRow(0).getLastCellNum();i++)sheet.autoSizeColumn(i);}
   ByteArrayOutputStream out=new ByteArrayOutputStream();book.write(out);String filename="questionnaire-"+id+"-statistics.xlsx";return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename="+filename).body(out.toByteArray());
  }finally{book.close();}
 }
 private void writeRow(org.apache.poi.ss.usermodel.Sheet sheet,int index,List<String> values){var row=sheet.createRow(index);for(int i=0;i<values.size();i++)row.createCell(i).setCellValue(values.get(i)==null?"":values.get(i));}
 private List<String> questionOptions(Object raw){if(raw==null)return List.of();try{List<Object> values=json.readValue(String.valueOf(raw),new TypeReference<List<Object>>(){});return values.stream().map(String::valueOf).toList();}catch(Exception ignored){return List.of();}}
 private Map<String,Object> parseAnswers(Object raw){if(raw==null)return Map.of();try{return json.readValue(String.valueOf(raw),new TypeReference<Map<String,Object>>(){});}catch(Exception ignored){return Map.of();}}
 private List<String> answerValues(Object raw){if(raw==null)return List.of();if(raw instanceof Collection<?> c)return c.stream().filter(Objects::nonNull).map(String::valueOf).filter(s->!s.isBlank()&&!"null".equalsIgnoreCase(s)).toList();String text=String.valueOf(raw);return text.isBlank()||"null".equalsIgnoreCase(text)?List.of():List.of(text);}
 @GetMapping("/exports/{resource}") public ResponseEntity<byte[]> export(@PathVariable String resource)throws Exception{List<Map<String,Object>> rows=switch(resource){case "participants"->db.queryForList("select p.*,u.nickname,u.phone,a.title activity_title from activity_participations p join users u on u.id=p.user_id join activities a on a.id=p.activity_id order by p.registered_at desc");case "responses"->db.queryForList("select r.*,u.nickname,u.phone,q.name questionnaire_name from questionnaire_responses r join users u on u.id=r.user_id join questionnaires q on q.id=r.questionnaire_id order by r.submitted_at desc");default->db.queryForList("select l.*,u.nickname,u.phone from point_logs l join users u on u.id=l.user_id order by l.created_at desc");};org.apache.poi.xssf.usermodel.XSSFWorkbook book=new org.apache.poi.xssf.usermodel.XSSFWorkbook();var sheet=book.createSheet(resource);if(!rows.isEmpty()){List<String>keys=new ArrayList<>(rows.get(0).keySet());var head=sheet.createRow(0);for(int i=0;i<keys.size();i++)head.createCell(i).setCellValue(keys.get(i));for(int r=0;r<rows.size();r++){var row=sheet.createRow(r+1);for(int i=0;i<keys.size();i++)row.createCell(i).setCellValue(String.valueOf(rows.get(r).getOrDefault(keys.get(i),"")));}}ByteArrayOutputStream out=new ByteArrayOutputStream();book.write(out);book.close();return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename="+resource+".xlsx").body(out.toByteArray());}
}
