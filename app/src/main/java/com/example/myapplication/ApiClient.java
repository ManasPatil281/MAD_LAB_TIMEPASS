package com.example.myapplication;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Central API client for ScholarMate that connects to the FastAPI backend.
 * All network calls are asynchronous and return results on the main thread.
 */
public class ApiClient {

    // ─── Change this to your backend server URL ───────────────────────────────
    // Use 10.0.2.2 for Android emulator (maps to host machine localhost)
    // Use your machine's LAN IP for physical devices on same Wi-Fi
    private static final String BASE_URL = "https://manpat-scholarmate-4m4r.onrender.com/api/v1";
    // ──────────────────────────────────────────────────────────────────────────

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final MediaType PDF  = MediaType.parse("application/pdf");
    private static final Gson gson = new Gson();

    private static OkHttpClient client;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static OkHttpClient getClient() {
        if (client == null) {
            client = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(300, TimeUnit.SECONDS)   // AI calls can be very slow, especially on HF Spaces
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(false)
                    .build();
        }
        return client;
    }

    // ─── Callback Interface ─────────────────────────────────────────────────
    public interface ApiCallback {
        void onSuccess(String responseBody);
        void onError(int code, String error);
    }

    // ─── Auth Endpoints ─────────────────────────────────────────────────────

    /**
     * POST /auth/register
     */
    public static void register(String fullName, String email, String password,
                                 String role, String institution, ApiCallback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("full_name", fullName);
        body.addProperty("email", email);
        body.addProperty("password", password);
        body.addProperty("role", role);
        if (institution != null && !institution.isEmpty()) {
            body.addProperty("institution", institution);
        }

        postJson("/auth/register", body.toString(), null, callback);
    }

    /**
     * POST /auth/login
     */
    public static void login(String email, String password, ApiCallback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);

