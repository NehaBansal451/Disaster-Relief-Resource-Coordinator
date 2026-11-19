package com.disasterrelief.frontend.components;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import com.disasterrelief.frontend.api.Api;
import com.disasterrelief.frontend.api.BackendException;
import com.disasterrelief.frontend.session.UserSession;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/** A grid of backend rows with a pager. Columns are declared by field name; paging calls the loader. */
public class MapGrid extends VerticalLayout {

    /** Loads one page (0-based) from the backend. */
    public interface Loader {
        Api.Page load(int page);
    }

    private final Grid<Map<String, Object>> grid = new Grid<>();
    private final UserSession session;
    private final Button previous = new Button("Previous");
    private final Button next = new Button("Next");
    private final Span pageLabel = new Span();
    private final HorizontalLayout pager = new HorizontalLayout(previous, pageLabel, next);
    private final Span empty = new Span("Nothing to show.");
    private Loader loader;
    private int page;
    private int totalPages;

    public MapGrid(UserSession session) {
        this.session = session;
        setPadding(false);
        setSpacing(false);
        addClassName("map-grid");
        pager.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        previous.addClickListener(e -> reload(page - 1));
        next.addClickListener(e -> reload(page + 1));
        empty.addClassName("empty");
        empty.setVisible(false);
        grid.setAllRowsVisible(true);
        add(grid, empty, pager);
    }

    public Grid<Map<String, Object>> grid() {
        return grid;
    }

    public MapGrid text(String header, String key) {
        grid.addColumn(row -> J.str(row, key)).setHeader(header).setAutoWidth(true);
        return this;
    }

    public MapGrid col(String header, Function<Map<String, Object>, String> value) {
        grid.addColumn(value::apply).setHeader(header).setAutoWidth(true);
        return this;
    }

    public MapGrid when(String header, String key) {
        grid.addColumn(row -> Formats.dateTime(J.str(row, key))).setHeader(header).setAutoWidth(true);
        return this;
    }

    public MapGrid badge(String header, String key) {
        grid.addComponentColumn(row -> Ui.status(J.str(row, key))).setHeader(header).setAutoWidth(true);
        return this;
    }

    public MapGrid component(String header, Function<Map<String, Object>, Component> value) {
        grid.addComponentColumn(value::apply).setHeader(header).setAutoWidth(true);
        return this;
    }

    public MapGrid onRowClick(Consumer<Map<String, Object>> action) {
        grid.addItemClickListener(e -> action.accept(e.getItem()));
        return this;
    }

    public MapGrid loader(Loader loader) {
        this.loader = loader;
        return this;
    }

    /** Show a fixed list (dashboard tables): no pager. */
    public MapGrid rows(List<Map<String, Object>> rows) {
        grid.setItems(rows);
        pager.setVisible(false);
        empty.setVisible(rows.isEmpty());
        return this;
    }

    public void reload() {
        reload(page);
    }

    public void reload(int target) {
        if (loader == null) {
            return;
        }
        try {
            Api.Page result = loader.load(Math.max(target, 0));
            page = result.page();
            totalPages = result.totalPages();
            grid.setItems(result.content());
            empty.setVisible(result.content().isEmpty());
            pageLabel.setText("Page " + (totalPages == 0 ? 0 : page + 1) + " of " + totalPages + "  (" + result.total() + " total)");
            previous.setEnabled(page > 0);
            next.setEnabled(page + 1 < totalPages);
            pager.setVisible(totalPages > 1);
        } catch (BackendException ex) {
            grid.setItems(List.of());
            empty.setText(Feedback.message(ex));
            empty.setVisible(true);
            Feedback.handle(ex, session);
        }
    }
}
