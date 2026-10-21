package com.example.deliveryapp.data;

import java.util.List;

/** UI routing only. API access is always checked independently by the server. */
public enum AccountRole {
    CUSTOMER, DRIVER, STAFF, UNKNOWN;

    public static AccountRole from(List<String> roles) {
        if (roles == null) return UNKNOWN;
        if (has(roles, "ROLE_DRIVER")) return DRIVER;
        if (has(roles, "ROLE_ADMIN") || has(roles, "ROLE_SUPER_ADMIN")) return STAFF;
        return has(roles, "ROLE_USER") ? CUSTOMER : UNKNOWN;
    }

    private static boolean has(List<String> roles, String role) {
        for (String value : roles) if (role.equalsIgnoreCase(value)) return true;
        return false;
    }
}
