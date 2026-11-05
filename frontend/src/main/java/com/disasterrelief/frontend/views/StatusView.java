package com.disasterrelief.frontend.views;

import com.disasterrelief.frontend.api.BackendStatus;
import com.disasterrelief.frontend.api.BackendStatusClient;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/** Day 2 foundation screen: shows whether the UI can reach the backend and whether the backend can reach the database. */
@Route("")
@PageTitle("DRRC - System status")
public class StatusView extends VerticalLayout {

    private final BackendStatusClient client;
    private final Div result = new Div();

    public StatusView(BackendStatusClient client) {
        this.client = client;
        setPadding(true);
        Button refresh = new Button("Check again", event -> refresh());
        add(new H1("Disaster Relief Resource Coordinator"),
                new Paragraph("Foundation check: UI to backend to database."),
                refresh, result);
        refresh();
    }

    private void refresh() {
        result.removeAll();
        BackendStatus status = client.fetch();
        if (!status.reachable()) {
            result.add(new Paragraph("Backend: NOT REACHABLE. Cannot connect to " + client.baseUrl()
                    + ". Start the backend, then press Check again."));
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
