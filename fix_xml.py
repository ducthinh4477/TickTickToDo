import re

layout_add = "app/src/main/res/layout/bottom_sheet_add_task.xml"

attachments_ui = """
        <!-- Attachments Section -->
        <LinearLayout
            android:id="@+id/ll_attachments"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:visibility="gone"
            android:layout_marginTop="8dp">

            <ImageView
                android:id="@+id/iv_attachment_image"
                android:layout_width="match_parent"
                android:layout_height="200dp"
                android:scaleType="centerCrop"
                android:visibility="gone"
                android:layout_marginBottom="8dp"/>

            <!-- Voice Player -->
            <LinearLayout
                android:id="@+id/ll_voice_player"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:gravity="center_vertical"
                android:visibility="gone"
                android:background="@drawable/bg_dialog_rounded"
                android:padding="8dp"
                android:layout_marginBottom="8dp">
                
                <ImageView
                    android:id="@+id/iv_play_pause"
                    android:layout_width="32dp"
                    android:layout_height="32dp"
                    android:src="@drawable/ic_play"
                    app:tint="@color/text_primary"/>
                    
                <SeekBar
                    android:id="@+id/sb_voice_progress"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"/>
                    
                <TextView
                    android:id="@+id/tv_voice_duration"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="00:00"
                    android:textColor="@color/text_secondary"/>
            </LinearLayout>

            <TextView
                android:id="@+id/tv_attachment_file"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:visibility="gone"
                android:padding="12dp"
                android:background="@drawable/bg_dialog_rounded"
                android:textColor="@color/text_primary"
                android:layout_marginBottom="8dp"/>
        </LinearLayout>
"""

with open(layout_add, "r", encoding="utf-8") as f:
    text = f.read()

# Insert before layout_extra_options
if "ll_attachments" not in text:
    text = text.replace('<LinearLayout\n        android:id="@+id/layout_extra_options"', attachments_ui + '\n    <LinearLayout\n        android:id="@+id/layout_extra_options"')

with open(layout_add, "w", encoding="utf-8") as f:
    f.write(text)

layout_det = "app/src/main/res/layout/bottom_sheet_task_detail.xml"
with open(layout_det, "r", encoding="utf-8") as f:
    text_det = f.read()

if "ll_attachments" not in text_det:
    # insert before <TextView android:id="@+id/tv_task_created_date" or after et_task_description
    det_attachment_ui = attachments_ui.replace('android:visibility="gone"', 'android:visibility="gone"')
    text_det = re.sub(r'(<EditText[^>]+android:id="@+id/et_task_description"[^>]+/>)', r'\1\n' + det_attachment_ui, text_det)

with open(layout_det, "w", encoding="utf-8") as f:
    f.write(text_det)
