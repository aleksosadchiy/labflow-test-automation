package com.mocklab.web.form;

/**
 * Form-backing object for the Sign In screen. A plain mutable class (rather
 * than a record) so Spring's {@code @ModelAttribute} binding and Thymeleaf's
 * {@code th:field} can both read and write it via a no-arg constructor and
 * setters.
 */
public class LoginForm {

    private String username;
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
