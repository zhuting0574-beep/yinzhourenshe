package com.wx.community.service;
import com.fasterxml.jackson.core.JsonProcessingException; import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.community.api.BusinessException;
import org.springframework.beans.factory.annotation.Value; import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.*; import org.springframework.jdbc.support.*; import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import javax.crypto.Mac; import javax.crypto.spec.SecretKeySpec; import java.nio.charset.StandardCharsets;
import java.sql.*; import java.time.*; import java.time.temporal.ChronoUnit; import java.util.*;
import java.time.format.DateTimeFormatter;

@Service public class PlatformService {
 private final JdbcTemplate db; private final ObjectMapper json; private final ZoneId zone=ZoneId.of("Asia/Shanghai"); private final byte[] qrSecret;
 public PlatformService(JdbcTemplate db,ObjectMapper json,@Value("${app.qr-secret}")String qrSecret){this.db=db;this.json=json;this.qrSecret=qrSecret.getBytes(StandardCharsets.UTF_8);}
 public List<Map<String,Object>> activities(Long uid){String sql="select a.*,p.status participation_status from activities a left join activity_participations p on p.activity_id=a.id and p.user_id=? where a.end_time>=now() and (a.status='PUBLISHED' or p.id is not null) order by case when a.status='PUBLISHED' then 0 else 1 end,a.start_time desc";return db.queryForList(sql,uid==null?-1:uid);}
 public Map<String,Object> activity(Long id,Long uid){return one("select a.*,p.status participation_status,p.checked_in_at from activities a left join activity_participations p on p.activity_id=a.id and p.user_id=? where a.id=?",uid==null?-1:uid,id);}
 public List<Map<String,Object>> products(){return db.queryForList("select *,stock available_stock from products where status='ON_SALE' order by id desc");}
 public Map<String,Object> product(Long id){return one("select *,stock available_stock from products where id=? and status='ON_SALE'",id);}
 public Map<String,Object> profile(Long uid){
  expireUser(uid);
  Map<String,Object> user=new LinkedHashMap<>(one("select id,nickname,avatar_url,phone,points,status,created_at,real_name,age,graduation_school,major_direction,city,profile_rewarded from users where id=?",uid));
  user.put("profileComplete",profileComplete(user));
  user.put("profileRewarded",Boolean.TRUE.equals(asBoolean(user.get("profile_rewarded"))));
  return user;
 }
 @Transactional public Map<String,Object> updateProfileDetails(Long uid,Map<String,Object> details){
  Map<String,Object> current=one("select * from users where id=? for update",uid);
  String nickname=profileNicknamePreserve(details,"nickname",current.get("nickname"));
  String avatarUrl=profileTextPreserve(details,"avatarUrl",current.get("avatar_url"));
  String realName=profileText(details,"realName",current.get("real_name"));
  Integer age=profileAge(details,current.get("age"));
  String graduationSchool=profileText(details,"graduationSchool",current.get("graduation_school"));
  String majorDirection=profileText(details,"majorDirection",current.get("major_direction"));
  String city=profileText(details,"city",current.get("city"));
  db.update("update users set nickname=?,avatar_url=?,real_name=?,age=?,graduation_school=?,major_direction=?,city=? where id=?",nickname,avatarUrl,realName,age,graduationSchool,majorDirection,city,uid);
  boolean complete=profileComplete(realName,age,graduationSchool,majorDirection,city);
  boolean rewarded=Boolean.TRUE.equals(asBoolean(current.get("profile_rewarded")));
  boolean rewardGranted=false;
  if(complete&&!rewarded){
   String rewardKey="profile:"+uid;
   boolean alreadyIssued=count("select count(*) from point_logs where idempotency_key=?",rewardKey)>0;
   if(!alreadyIssued)issueAward(uid,100,"PROFILE","PROFILE",String.valueOf(uid),rewardKey,"完善个人资料获得100积分",null);
   db.update("update users set profile_rewarded=true where id=?",uid);
   rewardGranted=!alreadyIssued;
  }
  Map<String,Object> result=new LinkedHashMap<>(profile(uid));
  result.put("rewardGranted",rewardGranted);
  return result;
 }
 public List<Map<String,Object>> pointLogs(Long uid){expireUser(uid);return db.queryForList("select * from point_logs where user_id=? order by created_at desc",uid);}
 public List<Map<String,Object>> orders(Long uid){expireOrders();return db.queryForList("select * from orders where user_id=? order by created_at desc",uid);}
 public Map<String,Object> home(Long uid){Map<String,Object> m=new LinkedHashMap<>();m.put("banners",content("BANNER"));m.put("about",content("ABOUT"));m.put("activities",activities(uid).stream().limit(3).toList());if(uid!=null){Map<String,Object> user=profile(uid);m.put("points",user.get("points"));m.put("user",user);m.put("questionnaire",activeQuestionnaire(uid));}else m.put("points",0);return m;}
 public List<Map<String,Object>> content(String type){
  if("BANNER".equals(type))return activeBanners();
  return db.queryForList("select * from content_items where content_type=? and enabled=true order by sort_order,id",type);
 }
 private List<Map<String,Object>> activeBanners(){
  LocalDateTime now=LocalDateTime.now(zone);
  return db.queryForList("select ci.* from content_items ci join activities a on a.id=ci.activity_id where ci.content_type='BANNER' and ci.enabled=true and ci.image_url is not null and trim(ci.image_url)<>'' and ci.start_time is not null and ci.end_time is not null and ? >= ci.start_time and ? <= ci.end_time order by ci.sort_order,ci.id",now,now);
 }
 public Map<String,Object> dashboardTrends(String dimension){
  String d=Set.of("year","month","day").contains(dimension)?dimension:"month"; LocalDate today=LocalDate.now(zone); LocalDate start=d.equals("year")?today.withDayOfYear(1).minusYears(4):d.equals("month")?today.withDayOfMonth(1).minusMonths(11):today.minusDays(29); LocalDate end=today.plusDays(1); String format=d.equals("year")?"%Y":d.equals("month")?"%Y-%m":"%Y-%m-%d";
  List<String> labels=new ArrayList<>(); DateTimeFormatter formatter=DateTimeFormatter.ofPattern(d.equals("year")?"yyyy":d.equals("month")?"yyyy-MM":"yyyy-MM-dd"); LocalDate cursor=start; while(cursor.isBefore(end)){labels.add(cursor.format(formatter));cursor=d.equals("year")?cursor.plusYears(1):d.equals("month")?cursor.plusMonths(1):cursor.plusDays(1);}
  Map<String,Integer> users=counts("select date_format(created_at,'"+format+"') bucket,count(*) value from users where created_at>=? and created_at<? group by bucket",start,end);
  Map<String,Integer> registrations=counts("select date_format(registered_at,'"+format+"') bucket,count(*) value from activity_participations where registered_at>=? and registered_at<? group by bucket",start,end);
  Map<String,Integer> active=counts("select bucket,count(distinct user_id) value from (select user_id,created_at event_at from point_logs union all select user_id,created_at event_at from orders union all select user_id,registered_at event_at from activity_participations union all select user_id,submitted_at event_at from questionnaire_responses) events where event_at>=? and event_at<? group by bucket",start,end,format);
  return Map.of("dimension",d,"labels",labels,"newUsers",labels.stream().map(x->users.getOrDefault(x,0)).toList(),"activeUsers",labels.stream().map(x->active.getOrDefault(x,0)).toList(),"registrations",labels.stream().map(x->registrations.getOrDefault(x,0)).toList());
 }
 private Map<String,Integer> counts(String sql,LocalDate start,LocalDate end,Object...suffix){String query=suffix.length>0?sql.replace("select bucket,count", "select date_format(event_at,'"+suffix[0]+"') bucket,count"):sql;List<Object> args=new ArrayList<>();args.add(Timestamp.valueOf(start.atStartOfDay()));args.add(Timestamp.valueOf(end.atStartOfDay()));return db.query(query,args.toArray(),rs->{Map<String,Integer> out=new HashMap<>();while(rs.next())out.put(rs.getString("bucket"),rs.getInt("value"));return out;});}

