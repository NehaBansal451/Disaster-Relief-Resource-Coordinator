package com.disasterrelief.backend.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/** "ADM, OFF" in docs/API_CONTRACT.md: roles allowed to create and change disasters and affected areas. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@PreAuthorize("hasAnyRole('ADMIN','OFFICER')")
public @interface AdminOrOfficer {
}
