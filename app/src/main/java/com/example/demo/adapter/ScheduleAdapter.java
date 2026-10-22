package com.example.demo.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.demo.R;
import com.example.demo.model.ScheduleItem;

import java.util.ArrayList;
import java.util.List;

public class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(ScheduleItem item);
    }

    private List<ScheduleItem> items = new ArrayList<>();
    private final OnItemClickListener listener;
    private boolean showDayLabel;

    public ScheduleAdapter(List<ScheduleItem> items, OnItemClickListener listener) {
        if (items != null) {
            this.items = items;
        }
        this.listener = listener;
    }

    public void updateData(List<ScheduleItem> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setShowDayLabel(boolean showDayLabel) {
        this.showDayLabel = showDayLabel;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_schedule_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ScheduleItem item = items.get(position);
        Context ctx = holder.itemView.getContext();

        holder.tvStartTime.setText(item.getStartTime());
        holder.tvEndTime.setText(item.getEndTime());
        String subjectLabel = item.getSubjectName();
        if (item.getSubjectCode() != null && !item.getSubjectCode().trim().isEmpty()) {
            subjectLabel = ctx.getString(R.string.schedule_card_subject_class_format,
                    subjectLabel, item.getSubjectCode());
        }
        if (showDayLabel && item.getDayOfWeek() != null) {
            subjectLabel = ctx.getString(R.string.schedule_card_day_subject_format,
                    item.getDayOfWeek(), subjectLabel);
        }
        holder.tvSubjectTag.setText(subjectLabel);
        holder.tvRoom.setText(item.getRoom());
        holder.tvLecturer.setText(item.getLecturer());

        // Apply color theme according to colorType
        String colorType = item.getColorType() != null ? item.getColorType().toLowerCase() : "blue";
        if ("green".equals(colorType)) {
            holder.viewIndicator.setBackgroundResource(R.drawable.bg_indicator_green);
            holder.tvSubjectTag.setBackgroundResource(R.drawable.bg_badge_green);
            holder.tvSubjectTag.setTextColor(ContextCompat.getColor(ctx, R.color.tag_green_text));
        } else if ("amber".equals(colorType) || "orange".equals(colorType)) {
            holder.viewIndicator.setBackgroundResource(R.drawable.bg_indicator_amber);
            holder.tvSubjectTag.setBackgroundResource(R.drawable.bg_badge_amber);
            holder.tvSubjectTag.setTextColor(ContextCompat.getColor(ctx, R.color.tag_amber_text));
        } else if ("violet".equals(colorType) || "purple".equals(colorType)) {
            holder.viewIndicator.setBackgroundResource(R.drawable.bg_indicator_blue);
            holder.tvSubjectTag.setBackgroundResource(R.drawable.bg_badge_violet);
            holder.tvSubjectTag.setTextColor(ContextCompat.getColor(ctx, R.color.tag_violet_text));
        } else {
            holder.viewIndicator.setBackgroundResource(R.drawable.bg_indicator_blue);
            holder.tvSubjectTag.setBackgroundResource(R.drawable.bg_badge_blue);
            holder.tvSubjectTag.setTextColor(ContextCompat.getColor(ctx, R.color.tag_blue_text));
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View viewIndicator;
        final TextView tvStartTime;
        final TextView tvEndTime;
        final TextView tvSubjectTag;
        final TextView tvRoom;
        final TextView tvLecturer;
        final ImageView ivChevron;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            viewIndicator = itemView.findViewById(R.id.viewIndicator);
            tvStartTime = itemView.findViewById(R.id.tvStartTime);
            tvEndTime = itemView.findViewById(R.id.tvEndTime);
            tvSubjectTag = itemView.findViewById(R.id.tvSubjectTag);
            tvRoom = itemView.findViewById(R.id.tvRoom);
            tvLecturer = itemView.findViewById(R.id.tvLecturer);
            ivChevron = itemView.findViewById(R.id.ivChevron);
        }
    }
}
