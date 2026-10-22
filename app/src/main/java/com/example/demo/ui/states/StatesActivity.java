package com.example.demo.ui.states;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.demo.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class StatesActivity extends AppCompatActivity {

    private static class StateItem {
        final int iconRes;
        final int iconColorRes;
        final int bgBoxRes;
        final String title;
        final String desc;
        final String action;

        StateItem(int iconRes, int iconColorRes, int bgBoxRes, String title, String desc, String action) {
            this.iconRes = iconRes;
            this.iconColorRes = iconColorRes;
            this.bgBoxRes = bgBoxRes;
            this.title = title;
            this.desc = desc;
            this.action = action;
        }
    }

    private LinearLayout layoutStateTabs;
    private FrameLayout boxStateIcon;
    private ImageView ivStateIcon;
    private TextView tvStateTitle;
    private TextView tvStateDesc;
    private MaterialButton btnStateAction;

    private final List<MaterialButton> tabButtons = new ArrayList<>();
    private final List<StateItem> states = new ArrayList<>();
    private int currentStateIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_states);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        layoutStateTabs = findViewById(R.id.layoutStateTabs);
        boxStateIcon = findViewById(R.id.boxStateIcon);
        ivStateIcon = findViewById(R.id.ivStateIcon);
        tvStateTitle = findViewById(R.id.tvStateTitle);
        tvStateDesc = findViewById(R.id.tvStateDesc);
        btnStateAction = findViewById(R.id.btnStateAction);

        initStatesData();
        buildTabs();
        showState(0);

        btnStateAction.setOnClickListener(v -> {
            Toast.makeText(this, "Hành động: " + states.get(currentStateIndex).action, Toast.LENGTH_SHORT).show();
            int next = (currentStateIndex + 1) % states.size();
            showState(next);
        });
    }

    private void initStatesData() {
        states.add(new StateItem(R.drawable.ic_nav_book, R.color.primary, R.drawable.bg_round_icon_blue,
                "Chưa có môn học", "Thêm môn học đầu tiên để bắt đầu tạo thời khóa biểu.", "Thêm môn học"));
        states.add(new StateItem(R.drawable.ic_nav_calendar, R.color.tag_green, R.drawable.bg_round_icon_green,
                "Chưa có lịch học", "Ngày đã chọn chưa có buổi học nào.", "Thêm lịch học"));
        states.add(new StateItem(R.drawable.ic_search, R.color.tag_amber, R.drawable.bg_round_icon_amber,
                "Không có kết quả", "Thử thay đổi từ khóa hoặc bộ lọc tìm kiếm.", "Xóa bộ lọc"));
        states.add(new StateItem(R.drawable.ic_database, R.color.tag_violet, R.drawable.bg_round_icon_violet,
                "Không thể tải dữ liệu", "Đã xảy ra lỗi khi đọc cơ sở dữ liệu. Vui lòng thử lại.", "Thử lại"));
        states.add(new StateItem(R.drawable.ic_bell, R.color.primary, R.drawable.bg_round_icon_blue,
                "Chưa cấp quyền", "Cấp quyền thông báo trong cài đặt Android để nhận lời nhắc.", "Cấp quyền"));
        states.add(new StateItem(R.drawable.ic_download, R.color.tag_amber, R.drawable.bg_round_icon_amber,
                "Xuất tệp thất bại", "Không thể lưu tệp. Kiểm tra dung lượng và thử lại.", "Thử lại"));
        states.add(new StateItem(R.drawable.ic_refresh, R.color.tag_violet, R.drawable.bg_round_icon_violet,
                "Tệp sao lưu không hợp lệ", "Tệp đã chọn bị hỏng hoặc không thuộc UniSchedule.", "Chọn tệp khác"));
    }

    private void buildTabs() {
        tabButtons.clear();
        layoutStateTabs.removeAllViews();

        int sizeInPx = (int) (38 * getResources().getDisplayMetrics().density);
        int marginInPx = (int) (6 * getResources().getDisplayMetrics().density);

        for (int i = 0; i < states.size(); i++) {
            final int index = i;
            MaterialButton btn = new MaterialButton(this);
            btn.setText(String.valueOf(i + 1));
            btn.setTextSize(13);
            btn.setCornerRadius((int) (8 * getResources().getDisplayMetrics().density));
            btn.setPadding(0, 0, 0, 0);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizeInPx, sizeInPx);
            if (i > 0) lp.setMarginStart(marginInPx);
            btn.setLayoutParams(lp);

            btn.setOnClickListener(v -> showState(index));

            tabButtons.add(btn);
            layoutStateTabs.addView(btn);
        }
    }

    private void showState(int index) {
        currentStateIndex = index;
        StateItem item = states.get(index);

        boxStateIcon.setBackgroundResource(item.bgBoxRes);
        ivStateIcon.setImageResource(item.iconRes);
        ivStateIcon.setColorFilter(ContextCompat.getColor(this, item.iconColorRes));
        tvStateTitle.setText(item.title);
        tvStateDesc.setText(item.desc);
        btnStateAction.setText(item.action);
        btnStateAction.setTextColor(ContextCompat.getColor(this, item.iconColorRes));
        btnStateAction.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this, item.iconColorRes)));

        int primaryColor = ContextCompat.getColor(this, R.color.primary);
        int whiteColor = ContextCompat.getColor(this, R.color.white);
        int secondaryColor = ContextCompat.getColor(this, R.color.text_secondary);
        int transparent = ContextCompat.getColor(this, R.color.transparent);
        int borderColor = ContextCompat.getColor(this, R.color.border_light);

        for (int i = 0; i < tabButtons.size(); i++) {
            MaterialButton btn = tabButtons.get(i);
            if (i == index) {
                btn.setBackgroundTintList(ColorStateList.valueOf(primaryColor));
                btn.setTextColor(whiteColor);
                btn.setStrokeColor(ColorStateList.valueOf(transparent));
            } else {
                btn.setBackgroundTintList(ColorStateList.valueOf(transparent));
                btn.setTextColor(secondaryColor);
                btn.setStrokeColor(ColorStateList.valueOf(borderColor));
            }
        }
    }
}
