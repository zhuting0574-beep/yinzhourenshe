package com.wx.community.security;
import jakarta.servlet.*; import jakarta.servlet.http.*;
import org.springframework.context.annotation.*; import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.*; import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*; import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import java.io.IOException; import java.util.List;
@Configuration public class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
 @Bean SecurityFilterChain filter(HttpSecurity h,TokenFilter f)throws Exception{return h.csrf(x->x.disable()).cors(x->{}).sessionManagement(x->x.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).authorizeHttpRequests(x->x
  .requestMatchers("/api/mini/auth/**","/api/admin/auth/login","/api/dev/**","/uploads/**").permitAll()
  .requestMatchers(HttpMethod.GET,"/api/admin/activities/*/qr.png").permitAll()
  .requestMatchers(HttpMethod.GET,"/api/mini/home","/api/mini/activities/**","/api/mini/products/**","/api/mini/content/**").permitAll()
  .requestMatchers("/api/admin/**").hasRole("ADMIN").anyRequest().authenticated()).addFilterBefore(f,UsernamePasswordAuthenticationFilter.class).build();}
}
@Component class TokenFilter extends org.springframework.web.filter.OncePerRequestFilter {
 private final TokenService tokens; TokenFilter(TokenService t){tokens=t;}
 protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain c)throws ServletException,IOException{String h=r.getHeader("Authorization");if(h!=null&&h.startsWith("Bearer "))try{AuthUser u=tokens.parse(h.substring(7));var a=new UsernamePasswordAuthenticationToken(u,null,List.of(new SimpleGrantedAuthority("ROLE_"+u.role())));org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(a);}catch(RuntimeException ignored){}c.doFilter(r,s);}
}
