package com.routebox.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.routebox.entity.sernes.HttpErrorResponse;
import com.routebox.entity.sernes.HttpResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @Description Http Request Util
 * @Date 2025/4/24
 **/
@SuppressWarnings("ALL")
@Slf4j
public class HttpUtil {
    private static final CloseableHttpClient httpClient = HttpClients.createDefault();

    public enum HttpOptions {
        QUICK_TIMEOUT,
        VERY_LONG_TIMEOUT,
        NO_TIMEOUT,
    }

    public static String get(String url, HttpOptions... options) {
        return get(url, null, null, options);
    }

    public static <T> T get(String url, Class<T> returnType, HttpOptions... options) {
        return get(url, null, returnType, options);
    }

    public static String get(String url, Map<String, Object> params, HttpOptions... options) {
        return get(url, params, null, options);
    }

    /**
     * @Description Get Request
     * @Date 2025/4/24
     **/
    public static <T> T get(String url, Map<String, Object> params, Class<T> returnType, HttpOptions... options) {
        if (params != null) {
            url += "?";
            StringBuilder urlBuilder = new StringBuilder(url);
            List<String> keys = new ArrayList<>(params.keySet());

            for (int i = 0; i < keys.size(); i++) {
                String key = keys.get(i);
                Object value = params.get(key);
                urlBuilder.append(key).append("=").append(JsonUtil.toJson(value));
                if (i < keys.size() - 1) {
                    urlBuilder.append("&");
                }
            }

            url = urlBuilder.toString();
        }

        try {
            HttpGet httpGet = new HttpGet(url);
            httpGet.setConfig(buildRequestConfig(options));

            return httpClient.execute(httpGet, response -> {
                if (checkBadResponse(httpGet.getRequestUri(), response) || returnType == null) return null;
                if (returnType.equals(InputStream.class)) return (T) response.getEntity().getContent();
                if (returnType.equals(byte[].class)) return (T) EntityUtils.toByteArray(response.getEntity());

                try { // expect default {status: "success", data: "{}"}
                    HttpResponse httpRes = JsonUtil.fromJson(EntityUtils.toString(response.getEntity()), HttpResponse.class);
                    return JsonUtil.convertValue(httpRes.getData(), returnType);
                } catch (Exception e) { // degradation data-self {}
                    return JsonUtil.fromJson(EntityUtils.toString(response.getEntity()), returnType);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String post(String url, HttpOptions... options) {
        return post(url, null, null, options);
    }

    public static <T> T post(String url, Class<T> returnType, HttpOptions... options) {
        return post(url, null, returnType, options);
    }

    public static String post(String url, Object data, HttpOptions... options) {
        return post(url, data, null, options);
    }

    /**
     * @Description Post Request
     * @Date 2025/4/24
     **/
    public static <T> T post(String url, Object data, Class<T> returnType, HttpOptions... options) {
        try {
            HttpPost httpPost = new HttpPost(url);
            httpPost.setConfig(buildRequestConfig(options));

            if (data != null) {
                String jsonRequest = JsonUtil.toJson(data);
                httpPost.setEntity(new StringEntity(jsonRequest, ContentType.APPLICATION_JSON));
            }

            return httpClient.execute(httpPost, response -> {
                if (checkBadResponse(httpPost.getRequestUri(), response) || returnType == null) return null;
                if (returnType.equals(InputStream.class)) return (T) response.getEntity().getContent();
                if (returnType.equals(byte[].class)) return (T) EntityUtils.toByteArray(response.getEntity());

                try { // expect default {status: "success", data: "{}"}
                    HttpResponse httpRes = JsonUtil.fromJson(EntityUtils.toString(response.getEntity()), HttpResponse.class);
                    return JsonUtil.convertValue(httpRes.getData(), returnType);
                } catch (Exception e) { // degradation data-self {}
                    return JsonUtil.fromJson(EntityUtils.toString(response.getEntity()), returnType);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @Description Post Request with TypeReference
     * @Date 2025/4/24
     **/
    public static <T> T typeRefPost(String url, Object data, TypeReference<T> typeRef, HttpOptions... options) {
        try {
            HttpPost httpPost = new HttpPost(url);
            httpPost.setConfig(buildRequestConfig(options));

            if (data != null) {
                String jsonRequest = JsonUtil.toJson(data);
                httpPost.setEntity(new StringEntity(jsonRequest, ContentType.APPLICATION_JSON));
            }

            return httpClient.execute(httpPost, response -> {
                if (checkBadResponse(httpPost.getRequestUri(), response) || typeRef == null) return null;
                return JsonUtil.fromJson(response.getEntity().getContent(), typeRef);
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static RequestConfig buildRequestConfig(HttpOptions... options) {
        RequestConfig.Builder custom = RequestConfig.custom();

        for (HttpOptions option : options) {
            if (option == HttpOptions.QUICK_TIMEOUT) {
                custom.setConnectionRequestTimeout(Timeout.of(5, TimeUnit.SECONDS)).setResponseTimeout(Timeout.of(5, TimeUnit.SECONDS)).build();
            } else if (option == HttpOptions.VERY_LONG_TIMEOUT) {
                custom.setConnectionRequestTimeout(Timeout.of(600, TimeUnit.SECONDS)).setResponseTimeout(Timeout.of(600, TimeUnit.SECONDS)).build();
            } else if (option == HttpOptions.NO_TIMEOUT) {
                custom.setConnectionRequestTimeout(Timeout.of(1800, TimeUnit.SECONDS)).setResponseTimeout(Timeout.of(1800, TimeUnit.SECONDS)).build();
            }
        }

        return custom.build();
    }

    private static boolean checkBadResponse(String requestUri, ClassicHttpResponse response) {
        try {
            if (response.getCode() == 200) return false; // success
            if (response.getCode() == 400 || response.getCode() == 500) { // bad request
                HttpErrorResponse errorResponse = JsonUtil.fromJson(response.getEntity().getContent(), HttpErrorResponse.class);
                log.error("Bad response '{}' -> (Status: {} Msg: {})", requestUri, response.getCode(), errorResponse.getMsg());
            } else {
                log.error("Unknown error '{}' -> {}", requestUri, response.getReasonPhrase());
            }
            return true;
        } catch (IOException e) {
            log.error("Error while parsing bad response {} -> {}", requestUri, e.getMessage());
            return true;
        }
    }
}