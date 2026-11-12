package com.disasterrelief.frontend.views.disasters;

import com.disasterrelief.frontend.api.AffectedAreaClient;
import com.disasterrelief.frontend.api.AffectedAreaForm;
import com.disasterrelief.frontend.api.BackendException;
import com.disasterrelief.frontend.components.Feedback;
import com.disasterrelief.frontend.components.Formats;
import com.disasterrelief.frontend.session.UserSession;
import com.vaadin.flow.component.HasValidation;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import java.util.List;
import java.util.Map;

/** Dialog to add an affected area to a disaster. The backend validates again and is the authority. */
class AffectedAreaDialog extends Dialog {

    private final AffectedAreaClient client;
    private final UserSession session;
    private final long disasterId;
    private final Runnable onSaved;

    private final TextField name = new TextField("Area name");
    private final ComboBox<String> areaType = new ComboBox<>("Area type");
    private final TextField district = new TextField("District");
    private final TextField state = new TextField("State");
    private final IntegerField population = new IntegerField("Estimated population");
    private final ComboBox<String> impact = new ComboBox<>("Impact level");
    private final NumberField latitude = new NumberField("Latitude");
    private final NumberField longitude = new NumberField("Longitude");
    private final TextArea impactDescription = new TextArea("Impact description");
    private final Button save = new Button("Add area");

    AffectedAreaDialog(AffectedAreaClient client, UserSession session, long disasterId, Runnable onSaved) {
        this.client = client;
        this.session = session;
        this.disasterId = disasterId;
        this.onSaved = onSaved;

        setHeaderTitle("Add affected area");
        setWidth("40rem");
        setCloseOnOutsideClick(false);

        name.setMaxLength(200);
        areaType.setItems(List.of("VILLAGE", "TOWN", "WARD", "DISTRICT"));
        areaType.setItemLabelGenerator(Formats::label);
        areaType.setValue("VILLAGE");
        areaType.setAllowCustomValue(false);
        population.setMin(0);
        population.setStepButtonsVisible(true);
        impact.setItems(List.of("CRITICAL", "HIGH", "MEDIUM", "LOW"));
        impact.setItemLabelGenerator(Formats::label);
        latitude.setMin(-90);
        latitude.setMax(90);
        longitude.setMin(-180);
        longitude.setMax(180);
        impactDescription.setMaxLength(5000);

        FormLayout form = new FormLayout(name, areaType, district, state, population, impact, latitude, longitude,
                impactDescription);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("30rem", 2));
        form.setColspan(name, 2);
        form.setColspan(impactDescription, 2);
        add(form);

        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickListener(event -> submit());
        getFooter().add(new Button("Cancel", event -> close()), save);
    }

    private void submit() {
        for (HasValidation field : fields().values()) {
            field.setInvalid(false);
        }
        boolean ok = true;
        if (name.getValue() == null || name.getValue().isBlank()) {
            mark(name, "Name is required");
            ok = false;
        }
        if (population.isEmpty()) {
            mark(population, "Population estimate is required");
            ok = false;
        }
        if (impact.isEmpty()) {
            mark(impact, "Choose an impact level");
            ok = false;
        }
        if (latitude.isEmpty()) {
            mark(latitude, "Latitude is required");
            ok = false;
        }
        if (longitude.isEmpty()) {
            mark(longitude, "Longitude is required");
            ok = false;
        }
        if (!ok) {
            return;
        }

        AffectedAreaForm body = new AffectedAreaForm(disasterId, name.getValue().trim(), areaType.getValue(),
                blankToNull(district.getValue()), blankToNull(state.getValue()), population.getValue(),
                impact.getValue(), blankToNull(impactDescription.getValue()), latitude.getValue(), longitude.getValue());
        save.setEnabled(false);
        try {
            client.create(session.token(), body);
            Feedback.success("Affected area added.");
            close();
            onSaved.run();
        } catch (BackendException ex) {
            boolean shown = false;
            for (Map.Entry<String, String> error : ex.fieldErrors().entrySet()) {
                HasValidation field = fields().get(error.getKey());
                if (field != null) {
                    mark(field, error.getValue());
                    shown = true;
                }
            }
            if (!shown) {
                Feedback.handle(ex, session);
            }
        } finally {
            save.setEnabled(true);
        }
    }

    private Map<String, HasValidation> fields() {
        return Map.of("name", name, "areaType", areaType, "district", district, "state", state,
                "populationEstimate", population, "impactLevel", impact, "latitude", latitude,
                "longitude", longitude, "impactDescription", impactDescription);
    }

    private static void mark(HasValidation field, String message) {
        field.setErrorMessage(message);
        field.setInvalid(true);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
