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
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stock by warehouse and batch, expiry warnings, stock history and goods receipts. */
@Route(value = "inventory", layout = MainLayout.class)
@PageTitle("DRRC - Inventory")
public class InventoryView extends AppView {

    private final MapGrid grid;
    private final Div filters = new Div();
    private final Checkbox expiring = new Checkbox("Expiring or expired only");
    private ComboBox<Map<String, Object>> warehouse;
    private ComboBox<Map<String, Object>> resource;
    private List<Map<String, Object>> warehouses = List.of();
    private List<Map<String, Object>> resources = List.of();
    private boolean built;

    public InventoryView(UserSession session, Api api) {
        super(session, api, "Resource inventory", "Stock by warehouse and batch. Quantities are always shown with their unit.");
        grid = new MapGrid(session)
                .text("Warehouse", "warehouseName")
                .col("Resource", r -> J.str(r, "resourceName") + " (" + J.str(r, "unit") + ")")
                .text("Batch", "batchRef")
                .col("On hand", r -> Formats.number(J.lng(r, "onHand")))
                .col("Reserved", r -> Formats.number(J.lng(r, "reserved")))
                .col("Available", r -> Formats.number(J.lng(r, "available")))
                .text("Expiry date", "expiryDate")
                .badge("Expiry", "expiryStatus")
                .component("", r -> Ui.small("History", () -> showLedger(r)))
                .loader(page -> api.page(token(), "/api/inventory", page, 20,
                        "warehouseId", Pick.id(warehouse), "resourceId", Pick.id(resource),
                        "expiringOnly", expiring.getValue() ? "true" : null));
        add(filters, new Div(grid));
    }

    @Override
    protected boolean allowed(String role) {
        return Roles.in(role, Roles.LOGISTICS);
    }

    @Override
    protected void enter(BeforeEnterEvent event) {
        try {
            warehouses = api.list(token(), "/api/warehouses");
            resources = api.list(token(), "/api/resources");
        } catch (BackendException ex) {
            Feedback.handle(ex, session);
            return;
        }
        if (!built) {
            build();
            built = true;
        }
        grid.reload(0);
    }

    private void build() {
        warehouse = Ui.combo("Warehouse", warehouses, w -> J.str(w, "name"));
        resource = Ui.combo("Resource", resources, r -> J.str(r, "name"));
        warehouse.addValueChangeListener(e -> grid.reload(0));
        resource.addValueChangeListener(e -> grid.reload(0));
        expiring.addValueChangeListener(e -> grid.reload(0));
        HorizontalLayout bar = Ui.row(warehouse, resource, expiring);
        if (Roles.in(role(), "ADMIN", "WAREHOUSE_MANAGER")) {
            bar.add(Ui.iconButton(VaadinIcon.PLUS, "Receive stock", this::receive));
        }
        filters.add(bar);
    }

    private void receive() {
        ComboBox<Map<String, Object>> wh = Ui.combo("Warehouse", warehouses, w -> J.str(w, "name"));
        ComboBox<Map<String, Object>> res = Ui.combo("Resource", resources,
                r -> J.str(r, "name") + " (" + J.str(r, "unit") + ")");
        IntegerField qty = new IntegerField("Quantity");
        qty.setMin(1);
        TextField batch = new TextField("Batch number (required for perishable items)");
        DatePicker expiry = new DatePicker("Expiry date");
        TextField note = new TextField("Note");
        FormLayout form = new FormLayout(wh, res, qty, batch, expiry, note);
        Ui.dialog("Receive stock", form, "Receive", () -> {
            if (wh.getValue() == null || res.getValue() == null || qty.getValue() == null || qty.getValue() < 1) {
                Feedback.error("Choose a warehouse, a resource and a quantity of at least 1.");
                return false;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("warehouseId", Pick.id(wh));
            payload.put("resourceId", Pick.id(res));
            payload.put("quantity", qty.getValue());
            if (!batch.getValue().isBlank()) {
                payload.put("batchRef", batch.getValue().trim());
            }
            if (expiry.getValue() != null) {
                payload.put("expiryDate", expiry.getValue().toString());
            }
            if (!note.getValue().isBlank()) {
                payload.put("note", note.getValue().trim());
            }
            boolean ok = run(() -> api.post(token(), "/api/inventory/receipts", payload), "Stock received");
            if (ok) {
                grid.reload();
            }
            return ok;
        });
    }

    private void showLedger(Map<String, Object> row) {
        try {
            List<Map<String, Object>> rows = api.list(token(), "/api/inventory/" + J.lng(row, "id") + "/transactions");
            MapGrid ledger = new MapGrid(session)
                    .when("When", "createdAt")
                    .badge("Type", "type")
                    .col("Change", x -> signed(J.lng(x, "quantity")))
                    .col("On hand after", x -> Formats.number(J.lng(x, "onHandAfter")))
                    .col("Reserved after", x -> Formats.number(J.lng(x, "reservedAfter")))
                    .text("By", "actorName")
                    .text("Note", "note")
                    .rows(rows);
            Dialog dialog = Ui.info("Stock history: " + J.str(row, "resourceName") + " at " + J.str(row, "warehouseName"), ledger);
            dialog.setWidth("60rem");
        } catch (BackendException ex) {
            Feedback.handle(ex, session);
        }
    }

    private static String signed(long value) {
        return (value > 0 ? "+" : "") + value;
    }
}
