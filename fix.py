import re

with open('app/src/main/java/hcmute/edu/vn/tickticktodo/service/FloatingAssistantService.java', 'r', encoding='utf-8') as f:
    text = f.read()

# Fix the literal \n
text = text.replace('";\\n    public static final', '";\n    public static final')

# Fix corrupted strings (because Powershell outputted UTF-8 as ISO-8859-1 or whatever)
text = text.replace('Báºt / Táº¯t trá»£ lĂ½', 'Bật / Tắt trợ lý')
text = text.replace('Trá»£ lĂ½ AI TickTickToDo', 'Trợ lý AI TickTickToDo')
text = text.replace('Trá»£ lĂ½ ná»•i Ä‘ang hoáº¡t Ä‘á»™ng', 'Trợ lý nổi đang hoạt động')

with open('app/src/main/java/hcmute/edu/vn/tickticktodo/service/FloatingAssistantService.java', 'w', encoding='utf-8') as f:
    f.write(text)

