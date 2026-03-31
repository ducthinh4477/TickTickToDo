import re

def fix_override(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        text = f.read()
    
    text = re.sub(r'@Override\s+@Override', r'@Override', text)

    with open(file_path, "w", encoding="utf-8") as f:
        f.write(text)

fix_override("app/src/main/java/hcmute/edu/vn/tickticktodo/ui/AddTaskBottomSheet.java")
fix_override("app/src/main/java/hcmute/edu/vn/tickticktodo/ui/TaskDetailBottomSheet.java")

print("Fixed overrides.")