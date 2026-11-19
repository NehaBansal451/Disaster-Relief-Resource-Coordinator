package com.disasterrelief.frontend.views;

import com.disasterrelief.frontend.api.Api;
import com.disasterrelief.frontend.components.AppView;
import com.disasterrelief.frontend.components.J;
import com.disasterrelief.frontend.components.MapGrid;
import com.disasterrelief.frontend.components.Pick;
import com.disasterrelief.frontend.components.Ui;
import com.disasterrelief.frontend.layouts.MainLayout;
import com.disasterrelief.frontend.security.Roles;
import com.disasterrelief.frontend.session.UserSession;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.util.Map;

/** Alerts: critical requests, expiring stock, overcrowded shelters. Mark each one read once it is dealt with. */
@Route(value = "alerts", layout = MainLayout.class)
@PageTitle("DRRC - Alerts")
public class AlertsView extends AppView {

    private final Select<String> status = Pick.choice("Status", "All", "NEW", "READ");
    private final MapGrid grid;

    public AlertsView(UserSession session, Api api) {
        super(session, api, "Alerts and notifications", "Things that need attention. Newest first.");
        grid = new MapGrid(session)
                .badge("Severity", "severity")
                .badge("Type", "type")
                .text("Message", "message")
                .when("Raised", "createdAt")
                .badge("Status", "status")
                .component("", this::action)
                .loader(page -> api.page(token(), "/api/alerts", page, 20, "status", Pick.value(status)));
        status.addValueChangeListener(e -> grid.reload(0));
        add(Ui.row(status), grid);
    }

    @Override
    protected boolean allowed(String role) {
        return Roles.in(role, Roles.STAFF);
    }

    @Override
    protected void enter(BeforeEnterEvent event) {
        grid.reload(0);
    }

    private Component action(Map<String, Object> alert) {
        Div box = new Div();
        if ("NEW".equals(J.str(alert, "status"))) {
            box.add(Ui.small("Mark read", () -> {
                if (run(() -> api.patch(token(), "/api/alerts/" + J.lng(alert, "id") + "/read", Map.of()), "Marked as read")) {
                    grid.reload();
                }
            }));
        }
        return box;
    }
}
