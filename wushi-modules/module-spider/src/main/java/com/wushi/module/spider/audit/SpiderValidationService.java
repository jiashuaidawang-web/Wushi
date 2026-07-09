package com.wushi.module.spider.audit;

import java.time.LocalDate;
import java.util.List;

/**
 * 数据校验 Service
 * 抓取后校验当天各表数据量是否 > 0, 不通过的表写入 audit log
 */
public interface SpiderValidationService {

    /**
     * 校验指定交易日各核心表数据量
     * @return 不通过的表名列表 (全部通过则返回空列表)
     */
    List<String> validate(LocalDate tradeDate);
}
