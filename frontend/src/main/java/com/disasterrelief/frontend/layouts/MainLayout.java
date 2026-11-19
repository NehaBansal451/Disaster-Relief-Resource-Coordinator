package com.disasterrelief.frontend.layouts;

import java.util.Map;

import com.disasterrelief.frontend.api.Api;
import com.disasterrelief.frontend.api.AuthUser;
import com.disasterrelief.frontend.api.BackendException;
import com.disasterrelief.frontend.components.Assets;
import com.disasterrelief.frontend.components.Formats;
import com.disasterrelief.frontend.components.J;
import com.disasterrelief.frontend.security.Roles;
import com.disasterrelief.frontend.session.UserSession;
import com.disasterrelief.frontend.views.LoginView;
import com.disasterrelief.frontend.views.StatusView;
import com.disasterrelief.frontend.views.AlertsView;
import com.disasterrelief.frontend.views.AllocationView;
import com.disasterrelief.frontend.views.AnalyticsView;
import com.disasterrelief.frontend.views.AnalyzerView;
import com.disasterrelief.frontend.views.AuditView;
import com.disasterrelief.frontend.views.DashboardView;
import com.disasterrelief.frontend.views.DispatchView;
import com.disasterrelief.frontend.views.InventoryView;
import com.disasterrelief.frontend.views.MapView;
import com.disasterrelief.frontend.views.PartnersView;
import com.disasterrelief.frontend.views.SheltersView;
import com.disasterrelief.frontend.views.VolunteersView;
import com.disasterrelief.frontend.views.disasters.DisasterListView;
import com.disasterrelief.frontend.views.requests.RequestDetailView;
import com.disasterrelief.frontend.views.requests.RequestListView;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.HighlightConditions;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RouterLink;

/** Shell for every logged-in page: teal sidebar (role-aware), top bar with request lookup, alerts bell, theme and log out. */
public class MainLayout extends AppLayout {

    private final UserSession session;
    private final Api api;
    private final Span bellCount = new Span();

    public MainLayout(UserSession session, Api api) {
        this.session = session;
        this.api = api;
        AuthUser user = session.current().map(UserSession.Login::user).orElse(null);
        String role = user == null ? "" : user.role();

        addToDrawer(sidebar(role));
        addToNavbar(topbar(user, role));
    }

    private Component sidebar(String role) {
        Div mark = new Div(VaadinIcon.HEART.create());
        mark.addClassName("brand-mark");
        Span name = new Span("RELIEF COORDINATOR");
        name.addClassName("brand-name");
        Span sub = new Span("Disaster Relief Resource Coordinator");
        sub.addClassName("brand-sub");
        Div text = new Div(name, new Div(sub));
        Div brand = new Div(mark, text);
        brand.addClassName("brand");

        Div nav = new Div();
        nav.addClassName("nav");
        boolean staff = Roles.in(role, Roles.STAFF);
        if (staff) {
            nav.add(link(VaadinIcon.DASHBOARD, "Dashboard", DashboardView.class));
            nav.add(link(VaadinIcon.MAP_MARKER, "Live map", MapView.class));
        }
        nav.add(link(VaadinIcon.WARNING, "Disasters", DisasterListView.class));
        nav.add(link(VaadinIcon.CLIPBOARD_TEXT, "Relief requests", RequestListView.class));
        if (staff) {
            if (Roles.in(role, Roles.LOGISTICS)) {
                nav.add(link(VaadinIcon.STOCK, "Inventory", InventoryView.class));
            }
            nav.add(link(VaadinIcon.HOME, "Shelters", SheltersView.class));
        }
        if (Roles.in(role, Roles.LOGISTICS)) {
            nav.add(link(VaadinIcon.LIST, "Allocation", AllocationView.class));
        }
        if (staff) {
            nav.add(link(VaadinIcon.TRUCK, "Dispatch", DispatchView.class));
        }
        if (Roles.in(role, Roles.VOLUNTEER_DESK)) {
            nav.add(link(VaadinIcon.USERS, "Volunteers", VolunteersView.class));
        }
        if (Roles.in(role, Roles.PARTNER_DESK)) {
            nav.add(link(VaadinIcon.HANDSHAKE, "Partners", PartnersView.class));
        }
        if (staff) {
            nav.add(link(VaadinIcon.BELL, "Alerts", AlertsView.class));
            nav.add(link(VaadinIcon.LIGHTBULB, "Report analyzer", AnalyzerView.class));
        }
        if (Roles.in(role, Roles.DECIDERS)) {
            nav.add(link(VaadinIcon.BAR_CHART, "Analytics & reports", AnalyticsView.class));
            nav.add(link(VaadinIcon.SHIELD, "Audit log", AuditView.class));
        }
        if ("ADMIN".equals(role)) {
            nav.add(link(VaadinIcon.COG, "System status", StatusView.class));
        }
        Div foot = new Div(new Span("Official use only"));
        foot.addClassName("side-foot");
        Div side = new Div(brand, nav, foot);
        side.addClassName("side");
        return side;
    }

