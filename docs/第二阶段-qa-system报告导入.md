# 第二阶段：导入 qa-system 评分报告

更新日期：2026-11-19。

本文记录第二阶段完成时的范围；后续同口径比较、复核和 Vue 页面见[第三阶段说明](第三阶段-版本对比与前端展示.md)。

## 本阶段范围

在第一阶段的运行元数据表之上，手动上传 `qa-system` 已生成的 `.report.json`，保存可查询的汇总指标、逐例检查值和来源信息。同一来源项目中按**报告原始字节的 SHA-256** 去重。导入事务写入运行、报告来源和所有指标；失败则回滚。没有调用模型或原项目评测脚本，也没有修改 `D:\qa-system` 或 `D:\Agent`。

原始文件依据为 `D:\qa-system\eval\baselines\2026-11-17-gpt-6-luna.report.json`、`D:\qa-system\eval\run_eval.py`、`D:\qa-system\eval\metrics.py` 和 `D:\qa-system\eval\README.md`。本仓库的 `src/test/resources/qa-system/2026-11-17-gpt-6-luna.report.json` 是该五题报告的逐字节测试副本；两个文件 SHA-256 均为 `719b3cb80e95df911af250a3aa97841a75821371fdc187351b8504182461874a`。原项目文档将这份基线记录为真实模型运行，四道可回答题和一道拒答题；评测只覆盖其固定小样本与指定环境，不能外推生产质量。

## 文件与数据流

| 文件 | 职责 |
| --- | --- |
| `pom.xml` | 加入 Spring JDBC，配合现有 MyBatis；两种数据访问都使用同一个 Spring 数据源和事务 |
| `src/main/resources/application.yml` | 上传文件及请求上限均为 2 MB |
| `src/main/resources/db/migration/V2__create_qa_report_results.sql` | 在 `eval_runs` 增加来源报告哈希与唯一约束，建立来源、语料哈希、运行指标、逐例结果及逐例指标表 |
| `src/main/java/dev/llmevalhub/importer/QaReportParser.java` | 校验 JSON 结构、样本分母、案例 ID、可回答题/拒答题指标形状及调用总数，并映射指标口径 |
| `src/main/java/dev/llmevalhub/importer/QaReportImportService.java` | 计算原文件哈希、处理重复及声明冲突，在事务中导入 |
| `src/main/java/dev/llmevalhub/importer/QaReportRepository.java` | 用 Spring JDBC 写入和读取报告来源、汇总指标与逐例指标 |
| `src/main/java/dev/llmevalhub/importer/QaReportImportController.java` | 上传和结果查询 HTTP 接口 |
| `src/test/java/dev/llmevalhub/importer/` | 真实报告解析、格式错误、重复导入及冲突声明的单元测试 |

导入产生一个 `sourceProject=qa-system`、`status=COMPLETED` 的运行。`datasetVersion` 为 `sha256:<报告中的 dataset_sha256>`；`startedAt` 暂用报告的 `run.created_at_utc`，它是**评分报告生成时间**，并非原模型请求开始时间。`modelId` 和 `resultSource` 来自上传者的明确声明，不从 `run.label` 猜测。其他第一阶段手工创建的运行没有报告结果，查询其 `/results` 返回 404。

报告原始字节哈希存在 `eval_runs.source_report_sha256`，与 `source_project` 组成唯一键。`qa_report_imports` 保存报告文件名、案例和预测 SHA-256、预测文件名、报告时间、Python 版本、`k` 和三个样本数；有 `run.corpus_sha256` 时存到 `qa_report_corpus_hashes`。预测路径只保留文件名，不保存原机器绝对路径。`eval_run_metrics` 保存指标值、指标定义版本、聚合方式、样本范围、分母和单位；`eval_case_results` 保存案例 ID、类别、题型、耗时和模型调用次数；`eval_case_metrics` 保存逐题布尔或数值检查值。所有表以运行 ID 关联，不把缺失值填成零。

## 指标映射与分母

所有导入指标使用定义版本 `qa-system.eval.metrics.v1`，键以 `qa.` 开头，避免与未来求职助手的同名检查混淆。定义依据为 `D:\qa-system\eval\metrics.py` 与 `D:\qa-system\eval\run_eval.py`。

| 范围 | 汇总指标 | 分母或聚合 |
| --- | --- | --- |
| 可回答题 `ANSWERABLE` | `qa.hit_at_k`、`qa.mrr_at_k`、`qa.citation_precision`、`qa.answer_contains_accuracy` | `answerable_count`；报告中的均值 |
| 拒答题 `REFUSAL` | `qa.refusal_accuracy` | `refusal_count`；报告中的均值 |
| 全部题 `ALL` | `qa.latency_p50_ms`、`qa.latency_p95_ms` | `sample_count`；P50/P95，单位毫秒 |
| 全部题 `ALL` | `qa.model_calls_total`、`qa.model_calls_mean` | `sample_count`；总和/每题均值 |

