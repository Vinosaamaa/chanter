package com.chanter.agent.infra;

import com.chanter.agent.AgentServiceApplication;
import com.chanter.agent.application.EmbeddingModelRouter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import java.util.List;
import java.util.UUID;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Synthetic corpus and real semantic model, executed inside the production image's memory/CPU limits. */
public final class VectorRuntimeProbe {
    private static final String[] DOCUMENTS={
        "Assignments must be uploaded to the learning portal before Friday at five in the afternoon.",
        "A student who misses an assessment because of illness should contact the instructor to arrange a replacement.",
        "Books borrowed from the university library must be returned within fourteen days.",
        "Plants use sunlight to convert carbon dioxide and water into glucose during photosynthesis.",
        "A queue preserves insertion order: the first element added is the first element removed.",
        "Spring Security applies authentication and authorization rules through a chain of servlet filters.",
        "The derivative measures the instantaneous rate of change of a function at a point.",
        "Water molecules contain two hydrogen atoms bonded to one oxygen atom.",
        "The course laboratory requires safety glasses and closed shoes during chemical experiments.",
        "Refund requests must be submitted to the finance office within thirty days of payment."
    };
    private static final String[] QUESTIONS={
        "When do I need to turn in my homework?", "What happens if I am sick on exam day?",
        "How long can I keep a library book?", "How do plants make food using light?",
        "Which item leaves a FIFO data structure first?", "How are web requests protected by Spring?",
        "What describes how quickly a function changes?", "What atoms make up a water molecule?",
        "What should I wear for chemistry experiments?", "What is the deadline to ask for my money back?"
    };
    public static void main(String[] args) throws Exception {
        var app=new SpringApplication(AgentServiceApplication.class);
        try(var context=app.run(args)) {
            var jdbc=context.getBean(JdbcClient.class);
            if(jdbc.sql("SELECT rolsuper OR rolcreatedb OR rolcreaterole FROM pg_roles WHERE rolname=current_user").query(Boolean.class).single())
                throw new AssertionError("Runtime proof must use an unprivileged schema owner");
            var search=context.getBean(JdbcVectorSearch.class);
            var client=context.getBean(EmbeddingModelRouter.class).pinned();
            if(!client.metadata().provider().equals("onnx")) throw new AssertionError("Runtime proof requires the real local semantic model");
            var vectors=new ArrayList<float[]>();
            for(String document:DOCUMENTS) vectors.add(client.embed(document));
            int correct=0,answered=0;
            boolean[] expectedAnswers=new boolean[QUESTIONS.length];
            for(int i=0;i<QUESTIONS.length;i++) {
                float[] query=client.embed(QUESTIONS[i]);int best=-1;double score=-1;
                for(int j=0;j<vectors.size();j++) { double candidate=cosine(query,vectors.get(j));if(candidate>score){score=candidate;best=j;} }
                if(best==i) correct++;
                if(score>=0.35) {
                    if(best!=i) throw new AssertionError("Wrong confident semantic source for fixture "+i);
                    expectedAnswers[i]=true;answered++;
                }
            }
            if(correct<9 || answered<8) throw new AssertionError("Semantic ranking/coverage below fixture baseline: "+correct+"/"+answered);
            float[] unrelated=client.embed("Who won the football championship in Argentina?");
            if(vectors.stream().anyMatch(vector->cosine(unrelated,vector)>=0.35)) throw new AssertionError("Unrelated question crossed the evidence threshold");
            // A dedicated empty schema is mandatory: the probe never clears an existing corpus.
            if(jdbc.sql("SELECT COUNT(*) FROM resource_chunks").query(Long.class).single()!=0) throw new AssertionError("Probe database must be empty");
            jdbc.sql("""
                INSERT INTO resource_index_lifecycle(resource_id,course_id,study_server_id,cohort_id,source_sha256,parser_version,file_name,status,generation)
                SELECT md5('resource-'||r)::uuid,md5('course-'||(r%10))::uuid,md5('server-'||(r%5))::uuid,
                    CASE WHEN r%3=0 THEN NULL ELSE md5('cohort-'||(r%3))::uuid END,repeat('a',64),'load-fixture-v1','guide.txt','PROCESSING',1
                FROM generate_series(1,1000) r
                """).update();
            jdbc.sql("""
                INSERT INTO resource_chunks(id,resource_id,course_id,chunk_index,start_offset,end_offset,content_text,content_sha256,file_name,created_at,
                    locator_kind,locator_number,locator_label,source_sha256,parser_version)
                SELECT md5(l.resource_id::text||':'||c)::uuid,l.resource_id,l.course_id,c,0,400,
                    repeat('Synthetic course material paragraph. ',12),repeat('b',64),'guide.txt',CURRENT_TIMESTAMP,
                    'PAGE',c+1,'Page '||(c+1),l.source_sha256,l.parser_version
                FROM resource_index_lifecycle l CROSS JOIN generate_series(0,99) c
                """).update();
            for(int i=0;i<vectors.size();i++) {
                jdbc.sql("""
                    INSERT INTO resource_chunk_embeddings(chunk_id,resource_id,course_id,model_id,dimensions,embedding,created_at)
                    SELECT id,resource_id,course_id,:model,384,CAST(:vector AS public.vector),CURRENT_TIMESTAMP
                    FROM resource_chunks WHERE chunk_index%10=:topic
                    """).param("model",client.modelId()).param("vector",VectorValue.encode(vectors.get(i),384)).param("topic",i).update();
            }
            jdbc.sql("UPDATE resource_index_lifecycle SET status='READY'").update();
            jdbc.sql("ANALYZE resource_chunks").update();jdbc.sql("ANALYZE resource_chunk_embeddings").update();jdbc.sql("ANALYZE resource_index_lifecycle").update();
            UUID course=jdbc.sql("SELECT md5('course-0')::uuid").query(UUID.class).single();
            var authorized=jdbc.sql("SELECT resource_id,study_server_id,cohort_id,source_sha256 FROM resource_index_lifecycle WHERE course_id=:course AND cohort_id IS NULL ORDER BY resource_id LIMIT 30")
                    .param("course",course).query((rs,row)->new JdbcVectorSearch.AuthorizedResource(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),rs.getString(4))).list();
            var allowed=authorized.stream().map(JdbcVectorSearch.AuthorizedResource::resourceId).collect(java.util.stream.Collectors.toSet());
            var inference=new ArrayList<Double>();var database=new ArrayList<Double>();var combined=new ArrayList<Double>();
            for(int i=0;i<45;i++) {
                long start=System.nanoTime();float[] query=client.embed(QUESTIONS[i%QUESTIONS.length]);long embedded=System.nanoTime();
                var results=search.nearest(client.metadata(),course,authorized,query,5,0.35);long completed=System.nanoTime();
                if(results.isEmpty()==expectedAnswers[i%QUESTIONS.length] || results.size()>5 || results.stream().anyMatch(row->!allowed.contains(row.resourceId()) || !course.equals(row.courseId())))
                    throw new AssertionError("Scoped datastore retrieval violated its result contract");
                if(i>=5){inference.add((embedded-start)/1e6);database.add((completed-embedded)/1e6);combined.add((completed-start)/1e6);}
            }
            String plan=search.explain(client.metadata(),course,authorized,client.embed(QUESTIONS[0]));
            Files.writeString(Path.of("/tmp/vector-query-plan.json"),plan);
            if(!plan.contains("Index") || !plan.contains("Limit")) throw new AssertionError("Actual scoped query has no indexed bounded plan");
            long invalid=jdbc.sql("SELECT COUNT(*) FROM pg_index i JOIN pg_class c ON c.oid=i.indexrelid JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname=current_schema() AND NOT i.indisvalid")
                    .query(Long.class).single();
            if(invalid!=0) throw new AssertionError("Invalid vector database index");
            System.out.printf(java.util.Locale.ROOT,"VECTOR_PROOF corpus=100000 resources=1000 courses=10 authorized=%d top1=%d/10 answered=%d/10 inference_p50_ms=%.1f inference_p95_ms=%.1f database_p50_ms=%.1f database_p95_ms=%.1f combined_p95_ms=%.1f cgroup_peak_bytes=%s%n",
                    authorized.size(),correct,answered,percentile(inference,0.5),percentile(inference,0.95),percentile(database,0.5),percentile(database,0.95),percentile(combined,0.95),Files.readString(Path.of("/sys/fs/cgroup/memory.peak")).trim());
            if(percentile(combined,0.95)>2000) throw new AssertionError("Scoped answer retrieval exceeds two-second p95 budget");
        }
    }
    private static double cosine(float[] left,float[] right){double score=0;for(int i=0;i<left.length;i++)score+=left[i]*right[i];return score;}
    private static double percentile(List<Double> values,double fraction){var sorted=values.stream().sorted().toList();return sorted.get((int)Math.ceil(sorted.size()*fraction)-1);}
}
