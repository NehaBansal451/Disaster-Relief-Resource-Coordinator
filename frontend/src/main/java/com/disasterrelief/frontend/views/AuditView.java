package com.disasterrelief.frontend.views;

import com.disasterrelief.frontend.api.Api;
import com.disasterrelief.frontend.components.AppView;
import com.disasterrelief.frontend.components.J;
import com.disasterrelief.frontend.components.MapGrid;
import com.disasterrelief.frontend.components.Ui;
import com.disasterrelief.frontend.layouts.MainLayout;
import com.disasterrelief.frontend.security.Roles;
import com.disasterrelief.frontend.session.UserSession;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/** Audit log: who did what and when. Read-only; entries cannot be changed or removed. */
@Route(value = "audit", layout = MainLayout.class)
@PageTitle("DRRC - Audit log")
public class AuditView extends AppView {

    private final TextField entityType = new TextField("Record type (for example ALLOCATION)");
    private final MapGrid grid;

    public AuditView(UserSession session, Api api) {
        super(session, api, "Audit log", "Every important action, with the person who did it. Entries are append-only.");
        grid = new MapGrid(session)
                .when("When", "createdAt")
                .col("Who", a -> J.str(a, "actorName") + " (" + J.str(a, "actorRole") + ")")
                .badge("Action", "action")
                .col("Record", a -> J.str(a, "entityType") + (a.get("entityId") == null ? "" : " #" + J.lng(a, "entityId")))
                .text("Details", "details")
                .loader(page -> api.page(token(), "/api/audit", page, 25, "entityType", entityType.getValue().trim().toUpperCase()));
        entityType.setClearButtonVisible(true);
        entityType.addValueChangeListener(e -> grid.reload(0));
        add(Ui.row(entityType), grid);
    }

    @Override
    protected boolean allowed(String role) {
        return Roles.in(role, Roles.DECIDERS);
    }

    @Override
    protected void enter(BeforeEnterEvent event) {
        grid.reload(0);
    }
}
