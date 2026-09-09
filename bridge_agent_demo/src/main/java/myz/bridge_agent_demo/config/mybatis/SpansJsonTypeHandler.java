package myz.bridge_agent_demo.config.mybatis;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * MyBatis 类型处理器：Java 的 {@code List<BigDecimal>} ↔ MySQL JSON 列。
 * <p>
 * 库里没有 list 类型，跨径存在 {@code project_unit.spans_m} 的 JSON 数组里，例如 {@code [40, 60, 40]}。
 */
@MappedTypes(List.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class SpansJsonTypeHandler extends BaseTypeHandler<List<BigDecimal>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<BigDecimal>> TYPE = new TypeReference<>() {
    };

    /** 写入：List 序列化成 JSON 字符串交给 JDBC */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<BigDecimal> parameter, JdbcType jdbcType)
            throws SQLException {
        try {
            ps.setString(i, MAPPER.writeValueAsString(parameter));
        } catch (JacksonException e) {
            throw new SQLException("跨径 JSON 序列化失败", e);
        }
    }

    @Override
    public List<BigDecimal> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public List<BigDecimal> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public List<BigDecimal> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private List<BigDecimal> parse(String json) throws SQLException {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(json, TYPE);
        } catch (JacksonException e) {
            throw new SQLException("跨径 JSON 解析失败: " + json, e);
        }
    }
}
