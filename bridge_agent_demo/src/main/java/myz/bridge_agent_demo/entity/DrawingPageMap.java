package myz.bridge_agent_demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对应表 {@code drawing_page_map}：一份图纸一份页地图与未确认摘录。
 * 删 {@code project_file} 时外键级联删除本行。
 */
@Data
@TableName("drawing_page_map")
public class DrawingPageMap {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long fileId;
    private String sha256;
    /** pages / extracts / gaps 等 JSON */
    private String mapJson;
    private LocalDateTime updatedAt;
}