 @Transactional public Map<String,Object> registerActivity(Long uid,Long aid){Map<String,Object>a=activity(aid,uid);if(Set.of("REGISTERED","CHECKED_IN").contains(String.valueOf(a.get("participation_status"))))return a;LocalDateTime now=LocalDateTime.now(zone);if(!"PUBLISHED".equals(a.get("status"))||before(now,a.get("signup_start"))||after(now,a.get("signup_end")))throw new BusinessException("REGISTRATION_CLOSED","当前不在报名时间内");db.update("insert into activity_participations(user_id,activity_id,status,registered_at) values(?,?,'REGISTERED',?)",uid,aid,now);return activity(aid,uid);}
 public String qrToken(Long aid){String body=String.valueOf(aid);return body+":"+sign(body);}
 @Transactional public Map<String,Object> verifyActivity(Long uid,String token,String method){String[] p=token==null?new String[0]:token.split(":");if(p.length!=2||!constant(sign(p[0]),p[1]))throw new BusinessException("INVALID_QR","核销码无效");Long aid=parseLongObj(p[0]);Map<String,Object>a=activity(aid,uid);String mode=String.valueOf(a.getOrDefault("checkin_mode","QR"));if(!Set.of("QR","BOTH").contains(mode))throw new BusinessException("CHECKIN_METHOD_NOT_ALLOWED","该活动仅支持定位签到");return completeActivityCheckIn(uid,aid,a,"QR",null,null,null,null);}
 @Transactional public Map<String,Object> locationCheckIn(Long uid,Long aid,double latitude,double longitude,double accuracy){if(!Double.isFinite(latitude)||!Double.isFinite(longitude)||!Double.isFinite(accuracy)||Math.abs(latitude)>90||Math.abs(longitude)>180||accuracy<0)throw new BusinessException("INVALID_LOCATION","定位数据无效");Map<String,Object>a=activity(aid,uid);String mode=String.valueOf(a.getOrDefault("checkin_mode","QR"));if(!Set.of("LOCATION","BOTH").contains(mode))throw new BusinessException("CHECKIN_METHOD_NOT_ALLOWED","该活动仅支持二维码签到");if(a.get("latitude")==null||a.get("longitude")==null)throw new BusinessException("ACTIVITY_LOCATION_MISSING","活动签到地点尚未配置");double targetLatitude=((Number)a.get("latitude")).doubleValue(),targetLongitude=((Number)a.get("longitude")).doubleValue();double distance=distanceMeters(latitude,longitude,targetLatitude,targetLongitude);double radius=a.get("checkin_radius_m") instanceof Number n?n.doubleValue():200;if(distance>radius)throw new BusinessException("OUTSIDE_CHECKIN_RANGE","距离活动地点约"+Math.round(distance)+"米，超出"+Math.round(radius)+"米签到范围");return completeActivityCheckIn(uid,aid,a,"LOCATION",latitude,longitude,accuracy,distance);}

