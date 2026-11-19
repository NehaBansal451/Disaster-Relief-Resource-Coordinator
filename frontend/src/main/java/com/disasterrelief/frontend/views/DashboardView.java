package com.disasterrelief.frontend.views;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.disasterrelief.frontend.api.Api;
import com.disasterrelief.frontend.api.BackendException;
import com.disasterrelief.frontend.components.AppView;
import com.disasterrelief.frontend.components.Feedback;
import com.disasterrelief.frontend.components.Formats;
import com.disasterrelief.frontend.components.J;
import com.disasterrelief.frontend.components.LiveMap;
import com.disasterrelief.frontend.components.MapGrid;
import com.disasterrelief.frontend.components.Ui;
import com.disasterrelief.frontend.layouts.MainLayout;
import com.disasterrelief.frontend.security.Roles;
import com.disasterrelief.frontend.session.UserSession;
import com.disasterrelief.frontend.views.disasters.DisasterFormView;
import com.disasterrelief.frontend.views.requests.RequestDetailView;
import com.disasterrelief.frontend.views.requests.RequestListView;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;

/** Home screen for staff: key numbers, live map, stock, recent requests, activity and quick actions. */
@Route(value = "", layout = MainLayout.class)
@PageTitle("DRRC - Dashboard")
public class DashboardView extends AppView {

    private final Div body = new Div();

    public DashboardView(UserSession session, Api api) {
        super(session, api, "Live relief overview", "Affected areas, resources and ongoing relief operations at a glance.");
        body.addClassName("stack");
        add(body);
    }

    @Override
    protected boolean allowed(String role) {
        return Roles.in(role, Roles.STAFF);
    }

