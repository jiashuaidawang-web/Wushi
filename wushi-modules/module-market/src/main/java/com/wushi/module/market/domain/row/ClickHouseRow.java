package com.wushi.module.market.domain.row;

import com.wushi.module.market.enums.FactTable;

import java.util.List;

public interface ClickHouseRow {

    FactTable table();

    List<String> columns();

    Object[] values();
}