        postJson("/auth/login", body.toString(), null, callback);
    }

    /**
     * POST /auth/logout
     */
    public static void logout(String token, ApiCallback callback) {
        postJson("/auth/logout", "{}", token, callback);
    }

    // ─── Feature Endpoints (text-based) ─────────────────────────────────────

    /**
     * POST /submit/ai-detection (text)
     */
    public static void detectAiText(String text, String language, String token, String modelType, ApiCallback callback) {
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("text", text)
                .addFormDataPart("language", language)
                .addFormDataPart("model_type", modelType)
                .build();

        postMultipart("/submit/ai-detection", body, token, callback);
    }


    /**
     * POST /submit/ai-detection (PDF)
     */
    public static void detectAiPdf(File pdfFile, String language, String token, String modelType, ApiCallback callback) {
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", pdfFile.getName(),
                        RequestBody.create(pdfFile, PDF))
                .addFormDataPart("language", language)
                .addFormDataPart("model_type", modelType)
                .build();

        postMultipart("/submit/ai-detection", body, token, callback);
    }


    /**
     * POST /submit/grammar-check (JSON body)
     */
    public static void grammarCheck(String text, String language, String token, String modelType, ApiCallback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("text", text);
        body.addProperty("language", language);
        body.addProperty("model_type", modelType);

        postJson("/submit/grammar-check", body.toString(), token, callback);
    }


    /**
     * POST /submit/paraphrase (text)
     */
    public static void paraphraseText(String text, String mode, String language,
                                      String token, String modelType, ApiCallback callback) {
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("text", text)
                .addFormDataPart("mode", mode)
                .addFormDataPart("language", language)
                .addFormDataPart("model_type", modelType)
                .build();

        postMultipart("/submit/paraphrase", body, token, callback);
    }


    /**
     * POST /submit/paraphrase (PDF)
     */
    public static void paraphrasePdf(File pdfFile, String mode, String language,
                                     String token, String modelType, ApiCallback callback) {
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", pdfFile.getName(),
                        RequestBody.create(pdfFile, PDF))
                .addFormDataPart("mode", mode)
                .addFormDataPart("language", language)
                .addFormDataPart("model_type", modelType)
                .build();

        postMultipart("/submit/paraphrase", body, token, callback);
    }


    /**
     * POST /submit/plagiarism (PDF only)
     */
    public static void detectPlagiarism(File pdfFile, String token, String modelType, ApiCallback callback) {
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", pdfFile.getName(),
                        RequestBody.create(pdfFile, PDF))
                .addFormDataPart("model_type", modelType)
                .build();

        postMultipart("/submit/plagiarism", body, token, callback);
    }

    /**
     * New: Directly call HuggingFace plagiarism endpoint
     */
    public static void callHuggingFacePlagiarism(File pdfFile, ApiCallback callback) {
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", pdfFile.getName(),
                        RequestBody.create(pdfFile, PDF))
                .build();

        Request request = new Request.Builder()
                .url("https://manas281-nlp-mp.hf.space/generate-detailed-report")
                .post(body)
                .build();

        enqueue(request, callback);
    }


    /**
     * POST /submit/summarize (text)
     */
    public static void summarizeText(String text, String summaryType, String language,
                                     String token, String modelType, ApiCallback callback) {
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("text", text)
                .addFormDataPart("summary_type", summaryType)
                .addFormDataPart("language", language)
                .addFormDataPart("model_type", modelType)
                .build();

        postMultipart("/submit/summarize", body, token, callback);
    }


    /**
     * POST /submit/summarize (PDF)
     */
    public static void summarizePdf(File pdfFile, String summaryType, String language,
                                    String token, String modelType, ApiCallback callback) {
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", pdfFile.getName(),
                        RequestBody.create(pdfFile, PDF))
                .addFormDataPart("summary_type", summaryType)
                .addFormDataPart("language", language)
                .addFormDataPart("model_type", modelType)
                .build();

        postMultipart("/submit/summarize", body, token, callback);
    }


    /**
     * POST /submit/paper-review (PDF only)
     */
    public static void paperReview(File pdfFile, String token, String modelType, ApiCallback callback) {
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", pdfFile.getName(),
                        RequestBody.create(pdfFile, PDF))
                .addFormDataPart("model_type", modelType)
                .build();

        postMultipart("/submit/paper-review", body, token, callback);
    }


    // ─── Dashboard / Home Endpoints ────────────────────────────────────────

    /**
     * GET /users/me/history?limit=1 — user usage statistics (total count)
     */
    public static void getDashboardStats(String token, ApiCallback callback) {
        getJson("/users/me/history?limit=1", token, callback);
    }

    /**
     * GET /users/me/history?limit=3 — recent submissions
     */
    public static void getRecentSubmissions(String token, ApiCallback callback) {
        getJson("/users/me/history?limit=3", token, callback);
    }

    /**
     * GET /submissions/{id} — fetch full submission detail with result data
     */
    public static void getSubmissionDetail(String submissionId, String token, ApiCallback callback) {
        getJson("/submissions/" + submissionId, token, callback);
    }

    // ─── Admin Endpoints ────────────────────────────────────────────────────

    /**
     * GET /admin/stats — platform-wide statistics (usage_logs, sessions, submissions, users)
     * Requires admin role.
     */
    public static void getAdminStats(String token, ApiCallback callback) {
        getJson("/admin/stats", token, callback);
    }

    /**
     * GET /admin/logs — paginated usage/error logs.
     * Requires admin role.
     */
    public static void getAdminLogs(String token, int page, int limit, ApiCallback callback) {
        getJson("/admin/logs?page=" + page + "&limit=" + limit, token, callback);
    }

    // ─── Internal HTTP helpers ──────────────────────────────────────────────

    private static void getJson(String path, String token, ApiCallback callback) {
        Request.Builder builder = new Request.Builder()
                .url(BASE_URL + path)
                .get();

        if (token != null) {
            builder.addHeader("Authorization", "Bearer " + token);
        }

        enqueue(builder.build(), callback);
    }

    private static void postJson(String path, String json, String token, ApiCallback callback) {
        RequestBody requestBody = RequestBody.create(json, JSON);
        Request.Builder builder = new Request.Builder()
                .url(BASE_URL + path)
                .post(requestBody);

        if (token != null) {
            builder.addHeader("Authorization", "Bearer " + token);
        }

        enqueue(builder.build(), callback);
    }

    private static void postMultipart(String path, MultipartBody body, String token,
                                      ApiCallback callback) {
        Request.Builder builder = new Request.Builder()
                .url(BASE_URL + path)
                .post(body);

        if (token != null) {
            builder.addHeader("Authorization", "Bearer " + token);
        }

        enqueue(builder.build(), callback);
    }

    private static void enqueue(Request request, ApiCallback callback) {
        getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError(-1, "Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    String body = response.body() != null ? response.body().string() : "";
                    if (response.isSuccessful()) {
                        mainHandler.post(() -> callback.onSuccess(body));
                    } else {
                        // Try to extract detail from JSON error
                        String errorMsg;
                        try {
                            JsonObject err = gson.fromJson(body, JsonObject.class);
                            errorMsg = err.has("detail") ? err.get("detail").getAsString()
                                    : "Error " + response.code();
                        } catch (Exception ex) {
                            errorMsg = "Error " + response.code() + ": " + body;
                        }
                        String finalMsg = errorMsg;
                        int finalCode = response.code();
                        mainHandler.post(() -> callback.onError(finalCode, finalMsg));
                    }
                } catch (IOException e) {
                    mainHandler.post(() -> callback.onError(-2, "Failed to read response"));
                }
            }
        });
    }
}
