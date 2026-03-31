import re

file_add = "app/src/main/res/layout/bottom_sheet_add_task.xml"

with open(file_add, "r", encoding="utf-8") as f:
    text_add = f.read()

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

# Insert attachments_ui before the options linear layout
if 'android:id="@+id/ll_task_options"' in text_add:
    text_add = text_add.replace('<LinearLayout\n            android:id="@+id/ll_task_options"', attachments_ui + '\n        <LinearLayout\n            android:id="@+id/ll_task_options"')
    print("Added to bottom_sheet_add_task")

    # Add buttons logic to the task options
    buttons = """
                <ImageView
                    android:id="@+id/btn_attach_image"
                    android:layout_width="24dp"
                    android:layout_height="24dp"
                    android:layout_marginEnd="16dp"
                    android:src="@drawable/ic_image"
                    app:tint="@color/text_disabled"/>

                <ImageView
                    android:id="@+id/btn_attach_voice"
                    android:layout_width="24dp"
                    android:layout_height="24dp"
                    android:layout_marginEnd="16dp"
                    android:src="@drawable/ic_mic"
                    app:tint="@color/text_disabled"/>

                <ImageView
                    android:id="@+id/btn_attach_file"
                    android:layout_width="24dp"
                    android:layout_height="24dp"
                    android:layout_marginEnd="16dp"
                    android:src="@drawable/ic_folder"
                    app:tint="@color/text_disabled"/>
    """
    
    text_add = text_add.replace('<ImageView\n                    android:id="@+id/btn_task_flag"', buttons + '\n                <ImageView\n                    android:id="@+id/btn_task_flag"')


with open(file_add, "w", encoding="utf-8") as f:
    f.write(text_add)

file_det = "app/src/main/res/layout/bottom_sheet_task_detail.xml"

with open(file_det, "r", encoding="utf-8") as f:
    text_det = f.read()

# For bottom_sheet_task_detail.xml we also want the attachments sections, and under description
# let's find the description EditText
det_attachment_ui = attachments_ui.replace("gone", "visible").replace('android:visibility="visible"', 'android:visibility="gone"')

if 'android:id="@+id/et_task_description"' in text_det:
    text_det = re.sub(r'(<EditText\n\s*android:id="@+id/et_task_description"[^>]+/>)', r'\1\n' + det_attachment_ui, text_det)
    print("Added to bottom_sheet_task_detail at description")

with open(file_det, "w", encoding="utf-8") as f:
    f.write(text_det)

