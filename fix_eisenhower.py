import os

path = 'app/src/main/java/hcmute/edu/vn/tickticktodo/ui/EisenhowerActivity.java'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

old_code = """    private void handleDrop(float rawX, float rawY) {
        String quadrantName = "";
        
        if (isViewContains(quadrantUrgent, rawX, rawY)) {
            quadrantName = "Khẩn cấp";
        } else if (isViewContains(quadrantNotUrgent, rawX, rawY)) {
            quadrantName = "Không gấp";
        } else if (isViewContains(quadrantNormal, rawX, rawY)) {
            quadrantName = "Bình thường";
        } else if (isViewContains(quadrantSlow, rawX, rawY)) {
            quadrantName = "Từ từ làm";
        }

        if (!quadrantName.isEmpty()) {
            Toast.makeText(this, "Đã thả vào ô: " + quadrantName, Toast.LENGTH_SHORT).show();
        }
    }"""

new_code = """    private void handleDrop(float rawX, float rawY) {
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

if old_code in text:
    print("Found old code")
else:
    print("Did not find old code")

text = text.replace(old_code, new_code)

old_observe = 'taskViewModel.getTodayAllTasks().observe(this, this::updateMatrixes);'
new_observe = 'taskViewModel.getTodayIncompleteTasks().observe(this, this::updateMatrixes);'
text = text.replace(old_observe, new_observe)

with open(path, 'w', encoding='utf-8') as f:
    f.write(text)
print("Updated EisenhowerActivity")
