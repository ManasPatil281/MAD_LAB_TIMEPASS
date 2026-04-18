package com.example.myapplication;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TablePlugin;

public class ResultBottomSheetFragment extends BottomSheetDialogFragment {

    private static final String ARG_TITLE = "title";
    private static final String ARG_CONTENT = "content";

    private String title;
    private String content;
    private Markwon markwon;

    public static ResultBottomSheetFragment newInstance(String title, String content) {
        ResultBottomSheetFragment fragment = new ResultBottomSheetFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_CONTENT, content);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            title = getArguments().getString(ARG_TITLE);
            content = getArguments().getString(ARG_CONTENT);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.layout_result_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView tvTitle = view.findViewById(R.id.tv_result_title);
        TextView tvContent = view.findViewById(R.id.tv_result_content);
        Button btnCopy = view.findViewById(R.id.btn_copy);
        Button btnDownload = view.findViewById(R.id.btn_download);
        Button btnDismiss = view.findViewById(R.id.btn_dismiss);

        View ivClose = view.findViewById(R.id.iv_close);

        tvTitle.setText(title);

        markwon = Markwon.builder(requireContext())
                .usePlugin(TablePlugin.create(requireContext()))
                .build();

        markwon.setMarkdown(tvContent, content);

        if (ivClose != null) {
            ivClose.setOnClickListener(v -> dismiss());
        }

        btnCopy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Result Content", content);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show();
        });

        btnDownload.setOnClickListener(v -> {
            try {
                String fileName = "ScholarHub_Result_" + System.currentTimeMillis() + ".txt";
                java.io.File downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                java.io.File file = new java.io.File(downloadDir, fileName);
                java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
                fos.write(content.getBytes());
                fos.close();
                Toast.makeText(requireContext(), "Saved to Downloads: " + fileName, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Failed to download", Toast.LENGTH_SHORT).show();
            }
        });

        btnDismiss.setOnClickListener(v -> dismiss());
    }

    @Override
    public int getTheme() {
        return R.style.CustomBottomSheetDialogTheme;
    }
}
