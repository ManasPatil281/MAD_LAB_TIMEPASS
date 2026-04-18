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
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TablePlugin;



import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class PaperReviewActivity extends AppCompatActivity {

    private LinearLayout llReviewProgress, llReviewResults, llFileInfo;
    private Button btnUploadPaper, btnReviewPaper;
    private TextView tvFileName, tvFileSize;
    private TextView tvOverallScore, tvScoreLabel, tvStrengths, tvWeaknesses, tvSuggestions, tvVerdict;
    private MaterialButtonToggleGroup modelToggle;
    private SessionManager session;
    private Markwon markwon;
    private File selectedPdf;






    private final ActivityResultLauncher<Intent> pdfPicker =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        selectedPdf = copyUriToTempFile(uri);
                        if (selectedPdf != null) {
                            llFileInfo.setVisibility(View.VISIBLE);
                            btnReviewPaper.setVisibility(View.VISIBLE);
                            tvFileName.setText(selectedPdf.getName());
                            tvFileSize.setText(String.format("%.1f KB", selectedPdf.length() / 1024.0));
                        }
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paper_review);

        session = new SessionManager(this);
        markwon = Markwon.builder(this)
                .usePlugin(TablePlugin.create(this))
                .build();


        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
        }

        btnUploadPaper = findViewById(R.id.btn_upload_paper);
        btnReviewPaper = findViewById(R.id.btn_review_paper);
        llFileInfo = findViewById(R.id.ll_file_info);
        llReviewProgress = findViewById(R.id.ll_review_progress);
        llReviewResults = findViewById(R.id.ll_review_results);
        tvFileName = findViewById(R.id.tv_file_name);
        tvFileSize = findViewById(R.id.tv_file_size);
        tvOverallScore = findViewById(R.id.tv_overall_score);
        tvScoreLabel = findViewById(R.id.tv_score_label);
        tvStrengths = findViewById(R.id.tv_strengths);
        tvWeaknesses = findViewById(R.id.tv_weaknesses);
        tvSuggestions = findViewById(R.id.tv_suggestions);
        tvVerdict = findViewById(R.id.tv_verdict);
        modelToggle = findViewById(R.id.model_selector).findViewById(R.id.model_toggle_group);


        btnUploadPaper.setOnClickListener(v -> openPdfPicker());

        btnReviewPaper.setOnClickListener(v -> {
            if (selectedPdf == null) {
                Toast.makeText(this, "Please select a PDF first", Toast.LENGTH_SHORT).show();
                return;
            }
            startReview();
        });
    }

    private void openPdfPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/pdf");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        pdfPicker.launch(Intent.createChooser(intent, "Select a PDF"));
    }

    private void startReview() {
        llReviewProgress.setVisibility(View.VISIBLE);
        llReviewResults.setVisibility(View.GONE);
        btnReviewPaper.setEnabled(false);

        String token = session.getToken();
        String modelType = (modelToggle.getCheckedButtonId() == R.id.btn_model_advanced) ? "advanced" : "fast";

        ApiClient.paperReview(selectedPdf, token, modelType, new ApiClient.ApiCallback() {

            @Override
            public void onSuccess(String responseBody) {
                llReviewProgress.setVisibility(View.GONE);
                llReviewResults.setVisibility(View.VISIBLE);
                btnReviewPaper.setEnabled(true);

                try {
                    JsonObject json = new Gson().fromJson(responseBody, JsonObject.class);
                    double score = json.has("overall_score") ? json.get("overall_score").getAsDouble() : 0;
                    tvOverallScore.setText(String.format("%.1f/10", score));
                    String verdictText = score >= 8 ? "Excellent Work" :
                            score >= 6 ? "Good Work" : "Needs Improvement";
                    tvScoreLabel.setText(verdictText);

                    // Fire notification for paper review
                    String reviewFileName = selectedPdf != null ? selectedPdf.getName() : "Document";
                    NotificationHelper.showPaperReviewNotification(
                            PaperReviewActivity.this, reviewFileName, score, verdictText);

                    StringBuilder reviewBuilder = new StringBuilder();
                    if (json.has("abstract_review") && !json.get("abstract_review").isJsonNull()) {
                        reviewBuilder.append("### Abstract\n").append(json.get("abstract_review").getAsString()).append("\n\n");
                    }
                    if (json.has("literature_review") && !json.get("literature_review").isJsonNull()) {
                        reviewBuilder.append("### Literature Review\n").append(json.get("literature_review").getAsString()).append("\n\n");
                    }
                    if (json.has("methodology_review") && !json.get("methodology_review").isJsonNull()) {
                        reviewBuilder.append("### Methodology\n").append(json.get("methodology_review").getAsString()).append("\n\n");
                    }
                    if (json.has("results_review") && !json.get("results_review").isJsonNull()) {
                        reviewBuilder.append("### Results & Data Analysis\n").append(json.get("results_review").getAsString()).append("\n\n");
                    }
                    if (json.has("conclusion_review") && !json.get("conclusion_review").isJsonNull()) {
                        reviewBuilder.append("### Conclusion & Discussion\n").append(json.get("conclusion_review").getAsString()).append("\n\n");
                    }
                    
                    reviewBuilder.append("### Final Verdict\n");
                    if (json.has("recommendation") && !json.get("recommendation").isJsonNull()) {
                        reviewBuilder.append("- **Recommendation:** ").append(json.get("recommendation").getAsString()).append("\n");
                    }
                    if (json.has("citations_quality") && !json.get("citations_quality").isJsonNull()) {
                        reviewBuilder.append("- **Citations Quality:** ").append(json.get("citations_quality").getAsString()).append("\n");
                    }
                    
                    String review = reviewBuilder.toString().trim();

                    // Build strengths
                    if (json.has("strengths") && json.get("strengths").isJsonArray()) {
                        JsonArray arr = json.getAsJsonArray("strengths");
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < arr.size(); i++)
                            sb.append("• ").append(arr.get(i).getAsString()).append("\n");
                        tvStrengths.setText(sb.toString().trim());
                    } else if (json.has("strengths") && json.get("strengths").isJsonPrimitive()) {
                        tvStrengths.setText(json.get("strengths").getAsString());
                    } else {
                        tvStrengths.setText("Detailed strengths in full review");
                    }

                    // Build weaknesses
                    if (json.has("weaknesses") && json.get("weaknesses").isJsonArray()) {
                        JsonArray arr = json.getAsJsonArray("weaknesses");
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < arr.size(); i++)
                            sb.append("• ").append(arr.get(i).getAsString()).append("\n");
                        tvWeaknesses.setText(sb.toString().trim());
                    } else if (json.has("weaknesses") && json.get("weaknesses").isJsonPrimitive()) {
                         tvWeaknesses.setText(json.get("weaknesses").getAsString());
                    }

                    // Build suggestions
                    if (json.has("suggestions") && json.get("suggestions").isJsonArray()) {
                        JsonArray arr = json.getAsJsonArray("suggestions");
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < arr.size(); i++)
                            sb.append(i + 1).append(". ").append(arr.get(i).getAsString()).append("\n");
                        tvSuggestions.setText(sb.toString().trim());
                    } else if (json.has("suggestions") && json.get("suggestions").isJsonPrimitive()) {
                        tvSuggestions.setText(json.get("suggestions").getAsString());
                    }

                    if (!review.isEmpty()) {
                        markwon.setMarkdown(tvVerdict, review);
                        // Also show a pop-up if the user wants it
                        ResultBottomSheetFragment.newInstance("Full Paper Review", review)
                                .show(getSupportFragmentManager(), "result");
                    } else {
                        markwon.setMarkdown(tvVerdict, responseBody);
                    }

                } catch (Exception e) {
                    markwon.setMarkdown(tvVerdict, responseBody);
                    ResultBottomSheetFragment.newInstance("Paper Review Results", responseBody)
                            .show(getSupportFragmentManager(), "result");
                }
            }

            @Override
            public void onError(int code, String error) {
                llReviewProgress.setVisibility(View.GONE);
                btnReviewPaper.setEnabled(true);
                
                if (code == 401) {
                    Toast.makeText(PaperReviewActivity.this, "Session expired. Please login again.", Toast.LENGTH_LONG).show();
                    session.clearSession();
                    startActivity(new Intent(PaperReviewActivity.this, LoginActivity.class));
                    finishAffinity();
                } else {
                    Toast.makeText(PaperReviewActivity.this, error, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private File copyUriToTempFile(Uri uri) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
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
