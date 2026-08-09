package com.sep490.slms2026.vision;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.sep490.slms2026.dto.response.VisionLabelItem;
import com.sep490.slms2026.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Model phân loại thiết bị chạy trong JVM (MobileNetV3 → ONNX).
 * Chưa có file model → {@link #isAvailable()} = false; plugin model sau là xong.
 * <p>
 * Nhãn phải khớp FE (`equipmentPhoto.ts`): tiếng Anh, thường, score 0..1.
 */
@Slf4j
@Component
public class LocalVisionProvider implements VisionProvider {

    public static final String NAME = "local";

    private static final int INPUT_SIZE = 224;
    /** ImageNet mean/std — chuẩn transfer learning MobileNetV3. */
    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD = {0.229f, 0.224f, 0.225f};

    @Value("${vision.local.model-path:classpath:models/equipment-mobilenetv3.onnx}")
    private String modelPath;

    @Value("${vision.local.labels-path:classpath:models/labels.txt}")
    private String labelsPath;

    @Value("${vision.local.min-score:0.35}")
    private double minScore;

    @Value("${vision.local.max-results:10}")
    private int maxResults;

    @Value("${vision.local.intra-op-threads:1}")
    private int intraOpThreads;

    private final ResourceLoader resourceLoader;

    private OrtEnvironment env;
    private OrtSession session;
    private List<String> labels = List.of();
    private volatile boolean ready;

    public LocalVisionProvider(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    void init() {
        try {
            labels = loadLabels(labelsPath);
            if (labels.isEmpty()) {
                log.info("Vision local: không có labels tại {} — provider tắt", labelsPath);
                return;
            }

            Resource model = resourceLoader.getResource(modelPath);
            if (!model.exists()) {
                log.info("Vision local: chưa có model tại {} — provider tắt (isAvailable=false). "
                        + "Đặt file ONNX + labels.txt để bật fallback offline.", modelPath);
                return;
            }

            byte[] modelBytes;
            try (InputStream in = model.getInputStream()) {
                modelBytes = in.readAllBytes();
            }

            env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            // 2 core VPS: chừa core còn lại cho Tomcat + PostgreSQL
            opts.setIntraOpNumThreads(Math.max(1, intraOpThreads));
            opts.setInterOpNumThreads(1);
            session = env.createSession(modelBytes, opts);
            warmUp();
            ready = true;
            log.info("Vision local: đã nạp model {} ({} lớp, min-score={})",
                    modelPath, labels.size(), minScore);
        } catch (Exception e) {
            log.warn("Vision local: không nạp được model — provider tắt: {}", e.toString());
            closeQuietly();
            ready = false;
        }
    }

    @PreDestroy
    void destroy() {
        closeQuietly();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isAvailable() {
        return ready && session != null && !labels.isEmpty();
    }

    @Override
    public List<VisionLabelItem> detect(ImageSource src) {
        if (!isAvailable()) {
            throw new BusinessException("Model nhận diện nội bộ chưa sẵn sàng");
        }
        try {
            byte[] imageBytes = src.bytes();
            float[][][][] input = preprocess(imageBytes);
            float[] logits = runInference(input);
            float[] probs = softmax(logits);
            return toLabels(probs);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Vision local inference lỗi", e);
            throw new BusinessException("Nhận diện nội bộ lỗi. Vui lòng thử lại.");
        }
    }

    private float[] runInference(float[][][][] nchw) throws OrtException {
        String inputName = session.getInputNames().iterator().next();
        long[] shape = {1, 3, INPUT_SIZE, INPUT_SIZE};
        FloatBuffer buf = FloatBuffer.allocate(1 * 3 * INPUT_SIZE * INPUT_SIZE);
        for (int c = 0; c < 3; c++) {
            for (int y = 0; y < INPUT_SIZE; y++) {
                for (int x = 0; x < INPUT_SIZE; x++) {
                    buf.put(nchw[0][c][y][x]);
                }
            }
        }
        buf.rewind();

        try (OnnxTensor tensor = OnnxTensor.createTensor(env, buf, shape);
             OrtSession.Result result = session.run(Map.of(inputName, tensor))) {
            OnnxTensor out = (OnnxTensor) result.get(0);
            float[][] raw = (float[][]) out.getValue();
            return raw[0];
        }
    }

    private List<VisionLabelItem> toLabels(float[] probs) {
        int n = Math.min(probs.length, labels.size());
        List<VisionLabelItem> items = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double score = probs[i];
            if (score < minScore) {
                continue;
            }
            items.add(VisionLabelItem.builder()
                    .name(labels.get(i))
                    .score(clamp01(score))
                    .build());
        }
        items.sort(Comparator.comparingDouble(VisionLabelItem::getScore).reversed());
        if (items.size() > maxResults) {
            return new ArrayList<>(items.subList(0, maxResults));
        }
        return items;
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    private static float[] softmax(float[] logits) {
        float max = Float.NEGATIVE_INFINITY;
        for (float v : logits) {
            if (v > max) max = v;
        }
        double sum = 0;
        float[] exp = new float[logits.length];
        for (int i = 0; i < logits.length; i++) {
            exp[i] = (float) Math.exp(logits[i] - max);
            sum += exp[i];
        }
        if (sum <= 0) {
            return exp;
        }
        for (int i = 0; i < exp.length; i++) {
            exp[i] = (float) (exp[i] / sum);
        }
        return exp;
    }

    /**
     * Resize 224×224, RGB NCHW, chuẩn hóa ImageNet.
     */
    static float[][][][] preprocess(byte[] imageBytes) throws Exception {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (src == null) {
            throw new BusinessException("Không đọc được định dạng ảnh (cần JPEG/PNG)");
        }
        BufferedImage rgb = new BufferedImage(INPUT_SIZE, INPUT_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            Image scaled = src.getScaledInstance(INPUT_SIZE, INPUT_SIZE, Image.SCALE_SMOOTH);
            g.drawImage(scaled, 0, 0, null);
        } finally {
            g.dispose();
        }

        float[][][][] out = new float[1][3][INPUT_SIZE][INPUT_SIZE];
        for (int y = 0; y < INPUT_SIZE; y++) {
            for (int x = 0; x < INPUT_SIZE; x++) {
                int pixel = rgb.getRGB(x, y);
                float r = ((pixel >> 16) & 0xff) / 255f;
                float green = ((pixel >> 8) & 0xff) / 255f;
                float b = (pixel & 0xff) / 255f;
                out[0][0][y][x] = (r - MEAN[0]) / STD[0];
                out[0][1][y][x] = (green - MEAN[1]) / STD[1];
                out[0][2][y][x] = (b - MEAN[2]) / STD[2];
            }
        }
        return out;
    }

    private List<String> loadLabels(String path) {
        if (!StringUtils.hasText(path)) {
            return List.of();
        }
        try {
            Resource resource = resourceLoader.getResource(path);
            if (!resource.exists()) {
                return List.of();
            }
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String t = line.trim();
                    if (t.isEmpty() || t.startsWith("#")) {
                        continue;
                    }
                    lines.add(t.toLowerCase());
                }
            }
            return Collections.unmodifiableList(lines);
        } catch (Exception e) {
            log.warn("Vision local: không đọc labels {}: {}", path, e.toString());
            return List.of();
        }
    }

    /** Lượt đầu luôn chậm — chạy dummy lúc boot để demo/fallback không “treo”. */
    private void warmUp() {
        try {
            BufferedImage blank = new BufferedImage(INPUT_SIZE, INPUT_SIZE, BufferedImage.TYPE_INT_RGB);
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            ImageIO.write(blank, "png", baos);
            float[][][][] input = preprocess(baos.toByteArray());
            runInference(input);
            log.debug("Vision local warm-up OK");
        } catch (Exception e) {
            log.warn("Vision local warm-up lỗi (vẫn dùng được): {}", e.toString());
        }
    }

    private void closeQuietly() {
        ready = false;
        if (session != null) {
            try {
                session.close();
            } catch (Exception ignored) {
                // ignore
            }
            session = null;
        }
        env = null;
    }
}
