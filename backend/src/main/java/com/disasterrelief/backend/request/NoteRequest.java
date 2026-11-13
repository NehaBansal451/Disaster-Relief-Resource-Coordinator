package com.disasterrelief.backend.request;

import jakarta.validation.constraints.Size;

/** Body for approve (optional note) and for reject / cancel (reason required, checked in the rules). */
public record NoteRequest(@Size(max = 1000) String note, @Size(max = 1000) String reason) {
}
