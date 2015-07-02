import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
//import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.NinePatchDrawable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.annotation.Nullable;

/**
 * Created by zhangyuanwei on 15/7/1.
 */
public class BorderDrawable extends Drawable {

    Resources mResources;
    String mName;

    @Nullable
    private Drawable mDrawable;
    @Nullable
    private Bitmap mBitmap;
    private YASizeF mBitmapSize;
    private Canvas mCanvas;
    private Rect mBounds = new Rect();

    private FloatSpacing mBorderInsetsSpacing = new FloatSpacing();
    private FloatSpacing mBorderRadiiSpacing = new FloatSpacing();
    private ColorSpacing mBorderColorsSpacing = new ColorSpacing();

    private Paint mPaint;
    private Path mPath;

    private BorderInsets mBorderInsets;
    private BorderColors mBorderColors;
    private CornerRadii mCornerRadii;

    private CornerInsets mCornerInsets;
    private BorderInsets mEdgeInsets;

    private int mBackgroundColor = Color.TRANSPARENT;
    private boolean mIsDirty = false;

    public BorderDrawable(Resources res, String name) {
        super();
        mResources = res;
        mName = name;
    }

    public void setBorderWidth(int spacingType, float border) {
        if (mBorderInsetsSpacing.set(spacingType, border)) {
            dirty();
        }
    }

    public void setBorderRadius(int spacingType, float radius) {
        if (mBorderRadiiSpacing.set(spacingType, radius)) {
            dirty();
        }
    }

    public void setBorderColor(int spacingType, int color) {
        if (mBorderColorsSpacing.set(spacingType, color)) {
            dirty();
        }
    }

    public void setBackgroundColor(int color) {
        if (!colorEquals(color, mBackgroundColor)) {
            mBackgroundColor = color;
            dirty();
        }
    }

    private void fillProperty() {
        if (mBorderInsets == null) {
            mBorderInsets = new BorderInsets();
        }
        mBorderInsets.set(
                mBorderInsetsSpacing.get(TOP, 0f),
                mBorderInsetsSpacing.get(RIGHT, 0f),
                mBorderInsetsSpacing.get(BOTTOM, 0f),
                mBorderInsetsSpacing.get(LEFT, 0f)
        );

        if (mBorderColors == null) {
            mBorderColors = new BorderColors();
        }
        mBorderColors.set(
                mBorderColorsSpacing.get(TOP, Color.BLACK),
                mBorderColorsSpacing.get(RIGHT, Color.BLACK),
                mBorderColorsSpacing.get(BOTTOM, Color.BLACK),
                mBorderColorsSpacing.get(LEFT, Color.BLACK)
        );

        if (mCornerRadii == null) {
            mCornerRadii = new CornerRadii();
        }
        mCornerRadii.set(
                mBorderRadiiSpacing.get(TOP_LEFT, 0f),
                mBorderRadiiSpacing.get(TOP_RIGHT, 0f),
                mBorderRadiiSpacing.get(BOTTOM_RIGHT, 0f),
                mBorderRadiiSpacing.get(BOTTOM_LEFT, 0f)
        );
    }

    private static final int ALPHA_SOLID = Color.alpha(Color.BLACK);
    private static final float BORDER_THRESHOLD = .001f;
    private static final int STRETCH_SIZE = 5;

