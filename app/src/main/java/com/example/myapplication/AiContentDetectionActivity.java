package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TablePlugin;



public class AiContentDetectionActivity extends AppCompatActivity {

    private TextInputEditText etTextInput;
    private Button btnDetectAi;
    private LinearLayout llProgress, llResults;
    private TextView tvResult, tvConfidenceScore, tvAnalysisDetails;
    private ProgressBar progressConfidence;
    private SessionManager session;
    private Markwon markwon;
    private MaterialButtonToggleGroup modelToggle;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_content_detection);

        session = new SessionManager(this);
        markwon = Markwon.builder(this)
                .usePlugin(TablePlugin.create(this))
                .build();


        // Toolbar back
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
        }

        // Bind views
        etTextInput = findViewById(R.id.et_text_input);
        btnDetectAi = findViewById(R.id.btn_detect_ai);
        llProgress = findViewById(R.id.ll_progress);
        llResults = findViewById(R.id.ll_results);
        tvResult = findViewById(R.id.tv_result);
        tvConfidenceScore = findViewById(R.id.tv_confidence_score);
        tvAnalysisDetails = findViewById(R.id.tv_analysis_details);
        progressConfidence = findViewById(R.id.progress_confidence);
        modelToggle = findViewById(R.id.model_selector).findViewById(R.id.model_toggle_group);


        // Initially hide results
        llResults.setVisibility(View.GONE);

        btnDetectAi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = etTextInput.getText() != null ? etTextInput.getText().toString().trim() : "";
                if (text.isEmpty()) {
                    etTextInput.setError("Please enter some text");
                    return;
                }

                // Show progress, hide results
                llProgress.setVisibility(View.VISIBLE);
                llResults.setVisibility(View.GONE);
                btnDetectAi.setEnabled(false);

                String token = session.getToken();
                String modelType = (modelToggle.getCheckedButtonId() == R.id.btn_model_advanced) ? "advanced" : "fast";
                ApiClient.detectAiText(text, "English", token, modelType, new ApiClient.ApiCallback() {

                    @Override
                    public void onSuccess(String responseBody) {
                        llProgress.setVisibility(View.GONE);
                        llResults.setVisibility(View.VISIBLE);
                        btnDetectAi.setEnabled(true);

                        try {
                            JsonObject json = new Gson().fromJson(responseBody, JsonObject.class);
                            String verdict = json.has("verdict") ? json.get("verdict").getAsString() : "Analysis complete.";
                            double aiProb = json.has("ai_probability") && !json.get("ai_probability").isJsonNull() ? json.get("ai_probability").getAsDouble() : 0;
                            double confidence = json.has("confidence_score") && !json.get("confidence_score").isJsonNull() ? json.get("confidence_score").getAsDouble() : 0;

                            tvResult.setText(aiProb > 50 ? "AI Generated Content Detected" : "Likely Human Written");
                            tvConfidenceScore.setText(String.format("%.0f%%", aiProb));
                            progressConfidence.setProgress((int) aiProb);
                            
                            StringBuilder analysisBuilder = new StringBuilder();
                            analysisBuilder.append(verdict).append("\n\n");
                            analysisBuilder.append("### Statistics\n");
                            analysisBuilder.append(String.format("- **AI Probability:** %.1f%%\n", aiProb));
                            if (json.has("human_probability") && !json.get("human_probability").isJsonNull()) {
                                analysisBuilder.append(String.format("- **Human Probability:** %.1f%%\n", json.get("human_probability").getAsDouble()));
                            }
                            analysisBuilder.append(String.format("- **Confidence Score:** %.1f%%\n", confidence));
                            if (json.has("model_used") && !json.get("model_used").isJsonNull()) {
                                analysisBuilder.append("- **Model Used:** ").append(json.get("model_used").getAsString()).append("\n");
                            }

                            if (json.has("highlighted_spans") && json.get("highlighted_spans").isJsonArray()) {
                                com.google.gson.JsonArray spans = json.getAsJsonArray("highlighted_spans");
                                if (spans.size() > 0) {
                                    analysisBuilder.append("\n### Highlighted Spans\n");
                                    for (int i = 0; i < spans.size(); i++) {
                                        JsonObject span = spans.get(i).getAsJsonObject();
                                        if (span.has("text")) {
                                            analysisBuilder.append("> \"").append(span.get("text").getAsString()).append("\"\n\n");
                                            if (span.has("reason")) {
                                                analysisBuilder.append("*Reason:* ").append(span.get("reason").getAsString()).append("\n\n");
                                            }
                                        }
                                    }
                                }
                            }

                            ResultBottomSheetFragment.newInstance("AI Content Analysis", analysisBuilder.toString())
                                    .show(getSupportFragmentManager(), "result");

                            markwon.setMarkdown(tvAnalysisDetails, verdict);

                        } catch (Exception e) {
                            ResultBottomSheetFragment.newInstance("Detection Results", responseBody)
                                    .show(getSupportFragmentManager(), "result");
                        }
                    }

                    @Override
                    public void onError(int code, String error) {
                        llProgress.setVisibility(View.GONE);
                        btnDetectAi.setEnabled(true);
                        
                        if (code == 401) {
                            Toast.makeText(AiContentDetectionActivity.this, "Session expired. Please login again.", Toast.LENGTH_LONG).show();
                            session.clearSession();
                            startActivity(new Intent(AiContentDetectionActivity.this, LoginActivity.class));
                            finishAffinity(); // Clear activity stack
                        } else {
                            Toast.makeText(AiContentDetectionActivity.this, error, Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        });
    }
}
