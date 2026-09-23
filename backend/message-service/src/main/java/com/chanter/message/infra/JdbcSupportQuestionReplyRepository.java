package com.chanter.message.infra;

import com.chanter.message.application.SupportQuestionReplyRepository;
import com.chanter.message.domain.SupportQuestionReply;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSupportQuestionReplyRepository implements SupportQuestionReplyRepository {

    private final JdbcClient jdbcClient;
    private final com.chanter.message.lifecycle.MessageLifecycleAccess lifecycle;

    public JdbcSupportQuestionReplyRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
        this.lifecycle = new com.chanter.message.lifecycle.MessageLifecycleAccess(jdbcClient);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public SupportQuestionReply save(SupportQuestionReply reply) {
        lifecycle.lock(); lifecycle.requireAccount(reply.authorUserId()); lifecycle.requireQuestion(reply.supportQuestionId());
        jdbcClient.sql("""
                        INSERT INTO support_question_replies (
                            id,
                            support_question_id,
                            author_user_id,
                            body,
                            created_at
                        )
                        VALUES (:id, :supportQuestionId, :authorUserId, :body, :createdAt)
                        """)
                .param("id", reply.id())
                .param("supportQuestionId", reply.supportQuestionId())
                .param("authorUserId", reply.authorUserId())
                .param("body", reply.body())
                .param("createdAt", OffsetDateTime.ofInstant(reply.createdAt(), ZoneOffset.UTC))
                .update();
        return reply;
    }

    @Override
    public List<SupportQuestionReply> findBySupportQuestionId(UUID supportQuestionId) {
        return jdbcClient.sql("""
                        SELECT id, support_question_id, author_user_id, body, created_at
                        FROM support_question_replies
                        WHERE support_question_id = :supportQuestionId
                        ORDER BY created_at ASC, id ASC
                        """)
                .param("supportQuestionId", supportQuestionId)
                .query((resultSet, rowNum) -> new SupportQuestionReply(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("support_question_id", UUID.class),
                        resultSet.getObject("author_user_id", UUID.class),
                        resultSet.getString("body"),
                        resultSet.getObject("created_at", OffsetDateTime.class).toInstant()
                ))
                .list();
    }
}
