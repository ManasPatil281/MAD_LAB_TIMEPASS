package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etEmail, etPassword;
    private Button btnLogin;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        session = new SessionManager(this);

        // If already logged in, skip to appropriate home screen
        if (session.isLoggedIn()) {
            goHome();
            return;
        }

        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        TextView tvSignup = findViewById(R.id.tv_signup);

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
                String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

                if (email.isEmpty()) {
                    etEmail.setError("Enter your email");
                    return;
                }
                if (password.isEmpty()) {
                    etPassword.setError("Enter your password");
                    return;
                }

                btnLogin.setEnabled(false);
                btnLogin.setText("Logging in…");

                ApiClient.login(email, password, new ApiClient.ApiCallback() {
                    @Override
                    public void onSuccess(String responseBody) {
                        try {
                            JsonObject json = new Gson().fromJson(responseBody, JsonObject.class);
                            String token = json.get("token").getAsString();
                            session.saveToken(token);

                            // Save user info if available (including role and institution from users schema)
                            if (json.has("user")) {
                                JsonObject user = json.getAsJsonObject("user");
                                session.saveUserInfo(
                                        user.has("user_id") ? user.get("user_id").getAsString() : "",
                                        user.has("email") ? user.get("email").getAsString() : email,
                                        user.has("full_name") ? user.get("full_name").getAsString() : "",
                                        user.has("role") ? user.get("role").getAsString() : "researcher",
                                        user.has("institution") && !user.get("institution").isJsonNull()
                                                ? user.get("institution").getAsString() : ""
                                );
                            }

                            Toast.makeText(LoginActivity.this, "Login successful!", Toast.LENGTH_SHORT).show();
                            goHome();
                        } catch (Exception e) {
                            btnLogin.setEnabled(true);
                            btnLogin.setText("Login");
                            Toast.makeText(LoginActivity.this, "Unexpected response", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(int code, String error) {
                        btnLogin.setEnabled(true);
                        btnLogin.setText("Login");
                        Toast.makeText(LoginActivity.this, error, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });

        tvSignup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LoginActivity.this, SignupActivity.class);
                startActivity(intent);
            }
        });
    }

    /**
     * Routes to the correct home screen based on user role.
     * Admin users go to AdminDashboardActivity, others go to HomeActivity.
     */
    private void goHome() {
        Intent intent;
        if (session.isAdmin()) {
            intent = new Intent(LoginActivity.this, AdminDashboardActivity.class);
        } else {
            intent = new Intent(LoginActivity.this, HomeActivity.class);
        }
        startActivity(intent);
        finish();
    }
}
