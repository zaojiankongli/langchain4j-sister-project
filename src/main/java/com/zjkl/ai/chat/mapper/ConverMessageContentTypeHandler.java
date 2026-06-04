package com.zjkl.ai.chat.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjkl.ai.chat.entity.MessageContent;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.apache.ibatis.type.TypeHandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * MyBatis TypeHandler，用于将 List<MessageContent> 与 JSON 互相转换
 *
 * 注意：MyBatis TypeHandler 实例化由 MyBatis 管理（非 Spring Bean），无法直接注入 Spring ObjectMapper。
 * 此处使用独立 ObjectMapper 并配置与 Spring 一致的容错设置。
 *
 * TODO: 如果后续需要将此 TypeHandler 注册为 Spring Bean（例如通过 MyBatis-Plus @Bean 注册），
 *       应改为注入 Spring 管理的 ObjectMapper，避免维护两套序列化配置。
 *       当前可通过 SqlSessionFactoryBean.setTypeHandlers() 手动注册 Spring Bean 实例。
 */
@Slf4j
@MappedTypes(List.class)
public class ConverMessageContentTypeHandler implements TypeHandler<List<MessageContent>> {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    @Override
    public void setParameter(PreparedStatement ps, int i, List<MessageContent> parameter, JdbcType jdbcType) throws SQLException {
        if (parameter == null) {
            ps.setString(i, "[]");
        } else {
            try {
                ps.setString(i, objectMapper.writeValueAsString(parameter));
            } catch (JsonProcessingException e) {
                log.warn("序列化 MessageContent 列表失败，回退为空数组", e);
                ps.setString(i, "[]");
            }
        }
    }

    @Override
    public List<MessageContent> getResult(ResultSet rs, String columnName) throws SQLException {
        String json = rs.getString(columnName);
        return parseJson(json);
    }

    @Override
    public List<MessageContent> getResult(ResultSet rs, int columnIndex) throws SQLException {
        String json = rs.getString(columnIndex);
        return parseJson(json);
    }

    @Override
    public List<MessageContent> getResult(CallableStatement cs, int columnIndex) throws SQLException {
        String json = cs.getString(columnIndex);
        return parseJson(json);
    }

    private List<MessageContent> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<MessageContent>>() {});
        } catch (JsonProcessingException e) {
            log.warn("反序列化 MessageContent 列表失败，返回空列表: json={}", json, e);
            return List.of();
        }
    }
}
