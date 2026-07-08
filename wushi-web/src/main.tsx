import React, { useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  Activity,
  AlertTriangle,
  BarChart3,
  BookOpenCheck,
  Brain,
  CalendarClock,
  ChevronRight,
  CircleDot,
  GitBranch,
  LineChart,
  ListChecks,
  RefreshCw,
  ShieldAlert,
  SlidersHorizontal,
  Sparkles,
  Swords,
  Target,
  type LucideIcon,
  Wrench
} from "lucide-react";
import "./styles.css";

type ApiResponse<T> = { success?: boolean; code?: string; message?: string; data: T };
type Query = {
  tradeDate?: string;
  asOfDate?: string;
  judgementMode?: string;
  ruleVersion?: string;
  [key: string]: string | undefined;
};

type EvidenceItem = {
  evidenceCode?: string;
  evidenceType?: string;
  factorCode?: string;
  title?: string;
  evidenceTitle?: string;
  description?: string;
  evidenceDesc?: string;
  score?: number | string;
  weight?: number | string;
  sourceTable?: string;
  sourceKey?: string;
  validationStatus?: string;
};

type NextWatchItem = {
  watchId?: string;
  judgementId?: string;
  tradeDate?: string;
  watchDate?: string;
  engineType?: string;
  targetType?: string;
  targetCode?: string;
  targetName?: string;
  title?: string;
  conditionExpression?: string;
  expectedSignal?: string;
  riskSignal?: string;
  priority?: number;
  ruleVersion?: string;
  validationStatus?: string;
};

type JudgmentMeta = {
  judgementId?: string;
  tradeDate?: string;
  asOfDate?: string;
  judgementMode?: string;
  engineType?: string;
  targetType?: string;
  targetCode?: string;
  targetName?: string;
  confidence?: number | string;
  ruleVersion?: string;
  dataQualityLevel?: string;
};

type JudgmentBlock = {
  meta?: JudgmentMeta;
  conclusion?: string;
  detail?: Record<string, unknown>;
  evidenceList?: EvidenceItem[];
  conflictList?: EvidenceItem[];
  warningList?: EvidenceItem[];
  nextWatchList?: NextWatchItem[];
  dataQualityIssues?: Record<string, unknown>[];
};

type ChainNode = {
  nodeCode?: string;
  nodeName?: string;
  targetCode?: string;
  targetName?: string;
  conclusion?: string;
  confidence?: number | string;
  ruleVersion?: string;
  evidenceCount?: number;
  conflictCount?: number;
  warningCount?: number;
  nextWatchCount?: number;
};

type InferenceContext = {
  query?: Query;
  chain?: Record<string, string | undefined>;
  chainNodes?: ChainNode[];
  cycleCard?: JudgmentBlock;
  mainlineCards?: JudgmentBlock[];
  leaderCards?: JudgmentBlock[];
  divergenceCard?: JudgmentBlock;
  riskCard?: JudgmentBlock;
  topMainline?: Record<string, unknown>;
  topLeader?: Record<string, unknown>;
  nextWatchList?: NextWatchItem[];
  dataQualityIssues?: Record<string, unknown>[];
};

type GrowthData = {
  factorResults?: Record<string, unknown>[];
  combinationResults?: Record<string, unknown>[];
  growthLogs?: string[];
};

type RuleCandidate = {
  candidateId?: string;
  baseRuleVersion?: string;
  targetRuleVersion?: string;
  engineType?: string;
  status?: string;
  statDate?: string;
  factorChangeCount?: number;
  sampleCount?: number;
  totalAbsDelta?: number | string;
  reasonSummary?: string;
  riskSummary?: string;
  generatedBy?: string;
  approvedBy?: string;
  approvalComment?: string;
  approvedAt?: string;
  effectiveAt?: string;
  factorChanges?: Record<string, unknown>[];
};

type HistoryReplayDay = {
  tradeDate?: string;
  asOfDate?: string;
  batchId?: string;
  status?: string;
  affectedRows?: number;
  errorMessage?: string;
  stepResults?: Record<string, unknown>[];
};

type HistoryReplayResult = {
  batchId?: string;
  startDate?: string;
  endDate?: string;
  ruleVersion?: string;
  judgementMode?: string;
  totalDays?: number;
  successDays?: number;
  failedDays?: number;
  affectedRows?: number;
  days?: HistoryReplayDay[];
};

type PageKey = "overview" | "cycle" | "mainline" | "leader" | "pattern" | "risk" | "watch" | "review" | "growth";
type PageDef = {
  key: PageKey;
  label: string;
  icon: LucideIcon;
};
type InferenceApi = {
  data: InferenceContext | null;
  loading: boolean;
  error: string;
  reload: () => void;
};
type GrowthApi = {
  data: GrowthData | null;
  loading: boolean;
  error: string;
  reload: () => void;
};
type RuleCandidateApi = {
  data: RuleCandidate[] | null;
  loading: boolean;
  error: string;
  reload: () => void;
};

const API_BASE = import.meta.env.VITE_API_BASE ?? "";

const pages: PageDef[] = [
  { key: "overview", label: "总览沙盘", icon: BarChart3 },
  { key: "cycle", label: "周期驾驶舱", icon: Activity },
  { key: "mainline", label: "主线推演", icon: GitBranch },
  { key: "leader", label: "龙头竞争", icon: Swords },
  { key: "pattern", label: "分歧一致", icon: LineChart },
  { key: "risk", label: "风险雷达", icon: ShieldAlert },
  { key: "watch", label: "明日验证", icon: ListChecks },
  { key: "review", label: "复盘修正", icon: Wrench },
  { key: "growth", label: "系统成长", icon: Brain }
];

