package com.example.myapplication;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;

public class PlagiarismDetectionActivity extends AppCompatActivity {

    private LinearLayout llInitialState, llScanningState, llResultsState;
    private Button btnUpload;
    private TextView tvOverallSimilarity;
    private ProgressBar progressOverallSimilarity;
    private MaterialButtonToggleGroup modelToggle;
    private SessionManager session;
    private File selectedPdf;


    private final ActivityResultLauncher<Intent> pdfPicker =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        selectedPdf = copyUriToTempFile(uri);
                        if (selectedPdf != null) {
                            startPlagiarismDetection();
                        }
                    }
                }
            });

    private boolean isScanning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plagiarism_detection);

        session = new SessionManager(this);

        // Security: Check login
        if (!session.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
        }

        llInitialState = findViewById(R.id.ll_initial_state);
        llScanningState = findViewById(R.id.ll_scanning_state);
        llResultsState = findViewById(R.id.ll_results);
        tvOverallSimilarity = findViewById(R.id.tv_overall_similarity);
        progressOverallSimilarity = findViewById(R.id.progress_overall_similarity);
        
        View selectorView = findViewById(R.id.model_selector);
        if (selectorView != null) {
            modelToggle = selectorView.findViewById(R.id.model_toggle_group);
        }
        
        btnUpload = findViewById(R.id.btn_upload_paper_initial);

        // Check if we are coming from a notification to show results
        if (getIntent().getBooleanExtra("open_latest", false)) {
            String cached = session.getLatestPlagiarismResult();
            if (cached != null) {
                renderResults(cached);
                Toast.makeText(this, "Showing latest report", Toast.LENGTH_SHORT).show();
            } else {
                llInitialState.setVisibility(View.VISIBLE);
                llScanningState.setVisibility(View.GONE);
                llResultsState.setVisibility(View.GONE);
            }
        } else {
            llInitialState.setVisibility(View.VISIBLE);
            llScanningState.setVisibility(View.GONE);
            llResultsState.setVisibility(View.GONE);
        }

        btnUpload.setOnClickListener(v -> {
            if (!isScanning) {
                openPdfPicker();
            }
        });
    }

    private void openPdfPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/pdf");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        pdfPicker.launch(Intent.createChooser(intent, "Select a PDF"));
    }

    private void startPlagiarismDetection() {
        if (isScanning) return;
        isScanning = true;
        
        llInitialState.setVisibility(View.GONE);
        llScanningState.setVisibility(View.VISIBLE);
        llResultsState.setVisibility(View.GONE);
        btnUpload.setEnabled(false);

        // Call HuggingFace endpoint (only sends the file)
        ApiClient.callHuggingFacePlagiarism(selectedPdf, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(String hfBody) {
                isScanning = false;
                btnUpload.setEnabled(true);
                
                // Save results for later (notifications)
                session.saveLatestPlagiarismResult(hfBody);
                
                // To keep history in DB, we call the backend submit/plagiarism as well.
                // The main results displayed come from HuggingFace.
                String token = session.getToken();
                String modelType = (modelToggle != null && modelToggle.getCheckedButtonId() == R.id.btn_model_advanced) ? "advanced" : "fast";
                
                ApiClient.detectPlagiarism(selectedPdf, token, modelType, new ApiClient.ApiCallback() {
                    @Override
                    public void onSuccess(String backendBody) {
                        renderResults(hfBody);
                    }

                    @Override
                    public void onError(int code, String error) {
                        // Log locally but still render HF results
                        renderResults(hfBody);
                    }
                });
            }

            @Override
            public void onError(int code, String error) {
                isScanning = false;
                btnUpload.setEnabled(true);
                llScanningState.setVisibility(View.GONE);
                llInitialState.setVisibility(View.VISIBLE);
                Toast.makeText(PlagiarismDetectionActivity.this, "Scan Failed: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void renderResults(String responseBody) {
        llScanningState.setVisibility(View.GONE);
        llResultsState.setVisibility(View.VISIBLE);

        try {
            JsonObject json = new Gson().fromJson(responseBody, JsonObject.class);
            
            // Extract core scores from 'summary' object
            double score = 0;
            String severity = "Unknown";
            String academicRisk = "";
            
            if (json.has("summary") && json.get("summary").isJsonObject()) {
                JsonObject summary = json.getAsJsonObject("summary");
                score = summary.has("overall_plagiarism_score") ? summary.get("overall_plagiarism_score").getAsDouble() : 0;
                severity = summary.has("severity_level") ? summary.get("severity_level").getAsString() : "Unknown";
                academicRisk = summary.has("academic_integrity_risk") ? summary.get("academic_integrity_risk").getAsString() : "";
            } else if (json.has("plagiarism_score")) {
                score = json.get("plagiarism_score").getAsDouble();
            }

            tvOverallSimilarity.setText(String.format(Locale.getDefault(), "%.1f%%", score));
            progressOverallSimilarity.setProgress((int) score);

            // Trigger In-App Notification
            NotificationHelper.showPlagiarismReportNotification(this, selectedPdf.getName(), score, severity);

            StringBuilder report = new StringBuilder();
            report.append("## Plagiarism Detailed Report\n\n");
            report.append(String.format(Locale.getDefault(), "### Similarity Score: %.2f%%\n", score));
            report.append("**Severity Level:** ").append(severity).append("\n");
            if (!academicRisk.isEmpty()) {
                report.append("**Risk Status:** ").append(academicRisk).append("\n");
            }
            report.append("\n---\n\n");

            // Executive Summary
            if (json.has("executive_summary")) {
                report.append(json.get("executive_summary").getAsString()).append("\n\n");
            }

            // Key Findings
            if (json.has("key_findings") && json.get("key_findings").isJsonArray()) {
                JsonArray findings = json.getAsJsonArray("key_findings");
                if (findings.size() > 0) {
                    report.append("### Key Findings\n\n");
                    for (JsonElement element : findings) {
                        report.append("• ").append(element.getAsString()).append("\n");
                    }
                    report.append("\n");
                }
            }

            // Plagiarism Breakdown
            if (json.has("plagiarism_breakdown") && json.get("plagiarism_breakdown").isJsonObject()) {
                JsonObject breakdown = json.getAsJsonObject("plagiarism_breakdown");
                report.append("### Plagiarism Breakdown\n\n");
                if (breakdown.has("academic_sources")) report.append("- **Academic Sources:** ").append(breakdown.get("academic_sources").getAsInt()).append("\n");
                if (breakdown.has("web_sources")) report.append("- **Web Sources:** ").append(breakdown.get("web_sources").getAsInt()).append("\n");
                if (breakdown.has("types") && breakdown.get("types").isJsonObject()) {
                    JsonObject types = breakdown.getAsJsonObject("types");
                    for (String key : types.keySet()) {
                        report.append("- **").append(key).append(":** ").append(types.get(key).getAsInt()).append("\n");
                    }
                }
                report.append("\n");
            }

            // Affected Sections
            if (json.has("affected_sections") && json.get("affected_sections").isJsonArray()) {
                JsonArray sections = json.getAsJsonArray("affected_sections");
                if (sections.size() > 0) {
                    report.append("### Affected Sections\n\n");
                    for (JsonElement element : sections) {
                        JsonObject section = element.getAsJsonObject();
                        int num = section.has("section_number") ? section.get("section_number").getAsInt() : 0;
                        String type = section.has("plagiarism_type") ? section.get("plagiarism_type").getAsString() : "Match";
                        double sim = section.has("similarity_score") ? section.get("similarity_score").getAsDouble() * 100 : 0;
                        String snippet = section.has("text_snippet") ? section.get("text_snippet").getAsString() : "";
                        String src = section.has("source") ? section.get("source").getAsString() : "";

                        report.append("**Section #").append(num).append("** (").append(type).append(")\n");
                        report.append(String.format(Locale.getDefault(), "> Match Similarity: %.1f%%\n", sim));
                        if (!snippet.isEmpty()) {
                            report.append("> _\"").append(snippet.length() > 150 ? snippet.substring(0, 150) + "..." : snippet).append("\"_\n");
                        }
                        if (!src.isEmpty()) report.append("  Source: ").append(src).append("\n");
                        report.append("\n");
                    }
                }
            }

            // Matched Sources
            if (json.has("matched_sources") && json.get("matched_sources").isJsonArray()) {
                JsonArray sources = json.getAsJsonArray("matched_sources");
                if (sources.size() > 0) {
                    report.append("### Matched Sources\n\n");
                    for (int i = 0; i < sources.size(); i++) {
                        JsonObject src = sources.get(i).getAsJsonObject();
                        String url = src.has("url") ? src.get("url").getAsString() : "Internal Source";
                        String type = src.has("type") ? src.get("type").getAsString() : "Reference";
                        double maxSim = src.has("max_similarity") ? src.get("max_similarity").getAsDouble() * 100 : 0;
                        report.append(String.format(Locale.getDefault(), "%d. **[%s](%s)**\n", i + 1, type, url));
                        report.append(String.format(Locale.getDefault(), "   Similarity: %.1f%%\n", maxSim));
                    }
                    report.append("\n");
                }
            }

            // Detailed Analysis (Large text block)
            if (json.has("detailed_analysis")) {
                report.append("### Specialist Analysis\n\n");
                report.append(json.get("detailed_analysis").getAsString());
            }

            Button btnViewReport = findViewById(R.id.btn_view_report);
            btnViewReport.setVisibility(View.VISIBLE);
            btnViewReport.setOnClickListener(v -> {
                ResultBottomSheetFragment.newInstance("Plagiarism Detailed Review", report.toString())
                        .show(getSupportFragmentManager(), "result");
            });

        } catch (Exception e) {
            Toast.makeText(this, "Failed to parse report: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private File copyUriToTempFile(Uri uri) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) return null;
            File tempFile = new File(getCacheDir(), getFileName(uri));
            FileOutputStream out = new FileOutputStream(tempFile);
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            in.close();
            out.close();
            return tempFile;
        } catch (Exception e) {
            Toast.makeText(this, "Failed to read file", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private String getFileName(Uri uri) {
        String name = "upload.pdf";
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = cursor.getString(idx);
            }
        }
        return name;
    }
}
