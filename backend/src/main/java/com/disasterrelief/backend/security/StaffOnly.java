package com.disasterrelief.backend.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * "staff" in docs/API_CONTRACT.md: every role that works inside the platform. VOLUNTEER and CITIZEN are excluded.
 * ASSUMPTION (contract does not define "staff"): ADMIN, OFFICER, WAREHOUSE_MANAGER, FIELD_WORKER, NGO. Needs approval.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@PreAuthorize("hasAnyRole('ADMIN','OFFICER','WAREHOUSE_MANAGER','FIELD_WORKER','NGO')")
public @interface StaffOnly {
}
