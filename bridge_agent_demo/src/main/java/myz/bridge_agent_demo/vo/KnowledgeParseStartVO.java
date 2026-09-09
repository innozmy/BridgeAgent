package myz.bridge_agent_demo.vo;

import lombok.Data;

/**
 * VO：知识库点「解析」的立即返回。{@code needConfirm=true} 时前端弹确认再带 force 重跑。
 */
@Data
public class KnowledgeParseStartVO {

    /** 已提交后台作业 */
    private boolean started;
    /** 已解析完成，须确认后全量重跑 */
    private boolean needConfirm;
}
