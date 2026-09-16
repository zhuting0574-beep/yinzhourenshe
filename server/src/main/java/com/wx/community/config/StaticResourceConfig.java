package com.wx.community.config;
import org.springframework.beans.factory.annotation.Value; import org.springframework.context.annotation.Configuration; import org.springframework.web.servlet.config.annotation.*;
import java.nio.file.Path;
@Configuration public class StaticResourceConfig implements WebMvcConfigurer { private final String upload; public StaticResourceConfig(@Value("${app.upload-dir:./uploads}")String upload){this.upload=upload;} public void addResourceHandlers(ResourceHandlerRegistry r){r.addResourceHandler("/uploads/**").addResourceLocations(Path.of(upload).toAbsolutePath().normalize().toUri().toString());}}