    public void update() {
        if (!isDirty()) {
            return;
        }
        mIsDirty = false;
        fillProperty();
        int backgroundAlpha = Color.alpha(mBackgroundColor);
        // 有没有背景颜色
        boolean hasBackground = backgroundAlpha > 0;
        // 背景颜色是否为实心
        boolean backgroundIsSolid = backgroundAlpha == ALPHA_SOLID;

        // 是否有边框
        boolean hasBorderInsets = mBorderInsets.top > BORDER_THRESHOLD ||
                mBorderInsets.right > BORDER_THRESHOLD ||
                mBorderInsets.bottom > BORDER_THRESHOLD ||
                mBorderInsets.left > BORDER_THRESHOLD;

        //边框宽度是否相等
        boolean borderInsetsAreEqual = floatEquals(mBorderInsets.left, mBorderInsets.top) &&
                floatEquals(mBorderInsets.left, mBorderInsets.right) &&
                floatEquals(mBorderInsets.left, mBorderInsets.bottom);

        //边框颜色是否相同
        boolean borderColorsAreEqual = colorEquals(mBorderColors.left, mBorderColors.top) &&
                colorEquals(mBorderColors.left, mBorderColors.right) &&
                colorEquals(mBorderColors.left, mBorderColors.bottom);

        // 左边框是否为实色
        boolean borderLeftIsSolid = Color.alpha(mBorderColors.left) == ALPHA_SOLID;

        // 是否有圆角
        boolean hasCornerRadii = mCornerRadii.topLeft > BORDER_THRESHOLD ||
                mCornerRadii.topRight > BORDER_THRESHOLD ||
                mCornerRadii.bottomLeft > BORDER_THRESHOLD ||
                mCornerRadii.bottomRight > BORDER_THRESHOLD;

        // 如果没有背景色，也没有边框的话，不需要设置背景渲染
        // TODO 如果有圆角的话，内容需要 clip，不过安卓好像比较难实现
        if (!hasBackground && !hasBorderInsets) {
            mDrawable = null;
            freeBitmap();
            invalidateSelf();
            return;
        }

        // 如果有背景色，没有边框，没有圆角，只需要设置背景颜色就行
        if (hasBackground && !hasBorderInsets && !hasCornerRadii) {
            if (mDrawable instanceof ColorDrawable) {
                ((ColorDrawable) mDrawable).setColor(mBackgroundColor);
            } else {
                mDrawable = new ColorDrawable(mBackgroundColor);
            }
            freeBitmap();
            invalidateSelf();
            return;
        }

        // 如果没有圆角、边框宽度相等、边框颜色相同、
        // 边框颜色不透明或者没有背景色(因为 GradientDrawable 边框无法覆盖整个背景)，
        // 则可以使用 GradientDrawable 实现
        if (!hasCornerRadii &&
                borderInsetsAreEqual &&
                borderColorsAreEqual &&
                (borderLeftIsSolid || !hasBackground)) {

            GradientDrawable gradientDrawable;

            if (mDrawable instanceof GradientDrawable) {
                gradientDrawable = ((GradientDrawable) mDrawable);
            } else {
                gradientDrawable = new GradientDrawable();
                mDrawable = gradientDrawable;
            }

            gradientDrawable.setGradientType(GradientDrawable.RECTANGLE);
            gradientDrawable.setColor(mBackgroundColor);
            gradientDrawable.setStroke(mBorderInsets.ceilLeft(), mBorderColors.left);
            freeBitmap();
            invalidateSelf();
            return;
        }

        // 外圆角
        float[] outterRadii = new float[]{
                mCornerRadii.topLeft, mCornerRadii.topLeft,
                mCornerRadii.topRight, mCornerRadii.topRight,
                mCornerRadii.bottomRight, mCornerRadii.bottomRight,
                mCornerRadii.bottomLeft, mCornerRadii.bottomLeft
        };

        // 内圆角
        float[] innerRadii = new float[]{
                Math.max(0, mCornerRadii.topLeft - mBorderInsets.left),
                Math.max(0, mCornerRadii.topLeft - mBorderInsets.top),
                Math.max(0, mCornerRadii.topRight - mBorderInsets.right),
                Math.max(0, mCornerRadii.topRight - mBorderInsets.top),
                Math.max(0, mCornerRadii.bottomRight - mBorderInsets.right),
                Math.max(0, mCornerRadii.bottomRight - mBorderInsets.bottom),
                Math.max(0, mCornerRadii.bottomLeft - mBorderInsets.left),
                Math.max(0, mCornerRadii.bottomLeft - mBorderInsets.bottom)};

        // 如果边框颜色相同且不透明、背景颜色不透明，
        // 则可以使用两个 GradientDrawable 重叠实现
        if (borderColorsAreEqual && borderLeftIsSolid && backgroundIsSolid) {

            GradientDrawable backgroundDrawable;
            GradientDrawable borderDrawable;
            //InsetDrawable backgroundInsetDrawable;
            LayerDrawable layerDrawable;
            if (mDrawable instanceof LayerDrawable) {
                layerDrawable = ((LayerDrawable) mDrawable);
                borderDrawable = ((GradientDrawable) layerDrawable.getDrawable(0));
                backgroundDrawable = ((GradientDrawable) layerDrawable.getDrawable(1));
                //backgroundInsetDrawable = ((InsetDrawable) layerDrawable.getDrawable(1));
                //backgroundDrawable = ((GradientDrawable) backgroundInsetDrawable.getDrawable());
            } else {
                borderDrawable = new GradientDrawable();
                backgroundDrawable = new GradientDrawable();
                //backgroundInsetDrawable = new InsetDrawable(backgroundDrawable, 0);
                // 将背景层和边框层组合起来
                // 因为边框层是带圆角的实心矩形，所以背景层需要放在上面，才不会被遮盖
                layerDrawable = new LayerDrawable(new Drawable[]{borderDrawable, backgroundDrawable});
                //layerDrawable = new LayerDrawable(new Drawable[]{borderDrawable, backgroundInsetDrawable});
                mDrawable = layerDrawable;
            }
            //背景层
            backgroundDrawable.setGradientType(GradientDrawable.RECTANGLE);
            backgroundDrawable.setColor(mBackgroundColor);
            backgroundDrawable.setCornerRadii(innerRadii);

            //边框层
            borderDrawable.setGradientType(GradientDrawable.RECTANGLE);
            borderDrawable.setColor(mBorderColors.left);
            borderDrawable.setCornerRadii(outterRadii);

            //背景层需要留出边框
            layerDrawable.setLayerInset(1,
                    mBorderInsets.ceilLeft(),
                    mBorderInsets.ceilTop(),
                    mBorderInsets.ceilRight(),
                    mBorderInsets.ceilBottom());

            // FIXME 看看有没有更好的办法让 setLayerInset 生效
            // 因为 LayerDrawable 默认情况下只有 onBoundsChange 的时候才会应用 Inset 的改变
            layerDrawable.setBounds(0, 0, 0, 0);

            freeBitmap();
            invalidateSelf();
            return;
        }

        // 如果以上条件不满足，则需要生成 NinePatchDrawable
        // 得到内部八个角弧形的矩形区域
        if (mCornerInsets == null) {
            mCornerInsets = new CornerInsets();
        }
        fillCornerInsets(mCornerInsets, mCornerRadii, mBorderInsets);
        // 缩放图像的边框
        if (mEdgeInsets == null) {
            mEdgeInsets = new BorderInsets();
        }
        mEdgeInsets.set(mBorderInsets.top + Math.max(mCornerInsets.topLeft.height, mCornerInsets.topRight.height),
                mBorderInsets.right + Math.max(mCornerInsets.bottomRight.width, mCornerInsets.topRight.width),
                mBorderInsets.bottom + Math.max(mCornerInsets.bottomLeft.height, mCornerInsets.bottomRight.height),
                mBorderInsets.left + Math.max(mCornerInsets.topLeft.width, mCornerInsets.bottomLeft.width));

        // 缩放图像尺寸
        YASizeF size = new YASizeF(
                mEdgeInsets.left + STRETCH_SIZE + mEdgeInsets.right,
                mEdgeInsets.top + STRETCH_SIZE + mEdgeInsets.bottom);

        if (mPaint == null) {
            mPaint = new Paint();
        }
        if (mPath == null) {
            mPath = new Path();
        }
        mPaint.setAntiAlias(true);

        // 图像大小相同的话，直接清屏
        if (mBitmap != null && size.ceilEquals(mBitmapSize)) {
            mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            mCanvas.drawPaint(mPaint);
        } else {
            freeBitmap();
            mBitmap = Bitmap.createBitmap(
                    size.ceilWidth(),
                    size.ceilHeight(),
                    Bitmap.Config.ARGB_8888);
            mCanvas = new Canvas(mBitmap);
            mBitmapSize = size;
        }
        // 画边框
        // 线段的起始点和结束点
        PointF lineStart = new PointF();
        //PointF lineEnd = new PointF();
        // 椭圆所在的矩形区域
        YARectF ellipseBounds = new YARectF();
        // 与椭圆相交的两点
        PointF points[] = new PointF[]{new PointF(), new PointF()};

        // 内框的左上角
        PointF topLeft = new PointF(
                mBorderInsets.left,
                mBorderInsets.top);
        if (!mCornerInsets.topLeft.isEmpty()) {
            ellipseBounds.setOriginAndSize(
                    topLeft.x,
                    topLeft.y,
                    2 * mCornerInsets.topLeft.width,
                    2 * mCornerInsets.topLeft.height);
            lineStart.set(0, 0);

            ellipseGetIntersectionsWithLine(ellipseBounds, lineStart, topLeft, points);

            if (!Float.isNaN(points[1].x) && !Float.isNaN(points[1].y)) {
                topLeft.set(points[1]);
            }
        }

        // 内框的左下角
        PointF bottomLeft = new PointF(
                mBorderInsets.left,
                size.height - mBorderInsets.bottom);
        if (!mCornerInsets.bottomLeft.isEmpty()) {
            ellipseBounds.setOriginAndSize(
                    bottomLeft.x,
                    bottomLeft.y - 2 * mCornerInsets.bottomLeft.height,
                    2 * mCornerInsets.bottomLeft.width,
                    2 * mCornerInsets.bottomLeft.height);
            lineStart.set(0, size.height);

            ellipseGetIntersectionsWithLine(ellipseBounds, lineStart, bottomLeft, points);

            if (!Float.isNaN(points[1].x) && !Float.isNaN(points[1].y)) {
                bottomLeft.set(points[1]);
            }
        }

        // 内框的右上角
        PointF topRight = new PointF(
                size.width - mBorderInsets.right,
                mBorderInsets.top);
        if (!mCornerInsets.topRight.isEmpty()) {
            ellipseBounds.setOriginAndSize(
                    topRight.x - 2 * mCornerInsets.topRight.width,
                    topRight.y,
                    2 * mCornerInsets.topRight.width,
                    2 * mCornerInsets.topRight.height);
            lineStart.set(size.width, 0);

            ellipseGetIntersectionsWithLine(ellipseBounds, lineStart, topRight, points);

            if (!Float.isNaN(points[0].x) && !Float.isNaN(points[0].y)) {
                topRight.set(points[0]);
            }
        }

        // 内框的右下角
        PointF bottomRight = new PointF(
                size.width - mBorderInsets.right,
                size.height - mBorderInsets.bottom);
        if (!mCornerInsets.bottomRight.isEmpty()) {
            ellipseBounds.setOriginAndSize(
                    bottomRight.x - 2 * mCornerInsets.bottomRight.width,
                    bottomRight.y - 2 * mCornerInsets.bottomRight.height,
                    2 * mCornerInsets.bottomRight.width,
                    2 * mCornerInsets.bottomRight.height);
            lineStart.set(size.width, size.height);

            ellipseGetIntersectionsWithLine(ellipseBounds, lineStart, bottomRight, points);

            if (!Float.isNaN(points[0].x) && !Float.isNaN(points[0].y)) {
                bottomRight.set(points[0]);
            }
        }

        // RIGHT
        if (mBorderInsets.right > 0) {

            mPath.reset();
            mPath.moveTo(size.width, 0);
            mPath.lineTo(topRight.x, topRight.y);
            mPath.lineTo(bottomRight.x, bottomRight.y);
            mPath.lineTo(size.width, size.height);
            mPath.close();

            mPaint.setColor(mBorderColors.right);
            mCanvas.drawPath(mPath, mPaint);
        }

        // BOTTOM
        if (mBorderInsets.bottom > 0) {

            mPath.reset();
            mPath.moveTo(0, size.height);
            mPath.lineTo(bottomLeft.x, bottomLeft.y);
            mPath.lineTo(bottomRight.x, bottomRight.y);
            mPath.lineTo(size.width, size.height);
            mPath.close();

            mPaint.setColor(mBorderColors.bottom);
            mCanvas.drawPath(mPath, mPaint);
        }

        // LEFT
        if (mBorderInsets.left > 0) {

            mPath.reset();
            mPath.moveTo(0, 0);
            mPath.lineTo(topLeft.x, topLeft.y);
            mPath.lineTo(bottomLeft.x, bottomLeft.y);
            mPath.lineTo(0, size.height);
            mPath.close();

            mPaint.setColor(mBorderColors.left);
            mCanvas.drawPath(mPath, mPaint);
        }

        // TOP
        if (mBorderInsets.top > 0) {

            mPath.reset();
            mPath.moveTo(0, 0);
            mPath.lineTo(topLeft.x, topLeft.y);
            mPath.lineTo(topRight.x, topRight.y);
            mPath.lineTo(size.width, 0);
            mPath.close();

            mPaint.setColor(mBorderColors.top);
            mCanvas.drawPath(mPath, mPaint);
        }

        // 只有在内部有圆角的情况下需要抠出圆角
        if (mCornerInsets.hasCornerInsets()) {
            // 抠出内边 DST_IN
            // 内框范围
            RectF innerRect = new RectF(
                    mBorderInsets.left,
                    mBorderInsets.top,
                    size.width - mBorderInsets.right,
                    size.height - mBorderInsets.bottom);

            mPath.reset();
            mPath.addRoundRect(innerRect, innerRadii, Path.Direction.CW);
            mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            mPaint.setAlpha(0);
            mCanvas.drawPath(mPath, mPaint);
        }

        // 有背景时，在边框后面绘制背景 DST_OVER
        if (hasBackground) {
            mPaint.setColor(mBackgroundColor);
            mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OVER));
            mCanvas.drawPaint(mPaint);
        }

        // 如果有圆角，抠出外框
        if (hasCornerRadii) {
            // 外框范围
            RectF outterRect = new RectF(0, 0, size.width, size.height);

            Bitmap clipImg = Bitmap.createBitmap(
                    size.ceilWidth(),
                    size.ceilHeight(),
                    Bitmap.Config.ARGB_4444);

            Canvas clipCanvas = new Canvas(clipImg);

            mPath.reset();
            mPath.addRoundRect(outterRect, outterRadii, Path.Direction.CW);
            mPaint.setColor(Color.BLACK);
            clipCanvas.drawPath(mPath, mPaint);

            mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            //paint.setXfermode(null);
            mCanvas.drawBitmap(clipImg, 0, 0, mPaint);
            clipImg.recycle();
        }

        /*
        // 测试用的。绘制拉伸区域
        paint.setXfermode(null);
        paint.setColor(Color.YELLOW);
        mCanvas.drawRect(
                mEdgeInsets.ceilLeft(),
                mEdgeInsets.ceilTop(),
                (int) size.width - mEdgeInsets.ceilRight(),
                (int) size.height - mEdgeInsets.ceilBottom(),
                paint);
        //*/

        mPath.reset();
        mPaint.reset();

        //构造 NinePatchDrawable
        ByteBuffer buffer = getByteBuffer(
                mEdgeInsets.ceilTop(),
                (int) size.width - mEdgeInsets.ceilRight(),
                (int) size.height - mEdgeInsets.ceilBottom(),
                mEdgeInsets.ceilLeft());


        mDrawable = new NinePatchDrawable(
                mResources,
                mBitmap,
                buffer.array(),
                new Rect(),
                mName);

        invalidateSelf();
    }

    public String cssText() {
        return "border-top-width:" + mBorderInsetsSpacing.get(TOP, 0f) + ";\n" +
                "border-right-width:" + mBorderInsetsSpacing.get(RIGHT, 0f) + ";\n" +
                "border-bottom-width:" + mBorderInsetsSpacing.get(BOTTOM, 0f) + ";\n" +
                "border-left-width:" + mBorderInsetsSpacing.get(LEFT, 0f) + ";\n" +
                "border-top-color:" + mBorderColorsSpacing.get(TOP, Color.BLACK) + ";\n" +
                "border-right-color:" + mBorderColorsSpacing.get(RIGHT, Color.BLACK) + ";\n" +
                "border-bottom-color:" + mBorderColorsSpacing.get(BOTTOM, Color.BLACK) + ";\n" +
                "border-left-color:" + mBorderColorsSpacing.get(LEFT, Color.BLACK) + ";\n" +
                "border-top-left-radius:" + mBorderRadiiSpacing.get(TOP_LEFT, 0f) + ";\n" +
                "border-top-right-radius:" + mBorderRadiiSpacing.get(TOP_RIGHT, 0f) + ";\n" +
                "border-bottom-right-radius:" + mBorderRadiiSpacing.get(BOTTOM_RIGHT, 0f) + ";\n" +
                "border-bottom-left-radius:" + mBorderRadiiSpacing.get(BOTTOM_LEFT, 0f) + ";";
    }

    private static final int NO_COLOR = 0x00000001;

    private static ByteBuffer getByteBuffer(int top, int right, int bottom, int left) {
        // Docs check the NinePatchChunkFile
        ByteBuffer buffer = ByteBuffer.allocate(84).order(ByteOrder.nativeOrder());
        // was translated
        buffer.put((byte) 0x01);
        // divx size
        buffer.put((byte) 0x02);
        // divy size
        buffer.put((byte) 0x02);
        // color size
        buffer.put((byte) 0x09);

        // skip
        buffer.putInt(0);
        buffer.putInt(0);

        // padding
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(0);

        // skip 4 bytes
        buffer.putInt(0);

        buffer.putInt(left);
        buffer.putInt(right);
        buffer.putInt(top);
        buffer.putInt(bottom);
        buffer.putInt(NO_COLOR);
        buffer.putInt(NO_COLOR);
        buffer.putInt(NO_COLOR);
        buffer.putInt(NO_COLOR);
        buffer.putInt(NO_COLOR);
        buffer.putInt(NO_COLOR);
        buffer.putInt(NO_COLOR);
        buffer.putInt(NO_COLOR);
        buffer.putInt(NO_COLOR);

        return buffer;
    }

    private static void fillCornerInsets(CornerInsets cornerInsets, CornerRadii cornerRadii, BorderInsets edgeInsets) {
        cornerInsets.set(
                Math.max(0, cornerRadii.topLeft - edgeInsets.left),
                Math.max(0, cornerRadii.topLeft - edgeInsets.top),
                Math.max(0, cornerRadii.topRight - edgeInsets.right),
                Math.max(0, cornerRadii.topRight - edgeInsets.top),
                Math.max(0, cornerRadii.bottomLeft - edgeInsets.left),
                Math.max(0, cornerRadii.bottomLeft - edgeInsets.bottom),
                Math.max(0, cornerRadii.bottomRight - edgeInsets.right),
                Math.max(0, cornerRadii.bottomRight - edgeInsets.bottom)
        );
    }

    private static void ellipseGetIntersectionsWithLine(RectF ellipseBounds,
                                                        PointF lineStart,
                                                        PointF lineEnd,
                                                        PointF[] intersections) {
        PointF ellipseCenter = new PointF(
                ellipseBounds.centerX(),
                ellipseBounds.centerY());

        lineStart.x -= ellipseCenter.x;
        lineStart.y -= ellipseCenter.y;
        lineEnd.x -= ellipseCenter.x;
        lineEnd.y -= ellipseCenter.y;

        float m = (lineEnd.y - lineStart.y) / (lineEnd.x - lineStart.x);
        float a = ellipseBounds.width() / 2;
        float b = ellipseBounds.height() / 2;
        float c = lineStart.y - m * lineStart.x;
        float A = (b * b + a * a * m * m);
        float B = 2 * a * a * c * m;
        float D = (float) Math.sqrt((a * a * (b * b - c * c)) / A + Math.pow(B / (2 * A), 2));

        float x_ = -B / (2 * A);
        float x1 = x_ + D;
        float x2 = x_ - D;
        float y1 = m * x1 + c;
        float y2 = m * x2 + c;

        intersections[0].set(x1 + ellipseCenter.x, y1 + ellipseCenter.y);
        intersections[1].set(x2 + ellipseCenter.x, y2 + ellipseCenter.y);
    }

    private void freeBitmap() {
        if (mBitmap != null) {
            mBitmap.recycle();
        }
        mBitmapSize = null;
    }

    private boolean isDirty() {
        return mIsDirty;
    }

    private void dirty() {
        mIsDirty = true;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        mBounds.set(bounds);
        if (mDrawable != null) {
            mDrawable.setBounds(bounds);
        }
    }

    @Override
    public void invalidateSelf() {
        if (mDrawable != null) {
            mDrawable.setBounds(mBounds);
        }
        super.invalidateSelf();
    }

    //private int count = 0;
    @Override
    public void draw(Canvas canvas) {
        /*
        Paint paint = new Paint();
        paint.setColor(Color.BLUE);
        canvas.drawPaint(paint);
        paint.setColor(Color.BLACK);
        canvas.drawText("" + ++count, 300, 300, paint);
        System.out.println(count);
        */
        if (mDrawable != null) {
            mDrawable.draw(canvas);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        if (mDrawable != null) {
            mDrawable.setAlpha(alpha);
        }
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        if (mDrawable != null) {
            mDrawable.setColorFilter(cf);
        }
    }

    @Override
    public int getOpacity() {
        if (mDrawable != null) {
            return mDrawable.getOpacity();
        }
        return 0;
    }

    private static final float EPSILON = .00001f;

    private static boolean floatEquals(float f1, float f2) {
        if (Float.isNaN(f1) || Float.isNaN(f2)) {
            return Float.isNaN(f1) && Float.isNaN(f2);
        }
        return Math.abs(f2 - f1) < EPSILON;
    }

    private static boolean colorEquals(int ca, int cb) {
        if (Color.alpha(ca) == 0 && Color.alpha(cb) == 0) {
            return true;
        }
        return ca == cb;
    }

    private static class CornerRadii {
        public float topLeft = 0;
        public float topRight = 0;
        public float bottomLeft = 0;
        public float bottomRight = 0;

        public void set(float topLeft, float topRight, float bottomRight, float bottomLeft) {
            this.topLeft = topLeft;
            this.topRight = topRight;
            this.bottomLeft = bottomLeft;
            this.bottomRight = bottomRight;
        }
    }

    private static class BorderInsets extends RectF {
        public float top = 0;
        public float right = 0;
        public float bottom = 0;
        public float left = 0;

        public void set(float top, float right, float bottom, float left) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public int ceilTop() {
            return (int) Math.ceil(top);
        }

        public int ceilRight() {
            return (int) Math.ceil(right);
        }

        public int ceilBottom() {
            return (int) Math.ceil(bottom);
        }

        public int ceilLeft() {
            return (int) Math.ceil(left);
        }

    }

    private static class BorderColors {
        public int top;
        public int left;
        public int bottom;
        public int right;

        public void set(int top, int right, int bottom, int left) {
            this.top = top;
            this.left = left;
            this.right = right;
            this.bottom = bottom;
        }
    }

    private static class YASizeF {
        public float width = 0;
        public float height = 0;

        public YASizeF(float width, float height) {
            this.width = width;
            this.height = height;
        }

        public int ceilWidth() {
            return (int) Math.ceil(width);
        }

        public int ceilHeight() {
            return (int) Math.ceil(height);
        }

        public boolean isEmpty() {
            return width < BORDER_THRESHOLD || height < BORDER_THRESHOLD;
        }

        public boolean ceilEquals(YASizeF size) {
            if (size != null) {
                return size.ceilWidth() == ceilWidth() &&
                        size.ceilHeight() == ceilHeight();
            }
            return false;
        }
    }

    private static class YARectF extends RectF {

        public YARectF() {
            super();
        }

        public void setOriginAndSize(float x, float y, float width, float height) {
            set(x, y, x + width, y + height);
        }
    }

    private static class CornerInsets {
        public YASizeF topLeft = new YASizeF(0, 0);
        public YASizeF topRight = new YASizeF(0, 0);
        public YASizeF bottomLeft = new YASizeF(0, 0);
        public YASizeF bottomRight = new YASizeF(0, 0);

        public void set(float topLeftWidth, float topLeftHeight, float topRightWidth, float topRightHeight,
                        float bottomLeftWidth, float bottomLeftHeight, float bottomRightWidth, float bottomRightHeight) {

            this.topLeft = new YASizeF(topLeftWidth, topLeftHeight);
            this.topRight = new YASizeF(topRightWidth, topRightHeight);
            this.bottomLeft = new YASizeF(bottomLeftWidth, bottomLeftHeight);
            this.bottomRight = new YASizeF(bottomRightWidth, bottomRightHeight);
        }

        public boolean hasCornerInsets() {
            return !topLeft.isEmpty() ||
                    !topRight.isEmpty() ||
                    !bottomLeft.isEmpty() ||
                    !bottomRight.isEmpty();
        }
    }

    private static class ColorSpacing extends Spacing<Integer> {
        @Override
        protected boolean valueEquals(Integer a, Integer b) {
            return colorEquals(a, b);
        }
    }

    private static class FloatSpacing extends Spacing<Float> {
        @Override
        protected boolean valueEquals(Float f1, Float f2) {
            return floatEquals(f1, f2);
        }
    }


    public static final int LEFT = 0;
    public static final int TOP = 1;
    public static final int RIGHT = 2;
    public static final int BOTTOM = 3;
    public static final int VERTICAL = 4;
    public static final int HORIZONTAL = 5;

    public static final int TOP_LEFT = 0;
    public static final int TOP_RIGHT = 1;
    public static final int BOTTOM_RIGHT = 2;
    public static final int BOTTOM_LEFT = 3;
    public static final int TOP_LEFT_BOTTOM_RIGHT = 4;
    public static final int TOP_RIGHT_BOTTOM_LEFT = 5;

    //public static final int START = 6;
    //public static final int END = 7;
    // NOTICE ALL 的值一定要是最大的，因为 FULL_SPACING_SIZE 会用到这个值
    public static final int ALL = 6;

    private static abstract class Spacing<T> {

        private static final int FULL_SPACING_SIZE = ALL + 1;
        private static final int SPACING_RESULT_SIZE = 4;

        private final Object[] mSpacing = newFullSpacingArray();
        private final boolean[] mSpacingDefined = newFullSpacingDefinedArray();

        @Nullable
        private Object[] mDefaultSpacing = null;

        abstract protected boolean valueEquals(T a, T b);

        private boolean isDefined(int spacingType) {
            return mSpacingDefined[spacingType];
        }

        public boolean set(int spacingType, T value) {
            if (!isDefined(spacingType) || !valueEquals((T) mSpacing[spacingType], value)) {
                mSpacing[spacingType] = value;
                mSpacingDefined[spacingType] = true;
                return true;
            }
            return false;
        }

        public boolean unset(int spacingType) {
            if (isDefined(spacingType)) {
                mSpacing[spacingType] = null;
                mSpacingDefined[spacingType] = false;
                return true;
            }
            return false;
        }

        public boolean setDefault(int spacingType, T value) {
            if (mDefaultSpacing == null) {
                mDefaultSpacing = newSpacingResultArray();
            }
            if (!valueEquals((T) mDefaultSpacing[spacingType], value)) {
                mDefaultSpacing[spacingType] = value;
                return true;
            }
            return false;
        }

        @Nullable
        public T get(int spacingType) {
            return get(spacingType, null);
        }

        public T get(int spacingType, T defaultValue) {

            int secondType =
                    spacingType == TOP || spacingType == BOTTOM ? VERTICAL :
                            spacingType == LEFT || spacingType == RIGHT ? HORIZONTAL :
                                    spacingType == TOP_LEFT || spacingType == BOTTOM_RIGHT ? TOP_LEFT_BOTTOM_RIGHT :
                                            spacingType == TOP_RIGHT || spacingType == BOTTOM_LEFT ? TOP_RIGHT_BOTTOM_LEFT :
                                                    ALL;

            return (T) (isDefined(spacingType)
                    ? mSpacing[spacingType]
                    : isDefined(secondType)
                    ? mSpacing[secondType]
                    : isDefined(ALL)
                    ? mSpacing[ALL]
                    : mDefaultSpacing != null
                    ? mDefaultSpacing[spacingType]
                    : defaultValue);
        }

        public T getRaw(int spacingType) {
            return (T) mSpacing[spacingType];
        }

        private static Object[] newFullSpacingArray() {
            return new Object[FULL_SPACING_SIZE];
        }

        private static boolean[] newFullSpacingDefinedArray() {
            boolean[] arr = new boolean[FULL_SPACING_SIZE];
            for (int i = 0; i < FULL_SPACING_SIZE; i++) {
                arr[i] = false;
            }
            return arr;
        }

        private static Object[] newSpacingResultArray() {
            return newSpacingResultArray(null);
        }

        private static Object[] newSpacingResultArray(Object defaultValue) {
            Object[] arr = new Object[SPACING_RESULT_SIZE];
            for (int i = 0; i < SPACING_RESULT_SIZE; i++) {
                arr[i] = defaultValue;
            }
            return arr;
        }
    }

}
