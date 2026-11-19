package com.disasterrelief.frontend.views;

import com.disasterrelief.frontend.api.Api;
import com.disasterrelief.frontend.api.BackendException;
import com.disasterrelief.frontend.components.AppView;
import com.disasterrelief.frontend.components.Feedback;
import com.disasterrelief.frontend.components.Formats;
import com.disasterrelief.frontend.components.J;
import com.disasterrelief.frontend.components.MapGrid;
import com.disasterrelief.frontend.components.Pick;
import com.disasterrelief.frontend.components.Ui;
import com.disasterrelief.frontend.layouts.MainLayout;
import com.disasterrelief.frontend.security.Roles;
import com.disasterrelief.frontend.session.UserSession;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Analytics for officers: request counts, fulfilment, shortages, and the situation report as a PDF. */
@Route(value = "analytics", layout = MainLayout.class)
@PageTitle("DRRC - Analytics")
public class AnalyticsView extends AppView {

    private final Div body = new Div();
    private ComboBox<Map<String, Object>> disaster;

    public AnalyticsView(UserSession session, Api api) {
        super(session, api, "Analytics and reports", "How the response is going, and where stock is short.");
        body.addClassName("stack");
        add(body);
    }

    @Override
    protected boolean allowed(String role) {
        return Roles.in(role, Roles.DECIDERS);
    }

    @Override
    protected void enter(BeforeEnterEvent event) {
        if (disaster == null) {
            try {
                disaster = Ui.combo("Disaster (all if empty)", api.page(token(), "/api/disasters", 0, 100).content(),
                        d -> J.str(d, "name"));
            } catch (BackendException ex) {
                Feedback.handle(ex, session);
                return;
            }
            disaster.addValueChangeListener(e -> load());
        }
        load();
    }

    private void load() {
        body.removeAll();
        Long scope = Pick.id(disaster);
        Map<String, Object> summary;
        List<Map<String, Object>> shortages;
        try {
            summary = api.get(token(), "/api/analytics/summary", "disasterId", scope);
            shortages = api.list(token(), "/api/analytics/shortages", "disasterId", scope);
        } catch (BackendException ex) {
            Feedback.handle(ex, session);
            return;
        }
        HorizontalLayout tools = Ui.row(disaster, Ui.iconButton(VaadinIcon.DOWNLOAD, "Prepare situation report (PDF)", () -> pdf(scope)));

        Object hours = summary.get("averageHoursToDeliver");
        Div kpis = new Div(
                Ui.kpi(VaadinIcon.CLIPBOARD_TEXT, "Total requests", Formats.number(J.lng(summary, "totalRequests")),
                        "Waiting for verification: " + J.lng(summary, "awaitingVerification"), "blue"),
                Ui.kpi(VaadinIcon.WARNING, "Open critical", Formats.number(J.lng(summary, "openCritical")), "Not yet delivered", "red"),
                Ui.kpi(VaadinIcon.CHECK, "Fulfilment", String.format(Locale.ENGLISH, "%.1f%%", J.dbl(summary, "fulfilmentPercent")),
                        Formats.number(J.lng(summary, "deliveredQuantity")) + " of " + Formats.number(J.lng(summary, "requestedQuantity")) + " units", "green"),
                Ui.kpi(VaadinIcon.CLOCK, "Average time to deliver",
                        hours == null ? "-" : String.format(Locale.ENGLISH, "%.1f h", J.dbl(summary, "averageHoursToDeliver")),
                        "From request to delivery", "amber"));
        kpis.addClassName("kpis");

        Div charts = new Div(Ui.card("Requests by status", bars(J.map(summary, "byStatus"))),
                Ui.card("Requests by severity", bars(J.map(summary, "bySeverity"))));
        charts.addClassNames("cols-2e");

        MapGrid gaps = new MapGrid(session)
                .col("Resource", r -> J.str(r, "resource") + " (" + J.str(r, "unit") + ")")
                .col("Open requests", r -> Formats.number(J.lng(r, "openRequests")))
                .col("Demand", r -> Formats.number(J.lng(r, "demand")))
                .col("Available", r -> Formats.number(J.lng(r, "available")))
                .col("Shortage", r -> Formats.number(J.lng(r, "shortage")))
                .col("Coverage", r -> String.format(Locale.ENGLISH, "%.1f%%", J.dbl(r, "coveragePercent")))
                .col("Offered by partners", r -> Formats.number(J.lng(r, "offeredByPartners")))
                .rows(shortages);
        body.add(tools, kpis, charts, Ui.card("Shortages (verified and approved requests against free stock)", gaps));
    }

    private Div bars(Map<String, Object> counts) {
        Div box = new Div();
        long max = 1;
        for (Object value : counts.values()) {
            if (value instanceof Number n) {
                max = Math.max(max, n.longValue());
            }
        }
        if (counts.isEmpty()) {
            box.add(new Span("No data."));
        }
        for (Map.Entry<String, Object> entry : counts.entrySet()) {
            long v = entry.getValue() instanceof Number m ? m.longValue() : 0L;
            Div fill = new Div();
            fill.addClassName("bar-fill");
            fill.getStyle().set("width", (v * 100 / max) + "%");
            Div track = new Div(fill);
            track.addClassName("bar-track");
            Div row = new Div(new Span(Formats.label(entry.getKey())), track, new Span(String.valueOf(v)));
            row.addClassName("bar-row");
            box.add(row);
        }
        return box;
    }

    private void pdf(Long scope) {
        try {
            byte[] data = api.bytes(token(), "/api/reports/situation.pdf", "disasterId", scope);
            Anchor link = new Anchor(new StreamResource("situation-report.pdf", () -> new ByteArrayInputStream(data)),
                    "Download situation-report.pdf");
            link.getElement().setAttribute("download", true);
            Ui.info("Situation report ready", new Div(link));
        } catch (BackendException ex) {
            Feedback.handle(ex, session);
        }
    }
}
