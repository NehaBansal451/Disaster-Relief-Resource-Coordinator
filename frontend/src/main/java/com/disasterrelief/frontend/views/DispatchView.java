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
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dispatch and delivery tracking: Dispatched, In transit, Delivered. Click a row to see the timeline. */
@Route(value = "dispatch", layout = MainLayout.class)
@PageTitle("DRRC - Dispatch")
public class DispatchView extends AppView {

    private final Select<String> status = Pick.choice("Status", "All", "DISPATCHED", "IN_TRANSIT", "DELIVERED");
    private final Div bar = new Div();
    private final MapGrid grid;
    private List<Map<String, Object>> warehouses = List.of();
    private boolean built;

    public DispatchView(UserSession session, Api api) {
        super(session, api, "Dispatch and delivery", "Track each load from the warehouse to the people who asked for it.");
        grid = new MapGrid(session)
                .col("Dispatch", d -> "D-" + J.lng(d, "id"))
                .col("Request", d -> "REQ-" + J.lng(d, "requestId"))
                .text("From", "warehouseName")
                .col("Load", d -> J.str(d, "resourceName") + " x " + Formats.number(J.lng(d, "quantity")) + " " + J.str(d, "unit"))
                .text("Vehicle", "vehicle")
                .text("Driver", "driverName")
                .badge("Status", "status")
                .when("Dispatched", "dispatchedAt")
                .onRowClick(this::open)
                .loader(page -> api.page(token(), "/api/dispatches", page, 20, "status", Pick.value(status)));
        status.addValueChangeListener(e -> grid.reload(0));
        add(bar, grid);
    }

    @Override
    protected boolean allowed(String role) {
        return Roles.in(role, Roles.STAFF);
    }

    @Override
    protected void enter(BeforeEnterEvent event) {
        if (!built) {
            HorizontalLayout row = Ui.row(status);
            if (Roles.in(role(), "ADMIN", "WAREHOUSE_MANAGER")) {
                row.add(Ui.iconButton(VaadinIcon.TRUCK, "New dispatch", this::create));
                try {
                    warehouses = api.list(token(), "/api/warehouses");
                } catch (BackendException ex) {
                    Feedback.handle(ex, session);
                }
            }
            bar.add(row);
            built = true;
        }
        grid.reload(0);
    }

    private void open(Map<String, Object> d) {
        long id = J.lng(d, "id");
        String current = J.str(d, "status");
        MapGrid timeline = new MapGrid(session)
                .badge("Status", "status")
                .when("When", "at")
                .text("By", "actorName")
                .text("Note", "note")
                .rows(J.list(d, "timeline"));
        Div content = new Div(Ui.stepper(current),
                Ui.line("Load", J.str(d, "resourceName") + " x " + Formats.number(J.lng(d, "quantity")) + " " + J.str(d, "unit")),
                Ui.line("From", J.str(d, "warehouseName")),
                Ui.line("Vehicle", J.str(d, "vehicle")),
                Ui.line("Driver", J.str(d, "driverName")),
                Ui.line("Delivered", Formats.dateTime(J.str(d, "deliveredAt"))),
                timeline);
        Dialog dialog = Ui.info("Dispatch D-" + id, content);
        dialog.setWidth("44rem");
        String next = switch (current) {
            case "DISPATCHED" -> "IN_TRANSIT";
            case "IN_TRANSIT" -> "DELIVERED";
            default -> null;
        };
        if (next != null && Roles.in(role(), "ADMIN", "OFFICER", "WAREHOUSE_MANAGER", "FIELD_WORKER")) {
            Button advance = Ui.primary("Mark as " + Formats.label(next), () -> {
                if (run(() -> api.patch(token(), "/api/dispatches/" + id + "/status",
                        Map.<String, Object>of("status", next)), "Status updated")) {
                    dialog.close();
                    grid.reload();
                }
            });
            dialog.getFooter().add(advance);
        }
    }

    private void create() {
        IntegerField allocationId = new IntegerField("Approved allocation number");
        allocationId.setMin(1);
        ComboBox<Map<String, Object>> warehouse = Ui.combo("Warehouse", warehouses, w -> J.str(w, "name"));
        TextField vehicle = new TextField("Vehicle");
        TextField driver = new TextField("Driver");
        TextField note = new TextField("Note");
        FormLayout form = new FormLayout(allocationId, warehouse, vehicle, driver, note);
        Ui.dialog("New dispatch", form, "Dispatch", () -> {
            if (allocationId.getValue() == null || warehouse.getValue() == null) {
                Feedback.error("Enter the allocation number and choose the warehouse.");
                return false;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("allocationId", allocationId.getValue().longValue());
            payload.put("warehouseId", Pick.id(warehouse));
            payload.put("vehicle", vehicle.getValue());
            payload.put("driverName", driver.getValue());
            payload.put("note", note.getValue());
            boolean ok = run(() -> api.post(token(), "/api/dispatches", payload), "Dispatch created");
            if (ok) {
                grid.reload(0);
            }
            return ok;
        });
    }
}
