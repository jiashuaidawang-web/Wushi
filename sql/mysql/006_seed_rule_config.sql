USE wushi;

START TRANSACTION;

INSERT INTO rule_version
    (rule_version, rule_name, engine_type, status, description, effective_date, created_by)
VALUES
    ('v0.1.0', '周期识别规则 v0.1.0', 'CYCLE', 'ACTIVE', '基于全市场宽度、涨跌停结构、赚钱亏钱效应识别市场周期阶段，所有结论必须输出证据、冲突证据、置信度和明日验证点。', CURRENT_DATE, 'system_seed'),
    ('v0.1.0', '主线识别规则 v0.1.0', 'MAINLINE', 'ACTIVE', '基于连续活跃、涨停梯队、龙头质量、中军承接、后排反馈和资金强度识别主线确认候选。', CURRENT_DATE, 'system_seed'),
    ('v0.1.0', '龙头竞争规则 v0.1.0', 'LEADER', 'ACTIVE', '基于空间高度、带动性、辨识度、分歧修复和挑战风险识别龙头地位。', CURRENT_DATE, 'system_seed'),
    ('v0.1.0', '分歧一致规则 v0.1.0', 'DIVERGENCE_CONSENSUS', 'ACTIVE', '基于分歧强度、回封质量、后排修复和炸板风险判断分歧是否转一致。', CURRENT_DATE, 'system_seed'),
    ('v0.1.0', '风险雷达规则 v0.1.0', 'RISK', 'ACTIVE', '基于高位负反馈、炸板率、亏钱扩散、龙头失败和板块失速识别系统性风险。', CURRENT_DATE, 'system_seed'),
    ('v0.1.0', '市场总览聚合规则 v0.1.0', 'MARKET_OVERVIEW', 'ACTIVE', '聚合周期、主线、龙头、分歧一致、风险和明日观察，服务任意交易日总览。', CURRENT_DATE, 'system_seed'),
    ('v0.1.0', '历史相似规则 v0.1.0', 'HISTORICAL_SIMILARITY', 'ACTIVE', '用因子向量匹配历史相似交易日，并展示后续行情路径和差异证据。', CURRENT_DATE, 'system_seed'),
    ('v0.1.0', '前向验证规则 v0.1.0', 'FORWARD_VALIDATION', 'ACTIVE', '对判断结论、证据和明日观察进行 T+1/T+3/T+5 验证，形成奖励或惩罚。', CURRENT_DATE, 'system_seed'),
    ('v0.1.0', '经验成长规则 v0.1.0', 'EXPERIENCE', 'ACTIVE', '根据前向验证、人工修正和样本确认更新因子经验分，辅助后续置信度调整。', CURRENT_DATE, 'system_seed')
ON DUPLICATE KEY UPDATE
    rule_name = VALUES(rule_name),
    status = VALUES(status),
    description = VALUES(description),
    effective_date = VALUES(effective_date),
    created_by = VALUES(created_by),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO factor_definition
    (factor_code, factor_name, engine_type, factor_direction, factor_desc, source_table, value_type)