function App() {
  const [page, setPage] = useState<PageKey>("overview");
  const [tradeDate, setTradeDate] = useState("");
  const [asOfDate, setAsOfDate] = useState("");
  const [ruleVersion, setRuleVersion] = useState("v0.1.0");
  const [judgementMode, setJudgementMode] = useState("REALTIME");
  const query = useMemo<Query>(() => ({
    tradeDate,
    asOfDate,
    ruleVersion,
    judgementMode
  }), [tradeDate, asOfDate, ruleVersion, judgementMode]);
  const ruleCandidateQuery = useMemo<Query>(() => ({
    statDate: asOfDate || tradeDate,
    ruleVersion
  }), [asOfDate, tradeDate, ruleVersion]);
  const contextApi = useApi<InferenceContext>("/api/market/inference-context", query);
  const growthApi = useApi<GrowthData>("/api/system/growth", query, page === "growth");
  const ruleCandidateApi = useApi<RuleCandidate[]>("/api/system/rule-evolution/candidates", ruleCandidateQuery, page === "growth");

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">悟</div>
          <div>
            <strong>悟势</strong>
            <span>市场推演沙盘</span>
          </div>
        </div>
        <nav>
          {pages.map((item) => {
            const Icon = item.icon;
            return (
              <button key={item.key} className={page === item.key ? "active" : ""} onClick={() => setPage(item.key)} title={item.label}>
                <Icon size={18} />
                <span>{item.label}</span>
              </button>
            );
          })}
        </nav>
      </aside>
      <main>
        <header className="topbar">
          <div className="page-title">
            <h1>{pages.find((item) => item.key === page)?.label}</h1>
            <p>用周期判断时，用主线判断机，用龙头判断势，用分歧一致判断点，用风险模型判断退。</p>
          </div>
          <div className="filters">
            <label>
              <span>交易日</span>
              <input type="date" value={tradeDate} onChange={(event) => setTradeDate(event.target.value)} />
            </label>
            <label>
              <span>观察日</span>
              <input type="date" value={asOfDate} onChange={(event) => setAsOfDate(event.target.value)} />
            </label>
            <label>
              <span>模式</span>
              <select value={judgementMode} onChange={(event) => setJudgementMode(event.target.value)}>
                <option value="REALTIME">盘后/实时</option>
                <option value="RETROSPECTIVE">历史回放</option>
              </select>
            </label>
            <label>
              <span>规则</span>
              <input value={ruleVersion} onChange={(event) => setRuleVersion(event.target.value)} aria-label="rule version" />
            </label>
            <button className="icon-button" onClick={contextApi.reload} title="刷新">
              <RefreshCw size={17} />
            </button>
          </div>
        </header>

        <RuntimeStatus contextApi={contextApi} />

        {contextApi.loading || contextApi.error || !contextApi.data ? (
          <State loading={contextApi.loading} error={contextApi.error} reload={contextApi.reload} />
        ) : (
          <PageContent page={page} context={contextApi.data} query={query} growthApi={growthApi} ruleCandidateApi={ruleCandidateApi} />
        )}
      </main>
    </div>
  );
}

function RuntimeStatus({ contextApi }: { contextApi: InferenceApi }) {
  const context = contextApi.data;
  return (
    <section className="status-strip">
      <StatusPill icon={CalendarClock} label="回放能力" value={context?.query?.tradeDate ? String(context.query.tradeDate) : "默认最近交易日"} />
      <StatusPill icon={SlidersHorizontal} label="规则版本" value={context?.query?.ruleVersion ?? "v0.1.0"} />
      <StatusPill icon={BookOpenCheck} label="证据" value={String(totalCount(context?.chainNodes, "evidenceCount"))} />
      <StatusPill icon={AlertTriangle} label="冲突/预警" value={String(totalCount(context?.chainNodes, "conflictCount") + totalCount(context?.chainNodes, "warningCount"))} />
      <StatusPill icon={ListChecks} label="待验证" value={String(context?.nextWatchList?.length ?? 0)} />
    </section>
  );
}

function PageContent({
  page,
  context,
  query,
  growthApi,
  ruleCandidateApi
}: {
  page: PageKey;
  context: InferenceContext;
  query: Query;
  growthApi: GrowthApi;
  ruleCandidateApi: RuleCandidateApi;
}) {
  if (page === "overview") return <OverviewPage context={context} />;
  if (page === "cycle") return <CyclePage context={context} />;
  if (page === "mainline") return <MainlinePage context={context} />;
  if (page === "leader") return <LeaderPage context={context} />;
  if (page === "pattern") return <PatternPage context={context} />;
  if (page === "risk") return <RiskPage context={context} />;
  if (page === "watch") return <WatchPage context={context} />;
  if (page === "review") return <ReviewPage context={context} query={query} />;
  return <GrowthPage growthApi={growthApi} ruleCandidateApi={ruleCandidateApi} context={context} query={query} />;
}

function OverviewPage({ context }: { context: InferenceContext }) {
  return (
    <div className="page-stack">
      <Hero context={context} />
      <ChainMap nodes={context.chainNodes ?? []} />
      <section className="layout-2">
        <JudgmentPanel title="周期卡" block={context.cycleCard} accent="blue" />
        <JudgmentPanel title="风险卡" block={context.riskCard} accent="red" />
      </section>
      <section className="layout-2">
        <CandidateBoard title="主线候选" blocks={context.mainlineCards ?? []} kind="mainline" />
        <CandidateBoard title="龙头候选" blocks={context.leaderCards ?? []} kind="leader" />
      </section>
      <section className="layout-2">
        <JudgmentPanel title="分歧一致" block={context.divergenceCard} accent="amber" />
        <WatchPanel items={context.nextWatchList ?? []} compact />
      </section>
    </div>
  );
}

function CyclePage({ context }: { context: InferenceContext }) {
  const detail = objectOf(context.cycleCard?.detail);
  return (
    <div className="page-stack">
      <SectionIntro icon={Activity} title="周期驾驶舱" desc="先判市场处于什么时，再决定允许什么模式、禁止什么动作。" />
      <section className="layout-3">
        <MetricCard label="大周期" value={display(detail.marketCycleStage)} sub={display(detail.stageReason)} />
        <MetricCard label="情绪阶段" value={display(detail.emotionCycleStage)} sub={display(detail.transitionSignal)} />
        <MetricCard label="策略边界" value={display(detail.strategyBoundary)} sub={display(detail.allowedMode)} />
      </section>
      <section className="layout-2">
        <JudgmentPanel title="周期判断证据" block={context.cycleCard} accent="blue" />
        <FactorTable title="周期因子" factors={arrayOf(detail.factorResults)} />
      </section>
    </div>
  );
}

function MainlinePage({ context }: { context: InferenceContext }) {
  return (
    <div className="page-stack">
      <SectionIntro icon={GitBranch} title="主线推演" desc="主线不是涨得多，而是资金、情绪、题材持续形成合力。" />
      <CandidateBoard title="主线候选排序" blocks={context.mainlineCards ?? []} kind="mainline" expanded />
      <section className="layout-2">
        {(context.mainlineCards ?? []).slice(0, 2).map((block, index) => (
          <LifecyclePanel key={index} block={block} index={index + 1} />
        ))}
      </section>
    </div>
  );
}

