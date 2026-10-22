package com.example.demo.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.demo.R;
import com.example.demo.model.SubjectItem;

import java.util.ArrayList;
import java.util.List;

public class SubjectAdapter extends RecyclerView.Adapter<SubjectAdapter.ViewHolder> {

    public interface OnSubjectClickListener {
        void onSubjectClick(SubjectItem subject);
    }

    private List<SubjectItem> items = new ArrayList<>();
    private final OnSubjectClickListener listener;

    public SubjectAdapter(List<SubjectItem> items, OnSubjectClickListener listener) {
        if (items != null) {
            this.items = items;
        }
        this.listener = listener;
    }

    public void updateData(List<SubjectItem> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_subject_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SubjectItem item = items.get(position);
        Context ctx = holder.itemView.getContext();

        holder.tvInitials.setText(item.getInitials());
        holder.tvCode.setText(item.getCode());
        holder.tvName.setText(item.getName());
        String lecturer = item.getLecturer() == null || item.getLecturer().trim().isEmpty()
                ? ctx.getString(R.string.value_not_set)
                : item.getLecturer();
        holder.tvSubtitle.setText(ctx.getString(
                R.string.subject_credits_lecturer_format, item.getCredits(), lecturer));

        // Set color for avatar box
        String color = item.getColorType() != null ? item.getColorType().toLowerCase() : "blue";
        if ("green".equals(color)) {
            holder.layoutIconBox.setBackgroundResource(R.drawable.bg_round_icon_green);
            holder.tvInitials.setTextColor(ContextCompat.getColor(ctx, R.color.tag_green_text));
        } else if ("amber".equals(color) || "orange".equals(color)) {
            holder.layoutIconBox.setBackgroundResource(R.drawable.bg_round_icon_amber);
            holder.tvInitials.setTextColor(ContextCompat.getColor(ctx, R.color.tag_amber_text));
        } else if ("violet".equals(color) || "purple".equals(color)) {
            holder.layoutIconBox.setBackgroundResource(R.drawable.bg_round_icon_violet);
            holder.tvInitials.setTextColor(ContextCompat.getColor(ctx, R.color.tag_violet_text));
        } else {
            holder.layoutIconBox.setBackgroundResource(R.drawable.bg_round_icon_blue);
            holder.tvInitials.setTextColor(ContextCompat.getColor(ctx, R.color.tag_blue_text));
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSubjectClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final FrameLayout layoutIconBox;
        final TextView tvInitials;
        final TextView tvCode;
        final TextView tvName;
        final TextView tvSubtitle;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutIconBox = itemView.findViewById(R.id.layoutIconBox);
            tvInitials = itemView.findViewById(R.id.tvInitials);
            tvCode = itemView.findViewById(R.id.tvCode);
            tvName = itemView.findViewById(R.id.tvName);
            tvSubtitle = itemView.findViewById(R.id.tvSubtitle);
        }
    }
}
