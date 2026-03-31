import re

def update():
    with open('app/src/main/res/layout/activity_main.xml', 'r', encoding='utf-8') as f:
        content = f.read()

    settings_idx = content.find('android:id="@+id/nav_item_settings"')
    if settings_idx == -1: return

    # find the preceding <LinearLayout
    lin_idx = content.rfind('<LinearLayout', 0, settings_idx)

    insert_layout = '''<LinearLayout
              android:id="@+id/nav_item_countdown"
              android:layout_width="match_parent"
              android:layout_height="wrap_content"
              android:background="?attr/selectableItemBackground"
              android:clickable="true"
              android:focusable="true"
              android:gravity="center_vertical"
              android:orientation="horizontal"
              android:paddingHorizontal="24dp"
              android:paddingVertical="12dp">

              <ImageView
                  android:layout_width="24dp"
                  android:layout_height="24dp"
                  android:src="@drawable/ic_timer"
                  app:tint="@color/text_secondary"
                  android:contentDescription="@null" />

              <TextView
                  android:layout_width="wrap_content"
                  android:layout_height="wrap_content"
                  android:layout_marginStart="16dp"
                  android:text="Đếm ngược"
                  android:textColor="@color/text_primary"
                  android:textSize="14sp" />
          </LinearLayout>

          '''

    new_content = content[:lin_idx] + insert_layout + content[lin_idx:]

    with open('app/src/main/res/layout/activity_main.xml', 'w', encoding='utf-8') as f:
        f.write(new_content)

if __name__ == '__main__':
    update()