package com.example.demo.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.demo.R;
import com.example.demo.model.SemesterItem;

import java.util.ArrayList;
import java.util.List;

public class SemesterAdapter extends RecyclerView.Adapter<SemesterAdapter.ViewHolder> {

    public interface OnSemesterClickListener {
        void onSemesterClick(SemesterItem semester);
    }

    private List<SemesterItem> items = new ArrayList<>();
    private final OnSemesterClickListener listener;

    public SemesterAdapter(List<SemesterItem> items, OnSemesterClickListener listener) {
        if (items != null) {
            this.items = items;
        }
        this.listener = listener;
    }

    public void updateData(List<SemesterItem> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_semester_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SemesterItem item = items.get(position);

        holder.tvSemesterName.setText(item.getName());
        String academicYear = item.getAcademicYear();
        holder.tvAcademicYear.setText(academicYear == null || academicYear.trim().isEmpty()
                || "—".equals(academicYear) ? "" : "· " + academicYear);
        holder.tvDateRange.setText(item.getPeriod());
        holder.tvSubjectCount.setText(item.getSubjectCount() + " môn học");

        if (item.isCurrent()) {
            holder.tvCurrentBadge.setVisibility(View.VISIBLE);
        } else {
            holder.tvCurrentBadge.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSemesterClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvSemesterName;
        final TextView tvAcademicYear;
        final TextView tvCurrentBadge;
        final TextView tvDateRange;
        final TextView tvSubjectCount;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSemesterName = itemView.findViewById(R.id.tvSemesterName);
            tvAcademicYear = itemView.findViewById(R.id.tvAcademicYear);
            tvCurrentBadge = itemView.findViewById(R.id.tvCurrentBadge);
            tvDateRange = itemView.findViewById(R.id.tvDateRange);
            tvSubjectCount = itemView.findViewById(R.id.tvSubjectCount);
        }
    }
}
