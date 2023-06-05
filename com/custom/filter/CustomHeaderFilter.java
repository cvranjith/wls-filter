package com.custom.filter;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
//import weblogic.logging.NonCatalogLogger;
import weblogic.diagnostics.context.DiagnosticContextHelper;

public class CustomHeaderFilter implements Filter {
    private Boolean dumpRequest = false;
    private Boolean dumpResponse = false;
    private Map<String, Map<String, String>> filterConfigurations;
    //private NonCatalogLogger logger;

    public void init(FilterConfig filterConfig) throws ServletException {
        filterConfigurations = new HashMap<>();
        Enumeration<String> filterInitParamNames = filterConfig.getInitParameterNames();
        String filterName = filterConfig.getFilterName();
        Map<String, String> initParams = new HashMap<>();
        while (filterInitParamNames.hasMoreElements()) {
            String paramName = filterInitParamNames.nextElement();
            String paramValue = filterConfig.getInitParameter(paramName);
            log("filterInitParamNames: paramName :" + paramName + "paramValue : "+ paramValue);
            if (paramName.startsWith("set.header.")) {
                String headerName = paramName.substring("set.header.".length());
                initParams.put(headerName, paramValue);
            }
            if ("dump.request".equals(paramName) && paramValue != null && paramValue.equals("true")) {
                dumpRequest = true;
            }
            if ("dump.response".equals(paramName) && paramValue != null && paramValue.equals("true")) {
                dumpResponse = true;
            }
        }
        if (!initParams.isEmpty()) {
            filterConfigurations.put(filterName, initParams);
        }
        //logger = new weblogic.logging.NonCatalogLogger("CustomHeaderFilter");
    }
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String ecid = getEcid();
        long requestStartTime = System.currentTimeMillis();
        if (dumpRequest) {
            dumpRequest(ecid, requestStartTime, httpRequest);
        }
        try {
            chain.doFilter(request, response);
        } catch (Exception e) {
            e.printStackTrace();
        }
        String filterName = getFilterNameFromConfig(httpRequest.getServletContext(), httpRequest.getServletPath());
        Map<String, String> initParams = filterConfigurations.get(filterName);
        if (initParams != null) {
            try {
                for (Map.Entry<String, String> entry : initParams.entrySet()) {
                    String headerName = entry.getKey();
                    String headerValue = entry.getValue();
                    if (!httpResponse.containsHeader(headerName)) {
                        log("Adding Header + " + headerName + ":" + headerValue);
                        httpResponse.addHeader(headerName, headerValue);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (response.getCharacterEncoding() == null) {
            response.setCharacterEncoding("UTF-8");
        }
        String headerName="Content-Type";
        if (httpResponse.containsHeader(headerName)){
            String headerValue = httpResponse.getHeader(headerName);
            if ((headerValue.equalsIgnoreCase("application/xml") || headerValue.equalsIgnoreCase("text/xml"))
                    && !headerValue.toLowerCase().contains("charset")) {
                headerValue += "; charset=utf-8";
                httpResponse.addHeader(headerName, headerValue);
            }
        }
        if (dumpResponse) {
            dumpResponse(ecid, requestStartTime, httpResponse);
        }
    }
    public void destroy() {
    }
    private String getEcid() {
        String ecid = DiagnosticContextHelper.getContextId();
        if (ecid == null || ecid.isEmpty()) {
            ecid = UUID.randomUUID().toString();
        }
        return ecid;
    }
    private String getFilterNameFromConfig(ServletContext servletContext, String servletPath) {
        String filterName = null;
        for (FilterRegistration registration : servletContext.getFilterRegistrations().values()) {
            for (String urlPattern : registration.getUrlPatternMappings()) {
                if (urlPattern.equals("/*") || urlPattern.equals(servletPath)) {
                    filterName = registration.getName();
                    break;
                }
            }
        }
        return filterName;
    }
    private void dumpRequest(String ecid, long requestStartTime, HttpServletRequest httpRequest) {
        try {
            log("dumpRequest: Request ID: " + ecid);
            log("Request Time : " + requestStartTime );
            log("Request Method: " + httpRequest.getMethod());
            log("Request URL: " + httpRequest.getRequestURL());
            log("Request Headers:");
            Enumeration<String> requestHeaderNames = httpRequest.getHeaderNames();
            while (requestHeaderNames.hasMoreElements()) {
                String headerName = requestHeaderNames.nextElement();
                String headerValue = httpRequest.getHeader(headerName);
                log(headerName + " : " + headerValue);
            }
            log("=============================");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void dumpResponse(String ecid, long requestStartTime, HttpServletResponse httpResponse) {
        try {
            log("dumpResponse: Request ID: " + ecid);
            log("Response Status Code: " + httpResponse.getStatus());
            log("Response Headers:");
            for (String headerName : httpResponse.getHeaderNames()) {
                String headerValue = httpResponse.getHeader(headerName);
                log(headerName + " : " + headerValue);
            }
            long elapsedTime = System.currentTimeMillis() - requestStartTime;
            log("Response Time : " + System.currentTimeMillis());
            log("Elapsed Time : " + elapsedTime);
            log("---------------------------------");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    void log(String str){
        System.out.println(str);
    }
}