VALUES
    ('CYCLE_LIMIT_UP_COUNT', '涨停家数', 'CYCLE', 'POSITIVE', '全市场涨停家数，衡量情绪热度和赚钱效应外溢。', 'stock_limit_status_daily', 'NUMBER'),
    ('CYCLE_LIMIT_DOWN_COUNT', '跌停家数', 'CYCLE', 'NEGATIVE', '全市场跌停家数，衡量亏钱效应和退潮压力。', 'stock_limit_status_daily', 'NUMBER'),
    ('CYCLE_BROKEN_LIMIT_COUNT', '炸板家数', 'CYCLE', 'NEGATIVE', '全市场炸板数量，衡量分歧和承接失败。', 'stock_limit_status_daily', 'NUMBER'),
    ('CYCLE_MONEY_EFFECT_SCORE', '赚钱效应', 'CYCLE', 'POSITIVE', '由涨停、上涨家数、强势股延续性等合成的赚钱效应评分。', 'market_breadth_daily_snapshot', 'NUMBER'),
    ('CYCLE_LOSS_EFFECT_SCORE', '亏钱效应', 'CYCLE', 'NEGATIVE', '由跌停、破位、高位杀跌和连续亏损样本合成的亏钱效应评分。', 'market_breadth_daily_snapshot', 'NUMBER'),
    ('CYCLE_ABOVE_MA20_RATIO', '20日线以上比例', 'CYCLE', 'POSITIVE', '全市场处于 20 日线以上股票比例，衡量市场基础宽度。', 'market_breadth_daily_snapshot', 'RATIO'),
    ('MAINLINE_ACTIVE_DAYS', '连续活跃天数', 'MAINLINE', 'POSITIVE', '板块连续进入活跃名单的交易日数量，识别是否从题材脉冲走向主线。', 'stock_plate_daily_snapshot', 'NUMBER'),
    ('MAINLINE_LIMIT_UP_COUNT', '板块涨停家数', 'MAINLINE', 'POSITIVE', '板块内涨停家数，衡量主线情绪强度。', 'stock_plate_daily_snapshot', 'NUMBER'),
    ('MAINLINE_LADDER_INTEGRITY', '梯队完整度', 'MAINLINE', 'POSITIVE', '板块内首板、二板、中高位梯队是否完整，衡量主线结构。', 'plate_ladder_daily_snapshot', 'NUMBER'),
    ('MAINLINE_LEADER_QUALITY', '龙头质量', 'MAINLINE', 'POSITIVE', '主线龙头的空间高度、封单、回封、辨识度和带动性综合评分。', 'leader_competition_daily_snapshot', 'NUMBER'),
    ('MAINLINE_MIDDLE_ARMY_SUPPORT', '中军承接', 'MAINLINE', 'POSITIVE', '板块中军成交额、趋势承接和大市值稳定性，衡量主线容量。', 'stock_plate_daily_snapshot', 'NUMBER'),
    ('MAINLINE_REAR_RISK', '后排风险', 'MAINLINE', 'NEGATIVE', '后排炸板、冲高回落和亏钱扩散，衡量主线分歧隐患。', 'stock_limit_status_daily', 'NUMBER'),
    ('MAINLINE_CAPITAL_INFLOW', '主力净流入', 'MAINLINE', 'POSITIVE', '板块主力净流入强度，辅助确认资金是否愿意持续聚焦。', 'capital_flow_daily_snapshot', 'NUMBER'),
    ('LEADER_POSITION_SCORE', '龙头地位评分', 'LEADER', 'POSITIVE', '个股在空间、辨识度、人气、唯一性上的综合地位。', 'leader_competition_daily_snapshot', 'NUMBER'),
    ('LEADER_CONSECUTIVE_LIMIT_DAYS', '连板高度', 'LEADER', 'POSITIVE', '个股连续涨停高度，是短线情绪空间锚。', 'stock_limit_status_daily', 'NUMBER'),
    ('LEADER_DRIVE_SCORE', '带动性', 'LEADER', 'POSITIVE', '龙头涨停或修复后对板块跟涨、涨停和回封的带动程度。', 'leader_competition_daily_snapshot', 'NUMBER'),
    ('LEADER_POPULARITY_SCORE', '人气强度', 'LEADER', 'POSITIVE', '热度、成交、换手、关注度等合成的人气评分。', 'leader_competition_daily_snapshot', 'NUMBER'),
    ('LEADER_DIVERGENCE_REPAIR', '分歧修复', 'LEADER', 'POSITIVE', '龙头在分歧后回封、弱转强或承接修复的质量。', 'stock_limit_intraday_event', 'NUMBER'),
    ('LEADER_CHALLENGE_RISK', '被挑战风险', 'LEADER', 'NEGATIVE', '同板块或跨板块候选龙头对当前龙头地位的挑战强度。', 'leader_competition_daily_snapshot', 'NUMBER'),
    ('PATTERN_DIVERGENCE_SCORE', '分歧强度', 'DIVERGENCE_CONSENSUS', 'NEGATIVE', '炸板、开板、换手放大、后排掉队形成的分歧强度。', 'stock_limit_intraday_event', 'NUMBER'),
    ('PATTERN_CONSENSUS_SCORE', '一致强度', 'DIVERGENCE_CONSENSUS', 'POSITIVE', '板块内龙头、中军、后排同步修复形成的一致强度。', 'plate_daily_snapshot', 'NUMBER'),
    ('PATTERN_REFILL_QUALITY', '回封质量', 'DIVERGENCE_CONSENSUS', 'POSITIVE', '开板后的回封速度、封单恢复、回封后稳定性。', 'stock_limit_intraday_event', 'NUMBER'),
    ('PATTERN_BROKEN_LIMIT_RISK', '炸板风险', 'DIVERGENCE_CONSENSUS', 'NEGATIVE', '炸板率和炸板后回落幅度，衡量一致失败风险。', 'stock_limit_status_daily', 'RATIO'),
    ('PATTERN_REAR_FEEDBACK', '后排反馈', 'DIVERGENCE_CONSENSUS', 'POSITIVE', '后排是否修复、是否继续补涨，验证主线分歧是否被承接。', 'plate_daily_snapshot', 'NUMBER'),
    ('PATTERN_TURNOVER_ACCEPTANCE', '换手承接', 'DIVERGENCE_CONSENSUS', 'POSITIVE', '分歧时成交额、换手率和振幅是否表现为良性承接，而不是失控抛压。', 'stock_daily_kline', 'NUMBER'),
    ('PATTERN_SHRINK_ACCELERATION', '缩量加速', 'DIVERGENCE_CONSENSUS', 'POSITIVE', '封单稳定、开板少、成交缩量且涨幅保持强势，衡量一致阶段加速程度。', 'stock_daily_kline', 'NUMBER'),
    ('PATTERN_HIGH_POSITION_FEEDBACK', '高位反馈', 'DIVERGENCE_CONSENSUS', 'NEGATIVE', '高位断板、大阴、跌停和炸板失败等负反馈，判断分歧是否兑现为退潮。', 'high_position_feedback_daily', 'NUMBER'),
    ('RISK_HIGH_POSITION_FEEDBACK', '高位负反馈', 'RISK', 'NEGATIVE', '高位股跌停、大阴、断板后 A 杀等负反馈。', 'high_position_feedback_daily', 'NUMBER'),
    ('RISK_BROKEN_LIMIT_RATE', '炸板率', 'RISK', 'NEGATIVE', '炸板数量占冲板数量比例，衡量短线承接脆弱度。', 'stock_limit_status_daily', 'RATIO'),
    ('RISK_LOSS_SPREAD', '亏钱效应扩散', 'RISK', 'NEGATIVE', '亏钱样本从局部高位向中低位和后排扩散的程度。', 'market_breadth_daily_snapshot', 'NUMBER'),
    ('RISK_LEADER_FAIL', '龙头失败', 'RISK', 'NEGATIVE', '核心龙头断板后无法修复、带崩板块或情绪空间。', 'leader_competition_daily_snapshot', 'BOOLEAN'),
    ('RISK_PLATE_LOSS', '板块失速', 'RISK', 'NEGATIVE', '主线板块涨停减少、梯队断裂、资金流出和后排亏钱同步出现。', 'stock_plate_daily_snapshot', 'NUMBER')
