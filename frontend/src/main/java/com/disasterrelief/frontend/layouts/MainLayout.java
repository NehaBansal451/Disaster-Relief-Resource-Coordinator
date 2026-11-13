package com.disasterrelief.frontend.layouts;

import com.disasterrelief.frontend.api.AuthUser;
import com.disasterrelief.frontend.components.Formats;
import com.disasterrelief.frontend.session.UserSession;
import com.disasterrelief.frontend.views.LoginView;
import com.disasterrelief.frontend.views.StatusView;
import com.disasterrelief.frontend.views.disasters.DisasterListView;
import com.disasterrelief.frontend.views.requests.RequestListView;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.RouterLink;

/** Shell for every logged-in page: title, navigation (role-aware), who is logged in, and log out. */
public class MainLayout extends AppLayout {

    public MainLayout(UserSession session) {
        AuthUser user = session.current().map(UserSession.Login::user).orElse(null);

        H1 title = new H1("DRRC");
        title.getStyle().set("font-size", "var(--lumo-font-size-l)").set("margin", "0");

        Span who = new Span(user == null ? "" : user.fullName() + " (" + Formats.label(user.role()) + ")");
        who.getStyle().set("font-size", "var(--lumo-font-size-s)");
        Button logout = new Button("Log out", event -> {
            session.logout();
            UI.getCurrent().navigate(LoginView.class);
        });
        logout.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout header = new HorizontalLayout(new DrawerToggle(), title, who, logout);
        header.setWidthFull();
        header.setDefaultVerticalComponentAlignment(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER);
        header.expand(title);
        header.getStyle().set("padding", "0 var(--lumo-space-m)");
        addToNavbar(header);

        VerticalLayout nav = new VerticalLayout(new RouterLink("Disasters", DisasterListView.class),
                new RouterLink("Requests", RequestListView.class));
        if (user != null && user.isAdmin()) {
            nav.add(new RouterLink("System status", StatusView.class));
        }
        addToDrawer(nav);
    }
}