    @Override
    protected void enter(BeforeEnterEvent event) {
        body.removeAll();
        Map<String, Object> dash;
        Map<String, Object> map;
        Api.Page requests;
        try {
            dash = api.get(token(), "/api/dashboard");
            map = api.get(token(), "/api/map");
            requests = api.page(token(), "/api/requests", 0, 6);
        } catch (BackendException ex) {
            body.add(new Paragraph(Feedback.message(ex)));
            Feedback.handle(ex, session);
            return;
        }
        List<Map<String, Object>> stock = J.list(dash, "stock");
        long available = 0;
        for (Map<String, Object> s : stock) {
            available += J.lng(s, "available");
        }
        Map<String, Object> byStatus = J.map(dash, "requestsByStatus");

        Div kpis = new Div(
                Ui.kpi(VaadinIcon.WARNING, "Active emergencies", Formats.number(J.lng(dash, "activeDisasters")),
                        J.lng(dash, "openCriticalRequests") + " critical requests open", "red"),
                Ui.kpi(VaadinIcon.USERS, "Affected people", Formats.number(J.lng(dash, "affectedPopulation")), "Across active disasters", "green"),
                Ui.kpi(VaadinIcon.CLIPBOARD_TEXT, "Pending requests", Formats.number(J.lng(byStatus, "PENDING")),
                        "Verified, waiting for approval: " + J.lng(byStatus, "VERIFIED"), "amber"),
                Ui.kpi(VaadinIcon.STOCK, "Available resources", Formats.number(available), "Units free to allocate", "blue"));
        kpis.addClassName("kpis");

        LiveMap liveMap = new LiveMap("340px");
        liveMap.show(map);
        Button full = Ui.small("Open full map", () -> UI.getCurrent().navigate(MapView.class));
        Div mapCard = Ui.card("Affected areas - live map", liveMap, LiveMap.legend(), full);

        MapGrid stockGrid = new MapGrid(session)
                .col("Resource", s -> J.str(s, "resourceName") + " (" + J.str(s, "unit") + ")")
                .col("On hand", s -> Formats.number(J.lng(s, "onHand")))
                .col("Reserved", s -> Formats.number(J.lng(s, "reserved")))
                .col("Available", s -> Formats.number(J.lng(s, "available")))
                .component("Status", this::stockStatus)
                .rows(stock);
        Div stockCard = Ui.card("Resource stock (inventory)", stockGrid,
                Ui.small("View all", () -> UI.getCurrent().navigate(InventoryView.class)));

        MapGrid recent = new MapGrid(session)
                .col("Request", r -> "REQ-" + J.lng(r, "id"))
                .text("Area", "affectedAreaName")
                .col("Need", r -> J.str(r, "resourceName") + " x " + J.lng(r, "quantity"))
                .col("People", r -> Formats.number(J.lng(r, "affectedPopulation")))
                .badge("Severity", "severity")
                .badge("Status", "status")
                .onRowClick(r -> UI.getCurrent().navigate(RequestDetailView.class,
                        new RouteParameters("id", String.valueOf(J.lng(r, "id")))))
                .rows(requests.content());
        Div recentCard = Ui.card("Recent relief requests", recent,
                Ui.small("View all", () -> UI.getCurrent().navigate(RequestListView.class)));

        Div quick = new Div();
        quick.addClassName("quick");
        if (session.user().canManageDisasters()) {
            quick.add(Ui.small("Add disaster", () -> UI.getCurrent().navigate(DisasterFormView.class)));
        }
        if (Roles.in(role(), Roles.LOGISTICS)) {
            quick.add(Ui.small("Allocate resources", () -> UI.getCurrent().navigate(AllocationView.class)));
        }
        if (Roles.in(role(), Roles.LOGISTICS)) {
            quick.add(Ui.small("Update inventory", () -> UI.getCurrent().navigate(InventoryView.class)));
        }
        if (Roles.in(role(), Roles.VOLUNTEER_DESK)) {
            quick.add(Ui.small("Register volunteer", () -> UI.getCurrent().navigate(VolunteersView.class)));
        }

        Map<String, Object> sh = J.map(dash, "shelters");
        Div shelters = new Div(
                Ui.line("Open shelters", Formats.number(J.lng(sh, "openShelters"))),
                Ui.line("Occupancy", Formats.number(J.lng(sh, "totalOccupancy")) + " of " + Formats.number(J.lng(sh, "totalCapacity"))),
                Ui.line("Overcrowded", Formats.number(J.lng(sh, "overcrowdedShelters"))));

        Div activity = new Div();
        activity.addClassName("activity");
        List<Map<String, Object>> items = J.list(dash, "recentActivity");
        if (items.isEmpty()) {
            activity.add(new Span("No recent activity."));
        }
        for (Map<String, Object> a : items.subList(0, Math.min(6, items.size()))) {
            Span text = new Span("Request #" + J.lng(a, "requestId") + ": " + Formats.label(J.str(a, "action"))
                    + (J.str(a, "toStatus").isEmpty() || Formats.label(J.str(a, "toStatus")).equals(Formats.label(J.str(a, "action"))) ? "" : " (" + Formats.label(J.str(a, "toStatus")) + ")"));
            Span small = new Span(J.str(a, "actorName") + " - " + Formats.dateTime(J.str(a, "at")));
            small.getElement().getStyle().set("display", "block").set("color", "var(--drrc-muted)").set("font-size", "0.78rem");
            Div item = new Div(text, small);
            item.addClassName("activity-item");
            activity.add(item);
        }

        Div right = new Div(Ui.card("Quick actions", quick), Ui.card("Shelters", shelters), Ui.card("Recent activity", activity));
        right.addClassName("stack");
        Div left = new Div(mapCard, recentCard);
        left.addClassName("stack");
        Div top = new Div(left, right);
        top.addClassName("cols-2");

        body.add(kpis, top, stockCard);
    }

    private Span stockStatus(Map<String, Object> s) {
        long onHand = J.lng(s, "onHand");
        long free = J.lng(s, "available");
        if (J.lng(s, "expiredQuantity") > 0) {
            return Ui.badge("Expired stock", "red");
        }
        if (J.lng(s, "expiringQuantity") > 0) {
            return Ui.badge("Expiring soon", "amber");
        }
        if (free <= 0) {
            return Ui.badge("Out", "red");
        }
        if (free * 5 < onHand) {
            return Ui.badge("Low", "red");
        }
        return Ui.badge("Available", "green");
    }

    /** Exposed for the unit test: the same rule as the stock badge. */
    static String stockLabel(long onHand, long available, long expiring, long expired) {
        if (expired > 0) {
            return "Expired stock";
        }
        if (expiring > 0) {
            return "Expiring soon";
        }
        if (available <= 0) {
            return "Out";
        }
        return available * 5 < onHand ? "Low" : "Available";
    }

    @SuppressWarnings("unused")
    private static List<Button> unused() {
        return new ArrayList<>();
    }
}
