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
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.combobox.ComboBox;
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

/** Offers of goods from NGOs and partners. Officers accept or decline; accepted offers are marked received. */
@Route(value = "partners", layout = MainLayout.class)
@PageTitle("DRRC - Partners")
public class PartnersView extends AppView {

    private final Select<String> status = Pick.choice("Status", "All", "OFFERED", "ACCEPTED", "DECLINED", "RECEIVED");
    private final Div bar = new Div();
    private final MapGrid grid;
    private List<Map<String, Object>> resources = List.of();
    private boolean built;

    public PartnersView(UserSession session, Api api) {
        super(session, api, "Partner contributions", "Offers of goods from NGOs and partner organisations.");
        grid = new MapGrid(session)
                .text("Organisation", "organizationName")
                .col("Item", c -> J.str(c, "resourceName") + " x " + Formats.number(J.lng(c, "quantity")))
                .text("Details", "description")
                .badge("Status", "status")
                .when("Offered", "createdAt")
                .component("", this::actions)
                .loader(page -> api.page(token(), "/api/partner-contributions", page, 20, "status", Pick.value(status)));
        status.addValueChangeListener(e -> grid.reload(0));
        add(bar, grid);
    }

    @Override
    protected boolean allowed(String role) {
        return Roles.in(role, Roles.PARTNER_DESK);
    }

    @Override
    protected void enter(BeforeEnterEvent event) {
        if (!built) {
            HorizontalLayout row = Ui.row(status);
            if (Roles.in(role(), "ADMIN", "NGO")) {
                row.add(Ui.iconButton(VaadinIcon.PLUS, "Offer contribution", this::offer));
                try {
                    resources = api.list(token(), "/api/resources");
                } catch (BackendException ex) {
                    Feedback.handle(ex, session);
                }
            }
            bar.add(row);
            built = true;
        }
        grid.reload(0);
    }

    private Component actions(Map<String, Object> c) {
        Div box = new Div();
        if (!Roles.in(role(), "ADMIN", "OFFICER")) {
            return box;
        }
        String s = J.str(c, "status");
        if ("OFFERED".equals(s)) {
            box.add(Ui.small("Accept", () -> move(c, "ACCEPTED")), Ui.small("Decline", () -> move(c, "DECLINED")));
        } else if ("ACCEPTED".equals(s)) {
            box.add(Ui.small("Mark received", () -> move(c, "RECEIVED")));
        }
        return box;
    }

    private void move(Map<String, Object> c, String to) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", to);
        if (run(() -> api.patch(token(), "/api/partner-contributions/" + J.lng(c, "id") + "/status", payload), "Status updated")) {
            grid.reload();
        }
    }

    private void offer() {
        ComboBox<Map<String, Object>> resource = Ui.combo("Resource", resources,
                r -> J.str(r, "name") + " (" + J.str(r, "unit") + ")");
        IntegerField quantity = new IntegerField("Quantity");
        quantity.setMin(1);
        TextField details = new TextField("Details");
        details.setWidthFull();
        IntegerField organization = new IntegerField("Organisation number (administrators only)");
        FormLayout form = new FormLayout(resource, quantity, details);
        if ("ADMIN".equals(role())) {
            form.add(organization);
        }
        Ui.dialog("Offer contribution", form, "Offer", () -> {
            if (resource.getValue() == null || quantity.getValue() == null || quantity.getValue() < 1) {
                Feedback.error("Choose a resource and a quantity of at least 1.");
                return false;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("resourceId", Pick.id(resource));
            payload.put("quantity", quantity.getValue());
            payload.put("description", details.getValue());
            if (organization.getValue() != null) {
                payload.put("organizationId", organization.getValue().longValue());
            }
            boolean ok = run(() -> api.post(token(), "/api/partner-contributions", payload), "Contribution offered");
            if (ok) {
                grid.reload(0);
            }
            return ok;
        });
    }
}
