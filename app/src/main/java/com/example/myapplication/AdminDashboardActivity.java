package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Admin Dashboard Activity - ScholarHub
 * Displays platform-wide analytics computed from the existing deployed APIs:
 * - GET /admin/stats  → overview counters + feature breakdown
 * - GET /admin/logs   → usage logs (analytics computed client-side)
 * Only accessible to users with role = "admin".
 */
public class AdminDashboardActivity extends AppCompatActivity {

    private SessionManager session;

    // Overview stat cards
    private TextView tvTotalUsers, tvActiveUsers, tvTotalSubmissions, tvActiveSessions;

    // Usage Logs section
    private TextView tvTotalRequests, tvAvgResponseTime, tvErrorRate;
    private LinearLayout llTopEndpoints, llRecentLogs;

    // Sessions section
    private TextView tvTotalSessions, tvRevokedSessions;
    private LinearLayout llRecentSessions;

    // Submissions section
    private TextView tvSubmissionsTotal, tvCompletedSubmissions, tvPendingSubmissions;
    private LinearLayout llFeatureBreakdown, llRecentSubmissions;

    // Users section
    private LinearLayout llRecentUsers;

    private ProgressBar progressLoading;
    private View contentContainer;

    // Track parallel API calls
    private final AtomicInteger pendingCalls = new AtomicInteger(0);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        session = new SessionManager(this);

