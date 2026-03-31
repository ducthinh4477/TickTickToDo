import re

def modify():
    with open('app/src/main/res/layout/activity_eisenhower.xml', 'r', encoding='utf-8') as f:
        content = f.read()

    # Find where the fourth header ends
    header_idx = content.find('android:text="Không cấp bách và ko q.trọng"')
    lin_end = content.find('</LinearLayout>', header_idx) + 15

    # Replace everything between the header's end and the CardView end
    card_end = content.find('</androidx.cardview.widget.CardView>', lin_end)
    
    # We want to replace between lin_end and card_end
    # Wait, the card view contains a parent LinearLayout also!
    
    # Actually, it's:
    # <LinearLayout ... > (Parent)
    #    <LinearLayout> Header </LinearLayout>
    #    --- WE REPLACE HERE ---
    # </LinearLayout> (Parent End)
    # </CardView>
    
    parent_lin_end = content.rfind('</LinearLayout>', 0, card_end)
    
    replacement = '''
            <androidx.core.widget.NestedScrollView
                android:layout_width="match_parent"
                android:layout_height="0dp"
                android:layout_weight="1"
                android:layout_marginTop="8dp">
                <LinearLayout
                    android:id="@+id/ll_tasks_slow"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical" />
            </androidx.core.widget.NestedScrollView>
        '''
        
    new_content = content[:lin_end] + replacement + content[parent_lin_end:]
    
    with open('app/src/main/res/layout/activity_eisenhower.xml', 'w', encoding='utf-8') as f:
        f.write(new_content)
        
if __name__ == '__main__':
    modify()