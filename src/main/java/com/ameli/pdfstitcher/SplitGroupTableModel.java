package com.ameli.pdfstitcher;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

final class SplitGroupTableModel extends AbstractTableModel {
    private static final String[] COLUMN_NAMES = {"Output File", "Pages", "Count", "Status"};

    private final List<SplitGroup> groups = new ArrayList<>();
    private boolean derivedUpdateInProgress;

    @Override
    public int getRowCount() {
        return groups.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return columnIndex == 2 ? Integer.class : String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == 0 || columnIndex == 1;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        SplitGroup group = groups.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> group.getOutputName();
            case 1 -> group.getPageSpec();
            case 2 -> group.getResolvedPageCount();
            case 3 -> group.hasValidationIssue() ? group.getValidationMessage() : "Ready";
            default -> "";
        };
    }

    @Override
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
        SplitGroup group = groups.get(rowIndex);
        if (columnIndex == 0) {
            group.setOutputName(value == null ? "" : value.toString());
        } else if (columnIndex == 1) {
            group.setPageSpec(value == null ? "" : value.toString());
        }
        fireTableRowsUpdated(rowIndex, rowIndex);
    }

    List<SplitGroup> getGroups() {
        return groups;
    }

    SplitGroup getGroupAt(int rowIndex) {
        return groups.get(rowIndex);
    }

    void addGroup(SplitGroup group) {
        int rowIndex = groups.size();
        groups.add(group);
        fireTableRowsInserted(rowIndex, rowIndex);
    }

    void removeRows(int[] rows) {
        for (int index = rows.length - 1; index >= 0; index--) {
            groups.remove(rows[index]);
        }
        fireTableDataChanged();
    }

    void refreshDerivedColumns() {
        if (getRowCount() == 0) {
            return;
        }
        derivedUpdateInProgress = true;
        try {
            fireTableRowsUpdated(0, getRowCount() - 1);
        } finally {
            derivedUpdateInProgress = false;
        }
    }

    boolean isDerivedUpdateInProgress() {
        return derivedUpdateInProgress;
    }
}
