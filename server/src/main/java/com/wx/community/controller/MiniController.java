package com.wx.community.controller;
import com.wx.community.domain.*; import com.wx.community.service.CommunityService; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api") public class MiniController { private final CommunityService s; public MiniController(CommunityService s){this.s=s;}
 @GetMapping("/mini/home") public Map<String,Object> home(){return Map.of("banners",List.of(),"activities",s.activities(),"points",0);}
 @GetMapping("/mini/activities") public List<Activity> activities(){return s.activities();} @GetMapping("/mini/activities/{id}") public Activity activity(@PathVariable Long id){return s.activity(id);} @GetMapping("/mini/products") public List<Product> products(){return s.products();} @GetMapping("/mini/products/{id}") public Product product(@PathVariable Long id){return s.product(id);}
 @GetMapping("/mini/profile/{userId}") public User profile(@PathVariable Long userId){return s.user(userId);} @GetMapping("/mini/profile/{userId}/points") public List<PointLog> points(@PathVariable Long userId){return s.points(userId);} @GetMapping("/mini/orders/{userId}") public List<Order> orders(@PathVariable Long userId){return s.orders(userId);}
 @GetMapping("/mini/profile") public User defaultProfile(){return s.user(1L);} @GetMapping("/mini/orders") public List<Order> defaultOrders(){return s.orders(1L);}
 @PostMapping("/mini/check-ins/{userId}") public Map<String,Object> checkIn(@PathVariable Long userId){s.dailyCheckIn(userId);return Map.of("success",true);} @PostMapping("/mini/orders") public Order exchange(@RequestParam Long userId,@RequestParam Long productId){return s.exchange(userId,productId);}
}
