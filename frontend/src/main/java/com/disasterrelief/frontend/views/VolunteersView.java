package com.disasterrelief.frontend.views;

import com.disasterrelief.frontend.api.Api;
import com.disasterrelief.frontend.api.BackendException;
import com.disasterrelief.frontend.components.AppView;
import com.disasterrelief.frontend.components.Feedback;
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
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Volunteer register: availability, assignments and their progress. */
@Route(value = "volunteers", layout = MainLayout.class)
@PageTitle("DRRC - Volunteers")
public class VolunteersView extends AppView {

    private final Select<String> availability = Pick.choice("Availability", "All", "AVAILABLE", "BUSY", "OFF_DUTY");
    private final TextField district = new TextField("District");
    private final Div bar = new Div();
    private final MapGrid grid;
    private boolean built;

    public VolunteersView(UserSession session, Api api) {
        super(session, api, "Volunteer management", "Who is available, where, and what they have been asked to do.");
        grid = new MapGrid(session)
                .text("Name", "fullName")
                .text("Phone", "phone")
                .text("Skills", "skills")
                .col("Place", v -> J.str(v, "district") + (J.str(v, "state").isEmpty() ? "" : ", " + J.str(v, "state")))
                .badge("Availability", "availability")
                .component("", this::actions)
                .loader(page -> api.page(token(), "/api/volunteers", page, 20,
                        "availability", Pick.value(availability), "district", district.getValue().trim()));
        availability.addValueChangeListener(e -> grid.reload(0));
        district.addValueChangeListener(e -> grid.reload(0));
        add(bar, grid);
    }

    @Override
    protected boolean allowed(String role) {
        return Roles.in(role, Roles.VOLUNTEER_DESK);
    }

    @Override
    protected void enter(BeforeEnterEvent event) {
        if (!built) {
            HorizontalLayout row = Ui.row(availability, district);
            row.add(Ui.iconButton(VaadinIcon.PLUS, "Register volunteer", this::register));
            bar.add(row);
            built = true;
        }
        grid.reload(0);
    }

    private Component actions(Map<String, Object> v) {
        Div box = new Div(Ui.small("Availability", () -> setAvailability(v)), Ui.small("Tasks", () -> tasks(v)));
        if (Roles.in(role(), "ADMIN", "OFFICER")) {
            box.add(Ui.small("Assign task", () -> assign(v)));
        }
        return box;
    }

    private void register() {
        TextField name = new TextField("Full name");
        TextField phone = new TextField("Phone");
        TextField skills = new TextField("Skills (for example: first aid, logistics)");
        TextField dist = new TextField("District");
        TextField state = new TextField("State");
        NumberField lat = new NumberField("Latitude (optional)");
        NumberField lon = new NumberField("Longitude (optional)");
        FormLayout form = new FormLayout(name, phone, skills, dist, state, lat, lon);
        Ui.dialog("Register volunteer", form, "Register", () -> {
            if (name.getValue().isBlank()) {
                Feedback.error("Full name is required.");
                return false;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("fullName", name.getValue().trim());
            payload.put("phone", phone.getValue());
            payload.put("skills", skills.getValue());
            payload.put("district", dist.getValue());
            payload.put("state", state.getValue());
            if (lat.getValue() != null && lon.getValue() != null) {
                payload.put("latitude", lat.getValue());
                payload.put("longitude", lon.getValue());
            }
            boolean ok = run(() -> api.post(token(), "/api/volunteers", payload), "Volunteer registered");
            if (ok) {
                grid.reload(0);
            }
            return ok;
        });
    }

    private void setAvailability(Map<String, Object> v) {
        long id = J.lng(v, "id");
        Select<String> choice = Pick.choice("Availability", "Choose", "AVAILABLE", "BUSY", "OFF_DUTY");
        choice.setValue(J.str(v, "availability"));
        Ui.dialog("Availability: " + J.str(v, "fullName"), choice, "Save", () -> {
            if (choice.getValue() == null) {
                Feedback.error("Choose an availability.");
                return false;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("availability", choice.getValue());
            boolean ok = run(() -> api.patch(token(), "/api/volunteers/" + id + "/availability", payload), "Availability updated");
            if (ok) {
                grid.reload();
            }
            return ok;
        });
    }

    private void assign(Map<String, Object> v) {
        long id = J.lng(v, "id");
        List<Map<String, Object>> disasters;
        try {
            disasters = api.page(token(), "/api/disasters", 0, 100).content();
        } catch (BackendException ex) {
            Feedback.handle(ex, session);
            return;
        }
        ComboBox<Map<String, Object>> disaster = Ui.combo("Disaster", disasters, d -> J.str(d, "name"));
        IntegerField request = new IntegerField("Relief request number (optional)");
        TextField task = new TextField("Task");
        task.setWidthFull();
        FormLayout form = new FormLayout(disaster, request, task);
        Ui.dialog("Assign task to " + J.str(v, "fullName"), form, "Assign", () -> {
            if (disaster.getValue() == null || task.getValue().isBlank()) {
                Feedback.error("Choose the disaster and describe the task.");
                return false;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("disasterId", Pick.id(disaster));
            if (request.getValue() != null) {
                payload.put("requestId", request.getValue().longValue());
            }
            payload.put("task", task.getValue().trim());
            boolean ok = run(() -> api.post(token(), "/api/volunteers/" + id + "/assignments", payload), "Task assigned");
            if (ok) {
                grid.reload();
            }
            return ok;
        });
    }

    private void tasks(Map<String, Object> v) {
        long id = J.lng(v, "id");
        try {
            List<Map<String, Object>> rows = api.list(token(), "/api/volunteers/" + id + "/assignments");
            MapGrid list = new MapGrid(session)
                    .text("Task", "task")
                    .col("Request", a -> a.get("requestId") == null ? "-" : "REQ-" + J.lng(a, "requestId"))
                    .badge("Status", "status")
                    .when("Updated", "updatedAt")
                    .component("", a -> progress(a, id))
                    .rows(rows);
            Ui.info("Tasks: " + J.str(v, "fullName"), list).setWidth("52rem");
        } catch (BackendException ex) {
            Feedback.handle(ex, session);
        }
    }

    private Component progress(Map<String, Object> a, long volunteerId) {
        String s = J.str(a, "status");
        Div box = new Div();
        if ("ASSIGNED".equals(s)) {
            box.add(Ui.small("Accepted", () -> move(a, "ACCEPTED")), Ui.small("Declined", () -> move(a, "DECLINED")));
        } else if ("ACCEPTED".equals(s)) {
            box.add(Ui.small("Completed", () -> move(a, "COMPLETED")));
        }
        return box;
    }

    private void move(Map<String, Object> a, String to) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", to);
        if (run(() -> api.patch(token(), "/api/assignments/" + J.lng(a, "id") + "/status", payload), "Task updated")) {
            grid.reload();
        }
    }
}
