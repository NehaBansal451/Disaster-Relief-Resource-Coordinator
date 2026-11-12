package com.disasterrelief.frontend.components;

import com.disasterrelief.frontend.api.BackendException;
import com.disasterrelief.frontend.session.UserSession;
import com.disasterrelief.frontend.views.LoginView;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;

/** One place that decides what the user sees when a backend call fails. */
public final class Feedback {

    private Feedback() {
    }

    /** Text shown for a failure. Pure function, unit tested. */
    public static String message(BackendException ex) {
        if (ex.isUnreachable()) {
            return "Cannot reach the server. Please try again in a moment.";
        }
        if (ex.isUnauthenticated()) {
            return "Your session has expired. Please log in again.";
        }
        if (ex.isForbidden()) {
            return "You do not have permission to do this.";
        }
        return ex.getMessage();
    }

    /** Expired or invalid login: end the session and go to the login page. Anything else: show a notification. */
    public static void handle(BackendException ex, UserSession session) {
        if (ex.isUnauthenticated()) {
            session.logout();
            UI ui = UI.getCurrent();
            if (ui != null) {
                ui.navigate(LoginView.class);
            }
            return;
        }
        error(message(ex));
    }

    public static void error(String text) {
        Notification notification = Notification.show(text, 6000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    public static void success(String text) {
        Notification notification = Notification.show(text, 3000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }
}