function LeaderPage({ context }: { context: InferenceContext }) {
  return (
    <div className="page-stack">
      <SectionIntro icon={Swords} title="龙头竞争" desc="龙头不是预测出来的，是在主线和周期边界里竞争出来的。" />
      <CandidateBoard title="龙头候选池" blocks={context.leaderCards ?? []} kind="leader" expanded />
      <section className="layout-2">
        {(context.leaderCards ?? []).slice(0, 2).map((block, index) => (
          <JudgmentPanel key={index} title={`竞争详情 ${index + 1}`} block={block} accent={index === 0 ? "green" : "gray"} />
        ))}
      </section>
    </div>
  );
}

function PatternPage({ context }: { context: InferenceContext }) {
  const detail = objectOf(context.divergenceCard?.detail);
  return (
    <div className="page-stack">
      <SectionIntro icon={LineChart} title="分歧一致" desc="只识别主线确认后的健康分歧，不把退潮分歧误当机会。" />
      <section className="layout-4">
        <MetricCard label="当前状态" value={display(detail.state)} sub={display(detail.confirmationSignal)} />
        <MetricCard label="分歧分" value={display(detail.divergenceScore)} sub="换手与资金分歧" />
        <MetricCard label="一致分" value={display(detail.consensusScore)} sub="回封与承接质量" />
        <MetricCard label="失败信号" value={display(detail.failureSignal)} sub={display(detail.patternRisk)} tone="red" />
      </section>
      <section className="layout-2">
        <JudgmentPanel title="分歧一致判断" block={context.divergenceCard} accent="amber" />
        <FactorTable title="模式因子" factors={arrayOf(detail.factorResults)} />
      </section>
    </div>
  );
}

function RiskPage({ context }: { context: InferenceContext }) {
  const detail = objectOf(context.riskCard?.detail);
  return (
    <div className="page-stack">
      <SectionIntro icon={ShieldAlert} title="风险雷达" desc="风险不是最后一步，而是贯穿周期、主线、龙头和分歧一致全过程。" />
      <section className="layout-4">
        <MetricCard label="风险等级" value={display(detail.riskLevel)} sub={display(detail.riskType)} tone="red" />
        <MetricCard label="风险分" value={display(detail.riskScore)} sub="综合链路风险" tone="red" />
        <MetricCard label="高位反馈" value={display(detail.highPositionFeedbackScore)} sub="核心票负反馈" />
        <MetricCard label="降险信号" value={display(detail.reduceRiskSignal)} sub="等待风险兑现或收敛" />
      </section>
      <JudgmentPanel title="风险证据链" block={context.riskCard} accent="red" />
    </div>
  );
}

function WatchPage({ context }: { context: InferenceContext }) {
  return (
    <div className="page-stack">
      <SectionIntro icon={ListChecks} title="明日验证" desc="今天的判断必须留下明天能验证的条件，系统随后用市场反馈奖惩证据。" />
      <WatchPanel items={context.nextWatchList ?? []} />
      <section className="layout-2">
        <EvidenceColumn title="全部冲突证据" items={collectEvidence(context, "conflictList")} tone="amber" />
        <EvidenceColumn title="全部预警证据" items={collectEvidence(context, "warningList")} tone="red" />
      </section>
    </div>
  );
}

function ReviewPage({ context, query }: { context: InferenceContext; query: Query }) {
  const [selectedEvidence, setSelectedEvidence] = useState<EvidenceItem | null>(null);
  return (
    <div className="page-stack">
      <SectionIntro icon={Wrench} title="复盘修正" desc="人工修正不是备注，而是给系统喂训练样本，修正后的经验会进入权重建议。" />
      <section className="layout-2">
        <CorrectionForm context={context} query={query} />
        <EvidenceLabelForm evidence={selectedEvidence} context={context} query={query} />
      </section>
      <EvidencePicker context={context} onSelect={setSelectedEvidence} selected={selectedEvidence} />
    </div>
  );
}

function GrowthPage({
  growthApi,
  ruleCandidateApi,
  context,
  query
}: {
  growthApi: GrowthApi;
  ruleCandidateApi: RuleCandidateApi;
  context: InferenceContext;
  query: Query;
}) {
  if (growthApi.loading || growthApi.error || !growthApi.data) {
    return <State loading={growthApi.loading} error={growthApi.error} reload={growthApi.reload} />;
  }
  const data = growthApi.data;
  return (
    <div className="page-stack">
      <SectionIntro icon={Brain} title="系统成长" desc="验证、奖惩、人工修正都会沉淀成因子经验，下一版规则从这里长出来。" />
      <section className="layout-3">
        <MetricCard label="当日验证点" value={String(context.nextWatchList?.length ?? 0)} sub="待市场反馈" />
        <MetricCard label="因子样本" value={String(data.factorResults?.length ?? 0)} sub="证据与引擎经验" />
        <MetricCard label="成长日志" value={String(data.growthLogs?.length ?? 0)} sub="系统学习记录" />
      </section>
      <section className="layout-2">
        <DataPanel title="因子奖惩" rows={data.factorResults ?? []} columns={["factorCode", "engineType", "sampleCount", "hitRate", "avgContributionScore", "suggestedAction"]} />
        <DataPanel title="组合表现" rows={data.combinationResults ?? []} columns={["combinationCode", "sampleCount", "hitRate", "avgForwardReturn", "avgDrawdown", "suggestedAction"]} />
      </section>
      <HistoryReplayPanel query={query} />
      <RuleEvolutionPanel query={query} candidateApi={ruleCandidateApi} />
      <section className="panel">
        <PanelTitle icon={Sparkles} title="成长日志" />
        <div className="log-list">
          {(data.growthLogs ?? []).length ? data.growthLogs!.map((item, index) => <p key={index}>{item}</p>) : <Empty />}
        </div>
      </section>
    </div>
  );
}

