package myz.bridge_agent_demo.config.mybatis;

import myz.bridge_agent_demo.entity.ProjectFieldMeta;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MyBatis 类型处理器：{@link ProjectFieldMeta} ↔ {@code project.field_meta} JSON 列。
 */
@MappedTypes(ProjectFieldMeta.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class FieldMetaJsonTypeHandler extends BaseTypeHandler<ProjectFieldMeta> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, ProjectFieldMeta parameter, JdbcType jdbcType)
            throws SQLException {
        try {
            ps.setString(i, MAPPER.writeValueAsString(parameter));
        } catch (JacksonException e) {
            throw new SQLException("字段来源 JSON 序列化失败", e);
        }
    }

    @Override
    public ProjectFieldMeta getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public ProjectFieldMeta getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public ProjectFieldMeta getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private ProjectFieldMeta parse(String json) throws SQLException {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, ProjectFieldMeta.class);
        } catch (JacksonException e) {
            throw new SQLException("字段来源 JSON 解析失败: " + json, e);
        }
    }
}
