package myz.bridge_agent_demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO：任务页起草或问询落卡。同意前可改这些字段。
 */
@Data
public class TaskCardRequest {

    @NotBlank(message = "请选择工种")
    @Size(max = 32)
    private String kind;

    private Long fileId;

    private List<String> pageKinds = new ArrayList<>();

    private Integer unitSeq;

    @Size(max = 32)
    private String supportCode;

    @Size(max = 500, message = "本轮指令不能超过500字")
    private String directive;

    @Size(max = 512)
    private String proposeReason;
}
