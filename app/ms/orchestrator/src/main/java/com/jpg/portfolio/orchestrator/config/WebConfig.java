package com.jpg.portfolio.orchestrator.config;

import com.jpg.portfolio.orchestrator.security.JwtInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    public WebConfig(JwtInterceptor jwtInterceptor) {
        this.jwtInterceptor = jwtInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Enforce JWT validation strictly on protected administrative paths, excluding public assets
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns(
                        "/admin/pages/**",
                        "/admin/css/**",
                        "/admin/js/**",
                        "/admin/index.html",
                        "/admin/favicon.ico"
                );
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve static resources from classpath:/static/ under the /admin/ path prefix
        registry.addResourceHandler("/admin/**")
                .addResourceLocations("classpath:/static/");
    }
}

