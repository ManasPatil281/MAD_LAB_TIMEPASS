package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TablePlugin;



public class SummarizationFragment extends Fragment {

    private TextInputEditText etInput;
    private Button btnSummarize;
    private MaterialButtonToggleGroup modelToggle;
    private SessionManager session;
    private Markwon markwon;



    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_summarization, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        session = new SessionManager(requireContext());
        markwon = Markwon.builder(requireContext())
                .usePlugin(TablePlugin.create(requireContext()))
                .build();


        etInput = view.findViewById(R.id.et_input);
        btnSummarize = view.findViewById(R.id.btn_summarize);
        modelToggle = view.findViewById(R.id.model_selector).findViewById(R.id.model_toggle_group);


        btnSummarize.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = etInput.getText() != null ? etInput.getText().toString().trim() : "";
                if (text.isEmpty()) {
                    etInput.setError("Please enter some text");
                    return;
                }

                btnSummarize.setEnabled(false);
                btnSummarize.setText("Summarizing…");

                String token = session.getToken();
                String modelType = (modelToggle.getCheckedButtonId() == R.id.btn_model_advanced) ? "advanced" : "fast";
                ApiClient.summarizeText(text, "abstractive", "English", token, modelType,
                        new ApiClient.ApiCallback() {

                            @Override
                            public void onSuccess(String responseBody) {
                                if (getContext() == null) return;
                                btnSummarize.setEnabled(true);
                                btnSummarize.setText("Summarize Info");
                                 try {
                                     JsonObject json = new Gson().fromJson(responseBody, JsonObject.class);
                                     String summary = json.has("summary_text") && !json.get("summary_text").isJsonNull()
                                             ? json.get("summary_text").getAsString()
                                             : responseBody;
                                     
                                     StringBuilder resultBuilder = new StringBuilder();
                                     resultBuilder.append(summary).append("\n\n");
                                     resultBuilder.append("### Statistics\n");
                                     if (json.has("original_length") && !json.get("original_length").isJsonNull()) {
                                         resultBuilder.append(String.format("- **Original Length:** %d words\n", json.get("original_length").getAsInt()));
                                     }
                                     if (json.has("summary_length") && !json.get("summary_length").isJsonNull()) {
                                         resultBuilder.append(String.format("- **Summary Length:** %d words\n", json.get("summary_length").getAsInt()));
                                     }
                                     if (json.has("compression_rate") && !json.get("compression_rate").isJsonNull()) {
                                         resultBuilder.append(String.format("- **Compression Rate:** %.1f%%\n", json.get("compression_rate").getAsDouble()));
                                     }

                                     ResultBottomSheetFragment.newInstance("Text Summary", resultBuilder.toString())
                                             .show(getParentFragmentManager(), "result");
                                 } catch (Exception e) {
                                     ResultBottomSheetFragment.newInstance("Text Summary", responseBody)
                                             .show(getParentFragmentManager(), "result");
                                 }

                                 Toast.makeText(getContext(), "Summary Generated", Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onError(int code, String error) {
                                if (getContext() == null) return;
                                btnSummarize.setEnabled(true);
                                btnSummarize.setText("Summarize Info");
                                
                                if (code == 401) {
                                    Toast.makeText(getContext(), "Session expired. Please login again.", Toast.LENGTH_LONG).show();
                                    session.clearSession();
                                    startActivity(new Intent(requireActivity(), LoginActivity.class));
                                    requireActivity().finishAffinity();
                                } else {
                                    Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
                                }
                            }
                        });
            }
        });
    }
}
