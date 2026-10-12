package com.steamarbitrage.test;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 把一张方图转成多尺寸的 Windows {@code .ico}，供 jpackage 作为应用图标。
 *
 * <p>JDK 的 ImageIO 只读不写 ICO，所以这里手写容器格式：
 * 每个条目用 32bpp 的 DIB（BITMAPINFOHEADER + BGRA 位图 + AND 掩码）编码。
 * 之所以不用「PNG 嵌进 ICO」这种更省事的写法，是因为 jpackage 内部的
 * ICO 解析器只认 DIB 条目。
 *
 * <p>四角会做圆角处理，避免方形白底在任务栏里显得突兀。
 *
 * <pre>
 * java -cp out\classes com.steamarbitrage.test.IconBuilder assets\icon-source.png assets\app.ico
 * </pre>
 */
public final class IconBuilder {

    /** Windows 图标常用尺寸。256 在 ICO 目录项里用 0 表示。 */
    private static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};

    /** 圆角半径占边长的比例。 */
    private static final double CORNER_RATIO = 0.18;

    private IconBuilder() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("用法: IconBuilder <源图片> <输出.ico>");
            System.exit(2);
        }

        BufferedImage source = ImageIO.read(new File(args[0]));
        if (source == null) {
            System.err.println("无法识别图片格式: " + args[0]);
            System.exit(1);
        }

        int maxSize = SIZES[SIZES.length - 1];
        BufferedImage square = toSquare(source, maxSize);

        List<byte[]> dibs = new ArrayList<>();
        for (int size : SIZES) {
            BufferedImage scaled = rounded(resize(square, size), size);
            dibs.add(encodeDib(scaled, size));
        }

        writeIco(new File(args[1]), SIZES, dibs);

        long total = 0;
        for (byte[] dib : dibs) {
            total += dib.length;
        }
        System.out.printf("已生成 %s（%d 个尺寸，共 %.1f KB）%n",
                args[1], SIZES.length, total / 1024.0);

        // 可选：导出保留 alpha 的预览，用于核对圆角与缩放
        // （不能借道 .NET 的 Icon.ToBitmap()，它会丢掉 alpha 通道）
        if (args.length >= 3) {
            BufferedImage preview = rounded(resize(square, maxSize), maxSize);
            ImageIO.write(preview, "png", new File(args[2]));
            System.out.printf("预览已导出 %s（角落 alpha=%d，中心 alpha=%d；角落为 0 说明圆角生效）%n",
                    args[2],
                    (preview.getRGB(1, 1) >>> 24) & 0xFF,
                    (preview.getRGB(maxSize / 2, maxSize / 2) >>> 24) & 0xFF);
        }
    }

    /** 居中裁剪成正方形，保证图标不被拉伸变形。 */
    private static BufferedImage toSquare(BufferedImage source, int size) {
        int side = Math.min(source.getWidth(), source.getHeight());
        int x = (source.getWidth() - side) / 2;
        int y = (source.getHeight() - side) / 2;

        BufferedImage square = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = square.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, size, size, x, y, x + side, y + side, null);
        } finally {
            g.dispose();
        }
        return square;
    }

    private static BufferedImage resize(BufferedImage source, int size) {
        BufferedImage target = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(source, 0, 0, size, size, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    /**
     * 用圆角遮罩把四角变透明。
     *
     * <p>注意不能用 {@code g.fillRoundRect(...)} 配 {@code DST_IN} 直接抠：
     * Java2D 只会对形状覆盖到的像素做合成，圆角外的像素根本不参与运算，结果依旧不透明。
     * 正确做法是把遮罩先渲染成一张图，再整张绘制上去 —— 这样每个像素都被合成到。
     * 遮罩本身开了抗锯齿，因此圆角边缘是平滑的。
     */
    private static BufferedImage rounded(BufferedImage source, int size) {
        int diameter = Math.max(2, (int) Math.round(size * CORNER_RATIO * 2));

        BufferedImage mask = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D maskGraphics = mask.createGraphics();
        try {
            maskGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            maskGraphics.setColor(Color.WHITE);
            maskGraphics.fillRoundRect(0, 0, size, size, diameter, diameter);
        } finally {
            maskGraphics.dispose();
        }

        BufferedImage target = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = target.createGraphics();
        try {
            g.drawImage(source, 0, 0, null);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.DST_IN));
            g.drawImage(mask, 0, 0, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    /** 编码一个 ICO 条目：BITMAPINFOHEADER + BGRA 位图 + AND 掩码。 */
    private static byte[] encodeDib(BufferedImage image, int size) {
        int xorSize = size * size * 4;
        int andRowBytes = ((size + 31) / 32) * 4;
        int andSize = andRowBytes * size;

        ByteArrayOutputStream buffer = new ByteArrayOutputStream(xorSize + andSize + 40);
        DataOutputStream out = new DataOutputStream(buffer);

        try {
            writeLe32(out, 40);                 // biSize
            writeLe32(out, size);               // biWidth
            writeLe32(out, size * 2);           // biHeight = XOR + AND
            writeLe16(out, 1);                  // biPlanes
            writeLe16(out, 32);                 // biBitCount
            writeLe32(out, 0);                  // biCompression = BI_RGB
            writeLe32(out, xorSize + andSize);  // biSizeImage
            writeLe32(out, 0);                  // biXPelsPerMeter
            writeLe32(out, 0);                  // biYPelsPerMeter
            writeLe32(out, 0);                  // biClrUsed
            writeLe32(out, 0);                  // biClrImportant

            // XOR 位图：自下而上、BGRA
            for (int y = size - 1; y >= 0; y--) {
                for (int x = 0; x < size; x++) {
                    int argb = image.getRGB(x, y);
                    out.writeByte(argb & 0xFF);          // B
                    out.writeByte((argb >> 8) & 0xFF);   // G
                    out.writeByte((argb >> 16) & 0xFF);  // R
                    out.writeByte((argb >>> 24) & 0xFF); // A
                }
            }

            // AND 掩码：1 表示透明，同样自下而上
            byte[] row = new byte[andRowBytes];
            for (int y = size - 1; y >= 0; y--) {
                Arrays.fill(row, (byte) 0);
                for (int x = 0; x < size; x++) {
                    if (((image.getRGB(x, y) >>> 24) & 0xFF) == 0) {
                        row[x / 8] |= (byte) (0x80 >> (x % 8));
                    }
                }
                out.write(row);
            }
            out.flush();
        } catch (Exception e) {
            throw new IllegalStateException("编码 ICO 条目失败", e);
        }
        return buffer.toByteArray();
    }

    private static void writeIco(File file, int[] sizes, List<byte[]> dibs) throws Exception {
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(file)))) {

            writeLe16(out, 0);            // reserved
            writeLe16(out, 1);            // type = 1（图标）
            writeLe16(out, sizes.length); // 条目数

            int offset = 6 + 16 * sizes.length;
            for (int i = 0; i < sizes.length; i++) {
                int size = sizes[i];
                byte[] dib = dibs.get(i);

                out.writeByte(size >= 256 ? 0 : size);  // 宽度（0 表示 256）
                out.writeByte(size >= 256 ? 0 : size);  // 高度
                out.writeByte(0);                       // 调色板数
                out.writeByte(0);                       // reserved
                writeLe16(out, 1);                      // planes
                writeLe16(out, 32);                     // 位深
                writeLe32(out, dib.length);             // 数据长度
                writeLe32(out, offset);                 // 数据偏移

                offset += dib.length;
            }

            for (byte[] dib : dibs) {
                out.write(dib);
            }
        }
    }

    private static void writeLe16(DataOutputStream out, int value) throws Exception {
        out.writeByte(value & 0xFF);
        out.writeByte((value >> 8) & 0xFF);
    }

    private static void writeLe32(DataOutputStream out, int value) throws Exception {
        out.writeByte(value & 0xFF);
        out.writeByte((value >> 8) & 0xFF);
        out.writeByte((value >> 16) & 0xFF);
        out.writeByte((value >> 24) & 0xFF);
    }
}
