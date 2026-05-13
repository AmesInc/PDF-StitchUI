package com.ameli.pdfstitcher;

import org.junit.jupiter.api.Test;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SplitGroupAndTableModelTest {
    @Test
    void splitGroupNormalizesValues() {
        SplitGroup group = new SplitGroup("  File 1  ", " 1-2 ");

        assertEquals("File 1", group.getOutputName());
        assertEquals("1-2", group.getPageSpec());

        group.setResolvedPages(List.of(0, 1));
        group.setValidationMessage("  Issue ");

        assertEquals(2, group.getResolvedPageCount());
        assertTrue(group.hasValidationIssue());
        assertEquals("Issue", group.getValidationMessage());

        group.setValidationMessage("   ");
        assertFalse(group.hasValidationIssue());
    }

    @Test
    void tableModelSupportsEditingAndRemoval() {
        SplitGroupTableModel model = new SplitGroupTableModel();
        model.addGroup(new SplitGroup("File 1", "1"));
        model.addGroup(new SplitGroup("File 2", "2"));

        assertEquals(2, model.getRowCount());
        assertEquals("Output File", model.getColumnName(0));
        assertEquals(Integer.class, model.getColumnClass(2));
        assertTrue(model.isCellEditable(0, 0));
        assertTrue(model.isCellEditable(0, 1));
        assertFalse(model.isCellEditable(0, 2));

        model.setValueAt("Renamed", 0, 0);
        model.setValueAt("1-3", 0, 1);

        assertEquals("Renamed", model.getGroupAt(0).getOutputName());
        assertEquals("1-3", model.getGroupAt(0).getPageSpec());
        assertEquals("", model.getValueAt(0, 99));

        model.removeRows(new int[]{0});
        assertEquals(1, model.getRowCount());
        assertEquals("File 2", model.getGroupAt(0).getOutputName());
    }

    @Test
    void refreshDerivedColumnsRaisesAFlagDuringNotification() {
        SplitGroupTableModel model = new SplitGroupTableModel();
        model.addGroup(new SplitGroup("File 1", "1"));

        AtomicBoolean sawDerivedUpdate = new AtomicBoolean(false);
        model.addTableModelListener(new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent event) {
                sawDerivedUpdate.set(model.isDerivedUpdateInProgress());
            }
        });

        model.refreshDerivedColumns();

        assertTrue(sawDerivedUpdate.get());
        assertFalse(model.isDerivedUpdateInProgress());
    }
}