function HistoryReplayPanel({ query }: { query: Query }) {
  const [startDate, setStartDate] = useState(query.tradeDate || "");
  const [endDate, setEndDate] = useState(query.asOfDate || query.tradeDate || "");
  const [continueOnError, setContinueOnError] = useState(true);
  const [maxDays, setMaxDays] = useState(366);
  const [status, setStatus] = useState("");
  const [result, setResult] = useState<HistoryReplayResult | null>(null);

  async function runReplay() {
    setStatus("历史回放运行中");
    try {
      const response = await postJson("/api/batch/history-replay", {
        startDate,
        endDate,
        ruleVersion: query.ruleVersion,
        judgementMode: "RETROSPECTIVE",
        continueOnError,
        maxDays
      });
      const data = (response as ApiResponse<HistoryReplayResult>).data;
      setResult(data);
      setStatus(`已完成 ${display(data.successDays)} / ${display(data.totalDays)} 天`);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : String(error));
    }
  }

  const dayRows = result?.days ?? [];
  return (
    <section className="panel history-replay">
      <PanelTitle icon={CalendarClock} title="历史批量回放" right={result?.batchId ?? "未运行"} />
      <div className="history-replay-toolbar">
        <label>
          <span>开始日期</span>
          <input type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} />
        </label>
        <label>
          <span>结束日期</span>
          <input type="date" value={endDate} onChange={(event) => setEndDate(event.target.value)} />
        </label>
        <label>
          <span>最大天数</span>
          <input type="number" min={1} value={maxDays} onChange={(event) => setMaxDays(Number(event.target.value || 1))} />
        </label>
        <label className="checkbox-label">
          <input type="checkbox" checked={continueOnError} onChange={(event) => setContinueOnError(event.target.checked)} />
          <span>失败后继续</span>
        </label>
        <button className="primary" onClick={runReplay} disabled={!startDate || !endDate}>运行完整链路</button>
        <p className="form-status">{status}</p>
      </div>
      {result && (
        <>
          <div className="rule-metrics">
            <MetricCard label="回放天数" value={display(result.totalDays)} sub={`${display(result.startDate)} 至 ${display(result.endDate)}`} />
            <MetricCard label="成功天数" value={display(result.successDays)} sub="完整链路成功" />
            <MetricCard label="失败天数" value={display(result.failedDays)} sub="数据缺失或任务异常" tone="red" />
            <MetricCard label="沉淀行数" value={display(result.affectedRows)} sub="证据、验证点和经验样本" />
          </div>
          <DataPanel title="每日回放结果" rows={dayRows as unknown as Record<string, unknown>[]} columns={["tradeDate", "status", "batchId", "affectedRows", "errorMessage"]} />
        </>
      )}
    </section>
  );
}

function RuleEvolutionPanel({ query, candidateApi }: { query: Query; candidateApi: RuleCandidateApi }) {
  const [operator, setOperator] = useState("manual");
  const [status, setStatus] = useState("");
  const [approvalComment, setApprovalComment] = useState("");
  const [minSampleCount, setMinSampleCount] = useState(3);
  const [maxDelta, setMaxDelta] = useState(0.15);
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [selected, setSelected] = useState<RuleCandidate | null>(null);
  const candidates = candidateApi.data ?? [];
  const filteredCandidates = statusFilter === "ALL" ? candidates : candidates.filter((candidate) => candidate.status === statusFilter);
  const statusCounts = countByStatus(candidates);

  useEffect(() => {
    if (!candidates.length) {
      setSelected(null);
      return;
    }
    setSelected((current) => candidates.find((candidate) => candidate.candidateId === current?.candidateId) ?? candidates[0]);
  }, [candidateApi.data]);

  async function generate() {
    setStatus("生成中");
    try {
      await postJson("/api/system/rule-evolution/candidates/generate", {
        statDate: query.asOfDate || query.tradeDate,
        baseRuleVersion: query.ruleVersion,
        minSampleCount,
        maxAbsDeltaPerFactor: maxDelta,
        generatedBy: operator
      });
      setStatus("已基于经验统计生成候选版本");
      candidateApi.reload();
    } catch (error) {
      setStatus(error instanceof Error ? error.message : String(error));
    }
  }

  async function transition(candidate: RuleCandidate, action: "approve" | "reject" | "activate") {
    if (!candidate.candidateId) return;
    const actionText = action === "approve" ? "批准" : action === "reject" ? "拒绝" : "生效";
    setStatus(`${actionText}中`);
    try {
      const response = await postJson(`/api/system/rule-evolution/candidates/${candidate.candidateId}/${action}`, {
        operator,
        approvalComment: approvalComment || `${operator} ${actionText}候选版本`
      });
      setSelected((response as ApiResponse<RuleCandidate>).data ?? candidate);
      setStatus(`已${actionText}`);
      candidateApi.reload();
    } catch (error) {
      setStatus(error instanceof Error ? error.message : String(error));
    }
  }

  return (
    <section className="panel rule-evolution">
      <PanelTitle icon={Brain} title="规则版本演进" right={`${candidates.length} 个候选`} />
      <div className="rule-toolbar">
        <label>
          <span>操作人</span>
          <input value={operator} onChange={(event) => setOperator(event.target.value)} />
        </label>
        <label>
          <span>最小样本</span>
          <input type="number" min={1} value={minSampleCount} onChange={(event) => setMinSampleCount(Number(event.target.value || 1))} />
        </label>
        <label>
          <span>单因子上限</span>
          <input type="number" step="0.01" min={0.01} max={1} value={maxDelta} onChange={(event) => setMaxDelta(Number(event.target.value || 0.15))} />
        </label>
        <label>
          <span>候选状态</span>
          <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
            <option value="ALL">全部</option>
            <option value="PENDING_APPROVAL">待批准</option>
            <option value="APPROVED">已批准</option>
            <option value="EFFECTIVE">已生效</option>
            <option value="REJECTED">已拒绝</option>
          </select>
        </label>
        <button className="primary" onClick={generate}>从经验统计生成候选</button>
        <button className="icon-button" onClick={candidateApi.reload} title="刷新候选"><RefreshCw size={17} /></button>
        <p className="form-status">{status}</p>
      </div>
      <div className="rule-status-row">
        {["PENDING_APPROVAL", "APPROVED", "EFFECTIVE", "REJECTED"].map((item) => (
          <button key={item} className={statusFilter === item ? "active" : ""} onClick={() => setStatusFilter(statusFilter === item ? "ALL" : item)}>
            <span>{statusText(item)}</span>
            <b>{statusCounts[item] ?? 0}</b>
          </button>
        ))}
      </div>
      {candidateApi.loading || candidateApi.error ? (
        <State loading={candidateApi.loading} error={candidateApi.error} reload={candidateApi.reload} />
      ) : !filteredCandidates.length ? <Empty /> : (
        <div className="rule-candidate-grid">
          <div className="rule-candidate-list">
            {filteredCandidates.map((candidate) => (
              <button
                key={candidate.candidateId}
                className={selected?.candidateId === candidate.candidateId ? "selected" : ""}
                onClick={() => setSelected(candidate)}
              >
                <b>{display(candidate.engineType)} · <StatusBadge status={candidate.status} /></b>
                <span>{display(candidate.baseRuleVersion)}{" -> "}{display(candidate.targetRuleVersion)}</span>
                <small>{display(candidate.factorChangeCount)} 个因子 · 样本 {display(candidate.sampleCount)} · Δ {display(candidate.totalAbsDelta)}</small>
              </button>
            ))}
          </div>
          <div className="rule-candidate-detail">
            {selected ? (
              <>
                <div className="rule-detail-head">
                  <div>
                    <span className="eyebrow">{display(selected.engineType)}</span>
                    <h2>{display(selected.targetRuleVersion)}</h2>
                    <p>{display(selected.baseRuleVersion)}{" -> "}{display(selected.targetRuleVersion)}</p>
                  </div>
                  <StatusBadge status={selected.status} />
                </div>
                <RuleStateFlow status={selected.status} />
                <div className="rule-metrics">
                  <MetricCard label="因子变化" value={display(selected.factorChangeCount)} sub="权重发生调整" />
                  <MetricCard label="样本数" value={display(selected.sampleCount)} sub="用于经验统计" />
                  <MetricCard label="总调整量" value={display(selected.totalAbsDelta)} sub="绝对 delta" />
                  <MetricCard label="生效时间" value={display(selected.effectiveAt)} sub={display(selected.approvedBy, "待人工确认")} />
                </div>
                <label className="approval-comment">
                  <span>审批意见</span>
                  <textarea value={approvalComment} onChange={(event) => setApprovalComment(event.target.value)} placeholder="写清楚为什么批准、拒绝或生效；这会进入规则演进审计链。" />
                </label>
                <div className="candidate-actions">
                  <button className="primary" onClick={() => transition(selected, "approve")} disabled={selected.status !== "PENDING_APPROVAL" && selected.status !== "GENERATED"}>批准为 DRAFT</button>
                  <button onClick={() => transition(selected, "activate")} disabled={selected.status !== "APPROVED"}>生效 ACTIVE</button>
                  <button onClick={() => transition(selected, "reject")} disabled={selected.status === "EFFECTIVE"}>拒绝</button>
                </div>
                <div className="narrative-block">
                  <p><b>生成原因</b>{display(selected.reasonSummary)}</p>
                  <p><b>风控约束</b>{display(selected.riskSummary)}</p>
                </div>
                <DataPanel title="因子调整明细" rows={selected.factorChanges ?? []} columns={["factorCode", "factorName", "currentWeight", "suggestedDelta", "suggestedWeight", "sampleCount", "hitCount", "missCount", "conflictHitCount", "hitRate", "avgContributionScore", "suggestedAction", "changeReason"]} />
              </>
            ) : <Empty />}
          </div>
        </div>
      )}
    </section>
  );
}

