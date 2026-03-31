import os
p='app/src/main/res/layout/activity_eisenhower.xml'
data=open(p,'rb').read()
if data.startswith(b'\xef\xbb\xbf'):
    data = data[3:]
    open(p,'wb').write(data)
    print("Fixed BOM")
else:
    print("No BOM found")
