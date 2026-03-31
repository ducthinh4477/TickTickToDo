import re
with open('app/src/main/java/hcmute/edu/vn/tickticktodo/ui/EisenhowerActivity.java', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('private TextView tvTitle;', 'private View appBar;')
content = content.replace('tvTitle = findViewById(R.id.tv_eisenhower_title);', 'appBar = findViewById(R.id.app_bar);')

new_insets = """    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(appBar, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(view.getPaddingLeft(), insets.top, view.getPaddingRight(), view.getPaddingBottom());
            return WindowInsetsCompat.CONSUMED;
        });
    }"""

content = re.sub(r'    private void applyWindowInsets\(\) \{[\s\S]*?    \}', new_insets, content)

with open('app/src/main/java/hcmute/edu/vn/tickticktodo/ui/EisenhowerActivity.java', 'w', encoding='utf-8') as f:
    f.write(content)