function StatusBadge({ status }: { status?: string }) {
  const className = String(status ?? "UNKNOWN").toLowerCase().replace(/_/g, "-");
  return <span className={`status-badge ${className}`}>{statusText(status)}</span>;
}

function RuleStateFlow({ status }: { status?: string }) {
  const steps = ["PENDING_APPROVAL", "APPROVED", "EFFECTIVE"];
  const currentIndex = steps.indexOf(status ?? "");
  return (
    <div className="rule-state-flow">
      {steps.map((step, index) => (
        <span key={step} className={index <= currentIndex ? "active" : ""}>{statusText(step)}</span>
      ))}
      {status === "REJECTED" && <span className="rejected active">已拒绝</span>}
    </div>
  );
}

function Hero({ context }: { context: InferenceContext }) {
  const chain = context.chain ?? {};
  return (
    <section className="hero">
      <div>
        <p className="eyebrow">Market Inference Sandbox</p>
        <h2>{display(chain.currentStage, "等待周期判断")}</h2>
        <p>{display(chain.chainSlogan, "判断 -> 证据 -> 冲突 -> 明日验证 -> 市场反馈 -> 奖惩 -> 人工修正 -> 经验沉淀")}</p>
      </div>
      <div className="hero-grid">
        <HeroItem label="策略边界" value={display(chain.strategyBoundary)} />
        <HeroItem label="主线机会" value={display(chain.opportunityState)} />
        <HeroItem label="参与结论" value={display(chain.participationDecision)} />
        <HeroItem label="风险状态" value={display(chain.riskState)} />
      </div>
    </section>
  );
}

