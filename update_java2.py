import re

def update():
    with open('app/src/main/java/hcmute/edu/vn/tickticktodo/ui/EisenhowerActivity.java', 'r', encoding='utf-8') as f:
        content = f.read()

    # 1. Add imports
    imports = """
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import androidx.lifecycle.ViewModelProvider;
import hcmute.edu.vn.tickticktodo.viewmodel.TaskViewModel;
import hcmute.edu.vn.tickticktodo.model.Task;
import java.util.Calendar;
import java.util.List;
import java.util.ArrayList;
"""
    content = re.sub(r'import hcmute\.edu\.vn\.tickticktodo\.R;', f'{imports}\nimport hcmute.edu.vn.tickticktodo.R;', content)

    # 2. Add variables
    vars = """    private LinearLayout llTasksUrgent, llTasksNotUrgent, llTasksNormal, llTasksSlow;
    private TaskViewModel taskViewModel;"""
    
    content = re.sub(r'private ImageButton btnBack;', f'private ImageButton btnBack;\n{vars}', content)

    # 3. Modify initViews
    init_views_add = """
        llTasksUrgent = findViewById(R.id.ll_tasks_urgent);
        llTasksNotUrgent = findViewById(R.id.ll_tasks_not_urgent);
        llTasksNormal = findViewById(R.id.ll_tasks_normal);
        llTasksSlow = findViewById(R.id.ll_tasks_slow);

        taskViewModel = new ViewModelProvider(this).get(TaskViewModel.class);
        taskViewModel.getTodayIncompleteTasks().observe(this, this::updateMatrixes);
        
        fab.setOnClickListener(v -> showAddTaskDialog(-1));
"""
    content = re.sub(r'btnBack\.setOnClickListener\(v -> finish\(\)\);', f'btnBack.setOnClickListener(v -> finish());\n{init_views_add}', content)

    # 4. Add methods to handle updating and showing dialog
    methods = """
    private void updateMatrixes(List<Task> tasks) {
        if (tasks == null) return;
        
        llTasksUrgent.removeAllViews();
        llTasksNotUrgent.removeAllViews();
        llTasksNormal.removeAllViews();
        llTasksSlow.removeAllViews();

        for (Task task : tasks) {
            View taskView = LayoutInflater.from(this).inflate(R.layout.item_eisenhower_task, null);
            TextView title = taskView.findViewById(R.id.tv_eisenhower_title);
            title.setText(task.getTitle());
            
            TextView desc = taskView.findViewById(R.id.tv_eisenhower_desc);
            if (task.getDescription() != null && !task.getDescription().isEmpty()) {
                desc.setVisibility(View.VISIBLE);
                desc.setText(task.getDescription());
            }

            ImageView check = taskView.findViewById(R.id.iv_eisenhower_check);
            check.setOnClickListener(v -> {
                task.setCompleted(true);
                task.setCompletedDate(System.currentTimeMillis());
                taskViewModel.update(task);
            });

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            
            taskView.setLayoutParams(params);

            switch (task.getPriority()) {
                case 3: llTasksUrgent.addView(taskView); break;
                case 2: llTasksNotUrgent.addView(taskView); break;
                case 1: llTasksNormal.addView(taskView); break;
                case 0: llTasksSlow.addView(taskView); break;
            }
        }
    }

    private void showAddTaskDialog(int quadrantIndex) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_eisenhower_add);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        EditText etTitle = dialog.findViewById(R.id.et_eisenhower_title);
        EditText etDesc = dialog.findViewById(R.id.et_eisenhower_desc);
        Spinner spinner = dialog.findViewById(R.id.spinner_eisenhower_quadrant);
        Button btnCancel = dialog.findViewById(R.id.btn_eisenhower_cancel);
        Button btnSave = dialog.findViewById(R.id.btn_eisenhower_save);

        String[] options = {"1. Khẩn cấp & Quan trọng", "2. Quan trọng, ko gắp", "3. Gấp, ko quan trọng", "4. Ko gấp, ko quan trọng"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        if (quadrantIndex >= 0 && quadrantIndex <= 3) {
            spinner.setSelection(quadrantIndex);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            if (title.isEmpty()) return;
            
            int priority = 0; // mapping from spinner to priority
            switch (spinner.getSelectedItemPosition()) {
                case 0: priority = 3; break;
                case 1: priority = 2; break;
                case 2: priority = 1; break;
                case 3: priority = 0; break;
            }

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            
            Task t = new Task(title, etDesc.getText().toString().trim(), cal.getTimeInMillis(), false, 0);
            t.setPriority(priority);
            taskViewModel.insert(t);
            
            dialog.dismiss();
        });

        dialog.show();
    }
"""

    # We need to replace handleDrop logic to show the dialog
    handle_drop_old = r'private void handleDrop\(float rawX, float rawY\) \{[\s\S]*?\}    \}'
    handle_drop_new = """private void handleDrop(float rawX, float rawY) {
        int quadrantIndex = -1;

        if (isViewContains(quadrantUrgent, rawX, rawY)) {
            quadrantIndex = 0;
        } else if (isViewContains(quadrantNotUrgent, rawX, rawY)) {
            quadrantIndex = 1;
        } else if (isViewContains(quadrantNormal, rawX, rawY)) {
            quadrantIndex = 2;
        } else if (isViewContains(quadrantSlow, rawX, rawY)) {
            quadrantIndex = 3;
        }

        if (quadrantIndex != -1) {
            showAddTaskDialog(quadrantIndex);
        }
    }"""
    
    content = re.sub(handle_drop_old, handle_drop_new, content)
    
    # inject methods before the last bracket
    last_bracket_idx = content.rfind('}')
    content = content[:last_bracket_idx] + methods + content[last_bracket_idx:]

    with open('app/src/main/java/hcmute/edu/vn/tickticktodo/ui/EisenhowerActivity.java', 'w', encoding='utf-8') as f:
        f.write(content)

if __name__ == '__main__':
    update()