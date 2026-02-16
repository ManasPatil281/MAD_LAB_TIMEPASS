package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        setupCard(R.id.card_ai_detection, AiContentDetectionActivity.class);
        setupCard(R.id.card_grammar_check, GrammarCheckActivity.class);
        setupCard(R.id.card_plagiarism, PlagiarismDetectionActivity.class);
        setupCard(R.id.card_paraphrase, ParaphrasingActivity.class);
        setupCard(R.id.card_summarize, SummarizationActivity.class);
        setupCard(R.id.card_paper_review, PaperReviewActivity.class);
    }

    private void setupCard(int cardId, final Class<?> destinationActivity) {
        View card = findViewById(cardId);
        if (card != null) {
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(HomeActivity.this, destinationActivity);
                    startActivity(intent);
                }
            });
        }
    }
}
