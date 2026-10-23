package com.wx.community.config;
import com.wx.community.security.AdminPasswordPolicy;
import org.springframework.beans.factory.annotation.Value; import org.springframework.boot.ApplicationRunner; import org.springframework.context.annotation.*; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.security.crypto.password.PasswordEncoder;
@Configuration public class BootstrapConfig {
 @Bean ApplicationRunner adminBootstrap(JdbcTemplate db,PasswordEncoder encoder,@Value("${app.admin.username:admin}")String username,@Value("${app.admin.password:}")String password){return args->{if(db.queryForObject("select count(*) from admins",Integer.class)==0&&password!=null&&!password.isBlank()){AdminPasswordPolicy.validate(password);db.update("insert into admins(username,password_hash) values(?,?)",username,encoder.encode(password));}};}
}
