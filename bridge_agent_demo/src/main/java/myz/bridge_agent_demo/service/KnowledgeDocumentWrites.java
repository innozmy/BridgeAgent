package myz.bridge_agent_demo.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import myz.bridge_agent_demo.entity.KnowledgeDocument;
import myz.bridge_agent_demo.mapper.KnowledgeDocumentMapper;
import org.springframework.stereotype.Component;

/**
 * Service：知识文献只改状态列，禁止 {@code updateById} 整行覆盖元数据或其它四步状态。
 */
@Component
@RequiredArgsConstructor
public class KnowledgeDocumentWrites {

    private final KnowledgeDocumentMapper documentMapper;

    /**
     * 解析结束。{@code resetDownstream} 对应强制重解析：下游合并/分割/嵌入作废。
     */
    public void markParseDone(Long documentId, String parseStatus, boolean resetDownstream) {
        var update = Wrappers.<KnowledgeDocument>lambdaUpdate()
                .set(KnowledgeDocument::getParseStatus, parseStatus)
                .eq(KnowledgeDocument::getId, documentId);
        if (resetDownstream) {
            update.set(KnowledgeDocument::getMergeStatus, "unmerged")
                    .set(KnowledgeDocument::getSplitStatus, "unsplit")
                    .set(KnowledgeDocument::getEmbedStatus, "unembedded");
        }
        documentMapper.update(null, update);
    }

    /** 合并结束：分割与嵌入作废。 */
    public void markMergeDone(Long documentId, String mergeStatus) {
        documentMapper.update(null, Wrappers.<KnowledgeDocument>lambdaUpdate()
                .set(KnowledgeDocument::getMergeStatus, mergeStatus)
                .set(KnowledgeDocument::getSplitStatus, "unsplit")
                .set(KnowledgeDocument::getEmbedStatus, "unembedded")
                .eq(KnowledgeDocument::getId, documentId));
    }

    /** 进程退出后仍停在进行中的解析：标失败，不自动重跑。 */
    public void interruptParse(Long documentId) {
        documentMapper.update(null, Wrappers.<KnowledgeDocument>lambdaUpdate()
                .set(KnowledgeDocument::getParseStatus, "failed")
                .eq(KnowledgeDocument::getId, documentId)
                .eq(KnowledgeDocument::getParseStatus, "parsing"));
    }

    /** 进程退出后仍停在进行中的合并：标失败，不自动重跑。 */
    public void interruptMerge(Long documentId) {
        documentMapper.update(null, Wrappers.<KnowledgeDocument>lambdaUpdate()
                .set(KnowledgeDocument::getMergeStatus, "failed")
                .eq(KnowledgeDocument::getId, documentId)
                .eq(KnowledgeDocument::getMergeStatus, "merging"));
    }

    /** 进程退出后仍停在进行中的分割：标失败，不自动重跑。 */
    public void interruptSplit(Long documentId) {
        documentMapper.update(null, Wrappers.<KnowledgeDocument>lambdaUpdate()
                .set(KnowledgeDocument::getSplitStatus, "failed")
                .eq(KnowledgeDocument::getId, documentId)
                .eq(KnowledgeDocument::getSplitStatus, "splitting"));
    }

    /** 进程退出后仍停在进行中的嵌入：标失败，不自动重跑。 */
    public void interruptEmbed(Long documentId) {
        documentMapper.update(null, Wrappers.<KnowledgeDocument>lambdaUpdate()
                .set(KnowledgeDocument::getEmbedStatus, "failed")
                .eq(KnowledgeDocument::getId, documentId)
                .eq(KnowledgeDocument::getEmbedStatus, "embedding"));
    }

    /** 分割结束：嵌入作废。 */
    public void markSplitDone(Long documentId, String splitStatus) {
        documentMapper.update(null, Wrappers.<KnowledgeDocument>lambdaUpdate()
                .set(KnowledgeDocument::getSplitStatus, splitStatus)
                .set(KnowledgeDocument::getEmbedStatus, "unembedded")
                .eq(KnowledgeDocument::getId, documentId));
    }
}
