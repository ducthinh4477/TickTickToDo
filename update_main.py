with open('app/src/main/res/layout/activity_main.xml', 'r', encoding='utf-8') as f:
    text = f.read()

idx = text.find('android:id="@+id/nav_item_school"')
idx2 = text.find('</LinearLayout>', idx) + len('</LinearLayout>')
old_segment = text[text.rfind('<LinearLayout', 0, idx) : idx2]
new_segment = old_segment + '\n\n' + '''          <LinearLayout
              android:id="@+id/nav_item_eisenhower"
              android:layout_width="match_parent"
              android:layout_height="wrap_content"
              android:background="?attr/selectableItemBackground"
              android:clickable="true"
              android:focusable="true"
              android:gravity="center"
              android:orientation="vertical"
              android:paddingTop="10dp"
              android:paddingBottom="10dp">
              <ImageView
                  android:id="@+id/nav_icon_eisenhower"
                  android:layout_width="26dp"
                  android:layout_height="26dp"
                  android:contentDescription="Matrix"
                  android:src="@drawable/ic_matrix"
                  app:tint="@color/text_secondary" />
              <TextView
                  android:id="@+id/nav_label_eisenhower"
                  android:layout_width="wrap_content"
                  android:layout_height="wrap_content"
                  android:layout_marginTop="4dp"
                  android:text="Ma trận"
                  android:textColor="@color/text_secondary"
                  android:textSize="11sp" />
          </LinearLayout>'''

text = text.replace(old_segment, new_segment)

with open('app/src/main/res/layout/activity_main.xml', 'w', encoding='utf-8') as f:
    f.write(text)