ON DUPLICATE KEY UPDATE
    factor_name = VALUES(factor_name),
    engine_type = VALUES(engine_type),
    factor_direction = VALUES(factor_direction),
    factor_desc = VALUES(factor_desc),
    source_table = VALUES(source_table),
    value_type = VALUES(value_type),
    enabled = 1,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO rule_factor_weight
    (rule_version, engine_type, factor_code, weight, threshold_value, threshold_operator, evidence_type)
VALUES
    ('v0.1.0', 'CYCLE', 'CYCLE_LIMIT_UP_COUNT', 0.2200, 50.000000, 'GTE', 'SUPPORT'),
    ('v0.1.0', 'CYCLE', 'CYCLE_LIMIT_DOWN_COUNT', 0.1800, 10.000000, 'LTE', 'CONFLICT'),
    ('v0.1.0', 'CYCLE', 'CYCLE_BROKEN_LIMIT_COUNT', 0.1600, 35.000000, 'LTE', 'WARNING'),
    ('v0.1.0', 'CYCLE', 'CYCLE_MONEY_EFFECT_SCORE', 0.2000, 0.600000, 'GTE', 'SUPPORT'),
    ('v0.1.0', 'CYCLE', 'CYCLE_LOSS_EFFECT_SCORE', 0.1400, 0.500000, 'LTE', 'CONFLICT'),
    ('v0.1.0', 'CYCLE', 'CYCLE_ABOVE_MA20_RATIO', 0.1000, 0.500000, 'GTE', 'SUPPORT'),
    ('v0.1.0', 'MAINLINE', 'MAINLINE_ACTIVE_DAYS', 0.1800, 3.000000, 'GTE', 'SUPPORT'),
    ('v0.1.0', 'MAINLINE', 'MAINLINE_LIMIT_UP_COUNT', 0.1600, 8.000000, 'GTE', 'SUPPORT'),
    ('v0.1.0', 'MAINLINE', 'MAINLINE_LADDER_INTEGRITY', 0.1800, 0.600000, 'GTE', 'SUPPORT'),
    ('v0.1.0', 'MAINLINE', 'MAINLINE_LEADER_QUALITY', 0.1800, 0.650000, 'GTE', 'SUPPORT'),
    ('v0.1.0', 'MAINLINE', 'MAINLINE_MIDDLE_ARMY_SUPPORT', 0.1300, 0.550000, 'GTE', 'SUPPORT'),
    ('v0.1.0', 'MAINLINE', 'MAINLINE_REAR_RISK', 0.1000, 0.450000, 'LTE', 'WARNING'),
    ('v0.1.0', 'MAINLINE', 'MAINLINE_CAPITAL_INFLOW', 0.0700, 0.000000, 'GT', 'SUPPORT'),
    ('v0.1.0', 'LEADER', 'LEADER_POSITION_SCORE', 0.2400, 0.700000, 'GTE', 'SUPPORT'),
    ('v0.1.0', 'LEADER', 'LEADER_CONSECUTIVE_LIMIT_DAYS', 0.1600, 3.000000, 'GTE', 'SUPPORT'),
    ('v0.1.0', 'LEADER', 'LEADER_DRIVE_SCORE', 0.2000, 0.650000, 'GTE', 'SUPPORT'),
    ('v0.1.0', 'LEADER', 'LEADER_POPULARITY_SCORE', 0.1400, 0.600000, 'GTE', 'SUPPORT'),
    ('v0.1.0', 'LEADER', 'LEADER_DIVERGENCE_REPAIR', 0.1800, 0.600000, 'GTE', 'SUPPORT'),
    ('v0.1.0', 'LEADER', 'LEADER_CHALLENGE_RISK', 0.0800, 0.500000, 'LTE', 'WARNING'),
    ('v0.1.0', 'DIVERGENCE_CONSENSUS', 'PATTERN_DIVERGENCE_SCORE', 0.1800, 0.600000, 'GTE', 'WARNING'),
    ('v0.1.0', 'DIVERGENCE_CONSENSUS', 'PATTERN_CONSENSUS_SCORE', 0.2400, 0.650000, 'GTE', 'SUPPORT'),
    ('v0.1.0', 'DIVERGENCE_CONSENSUS', 'PATTERN_REFILL_QUALITY', 0.2400, 0.600000, 'GTE', 'SUPPORT'),
    ('v0.1.0', 'DIVERGENCE_CONSENSUS', 'PATTERN_BROKEN_LIMIT_RISK', 0.1800, 0.350000, 'LTE', 'CONFLICT'),
    ('v0.1.0', 'DIVERGENCE_CONSENSUS', 'PATTERN_REAR_FEEDBACK', 0.1600, 0.550000, 'GTE', 'SUPPORT'),
    ('v0.1.0', 'DIVERGENCE_CONSENSUS', 'PATTERN_TURNOVER_ACCEPTANCE', 0.1000, 0.520000, 'GTE', 'SUPPORT'),
    ('v0.1.0', 'DIVERGENCE_CONSENSUS', 'PATTERN_SHRINK_ACCELERATION', 0.0900, 0.550000, 'GTE', 'SUPPORT'),
    ('v0.1.0', 'DIVERGENCE_CONSENSUS', 'PATTERN_HIGH_POSITION_FEEDBACK', 0.0700, 0.450000, 'LTE', 'WARNING'),
    ('v0.1.0', 'RISK', 'RISK_HIGH_POSITION_FEEDBACK', 0.2600, 0.600000, 'GTE', 'CONFLICT'),
    ('v0.1.0', 'RISK', 'RISK_BROKEN_LIMIT_RATE', 0.2200, 0.350000, 'GTE', 'WARNING'),
    ('v0.1.0', 'RISK', 'RISK_LOSS_SPREAD', 0.2000, 0.550000, 'GTE', 'CONFLICT'),
    ('v0.1.0', 'RISK', 'RISK_LEADER_FAIL', 0.1800, 1.000000, 'EQ', 'CONFLICT'),
    ('v0.1.0', 'RISK', 'RISK_PLATE_LOSS', 0.1400, 0.600000, 'GTE', 'WARNING')
