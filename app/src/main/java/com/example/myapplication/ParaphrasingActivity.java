package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TablePlugin;



public class ParaphrasingActivity extends AppCompatActivity {

    private TextInputEditText etInput;
    private Button btnParaphrase;
    private TextView tvOutput;
    private ImageView ivCopy;
    private MaterialButtonToggleGroup modelToggle;
    private SessionManager session;
    private Markwon markwon;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paraphrasing);

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

        etInput = findViewById(R.id.et_input);
        btnParaphrase = findViewById(R.id.btn_paraphrase);
        tvOutput = findViewById(R.id.tv_output);
        ivCopy = findViewById(R.id.iv_copy);
        modelToggle = findViewById(R.id.model_selector).findViewById(R.id.model_toggle_group);
        View cvOutputCard = findViewById(R.id.cv_output_card);

        btnParaphrase.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = etInput.getText() != null ? etInput.getText().toString().trim() : "";
                if (text.isEmpty()) {
                    etInput.setError("Please enter text");
                    return;
                }

                btnParaphrase.setEnabled(false);
                btnParaphrase.setText("Paraphrasing…");

                String token = session.getToken();
                String modelType = (modelToggle.getCheckedButtonId() == R.id.btn_model_advanced) ? "advanced" : "fast";
                ApiClient.paraphraseText(text, "standard", "English", token, modelType,
                        new ApiClient.ApiCallback() {

                            @Override
                            public void onSuccess(String responseBody) {
                                btnParaphrase.setEnabled(true);
                                btnParaphrase.setText("Paraphrase Text");
                                try {
                                    JsonObject json = new Gson().fromJson(responseBody, JsonObject.class);
                                    String formattedResult = formatParaphraseResponse(json, responseBody);

                                    cvOutputCard.setVisibility(View.VISIBLE);
                                    markwon.setMarkdown(tvOutput, formattedResult);

                                    ResultBottomSheetFragment.newInstance("Paraphrased Text", formattedResult)
                                            .show(getSupportFragmentManager(), "result");
                                } catch (Exception e) {
                                    // Last resort fallback
                                    cvOutputCard.setVisibility(View.VISIBLE);
                                    tvOutput.setText(responseBody);

                                    ResultBottomSheetFragment.newInstance("Paraphrased Text", responseBody)
                                            .show(getSupportFragmentManager(), "result");
                                }

                                Toast.makeText(ParaphrasingActivity.this,
                                        "Text Paraphrased", Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onError(int code, String error) {
                                btnParaphrase.setEnabled(true);
                                btnParaphrase.setText("Paraphrase Text");

                                if (code == 401) {
                                    Toast.makeText(ParaphrasingActivity.this, "Session expired. Please login again.", Toast.LENGTH_LONG).show();
                                    session.clearSession();
                                    startActivity(new Intent(ParaphrasingActivity.this, LoginActivity.class));
                                    finishAffinity();
                                } else {
                                    Toast.makeText(ParaphrasingActivity.this, error, Toast.LENGTH_LONG).show();
                                }
                            }
                        });
            }
        });

        ivCopy.setOnClickListener(view -> {
            String text = tvOutput.getText().toString();
            if (!text.isEmpty()) {
                android.content.ClipboardManager clipboard =
                        (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                android.content.ClipData clip =
                        android.content.ClipData.newPlainText("Paraphrased Text", text);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(ParaphrasingActivity.this,
                        "Copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Formats the paraphrase API response into clean markdown.
     * Handles multiple response shapes:
     *   - Direct: {"paraphrased_text":"...", "similarity_score":..., "mode":"..."}
     *   - Nested: {"result": {"paraphrased_text":"..."}}
     *   - Wrapped: {"status":"completed", "paraphrased_text":"..."}
     */
    private String formatParaphraseResponse(JsonObject json, String rawFallback) {
        StringBuilder result = new StringBuilder();

        // Try to find paraphrased_text at top level or nested in "result"
        String paraphrased = extractStringField(json, "paraphrased_text");
        if (paraphrased == null && json.has("result")) {
            JsonElement resultEl = json.get("result");
            if (resultEl.isJsonObject()) {
                paraphrased = extractStringField(resultEl.getAsJsonObject(), "paraphrased_text");
            } else if (resultEl.isJsonPrimitive()) {
                paraphrased = resultEl.getAsString();
            }
        }

        if (paraphrased != null && !paraphrased.isEmpty()) {
            result.append("## Paraphrased Text\n\n");
            result.append(paraphrased);
            result.append("\n\n---\n\n");

            // Statistics section
            boolean hasStats = false;
            StringBuilder stats = new StringBuilder();
            stats.append("### Statistics\n\n");

            // Similarity score
            Double similarity = extractDoubleField(json, "similarity_score");
            if (similarity == null && json.has("result") && json.get("result").isJsonObject()) {
                similarity = extractDoubleField(json.getAsJsonObject("result"), "similarity_score");
            }
            if (similarity != null) {
                stats.append(String.format("- **Similarity with Original:** %.1f%%\n", similarity));
                hasStats = true;
            }

            // Mode
            String mode = extractStringField(json, "mode");
            if (mode == null && json.has("result") && json.get("result").isJsonObject()) {
                mode = extractStringField(json.getAsJsonObject("result"), "mode");
            }
            if (mode != null && !mode.isEmpty()) {
                stats.append("- **Mode:** ").append(capitalize(mode)).append("\n");
                hasStats = true;
            }

            // Status
            String apiStatus = extractStringField(json, "status");
            if (apiStatus != null && !apiStatus.isEmpty()) {
                stats.append("- **Status:** ").append(capitalize(apiStatus)).append("\n");
                hasStats = true;
            }

            if (hasStats) {
                result.append(stats);
            }
        } else {
            // Could not find paraphrased_text — try to present the entire response cleanly
            result.append("## Result\n\n");
            result.append(formatJsonAsMarkdown(json));
        }

        return result.toString();
    }

    /**
     * Formats any JSON object as readable markdown key-value pairs.
     */
    private String formatJsonAsMarkdown(JsonObject json) {
        StringBuilder sb = new StringBuilder();
        for (String key : json.keySet()) {
            JsonElement val = json.get(key);
            String label = capitalize(key.replace("_", " "));

            if (val.isJsonNull()) continue;

            if (val.isJsonPrimitive()) {
                sb.append("- **").append(label).append(":** ").append(val.getAsString()).append("\n");
            } else if (val.isJsonObject()) {
                sb.append("\n### ").append(label).append("\n\n");
                sb.append(formatJsonAsMarkdown(val.getAsJsonObject()));
            } else if (val.isJsonArray()) {
                sb.append("- **").append(label).append(":** ");
                sb.append(val.toString().replace("[", "").replace("]", "").replace("\"", ""));
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private String extractStringField(JsonObject json, String field) {
        if (json.has(field) && !json.get(field).isJsonNull()) {
            return json.get(field).getAsString();
        }
        return null;
    }

    private Double extractDoubleField(JsonObject json, String field) {
        if (json.has(field) && !json.get(field).isJsonNull()) {
            try {
                return json.get(field).getAsDouble();
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
