package com.disasterrelief.frontend.components;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.select.Select;
import java.util.Map;

/** Small helpers for drop-downs used by filters and forms. */
public final class Pick {

    private Pick() {
    }

    /** Drop-down of fixed words. Choosing nothing gives null, and the empty entry shows the given caption. */
    public static Select<String> choice(String label, String caption, String... values) {
        Select<String> select = new Select<>();
        select.setLabel(label);
        select.setItems(values);
        select.setEmptySelectionAllowed(true);
        select.setEmptySelectionCaption(caption);
        select.setItemLabelGenerator(Formats::label);
        return select;
    }

    /** Chosen word, or null when nothing is chosen. */
    public static String value(Select<String> select) {
        return select == null ? null : select.getValue();
    }

    /** Id of the chosen backend row, or null when nothing is chosen. */
    public static Long id(ComboBox<Map<String, Object>> box) {
        if (box == null || box.getValue() == null) {
            return null;
        }
        return J.lng(box.getValue(), "id");
    }
}
