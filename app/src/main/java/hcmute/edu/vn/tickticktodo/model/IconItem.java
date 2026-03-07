package hcmute.edu.vn.tickticktodo.model;

/**
 * Model đơn giản đại diện cho một lựa chọn icon trong AddListDialog.
 *
 * @param drawableResId  R.drawable.* của icon (ví dụ R.drawable.ic_work)
 * @param labelResId     R.string.* tên hiển thị (ví dụ R.string.icon_name_work)
 */
public class IconItem {
    private final int drawableResId;
    private final int labelResId;

    public IconItem(int drawableResId, int labelResId) {
        this.drawableResId = drawableResId;
        this.labelResId = labelResId;
    }

    public int getDrawableResId() { return drawableResId; }
    public int getLabelResId()    { return labelResId; }
}

