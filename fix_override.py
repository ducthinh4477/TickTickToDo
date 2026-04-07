import re

with open('app/src/main/java/hcmute/edu/vn/tickticktodo/service/FloatingAssistantService.java', 'r', encoding='utf-8') as f:
    text = f.read()

# Fix the duplicate @Override
text = text.replace('    @Override\n    @Override\n    public void onConfigurationChanged', '    @Override\n    public void onConfigurationChanged')

with open('app/src/main/java/hcmute/edu/vn/tickticktodo/service/FloatingAssistantService.java', 'w', encoding='utf-8') as f:
    f.write(text)