ON DUPLICATE KEY UPDATE
    weight = VALUES(weight),
    threshold_value = VALUES(threshold_value),
    threshold_operator = VALUES(threshold_operator),
    evidence_type = VALUES(evidence_type),
    enabled = 1,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO factor_combination_definition
    (combination_code, combination_name, engine_type, rule_version, factor_codes, condition_expression, expected_meaning)
VALUES
    ('COMBO_MAINLINE_CONFIRM_ROBOT_LIKE', '主线确认候选组合', 'MAINLINE', 'v0.1.0',
     '["MAINLINE_ACTIVE_DAYS","MAINLINE_LADDER_INTEGRITY","MAINLINE_LEADER_QUALITY","LEADER_DIVERGENCE_REPAIR","MAINLINE_MIDDLE_ARMY_SUPPORT","MAINLINE_REAR_RISK"]',
     'MAINLINE_ACTIVE_DAYS >= 3 AND MAINLINE_LADDER_INTEGRITY >= 0.60 AND MAINLINE_LEADER_QUALITY >= 0.65 AND LEADER_DIVERGENCE_REPAIR >= 0.60 AND MAINLINE_MIDDLE_ARMY_SUPPORT >= 0.55 AND MAINLINE_REAR_RISK <= 0.45',
     '板块已满足连续活跃、梯队完整、龙头分歧回封和中军承接，进入主线确认候选；若后排炸板率升高，则降级为有瑕疵候选。'),
    ('COMBO_DIVERGENCE_TO_CONSENSUS', '分歧转一致候选组合', 'DIVERGENCE_CONSENSUS', 'v0.1.0',
     '["PATTERN_DIVERGENCE_SCORE","PATTERN_REFILL_QUALITY","PATTERN_CONSENSUS_SCORE","PATTERN_REAR_FEEDBACK","PATTERN_BROKEN_LIMIT_RISK","PATTERN_TURNOVER_ACCEPTANCE","PATTERN_SHRINK_ACCELERATION","PATTERN_HIGH_POSITION_FEEDBACK"]',
     'PATTERN_DIVERGENCE_SCORE >= 0.60 AND PATTERN_REFILL_QUALITY >= 0.60 AND PATTERN_CONSENSUS_SCORE >= 0.65 AND PATTERN_REAR_FEEDBACK >= 0.55 AND PATTERN_BROKEN_LIMIT_RISK <= 0.35 AND PATTERN_TURNOVER_ACCEPTANCE >= 0.52 AND PATTERN_SHRINK_ACCELERATION >= 0.55 AND PATTERN_HIGH_POSITION_FEEDBACK <= 0.45',
     '市场经历充分分歧后，龙头回封质量提升，后排开始修复，换手承接健康且高位负反馈可控，具备分歧转一致候选特征。'),
    ('COMBO_CLIMAX_RISK', '一致过热风险组合', 'RISK', 'v0.1.0',
     '["PATTERN_CONSENSUS_SCORE","PATTERN_BROKEN_LIMIT_RISK","RISK_HIGH_POSITION_FEEDBACK","RISK_LOSS_SPREAD"]',
     'PATTERN_CONSENSUS_SCORE >= 0.80 AND (PATTERN_BROKEN_LIMIT_RISK >= 0.35 OR RISK_HIGH_POSITION_FEEDBACK >= 0.60 OR RISK_LOSS_SPREAD >= 0.55)',
     '一致强度过高但承接开始松动，高位或后排出现负反馈，进入高潮后风险观察。'),
    ('COMBO_LEADER_RISING', '龙头上位组合', 'LEADER', 'v0.1.0',
     '["LEADER_POSITION_SCORE","LEADER_DRIVE_SCORE","LEADER_DIVERGENCE_REPAIR","LEADER_CHALLENGE_RISK"]',
     'LEADER_POSITION_SCORE >= 0.70 AND LEADER_DRIVE_SCORE >= 0.65 AND LEADER_DIVERGENCE_REPAIR >= 0.60 AND LEADER_CHALLENGE_RISK <= 0.50',
     '候选个股具备地位、带动性和分歧修复，挑战风险可控，可作为龙头上位候选。'),
    ('COMBO_RECESSION_WARNING', '退潮预警组合', 'RISK', 'v0.1.0',
     '["RISK_LOSS_SPREAD","RISK_HIGH_POSITION_FEEDBACK","RISK_LEADER_FAIL","RISK_PLATE_LOSS"]',
     'RISK_LOSS_SPREAD >= 0.55 AND RISK_HIGH_POSITION_FEEDBACK >= 0.60 AND (RISK_LEADER_FAIL = 1 OR RISK_PLATE_LOSS >= 0.60)',
     '亏钱效应扩散，高位负反馈明显，核心龙头或主线板块失速，退潮风险显著。')