 private Map<String,Object> completeActivityCheckIn(Long uid,Long aid,Map<String,Object>a,String method,Double latitude,Double longitude,Double accuracy,Double distance){
  // A participation can only transition from REGISTERED to CHECKED_IN once.
  // Return the existing record before validating the activity window so retries
  // remain idempotent even when the retry happens after the activity ends.
  if("CHECKED_IN".equals(String.valueOf(a.get("participation_status"))))return existingCheckIn(uid,aid);
  LocalDateTime now=LocalDateTime.now(zone);if(before(now,a.get("start_time"))||after(now,a.get("end_time")))throw new BusinessException("ACTIVITY_NOT_RUNNING","当前不在活动核销时间内");int n=db.update("update activity_participations set status='CHECKED_IN',checked_in_at=?,checkin_method=?,checkin_latitude=?,checkin_longitude=?,checkin_accuracy_m=?,checkin_distance_m=? where user_id=? and activity_id=? and status='REGISTERED'",now,method,latitude,longitude,accuracy,distance,uid,aid);if(n==0){Map<String,Object> existing=new LinkedHashMap<>(one("select * from activity_participations where user_id=? and activity_id=?",uid,aid));if(!"CHECKED_IN".equals(existing.get("status")))throw new BusinessException("NOT_REGISTERED","请先报名活动");existing.put("alreadyCheckedIn",true);return existing;}award(uid,100,"ACTIVITY","ACTIVITY",String.valueOf(aid),"activity:"+aid+":user:"+uid,"参加“"+a.get("title")+"”核销奖励",null);return one("select * from activity_participations where user_id=? and activity_id=?",uid,aid);}

 private Map<String,Object> existingCheckIn(Long uid,Long aid){Map<String,Object> existing=new LinkedHashMap<>(one("select * from activity_participations where user_id=? and activity_id=?",uid,aid));existing.put("alreadyCheckedIn",true);return existing;}

