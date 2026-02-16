package com.example.myapplication;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import com.google.android.material.textfield.TextInputEditText;

public class SummarizationFragment extends Fragment {

    private TextInputEditText etInput;
    private Button btnSummarize;
    private CardView cardResults;
    private TextView tvSummaryOutput;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_summarization, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etInput = view.findViewById(R.id.et_input);
        btnSummarize = view.findViewById(R.id.btn_summarize);
        cardResults = view.findViewById(R.id.card_results);
        tvSummaryOutput = view.findViewById(R.id.tv_summary_output);

        btnSummarize.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = etInput.getText().toString().trim();
                if (text.isEmpty()) {
                    etInput.setError("Please enter some text");
                    return;
                }

                // Simulate processing
                btnSummarize.setEnabled(false);
                btnSummarize.setText("Summarizing...");
                
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (getContext() == null) return;
                        
                        btnSummarize.setEnabled(true);
                        btnSummarize.setText("Summarize Info");
                        cardResults.setVisibility(View.VISIBLE);
                        tvSummaryOutput.setText("This is a simulated summary of the input text. In a real application, this would be the output from an AI model processing your content.");
                        Toast.makeText(getContext(), "Summary Generated", Toast.LENGTH_SHORT).show();
                    }
                }, 1500);
            }
        });
    }
}