逐题保留 `qa.hit_at_k`、`qa.reciprocal_rank`、`qa.citation_precision`、`qa.answer_contains_all` 或 `qa.refusal_correct`，并记录题型。报告只有评分后的指标，`answer_contains` 是字符串检查，不能称为语义正确率。解析器校验指标类型、范围、样本数、案例 ID 唯一、可回答/拒答划分和 `model_calls_total` 与逐题之和；其余汇总分数沿用来源报告，**不重跑评分脚本**。`k` 随来源记录保存；后续比较还应核对 `k`、案例集哈希和定义版本。

## 本地使用

先按[第一阶段说明](第一阶段-服务与评测运行.md)配置 JDK 17、MySQL 8、`DB_URL`、`DB_USER`、`DB_PASSWORD`，运行 `mvn -B verify` 和 `java -jar target\llm-eval-hub-0.1.0-SNAPSHOT.jar`。应用在本机 `127.0.0.1:8080` 启动后，在 PowerShell 中上传已有报告：

```powershell
curl.exe -i -F 'report=@D:\qa-system\eval\baselines\2026-11-17-gpt-6-luna.report.json;type=application/json' -F 'resultSource=LIVE_MODEL' -F 'modelId=gpt-6-luna' http://127.0.0.1:8080/api/imports/qa-system
```

`report` 必填，限 2 MiB；`resultSource` 必须显式填 `LIVE_MODEL` 或 `OFFLINE_FIXTURE`，不能填 `HUMAN_REVIEW`；`modelId` 可省略，省略时保持 `null`。首次成功返回 `201 Created`、`Location: /api/runs/{id}/results` 和 `{"runId":"…","reportSha256":"…","imported":true}`。同一报告字节及相同来源声明再次上传返回 `200 OK`，相同 `runId`、`imported:false`；不同的模型或结果来源声明返回 409，避免悄悄改写来源。格式不合法返回 400。`GET /api/runs/{id}/results` 返回 `run`、`provenance`、`metrics`、`cases`，未知或没有导入结果的运行返回 404。

导入接口依赖上传者对 `resultSource`、`modelId` 的声明；仅凭报告本身无法核实真实模型名称或证明发生过真实调用。报告内 `dataset_sha256`、`predictions_sha256` 和可选语料哈希按报告原值存储，只检查格式；本阶段没有取得配对文件并重新计算这些哈希。报告文件自身的 SHA-256 则由本服务对收到的原始字节重新计算。同一 JSON 内容若字节不同会被视为另一份报告，这是当前去重口径。

## 已有数据与后续空缺

五题报告可直接提供运行标签、报告时间、案例/预测哈希、`k`、样本数、逐题评分、逐题耗时、模型调用计数和九项汇总值；该文件没有 `corpus_sha256`。配对预测文件 `D:\qa-system\eval\baselines\2026-11-17-gpt-6-luna.predictions.jsonl` 另有回答、召回顺序、引用、向量后端与模型字段，本阶段**没有导入**。报告本身没有原始回答、每次调用 token、价格、费用、稳定提示词版本或人工复核记录。`model_calls` 是原项目包装器计数，不能代替 token 或供应商计费次数。来源及两个项目指标差异见[初始工程调研](初始工程与现有评测调研.md)。AI 求职助手仍只读调研，尚未接入。

第三阶段才会做同案例集版本对比、失败分类、人工复核和 Vue 页面；第四阶段处理真实用量与费用；第五阶段考虑两个原项目自动上报；第六阶段处理隔离集成测试、Compose、CI 和演示流程。本阶段不声称已有这些功能。

## 验证记录与限制

2026-11-19 在 Windows 11、Oracle JDK 17.0.12、Maven 3.9.16 上执行 `mvn -B '-Dmaven.compiler.showDeprecation=true' verify`，构建成功，9 项测试通过（第一阶段 4 项、第二阶段 5 项）。第二阶段测试包含真实五题报告解析、重复案例 ID 与分母错误拒绝、同文件重复导入只写一次及不同模型声明冲突。自动化测试中导入服务的仓库是模拟对象，尚无自动化 MySQL 集成测试。

使用一次性 `mysql:8.4` 容器和本机服务实际验证：空库启动时 Flyway 应用 V1、V2；上传上述真实报告后，`GET /api/runs/{id}/results` 返回 `LIVE_MODEL`、显式提供的 `gpt-6-luna`、5 个案例与 9 个汇总指标；重复上传返回 200 且同一运行 ID，改变 `modelId` 返回 409，无效 JSON 返回 400。隔离库查询得到 1 条运行、1 条报告来源、9 条汇总指标、5 条案例、17 条逐例指标，Flyway 版本为 2。此容器仅供本轮验证，不是用户的长期数据库；未执行原项目脚本或模型调用。尚未测并发导入、重启恢复或完整前后端流程。