ON DUPLICATE KEY UPDATE
    combination_name = VALUES(combination_name),
    engine_type = VALUES(engine_type),
    factor_codes = VALUES(factor_codes),
    condition_expression = VALUES(condition_expression),
    expected_meaning = VALUES(expected_meaning),
    enabled = 1,
    updated_at = CURRENT_TIMESTAMP;

DELETE FROM data_quality_impact_config
WHERE data_domain IN ('MARKET', 'PLATE', 'STOCK', 'LIMIT_STATUS', 'INTRADAY', 'CAPITAL', 'RISK', 'INDEX');

INSERT INTO data_quality_impact_config
    (data_domain, table_name, missing_field, impact_pages, confidence_penalty, impact_desc)
VALUES
    ('STOCK', 'stock_daily_kline', NULL, '["MARKET_OVERVIEW","CYCLE_DASHBOARD","MAINLINE_DASHBOARD","LEADER_COMPETITION","HISTORICAL_SIMILARITY"]', 0.4000, '个股日 K 是宽度、强弱、历史相似和后续验证的底层事实，缺失会显著降低全局置信度。'),
    ('PLATE', 'stock_plate_daily_kline', NULL, '["MARKET_OVERVIEW","MAINLINE_DASHBOARD","HISTORICAL_SIMILARITY"]', 0.2500, '板块日 K 缺失会影响主线强度、板块趋势和历史相似判断。'),
    ('PLATE', 'stock_plate_relation_snapshot', 'relation_version', '["MAINLINE_DASHBOARD","LEADER_COMPETITION","RISK_RADAR"]', 0.2000, '历史板块成分关系若只能用当前版本回填，主线归因和龙头带动性需要降置信度。'),
    ('LIMIT_STATUS', 'stock_limit_status_daily', NULL, '["MARKET_OVERVIEW","CYCLE_DASHBOARD","MAINLINE_DASHBOARD","LEADER_COMPETITION","DIVERGENCE_CONSENSUS","RISK_RADAR"]', 0.3500, '涨停、跌停、炸板、连板和封单是情绪周期核心事实，缺失会影响所有短线判断。'),
    ('INTRADAY', 'stock_limit_intraday_event', NULL, '["DIVERGENCE_CONSENSUS","LEADER_COMPETITION","REVIEW_CORRECTION"]', 0.2000, '开板、回封、封单变化决定分歧转一致和龙头修复质量，缺失时只能做日线级判断。'),
    ('INTRADAY', 'stock_minute_kline', NULL, '["DIVERGENCE_CONSENSUS","LEADER_COMPETITION"]', 0.1500, '分钟线用于判断承接和回封过程，缺失会降低分时结构判断可信度。'),
    ('CAPITAL', 'capital_flow_daily_snapshot', NULL, '["MAINLINE_DASHBOARD","MARKET_OVERVIEW"]', 0.1000, '资金流数据用于辅助主线和中军承接，不作为唯一判断依据。'),
    ('RISK', 'high_position_feedback_daily', NULL, '["RISK_RADAR","CYCLE_DASHBOARD","MARKET_OVERVIEW"]', 0.2500, '高位负反馈是退潮与亏钱效应扩散的关键证据，缺失会低估风险。'),
    ('INDEX', 'index_daily_kline', NULL, '["MARKET_OVERVIEW","CYCLE_DASHBOARD","HISTORICAL_SIMILARITY"]', 0.1500, '指数环境用于识别市场背景和历史相似环境，缺失会影响周期背景判断。');

