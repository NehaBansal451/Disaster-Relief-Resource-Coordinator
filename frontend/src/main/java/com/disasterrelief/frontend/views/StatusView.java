package com.disasterrelief.frontend.views;

import com.disasterrelief.frontend.api.BackendStatus;
import com.disasterrelief.frontend.api.BackendStatusClient;
import com.disasterrelief.frontend.layouts.MainLayout;
import com.disasterrelief.frontend.session.UserSession;
import com.disasterrelief.frontend.views.disasters.DisasterListView;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/** Foundation check (Day 2): UI to backend to database. Admin only, because the backend endpoint is admin only. */
@Route(value = "status", layout = MainLayout.class)
@PageTitle("DRRC - System status")
public class StatusView extends VerticalLayout implements BeforeEnterObserver {

    private final BackendStatusClient client;
    private final UserSession session;
    private final Div result = new Div();

    public StatusView(BackendStatusClient client, UserSession session) {
        this.client = client;
        this.session = session;
        setPadding(true);
        Button refresh = new Button("Check again", event -> refresh());
        add(new H2("System status"),
                new Paragraph("Foundation check: UI to backend to database."),
                refresh, result);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (session.current().map(login -> !login.user().isAdmin()).orElse(true)) {
            event.forwardTo(DisasterListView.class);
            return;
        }
        refresh();
    }

    private void refresh() {
        result.removeAll();
        BackendStatus status = client.fetch(session.token());
        if (!status.reachable()) {
            result.add(new Paragraph("Backend: NOT REACHABLE. Cannot connect to " + client.baseUrl()
                    + ". Start the backend, then press Check again."));
            return;
        }
        if ("FORBIDDEN".equals(status.status())) {
            result.add(new Paragraph("The backend refused this request. Log in again as an administrator."));
            return;
        }
        result.add(new Paragraph("Backend: REACHABLE (" + status.status() + ")"));
        result.add(new Paragraph("Database: " + status.databaseStatus()));
        if ("UP".equals(status.databaseStatus())) {
            result.add(new Paragraph("PostGIS: " + status.postgis()));
            result.add(new Paragraph("Latest migration applied: V" + status.latestMigration()));
        } else {
            result.add(new Paragraph("The backend is running but cannot use the database. Check the database container and DB_* variables."));
        }
    }
}
