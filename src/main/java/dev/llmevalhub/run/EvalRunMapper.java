package dev.llmevalhub.run;

import java.util.List;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EvalRunMapper {
    @Insert("""
            INSERT INTO eval_runs (id, source_project, run_label, dataset_version,
                model_id, prompt_version, result_source, status, started_at, created_at)
            VALUES (#{id}, #{sourceProject}, #{runLabel}, #{datasetVersion},
                #{modelId}, #{promptVersion}, #{resultSource}, #{status}, #{startedAt}, #{createdAt})
            """)
    int insert(EvalRun run);

    @ConstructorArgs({
            @Arg(column = "id", javaType = String.class),
            @Arg(column = "source_project", javaType = String.class),
            @Arg(column = "run_label", javaType = String.class),
            @Arg(column = "dataset_version", javaType = String.class),
            @Arg(column = "model_id", javaType = String.class),
            @Arg(column = "prompt_version", javaType = String.class),
            @Arg(column = "result_source", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "started_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "created_at", javaType = java.time.LocalDateTime.class)
    })
    @Select("""
            SELECT id, source_project, run_label, dataset_version, model_id, prompt_version,
                result_source, status, started_at, created_at
            FROM eval_runs WHERE id = #{id}
            """)
    EvalRun findById(String id);

    @ConstructorArgs({
            @Arg(column = "id", javaType = String.class),
            @Arg(column = "source_project", javaType = String.class),
            @Arg(column = "run_label", javaType = String.class),
            @Arg(column = "dataset_version", javaType = String.class),
            @Arg(column = "model_id", javaType = String.class),
            @Arg(column = "prompt_version", javaType = String.class),
            @Arg(column = "result_source", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "started_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "created_at", javaType = java.time.LocalDateTime.class)
    })
    @Select("""
            <script>
            SELECT id, source_project, run_label, dataset_version, model_id, prompt_version,
                result_source, status, started_at, created_at
            FROM eval_runs
            <where>
              <if test='sourceProject != null'>source_project = #{sourceProject}</if>
              <if test='datasetVersion != null'>AND dataset_version = #{datasetVersion}</if>
              <if test='modelId != null'>AND model_id = #{modelId}</if>
            </where>
            ORDER BY created_at DESC, id DESC LIMIT #{limit}
            </script>
            """)
    List<EvalRun> findRecent(@Param("sourceProject") String sourceProject,
            @Param("datasetVersion") String datasetVersion,
            @Param("modelId") String modelId, @Param("limit") int limit);
}
