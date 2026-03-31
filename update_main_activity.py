import re
import os

MAIN_ACTIVITY_PATH = "app/src/main/java/hcmute/edu/vn/tickticktodo/MainActivity.java"

with open(MAIN_ACTIVITY_PATH, "r", encoding="utf-8") as f:
    content = f.read()

# Replace Inbox listener
old_inbox = 'findViewById(R.id.panel_item_inbox).setOnClickListener(v ->\n                onPanelItemSelected(getString(R.string.panel_inbox)));'
new_inbox = 'findViewById(R.id.panel_item_inbox).setOnClickListener(v -> showDeveloperMessageDialog());'

content = content.replace(old_inbox, new_inbox)

# If it is slightly formatted differently
old_inbox_alt = 'findViewById(R.id.panel_item_inbox).setOnClickListener(v -> onPanelItemSelected(getString(R.string.panel_inbox)));'
content = content.replace(old_inbox_alt, new_inbox)
content = re.sub(r'findViewById\(R\.id\.panel_item_inbox\)\.setOnClickListener\(v\s*->\s*onPanelItemSelected\(getString\(R\.string\.panel_inbox\)\)\);', new_inbox, content)

# Replace Completed listener
old_comp = r'findViewById\(R\.id\.panel_item_completed\)\.setOnClickListener\(v\s*->\s*onPanelItemSelected\(getString\(R\.string\.menu_completed\)\)\);'
new_comp = r'findViewById(R.id.panel_item_completed).setOnClickListener(v -> showHistoryDialog("Nhật ký: Đã hoàn thành", taskViewModel.getAllCompletedTasksLog()));'
content = re.sub(old_comp, new_comp, content)

# Replace Trash listener
old_trash = r'findViewById\(R\.id\.panel_item_trash\)\.setOnClickListener\(v\s*->\s*onPanelItemSelected\(getString\(R\.string\.menu_trash\)\)\);'
new_trash = r'findViewById(R.id.panel_item_trash).setOnClickListener(v -> showHistoryDialog("Nhật ký: Quá hạn", taskViewModel.getAllOverdueTasksLog()));'
content = re.sub(old_trash, new_trash, content)

# Inject dialog methods
dialog_methods = """
    private void showHistoryDialog(String title, androidx.lifecycle.LiveData<java.util.List<hcmute.edu.vn.tickticktodo.database.Task>> liveData) {
        closeMenu();
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.setContentView(R.layout.dialog_history_log);
        
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = (int) (metrics.widthPixels * 0.75);
        int height = (int) (metrics.heightPixels * 0.75);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(width, height);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        android.widget.TextView tvTitle = dialog.findViewById(R.id.tv_dialog_title);
        tvTitle.setText(title);
        
        androidx.recyclerview.widget.RecyclerView rv = dialog.findViewById(R.id.rv_history_log);
        rv.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        
        hcmute.edu.vn.tickticktodo.adapter.TaskAdapter adapter = new hcmute.edu.vn.tickticktodo.adapter.TaskAdapter(
            (task, isChecked) -> {}, 
            task -> {}
        );
        rv.setAdapter(adapter);
        
        liveData.observe(this, tasks -> {
            if (tasks != null) {
                adapter.submitList(tasks);
            }
        });
        
        dialog.findViewById(R.id.btn_close_dialog).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showDeveloperMessageDialog() {
        closeMenu();
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Tin nhắn từ nhà phát triển")
            .setMessage("Cảm ơn bạn đã sử dụng TickTickToDo! Phiên bản này đang trong quá trình thử nghiệm. Các tính năng mở rộng sẽ sớm ra mắt.")
            .setPositiveButton("Đóng", (d, w) -> d.dismiss())
            .show();
    }
"""

# Insert before `private void showLoginDialog()`
content = content.replace("private void showLoginDialog() {", dialog_methods + "\n    private void showLoginDialog() {")


with open(MAIN_ACTIVITY_PATH, "w", encoding="utf-8") as f:
    f.write(content)

print("Main Activity Updated")
