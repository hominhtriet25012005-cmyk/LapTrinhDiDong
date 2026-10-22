package com.example.demo.model;

public class QuickActionItem {
    private String title;
    private int iconRes;
    private int backgroundRes;
    private int iconColorRes;
    private String actionKey;

    public QuickActionItem(String title, int iconRes, int backgroundRes, int iconColorRes, String actionKey) {
        this.title = title;
        this.iconRes = iconRes;
        this.backgroundRes = backgroundRes;
        this.iconColorRes = iconColorRes;
        this.actionKey = actionKey;
    }

    public String getTitle() { return title; }
    public int getIconRes() { return iconRes; }
    public int getBackgroundRes() { return backgroundRes; }
    public int getIconColorRes() { return iconColorRes; }
    public String getActionKey() { return actionKey; }
}
