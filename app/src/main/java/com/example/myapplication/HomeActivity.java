package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.LayoutInflater;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import androidx.appcompat.app.AppCompatActivity;

import android.widget.ImageView;
import android.net.Uri;
import android.app.AlertDialog;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.content.Context;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import androidx.core.app.ActivityCompat;
import android.os.Build;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class HomeActivity extends AppCompatActivity {

    private SessionManager session;
    private TextView tvTotalPapers;
    private LinearLayout llRecentSubmissions;
    private TextView tvNoSubmissions;

    private ImageView ivProfile;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private float acceleration = 10f;
    private float currentAcceleration = SensorManager.GRAVITY_EARTH;
    private float lastAcceleration = SensorManager.GRAVITY_EARTH;
    private long lastShakeTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        session = new SessionManager(this);

        // Check login
        if (!session.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        tvTotalPapers = findViewById(R.id.tv_total_papers);
        llRecentSubmissions = findViewById(R.id.ll_recent_submissions);
        tvNoSubmissions = findViewById(R.id.tv_no_submissions);

        ivProfile = findViewById(R.id.iv_profile);

        // Settings / Profile popup on icon click
        if (ivProfile != null) {
            ivProfile.setOnClickListener(v -> showProfilePopup());
        }

        // Sensor Integration (Experiment 7)
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        // Request Notification Permission for Android 13+ (Experiment 8)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        setupCard(R.id.card_ai_detection, AiContentDetectionActivity.class);
        setupCard(R.id.card_grammar_check, GrammarCheckActivity.class);
        setupCard(R.id.card_plagiarism, PlagiarismDetectionActivity.class);
        setupCard(R.id.card_paraphrase, ParaphrasingActivity.class);
        setupCard(R.id.card_summarize, SummarizationActivity.class);
        setupCard(R.id.card_paper_review, PaperReviewActivity.class);
        setupCard(R.id.card_gestures, GesturePlaygroundActivity.class);

        // Setup toolbar
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        loadDashboardData();
    }

    private final SensorEventListener sensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            lastAcceleration = currentAcceleration;
            currentAcceleration = (float) Math.sqrt((double) (x * x + y * y + z * z));
            float delta = currentAcceleration - lastAcceleration;
            acceleration = acceleration * 0.9f + delta;

            if (acceleration > 12) {
                long currentTime = System.currentTimeMillis();
                if ((currentTime - lastShakeTime) > 2000) {
                    lastShakeTime = currentTime;
                    Intent serviceIntent = new Intent(HomeActivity.this, SensorNotificationService.class);
                    startService(serviceIntent);
                    Toast.makeText(HomeActivity.this, "Shake detected! Background service sent a notification.", Toast.LENGTH_SHORT).show();
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null && accelerometer != null) {
            sensorManager.unregisterListener(sensorEventListener);
        }
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

    private void setupCard(int cardId, final Class<?> destinationActivity) {
        View card = findViewById(cardId);
        if (card != null) {
            card.setOnClickListener(v -> {
                Intent intent = new Intent(HomeActivity.this, destinationActivity);
                startActivity(intent);
            });
        }
    }

    private void loadDashboardData() {
        String token = session.getToken();

        // Get Dashboard Stats
        ApiClient.getDashboardStats(token, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject json = new JSONObject(response);
                    if (json.has("total")) {
                        tvTotalPapers.setText(String.valueOf(json.getInt("total")));
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onError(int code, String error) {}
        });

        // Get Recent Submissions
        ApiClient.getRecentSubmissions(token, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(String response) {
                populateRecentSubmissions(response);
            }

            @Override
            public void onError(int code, String error) {
                if (tvNoSubmissions != null) {
                    tvNoSubmissions.setText("Unable to load submissions");
                }
            }
        });
    }

    /**
     * Settings popup showing user info and logout button.
     * Accessible via the profile icon in the toolbar.
     */
    private void showProfilePopup() {
        String name = session.getName();
        String email = session.getEmail();
        String role = session.getRole();
        String institution = session.getInstitution();

        StringBuilder info = new StringBuilder();
        info.append("Name: ").append(name != null && !name.isEmpty() ? name : "—").append("\n\n");
        info.append("Email: ").append(email != null && !email.isEmpty() ? email : "—").append("\n\n");
        info.append("Role: ").append(role != null && !role.isEmpty() ? capitalize(role) : "Researcher").append("\n\n");
        if (institution != null && !institution.isEmpty()) {
            info.append("Institution: ").append(institution).append("\n\n");
        }

        new AlertDialog.Builder(this)
                .setTitle("Account")
                .setMessage(info.toString().trim())
                .setPositiveButton("Close", null)
                .setNeutralButton("Logout", (dialog, which) -> {
                    session.clearSession();
                    Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(HomeActivity.this, LoginActivity.class));
                    finishAffinity();
                })
                .show();
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    private void populateRecentSubmissions(String jsonResponse) {
        try {
            JSONObject response = new JSONObject(jsonResponse);
            JSONArray array = response.optJSONArray("submissions");

            if (array == null || array.length() == 0) {
                if (tvNoSubmissions != null) {
                    tvNoSubmissions.setText("No recent submissions");
                }
                return;
            }

            if (tvNoSubmissions != null) tvNoSubmissions.setVisibility(View.GONE);
            llRecentSubmissions.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(this);

            for (int i = 0; i < array.length(); i++) {
                JSONObject sub = array.getJSONObject(i);

                View itemView = inflater.inflate(R.layout.item_recent_submission, llRecentSubmissions, false);

                TextView tvBadge = itemView.findViewById(R.id.tv_item_badge);
                TextView tvDate = itemView.findViewById(R.id.tv_item_date);
                TextView tvTitle = itemView.findViewById(R.id.tv_item_title);
                TextView tvDesc = itemView.findViewById(R.id.tv_item_desc);

                String featureParam = sub.optString("feature_type", "Unknown");
                String featureTypeFormat = featureParam.replace("_", " ").toUpperCase(Locale.ROOT);
                tvBadge.setText(featureTypeFormat);

                String dateStr = sub.optString("created_at", "");
                tvDate.setText(formatDate(dateStr));

                String name = sub.optString("file_name", "");
                if (name.isEmpty() || name.equals("null")) {
                    name = "Text Input (" + featureTypeFormat + ")";
                }
                tvTitle.setText(name);

                String submissionStatus = sub.optString("status", "pending");
                tvDesc.setText("Status: " + submissionStatus.substring(0, 1).toUpperCase(Locale.ROOT) + submissionStatus.substring(1));

                // Click to fetch full result from /submissions/{id} and display formatted
                final String submissionId = sub.optString("submission_id", "");
                final String finalName = name;
                final String finalFeatureType = featureParam;

                itemView.setOnClickListener(v -> {
                    if (submissionId.isEmpty()) {
                        Toast.makeText(this, "No submission ID available", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Toast.makeText(this, "Loading result…", Toast.LENGTH_SHORT).show();
                    String token = session.getToken();

                    ApiClient.getSubmissionDetail(submissionId, token, new ApiClient.ApiCallback() {
                        @Override
                        public void onSuccess(String detailResponse) {
                            try {
                                JSONObject detail = new JSONObject(detailResponse);
                                String formattedResult = formatSubmissionResult(detail, finalFeatureType);

                                ResultBottomSheetFragment.newInstance(finalName, formattedResult)
                                        .show(getSupportFragmentManager(), "result");
                            } catch (JSONException e) {
                                ResultBottomSheetFragment.newInstance(finalName, detailResponse)
                                        .show(getSupportFragmentManager(), "result");
                            }
                        }

                        @Override
                        public void onError(int code, String error) {
                            Toast.makeText(HomeActivity.this, "Could not load result: " + error, Toast.LENGTH_LONG).show();
                        }
                    });
                });

                llRecentSubmissions.addView(itemView);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Formats a full submission detail response (from /submissions/{id}) into clean markdown.
     * The response has: submission_id, feature_type, input_type, language, status, created_at,
     * completed_at, and a "result" object with feature-specific fields.
     */
    private String formatSubmissionResult(JSONObject detail, String featureType) {
        StringBuilder md = new StringBuilder();

        // Header
        String featureLabel = featureType.replace("_", " ");
        featureLabel = featureLabel.substring(0, 1).toUpperCase(Locale.ROOT) + featureLabel.substring(1);
        md.append("## ").append(featureLabel).append(" Result\n\n");

        String status = detail.optString("status", "unknown");
        md.append("**Status:** ").append(status.substring(0, 1).toUpperCase(Locale.ROOT)).append(status.substring(1)).append("\n\n");

        // Parse the "result" object
        JSONObject result = detail.optJSONObject("result");
        if (result == null) {
            md.append("_No detailed results available yet._\n");
            return md.toString();
        }

        md.append("---\n\n");

        switch (featureType) {
            case "ai_detection":
                formatAiDetection(md, result);
                break;
            case "grammar_check":
                formatGrammarCheck(md, result);
                break;
            case "paraphrase":
                formatParaphrase(md, result);
                break;
            case "plagiarism":
                formatPlagiarism(md, result);
                break;
            case "summarize":
                formatSummarize(md, result);
                break;
            case "paper_review":
                formatPaperReview(md, result);
                break;
            default:
                formatGenericResult(md, result);
                break;
        }

        return md.toString();
    }

    private void formatAiDetection(StringBuilder md, JSONObject r) {
        String verdict = r.optString("verdict", "");
        if (!verdict.isEmpty() && !verdict.equals("null")) {
            md.append("### Verdict\n\n").append(verdict).append("\n\n");
        }
        double aiProb = r.optDouble("ai_probability", -1);
        double humanProb = r.optDouble("human_probability", -1);
        double confidence = r.optDouble("confidence_score", -1);
        String model = r.optString("model_used", "");

        if (aiProb >= 0 || humanProb >= 0 || confidence >= 0) {
            md.append("### Analysis\n\n");
            if (aiProb >= 0) md.append(String.format(Locale.ROOT, "- **AI Probability:** %.1f%%\n", aiProb * 100));
            if (humanProb >= 0) md.append(String.format(Locale.ROOT, "- **Human Probability:** %.1f%%\n", humanProb * 100));
            if (confidence >= 0) md.append(String.format(Locale.ROOT, "- **Confidence Score:** %.1f%%\n", confidence * 100));
            if (!model.isEmpty()) md.append("- **Model Used:** ").append(model).append("\n");
        }
    }

    private void formatGrammarCheck(StringBuilder md, JSONObject r) {
        String corrected = r.optString("corrected_text", "");
        if (!corrected.isEmpty() && !corrected.equals("null")) {
            md.append("### Corrected Text\n\n").append(corrected).append("\n\n");
        }
        int errorCount = r.optInt("error_count", -1);
        double readability = r.optDouble("readability_score", -1);

        md.append("### Statistics\n\n");
        if (errorCount >= 0) md.append("- **Errors Found:** ").append(errorCount).append("\n");
        if (readability >= 0) md.append(String.format(Locale.ROOT, "- **Readability Score:** %.1f\n", readability));
    }

    private void formatParaphrase(StringBuilder md, JSONObject r) {
        String paraphrased = r.optString("paraphrased_text", "");
        if (!paraphrased.isEmpty() && !paraphrased.equals("null")) {
            md.append("### Paraphrased Text\n\n").append(paraphrased).append("\n\n");
        }
        double similarity = r.optDouble("similarity_score", -1);
        String mode = r.optString("mode", "");

        if (similarity >= 0 || (!mode.isEmpty() && !mode.equals("null"))) {
            md.append("### Statistics\n\n");
            if (similarity >= 0) md.append(String.format(Locale.ROOT, "- **Similarity:** %.1f%%\n", similarity));
            if (!mode.isEmpty() && !mode.equals("null")) md.append("- **Mode:** ").append(mode).append("\n");
        }
    }

    private void formatPlagiarism(StringBuilder md, JSONObject r) {
        double plagScore = r.optDouble("plagiarism_score", -1);
        double uniqueScore = r.optDouble("unique_score", -1);
        int totalWords = r.optInt("total_words", -1);
        int plagWords = r.optInt("plagiarized_words", -1);

        md.append("### Plagiarism Analysis\n\n");
        if (plagScore >= 0) md.append(String.format(Locale.ROOT, "- **Plagiarism Score:** %.1f%%\n", plagScore));
        if (uniqueScore >= 0) md.append(String.format(Locale.ROOT, "- **Unique Content:** %.1f%%\n", uniqueScore));
        if (totalWords >= 0) md.append("- **Total Words:** ").append(totalWords).append("\n");
        if (plagWords >= 0) md.append("- **Plagiarized Words:** ").append(plagWords).append("\n");

        // Matched sources
        try {
            org.json.JSONArray sources = r.optJSONArray("matched_sources");
            if (sources != null && sources.length() > 0) {
                md.append("\n### Matched Sources\n\n");
                for (int i = 0; i < sources.length(); i++) {
                    JSONObject source = sources.getJSONObject(i);
                    String title = source.optString("title", "Source " + (i + 1));
                    String url = source.optString("url", "");
                    double matchPct = source.optDouble("match_percent", 0);
                    String matchedText = source.optString("matched_text", "");

                    md.append("**").append(i + 1).append(". ").append(title).append("**\n");
                    if (!url.isEmpty()) md.append("   - URL: ").append(url).append("\n");
                    if (matchPct > 0) md.append(String.format(Locale.ROOT, "   - Match: %.1f%%\n", matchPct));
                    if (!matchedText.isEmpty() && !matchedText.equals("null")) {
                        md.append("   - Details: ").append(matchedText.length() > 200 ? matchedText.substring(0, 200) + "…" : matchedText).append("\n");
                    }
                    md.append("\n");
                }
            }
        } catch (JSONException ignored) {}
    }

    private void formatSummarize(StringBuilder md, JSONObject r) {
        String summary = r.optString("summary_text", "");
        if (!summary.isEmpty() && !summary.equals("null")) {
            md.append("### Summary\n\n").append(summary).append("\n\n");
        }
        int origLen = r.optInt("original_length", -1);
        int sumLen = r.optInt("summary_length", -1);
        double compression = r.optDouble("compression_rate", -1);
        String type = r.optString("summary_type", "");

        if (origLen >= 0 || sumLen >= 0 || compression >= 0) {
            md.append("### Statistics\n\n");
            if (origLen >= 0) md.append("- **Original Length:** ").append(origLen).append(" chars\n");
            if (sumLen >= 0) md.append("- **Summary Length:** ").append(sumLen).append(" chars\n");
            if (compression >= 0) md.append(String.format(Locale.ROOT, "- **Compression Rate:** %.1f%%\n", compression));
            if (!type.isEmpty() && !type.equals("null")) md.append("- **Type:** ").append(type).append("\n");
        }
    }

    private void formatPaperReview(StringBuilder md, JSONObject r) {
        double score = r.optDouble("overall_score", -1);
        if (score >= 0) {
            md.append(String.format(Locale.ROOT, "### Overall Score: %.1f/10\n\n", score));
        }

        String recommendation = r.optString("recommendation", "");
        if (!recommendation.isEmpty() && !recommendation.equals("null")) {
            md.append("**Recommendation:** ").append(recommendation).append("\n\n");
        }

        // Review sections
        String[] sections = {"abstract_review", "methodology_review", "literature_review", "results_review", "conclusion_review"};
        String[] labels = {"Abstract Review", "Methodology Review", "Literature Review", "Results Review", "Conclusion Review"};
        for (int i = 0; i < sections.length; i++) {
            String text = r.optString(sections[i], "");
            if (!text.isEmpty() && !text.equals("null")) {
                md.append("### ").append(labels[i]).append("\n\n").append(text).append("\n\n");
            }
        }

        String citationsQuality = r.optString("citations_quality", "");
        if (!citationsQuality.isEmpty() && !citationsQuality.equals("null")) {
            md.append("- **Citations Quality:** ").append(citationsQuality).append("\n");
        }
    }

    /**
     * Fallback for unknown feature types — formats any JSON keys as markdown.
     */
    private void formatGenericResult(StringBuilder md, JSONObject r) {
        java.util.Iterator<String> keys = r.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            // Skip internal IDs
            if (key.endsWith("_id") || key.equals("created_at")) continue;

            String label = key.replace("_", " ");
            label = label.substring(0, 1).toUpperCase(Locale.ROOT) + label.substring(1);

            Object val = r.opt(key);
            if (val == null || val.toString().equals("null")) continue;

            String valStr = val.toString();
            if (valStr.length() > 300) {
                md.append("### ").append(label).append("\n\n").append(valStr).append("\n\n");
            } else {
                md.append("- **").append(label).append(":** ").append(valStr).append("\n");
            }
        }
    }

    private String formatDate(String dateStr) {
        if (dateStr.isEmpty() || dateStr.equals("null")) return "";
        try {
            SimpleDateFormat inFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.ROOT);
            inFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = inFormat.parse(dateStr);
            if (date == null) return dateStr;

            SimpleDateFormat outFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.ROOT);
            return outFormat.format(date).toUpperCase(Locale.ROOT);
        } catch (ParseException e) {
            // Try without microseconds
            try {
                SimpleDateFormat inFormat2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);
                inFormat2.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date date2 = inFormat2.parse(dateStr);
                if (date2 != null) {
                    SimpleDateFormat outFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.ROOT);
                    return outFormat.format(date2).toUpperCase(Locale.ROOT);
                }
            } catch (ParseException ignored) {}
            return dateStr.length() > 10 ? dateStr.substring(0, 10) : dateStr;
        }
    }
}
