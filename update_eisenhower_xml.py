import re
import sys

def modify():
    try:
        with open('app/src/main/res/layout/activity_eisenhower.xml', 'r', encoding='utf-8') as f:
            content = f.read()

        # We will replace the "Không có Nhiệm vụ" TextView with a ScrollView containing a LinearLayout
        # For quadrant 1:
        pattern = r'<TextView\s+android:layout_width="match_parent"\s+android:layout_height="match_parent"\s+android:text="Không có Nhiệm vụ"[\s\S]*?/>'
        
        replacement = '''<androidx.core.widget.NestedScrollView
                android:layout_width="match_parent"
                android:layout_height="0dp"
                android:layout_weight="1"
                android:layout_marginTop="8dp">
                <LinearLayout
                    android:id="@+id/ll_tasks_{quadrant}"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical" />
            </androidx.core.widget.NestedScrollView>'''
            
        def repl(match):
            nonlocal count
            quadrants = ['urgent', 'not_urgent', 'normal', 'slow']
            res = replacement.format(quadrant=quadrants[count])
            count += 1
            return res
            
        count = 0
        new_content = re.sub(pattern, repl, content)
        
        with open('app/src/main/res/layout/activity_eisenhower.xml', 'w', encoding='utf-8') as f:
            f.write(new_content)
        
        print("Replaced", count, "occurrences")
    except Exception as e:
        print(f"Error: {e}")

if __name__ == '__main__':
    modify()