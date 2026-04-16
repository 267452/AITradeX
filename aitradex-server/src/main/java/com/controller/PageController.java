package com.controller;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Controller
public class PageController {

    private static final String FRONTEND_PAGES_PATH = "/Users/wangyan/Desktop/AITradeX/frontend/src/pages";

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String indexPage() throws IOException {
        FileSystemResource resource = new FileSystemResource(FRONTEND_PAGES_PATH + "/index.html");
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }

    @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String loginPage() throws IOException {
        FileSystemResource resource = new FileSystemResource(FRONTEND_PAGES_PATH + "/login.html");
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }

    @GetMapping(value = "/register", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String registerPage() throws IOException {
        FileSystemResource resource = new FileSystemResource(FRONTEND_PAGES_PATH + "/register.html");
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }

    @GetMapping(value = "/dashboard", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String dashboardPage() throws IOException {
        FileSystemResource resource = new FileSystemResource(FRONTEND_PAGES_PATH + "/index.html");
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
}