package com.example.demo.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.demo.R;
import com.example.demo.model.QuickActionItem;

import java.util.List;

public class QuickActionAdapter extends RecyclerView.Adapter<QuickActionAdapter.ViewHolder> {

    public interface OnQuickActionClickListener {
        void onQuickActionClick(QuickActionItem action);
    }

    private final List<QuickActionItem> items;
    private final OnQuickActionClickListener listener;

    public QuickActionAdapter(List<QuickActionItem> items, OnQuickActionClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_quick_action, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        QuickActionItem item = items.get(position);
        Context ctx = holder.itemView.getContext();

        holder.tvTitle.setText(item.getTitle());
        holder.layoutIconContainer.setBackgroundResource(item.getBackgroundRes());
        holder.ivIcon.setImageResource(item.getIconRes());
        holder.ivIcon.setColorFilter(ContextCompat.getColor(ctx, item.getIconColorRes()));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onQuickActionClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final FrameLayout layoutIconContainer;
        final ImageView ivIcon;
        final TextView tvTitle;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutIconContainer = itemView.findViewById(R.id.layoutIconContainer);
            ivIcon = itemView.findViewById(R.id.ivIcon);
            tvTitle = itemView.findViewById(R.id.tvTitle);
        }
    }
}
