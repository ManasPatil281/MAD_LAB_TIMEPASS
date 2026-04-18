package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persists the JWT token and basic user info returned by the login / register endpoints.
 * Uses SharedPreferences under the hood, so the token survives process kills.
 */
public class SessionManager {

    private static final String PREF_NAME = "scholarmate_session";
    private static final String KEY_TOKEN = "jwt_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_NAME = "full_name";
    private static final String KEY_ROLE = "role";
    private static final String KEY_INSTITUTION = "institution";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ── Token ──────────────────────────────────────────────────────

    public void saveToken(String token) {
        prefs.edit().putString(KEY_TOKEN, token).apply();
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public boolean isLoggedIn() {
        return getToken() != null;
    }

    // ── User info ──────────────────────────────────────────────────

    public void saveUserInfo(String userId, String email, String fullName) {
        prefs.edit()
                .putString(KEY_USER_ID, userId)
                .putString(KEY_EMAIL, email)
                .putString(KEY_NAME, fullName)
                .apply();
    }

    public void saveUserInfo(String userId, String email, String fullName, String role, String institution) {
        prefs.edit()
                .putString(KEY_USER_ID, userId)
                .putString(KEY_EMAIL, email)
                .putString(KEY_NAME, fullName)
                .putString(KEY_ROLE, role)
                .putString(KEY_INSTITUTION, institution)
                .apply();
    }

    public String getUserId()      { return prefs.getString(KEY_USER_ID, ""); }
    public String getEmail()       { return prefs.getString(KEY_EMAIL, ""); }
    public String getName()        { return prefs.getString(KEY_NAME, ""); }
    public String getRole()        { return prefs.getString(KEY_ROLE, "researcher"); }
    public String getInstitution() { return prefs.getString(KEY_INSTITUTION, ""); }

    public boolean isAdmin() {
        return "admin".equalsIgnoreCase(getRole());
    }

    // ── Result Caching (for Notifications) ────────────────────────
    
    private static final String KEY_LATEST_PLAGIARISM = "latest_plagiarism";
    private static final String KEY_LATEST_REVIEW = "latest_review";

    public void saveLatestPlagiarismResult(String json) {
        prefs.edit().putString(KEY_LATEST_PLAGIARISM, json).apply();
    }

    public String getLatestPlagiarismResult() {
        return prefs.getString(KEY_LATEST_PLAGIARISM, null);
    }

    public void saveLatestPaperReviewResult(String json) {
        prefs.edit().putString(KEY_LATEST_REVIEW, json).apply();
    }

    public String getLatestPaperReviewResult() {
        return prefs.getString(KEY_LATEST_REVIEW, null);
    }

    // ── Logout ─────────────────────────────────────────────────────

    public void clearSession() {
        prefs.edit().clear().apply();
    }
}
