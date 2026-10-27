package com.wx.community.controller;
import com.fasterxml.jackson.databind.ObjectMapper; import com.wx.community.api.*; import com.wx.community.security.*; import com.wx.community.service.HtmlSanitizer; import com.wx.community.service.PlatformService;
import org.springframework.jdbc.core.*; import org.springframework.security.core.annotation.AuthenticationPrincipal; import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.sql.*; import java.time.*; import java.util.*;
@RestController @RequestMapping("/api/admin") public class AdminController {
 private final JdbcTemplate db; private final PlatformService s; private final TokenService tokens; private final PasswordEncoder passwords; private final ObjectMapper json; private final HtmlSanitizer html;
 public AdminController(JdbcTemplate d,PlatformService s,TokenService t,PasswordEncoder p,ObjectMapper j,HtmlSanitizer h){db=d;this.s=s;tokens=t;passwords=p;json=j;html=h;}
 @PostMapping("/auth/login") public ApiResponse<?> login(@RequestBody Map<String,Object>b){List<Map<String,Object>>r=db.queryForList("select * from admins where username=? and status='ACTIVE'",b.get("username"));if(r.isEmpty()||!passwords.matches(String.valueOf(b.get("password")),String.valueOf(r.get(0).get("password_hash"))))throw new BusinessException("LOGIN_FAILED","账号或密码错误");Long id=((Number)r.get(0).get("id")).longValue();String role=String.valueOf(r.get(0).getOrDefault("role","ADMIN"));db.update("update admins set last_login_at=now() where id=?",id);return ApiResponse.ok(Map.of("token",tokens.issue(role,id),"username",r.get(0).get("username"),"role",role));}
 @PutMapping("/auth/password") @Transactional public ApiResponse<?> changePassword(@AuthenticationPrincipal AuthUser admin,@RequestBody Map<String,Object>b){
  String current=String.valueOf(b.getOrDefault("currentPassword",""));
  String next=String.valueOf(b.getOrDefault("newPassword",""));
  Map<String,Object> row=db.queryForMap("select password_hash from admins where id=? and status='ACTIVE'",admin.id());
  if(!passwords.matches(current,String.valueOf(row.get("password_hash"))))throw new BusinessException("INVALID_CURRENT_PASSWORD","当前密码不正确");
  AdminPasswordPolicy.validate(next);
  if(passwords.matches(next,String.valueOf(row.get("password_hash"))))throw new BusinessException("PASSWORD_UNCHANGED","新密码不能与当前密码相同");
  db.update("update admins set password_hash=? where id=?",passwords.encode(next),admin.id());
  return ApiResponse.ok(true);
 }
 @GetMapping("/dashboard") public ApiResponse<?> dashboard(){return ApiResponse.ok(Map.of("users",scalar("select count(*) from users"),"activities",scalar("select count(*) from activities"),"orders",scalar("select count(*) from orders"),"pointsIssued",scalar("select coalesce(sum(amount),0) from point_logs where amount>0")));}
 @GetMapping("/dashboard/trends") public ApiResponse<?> dashboardTrends(@RequestParam(defaultValue="month")String dimension){return ApiResponse.ok(s.dashboardTrends(dimension));}
 @GetMapping("/users") public ApiResponse<?> users(@RequestParam(defaultValue="")String phone){return ApiResponse.ok(db.queryForList("select id,nickname,avatar_url,phone,points,status,created_at from users where (?='' or phone=?) order by id desc limit 500",phone,phone));}
 @GetMapping("/users/{id}") public ApiResponse<?> user(@PathVariable Long id){Map<String,Object>m=new LinkedHashMap<>(db.queryForMap("select * from users where id=?",id));m.put("activities",db.queryForList("select p.*,a.title from activity_participations p join activities a on a.id=p.activity_id where p.user_id=?",id));m.put("questionnaires",db.queryForList("select r.*,q.name from questionnaire_responses r join questionnaires q on q.id=r.questionnaire_id where r.user_id=?",id));m.put("checkins",db.queryForList("select * from daily_checkins where user_id=? order by check_date desc",id));m.put("orders",s.orders(id));m.put("pointLogs",s.pointLogs(id));return ApiResponse.ok(m);}
 @DeleteMapping("/users/{id}") @Transactional public ApiResponse<?> deleteUser(@PathVariable Long id){
  if(db.queryForObject("select count(*) from users where id=?",Integer.class,id)==0)throw new BusinessException("NOT_FOUND","用户不存在");
  db.update("delete u from point_lot_usages u join point_lots l on l.id=u.lot_id where l.user_id=?",id);
  db.update("delete from point_lots where user_id=?",id);
  db.update("delete from point_logs where user_id=?",id);
  db.update("delete from orders where user_id=?",id);
  db.update("delete from activity_participations where user_id=?",id);
  db.update("delete from questionnaire_responses where user_id=?",id);
  db.update("delete from daily_checkins where user_id=?",id);
  db.update("delete from users where id=?",id);
  return ApiResponse.ok(true);
 }
 @PostMapping("/users/{id}/points") public ApiResponse<?> adjust(@PathVariable Long id,@AuthenticationPrincipal AuthUser a,@RequestBody Map<String,Object>b){int delta=Integer.parseInt(String.valueOf(b.get("delta")));String reason=String.valueOf(b.getOrDefault("reason","管理员调整"));return ApiResponse.ok(s.adminAdjust(id,delta,reason,a.id()));}
 @GetMapping("/activities") public ApiResponse<?> activities(){return ApiResponse.ok(db.queryForList("select a.*,(select count(*) from activity_participations p where p.activity_id=a.id) registrations,(select count(*) from activity_participations p where p.activity_id=a.id and p.status='CHECKED_IN') checkins from activities a order by id desc"));}
 @PostMapping("/activities") public ApiResponse<?> createActivity(@RequestBody Map<String,Object>b){normalizeActivity(b);Long id=insert("insert into activities(title,cover_url,description,signup_start,signup_end,start_time,end_time,points,place_name,address,latitude,longitude,checkin_mode,checkin_radius_m,status) values(?,?,?,?,?,?,?,100,?,?,?,?,?,?,?)",b,"title","coverUrl","description","signupStart","signupEnd","startTime","endTime","placeName","address","latitude","longitude","checkinMode","checkinRadiusM","status");return ApiResponse.ok(db.queryForMap("select * from activities where id=?",id));}
 @PutMapping("/activities/{id}") public ApiResponse<?> updateActivity(@PathVariable Long id,@RequestBody Map<String,Object>b){normalizeActivity(b);db.update("update activities set title=?,cover_url=?,description=?,signup_start=?,signup_end=?,start_time=?,end_time=?,points=100,place_name=?,address=?,latitude=?,longitude=?,checkin_mode=?,checkin_radius_m=?,status=? where id=?",v(b,"title"),v(b,"coverUrl"),v(b,"description"),v(b,"signupStart"),v(b,"signupEnd"),v(b,"startTime"),v(b,"endTime"),v(b,"placeName"),v(b,"address"),v(b,"latitude"),v(b,"longitude"),v(b,"checkinMode"),v(b,"checkinRadiusM"),v(b,"status"),id);return ApiResponse.ok(db.queryForMap("select * from activities where id=?",id));}
 @GetMapping("/activities/{id}/participants") public ApiResponse<?> participants(@PathVariable Long id){return ApiResponse.ok(db.queryForList("select p.*,u.nickname,u.phone from activity_participations p join users u on u.id=p.user_id where p.activity_id=? order by p.registered_at",id));}
 @GetMapping("/activities/{id}/qr") public ApiResponse<?> qr(@PathVariable Long id){return ApiResponse.ok(Map.of("token",s.qrToken(id),"fixed",true));}
 @PostMapping("/activities/{aid}/participants/{uid}/check-in") public ApiResponse<?> manual(@PathVariable Long aid,@PathVariable Long uid){return ApiResponse.ok(s.manualCheckIn(uid,aid));}
 @GetMapping("/questionnaires") public ApiResponse<?> questionnaires(){return ApiResponse.ok(db.queryForList("select q.*,(select count(*) from questionnaire_responses r where r.questionnaire_id=q.id) submissions from questionnaires q order by id desc"));}
 @GetMapping("/questionnaires/{id}") public ApiResponse<?> questionnaireDetail(@PathVariable Long id){Map<String,Object> q=new LinkedHashMap<>(db.queryForMap("select * from questionnaires where id=?",id));q.put("questions",db.queryForList("select * from questionnaire_questions where questionnaire_id=? order by sort_order,id",id));return ApiResponse.ok(q);}
 @PostMapping("/questionnaires") @Transactional public ApiResponse<?> questionnaire(@RequestBody Map<String,Object>b){
  lockQuestionnaireRows();
  boolean pinned=optionalBoolean(b.get("pinned"),false);
  if(pinned)db.update("update questionnaires set pinned=false where pinned=true");
  b.put("pinned",pinned);
  Long id=insert("insert into questionnaires(name,description,popup_image_url,popup_title,header_title,start_time,end_time,reward_points,status,pinned) values(?,?,?,?,?,?,?,100,?,?)",b,"name","description","popupImageUrl","popupTitle","headerTitle","startTime","endTime","status","pinned");
  replaceQuestions(id,(List<?>)b.getOrDefault("questions",List.of()));
  return ApiResponse.ok(db.queryForMap("select * from questionnaires where id=?",id));
 }
 @PutMapping("/questionnaires/{id}") @Transactional public ApiResponse<?> questionnaireUpdate(@PathVariable Long id,@RequestBody Map<String,Object>b){
  lockQuestionnaireRows();
  Map<String,Object> current=db.queryForMap("select * from questionnaires where id=? for update",id);
  if(db.queryForObject("select count(*) from questionnaire_responses where questionnaire_id=?",Integer.class,id)>0&&b.containsKey("questions"))throw new BusinessException("QUESTIONNAIRE_LOCKED","已有答卷，不能修改题目");
  boolean pinned=b.containsKey("pinned")?optionalBoolean(b.get("pinned"),false):Boolean.TRUE.equals(current.get("pinned"));
  if(pinned)db.update("update questionnaires set pinned=false where pinned=true and id<>?",id);
  db.update("update questionnaires set name=?,description=?,popup_image_url=?,popup_title=?,header_title=?,start_time=?,end_time=?,reward_points=100,status=?,pinned=? where id=?",v(b,"name"),v(b,"description"),v(b,"popupImageUrl"),v(b,"popupTitle"),v(b,"headerTitle"),v(b,"startTime"),v(b,"endTime"),v(b,"status"),pinned,id);
  if(b.containsKey("questions"))replaceQuestions(id,(List<?>)b.get("questions"));
  return ApiResponse.ok(db.queryForMap("select * from questionnaires where id=?",id));
 }
 @PostMapping("/questionnaires/{id}/pin") @Transactional public ApiResponse<?> pin(@PathVariable Long id){lockQuestionnaireRows();db.update("update questionnaires set pinned=false");db.update("update questionnaires set pinned=true where id=? and status='PUBLISHED'",id);return ApiResponse.ok(true);}
 @GetMapping("/questionnaires/{id}/responses") public ApiResponse<?> responses(@PathVariable Long id){return ApiResponse.ok(db.queryForList("select r.*,u.nickname,u.phone from questionnaire_responses r join users u on u.id=r.user_id where r.questionnaire_id=? order by submitted_at desc",id));}
 @GetMapping("/products") public ApiResponse<?> products(){return ApiResponse.ok(db.queryForList("select * from products order by id desc"));}
 @PostMapping("/products") public ApiResponse<?> product(@RequestBody Map<String,Object>b){b.put("description",html.clean(b.get("description")));Long id=insert("insert into products(name,cover_url,description,points_cost,stock,status) values(?,?,?,?,?,?)",b,"name","coverUrl","description","pointsCost","stock","status");return ApiResponse.ok(db.queryForMap("select * from products where id=?",id));}
 @PutMapping("/products/{id}") public ApiResponse<?> productUpdate(@PathVariable Long id,@RequestBody Map<String,Object>b){b.put("description",html.clean(b.get("description")));db.update("update products set name=?,cover_url=?,description=?,points_cost=?,status=? where id=?",v(b,"name"),v(b,"coverUrl"),v(b,"description"),v(b,"pointsCost"),v(b,"status"),id);return ApiResponse.ok(db.queryForMap("select * from products where id=?",id));}
 @PostMapping("/products/{id}/stock") public ApiResponse<?> stock(@PathVariable Long id,@RequestBody Map<String,Object>b){int d=Integer.parseInt(String.valueOf(b.get("delta")));if(db.update("update products set stock=stock+? where id=? and stock+?>=0",d,id,d)!=1)throw new BusinessException("INVALID_STOCK","库存不足");return ApiResponse.ok(db.queryForMap("select * from products where id=?",id));}
 @GetMapping("/orders") public ApiResponse<?> orders(@RequestParam(defaultValue="")String phone){s.expireOrders();String query=phone.trim();String pattern="%"+query+"%";return ApiResponse.ok(db.queryForList("select o.*,u.nickname,u.phone from orders o join users u on u.id=o.user_id where (?='' or coalesce(o.phone_snapshot,'') like ? or coalesce(u.phone,'') like ?) order by o.created_at desc",query,pattern,pattern));}
 @PostMapping("/orders/{id}/pickup") public ApiResponse<?> pickup(@PathVariable Long id,@AuthenticationPrincipal AuthUser a){return ApiResponse.ok(s.finishOrder(id,a.id()));}
 @PostMapping("/orders/pickup/batch") public ApiResponse<?> pickupBatch(@AuthenticationPrincipal AuthUser a,@RequestBody Map<String,Object>b){Object raw=b.get("ids");if(!(raw instanceof List<?> values))throw new BusinessException("INVALID_ORDER_IDS","请选择要核销的订单");List<Long> ids=new ArrayList<>();for(Object value:values){try{if(!(value instanceof Number))throw new NumberFormatException();ids.add(new java.math.BigDecimal(value.toString()).longValueExact());}catch(Exception e){throw new BusinessException("INVALID_ORDER_IDS","订单列表包含无效订单");}}return ApiResponse.ok(s.finishOrders(ids,a.id()));}
 @PostMapping("/orders/cancel/batch") public ApiResponse<?> cancelBatch(@AuthenticationPrincipal AuthUser a,@RequestBody Map<String,Object>b){Object raw=b.get("ids");if(!(raw instanceof List<?> values))throw new BusinessException("INVALID_ORDER_IDS","请选择要取消的订单");List<Long> ids=new ArrayList<>();for(Object value:values){try{if(!(value instanceof Number))throw new NumberFormatException();ids.add(new java.math.BigDecimal(value.toString()).longValueExact());}catch(Exception e){throw new BusinessException("INVALID_ORDER_IDS","订单列表包含无效订单");}}Object rawReason=b.get("reason");String reason=rawReason==null?null:String.valueOf(rawReason);return ApiResponse.ok(s.cancelOrders(ids,reason,a.id()));}
 @PostMapping("/orders/{id}/cancel") public ApiResponse<?> cancel(@PathVariable Long id,@AuthenticationPrincipal AuthUser a,@RequestBody Map<String,Object>b){return ApiResponse.ok(s.cancelOrder(id,String.valueOf(b.get("reason")),a.id()));}
 @GetMapping("/points") public ApiResponse<?> points(){return ApiResponse.ok(db.queryForList("select l.*,u.nickname,u.phone,a.username operator_name from point_logs l join users u on u.id=l.user_id left join admins a on a.id=l.operator_admin_id order by l.created_at desc limit 2000"));}
 @GetMapping("/content") public ApiResponse<?> content(){return ApiResponse.ok(db.queryForList("select * from content_items where content_type<>'BANNER' order by content_type,sort_order,id"));}
 @PostMapping("/content") public ApiResponse<?> contentCreate(@RequestBody Map<String,Object>b){if("BANNER".equalsIgnoreCase(String.valueOf(b.get("contentType"))))throw new BusinessException("BANNER_USE_DEDICATED_API","请通过首页 banner 配置管理横幅");b.put("content",html.clean(b.get("content")));Long id=insert("insert into content_items(content_type,title,image_url,content,sort_order,enabled,activity_id,latitude,longitude,address) values(?,?,?,?,?,?,?,?,?,?)",b,"contentType","title","imageUrl","content","sortOrder","enabled","activityId","latitude","longitude","address");return ApiResponse.ok(db.queryForMap("select * from content_items where id=?",id));}
 @PutMapping("/content/{id}") public ApiResponse<?> contentUpdate(@PathVariable Long id,@RequestBody Map<String,Object>b){if(scalar("select count(*) from content_items where id="+id+" and content_type='BANNER'").intValue()>0)throw new BusinessException("BANNER_USE_DEDICATED_API","请通过首页 banner 配置管理横幅");b.put("content",html.clean(b.get("content")));db.update("update content_items set title=?,image_url=?,content=?,sort_order=?,enabled=?,activity_id=?,latitude=?,longitude=?,address=? where id=? and content_type<>'BANNER'",v(b,"title"),v(b,"imageUrl"),v(b,"content"),v(b,"sortOrder"),v(b,"enabled"),v(b,"activityId"),v(b,"latitude"),v(b,"longitude"),v(b,"address"),id);return ApiResponse.ok(db.queryForMap("select * from content_items where id=?",id));}
 @GetMapping("/banners") public ApiResponse<?> banners(){return ApiResponse.ok(db.queryForList("select ci.*,a.title activity_title from content_items ci left join activities a on a.id=ci.activity_id where ci.content_type='BANNER' order by ci.sort_order,ci.id"));}
 @GetMapping("/banners/{id}") public ApiResponse<?> banner(@PathVariable Long id){return ApiResponse.ok(bannerById(id));}
 @PostMapping("/banners") @Transactional public ApiResponse<?> bannerCreate(@RequestBody Map<String,Object>b){
  lockBannerRows();
  if(scalar("select count(*) from content_items where content_type='BANNER'").intValue()>=4)throw new BusinessException("BANNER_LIMIT","banner最多4个");
  LocalDateTime start=parseBannerTime(b.get("startTime"),"开始时间"),end=parseBannerTime(b.get("endTime"),"结束时间");
  if(start.isAfter(end))throw new BusinessException("INVALID_BANNER_TIME","开始时间不能晚于结束时间");
  String image=requiredText(b.get("imageUrl"),"图片不能为空");Long activityId=optionalId(b.get("activityId"));if(activityId!=null)validateBannerActivity(activityId);
  Boolean enabled=optionalBoolean(b.get("enabled"),true);Integer sortOrder=optionalInteger(b.get("sortOrder"),0);
  // LinkedHashMap permits a null activityId, which represents a purely visual banner.
  Map<String,Object> values=new LinkedHashMap<>();values.put("imageUrl",image);values.put("startTime",start);values.put("endTime",end);values.put("activityId",activityId);values.put("enabled",enabled);values.put("sortOrder",sortOrder);
  Long id=insert("insert into content_items(content_type,image_url,start_time,end_time,activity_id,enabled,sort_order) values('BANNER',?,?,?,?,?,?)",values,"imageUrl","startTime","endTime","activityId","enabled","sortOrder");
  return ApiResponse.ok(bannerById(id));
 }
 @PutMapping("/banners/{id}") @Transactional public ApiResponse<?> bannerUpdate(@PathVariable Long id,@RequestBody Map<String,Object>b){
  requireBanner(id);
  LocalDateTime start=parseBannerTime(b.get("startTime"),"开始时间"),end=parseBannerTime(b.get("endTime"),"结束时间");
  if(start.isAfter(end))throw new BusinessException("INVALID_BANNER_TIME","开始时间不能晚于结束时间");
  String image=requiredText(b.get("imageUrl"),"图片不能为空");Long activityId=optionalId(b.get("activityId"));if(activityId!=null)validateBannerActivity(activityId);
  Boolean enabled=optionalBoolean(b.get("enabled"),true);Integer sortOrder=optionalInteger(b.get("sortOrder"),0);
  db.update("update content_items set image_url=?,start_time=?,end_time=?,activity_id=?,enabled=?,sort_order=? where id=? and content_type='BANNER'",image,start,end,activityId,enabled,sortOrder,id);
  return ApiResponse.ok(bannerById(id));
 }
 @DeleteMapping("/banners/{id}") @Transactional public ApiResponse<?> bannerDelete(@PathVariable Long id){requireBanner(id);db.update("delete from content_items where id=? and content_type='BANNER'",id);return ApiResponse.ok(true);}
 private void lockBannerRows(){
  // ABOUT is seeded by V4 and acts as a stable mutex row, serializing concurrent creates.
  db.queryForList("select id from content_items where content_type='ABOUT' order by id limit 1 for update");
 }
 private void lockQuestionnaireRows(){
  // ABOUT is seeded by V4 and acts as a stable mutex even when the questionnaire
  // table is empty, serializing concurrent pin changes and questionnaire writes.
  db.queryForList("select id from content_items where content_type='ABOUT' order by id limit 1 for update");
 }
 private Map<String,Object> bannerById(Long id){List<Map<String,Object>> rows=db.queryForList("select ci.*,a.title activity_title from content_items ci left join activities a on a.id=ci.activity_id where ci.id=? and ci.content_type='BANNER'",id);if(rows.isEmpty())throw new BusinessException("NOT_FOUND","banner不存在");return rows.get(0);}
 private void requireBanner(Long id){if(db.queryForObject("select count(*) from content_items where id=? and content_type='BANNER'",Integer.class,id)==0)throw new BusinessException("NOT_FOUND","banner不存在");}
 private void validateBannerActivity(Long id){if(db.queryForObject("select count(*) from activities where id=?",Integer.class,id)==0)throw new BusinessException("INVALID_ACTIVITY","关联活动不存在");}
 private String requiredText(Object value,String message){if(value==null||String.valueOf(value).isBlank())throw new BusinessException("INVALID_BANNER","图片不能为空");return String.valueOf(value).trim();}
 private Long optionalId(Object value){
  if(value==null)return null;
  String text=String.valueOf(value).trim();
  if(text.isEmpty()||"null".equalsIgnoreCase(text))return null;
  try{long id=Long.parseLong(text);if(id<=0)throw new NumberFormatException();return id;}
  catch(Exception e){throw new BusinessException("INVALID_ACTIVITY","请选择有效活动");}
 }
 private LocalDateTime parseBannerTime(Object value,String label){try{String text=String.valueOf(value).trim();if(text.isEmpty()||"null".equals(text))throw new IllegalArgumentException();return LocalDateTime.parse(text.replace(' ','T'));}catch(Exception e){throw new BusinessException("INVALID_BANNER_TIME",label+"不能为空或格式错误");}}
 private Boolean optionalBoolean(Object value,boolean fallback){if(value==null)return fallback;if(value instanceof Boolean b)return b;String text=String.valueOf(value);if("true".equalsIgnoreCase(text))return true;if("false".equalsIgnoreCase(text))return false;throw new BusinessException("INVALID_BANNER","启用状态格式无效");}
 private Integer optionalInteger(Object value,int fallback){if(value==null)return fallback;try{return Integer.valueOf(String.valueOf(value));}catch(Exception e){throw new BusinessException("INVALID_BANNER","排序值必须为整数");}}
 private void replaceQuestions(Long id,List<?> qs){db.update("delete from questionnaire_questions where questionnaire_id=?",id);int i=0;for(Object x:qs){Map<?,?>q=(Map<?,?>)x;Object options=q.containsKey("options")?q.get("options"):List.of();Object required=q.containsKey("required")?q.get("required"):Boolean.TRUE;try{db.update("insert into questionnaire_questions(questionnaire_id,question_type,title,options_json,required,sort_order) values(?,?,?,?,?,?)",id,q.get("questionType"),q.get("title"),json.writeValueAsString(options),required,i++);}catch(Exception e){throw new BusinessException("INVALID_QUESTION","题目格式错误");}}}
 private Long insert(String sql,Map<String,Object>b,String...keys){org.springframework.jdbc.support.GeneratedKeyHolder h=new org.springframework.jdbc.support.GeneratedKeyHolder();db.update(c->{PreparedStatement p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<keys.length;i++)p.setObject(i+1,v(b,keys[i]));return p;},h);return Objects.requireNonNull(h.getKey()).longValue();}
 private void normalizeActivity(Map<String,Object>b){String mode=String.valueOf(b.getOrDefault("checkinMode","QR")).toUpperCase(Locale.ROOT);if(!Set.of("QR","LOCATION","BOTH").contains(mode))throw new BusinessException("INVALID_CHECKIN_MODE","签到方式无效");int radius;try{radius=Integer.parseInt(String.valueOf(b.getOrDefault("checkinRadiusM",200)));}catch(Exception e){throw new BusinessException("INVALID_CHECKIN_RADIUS","签到范围必须为正整数");}if(radius<=0)throw new BusinessException("INVALID_CHECKIN_RADIUS","签到范围必须为正整数");Double latitude=coordinate(b.get("latitude"),90,"纬度"),longitude=coordinate(b.get("longitude"),180,"经度");if("PUBLISHED".equals(b.get("status"))&&!"QR".equals(mode)&&(latitude==null||longitude==null))throw new BusinessException("LOCATION_REQUIRED","定位签到活动发布前必须设置经纬度");b.put("latitude",latitude);b.put("longitude",longitude);b.put("checkinMode",mode);b.put("checkinRadiusM",radius);b.put("description",html.clean(b.get("description")));}
 private Double coordinate(Object value,double limit,String label){if(value==null||String.valueOf(value).isBlank())return null;try{double parsed=Double.parseDouble(String.valueOf(value));if(!Double.isFinite(parsed)||Math.abs(parsed)>limit)throw new NumberFormatException();return parsed;}catch(Exception e){throw new BusinessException("INVALID_LOCATION",label+"格式无效");}}
 private Object v(Map<String,Object>b,String k){return b.get(k);} private Number scalar(String sql){return db.queryForObject(sql,Number.class);}
}
