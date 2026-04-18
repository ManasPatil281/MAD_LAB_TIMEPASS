package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * SignupActivity - ScholarHub
 * Registers a new user matching the users table schema:
 * full_name, email, password, role, institution
 */
public class SignupActivity extends AppCompatActivity {

    private TextInputEditText etName, etEmail, etPassword, etConfirmPassword, etInstitution;
    private AutoCompleteTextView spinnerRole;
    private Button btnSignup;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        session = new SessionManager(this);

        etName = findViewById(R.id.et_name);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);
        etInstitution = findViewById(R.id.et_institution);
        spinnerRole = findViewById(R.id.spinner_role);
        btnSignup = findViewById(R.id.btn_signup);
        TextView tvLogin = findViewById(R.id.tv_login);

        // Populate role dropdown — matches backend RegisterRequest.validate_role
        // which only accepts 'researcher' or 'admin'
        String[] roles = {"researcher", "admin"};
        ArrayAdapter<String> roleAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, roles);
        spinnerRole.setAdapter(roleAdapter);
        spinnerRole.setText("researcher", false); // default

        btnSignup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = etName.getText() != null ? etName.getText().toString().trim() : "";
                String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
                String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";
                String confirm = etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString().trim() : "";
                String institution = etInstitution.getText() != null ? etInstitution.getText().toString().trim() : "";
                String role = spinnerRole.getText() != null ? spinnerRole.getText().toString().trim() : "researcher";

                if (name.isEmpty()) { etName.setError("Enter your name"); return; }
                if (email.isEmpty()) { etEmail.setError("Enter your email"); return; }
                if (password.length() < 8) { etPassword.setError("Password must be at least 8 characters"); return; }
                if (!password.equals(confirm)) { etConfirmPassword.setError("Passwords don't match"); return; }
                if (role.isEmpty()) { spinnerRole.setError("Select a role"); return; }

                btnSignup.setEnabled(false);
                btnSignup.setText("Creating account…");

                ApiClient.register(name, email, password, role, institution, new ApiClient.ApiCallback() {
                    @Override
                    public void onSuccess(String responseBody) {
                        try {
                            JsonObject json = new Gson().fromJson(responseBody, JsonObject.class);
                            String token = json.get("token").getAsString();
                            session.saveToken(token);

                            // Save full user info including role and institution
                            String userId = "";
                            String savedRole = role;
                            String savedInstitution = institution;

                            if (json.has("user")) {
                                JsonObject user = json.getAsJsonObject("user");
                                userId = user.has("user_id") ? user.get("user_id").getAsString() : "";
                                if (user.has("role")) savedRole = user.get("role").getAsString();
                                if (user.has("institution") && !user.get("institution").isJsonNull())
                                    savedInstitution = user.get("institution").getAsString();
                            } else {
                                userId = json.has("user_id") ? json.get("user_id").getAsString() : "";
                            }

                            session.saveUserInfo(userId, email, name, savedRole, savedInstitution);

                            Toast.makeText(SignupActivity.this, "Account created!", Toast.LENGTH_SHORT).show();

                            // Route to admin dashboard if admin, else home
                            Intent intent;
                            if ("admin".equalsIgnoreCase(savedRole)) {
                                intent = new Intent(SignupActivity.this, AdminDashboardActivity.class);
                            } else {
                                intent = new Intent(SignupActivity.this, HomeActivity.class);
                            }
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        } catch (Exception e) {
                            btnSignup.setEnabled(true);
                            btnSignup.setText("Sign Up");
                            Toast.makeText(SignupActivity.this, "Unexpected response", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(int code, String error) {
                        btnSignup.setEnabled(true);
                        btnSignup.setText("Sign Up");
                        Toast.makeText(SignupActivity.this, error, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });

        tvLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(SignupActivity.this, LoginActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }
}