    private RouterLink link(VaadinIcon icon, String label, Class<? extends Component> target) {
        RouterLink link = new RouterLink();
        link.add(icon.create(), new Span(label));
        link.setRoute(target);
        link.addClassName("nav-item");
        link.setHighlightCondition(HighlightConditions.sameLocation());
        return link;
    }

    private Component topbar(AuthUser user, String role) {
        DrawerToggle toggle = new DrawerToggle();

        TextField lookup = new TextField();
        lookup.setPlaceholder("Open request #");
        lookup.setPrefixComponent(VaadinIcon.SEARCH.create());
        lookup.setWidth("11rem");
        Button open = new Button("Open", e -> openRequest(lookup));
        open.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Div spacer = new Div();
        spacer.addClassName("spacer");

        Div bell = new Div();
        bell.addClassName("bell");
        Button bellButton = new Button(VaadinIcon.BELL.create(), e -> UI.getCurrent().navigate(AlertsView.class));
        bellButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        bellButton.setTooltipText("Alerts");
        bellCount.addClassName("bell-count");
        bellCount.setVisible(false);
        bell.add(bellButton, bellCount);
        bell.setVisible(Roles.in(role, Roles.STAFF));

        Button theme = new Button(VaadinIcon.MOON.create(), e -> Assets.toggleTheme(UI.getCurrent()));
        theme.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        theme.setTooltipText("Light / dark");

        String fullName = user == null ? "" : user.fullName();
        Div avatar = new Div(new Span(initials(fullName)));
        avatar.addClassName("avatar");
        Span nameLine = new Span(fullName);
        Span roleLine = new Span(user == null ? "" : Formats.label(user.role()));
        Div who = new Div(new Div(nameLine), new Div(roleLine));
        who.addClassName("who");
        nameLine.getElement().getStyle().set("font-weight", "700");

        Button logout = new Button("Log out", e -> {
            session.logout();
            UI.getCurrent().navigate(LoginView.class);
        });
        logout.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Div bar = new Div(toggle, lookup, open, spacer, bell, theme, avatar, who, logout);
        bar.addClassName("topbar");
        return bar;
    }

    private void openRequest(TextField lookup) {
        try {
            long id = Long.parseLong(lookup.getValue().replaceAll("[^0-9]", ""));
            UI.getCurrent().navigate(RequestDetailView.class, new RouteParameters("id", String.valueOf(id)));
            lookup.clear();
        } catch (NumberFormatException ex) {
            lookup.setInvalid(true);
            lookup.setErrorMessage("Type a request number");
        }
    }

    static String initials(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "?";
        }
        String[] parts = fullName.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase();
    }

    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        Assets.install(event.getUI());
        if (session.current().isPresent() && Roles.in(session.user().role(), Roles.STAFF)) {
            try {
                Map<String, Object> unread = api.get(session.token(), "/api/alerts", "status", "NEW", "size", 1);
                long count = J.lng(unread, "totalElements");
                bellCount.setText(count > 99 ? "99+" : String.valueOf(count));
                bellCount.setVisible(count > 0);
            } catch (BackendException ignored) {
                // The bell is a convenience; a failure here must not break the page.
            }
        }
    }
}
