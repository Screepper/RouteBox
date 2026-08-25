package com.routebox.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.InputStream;

/**
 * @Description JSON Tool
 * @Date 2025/9/12
**/
public class JsonUtil {

    private static final ObjectMapper OBJECT_MAPPER;

    static {
        // Configure JSON ObjectMapper
        OBJECT_MAPPER = new ObjectMapper();
        OBJECT_MAPPER.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        OBJECT_MAPPER.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        OBJECT_MAPPER.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
    }

    public static String toJson(Object obj) {
        if (obj == null) return null;

        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.trim().isEmpty()) return null;

        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T convertValue(Object fromValue, Class<T> toValueType) {
        if (fromValue == null) return null;
        return OBJECT_MAPPER.convertValue(fromValue, toValueType);
    }

    public static <T> T fromJson(String json, TypeReference<T> typeRef) {
        if (json == null || json.trim().isEmpty()) return null;

        try {
            return OBJECT_MAPPER.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T[] fromJsonToArray(String json, Class<T> arrayClazz) {
        if (json == null || json.trim().isEmpty()) return null;

        try {
            return OBJECT_MAPPER.readValue(json, OBJECT_MAPPER.getTypeFactory().constructArrayType(arrayClazz));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T fromJson(InputStream inputStream, Class<T> clazz) {
        if (inputStream == null) return null;

        try {
            return OBJECT_MAPPER.readValue(inputStream, clazz);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T fromJson(InputStream inputStream, TypeReference<T> typeRef) {
        if (inputStream == null) return null;

        try {
            return OBJECT_MAPPER.readValue(inputStream, typeRef);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T fromJsonThrowError(String json, Class<T> clazz) throws JsonProcessingException {
        return (json == null || json.trim().isEmpty()) ? null : OBJECT_MAPPER.readValue(json, clazz);
    }
}