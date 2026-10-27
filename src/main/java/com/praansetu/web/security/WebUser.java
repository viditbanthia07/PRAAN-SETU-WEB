package com.praansetu.web.security;

import java.io.Serializable;

/** The authenticated responder stored in the HTTP session. Contains no credentials. */
public record WebUser(String id, String username, String fullName, String organization) implements Serializable {

    public static final String SESSION_KEY = "praansetu.user";
}
