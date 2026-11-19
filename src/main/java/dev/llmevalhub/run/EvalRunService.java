package dev.llmevalhub.run;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvalRunService {
    private final EvalRunMapper mapper;
    private final Clock clock;

    public EvalRunService(EvalRunMapper mapper) {
        this.mapper = mapper;
        this.clock = Clock.systemUTC();
    }

    @Transactional
    public EvalRun create(CreateRunRequest request) {
        EvalRun run = new EvalRun(
                UUID.randomUUID().toString(), request.sourceProject(), request.runLabel(),
                request.datasetVersion(), request.modelId(), request.promptVersion(),
                request.resultSource().name(), request.status().name(),
                LocalDateTime.ofInstant(request.startedAt(), ZoneOffset.UTC),
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        if (mapper.insert(run) != 1) {
            throw new IllegalStateException("评测运行写入未影响一行");
        }
        return run;
    }

    @Transactional(readOnly = true)
    public EvalRun findById(String id) {
        return mapper.findById(id);
    }

    @Transactional(readOnly = true)
    public List<EvalRun> findRecent(String sourceProject, int limit) {
        return mapper.findRecent(sourceProject, limit);
    }
}
