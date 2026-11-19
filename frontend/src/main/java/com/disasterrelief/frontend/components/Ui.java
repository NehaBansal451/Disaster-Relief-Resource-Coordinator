package com.disasterrelief.frontend.components;

import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

/** Small building blocks that give every screen the same look (styles live in drrc.css). */
public final class Ui {

    private Ui() {
    }

    /** White rounded panel with a title. */
    public static Div card(String title, Component... body) {
        Div card = new Div();
        card.addClassName("card");
        if (title != null) {
            H3 heading = new H3(title);
            heading.addClassName("card-title");
            card.add(heading);
        }
        card.add(body);
        return card;
    }

    /** Big-number tile. tone: red, amber, green, blue. */
    public static Div kpi(VaadinIcon icon, String label, String value, String note, String tone) {
        Div tile = new Div();
        tile.addClassNames("kpi", "tone-" + tone);
        Div ico = new Div(icon.create());
        ico.addClassName("kpi-icon");
        Span l = new Span(label);
        l.addClassName("kpi-label");
        Span v = new Span(value);
        v.addClassName("kpi-value");
        Div text = new Div(l, v);
        text.addClassName("kpi-text");
        if (note != null && !note.isBlank()) {
            Span n = new Span(note);
            n.addClassName("kpi-note");
            text.add(n);
        }
        tile.add(ico, text);
        return tile;
    }

    public static Span badge(String text, String tone) {
        Span badge = new Span(text);
        badge.addClassNames("badge", "tone-" + tone);
        return badge;
    }

    /** Badge for a status word such as PENDING or IN_TRANSIT; colour chosen by {@link #tone(String)}. */
    public static Span status(String value) {
        return badge(Formats.label(value), tone(value));
    }

    public static String tone(String value) {
        String s = value == null ? "" : value.toUpperCase();
        return switch (s) {
            case "CRITICAL", "HIGH", "REJECTED", "CANCELLED", "DECLINED", "FAILED", "STALE", "EXPIRED", "OVERCROWDED",
                 "CLOSED", "UNAVAILABLE", "OFF_DUTY" -> "red";
            case "PENDING", "MEDIUM", "NEW", "OFFERED", "EXPIRING_SOON", "EXPIRING", "ASSIGNED", "BUSY", "VERIFIED",
                 "PROPOSED", "RECOMMENDED", "UNREVIEWED", "DISPATCHED", "INACTIVE" -> "amber";
            case "IN_TRANSIT", "IN_PROGRESS", "ACCEPTED", "APPROVED", "ALLOCATED", "ACTIVE", "MONITORING" -> "blue";
            case "DELIVERED", "AVAILABLE", "RECEIVED", "COMPLETED", "READ", "OK", "LOW", "OPEN", "REVIEWED" -> "green";
            default -> "grey";
        };
    }

    public static Button primary(String text, Runnable action) {
        Button button = new Button(text, e -> action.run());
        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        return button;
    }

    public static Button small(String text, Runnable action) {
        Button button = new Button(text, e -> action.run());
        button.addThemeVariants(ButtonVariant.LUMO_SMALL);
        return button;
    }

    public static Button iconButton(VaadinIcon icon, String text, Runnable action) {
        Button button = new Button(text, icon.create(), e -> action.run());
        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        return button;
    }

    public static HorizontalLayout row(Component... parts) {
        HorizontalLayout row = new HorizontalLayout(parts);
        row.setDefaultVerticalComponentAlignment(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.END);
        row.setWrap(true);
        return row;
    }

    /** Dialog with Cancel and a save button. onSave returns true when it worked, and the dialog then closes. */
    public static Dialog dialog(String title, Component content, String saveLabel, BooleanSupplier onSave) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(title);
        dialog.add(content);
        Button cancel = new Button("Cancel", e -> dialog.close());
        Button save = new Button(saveLabel, e -> {
            if (onSave.getAsBoolean()) {
                dialog.close();
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(cancel, save);
        dialog.open();
        return dialog;
    }

    /** Read-only dialog with a Close button. */
    public static Dialog info(String title, Component content) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(title);
        dialog.add(content);
        dialog.getFooter().add(new Button("Close", e -> dialog.close()));
        dialog.open();
        return dialog;
    }

    /** "label: value" line. */
    public static Div line(String label, String value) {
        Span l = new Span(label);
        l.addClassName("line-label");
        Span v = new Span(value == null || value.isBlank() ? "-" : value);
        v.addClassName("line-value");
        Div line = new Div(l, v);
        line.addClassName("line");
        return line;
    }

    /** Pick-list of backend rows, shown by the text function. */
    public static ComboBox<Map<String, Object>> combo(String label, List<Map<String, Object>> items,
                                                      Function<Map<String, Object>, String> text) {
        ComboBox<Map<String, Object>> box = new ComboBox<>(label);
        box.setItems(items);
        box.setItemLabelGenerator(text::apply);
        box.setClearButtonVisible(true);
        return box;
    }

    /** Allocated, Dispatched, In transit, Delivered: steps up to the reached one are ticked. */
    public static Div stepper(String status) {
        String[] names = {"Allocated", "Dispatched", "In transit", "Delivered"};
        int reached = switch (status == null ? "" : status) {
            case "DISPATCHED" -> 1;
            case "IN_TRANSIT" -> 2;
            case "DELIVERED" -> 3;
            default -> 0;
        };
        Div box = new Div();
        box.addClassName("stepper");
        for (int i = 0; i < names.length; i++) {
            if (i > 0) {
                Div bar = new Div();
                bar.addClassName("step-bar");
                if (i <= reached) {
                    bar.addClassName("done");
                }
                box.add(bar);
            }
            Div step = new Div();
            step.addClassName("step");
            if (i <= reached) {
                step.addClassName("done");
            }
            Div circle = new Div(new Span(i <= reached ? "\u2713" : String.valueOf(i + 1)));
            circle.addClassName("circle");
            step.add(circle, new Span(names[i]));
            box.add(step);
        }
        return box;
    }
}