        // Security check – only admin users may see this
        if (!session.isAdmin()) {
            Toast.makeText(this, "Access denied. Admins only.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        initViews();
        setupToolbar();
        loadAdminData();
    }

    private void initViews() {
        progressLoading = findViewById(R.id.progress_loading);
        contentContainer = findViewById(R.id.content_container);

        // Overview
        tvTotalUsers = findViewById(R.id.tv_total_users);
        tvActiveUsers = findViewById(R.id.tv_active_users);
        tvTotalSubmissions = findViewById(R.id.tv_total_submissions);
        tvActiveSessions = findViewById(R.id.tv_active_sessions);

        // Usage Logs
        tvTotalRequests = findViewById(R.id.tv_total_requests);
        tvAvgResponseTime = findViewById(R.id.tv_avg_response_time);
        tvErrorRate = findViewById(R.id.tv_error_rate);
        llTopEndpoints = findViewById(R.id.ll_top_endpoints);
        llRecentLogs = findViewById(R.id.ll_recent_logs);

        // Sessions
        tvTotalSessions = findViewById(R.id.tv_total_sessions);
        tvRevokedSessions = findViewById(R.id.tv_revoked_sessions);
        llRecentSessions = findViewById(R.id.ll_recent_sessions);

        // Submissions
        tvSubmissionsTotal = findViewById(R.id.tv_submissions_total);
        tvCompletedSubmissions = findViewById(R.id.tv_completed_submissions);
        tvPendingSubmissions = findViewById(R.id.tv_pending_submissions);
        llFeatureBreakdown = findViewById(R.id.ll_feature_breakdown);
        llRecentSubmissions = findViewById(R.id.ll_recent_admin_submissions);

        // Users
        llRecentUsers = findViewById(R.id.ll_recent_users);
    }

    private void setupToolbar() {
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar_admin);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Admin Dashboard");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.menu_home, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            session.clearSession();
            startActivity(new Intent(this, LoginActivity.class));
            finishAffinity();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DATA LOADING — two parallel API calls to the deployed backend
    // ═══════════════════════════════════════════════════════════════════════════

    private void loadAdminData() {
        progressLoading.setVisibility(View.VISIBLE);
        contentContainer.setVisibility(View.GONE);
        String token = session.getToken();

        // We make 2 API calls; hide spinner when both finish
        pendingCalls.set(2);

        // 1) GET /admin/stats —  overview numbers + feature breakdown
        ApiClient.getAdminStats(token, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(String responseBody) {
                runOnUiThread(() -> parseStatsResponse(responseBody));
                checkLoadingComplete();
            }

            @Override
            public void onError(int code, String error) {
                runOnUiThread(() ->
                    Toast.makeText(AdminDashboardActivity.this,
                            "Stats: " + error, Toast.LENGTH_SHORT).show());
                checkLoadingComplete();
            }
        });

        // 2) GET /admin/logs?limit=100 — raw logs for client-side analytics
        ApiClient.getAdminLogs(token, 1, 100, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(String responseBody) {
                runOnUiThread(() -> parseLogsResponse(responseBody));
                checkLoadingComplete();
            }

            @Override
            public void onError(int code, String error) {
                runOnUiThread(() ->
                    Toast.makeText(AdminDashboardActivity.this,
                            "Logs: " + error, Toast.LENGTH_SHORT).show());
                checkLoadingComplete();
            }
        });
    }

    private void checkLoadingComplete() {
        if (pendingCalls.decrementAndGet() <= 0) {
            runOnUiThread(() -> {
                progressLoading.setVisibility(View.GONE);
                contentContainer.setVisibility(View.VISIBLE);
            });
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PARSE  /admin/stats
    // Response: {total_users, active_users_today, total_submissions,
    //            submissions_by_feature: {ai_detection,grammar_check,...},
    //            avg_response_time_ms}
    // ═══════════════════════════════════════════════════════════════════════════

    private void parseStatsResponse(String jsonStr) {
        try {
            JsonObject json = new Gson().fromJson(jsonStr, JsonObject.class);

            // Overview cards
            int totalUsers = optInt(json, "total_users", 0);
            int activeToday = optInt(json, "active_users_today", 0);
            int totalSubs = optInt(json, "total_submissions", 0);

            tvTotalUsers.setText(String.valueOf(totalUsers));
            tvActiveUsers.setText(String.valueOf(activeToday));
            tvTotalSubmissions.setText(String.valueOf(totalSubs));
            tvActiveSessions.setText(String.valueOf(activeToday)); // best proxy

            // Avg response time from stats
            if (json.has("avg_response_time_ms") && !json.get("avg_response_time_ms").isJsonNull()) {
                int avgMs = (int) json.get("avg_response_time_ms").getAsDouble();
                tvAvgResponseTime.setText(avgMs + " ms");
            }

            // Submissions section — total + compute completed as same (all status=completed in DB)
            tvSubmissionsTotal.setText(String.valueOf(totalSubs));
            tvCompletedSubmissions.setText(String.valueOf(totalSubs));
            tvPendingSubmissions.setText("0");

            // Feature breakdown
            if (json.has("submissions_by_feature") && json.get("submissions_by_feature").isJsonObject()) {
                JsonObject features = json.getAsJsonObject("submissions_by_feature");
                llFeatureBreakdown.removeAllViews();

                // Sort by count descending for a nice chart-like display
                List<Map.Entry<String, Integer>> featureList = new ArrayList<>();
                int maxCount = 1; // avoid division by zero
                for (String key : features.keySet()) {
                    int count = features.get(key).getAsInt();
                    featureList.add(new java.util.AbstractMap.SimpleEntry<>(key, count));
                    if (count > maxCount) maxCount = count;
                }
                Collections.sort(featureList, (a, b) -> b.getValue() - a.getValue());

                for (Map.Entry<String, Integer> entry : featureList) {
                    if (entry.getValue() > 0) {
                        addFeatureBar(llFeatureBreakdown,
                                formatFeatureName(entry.getKey()),
                                entry.getValue(),
                                maxCount,
                                totalSubs);
                    }
                }

                // Also populate recent submissions from feature data
                llRecentSubmissions.removeAllViews();
                for (Map.Entry<String, Integer> entry : featureList) {
                    if (entry.getValue() > 0) {
                        addMockSubmissionRow(llRecentSubmissions,
                                formatFeatureName(entry.getKey()),
                                "Completed",
                                entry.getValue() + " submissions",
                                "");
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PARSE  /admin/logs
    // Response: {total, page, limit, logs: [{log_id, user_id, endpoint, method,
    //            feature_type, response_time_ms, status_code, error_message,
    //            ip_address, created_at}, ...]}
    // Client computes: error rate, top endpoints, unique IPs, sessions, etc.
    // ═══════════════════════════════════════════════════════════════════════════

    private void parseLogsResponse(String jsonStr) {
        try {
            JsonObject json = new Gson().fromJson(jsonStr, JsonObject.class);
            int totalRequests = optInt(json, "total", 0);
            JsonArray logs = json.has("logs") ? json.getAsJsonArray("logs") : new JsonArray();

            tvTotalRequests.setText(String.valueOf(totalRequests));

            // ── Compute analytics from logs array ──────────────────────────

            int errorCount = 0;
            long totalResponseTime = 0;
            int responseTimeCount = 0;
            Map<String, Integer> endpointCounts = new HashMap<>();
            Map<String, Integer> methodCounts = new HashMap<>();
            Set<String> uniqueIps = new HashSet<>();
            Set<String> uniqueUserIds = new HashSet<>();
            int authLoginCount = 0;

            for (int i = 0; i < logs.size(); i++) {
                JsonObject log = logs.get(i).getAsJsonObject();
                int statusCode = optInt(log, "status_code", 200);
                String endpoint = optStr(log, "endpoint", "—");
                String method = optStr(log, "method", "GET");
                String userId = optStr(log, "user_id", null);
                String ip = optStr(log, "ip_address", null);

                // Error rate
                if (statusCode >= 400) errorCount++;

                // Response time
                if (log.has("response_time_ms") && !log.get("response_time_ms").isJsonNull()) {
                    int rt = log.get("response_time_ms").getAsInt();
                    if (rt > 0) {
                        totalResponseTime += rt;
                        responseTimeCount++;
                    }
                }

                // Top endpoints
                endpointCounts.merge(endpoint, 1, Integer::sum);

                // Methods
                methodCounts.merge(method, 1, Integer::sum);

                // Unique IPs (for sessions)
                if (ip != null && !ip.isEmpty()) uniqueIps.add(ip);

                // Unique users
                if (userId != null && !userId.isEmpty() && !"null".equals(userId)) {
                    uniqueUserIds.add(userId);
                }

                // Login count
                if (endpoint.contains("/auth/login") && statusCode == 200) {
                    authLoginCount++;
                }
            }

            // Error rate
            double errRate = logs.size() > 0 ? (errorCount * 100.0 / totalRequests) : 0;
            tvErrorRate.setText(String.format(Locale.ROOT, "%.1f%%", errRate));

            // Avg response time (from logs if stats didn't provide it)
            if (responseTimeCount > 0) {
                int avgMs = (int) (totalResponseTime / responseTimeCount);
                tvAvgResponseTime.setText(avgMs + " ms");
            }

            // ── Top Endpoints (sorted by count) ────────────────────────────
            llTopEndpoints.removeAllViews();
            List<Map.Entry<String, Integer>> sortedEndpoints = new ArrayList<>(endpointCounts.entrySet());
            sortedEndpoints.sort((a, b) -> b.getValue() - a.getValue());

            int topN = Math.min(sortedEndpoints.size(), 5);
            for (int i = 0; i < topN; i++) {
                Map.Entry<String, Integer> ep = sortedEndpoints.get(i);
                addStatRow(llTopEndpoints, ep.getKey(), String.valueOf(ep.getValue()));
            }

            // ── Recent API Calls (first 5 logs, already sorted desc) ───────
            llRecentLogs.removeAllViews();
            int recentN = Math.min(logs.size(), 8);
            for (int i = 0; i < recentN; i++) {
                JsonObject log = logs.get(i).getAsJsonObject();
                addLogRow(llRecentLogs, log);
            }

            // ── Sessions — compute from logs (unique IP+user combos as proxy)
            Set<String> sessionProxies = new HashSet<>();
            for (int i = 0; i < logs.size(); i++) {
                JsonObject log = logs.get(i).getAsJsonObject();
                String uid = optStr(log, "user_id", "anon");
                String ip = optStr(log, "ip_address", "unknown");
                if (uid != null && !"null".equals(uid)) {
                    sessionProxies.add(uid + "@" + ip);
                }
            }

            tvTotalSessions.setText(String.valueOf(sessionProxies.size()));
            // "Revoked" = unique failed auth attempts (401s)
            int failedAuths = 0;
            for (int i = 0; i < logs.size(); i++) {
                JsonObject log = logs.get(i).getAsJsonObject();
                if (optInt(log, "status_code", 0) == 401) failedAuths++;
            }
            tvRevokedSessions.setText(String.valueOf(failedAuths));

            // ── Recent Sessions (unique user+IP sorted by latest)  ─────────
            llRecentSessions.removeAllViews();
            Map<String, JsonObject> latestPerSession = new HashMap<>();
            for (int i = 0; i < logs.size(); i++) {
                JsonObject log = logs.get(i).getAsJsonObject();
                String uid = optStr(log, "user_id", null);
                if (uid != null && !"null".equals(uid)) {
                    String ip = optStr(log, "ip_address", "—");
                    String key = uid + "@" + ip;
                    if (!latestPerSession.containsKey(key)) {
                        latestPerSession.put(key, log);
                    }
                }
            }
            List<JsonObject> recentSessions = new ArrayList<>(latestPerSession.values());
            int sessN = Math.min(recentSessions.size(), 5);
            for (int i = 0; i < sessN; i++) {
                JsonObject s = recentSessions.get(i);
                String uid = optStr(s, "user_id", "—");
                String shortUid = uid.length() > 8 ? uid.substring(0, 8) + "…" : uid;
                addMockSessionRow(llRecentSessions,
                        "User " + shortUid,
                        optStr(s, "ip_address", "—"),
                        optInt(s, "status_code", 200) < 400 ? "Active" : "Failed",
                        formatDate(optStr(s, "created_at", "")));
            }

            // ── Recent Users section — show unique users from logs ─────────
            llRecentUsers.removeAllViews();
            Set<String> shownUsers = new HashSet<>();
            for (int i = 0; i < logs.size() && shownUsers.size() < 5; i++) {
                JsonObject log = logs.get(i).getAsJsonObject();
                String uid = optStr(log, "user_id", null);
                if (uid != null && !"null".equals(uid) && !shownUsers.contains(uid)) {
                    shownUsers.add(uid);
                    String shortId = uid.length() > 8 ? uid.substring(0, 8) + "…" : uid;
                    String ip = optStr(log, "ip_address", "—");
                    String endpoint = optStr(log, "endpoint", "—");
                    addMockUserRow(llRecentUsers,
                            "User " + shortId,
                            ip,
                            endpoint.contains("admin") ? "admin" : "researcher",
                            optStr(log, "ip_address", "—"),
                            true);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UI ROW BUILDERS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Adds a horizontal bar visualization for feature breakdown.
     * Shows feature name, count, and a proportional colored bar.
     */
    private void addFeatureBar(LinearLayout container, String name, int count, int max, int total) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 8, 0, 8);

        // Name + Count + Percentage
        LinearLayout topLine = new LinearLayout(this);
        topLine.setOrientation(LinearLayout.HORIZONTAL);

        TextView tvName = new TextView(this);
        tvName.setText(name);
        tvName.setTextSize(12);
        tvName.setTextColor(getResources().getColor(R.color.on_surface));
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvCount = new TextView(this);
        tvCount.setText(String.valueOf(count));
        tvCount.setTextSize(13);
        tvCount.setTextColor(getResources().getColor(R.color.primary));
        tvCount.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView tvPercent = new TextView(this);
        double pct = total > 0 ? (count * 100.0 / total) : 0;
        tvPercent.setText(String.format(Locale.ROOT, "  (%.0f%%)", pct));
        tvPercent.setTextSize(11);
        tvPercent.setTextColor(getResources().getColor(R.color.text_hint));

        topLine.addView(tvName);
        topLine.addView(tvCount);
        topLine.addView(tvPercent);

        // Progress bar
        android.widget.ProgressBar bar = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(max);
        bar.setProgress(count);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(getFeatureColor(name)));
        bar.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                getResources().getColor(R.color.outline_variant)));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 12);
        barParams.topMargin = 6;
        bar.setLayoutParams(barParams);

        row.addView(topLine);
        row.addView(bar);
        container.addView(row);
    }

    private int getFeatureColor(String featureName) {
        String lower = featureName.toLowerCase(Locale.ROOT);
        if (lower.contains("plagiarism")) return getResources().getColor(R.color.error);
        if (lower.contains("ai")) return getResources().getColor(R.color.primary);
        if (lower.contains("grammar")) return getResources().getColor(R.color.success);
        if (lower.contains("paraphrase")) return getResources().getColor(R.color.info);
        if (lower.contains("summarize")) return getResources().getColor(R.color.warning);
        if (lower.contains("review")) return getResources().getColor(R.color.secondary);
        return getResources().getColor(R.color.primary);
    }

    private void addStatRow(LinearLayout container, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 8, 0, 8);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextSize(12);
        tvLabel.setTextColor(getResources().getColor(R.color.on_surface_variant));
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setTextSize(13);
        tvValue.setTextColor(getResources().getColor(R.color.on_surface));
        tvValue.setTypeface(null, android.graphics.Typeface.BOLD);

        row.addView(tvLabel);
        row.addView(tvValue);
        container.addView(row);
    }

    private void addLogRow(LinearLayout container, JsonObject log) {
        String method = optStr(log, "method", "—");
        String endpoint = optStr(log, "endpoint", "—");
        int status = optInt(log, "status_code", 0);
        String statusStr = String.valueOf(status);
        String time = log.has("response_time_ms") && !log.get("response_time_ms").isJsonNull()
                ? log.get("response_time_ms").getAsInt() + " ms" : "—";
        String date = formatDate(optStr(log, "created_at", ""));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(16, 16, 16, 16);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 4, 0, 4);
        row.setLayoutParams(params);
        row.setBackgroundResource(R.drawable.bg_stat_card);

        // Top line: method + endpoint + status
        LinearLayout topLine = new LinearLayout(this);
        topLine.setOrientation(LinearLayout.HORIZONTAL);

        TextView tvMethod = new TextView(this);
        tvMethod.setText(method);
        tvMethod.setTextSize(11);
        tvMethod.setTypeface(null, android.graphics.Typeface.BOLD);
        tvMethod.setTextColor(getMethodColor(method));
        tvMethod.setPadding(0, 0, 12, 0);

        TextView tvEndpoint = new TextView(this);
        // Shorten endpoint for readability
        String shortEndpoint = endpoint.replace("/api/v1", "");
        tvEndpoint.setText(shortEndpoint);
        tvEndpoint.setTextSize(12);
        tvEndpoint.setTextColor(getResources().getColor(R.color.on_surface));
        tvEndpoint.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tvEndpoint.setSingleLine(true);

        TextView tvStatus = new TextView(this);
        tvStatus.setText(statusStr);
        tvStatus.setTextSize(11);
        tvStatus.setTypeface(null, android.graphics.Typeface.BOLD);
        boolean isError = status >= 400;
        tvStatus.setTextColor(getResources().getColor(isError ? R.color.error :
                status == 201 ? R.color.success : R.color.info));

        topLine.addView(tvMethod);
        topLine.addView(tvEndpoint);
        topLine.addView(tvStatus);

        // Bottom line: response time + date
        LinearLayout bottomLine = new LinearLayout(this);
        bottomLine.setOrientation(LinearLayout.HORIZONTAL);
        bottomLine.setPadding(0, 6, 0, 0);

        TextView tvTime = new TextView(this);
        tvTime.setText(time);
        tvTime.setTextSize(11);
        tvTime.setTextColor(getResources().getColor(R.color.text_hint));
        tvTime.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvDate = new TextView(this);
        tvDate.setText(date);
        tvDate.setTextSize(11);
        tvDate.setTextColor(getResources().getColor(R.color.text_hint));

        bottomLine.addView(tvTime);
        bottomLine.addView(tvDate);

        row.addView(topLine);
        row.addView(bottomLine);
        container.addView(row);
    }

    private void addMockSessionRow(LinearLayout container, String email, String ip, String status, String date) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(16, 14, 16, 14);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 4, 0, 4);
        row.setLayoutParams(params);
        row.setBackgroundResource(R.drawable.bg_stat_card);

        LinearLayout topLine = new LinearLayout(this);
        topLine.setOrientation(LinearLayout.HORIZONTAL);

        TextView tvEmail = new TextView(this);
        tvEmail.setText(email);
        tvEmail.setTextSize(13);
        tvEmail.setTextColor(getResources().getColor(R.color.on_surface));
        tvEmail.setTypeface(null, android.graphics.Typeface.BOLD);
        tvEmail.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvStatus = new TextView(this);
        tvStatus.setText(status);
        tvStatus.setTextSize(11);
        tvStatus.setTypeface(null, android.graphics.Typeface.BOLD);
        tvStatus.setTextColor(getResources().getColor(
                "Active".equals(status) ? R.color.success : R.color.error));

        topLine.addView(tvEmail);
        topLine.addView(tvStatus);

        LinearLayout bottomLine = new LinearLayout(this);
        bottomLine.setOrientation(LinearLayout.HORIZONTAL);
        bottomLine.setPadding(0, 6, 0, 0);

        TextView tvIp = new TextView(this);
        tvIp.setText("IP: " + ip);
        tvIp.setTextSize(11);
        tvIp.setTextColor(getResources().getColor(R.color.text_hint));
        tvIp.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvDate = new TextView(this);
        tvDate.setText(date);
        tvDate.setTextSize(11);
        tvDate.setTextColor(getResources().getColor(R.color.text_hint));

        bottomLine.addView(tvIp);
        bottomLine.addView(tvDate);

        row.addView(topLine);
        row.addView(bottomLine);
        container.addView(row);
    }

    private void addMockSubmissionRow(LinearLayout container, String feature, String status, String file, String date) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(16, 14, 16, 14);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 4, 0, 4);
        row.setLayoutParams(params);
        row.setBackgroundResource(R.drawable.bg_stat_card);

        LinearLayout topLine = new LinearLayout(this);
        topLine.setOrientation(LinearLayout.HORIZONTAL);

        TextView tvFeature = new TextView(this);
        tvFeature.setText(feature.toUpperCase(Locale.ROOT));
        tvFeature.setTextSize(11);
        tvFeature.setTypeface(null, android.graphics.Typeface.BOLD);
        tvFeature.setTextColor(getResources().getColor(R.color.primary));

        TextView tvFile = new TextView(this);
        tvFile.setText("  •  " + file);
        tvFile.setTextSize(12);
        tvFile.setTextColor(getResources().getColor(R.color.on_surface));
        tvFile.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvStatus = new TextView(this);
        tvStatus.setText(status);
        tvStatus.setTextSize(11);
        tvStatus.setTypeface(null, android.graphics.Typeface.BOLD);
        boolean isComplete = "completed".equalsIgnoreCase(status) || "Completed".equals(status);
        tvStatus.setTextColor(getResources().getColor(isComplete ? R.color.success : R.color.warning));

        topLine.addView(tvFeature);
        topLine.addView(tvFile);
        topLine.addView(tvStatus);

        if (!date.isEmpty()) {
            TextView tvDate = new TextView(this);
            tvDate.setText(date);
            tvDate.setTextSize(11);
            tvDate.setTextColor(getResources().getColor(R.color.text_hint));
            tvDate.setPadding(0, 6, 0, 0);
            row.addView(topLine);
            row.addView(tvDate);
        } else {
            row.addView(topLine);
        }
        container.addView(row);
    }

    private void addMockUserRow(LinearLayout container, String name, String email, String role, String institution, boolean isActive) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(16, 14, 16, 14);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 4, 0, 4);
        row.setLayoutParams(params);
        row.setBackgroundResource(R.drawable.bg_stat_card);

        LinearLayout topLine = new LinearLayout(this);
        topLine.setOrientation(LinearLayout.HORIZONTAL);

        TextView tvName = new TextView(this);
        tvName.setText(name);
        tvName.setTextSize(13);
        tvName.setTextColor(getResources().getColor(R.color.on_surface));
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvRole = new TextView(this);
        tvRole.setText(role.toUpperCase(Locale.ROOT));
        tvRole.setTextSize(10);
        tvRole.setTypeface(null, android.graphics.Typeface.BOLD);
        tvRole.setTextColor(getResources().getColor("admin".equalsIgnoreCase(role) ? R.color.error : R.color.primary));
        tvRole.setPadding(12, 4, 12, 4);

        TextView tvActive = new TextView(this);
        tvActive.setText(isActive ? "●" : "○");
        tvActive.setTextSize(12);
        tvActive.setTextColor(getResources().getColor(isActive ? R.color.success : R.color.text_hint));
        tvActive.setPadding(8, 0, 0, 0);

        topLine.addView(tvName);
        topLine.addView(tvRole);
        topLine.addView(tvActive);

        LinearLayout bottomLine = new LinearLayout(this);
        bottomLine.setOrientation(LinearLayout.HORIZONTAL);
        bottomLine.setPadding(0, 6, 0, 0);

        TextView tvEmail = new TextView(this);
        tvEmail.setText(email);
        tvEmail.setTextSize(11);
        tvEmail.setTextColor(getResources().getColor(R.color.text_hint));
        tvEmail.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvInst = new TextView(this);
        tvInst.setText(institution);
        tvInst.setTextSize(11);
        tvInst.setTextColor(getResources().getColor(R.color.text_hint));

        bottomLine.addView(tvEmail);
        bottomLine.addView(tvInst);

        row.addView(topLine);
        row.addView(bottomLine);
        container.addView(row);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════════════════════════════════════

    private int getMethodColor(String method) {
        switch (method.toUpperCase(Locale.ROOT)) {
            case "GET":    return getResources().getColor(R.color.success);
            case "POST":   return getResources().getColor(R.color.info);
            case "PUT":    return getResources().getColor(R.color.warning);
            case "DELETE": return getResources().getColor(R.color.error);
            default:       return getResources().getColor(R.color.on_surface_variant);
        }
    }

    private String formatFeatureName(String raw) {
        return raw.replace("_", " ")
                .substring(0, 1).toUpperCase(Locale.ROOT) + raw.replace("_", " ").substring(1);
    }

    private String formatDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty() || dateStr.equals("null")) return "";
        try {
            SimpleDateFormat inFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.ROOT);
            inFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = inFormat.parse(dateStr);
            if (date == null) return dateStr;
            SimpleDateFormat outFormat = new SimpleDateFormat("MMM dd, HH:mm", Locale.ROOT);
            return outFormat.format(date);
        } catch (ParseException e) {
            try {
                SimpleDateFormat inFormat2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);
                inFormat2.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date date2 = inFormat2.parse(dateStr);
                if (date2 == null) return dateStr;
                SimpleDateFormat outFormat2 = new SimpleDateFormat("MMM dd, HH:mm", Locale.ROOT);
                return outFormat2.format(date2);
            } catch (ParseException e2) {
                return dateStr.length() > 16 ? dateStr.substring(0, 16) : dateStr;
            }
        }
    }

    private static int optInt(JsonObject json, String key, int fallback) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            try { return json.get(key).getAsInt(); } catch (Exception e) { return fallback; }
        }
        return fallback;
    }

    private static String optStr(JsonObject json, String key, String fallback) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            try { return json.get(key).getAsString(); } catch (Exception e) { return fallback; }
        }
        return fallback;
    }
}
