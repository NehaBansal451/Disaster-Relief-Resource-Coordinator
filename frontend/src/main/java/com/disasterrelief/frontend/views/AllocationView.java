package com.disasterrelief.frontend.views;

import com.disasterrelief.frontend.api.Api;
import com.disasterrelief.frontend.api.BackendException;
import com.disasterrelief.frontend.components.AppView;
import com.disasterrelief.frontend.components.Feedback;
import com.disasterrelief.frontend.components.Formats;
import com.disasterrelief.frontend.components.J;
import com.disasterrelief.frontend.components.MapGrid;
import com.disasterrelief.frontend.components.Ui;
import com.disasterrelief.frontend.layouts.MainLayout;
import com.disasterrelief.frontend.security.Roles;
import com.disasterrelief.frontend.session.UserSession;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resource allocation. An officer asks for a recommendation for an approved request, reads why each warehouse was
 * chosen, may change quantities, then approves or rejects. Stock is reserved only on approval.
 */
@Route(value = "allocation", layout = MainLayout.class)
@PageTitle("DRRC - Allocation")
public class AllocationView extends AppView {

    private final IntegerField requestId = new IntegerField("Request number");
    private final Div results = new Div();
    private final MapGrid waiting;
    private final Button recommend = Ui.primary("Recommend allocation", this::recommend);
    private boolean built;

    public AllocationView(UserSession session, Api api) {
        super(session, api, "Resource allocation",
                "Approved requests waiting for stock. Only an officer can recommend, approve or reject.");
        requestId.setMin(1);
        waiting = new MapGrid(session)
                .col("Request", r -> "REQ-" + J.lng(r, "id"))
                .text("Area", "affectedAreaName")
                .col("Need", r -> J.str(r, "resourceName") + " x " + Formats.number(J.lng(r, "quantity")))
                .badge("Severity", "severity")
                .badge("Status", "status")
                .onRowClick(r -> {
                    requestId.setValue((int) J.lng(r, "id"));
                    show();
                })
                .loader(page -> api.page(token(), "/api/requests", page, 10, "status", "APPROVED"));
        results.addClassName("stack");
        HorizontalLayout pick = Ui.row(requestId, Ui.small("Show allocations", this::show), recommend);
        add(Ui.card("Approved requests (click one)", waiting), Ui.card("Choose a request", pick), results);
    }

    @Override
    protected boolean allowed(String role) {
        return Roles.in(role, Roles.LOGISTICS);
    }

    @Override
    protected void enter(BeforeEnterEvent event) {
        if (!built) {
            recommend.setVisible("OFFICER".equals(role()));
            built = true;
        }
        waiting.reload(0);
    }

    private void show() {
        results.removeAll();
        Integer id = requestId.getValue();
        if (id == null) {
            Feedback.error("Type a request number, or click a request above.");
            return;
        }
        try {
            List<Map<String, Object>> all = api.list(token(), "/api/allocations", "requestId", id);
            if (all.isEmpty()) {
                results.add(new Paragraph("No allocation yet for REQ-" + id + "."));
            }
            for (Map<String, Object> a : all) {
                results.add(card(a));
            }
        } catch (BackendException ex) {
            Feedback.handle(ex, session);
        }
    }

    private void recommend() {
        Integer id = requestId.getValue();
        if (id == null) {
            Feedback.error("Type a request number, or click a request above.");
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", id.longValue());
        if (run(() -> api.post(token(), "/api/allocations/recommendations", payload), "Recommendation created")) {
            show();
        }
    }

    private Div card(Map<String, Object> a) {
        String status = J.str(a, "status");
        Div head = new Div(Ui.status(status), new Span("  Allocation #" + J.lng(a, "id") + " for REQ-" + J.lng(a, "requestId")));
        Div numbers = new Div(
                Ui.line("Requested", Formats.number(J.lng(a, "requestedQty"))),
                Ui.line("Allocated", Formats.number(J.lng(a, "allocatedQty"))),
                Ui.line("Unfilled", Formats.number(J.lng(a, "unfilledQty"))));
        Paragraph why = new Paragraph(J.str(a, "explanation"));
        MapGrid items = new MapGrid(session)
                .text("Warehouse", "warehouseName")
                .col("Quantity", x -> Formats.number(J.lng(x, "quantity")))
                .col("Distance", x -> x.get("distanceKm") == null ? "-" : String.format(Locale.ENGLISH, "%.1f km", J.dbl(x, "distanceKm")))
                .text("Why chosen", "reason")
                .rows(J.list(a, "items"));
        Div card = Ui.card(null, head, numbers, why, items);
        List<Map<String, Object>> skipped = J.list(a, "notSelected");
        if (!skipped.isEmpty()) {
            card.add(new MapGrid(session).text("Warehouse not used", "warehouseName").text("Reason", "reason").rows(skipped));
        }
        if ("RECOMMENDED".equals(status) && "OFFICER".equals(role())) {
            card.add(Ui.row(Ui.primary("Approve", () -> approve(a)), Ui.small("Reject", () -> reject(a))));
        }
        return card;
    }

    private void approve(Map<String, Object> a) {
        long id = J.lng(a, "id");
        List<Map<String, Object>> items = J.list(a, "items");
        List<IntegerField> fields = new ArrayList<>();
        Div content = new Div();
        for (Map<String, Object> item : items) {
            IntegerField field = new IntegerField(J.str(item, "warehouseName") + " - quantity");
            field.setMin(1);
            field.setValue((int) J.lng(item, "quantity"));
            fields.add(field);
            content.add(field);
        }
        TextField note = new TextField("Note (optional)");
        note.setWidthFull();
        content.add(note);
        Ui.dialog("Approve allocation #" + id, content, "Approve", () -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("version", (int) J.lng(a, "version"));
            if (!note.getValue().isBlank()) {
                payload.put("note", note.getValue().trim());
            }
            boolean changed = false;
            List<Object> edits = new ArrayList<>();
            for (int k = 0; k < items.size(); k++) {
                Integer value = fields.get(k).getValue();
                if (value == null || value < 1) {
                    Feedback.error("Every quantity must be at least 1.");
                    return false;
                }
                if (value != J.lng(items.get(k), "quantity")) {
                    changed = true;
                }
                Map<String, Object> edit = new LinkedHashMap<>();
                edit.put("warehouseId", J.lng(items.get(k), "warehouseId"));
                edit.put("quantity", value);
                edits.add(edit);
            }
            if (changed) {
                payload.put("items", edits);
            }
            boolean ok = run(() -> api.post(token(), "/api/allocations/" + id + "/approve", payload), "Allocation approved");
            if (ok) {
                show();
                waiting.reload();
            }
            return ok;
        });
    }

    private void reject(Map<String, Object> a) {
        long id = J.lng(a, "id");
        TextField note = new TextField("Reason (optional)");
        note.setWidthFull();
        Ui.dialog("Reject allocation #" + id, note, "Reject", () -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("note", note.getValue());
            boolean ok = run(() -> api.post(token(), "/api/allocations/" + id + "/reject", payload), "Allocation rejected");
            if (ok) {
                show();
            }
            return ok;
        });
    }
}