function HeroItem({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function ChainMap({ nodes }: { nodes: ChainNode[] }) {
  if (!nodes.length) return <EmptyPanel title="推演链路" />;
  return (
    <section className="panel">
      <PanelTitle icon={CircleDot} title="核心推演链路" right={`${nodes.length} 个判断节点`} />
      <div className="chain-map">
        {nodes.map((node, index) => (
          <React.Fragment key={`${node.nodeCode}-${index}`}>
            <div className={node.conflictCount || node.warningCount ? "chain-node warn" : "chain-node"}>
              <span>{node.nodeName}</span>
              <strong>{display(node.targetName || node.targetCode || node.nodeCode)}</strong>
              <small>{formatConfidence(node.confidence)} · 证据 {node.evidenceCount ?? 0} · 冲突 {node.conflictCount ?? 0}</small>
            </div>
            {index < nodes.length - 1 && <ChevronRight className="chain-arrow" size={18} />}
          </React.Fragment>
        ))}
      </div>
    </section>
  );
}

function CandidateBoard({ title, blocks, kind, expanded = false }: { title: string; blocks: JudgmentBlock[]; kind: "mainline" | "leader"; expanded?: boolean }) {
  return (
    <section className="panel">
      <PanelTitle icon={kind === "mainline" ? GitBranch : Swords} title={title} right={`${blocks.length} 个候选`} />
      {!blocks.length ? <Empty /> : (
        <div className={expanded ? "candidate-list expanded" : "candidate-list"}>
          {blocks.map((block, index) => {
            const detail = objectOf(block.detail);
            const name = kind === "mainline" ? detail.plateName : detail.stockName;
            const code = kind === "mainline" ? detail.plateCode : detail.stockCode;
            const status = kind === "mainline" ? detail.mainlineStatus : detail.leaderStatus;
            const score = kind === "mainline" ? detail.candidateScore : detail.candidateScore;
            return (
              <article key={`${String(code)}-${index}`} className="candidate-item">
                <div className="rank">{index + 1}</div>
                <div>
                  <h3>{display(name, block.meta?.targetName ?? "候选")}</h3>
                  <p>{display(code)} · {display(status)} · {formatConfidence(block.meta?.confidence)}</p>
                  {expanded && <p className="candidate-reason">{display(detail.candidateReason || detail.leaderReason || detail.lifecycleReason || block.conclusion)}</p>}
                </div>
                <strong>{display(score)}</strong>
              </article>
            );
          })}
        </div>
      )}
    </section>
  );
}

function LifecyclePanel({ block, index }: { block: JudgmentBlock; index: number }) {
  const detail = objectOf(block.detail);
  const steps = ["IGNITION", "FERMENTATION", "CONFIRMATION", "ACCELERATION", "CONSENSUS", "DIVERGENCE", "RE_CONSENSUS", "DECLINE", "RECESSION"];
  const current = String(detail.lifecycleStage ?? "");
  return (
    <section className="panel">
      <PanelTitle icon={GitBranch} title={`生命周期 ${index}`} right={display(detail.plateName)} />
      <div className="lifecycle">
        {steps.map((step) => <span key={step} className={step === current ? "active" : ""}>{stageText(step)}</span>)}
      </div>
      <div className="narrative-block">
        <p><b>阶段理由</b>{display(detail.lifecycleReason)}</p>
        <p><b>风险</b>{display(detail.lifecycleRisk)}</p>
        <p><b>明日验证</b>{display(detail.lifecycleNextSignal || detail.tomorrowValidation)}</p>
      </div>
    </section>
  );
}

function JudgmentPanel({ title, block, accent = "gray" }: { title: string; block?: JudgmentBlock; accent?: "blue" | "red" | "green" | "amber" | "gray" }) {
  if (!block) return <EmptyPanel title={title} />;
  const detail = objectOf(block.detail);
  return (
    <section className={`panel judgment accent-${accent}`}>
      <PanelTitle icon={Target} title={title} right={formatConfidence(block.meta?.confidence)} />
      <strong className="conclusion">{display(block.conclusion, "暂无判断")}</strong>
      <div className="summary-grid">
        {summaryPairs(detail).map(([label, value]) => <SummaryCell key={label} label={label} value={value} />)}
      </div>
      <Narrative detail={detail} />
      <EvidenceTabs block={block} />
    </section>
  );
}

function EvidenceTabs({ block }: { block: JudgmentBlock }) {
  return (
    <div className="evidence-grid">
      <EvidenceColumn title="支持证据" items={block.evidenceList ?? []} tone="green" />
      <EvidenceColumn title="冲突证据" items={block.conflictList ?? []} tone="amber" />
      <EvidenceColumn title="风险预警" items={block.warningList ?? []} tone="red" />
    </div>
  );
}

function EvidenceColumn({ title, items, tone }: { title: string; items: EvidenceItem[]; tone: "green" | "amber" | "red" }) {
  return (
    <div className={`evidence-column ${tone}`}>
      <b>{title}</b>
      {items.length ? items.slice(0, 6).map((item, index) => (
        <p key={`${item.evidenceCode}-${index}`}>
          <span>{display(item.title || item.evidenceTitle || item.factorCode || item.evidenceCode)}</span>
          <small>{display(item.score)} / {display(item.weight)}</small>
        </p>
      )) : <small>暂无</small>}
    </div>
  );
}

type NarrativeField = [string, unknown];

function Narrative({ detail }: { detail: Record<string, unknown> }) {
  const allFields: NarrativeField[] = [
    ["策略边界", detail.strategyBoundary],
    ["适用模式", detail.allowedMode],
    ["假信号风险", detail.falseSignalRisk],
    ["参与结论", detail.participationDecision],
    ["强度证据", detail.strengthEvidence],
    ["持续证据", detail.continuityEvidence],
    ["扩展证据", detail.extensibilityEvidence],
    ["竞争证据", detail.competitivenessEvidence],
    ["带动证据", detail.driveEvidence],
    ["未满足", detail.unmetCondition],
    ["明日验证", detail.tomorrowValidation]
  ];
  const fields = allFields.filter(([, value]) => hasValue(value));
  if (!fields.length) return null;
  return (
    <div className="narrative-block">
      {fields.slice(0, 7).map(([label, value]) => (
        <p key={label}>
          <b>{label}</b>
          {display(value)}
        </p>
      ))}
    </div>
  );
}

function FactorTable({ title, factors }: { title: string; factors: Record<string, unknown>[] }) {
  return <DataPanel title={title} rows={factors} columns={["factorCode", "factorName", "factorValue", "score", "weight", "reason"]} />;
}

function WatchPanel({ items, compact = false }: { items: NextWatchItem[]; compact?: boolean }) {
  const sorted = [...items].sort((a, b) => Number(b.priority ?? 0) - Number(a.priority ?? 0));
  return (
    <section className="panel">
      <PanelTitle icon={ListChecks} title="明日验证清单" right={`${sorted.length} 条`} />
      {!sorted.length ? <Empty /> : (
        <div className={compact ? "watch-list compact" : "watch-list"}>
          {sorted.map((item, index) => (
            <article key={item.watchId ?? index} className="watch-item">
              <div>
                <b>{display(item.title)}</b>
                <p>{display(item.conditionExpression)}</p>
                <small>{display(item.engineType)} · {display(item.targetName || item.targetCode)} · {display(item.watchDate)}</small>
              </div>
              <div>
                <span className="good">{display(item.expectedSignal)}</span>
                <span className="bad">{display(item.riskSignal)}</span>
              </div>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}

function CorrectionForm({ context, query }: { context: InferenceContext; query: Query }) {
  const [engineType, setEngineType] = useState("CYCLE");
  const [targetCode, setTargetCode] = useState("MARKET");
  const [reason, setReason] = useState("");
  const [status, setStatus] = useState("");

  async function submit() {
    setStatus("提交中");
    const block = blockByEngine(context, engineType);
    const payload = {
      tradeDate: query.tradeDate || context.query?.tradeDate,
      asOfDate: query.asOfDate || context.query?.asOfDate || query.tradeDate || context.query?.tradeDate,
      judgementMode: query.judgementMode || "RETROSPECTIVE",
      engineType,
      targetType: engineType === "CYCLE" || engineType === "RISK" ? "MARKET" : engineType === "LEADER" ? "STOCK" : "PLATE",
      targetCode,
      judgementId: block?.meta?.judgementId,
      correctionType: "CONCLUSION",
      correctionReason: reason || "人工复盘修正",
      reviewer: "manual",
      items: [{ fieldName: "conclusion", oldValue: block?.conclusion ?? "", newValue: reason, fieldDesc: "人工修正结论" }]
    };
    try {
      await postJson("/api/review/correction", payload);
      setStatus("已提交，已进入经验成长");
    } catch (error) {
      setStatus(error instanceof Error ? error.message : String(error));
    }
  }

  return (
    <section className="panel">
      <PanelTitle icon={Wrench} title="判断修正" />
      <div className="form-grid">
        <label><span>引擎</span><select value={engineType} onChange={(event) => setEngineType(event.target.value)}>{["CYCLE", "MAINLINE", "LEADER", "DIVERGENCE_CONSENSUS", "RISK"].map((item) => <option key={item}>{item}</option>)}</select></label>
        <label><span>对象代码</span><input value={targetCode} onChange={(event) => setTargetCode(event.target.value)} /></label>
        <label className="full"><span>修正原因</span><textarea value={reason} onChange={(event) => setReason(event.target.value)} placeholder="例如：该日机器人只是弱修复，不应进入主线确认。" /></label>
        <button className="primary" onClick={submit}>提交修正并反哺</button>
        <p className="form-status">{status}</p>
      </div>
    </section>
  );
}

function EvidenceLabelForm({ evidence, context, query }: { evidence: EvidenceItem | null; context: InferenceContext; query: Query }) {
  const [labelResult, setLabelResult] = useState("VALID");
  const [reason, setReason] = useState("");
  const [status, setStatus] = useState("");
  const judgementId = findJudgementIdForEvidence(context, evidence);

  async function submit() {
    if (!evidence?.evidenceCode && !evidence?.factorCode) {
      setStatus("先选择一条证据");
      return;
    }
    setStatus("提交中");
    try {
      await postJson("/api/review/evidence-label", {
        evidenceId: evidence.evidenceCode,
        judgementId,
        tradeDate: query.tradeDate || context.query?.tradeDate,
        labelResult,
        labelReason: reason || "人工证据标注",
        reviewer: "manual"
      });
      setStatus("已提交，证据已进入经验样本");
    } catch (error) {
      setStatus(error instanceof Error ? error.message : String(error));
    }
  }

  return (
    <section className="panel">
      <PanelTitle icon={BookOpenCheck} title="证据标注" />
      <div className="selected-evidence">
        <b>{display(evidence?.title || evidence?.evidenceTitle || evidence?.evidenceCode, "未选择证据")}</b>
        <p>{display(evidence?.description || evidence?.evidenceDesc)}</p>
      </div>
      <div className="form-grid">
        <label><span>标注</span><select value={labelResult} onChange={(event) => setLabelResult(event.target.value)}>{["VALID", "INVALID", "OVER_WEIGHTED", "UNDER_WEIGHTED"].map((item) => <option key={item}>{item}</option>)}</select></label>
        <label className="full"><span>原因</span><textarea value={reason} onChange={(event) => setReason(event.target.value)} placeholder="说明这条证据为什么有效/无效/权重不合适。" /></label>
        <button className="primary" onClick={submit}>提交证据标注</button>
        <p className="form-status">{status}</p>
      </div>
    </section>
  );
}

function EvidencePicker({ context, selected, onSelect }: { context: InferenceContext; selected: EvidenceItem | null; onSelect: (item: EvidenceItem) => void }) {
  const items = collectEvidence(context, "evidenceList").concat(collectEvidence(context, "conflictList"), collectEvidence(context, "warningList"));
  return (
    <section className="panel">
      <PanelTitle icon={BookOpenCheck} title="选择证据" right={`${items.length} 条`} />
      <div className="evidence-picker">
        {items.length ? items.map((item, index) => (
          <button key={`${item.evidenceCode}-${index}`} className={selected === item ? "selected" : ""} onClick={() => onSelect(item)}>
            <b>{display(item.title || item.evidenceTitle || item.factorCode)}</b>
            <span>{display(item.evidenceType)} · {display(item.score)} · {display(item.sourceTable)}</span>
          </button>
        )) : <Empty />}
      </div>
    </section>
  );
}

function DataPanel({ title, rows, columns }: { title: string; rows: Record<string, unknown>[]; columns: string[] }) {
  return (
    <section className="panel">
      <PanelTitle icon={BarChart3} title={title} right={`${rows.length} 行`} />
      {!rows.length ? <Empty /> : <div className="table-wrap"><table><thead><tr>{columns.map((column) => <th key={column}>{column}</th>)}</tr></thead><tbody>{rows.map((row, index) => <tr key={index}>{columns.map((column) => <td key={column}>{display(row[column])}</td>)}</tr>)}</tbody></table></div>}
    </section>
  );
}

function SectionIntro({ icon: Icon, title, desc }: { icon: typeof Activity; title: string; desc: string }) {
  return (
    <section className="section-intro">
      <Icon size={22} />
      <div>
        <h2>{title}</h2>
        <p>{desc}</p>
      </div>
    </section>
  );
}

function MetricCard({ label, value, sub, tone = "default" }: { label: string; value: string; sub?: string; tone?: "default" | "red" }) {
  return (
    <section className={`metric-card ${tone}`}>
      <span>{label}</span>
      <strong>{value}</strong>
      {sub && <p>{sub}</p>}
    </section>
  );
}

function SummaryCell({ label, value }: { label: string; value: string }) {
  return <div><span>{label}</span><strong>{value}</strong></div>;
}

function PanelTitle({ icon: Icon, title, right }: { icon: typeof Activity; title: string; right?: string }) {
  return (
    <div className="panel-title">
      <h2><Icon size={17} />{title}</h2>
      {right && <span>{right}</span>}
    </div>
  );
}

function StatusPill({ icon: Icon, label, value }: { icon: typeof Activity; label: string; value: string }) {
  return (
    <div className="status-pill">
      <Icon size={16} />
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function EmptyPanel({ title }: { title: string }) {
  return (
    <section className="panel">
      <PanelTitle icon={AlertTriangle} title={title} />
      <Empty />
    </section>
  );
}

function Empty() {
  return <p className="empty">暂无数据</p>;
}

function State({ loading, error, reload }: { loading?: boolean; error?: string; reload: () => void }) {
  return (
    <section className="panel state">
      <div>
        <b>{loading ? "加载中" : "接口异常"}</b>
        <p>{loading ? "正在读取市场推演上下文..." : error}</p>
      </div>
      <button className="icon-button" onClick={reload} title="刷新"><RefreshCw size={18} /></button>
    </section>
  );
}

function useApi<T>(endpoint: string, query: Query, enabled = true) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(enabled);
  const [error, setError] = useState("");
  const [tick, setTick] = useState(0);
  useEffect(() => {
    if (!enabled) return;
    const params = new URLSearchParams();
    Object.entries(query).forEach(([key, value]) => {
      if (value) params.set(key, value);
    });
    const controller = new AbortController();
    setLoading(true);
    setError("");
    fetch(`${API_BASE}${endpoint}?${params.toString()}`, { signal: controller.signal })
      .then((response) => readApiResponse<T>(response))
      .then((json) => setData(json.data))
      .catch((reason) => {
        if (reason instanceof DOMException && reason.name === "AbortError") return;
        setError(reason instanceof Error ? reason.message : String(reason));
      })
      .finally(() => setLoading(false));
    return () => controller.abort();
  }, [endpoint, JSON.stringify(query), tick, enabled]);
  return { data, loading, error, reload: () => setTick((value) => value + 1) };
}

async function postJson(endpoint: string, body: unknown) {
  const response = await fetch(`${API_BASE}${endpoint}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  });
  return readApiResponse<unknown>(response);
}

async function readApiResponse<T>(response: Response): Promise<ApiResponse<T>> {
  const text = await response.text();
  let json: ApiResponse<T> | null = null;
  if (text) {
    try {
      json = JSON.parse(text) as ApiResponse<T>;
    } catch {
      json = null;
    }
  }
  if (!response.ok) {
    throw new Error(json?.message || response.statusText || `HTTP ${response.status}`);
  }
  if (json && json.success === false) {
    throw new Error(`${json.code ?? "ERROR"}: ${json.message ?? "请求失败"}`);
  }
  return json ?? ({ success: true, code: "OK", message: "success", data: undefined as T });
}

function blockByEngine(context: InferenceContext, engineType: string): JudgmentBlock | undefined {
  if (engineType === "CYCLE") return context.cycleCard;
  if (engineType === "RISK") return context.riskCard;
  if (engineType === "DIVERGENCE_CONSENSUS") return context.divergenceCard;
  if (engineType === "MAINLINE") return context.mainlineCards?.[0];
  if (engineType === "LEADER") return context.leaderCards?.[0];
  return undefined;
}

function findJudgementIdForEvidence(context: InferenceContext, evidence: EvidenceItem | null) {
  if (!evidence) return "";
  const blocks = [context.cycleCard, ...(context.mainlineCards ?? []), ...(context.leaderCards ?? []), context.divergenceCard, context.riskCard].filter(Boolean) as JudgmentBlock[];
  const owner = blocks.find((block) => [...(block.evidenceList ?? []), ...(block.conflictList ?? []), ...(block.warningList ?? [])].includes(evidence));
  return owner?.meta?.judgementId ?? "";
}

function collectEvidence(context: InferenceContext, key: "evidenceList" | "conflictList" | "warningList") {
  const blocks = [context.cycleCard, ...(context.mainlineCards ?? []), ...(context.leaderCards ?? []), context.divergenceCard, context.riskCard].filter(Boolean) as JudgmentBlock[];
  return blocks.flatMap((block) => block[key] ?? []);
}

function summaryPairs(detail: Record<string, unknown>): [string, string][] {
  const candidates: [string, unknown][] = [
    ["状态", detail.marketCycleStage ?? detail.mainlineStatus ?? detail.leaderStatus ?? detail.state ?? detail.riskLevel],
    ["阶段", detail.emotionCycleStage ?? detail.lifecycleStageName ?? detail.leaderType ?? detail.riskType],
    ["强度", detail.stageScore ?? detail.strengthScore ?? detail.positionScore ?? detail.consensusScore ?? detail.riskScore],
    ["风险", detail.falseSignalRisk ?? detail.rearRiskScore ?? detail.challengeRiskScore ?? detail.brokenLimitRiskScore ?? detail.lossSpreadScore]
  ];
  return candidates.filter(([, value]) => hasValue(value)).map(([label, value]) => [label, display(value)]);
}

function arrayOf(value: unknown): Record<string, unknown>[] {
  return Array.isArray(value) ? value as Record<string, unknown>[] : [];
}

function objectOf(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" ? value as Record<string, unknown> : {};
}

function totalCount(nodes: ChainNode[] | undefined, key: keyof ChainNode) {
  return (nodes ?? []).reduce((sum, node) => sum + Number(node[key] ?? 0), 0);
}

function countByStatus(items: RuleCandidate[]) {
  const counts: Record<string, number> = {};
  items.forEach((item) => {
    const value = String(item.status ?? "UNKNOWN");
    counts[value] = (counts[value] ?? 0) + 1;
  });
  return counts;
}

function statusText(status?: string) {
  const map: Record<string, string> = {
    GENERATED: "已生成",
    PENDING_APPROVAL: "待批准",
    APPROVED: "已批准",
    EFFECTIVE: "已生效",
    REJECTED: "已拒绝",
    DRAFT: "草稿",
    ACTIVE: "生效",
    ARCHIVED: "归档"
  };
  return map[String(status ?? "")] ?? display(status, "未知");
}

function formatConfidence(value: unknown) {
  if (value === null || value === undefined || value === "") return "置信 --";
  const numeric = Number(value);
  if (!Number.isNaN(numeric)) return `置信 ${(numeric * (numeric <= 1 ? 100 : 1)).toFixed(0)}%`;
  return `置信 ${String(value)}`;
}

function stageText(stage: string) {
  const map: Record<string, string> = {
    IGNITION: "点火",
    FERMENTATION: "发酵",
    CONFIRMATION: "确认",
    ACCELERATION: "加速",
    CONSENSUS: "一致",
    DIVERGENCE: "分歧",
    RE_CONSENSUS: "再一致",
    DECLINE: "衰退",
    RECESSION: "退潮"
  };
  return map[stage] ?? stage;
}

function hasValue(value: unknown) {
  return value !== null && value !== undefined && value !== "";
}

function display(value: unknown, fallback = "--") {
  if (!hasValue(value)) return fallback;
  if (typeof value === "number") return Number.isInteger(value) ? String(value) : value.toFixed(4);
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}

createRoot(document.getElementById("root")!).render(<App />);
