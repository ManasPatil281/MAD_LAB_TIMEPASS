package com.example.myapplication;

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
import com.google.android.material.textfield.TextInputEditText;

public class ParaphrasingActivity extends AppCompatActivity {

    private TextInputEditText etInput;
    private Button btnParaphrase;
    private TextView tvOutput;
    private ImageView ivCopy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paraphrasing);

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

        btnParaphrase.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = etInput.getText().toString().trim();
                if (text.isEmpty()) {
                    etInput.setError("Please enter text");
                    return;
                }

                // Simulate processing
                btnParaphrase.setEnabled(false);
                btnParaphrase.setText("Paraphrasing...");

                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        btnParaphrase.setEnabled(true);
                        btnParaphrase.setText("Paraphrase Text");
                        tvOutput.setText("Here is the paraphrased version of your text. It has been reworded to improve clarity and flow while maintaining the original meaning.");
                        Toast.makeText(ParaphrasingActivity.this, "Text Paraphrased", Toast.LENGTH_SHORT).show();
                    }
                }, 1500);
            }
        });

        ivCopy.setOnClickListener(view -> 
            Toast.makeText(ParaphrasingActivity.this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        );
    }
}
