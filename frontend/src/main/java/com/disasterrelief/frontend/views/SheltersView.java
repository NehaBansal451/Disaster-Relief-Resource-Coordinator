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
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Relief shelters: capacity and occupancy, overcrowding warnings, alternatives and adding a shelter. */
@Route(value = "shelters", layout = MainLayout.class)
@PageTitle("DRRC - Shelters")
public class SheltersView extends AppView {

    private final Select<String> status = Pick.choice("Status", "All", "OPEN", "CLOSED", "INACTIVE");
    private final Div bar = new Div();
    private final MapGrid grid;
    private boolean built;

    public SheltersView(UserSession session, Api api) {
        super(session, api, "Relief shelters", "Capacity, occupancy and free beds. A shelter above capacity raises an alert.");
        grid = new MapGrid(session)
                .text("Code", "code")
                .text("Shelter", "name")
                .col("Place", s -> J.str(s, "district") + (J.str(s, "state").isEmpty() ? "" : ", " + J.str(s, "state")))
                .component("Occupancy", this::occupancy)
                .col("Free beds", s -> Formats.number(J.lng(s, "freeBeds")))
                .badge("Status", "status")
                .component("Flags", this::flags)
                .component("", this::actions)
                .loader(page -> api.page(token(), "/api/shelters", page, 20, "status", Pick.value(status)));
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
            if (Roles.in(role(), "ADMIN", "OFFICER")) {
                row.add(Ui.iconButton(VaadinIcon.PLUS, "Add shelter", this::addShelter));
            }
            bar.add(row);
            built = true;
        }
        grid.reload(0);
    }

    private Component occupancy(Map<String, Object> s) {
        long capacity = Math.max(J.lng(s, "capacity"), 1);
        long occupancy = J.lng(s, "occupancy");
        ProgressBar meter = new ProgressBar(0, capacity, Math.min(occupancy, capacity));
        meter.setWidth("9rem");
        return new Div(new Span(Formats.number(occupancy) + " / " + Formats.number(capacity)), meter);
    }

    private Component flags(Map<String, Object> s) {
        Div box = new Div();
        if (J.bool(s, "overcrowded")) {
            box.add(Ui.badge("Overcrowded", "red"));
        }
        if (J.bool(s, "accessible")) {
            box.add(Ui.badge("Accessible", "green"));
        }
        return box;
    }

    private Component actions(Map<String, Object> s) {
        Div box = new Div();
        if (Roles.in(role(), "ADMIN", "OFFICER", "FIELD_WORKER")) {
            box.add(Ui.small("Update occupancy", () -> updateOccupancy(s)));
        }
        if (J.bool(s, "overcrowded")) {
            box.add(Ui.small("Alternatives", () -> alternatives(s)));
        }
        return box;
    }

    private void updateOccupancy(Map<String, Object> s) {
        long id = J.lng(s, "id");
        IntegerField people = new IntegerField("People staying now");
        people.setMin(0);
        people.setValue((int) J.lng(s, "occupancy"));
        Ui.dialog("Update occupancy: " + J.str(s, "name"), people, "Save", () -> {
            if (people.getValue() == null || people.getValue() < 0) {
                Feedback.error("Enter the number of people, 0 or more.");
                return false;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("occupancy", people.getValue());
            boolean ok = run(() -> api.patch(token(), "/api/shelters/" + id + "/occupancy", payload), "Occupancy updated");
            if (ok) {
                grid.reload();
            }
            return ok;
        });
    }

    private void alternatives(Map<String, Object> s) {
        try {
            List<Map<String, Object>> rows = api.list(token(), "/api/shelters/" + J.lng(s, "id") + "/alternatives");
            MapGrid list = new MapGrid(session)
                    .text("Shelter", "name")
                    .text("District", "district")
                    .col("Free beds", x -> Formats.number(J.lng(x, "freeBeds")))
                    .col("Distance", x -> x.get("distanceKm") == null ? "-" : String.format(Locale.ENGLISH, "%.1f km", J.dbl(x, "distanceKm")))
                    .rows(rows);
            Ui.info("Nearby shelters with free beds", list).setWidth("44rem");
        } catch (BackendException ex) {
            Feedback.handle(ex, session);
        }
    }

    private void addShelter() {
        TextField code = new TextField("Code (optional)");
        TextField name = new TextField("Name");
        TextField address = new TextField("Address");
        TextField district = new TextField("District");
        TextField state = new TextField("State");
        IntegerField capacity = new IntegerField("Capacity (people)");
        capacity.setMin(1);
        IntegerField occupancy = new IntegerField("People staying now");
        occupancy.setMin(0);
        occupancy.setValue(0);
        TextField facilities = new TextField("Facilities");
        Checkbox accessible = new Checkbox("Wheelchair accessible");
        Select<String> state2 = Pick.choice("Status", "Choose", "OPEN", "CLOSED", "INACTIVE");
        state2.setValue("OPEN");
        NumberField lat = new NumberField("Latitude");
        NumberField lon = new NumberField("Longitude");
        FormLayout form = new FormLayout(code, name, address, district, state, capacity, occupancy, facilities, state2, lat, lon, accessible);
        Ui.dialog("Add shelter", form, "Create", () -> {
            if (name.getValue().isBlank() || capacity.getValue() == null || lat.getValue() == null || lon.getValue() == null) {
                Feedback.error("Name, capacity, latitude and longitude are required.");
                return false;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            if (!code.getValue().isBlank()) {
                payload.put("code", code.getValue().trim());
            }
            payload.put("name", name.getValue().trim());
            payload.put("address", address.getValue());
            payload.put("district", district.getValue());
            payload.put("state", state.getValue());
            payload.put("capacity", capacity.getValue());
            payload.put("occupancy", occupancy.getValue() == null ? 0 : occupancy.getValue());
            payload.put("facilities", facilities.getValue());
            payload.put("accessible", accessible.getValue());
            payload.put("status", state2.getValue() == null ? "OPEN" : state2.getValue());
            payload.put("latitude", lat.getValue());
            payload.put("longitude", lon.getValue());
            boolean ok = run(() -> api.post(token(), "/api/shelters", payload), "Shelter added");
            if (ok) {
                grid.reload(0);
            }
            return ok;
        });
    }
}
