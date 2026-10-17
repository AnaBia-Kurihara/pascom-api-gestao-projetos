package br.org.pascom.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * ETag pelo conteúdo das páginas do front. O build do Railway grava a mesma data (1980) em
     * todos os arquivos, então o Last-Modified nunca mudava: o navegador perguntava "mudou desde
     * 1980?", recebia 304 e ficava pra sempre com o index.html antigo. Com o ETag (hash do conteúdo)
     * e o Last-Modified desligado (spring.web.resources.cache.use-last-modified=false), cada
     * versão nova chega sozinha e, sem mudança, continua respondendo 304 (rápido).
     */
    @org.springframework.context.annotation.Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<org.springframework.web.filter.ShallowEtagHeaderFilter> etagDoFront() {
        var registro = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(new org.springframework.web.filter.ShallowEtagHeaderFilter());
        registro.addUrlPatterns("/", "/index.html", "/privacidade.html", "/musicos.html");
        registro.setName("etagDoFront");
        return registro;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*") // Permite chamadas do front-end local ou hospedado
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }
}