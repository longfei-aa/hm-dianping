package com.hmdp.utils;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 只负责：把文本分片发送到 Python /embed 接口，拿回 float[] 向量。
 * 归一化/维度等处理全部交给 Python 服务端（bge-m3）完成。
 */
@Component
public class EmbeddingClient {

    @Value("${ai.embed.endpoint:http://127.0.0.1:8000/embed}")
    private String endpoint;

    @Value("${ai.embed.expected-dim:1024}")
    private int expectedDim;

    @Value("${ai.embed.model:bge-m3}")
    private String model;

    @Value("${ai.embed.normalize:true}")
    private boolean normalize;

    @Value("${ai.embed.batch-size:32}")
    private int batchSize;

    /**
     * 批量向量化。若 texts 为空返回空列表。
     * 所有处理（清洗、归一化等）都在 Python 端完成，这里仅负责请求和解析。
     */
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) return new ArrayList<>();

        List<float[]> all = new ArrayList<>(texts.size());
        final int n = texts.size();
        final int bs = Math.max(1, batchSize);

        for (int i = 0; i < n; i += bs) {
            int j = Math.min(i + bs, n);
            List<String> sub = texts.subList(i, j);
            all.addAll(callOnce(sub));
        }
        return all;
    }

    /** 真实 HTTP 调用（最小实现） */
    private List<float[]> callOnce(List<String> texts) {
        JSONObject req = new JSONObject();
        req.set("texts", texts);
        req.set("model", model);
        req.set("normalize", normalize);

        HttpResponse resp = HttpRequest.post(endpoint)
                .header("Content-Type", "application/json; charset=UTF-8")
                .body(req.toString())
                .timeout(20000) // 20s
                .execute();

        if (resp == null || resp.getStatus() / 100 != 2) {
            int code = resp == null ? -1 : resp.getStatus();
            String body = resp == null ? "" : safeStr(resp.bodyBytes());
            throw new RuntimeException("Embedding HTTP error, code=" + code + ", body=" + body);
        }

        String body = safeStr(resp.bodyBytes());
        JSONObject obj = JSONUtil.parseObj(body);

        // （可选）维度校验
        int dim = obj.getInt("dim", -1);
        if (expectedDim > 0 && dim > 0 && dim != expectedDim) {
            throw new RuntimeException("Dim mismatch: got " + dim + ", expect " + expectedDim);
        }

        JSONArray arr = obj.getJSONArray("vectors");
        List<float[]> out = new ArrayList<>(arr.size());
        for (int i = 0; i < arr.size(); i++) {
            JSONArray vi = arr.getJSONArray(i);
            float[] v = new float[vi.size()];
            for (int k = 0; k < vi.size(); k++) {
                // Hutool 解析为 BigDecimal，再转 float
                v[k] = vi.getBigDecimal(k).floatValue();
            }
            out.add(v);
        }
        return out;
    }

    private String safeStr(byte[] bytes) {
        return bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
    }
}