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
 @GetMapping("/exports/users") public ResponseEntity<byte[]> exportUsers(@RequestParam(defaultValue="")String phone,@RequestParam(defaultValue="")String createdStart,@RequestParam(defaultValue="")String createdEnd)throws IOException{String p=phone.trim(),start=createdStart.trim(),end=createdEnd.trim();List<String> clauses=new ArrayList<>();List<Object> args=new ArrayList<>();if(!p.isBlank()){clauses.add("coalesce(phone,'') like ?");args.add("%"+p+"%");}if(!start.isBlank()){clauses.add("created_at>=?");args.add(java.sql.Timestamp.valueOf(java.time.LocalDate.parse(start).atStartOfDay()));}if(!end.isBlank()){clauses.add("created_at<?");args.add(java.sql.Timestamp.valueOf(java.time.LocalDate.parse(end).plusDays(1).atStartOfDay()));}String where=clauses.isEmpty()?"":" where "+String.join(" and ",clauses);List<Map<String,Object>> rows=db.queryForList("select real_name,nickname,gender,phone,points,status,created_at,graduation_school,major_direction from users"+where+" order by created_at desc",args.toArray());return workbook("users",List.of("姓名","昵称","性别","手机号","积分","状态","注册时间","学校","专业"),rows,List.of("real_name","nickname","gender","phone","points","status","created_at","graduation_school","major_direction"));}
 @GetMapping("/exports/users/selected") public ResponseEntity<byte[]> exportSelectedUsers(@RequestParam List<Long> ids)throws IOException{if(ids==null||ids.isEmpty()||ids.size()>1000)throw new BusinessException("INVALID_USER_IDS","请选择1到1000个用户");String ph=String.join(",",Collections.nCopies(ids.size(),"?"));List<Map<String,Object>> rows=db.queryForList("select real_name,nickname,gender,phone,points,status,created_at,graduation_school,major_direction from users where id in ("+ph+") order by id desc",ids.toArray());return workbook("users-selected",List.of("姓名","昵称","性别","手机号","积分","状态","注册时间","学校","专业"),rows,List.of("real_name","nickname","gender","phone","points","status","created_at","graduation_school","major_direction"));}
 @GetMapping("/activities/{id}/participants/export") public ResponseEntity<byte[]> exportActivityParticipants(@PathVariable Long id)throws IOException{
  Map<String,Object> activity;
  try{activity=db.queryForMap("select title from activities where id=?",id);}catch(org.springframework.dao.EmptyResultDataAccessException e){throw new BusinessException("NOT_FOUND","活动不存在");}
  String activityName=String.valueOf(activity.getOrDefault("title","活动参与人"));
  List<Map<String,Object>> rows=db.queryForList("select u.real_name,u.gender,u.age,u.graduation_school,u.major_direction,coalesce(p.registration_phone,u.phone) registration_phone from activity_participations p join users u on u.id=p.user_id where p.activity_id=? order by p.registered_at",id);
  return workbook(activityName,List.of("姓名","性别","年龄","学校","专业","报名活动电话"),rows,List.of("real_name","gender","age","graduation_school","major_direction","registration_phone"));
 }
 private ResponseEntity<byte[]> workbook(String name,List<String> headers,List<Map<String,Object>> rows,List<String> keys)throws IOException{org.apache.poi.xssf.usermodel.XSSFWorkbook book=new org.apache.poi.xssf.usermodel.XSSFWorkbook();try{var sheet=book.createSheet(safeSheetName(name));writeRow(sheet,0,headers);for(int i=0;i<rows.size();i++){List<String> values=new ArrayList<>();for(String key:keys){Object value=rows.get(i).get(key);values.add(value==null?"":String.valueOf(value));}writeRow(sheet,i+1,values);}for(int i=0;i<headers.size();i++)sheet.autoSizeColumn(i);ByteArrayOutputStream out=new ByteArrayOutputStream();book.write(out);String filename=safeFileName(name)+".xlsx";return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename*=UTF-8''"+java.net.URLEncoder.encode(filename,java.nio.charset.StandardCharsets.UTF_8).replace("+","%20")).body(out.toByteArray());}finally{book.close();}}
 private String safeFileName(String value){String name=String.valueOf(value==null?"活动参与人":value).replaceAll("[\\\\/:*?\"<>|\\r\\n]","_").trim();return name.isBlank()?"活动参与人":name;}
 private String safeSheetName(String value){String name=safeFileName(value);return name.length()>31?name.substring(0,31):name;}
 private List<String> questionOptions(Object raw){if(raw==null)return List.of();try{List<Object> values=json.readValue(String.valueOf(raw),new TypeReference<List<Object>>(){});return values.stream().map(String::valueOf).toList();}catch(Exception ignored){return List.of();}}
 private Map<String,Object> parseAnswers(Object raw){if(raw==null)return Map.of();try{return json.readValue(String.valueOf(raw),new TypeReference<Map<String,Object>>(){});}catch(Exception ignored){return Map.of();}}
 private List<String> answerValues(Object raw){if(raw==null)return List.of();if(raw instanceof Collection<?> c)return c.stream().filter(Objects::nonNull).map(String::valueOf).filter(s->!s.isBlank()&&!"null".equalsIgnoreCase(s)).toList();String text=String.valueOf(raw);return text.isBlank()||"null".equalsIgnoreCase(text)?List.of():List.of(text);}
 @GetMapping("/exports/{resource}") public ResponseEntity<byte[]> export(@PathVariable String resource)throws Exception{List<Map<String,Object>> rows=switch(resource){case "participants"->db.queryForList("select p.*,u.nickname,u.phone,a.title activity_title from activity_participations p join users u on u.id=p.user_id join activities a on a.id=p.activity_id order by p.registered_at desc");case "responses"->db.queryForList("select r.*,u.nickname,u.phone,q.name questionnaire_name from questionnaire_responses r join users u on u.id=r.user_id join questionnaires q on q.id=r.questionnaire_id order by r.submitted_at desc");default->db.queryForList("select l.*,u.nickname,u.phone from point_logs l join users u on u.id=l.user_id order by l.created_at desc");};org.apache.poi.xssf.usermodel.XSSFWorkbook book=new org.apache.poi.xssf.usermodel.XSSFWorkbook();var sheet=book.createSheet(resource);if(!rows.isEmpty()){List<String>keys=new ArrayList<>(rows.get(0).keySet());var head=sheet.createRow(0);for(int i=0;i<keys.size();i++)head.createCell(i).setCellValue(keys.get(i));for(int r=0;r<rows.size();r++){var row=sheet.createRow(r+1);for(int i=0;i<keys.size();i++)row.createCell(i).setCellValue(String.valueOf(rows.get(r).getOrDefault(keys.get(i),"")));}}ByteArrayOutputStream out=new ByteArrayOutputStream();book.write(out);book.close();return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename="+resource+".xlsx").body(out.toByteArray());}
}
