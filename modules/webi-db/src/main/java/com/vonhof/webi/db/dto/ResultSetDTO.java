package com.vonhof.webi.db.dto;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class ResultSetDTO<T> {
    private List<T> rows = new ArrayList<T>();
    private int totalRows;
    private int offset;

    public ResultSetDTO(List<T> rows) {
        this.rows = rows;
        totalRows = rows.size();
        offset = 0;
    }

    public ResultSetDTO() {
    }
    

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public List<T> getRows() {
        return rows;
    }

    public void setRows(List<T> rows) {
        this.rows = rows;
    }
}
