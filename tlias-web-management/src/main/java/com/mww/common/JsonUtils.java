package com.mww.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * 项目内统一的 JSON 工具。
 * <p>
 * 缓存、队列都走 JSON 文本，用同一个 ObjectMapper 可以保证两边对
 * {@code LocalDateTime}、{@code LocalDate} 的处理方式完全一致。
 * <p>
 * 用 {@code StringRedisTemplate} 存纯文本而不是 {@code GenericJackson2JsonRedisSerializer}，
 * 好处是 redis-cli 里直接能看到人类可读的内容，排查问题不用额外的反序列化工具；
 * 代价是反序列化时要显式给出目标类型（{@link TypeReference}），但这也让类型更明确。
 */
public final class JsonUtils {

    /** 用 JsonMapper.builder() 构建（ObjectMapper 的部分 setter 在 Jackson 2.19 已标记弃用） */
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            // 支持 LocalDate / LocalDateTime
            .addModule(new JavaTimeModule())
            // 时间序列化成 ISO-8601 字符串，而不是时间戳数组
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            // 没有 getter 的空对象不要抛异常
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            // 缓存里字段多了（比如升级后加了字段），反序列化不要炸
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private JsonUtils() {
    }

    /**
     * 序列化。失败返回 null（由调用方决定降级策略），不抛异常。
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 反序列化为简单类型。
     *
     * @throws IllegalStateException JSON 格式非法（调用方需捕获，避免脏数据污染缓存）
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 反序列化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 反序列化为泛型类型，例如 {@code new TypeReference<List<Dept>>() {}}。
     */
    public static <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 反序列化失败: " + e.getMessage(), e);
        }
    }
}
