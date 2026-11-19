package com.disasterrelief.frontend.components;

import com.disasterrelief.frontend.api.Api;
import com.disasterrelief.frontend.api.BackendException;
import com.disasterrelief.frontend.session.UserSession;
import com.disasterrelief.frontend.views.LoginView;
import com.disasterrelief.frontend.views.disasters.DisasterListView;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;

/**
 * Base for the new screens: login check, role check, page title, and error handling in one place.
 * Data is loaded in {@link #enter}, not in the constructor, because Vaadin builds the page before the guard runs.
 */
public abstract class AppView extends VerticalLayout implements BeforeEnterObserver {

    protected final UserSession session;
    protected final Api api;

    protected AppView(UserSession session, Api api, String title, String subtitle) {
        this.session = session;
        this.api = api;
        setPadding(false);
        setSpacing(true);
        addClassName("page");
        H2 heading = new H2(title);
        Paragraph sub = new Paragraph(subtitle);
        sub.addClassName("page-sub");
        Div head = new Div(heading, sub);
        head.addClassName("page-head");
        add(head);
    }

    /** Roles that may open this screen. Default: everyone logged in. */
    protected boolean allowed(String role) {
        return true;
    }

    protected abstract void enter(BeforeEnterEvent event);

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (session.current().isEmpty()) {
            event.forwardTo(LoginView.class);
            return;
        }
        if (!allowed(role())) {
            event.forwardTo(DisasterListView.class);
            return;
        }
        enter(event);
    }

    protected String token() {
        return session.token();
    }

    protected String role() {
        return session.user().role();
    }

    /** Runs a backend action; shows the message on success, or the error on failure. Returns true on success. */
    protected boolean run(Runnable action, String success) {
        try {
            action.run();
            if (success != null) {
                Feedback.success(success);
            }
            return true;
        } catch (BackendException ex) {
            Feedback.handle(ex, session);
            return false;
        }
    }
}
