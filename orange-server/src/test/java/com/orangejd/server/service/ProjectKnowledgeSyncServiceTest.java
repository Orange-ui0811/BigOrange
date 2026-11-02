package com.orangejd.server.service;

import com.orangejd.pojo.entity.BeginnerProject;
import com.orangejd.pojo.entity.ProjectKnowledgeIndexStatus;
import com.orangejd.pojo.vo.KnowledgeDocumentVO;
import com.orangejd.server.mapper.BeginnerProjectMapper;
import com.orangejd.server.mapper.CareerProjectMapper;
import com.orangejd.server.mapper.ProjectKnowledgeIndexStatusMapper;
import com.orangejd.server.service.impl.ProjectKnowledgeDocumentWriter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectKnowledgeSyncServiceTest {
    @Test
    void publishedBeginnerProjectBecomesFullDocumentInUnifiedKnowledgeBase() {
        BeginnerProjectMapper beginner = mock(BeginnerProjectMapper.class);
        CareerProjectMapper career = mock(CareerProjectMapper.class);
        ProjectKnowledgeIndexStatusMapper statuses = mock(ProjectKnowledgeIndexStatusMapper.class);
        ProjectKnowledgeDocumentWriter documents = mock(ProjectKnowledgeDocumentWriter.class);
        BeginnerProject project = beginnerProject("PUBLISHED");
        when(beginner.selectById(7L)).thenReturn(project);
        KnowledgeDocumentVO stored = new KnowledgeDocumentVO();
        stored.setId(19L);
        stored.setChunkCount(4);
        when(documents.upsert(eq("BEGINNER_PROJECT"), eq("beginner:7"),
                eq("owner/repo"), anyString())).thenReturn(stored);
        ProjectKnowledgeSyncService service = new ProjectKnowledgeSyncService(beginner, career, statuses, documents);

        service.syncBeginner(7L);

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(documents).upsert(eq("BEGINNER_PROJECT"), eq("beginner:7"),
                eq("owner/repo"), content.capture());
        assertTrue(content.getValue().contains("## 可改造方向"));
        assertTrue(content.getValue().contains("## README 摘要"));
        verify(statuses).markDone(eq("BEGINNER_PROJECT"), eq(7L), eq(19L), anyString(), eq(4));
    }

    @Test
    void unpublishedProjectIsRemovedFromDocumentAndIndexStatus() {
        BeginnerProjectMapper beginner = mock(BeginnerProjectMapper.class);
        CareerProjectMapper career = mock(CareerProjectMapper.class);
        ProjectKnowledgeIndexStatusMapper statuses = mock(ProjectKnowledgeIndexStatusMapper.class);
        ProjectKnowledgeDocumentWriter documents = mock(ProjectKnowledgeDocumentWriter.class);
        when(beginner.selectById(7L)).thenReturn(beginnerProject("HIDDEN"));
        ProjectKnowledgeIndexStatus status = new ProjectKnowledgeIndexStatus();
        status.setSourceRef("beginner:7");
        when(statuses.selectBySourceRef("beginner:7")).thenReturn(status);
        ProjectKnowledgeSyncService service = new ProjectKnowledgeSyncService(beginner, career, statuses, documents);

        service.syncBeginner(7L);

        verify(documents).delete("beginner:7");
        verify(statuses).markRemoved("BEGINNER_PROJECT", 7L);
        verify(documents, never()).upsert(anyString(), anyString(), anyString(), anyString());
    }

    private BeginnerProject beginnerProject(String status) {
        BeginnerProject value = new BeginnerProject();
        value.setId(7L);
        value.setFullName("owner/repo");
        value.setHtmlUrl("https://github.com/owner/repo");
        value.setDescriptionZh("一个可运行的智能体项目");
        value.setWhatIsIt("用于搭建智能体应用");
        value.setBeginnerReason("文档完整且可快速启动");
        value.setModificationIdeas("增加知识库和可视化面板");
        value.setMainRisks("需要外部模型 Key");
        value.setFinalRecommendation("适合作为入门作品");
        value.setReadmeExcerpt("npm install\nnpm run dev");
        value.setStatus(status);
        value.setContentHash("source-hash");
        return value;
    }
}
