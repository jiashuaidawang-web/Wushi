package com.wushi.module.spider.eastmoney;

import com.fasterxml.jackson.databind.JsonNode;
import com.wushi.module.market.domain.row.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
public class EastMoneyFieldMapper {

    /**
     * 股票日K映射
     * 东财字段: f2=close, f3=changePct, f4=changeAmount, f5=volume, f6=amount
     * f7=amplitude, f8=turnoverRate, f12=stockCode, f13=exchange(0=SZ,1=SH), f14=stockName
     * f15=high, f16=low, f17=open, f18=preClose, f20=totalMarketValue, f21=floatMarketValue
     */
    public StockDailyKlineRow toStockDailyKline(LocalDate tradeDate, JsonNode node) {
        return new StockDailyKlineRow(
                tradeDate,
                text(node, "f12"),         // stockCode
                text(node, "f14"),         // stockName
                decimal(node, "f17"),      // open
                decimal(node, "f15"),      // high
                decimal(node, "f16"),      // low
                decimal(node, "f2"),       // close
                decimal(node, "f18"),      // preClose
                decimal(node, "f4"),       // changeAmount
                decimal(node, "f3"),       // changePct
                longValue(node, "f5"),     // volume
                decimal(node, "f6"),       // amount
                decimal(node, "f8"),       // turnoverRate
                decimal(node, "f7"),       // amplitude
                null,                      // limitUpPrice (接口不返回)
                null,                      // limitDownPrice (接口不返回)
                decimal(node, "f20"),      // totalMarketValue
                decimal(node, "f21"),      // floatMarketValue
                "1"                        // source: 1=东财
        );
    }

    /**
     * 板块维度映射
     * 东财字段: f12=plateCode(BKxxxx), f14=plateName
     */
    public StockPlateDimensionRow toPlateDimension(JsonNode node) {
        return new StockPlateDimensionRow(
                text(node, "f12"),   // plateCode
                text(node, "f14"),   // plateName
                "UNKNOWN",           // plateType (由调用方覆盖)
                "",                  // parentPlateCode
                "ACTIVE",            // status
                "1"                  // source: 1=东财
        );
    }

    /**
     * 板块日K映射
     * 东财字段同 stock_daily_kline: f12=plateCode, f14=plateName, f17=open, f15=high, f16=low, f2=close
     * f18=preClose, f3=changePct, f5=volume, f6=amount, f8=turnoverRate
     */
    public StockPlateDailyKlineRow toPlateDailyKline(LocalDate tradeDate, JsonNode node) {
        return new StockPlateDailyKlineRow(
                tradeDate,
                text(node, "f12"),    // plateCode
                text(node, "f14"),    // plateName
                "",                   // plateType (由调用方覆盖)
                decimal(node, "f17"), // open
                decimal(node, "f15"), // high
                decimal(node, "f16"), // low
                decimal(node, "f2"),  // close
                decimal(node, "f18"), // preClose
                decimal(node, "f3"),  // changePct
                longValue(node, "f5"), // volume
                decimal(node, "f6"),  // amount
                decimal(node, "f8"),  // turnoverRate
                BigDecimal.ZERO,      // mainNetInflow
                "1"                   // source: 1=东财
        );
    }

    /**
     * 板块个股关系映射
     */
    public StockPlateRelationSnapshotRow toPlateRelation(LocalDate tradeDate, String plateCode, String plateName,
                                                          String plateType, JsonNode node) {
        return new StockPlateRelationSnapshotRow(
                tradeDate,
                text(node, "f12"),          // stockCode
                text(node, "f14"),          // stockName
                plateCode,
                plateName,
                plateType,
                "HISTORICAL_CRAWLED",       // relationSource
                new BigDecimal("0.8000"),   // relationConfidence
                1,                          // isCurrentBackfill
                "1"                         // source: 1=东财
        );
    }

    /**
     * 股票池快照映射
     * 涨停池字段: c=stockCode, m=exchange(0=SZ,1=SH), n=stockName, p=price(分), zdp=pctChange
     * amount=成交额, ltsz=floatMarketValue, tshare=totalMarketValue, hs=turnoverRate
     * lbc=连板数, fbt=首次封板时间(HHmmss), lbt=最后封板时间, zbc=炸板次数, fund=封单资金
     * hybk=行业板块, zttj={days:连板天数, ct:涨停次数}
     */
    public StockPoolDailySnapshotRow toPoolSnapshot(LocalDate tradeDate, String poolType, JsonNode node) {
        return new StockPoolDailySnapshotRow(
                tradeDate,
                text(node, "c"),     // stockCode
                text(node, "n"),     // stockName
                node.path("m").asInt(0) == 1 ? "SH" : "SZ",  // market
                "",                  // board
                null,                // isSt (接口未返回)
                null,                // isNewStock
                null,                // listDate
                "",                  // suspendStatus
                poolType,            // poolType: LIMIT_UP/LIMIT_DOWN/STRONG/etc
                "1"                  // source: 1=东财
        );
    }

