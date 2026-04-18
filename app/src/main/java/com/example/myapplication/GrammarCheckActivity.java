package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TablePlugin;



public class GrammarCheckActivity extends AppCompatActivity {

    private TextInputEditText etTextInput;
    private Button btnCheckGrammar;
    private LinearLayout llLoading, llResultsSection;
    private TextView tvIssueCount, tvCorrectedText;
    private MaterialButtonToggleGroup modelToggle;
    private SessionManager session;
    private Markwon markwon;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_grammar_check);

        session = new SessionManager(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
        }

        etTextInput = findViewById(R.id.et_text_input);
        btnCheckGrammar = findViewById(R.id.btn_check_grammar);
        llLoading = findViewById(R.id.ll_loading);
        llResultsSection = findViewById(R.id.ll_results_section);
        tvIssueCount = findViewById(R.id.tv_issue_count);
        tvCorrectedText = findViewById(R.id.tv_corrected_text);
        modelToggle = findViewById(R.id.model_selector).findViewById(R.id.model_toggle_group);

        markwon = Markwon.builder(this)
                .usePlugin(TablePlugin.create(this))
                .build();


        // Initially hide results
        llResultsSection.setVisibility(View.GONE);

        btnCheckGrammar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = etTextInput.getText() != null ? etTextInput.getText().toString().trim() : "";
                if (text.isEmpty()) {
                    etTextInput.setError("Please enter some text");
                    return;
                }

                llLoading.setVisibility(View.VISIBLE);
                llResultsSection.setVisibility(View.GONE);
                btnCheckGrammar.setEnabled(false);

                String token = session.getToken();
                String modelType = (modelToggle.getCheckedButtonId() == R.id.btn_model_advanced) ? "advanced" : "fast";

                ApiClient.grammarCheck(text, "English", token, modelType, new ApiClient.ApiCallback() {

                    @Override
                    public void onSuccess(String responseBody) {
                        llLoading.setVisibility(View.GONE);
                        llResultsSection.setVisibility(View.VISIBLE);
                        btnCheckGrammar.setEnabled(true);

                        try {
                            JsonObject json = new Gson().fromJson(responseBody, JsonObject.class);
                            String correctedText = json.has("corrected_text") && !json.get("corrected_text").isJsonNull()
                                    ? json.get("corrected_text").getAsString() : "No corrections provided.";
                            int errorCount = json.has("error_count") && !json.get("error_count").isJsonNull()
                                    ? json.get("error_count").getAsInt() : 0;

                            tvIssueCount.setText(String.valueOf(errorCount));
                            markwon.setMarkdown(tvCorrectedText, correctedText);

                            StringBuilder resultBuilder = new StringBuilder();
                            resultBuilder.append("### Corrected Text\n").append(correctedText).append("\n\n");
                            
                            if (json.has("readability_score") && !json.get("readability_score").isJsonNull()) {
                                resultBuilder.append("### Readability Score: ")
                                        .append(String.format("%.1f/100\n\n", json.get("readability_score").getAsDouble()));
                            }

                            if (json.has("errors") && json.get("errors").isJsonArray()) {
                                com.google.gson.JsonArray errors = json.getAsJsonArray("errors");
                                if (errors.size() > 0) {
                                    resultBuilder.append("### Errors Detected\n");
                                    for (int i = 0; i < errors.size(); i++) {
                                        JsonObject err = errors.get(i).getAsJsonObject();
                                        String type = err.has("type") ? err.get("type").getAsString() : "grammar";
                                        String orig = err.has("original") ? err.get("original").getAsString() : "";
                                        String sugg = err.has("suggestion") ? err.get("suggestion").getAsString() : "";
                                        resultBuilder.append(String.format("- **[%s]** ~~%s~~ → **%s**\n", type, orig, sugg));
                                    }
                                    resultBuilder.append("\n");
                                }
                            }

                            if (json.has("style_suggestions") && json.get("style_suggestions").isJsonArray()) {
                                com.google.gson.JsonArray suggestions = json.getAsJsonArray("style_suggestions");
                                if (suggestions.size() > 0) {
                                    resultBuilder.append("### Style Suggestions\n");
                                    for (int i = 0; i < suggestions.size(); i++) {
                                        resultBuilder.append("- ").append(suggestions.get(i).getAsString()).append("\n");
                                    }
                                }
                            }

                            ResultBottomSheetFragment.newInstance("Grammar Check Results", resultBuilder.toString())
                                    .show(getSupportFragmentManager(), "result");
                            
                            Toast.makeText(GrammarCheckActivity.this,
                                    "Grammar check complete!", Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            ResultBottomSheetFragment.newInstance("Grammar Check Results", responseBody)
                                    .show(getSupportFragmentManager(), "result");
                        }

                    }

                    @Override
                    public void onError(int code, String error) {
                        llLoading.setVisibility(View.GONE);
                        btnCheckGrammar.setEnabled(true);
                        
                        if (code == 401) {
                            Toast.makeText(GrammarCheckActivity.this, "Session expired. Please login again.", Toast.LENGTH_LONG).show();
                            session.clearSession();
                            startActivity(new Intent(GrammarCheckActivity.this, LoginActivity.class));
                            finishAffinity();
                        } else {
                            Toast.makeText(GrammarCheckActivity.this, error, Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        });
    }
}
