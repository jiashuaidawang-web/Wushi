package com.wushi.module.spider.eastmoney;

import lombok.Getter;

@Getter
public enum EastMoneyEndpoint {

    ALL_STOCK("stock_daily_kline",
            "http://83.push2.eastmoney.com/api/qt/clist/get?pn=%d&pz=100&po=1&np=1&ut=bd1d9ddb04089700cf9c27f6f7426281&fltt=2&invt=2&fid=f3&fs=m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23&fields=f1,f2,f3,f4,f5,f6,f7,f8,f9,f10,f12,f13,f14,f15,f16,f17,f18,f20,f21,f23,f24,f25,f22,f11,f62,f128,f136,f115,f152,f173&_=%d"),

    REGION_PLATE("stock_plate_dimension",
            "http://81.push2.eastmoney.com/api/qt/clist/get?pn=%d&pz=100&po=1&np=1&ut=bd1d9ddb04089700cf9c27f6f7426281&fltt=2&invt=2&fid=f3&fs=m:90+t:1+f:!50&fields=f1,f2,f3,f4,f5,f6,f7,f8,f9,f10,f12,f13,f14,f15,f16,f17,f18,f20,f21,f23,f24,f25,f26,f22,f33,f11,f62,f128,f136,f115,f152,f124,f107,f104,f105,f140,f141,f207,f208,f209,f222&_=%d"),

    INDUSTRY_PLATE("stock_plate_dimension",
            "http://81.push2.eastmoney.com/api/qt/clist/get?pn=%d&pz=100&po=1&np=1&ut=bd1d9ddb04089700cf9c27f6f7426281&fltt=2&invt=2&fid=f3&fs=m:90+t:2+f:!50&fields=f1,f2,f3,f4,f5,f6,f7,f8,f9,f10,f12,f13,f14,f15,f16,f17,f18,f20,f21,f23,f24,f25,f26,f22,f33,f11,f62,f128,f136,f115,f152,f124,f107,f104,f105,f140,f141,f207,f208,f209,f222&_=%d"),

    CONCEPT_PLATE("stock_plate_dimension",
            "http://81.push2.eastmoney.com/api/qt/clist/get?pn=%d&pz=100&po=1&np=1&ut=bd1d9ddb04089700cf9c27f6f7426281&fltt=2&invt=2&fid=f3&fs=m:90+t:3+f:!50&fields=f1,f2,f3,f4,f5,f6,f7,f8,f9,f10,f12,f13,f14,f15,f16,f17,f18,f20,f21,f23,f24,f25,f26,f22,f33,f11,f62,f128,f136,f115,f152,f124,f107,f104,f105,f140,f141,f207,f208,f209,f222&_=%d"),

    INDEX_KLINE("index_daily_kline",
            "http://83.push2.eastmoney.com/api/qt/clist/get?pn=%d&pz=100&po=1&np=1&ut=bd1d9ddb04089700cf9c27f6f7426281&fltt=2&invt=2&fid=f3&fs=m:1+s:2,m:0+t:5&fields=f2,f3,f5,f6,f12,f14,f15,f16,f17,f18&_=%d"),

    CAPITAL_FLOW("capital_flow_daily_snapshot",
            "http://push2.eastmoney.com/api/qt/clist/get?pn=%d&pz=100&po=1&np=1&ut=bd1d9ddb04089700cf9c27f6f7426281&fltt=2&invt=2&fid=f62&fs=m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23&fields=f12,f14,f62,f66,f72,f78,f84&_=%d"),

    LIMIT_UP_POOL("stock_pool_daily_snapshot",
            "https://push2ex.eastmoney.com/getTopicZTPool?ut=7eea3edcaed734bea9cbfc24409ed989&dpt=wz.ztzt&Pageindex=0&pagesize=20000&sort=fbt%%3Aasc&date=%s&_=%d"),

    YEST_LIMIT_UP_POOL("stock_pool_daily_snapshot",
            "https://push2ex.eastmoney.com/getYesterdayZTPool?ut=7eea3edcaed734bea9cbfc24409ed989&dpt=wz.ztzt&Pageindex=0&pagesize=20000&sort=zs%%3Adesc&date=%s&_=%d"),

    STRONG_POOL("stock_pool_daily_snapshot",
            "https://push2ex.eastmoney.com/getTopicQSPool?ut=7eea3edcaed734bea9cbfc24409ed989&dpt=wz.ztzt&Pageindex=0&pagesize=20000&sort=zdp%%3Adesc&date=%s&_=%d"),

    SUB_NEW_POOL("stock_pool_daily_snapshot",
            "https://push2ex.eastmoney.com/getTopicCXPooll?ut=7eea3edcaed734bea9cbfc24409ed989&dpt=wz.ztzt&Pageindex=0&pagesize=20000&sort=ods%%3Aasc&date=%s&_=%d"),

    BROKEN_POOL("stock_pool_daily_snapshot",
            "https://push2ex.eastmoney.com/getTopicZBPool?ut=7eea3edcaed734bea9cbfc24409ed989&dpt=wz.ztzt&Pageindex=0&pagesize=20000&sort=fbt%%3Aasc&date=%s&_=%d"),

    LIMIT_DOWN_POOL("stock_pool_daily_snapshot",
            "https://push2ex.eastmoney.com/getTopicDTPool?ut=7eea3edcaed734bea9cbfc24409ed989&dpt=wz.ztzt&Pageindex=0&pagesize=20000&sort=fund%%3Aasc&date=%s&_=%d");

    private final String targetTable;
    private final String urlTemplate;

    EastMoneyEndpoint(String targetTable, String urlTemplate) {
        this.targetTable = targetTable;
        this.urlTemplate = urlTemplate;
    }

    public boolean isPoolEndpoint() {
        return this.targetTable.equals("stock_pool_daily_snapshot");
    }

    public boolean isPlateEndpoint() {
        return this == REGION_PLATE || this == INDUSTRY_PLATE || this == CONCEPT_PLATE;
    }
}
