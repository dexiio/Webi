package com.vonhof.webi.db.dto;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class ResultSetDTO<T> {
    private List<T> rows = new ArrayList<T>();
    private long totalRows;
    private long offset;

    public ResultSetDTO(List<T> rows) {
        this.rows = rows;
        totalRows = rows.size();
        offset = 0;
    }

    public ResultSetDTO() {
    }
    

    public long getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public long getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(long totalRows) {
        this.totalRows = totalRows;
    }

    public List<T> getRows() {
        return rows;
    }

    public void setRows(List<T> rows) {
        this.rows = rows;
    }
}