    /**
     * 涨跌停状态映射 (从涨停池数据中提取)
     */
    public StockLimitStatusDailyRow toLimitStatusDaily(LocalDate tradeDate, JsonNode node) {
        int lbc = node.path("lbc").asInt(0);  // 连板数
        int zbc = node.path("zbc").asInt(0);  // 炸板次数
        String fbt = timeText(node, "fbt");    // 首次封板时间
        String lbt = timeText(node, "lbt");    // 最后封板时间
        return new StockLimitStatusDailyRow(
                tradeDate,
                text(node, "c"),                  // stockCode
                text(node, "n"),                  // stockName
                "LIMIT_UP",                       // limitStatus
                lbc > 1 ? "RELAY" : "FIRST",      // limitUpType
                lbc,                              // consecutiveLimitUpDays
                parseDateTime(tradeDate, fbt),    // firstLimitTime
                parseDateTime(tradeDate, lbt),    // lastLimitTime
                zbc,                              // openLimitTimes
                decimal(node, "fund"),            // sealAmount
                null,                             // sealVolume
                "",                               // brokenLimitReason
                "1"                               // source: 1=东财
        );
    }

    /**
     * 指数日K映射
     * 东财字段: f12=indexCode, f14=indexName, f17=open, f15=high, f16=low, f2=close
     * f18=preClose, f3=changePct, f5=volume, f6=amount
     * indexType 由调用方根据 f13(市场) 判定, 此处默认传空
     */
    public IndexDailyKlineRow toIndexDailyKline(LocalDate tradeDate, JsonNode node) {
        return new IndexDailyKlineRow(
                tradeDate,
                text(node, "f12"),     // indexCode
                text(node, "f14"),     // indexName
                "",                    // indexType (接口无此字段, 默认空)
                decimal(node, "f17"),  // open
                decimal(node, "f15"),  // high
                decimal(node, "f16"),  // low
                decimal(node, "f2"),   // close
                decimal(node, "f18"),  // preClose
                decimal(node, "f3"),   // changePct
                longValue(node, "f5"), // volume
                decimal(node, "f6"),   // amount
                "1"                    // source: 1=东财
        );
    }

    /**
     * 资金流向日快照映射 (个股维度)
     * 东财推送资金流向字段 (push2.eastmoney.com):
     *   f12=stockCode, f14=stockName
     *   f62=主力净流入-净额(mainNetInflow)
     *   f66=超大单净流入-净额(superLargeNetInflow)
     *   f72=大单净流入-净额(largeNetInflow)
     *   f78=中单净流入-净额(mediumNetInflow)
     *   f84=小单净流入-净额(smallNetInflow)
     * 注意: 资金流向 API 的单位为元, 接口直接返回元
     */
    public CapitalFlowDailySnapshotRow toCapitalFlowDailySnapshot(LocalDate tradeDate, JsonNode node) {
        return new CapitalFlowDailySnapshotRow(
                tradeDate,
                "STOCK",                       // targetType: 个股维度
                text(node, "f12"),             // targetCode (stockCode)
                text(node, "f14"),             // targetName (stockName)
                decimal(node, "f62"),          // mainNetInflow
                decimal(node, "f66"),          // superLargeNetInflow
                decimal(node, "f72"),          // largeNetInflow
                decimal(node, "f78"),          // mediumNetInflow
                decimal(node, "f84"),          // smallNetInflow
                "1"                            // source: 1=东财
        );
    }

    /**
     * 涨停盘中事件映射 (从涨停池数据中提取首封事件)
     * 涨停池字段: c=stockCode, n=stockName, p=price(分), fbt=首次封板时间(HHmmss),
     * lbt=最后封板时间, zbc=炸板次数, fund=封单资金
     * <p>
     * 事件类型: 首封 (FIRST_SEAL) - 从 fbt 提取首次封板时间
     * 后续开板(BREAK)/回封(RE_SEAL)事件需要 pool 明细时间序列, 涨停池接口仅返回聚合数据,
     * 此处仅提取首封事件. 开板/回封事件需接入专门的 tick 数据源.
     */
    public StockLimitIntradayEventRow toLimitIntradayEventRow(LocalDate tradeDate, JsonNode node) {
        return new StockLimitIntradayEventRow(
                tradeDate,
                parseDateTime(tradeDate, timeText(node, "fbt")),  // eventTime: 首次封板时间
                text(node, "c"),                                  // stockCode
                text(node, "n"),                                  // stockName
                "FIRST_SEAL",                                     // eventType: 首封
                convertFenToYuan(node, "p"),                      // price: 分→元
                decimal(node, "fund"),                            // sealAmount: 封单资金
                1,                                                // eventSequence: 首封固定为1
                "1"                                               // source: 1=东财
        );
    }

    // ========== 工具方法 ==========

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || "-".equals(value.asText())) {
            return "";
        }
        return value.asText();
    }

    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || "-".equals(value.asText())) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private Long longValue(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || "-".equals(value.asText())) {
            return 0L;
        }
        return value.asLong(0L);
    }

    /**
     * 解析HHmmss格式时间为 HH:MM:SS
     */
    private String timeText(JsonNode node, String field) {
        String value = text(node, field);
        if (value.isBlank() || "0".equals(value)) {
            return "";
        }
        String padded = "000000" + value;
        String time = padded.substring(padded.length() - 6);
        return time.substring(0, 2) + ":" + time.substring(2, 4) + ":" + time.substring(4, 6);
    }

    private LocalDateTime parseDateTime(LocalDate tradeDate, String timeStr) {
        if (timeStr == null || timeStr.isBlank()) {
            return null;
        }
        try {
            String[] parts = timeStr.split(":");
            if (parts.length == 3) {
                return tradeDate.atTime(
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 将分为单位的金额转换为元 (除以100, 保留2位小数)
     */
    private BigDecimal convertFenToYuan(JsonNode node, String field) {
        BigDecimal fen = decimal(node, field);
        if (fen.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return fen.divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }
}
