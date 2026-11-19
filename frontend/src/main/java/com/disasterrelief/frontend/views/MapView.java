package com.disasterrelief.frontend.views;

import com.disasterrelief.frontend.api.Api;
import com.disasterrelief.frontend.api.BackendException;
import com.disasterrelief.frontend.components.AppView;
import com.disasterrelief.frontend.components.Feedback;
import com.disasterrelief.frontend.components.LiveMap;
import com.disasterrelief.frontend.components.Ui;
import com.disasterrelief.frontend.layouts.MainLayout;
import com.disasterrelief.frontend.security.Roles;
import com.disasterrelief.frontend.session.UserSession;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/** Full-size live map: affected areas, shelters and warehouses. */
@Route(value = "map", layout = MainLayout.class)
@PageTitle("DRRC - Live map")
public class MapView extends AppView {

    private final LiveMap map = new LiveMap("640px");

    public MapView(UserSession session, Api api) {
        super(session, api, "Live disaster map", "Affected areas, relief shelters and resource warehouses. Click a marker for details.");
        add(Ui.card(null, map, LiveMap.legend()));
    }

    @Override
    protected boolean allowed(String role) {
        return Roles.in(role, Roles.STAFF);
    }

    @Override
    protected void enter(BeforeEnterEvent event) {
        try {
            map.show(api.get(token(), "/api/map"));
        } catch (BackendException ex) {
            Feedback.handle(ex, session);
        }
    }
}
