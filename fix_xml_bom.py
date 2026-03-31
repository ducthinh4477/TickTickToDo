import os

def remove_bom(file_path):
    with open(file_path, 'rb') as f:
        content = f.read()
    
    if content.startswith(b'\xef\xbb\xbf'):
        print(f"Removing BOM from {file_path}")
        content = content[3:]
        with open(file_path, 'wb') as f:
            f.write(content)
        return True
    return False

layout_dir = 'app/src/main/res/layout'
count = 0
for root, _, files in os.walk(layout_dir):
    for f in files:
        if f.endswith('.xml'):
            if remove_bom(os.path.join(root, f)):
                count += 1
                
print(f"Total BOM removed: {count}")