INSERT INTO dashboard_card_config
    (page_code, card_code, card_name, card_type, data_api, display_order, required_fields, thought_mapping)
VALUES
    ('MARKET_OVERVIEW', 'CYCLE_CARD', '市场周期卡', 'JUDGEMENT', '/api/cycle/dashboard', 10, '["stage","confidence","evidenceList","conflictList","ruleVersion"]', '回答今天或历史某日处于什么周期阶段，并说明为什么这么判断。'),
    ('MARKET_OVERVIEW', 'MAINLINE_CARDS', '主线机会卡', 'LIST', '/api/mainline/dashboard', 20, '["mainlineCandidates","evidenceList","nextWatchList"]', '展示哪些板块从题材脉冲进入主线确认候选，以及还缺什么条件。'),
    ('MARKET_OVERVIEW', 'LEADER_CARDS', '龙头竞争卡', 'LIST', '/api/leader/competition', 30, '["leaderCandidates","positionScore","driveScore","conflictList"]', '展示龙头是谁、为什么是它、挑战者在哪里。'),
    ('MARKET_OVERVIEW', 'RISK_CARD', '风险雷达卡', 'JUDGEMENT', '/api/risk/radar', 40, '["riskLevel","riskItems","confidence","nextWatchList"]', '展示当前市场最需要规避的负反馈和明日风险验证点。'),
    ('MARKET_OVERVIEW', 'NEXT_WATCH_CARD', '明日观察卡', 'LIST', '/api/market/overview', 50, '["nextWatchList","forwardValidationPlan"]', '把今日推演转成明日可验证条件，服务闭环成长。'),
    ('CYCLE_DASHBOARD', 'CYCLE_STAGE_CARD', '周期阶段卡', 'JUDGEMENT', '/api/cycle/dashboard', 10, '["stage","confidence","evidenceList","conflictList"]', '表达情绪周期不是标签，而是由宽度、赚钱效应、亏钱效应共同推导。'),
    ('CYCLE_DASHBOARD', 'BREADTH_CARD', '市场宽度卡', 'CHART', '/api/cycle/dashboard', 20, '["limitUpCount","limitDownCount","aboveMa20Ratio"]', '展示市场底层温度，避免只盯龙头忽略整体土壤。'),
    ('CYCLE_DASHBOARD', 'EVIDENCE_CARD', '支持证据卡', 'LIST', '/api/cycle/dashboard', 30, '["evidenceList"]', '逐条解释周期判断的正向证据。'),
    ('CYCLE_DASHBOARD', 'CONFLICT_CARD', '冲突证据卡', 'LIST', '/api/cycle/dashboard', 40, '["conflictList"]', '保留相反证据，避免输出绝对结论。'),
    ('MAINLINE_DASHBOARD', 'MAINLINE_RANK_CARD', '主线候选排行卡', 'TABLE', '/api/mainline/dashboard', 10, '["plateCode","plateName","score","confidence"]', '比较多个热点谁更接近主线。'),
    ('MAINLINE_DASHBOARD', 'LADDER_CARD', '涨停梯队卡', 'CHART', '/api/mainline/dashboard', 20, '["ladderIntegrity","limitUpLadder"]', '看主线是否有一板、二板、中高位的完整梯队。'),
    ('MAINLINE_DASHBOARD', 'MIDDLE_ARMY_CARD', '中军承接卡', 'JUDGEMENT', '/api/mainline/dashboard', 30, '["middleArmySupport","capitalInflow"]', '验证主线是否只有情绪小票，还是有容量承接。'),
    ('MAINLINE_DASHBOARD', 'REAR_RISK_CARD', '后排风险卡', 'JUDGEMENT', '/api/mainline/dashboard', 40, '["rearRisk","brokenLimitRate","conflictList"]', '提示后排炸板或亏钱是否会破坏主线确认。'),
    ('LEADER_COMPETITION', 'LEADER_RANK_CARD', '龙头排行卡', 'TABLE', '/api/leader/competition', 10, '["stockCode","stockName","positionScore","confidence"]', '展示龙头地位排序及其证据。'),
    ('LEADER_COMPETITION', 'CHALLENGER_CARD', '挑战者卡', 'LIST', '/api/leader/competition', 20, '["challengers","challengeRisk"]', '识别可能替代当前龙头的竞争者。'),
    ('LEADER_COMPETITION', 'LEADER_EVIDENCE_CARD', '龙头证据卡', 'LIST', '/api/leader/competition', 30, '["evidenceList","conflictList","nextWatchList"]', '解释龙头地位是怎样形成、如何被验证或证伪。'),
    ('DIVERGENCE_CONSENSUS', 'DIVERGENCE_STATE_CARD', '分歧一致状态卡', 'JUDGEMENT', '/api/divergence/dashboard', 10, '["state","confidence","evidenceList","conflictList"]', '判断分歧是否转一致，还是只是弱修复。'),
    ('DIVERGENCE_CONSENSUS', 'REFILL_QUALITY_CARD', '回封质量卡', 'CHART', '/api/divergence/dashboard', 20, '["refillQuality","openLimitEvents"]', '用回封速度和封单恢复解释修复质量。'),
    ('DIVERGENCE_CONSENSUS', 'BROKEN_LIMIT_CARD', '炸板风险卡', 'CHART', '/api/divergence/dashboard', 30, '["brokenLimitRate","rearFeedback"]', '观察一致失败的风险是否正在扩大。'),
    ('RISK_RADAR', 'RISK_LEVEL_CARD', '风险等级卡', 'JUDGEMENT', '/api/risk/radar', 10, '["riskLevel","confidence","ruleVersion"]', '给出风险等级但保留证据和置信度。'),
    ('RISK_RADAR', 'HIGH_POSITION_FEEDBACK_CARD', '高位负反馈卡', 'LIST', '/api/risk/radar', 20, '["highPositionFeedbackList"]', '展示高位股断板、跌停、大阴等退潮信号。'),
    ('RISK_RADAR', 'LOSS_SPREAD_CARD', '亏钱扩散卡', 'CHART', '/api/risk/radar', 30, '["lossSpread","riskItems"]', '判断亏钱效应是否从局部扩散到全局。'),
    ('HISTORICAL_SIMILARITY', 'SIMILAR_CASE_CARD', '历史相似样本卡', 'TABLE', '/api/history/similarity', 10, '["similarDates","similarityScore"]', '找出历史上结构相似的交易日，不用单日标签替代路径推演。'),
    ('HISTORICAL_SIMILARITY', 'SIMILAR_FACTOR_CARD', '相似因子卡', 'LIST', '/api/history/similarity', 20, '["matchedFactors","differentFactors"]', '解释为什么相似，以及哪里不像。'),
    ('HISTORICAL_SIMILARITY', 'FORWARD_PERFORMANCE_CARD', '后续表现卡', 'CHART', '/api/history/similarity', 30, '["forwardPerformance"]', '展示相似日之后 T+1/T+3/T+5 的演化路径。'),
    ('REVIEW_CORRECTION', 'CORRECTION_FORM_CARD', '人工修正卡', 'FORM', '/api/review/correction', 10, '["targetDate","engineType","originalConclusion","correctedConclusion"]', '允许人把市场老师的答案录入系统，修正错误理解。'),
    ('REVIEW_CORRECTION', 'EVIDENCE_LABEL_CARD', '证据标注卡', 'FORM', '/api/review/evidence-label', 20, '["evidenceId","label","reason"]', '标注证据是有效、误导还是需要降权。'),
    ('REVIEW_CORRECTION', 'SAMPLE_CONFIRM_CARD', '样本确认卡', 'FORM', '/api/review/sample-confirmation', 30, '["sampleDate","sampleType","confirmed"]', '确认历史样本是否具备教学意义。'),
    ('SYSTEM_GROWTH', 'FACTOR_EXPERIENCE_CARD', '因子经验卡', 'TABLE', '/api/system/growth', 10, '["factorCode","experienceScore","winRate"]', '展示每个因子在历史验证中的有效性。'),
    ('SYSTEM_GROWTH', 'COMBINATION_EXPERIENCE_CARD', '组合经验卡', 'TABLE', '/api/system/growth', 20, '["combinationCode","experienceScore","sampleCount"]', '展示组合条件是否真的能解释市场演化。'),
    ('SYSTEM_GROWTH', 'GROWTH_LOG_CARD', '成长日志卡', 'LIST', '/api/system/growth', 30, '["validationLogs","rewardPenalty"]', '记录系统每次被市场验证后的奖励和惩罚。')
ON DUPLICATE KEY UPDATE
    card_name = VALUES(card_name),
    card_type = VALUES(card_type),
    data_api = VALUES(data_api),
    display_order = VALUES(display_order),
    required_fields = VALUES(required_fields),
    thought_mapping = VALUES(thought_mapping),
    enabled = 1,
    updated_at = CURRENT_TIMESTAMP;

COMMIT;