 static double distanceMeters(double lat1,double lon1,double lat2,double lon2){double earth=6371000;double p1=Math.toRadians(lat1),p2=Math.toRadians(lat2);double dp=Math.toRadians(lat2-lat1),dl=Math.toRadians(lon2-lon1);double h=Math.sin(dp/2)*Math.sin(dp/2)+Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)*Math.sin(dl/2);return earth*2*Math.atan2(Math.sqrt(h),Math.sqrt(1-h));}
 @Transactional public Map<String,Object> dailyCheckIn(Long uid){LocalDate date=LocalDate.now(zone);db.update("insert into daily_checkins(user_id,check_date,points) values(?,?,10)",uid,date);award(uid,10,"DAILY_CHECKIN","CHECKIN",date.toString(),"checkin:"+uid+":"+date,"每日签到获得10积分",null);return checkinSummary(uid,date.getYear(),date.getMonthValue());}
 public Map<String,Object> checkinSummary(Long uid,int year,int month){YearMonth ym=YearMonth.of(year,month);List<Map<String,Object>> days=db.queryForList("select check_date,points,created_at from daily_checkins where user_id=? and check_date between ? and ? order by check_date",uid,ym.atDay(1),ym.atEndOfMonth());return Map.of("year",year,"month",month,"days",days,"count",days.size(),"todayChecked",days.stream().anyMatch(x->LocalDate.now(zone).equals(toDate(x.get("check_date")))));}
 public Map<String,Object> activeQuestionnaire(Long uid){
  String ym=YearMonth.now(zone).toString();
  // A response is unique per user and calendar month, so exclude every questionnaire
  // once the user has answered any questionnaire in the current month.  Ordering by
  // pinned first lets an effective pinned questionnaire win; expired pinned rows are
  // filtered before ordering and therefore naturally fall back to the newest regular one.
  List<Map<String,Object>> rows=db.queryForList("select q.* from questionnaires q where q.status='PUBLISHED' and now() between q.start_time and q.end_time and not exists (select 1 from questionnaire_responses r where r.user_id=? and r.response_month=?) order by q.pinned desc,q.start_time desc,q.id desc limit 1",uid,ym);
  if(rows.isEmpty())return null;
  Map<String,Object> q=new LinkedHashMap<>(rows.get(0));
  q.put("questions",db.queryForList("select * from questionnaire_questions where questionnaire_id=? order by sort_order,id",q.get("id")));
  q.put("completedThisMonth",false);
  return q;
 }
 @Transactional public Map<String,Object> submitQuestionnaire(Long uid,Long qid,Object answers){Map<String,Object>q=one("select * from questionnaires where id=? and status='PUBLISHED' and now() between start_time and end_time",qid);validateAnswers(qid,answers);String ym=YearMonth.now(zone).toString();String encoded=write(answers);db.update("insert into questionnaire_responses(questionnaire_id,user_id,response_month,answers_json,reward_points,submitted_at) values(?,?,?,?,100,?)",qid,uid,ym,encoded,LocalDateTime.now(zone));award(uid,100,"QUESTIONNAIRE","QUESTIONNAIRE",String.valueOf(qid),"questionnaire:"+uid+":"+ym,"完成“"+q.get("name")+"”获得积分",null);return profile(uid);}

 @Transactional public Map<String,Object> exchange(Long uid,Long pid,String requestId){Map<String,Object> existing=first("select * from orders where user_id=? and idempotency_key=?",uid,requestId);if(existing!=null)return existing;Map<String,Object> p=one("select * from products where id=? and status='ON_SALE'",pid);if(db.update("update products set stock=stock-1,locked_stock=locked_stock+1 where id=? and stock>0",pid)!=1)throw new BusinessException("OUT_OF_STOCK","商品已兑完");Map<String,Object>u=one("select * from users where id=? for update",uid);int cost=((Number)p.get("points_cost")).intValue();if(((Number)u.get("points")).intValue()<cost)throw new BusinessException("INSUFFICIENT_POINTS","积分不足");String no="YL"+System.currentTimeMillis()+UUID.randomUUID().toString().substring(0,6);KeyHolder kh=new GeneratedKeyHolder();db.update(c->{PreparedStatement ps=c.prepareStatement("insert into orders(order_no,user_id,product_id,points,status,expire_at,idempotency_key,product_name,product_cover_url,phone_snapshot) values(?,?,?,?, 'PENDING',?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);ps.setString(1,no);ps.setLong(2,uid);ps.setLong(3,pid);ps.setInt(4,cost);ps.setTimestamp(5,Timestamp.valueOf(LocalDateTime.now(zone).plusDays(7)));ps.setString(6,requestId);ps.setString(7,String.valueOf(p.get("name")));ps.setString(8,(String)p.get("cover_url"));ps.setString(9,(String)u.get("phone"));return ps;},kh);Long oid=Objects.requireNonNull(kh.getKey()).longValue();spend(uid,cost,oid,"order:"+oid,"兑换“"+p.get("name")+"”扣除积分");return one("select * from orders where id=?",oid);}
 @Transactional public Map<String,Object> finishOrder(Long oid,Long adminId){if(db.update("update orders set status='PICKED_UP',picked_at=?,operator_admin_id=? where id=? and status='PENDING'",LocalDateTime.now(zone),adminId,oid)!=1)throw new BusinessException("ORDER_FINAL","订单已处理");db.update("update products p join orders o on o.product_id=p.id set p.locked_stock=p.locked_stock-1,p.redeemed_stock=p.redeemed_stock+1 where o.id=? and p.locked_stock>0",oid);return one("select * from orders where id=?",oid);}
 @Transactional public Map<String,Object> finishOrders(Collection<Long> orderIds,Long adminId){
  if(orderIds==null||orderIds.isEmpty())throw new BusinessException("INVALID_ORDER_IDS","请选择要核销的订单");
  if(orderIds.size()>500)throw new BusinessException("TOO_MANY_ORDERS","一次最多核销500个订单");
  LinkedHashSet<Long> uniqueIds=new LinkedHashSet<>();
  for(Long id:orderIds)if(id==null||id<=0||!uniqueIds.add(id))throw new BusinessException("INVALID_ORDER_IDS","订单列表包含无效或重复订单");
  List<Long> ids=new ArrayList<>(uniqueIds);
  String placeholders=String.join(",",Collections.nCopies(ids.size(),"?"));
  List<Map<String,Object>> locked=db.queryForList("select id,product_id,status from orders where id in ("+placeholders+") order by id for update",ids.toArray());
  if(locked.size()!=ids.size())throw new BusinessException("ORDER_NOT_FOUND","部分订单不存在，请刷新列表后重试");
  for(Map<String,Object> order:locked)if(!"PENDING".equals(order.get("status")))throw new BusinessException("ORDER_FINAL","部分订单已处理，请刷新列表后重试");

  LocalDateTime now=LocalDateTime.now(zone);
  for(Long id:ids)if(db.update("update orders set status='PICKED_UP',picked_at=?,operator_admin_id=? where id=? and status='PENDING'",now,adminId,id)!=1)throw new BusinessException("ORDER_FINAL","部分订单已处理，请刷新列表后重试");
  Map<Long,Integer> quantityByProduct=new TreeMap<>();
  for(Map<String,Object> order:locked){Long productId=((Number)order.get("product_id")).longValue();quantityByProduct.merge(productId,1,Integer::sum);}
  for(Map.Entry<Long,Integer> entry:quantityByProduct.entrySet()){
   int quantity=entry.getValue();
   if(db.update("update products set locked_stock=locked_stock-?,redeemed_stock=redeemed_stock+? where id=? and locked_stock>=?",quantity,quantity,entry.getKey(),quantity)!=1)throw new BusinessException("STOCK_LEDGER_ERROR","商品锁定库存不足，批量核销已取消");
  }
  List<Map<String,Object>> updated=db.queryForList("select * from orders where id in ("+placeholders+")",ids.toArray());
  Map<Long,Map<String,Object>> byId=new HashMap<>();for(Map<String,Object> order:updated)byId.put(((Number)order.get("id")).longValue(),order);
  List<Map<String,Object>> ordered=ids.stream().map(byId::get).filter(Objects::nonNull).toList();
  Map<String,Object> result=new LinkedHashMap<>();result.put("pickedUp",ordered.size());result.put("orders",ordered);return result;
 }
 @Transactional public Map<String,Object> cancelOrders(Collection<Long> orderIds,String reason,Long adminId){
  if(orderIds==null||orderIds.isEmpty())throw new BusinessException("INVALID_ORDER_IDS","请选择要取消的订单");
  if(orderIds.size()>500)throw new BusinessException("TOO_MANY_ORDERS","一次最多取消500个订单");
  LinkedHashSet<Long> uniqueIds=new LinkedHashSet<>();
  for(Long id:orderIds)if(id==null||id<=0||!uniqueIds.add(id))throw new BusinessException("INVALID_ORDER_IDS","订单列表包含无效或重复订单");
  if(reason==null||reason.isBlank())throw new BusinessException("INVALID_CANCEL_REASON","必须填写取消原因");
  if(reason.codePointCount(0,reason.length())>300)throw new BusinessException("INVALID_CANCEL_REASON","取消原因不能超过300个字符");
  List<Long> ids=new ArrayList<>(uniqueIds);
  String placeholders=String.join(",",Collections.nCopies(ids.size(),"?"));
  List<Map<String,Object>> locked=db.queryForList("select id,status from orders where id in ("+placeholders+") order by id for update",ids.toArray());
  if(locked.size()!=ids.size())throw new BusinessException("ORDER_NOT_FOUND","部分订单不存在，请刷新列表后重试");
  for(Map<String,Object> order:locked)if(!"PENDING".equals(order.get("status")))throw new BusinessException("ORDER_FINAL","部分订单已处理，请刷新列表后重试");

  List<Map<String,Object>> cancelled=new ArrayList<>();
  for(Long id:ids)cancelled.add(cancelOrder(id,reason,adminId));
  Map<String,Object> result=new LinkedHashMap<>();result.put("cancelled",cancelled.size());result.put("orders",cancelled);return result;
 }
 @Transactional public Map<String,Object> cancelOrder(Long oid,String reason,Long adminId){if(reason!=null&&reason.codePointCount(0,reason.length())>300)throw new BusinessException("INVALID_CANCEL_REASON","取消原因不能超过300个字符");Map<String,Object>o=one("select * from orders where id=? for update",oid);if(!"PENDING".equals(o.get("status")))throw new BusinessException("ORDER_FINAL","订单已处理");db.update("update orders set status='CANCELED',canceled_at=?,cancel_reason=?,operator_admin_id=? where id=?",LocalDateTime.now(zone),reason,adminId,oid);db.update("update products set stock=stock+1,locked_stock=locked_stock-1 where id=? and locked_stock>0",o.get("product_id"));refund(oid,((Number)o.get("user_id")).longValue(),String.valueOf(o.get("product_name")));return one("select * from orders where id=?",oid);}
 @Scheduled(cron="0 10 0 * * *",zone="Asia/Shanghai") public void scheduled(){expireOrders();expireAll();}
 public void expireOrders(){for(Map<String,Object>o:db.queryForList("select id from orders where status='PENDING' and expire_at<=now()"))try{cancelOrder(((Number)o.get("id")).longValue(),"逾期未领取",null);}catch(RuntimeException ignored){}}
 public void expireAll(){for(Map<String,Object>u:db.queryForList("select distinct user_id from point_lots where available_amount>0 and expires_at<=now()"))expireUser(((Number)u.get("user_id")).longValue());}
 @Transactional public void expireUser(Long uid){List<Map<String,Object>> lots=db.queryForList("select * from point_lots where user_id=? and available_amount>0 and expires_at<=now() for update",uid);int n=lots.stream().mapToInt(x->((Number)x.get("available_amount")).intValue()).sum();if(n==0)return;db.update("update point_lots set available_amount=0 where user_id=? and available_amount>0 and expires_at<=now()",uid);db.update("update users set points=greatest(points-?,0) where id=?",n,uid);insertLog(uid,-n,"EXPIRED","YEAR",String.valueOf(Year.now(zone).getValue()-1),"expire:"+uid+":"+LocalDate.now(zone),"年度积分到期清零",null,null);}

 @Transactional public Map<String,Object> createOrUpdateUser(String openid,String nickname,String avatar,String phone){String nNickname=nonNickname(nickname),nAvatar=nonBlank(avatar),nPhone=nonBlank(phone);Map<String,Object>u=first("select * from users where openid=? for update",openid);if(u==null){KeyHolder kh=new GeneratedKeyHolder();db.update(c->{PreparedStatement ps=c.prepareStatement("insert into users(openid,nickname,avatar_url,phone,points) values(?,?,?,?,0)",Statement.RETURN_GENERATED_KEYS);ps.setString(1,openid);ps.setString(2,nNickname);ps.setString(3,nAvatar);ps.setString(4,nPhone);return ps;},kh);Long id=Objects.requireNonNull(kh.getKey()).longValue();if(nPhone!=null)award(id,100,"REGISTER","USER",String.valueOf(id),"register:"+id,"首次注册小程序",null);return profile(id);}Long id=((Number)u.get("id")).longValue();db.update("update users set nickname=coalesce(?,nickname),avatar_url=coalesce(?,avatar_url),phone=coalesce(?,phone) where id=?",nNickname,nAvatar,nPhone,id);if(nPhone!=null)award(id,100,"REGISTER","USER",String.valueOf(id),"register:"+id,"首次注册小程序",null);return profile(id);}
 @Transactional public Map<String,Object> bindPhone(Long uid,String phone){
  String normalized=nonBlank(phone);if(normalized==null)throw new BusinessException("PHONE_REQUIRED","请授权手机号");
  Map<String,Object> current=one("select * from users where id=? for update",uid);String openid=String.valueOf(current.get("openid"));
  Map<String,Object> existing=first("select * from users where phone=? and id<>? for update",normalized,uid);
  if(existing!=null){
   Long existingId=((Number)existing.get("id")).longValue();
   // Keep the phone owner's account and rebind this WeChat openid to it.
   db.update("update users set openid=? where id=?", "unlinked:"+uid+":"+UUID.randomUUID(), uid);
   db.update("update users set openid=?,phone=? where id=?", openid, normalized, existingId);
   award(existingId,100,"REGISTER","USER",String.valueOf(existingId),"register:"+existingId,"首次注册小程序",null);
   Map<String,Object> result=new LinkedHashMap<>(profile(existingId));
   result.put("phoneAlreadyRegistered",true);
   return result;
  }
  Map<String,Object> result=new LinkedHashMap<>(createOrUpdateUser(openid,null,null,normalized));
  result.put("phoneAlreadyRegistered",false);
  return result;
 }
 private String nonBlank(String value){return value==null||value.isBlank()?null:value.trim();}
 private String nonNickname(String value){String normalized=nonBlank(value);return normalized!=null&&Set.of("微信用户","测试用户","未设置昵称").contains(normalized)?null:normalized;}
 @Transactional public Map<String,Object> adminAdjust(Long uid,int delta,String reason,Long adminId){if(delta==0)throw new BusinessException("INVALID_POINTS","调整值不能为0");String key="admin:"+adminId+":"+UUID.randomUUID();if(delta>0)award(uid,delta,"ADMIN_ADD","ADMIN",String.valueOf(adminId),key,"管理员增加："+reason,adminId);else{expireUser(uid);Map<String,Object>u=one("select * from users where id=? for update",uid);if(((Number)u.get("points")).intValue()<-delta)throw new BusinessException("INSUFFICIENT_POINTS","扣减值超过余额");int left=-delta;for(Map<String,Object>lot:db.queryForList("select * from point_lots where user_id=? and available_amount>0 and expires_at>now() order by expires_at,id for update",uid)){int take=Math.min(left,((Number)lot.get("available_amount")).intValue());db.update("update point_lots set available_amount=available_amount-? where id=?",take,lot.get("id"));left-=take;if(left==0)break;}db.update("update users set points=points+? where id=?",delta,uid);insertLog(uid,delta,"ADMIN_SUBTRACT","ADMIN",String.valueOf(adminId),key,"管理员减少："+reason,adminId,null);}return profile(uid);}
 @Transactional public Map<String,Object> manualCheckIn(Long uid,Long aid){Map<String,Object>a=activity(aid,uid);int n=db.update("update activity_participations set status='CHECKED_IN',checked_in_at=?,checkin_method='ADMIN' where user_id=? and activity_id=? and status='REGISTERED'",LocalDateTime.now(zone),uid,aid);if(n==0){Map<String,Object>p=one("select * from activity_participations where user_id=? and activity_id=?",uid,aid);if(!"CHECKED_IN".equals(p.get("status")))throw new BusinessException("NOT_REGISTERED","用户未报名");return p;}award(uid,100,"ACTIVITY","ACTIVITY",String.valueOf(aid),"activity:"+aid+":user:"+uid,"参加“"+a.get("title")+"”管理员补签奖励",null);return one("select * from activity_participations where user_id=? and activity_id=?",uid,aid);}

 private void award(Long uid,int amount,String type,String bizType,String bizId,String key,String remark,Long admin){if(count("select count(*) from point_logs where idempotency_key=?",key)>0)return;issueAward(uid,amount,type,bizType,bizId,key,remark,admin);}
 private void issueAward(Long uid,int amount,String type,String bizType,String bizId,String key,String remark,Long admin){one("select id from users where id=? for update",uid);db.update("update users set points=points+? where id=?",amount,uid);LocalDateTime expiry=LocalDate.of(LocalDate.now(zone).getYear(),12,31).atTime(23,59,59);Long logId=insertLog(uid,amount,type,bizType,bizId,key,remark,admin,expiry);db.update("insert into point_lots(user_id,source_log_id,original_amount,available_amount,expires_at) values(?,?,?,?,?)",uid,logId,amount,amount,expiry);}
 private void spend(Long uid,int amount,Long orderId,String key,String remark){expireUser(uid);Map<String,Object>u=one("select * from users where id=? for update",uid);if(((Number)u.get("points")).intValue()<amount)throw new BusinessException("INSUFFICIENT_POINTS","积分不足");int left=amount;for(Map<String,Object>lot:db.queryForList("select * from point_lots where user_id=? and available_amount>0 and expires_at>now() order by expires_at,id for update",uid)){int take=Math.min(left,((Number)lot.get("available_amount")).intValue());db.update("update point_lots set available_amount=available_amount-? where id=?",take,lot.get("id"));db.update("insert into point_lot_usages(order_id,lot_id,amount) values(?,?,?)",orderId,lot.get("id"),take);left-=take;if(left==0)break;}if(left>0)throw new BusinessException("POINT_LEDGER_ERROR","积分批次不足，请联系管理员");db.update("update users set points=points-? where id=?",amount,uid);insertLog(uid,-amount,"EXCHANGE","ORDER",String.valueOf(orderId),key,remark,null,null);}
 private void refund(Long oid,Long uid,String product){int restored=0,expired=0;for(Map<String,Object>x:db.queryForList("select u.*,l.expires_at from point_lot_usages u join point_lots l on l.id=u.lot_id where u.order_id=? for update",oid)){int amount=((Number)x.get("amount")).intValue()-((Number)x.get("refunded_amount")).intValue();if(amount<=0)continue;if(toDateTime(x.get("expires_at")).isAfter(LocalDateTime.now(zone))){db.update("update point_lots set available_amount=available_amount+? where id=?",amount,x.get("lot_id"));restored+=amount;}else expired+=amount;db.update("update point_lot_usages set refunded_amount=refunded_amount+? where id=?",amount,x.get("id"));}if(restored>0)db.update("update users set points=points+? where id=?",restored,uid);String remark="“"+product+"”订单取消退还积分"+(expired>0?"，其中"+expired+"积分已过期":"");if(remark.length()>200)remark=remark.substring(0,200);insertLog(uid,restored,"EXCHANGE_CANCEL","ORDER",String.valueOf(oid),"refund:"+oid,remark,null,null);}
 private Long insertLog(Long uid,int amount,String type,String bizType,String bizId,String key,String remark,Long admin,LocalDateTime expiry){int balance=db.queryForObject("select points from users where id=?",Integer.class,uid);KeyHolder kh=new GeneratedKeyHolder();db.update(c->{PreparedStatement ps=c.prepareStatement("insert into point_logs(user_id,amount,balance,type,remark,biz_type,biz_id,idempotency_key,operator_admin_id,expires_at) values(?,?,?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);ps.setLong(1,uid);ps.setInt(2,amount);ps.setInt(3,balance);ps.setString(4,type);ps.setString(5,remark);ps.setString(6,bizType);ps.setString(7,bizId);ps.setString(8,key);if(admin==null)ps.setNull(9,Types.BIGINT);else ps.setLong(9,admin);if(expiry==null)ps.setNull(10,Types.TIMESTAMP);else ps.setTimestamp(10,Timestamp.valueOf(expiry));return ps;},kh);return Objects.requireNonNull(kh.getKey()).longValue();}
 private void validateAnswers(Long qid,Object answers){if(!(answers instanceof Map<?,?> map))throw new BusinessException("INVALID_ANSWERS","请完成问卷");for(Map<String,Object>q:db.queryForList("select id,title from questionnaire_questions where questionnaire_id=? and required=true",qid))if(!map.containsKey(String.valueOf(q.get("id")))||map.get(String.valueOf(q.get("id")))==null||String.valueOf(map.get(String.valueOf(q.get("id")))).isBlank())throw new BusinessException("REQUIRED_QUESTION","请完成必填题："+q.get("title"));}
 private String profileText(Map<String,Object> details,String key,Object existing){
  if(!details.containsKey(key))return textOrNull(existing);
  Object value=details.get(key);return textOrNull(value);
 }
 private String profileTextPreserve(Map<String,Object> details,String key,Object existing){
  String value=details.containsKey(key)?textOrNull(details.get(key)):null;
  return value==null?textOrNull(existing):value;
 }
 private String profileNicknamePreserve(Map<String,Object> details,String key,Object existing){
  Object raw=details.get(key);String value=details.containsKey(key)&&raw!=null?nonNickname(String.valueOf(raw)):null;
  return value==null?textOrNull(existing):value;
 }
 private String textOrNull(Object value){if(value==null)return null;String text=String.valueOf(value).trim();return text.isEmpty()?null:text;}
 private Integer profileAge(Map<String,Object> details,Object existing){
  Object value=details.containsKey("age")?details.get("age"):existing;
  if(value==null||String.valueOf(value).trim().isEmpty())return null;
  final int age;
  try{age=Integer.parseInt(String.valueOf(value).trim());}catch(Exception e){throw new BusinessException("INVALID_PROFILE_AGE","年龄必须是1到150的整数");}
  if(age<1||age>150)throw new BusinessException("INVALID_PROFILE_AGE","年龄必须是1到150的整数");
  return age;
 }
 private boolean profileComplete(Map<String,Object> user){return profileComplete(textOrNull(user.get("real_name")),asInteger(user.get("age")),textOrNull(user.get("graduation_school")),textOrNull(user.get("major_direction")),textOrNull(user.get("city")));}
 private boolean profileComplete(String realName,Integer age,String graduationSchool,String majorDirection,String city){return realName!=null&&age!=null&&graduationSchool!=null&&majorDirection!=null&&city!=null;}
 private Integer asInteger(Object value){if(value==null)return null;if(value instanceof Number n)return n.intValue();try{return Integer.valueOf(String.valueOf(value));}catch(Exception e){return null;}}
 private Boolean asBoolean(Object value){if(value==null)return false;if(value instanceof Boolean b)return b;if(value instanceof Number n)return n.intValue()!=0;return Boolean.parseBoolean(String.valueOf(value));}
 private Map<String,Object> one(String sql,Object...a){try{return db.queryForMap(sql,a);}catch(EmptyResultDataAccessException e){throw new BusinessException("NOT_FOUND","数据不存在");}} private Map<String,Object> first(String sql,Object...a){List<Map<String,Object>>r=db.queryForList(sql,a);return r.isEmpty()?null:r.get(0);} private int count(String sql,Object...a){return db.queryForObject(sql,Integer.class,a);} private String write(Object o){try{return json.writeValueAsString(o);}catch(JsonProcessingException e){throw new BusinessException("INVALID_DATA","数据格式错误");}}
 private boolean before(LocalDateTime n,Object v){return v!=null&&n.isBefore(toDateTime(v));} private boolean after(LocalDateTime n,Object v){return v!=null&&n.isAfter(toDateTime(v));} static LocalDateTime toDateTime(Object v){if(v instanceof Timestamp t)return t.toLocalDateTime();if(v instanceof LocalDateTime d)return d;throw new IllegalArgumentException("Unsupported database datetime type: "+(v==null?"null":v.getClass().getName()));} private LocalDate toDate(Object v){return v instanceof java.sql.Date d?d.toLocalDate():(LocalDate)v;}
 private long parseLong(String v){try{return Long.parseLong(v);}catch(Exception e){throw new BusinessException("INVALID_QR","核销码无效");}} private Long parseLongObj(String v){return parseLong(v);} private String sign(String b){try{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(qrSecret,"HmacSHA256"));return Base64.getUrlEncoder().withoutPadding().encodeToString(m.doFinal(b.getBytes(StandardCharsets.UTF_8))).substring(0,22);}catch(Exception e){throw new IllegalStateException(e);}} private boolean constant(String a,String b){return java.security.MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));}
}
