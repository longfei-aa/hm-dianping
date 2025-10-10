package com.hmdp.service.impl;

import com.hmdp.dto.*;
import com.hmdp.service.ISearchService;
import com.hmdp.utils.EmbeddingClient;
import com.hmdp.utils.VectorUtils;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchServiceImpl implements ISearchService {

    @Resource
    private StringRedisTemplate redis;
    @Resource
    private EmbeddingClient embeddingClient;

    /** 按你的索引名修改 */
    private static final String IDX_DOC   = "idx:blog:doc";
    private static final String IDX_CHUNK = "idx:blog:chunk";

    @Override
    public Result semanticSearch(String query, int size) {
        // 1) query 向量化
        float[] q = embeddingClient.embed(query);
        byte[] qBlob = VectorUtils.toFloat32LE(q);

        // 2) doc 粗召回
        List<DocHit> docs = knnDocs(qBlob, 60);
        if (docs.isEmpty()) return Result.ok(Collections.emptyList());

        // 3) chunk 精排（仅在候选 doc 范围）
        String tag = toTagFilter(docs.stream().map(DocHit::getBlogId).collect(Collectors.toList()));
        List<ChunkHit> chunks = knnChunksWithin(tag, qBlob, 200);

        // 4) 规则打分 + 聚合
        List<ScoredChunk> ranked = rank(chunks);
        return Result.ok(aggregateAndFormat(ranked, size));
    }

    // ========================== RediSearch 查询实现 ==========================

    /** doc 级 KNN 粗召回（返回 blogId/title/summary/sim） */
    private List<DocHit> knnDocs(byte[] vec, int k) {
        final String q = "*=>[KNN " + k + " @embedding $vec AS sim]";
        return redis.execute((RedisCallback<List<DocHit>>) conn -> {
            Object resp = conn.execute(
                    "FT.SEARCH",
                    // 索引名、查询、所有参数都要是 byte[]
                    b(IDX_DOC),
                    b(q),
                    b("PARAMS"), b("2"), b("vec"), vec,
                    b("SORTBY"), b("sim"),
                    b("RETURN"), b("4"), b("blogId"), b("title"), b("summary"), b("sim"),
                    b("DIALECT"), b("2"),
                    b("LIMIT"), b("0"), b(String.valueOf(k))
            );
            return parseDocHits(resp == null ? Collections.emptyList() : resp);
        });
    }

    /** 在候选 doc 范围内做 chunk 级 KNN 精排（返回 blogId/title/text/sim/createdAt） */
    private List<ChunkHit> knnChunksWithin(String blogIdTagFilter, byte[] vec, int k) {
        final String q = "(@blogId:" + blogIdTagFilter + ")=>[KNN " + k + " @embedding $vec AS sim]";
        return redis.execute((RedisCallback<List<ChunkHit>>) conn -> {
            Object resp = conn.execute(
                    "FT.SEARCH",
                    b(IDX_CHUNK),
                    b(q),
                    b("PARAMS"), b("2"), b("vec"), vec,
                    b("SORTBY"), b("sim"),
                    b("RETURN"), b("5"), b("blogId"), b("title"), b("text"), b("sim"), b("createdAt"),
                    b("DIALECT"), b("2"),
                    b("LIMIT"), b("0"), b(String.valueOf(k))
            );
            return parseChunkHits(resp == null ? Collections.emptyList() : resp);
        });
    }

    // ========================== 解析 RESP 工具 ==========================

    @SuppressWarnings("unchecked")
    private List<DocHit> parseDocHits(Object resp) {
        List<DocHit> out = new ArrayList<>();
        if (!(resp instanceof List)) return out;

        List<Object> arr = (List<Object>) resp;
        // arr[0] = total, 之后是成对（docKey, fields[]）
        for (int i = 1; i + 1 < arr.size(); i += 2) {
            Object fields = arr.get(i + 1);
            Map<String, String> map = flatFieldsToMap(fields);
            if (map.isEmpty()) continue;

            DocHit d = new DocHit();
            d.setBlogId(parseLong(map.get("blogId"), -1L));
            d.setTitle(map.get("title"));
            d.setSummary(map.get("summary"));
            d.setSim(parseDouble(map.get("sim"), 1.0));
            if (d.getBlogId() > 0) out.add(d);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<ChunkHit> parseChunkHits(Object resp) {
        List<ChunkHit> out = new ArrayList<>();
        if (!(resp instanceof List)) return out;

        List<Object> arr = (List<Object>) resp;
        for (int i = 1; i + 1 < arr.size(); i += 2) {
            Object fields = arr.get(i + 1);
            Map<String, String> map = flatFieldsToMap(fields);
            if (map.isEmpty()) continue;

            ChunkHit c = new ChunkHit();
            c.setBlogId(parseLong(map.get("blogId"), -1L));
            c.setTitle(map.get("title"));
            c.setText(map.get("text"));
            c.setSim(parseDouble(map.get("sim"), 1.0));
            c.setCreatedAt(parseLong(map.get("createdAt"), 0L));
            if (c.getBlogId() > 0) out.add(c);
        }
        return out;
    }

    /** 将 RediSearch 返回的 [field, value, field, value...] 拍平成 Map */
    @SuppressWarnings("unchecked")
    private Map<String, String> flatFieldsToMap(Object fields) {
        Map<String, String> map = new HashMap<>();
        if (!(fields instanceof List)) return map;

        List<Object> lst = (List<Object>) fields;
        // 兼容返回形如 ["field","value","field","value"...]
        for (int j = 0; j + 1 < lst.size(); j += 2) {
            String k = asStr(lst.get(j));
            Object vObj = lst.get(j + 1);
            String v;
            if (vObj instanceof byte[]) {
                v = new String((byte[]) vObj, StandardCharsets.UTF_8);
            } else if (vObj instanceof List) {
                // HASH 可能返回嵌套，再拍平
                v = asStr(vObj);
            } else {
                v = String.valueOf(vObj);
            }
            map.put(k, v);
        }
        return map;
    }

    private String asStr(Object o) {
        if (o == null) return "";
        if (o instanceof byte[]) return new String((byte[]) o, StandardCharsets.UTF_8);
        return String.valueOf(o);
    }

    private long parseLong(String s, long def) {
        try { return s == null ? def : Long.parseLong(s); } catch (Exception e) { return def; }
    }
    private double parseDouble(String s, double def) {
        try { return s == null ? def : Double.parseDouble(s); } catch (Exception e) { return def; }
    }

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    // ========================== 打分/聚合与 DTO ==========================

    private List<ScoredChunk> rank(List<ChunkHit> hits) {
        long now = System.currentTimeMillis();
        double wSem = 0.7, wTime = 0.2, wHot = 0.1; // 热度先占位
        List<ScoredChunk> out = new ArrayList<>(hits.size());
        for (ChunkHit h : hits) {
            // RediSearch 给的是距离（越小越好），简单映射为相似度
            double sem = Math.max(0.0, 1.0 - h.getSim() / 2.0);
            double days = (now - h.getCreatedAt()) / 86_400_000.0;
            double timeScore = Math.exp(-days / 7.0); // 7 天半衰期示例
            double hot = 0.5;
            double score = wSem * sem + wTime * timeScore + wHot * hot;
            out.add(new ScoredChunk(h, score));
        }
        out.sort(Comparator.comparingDouble(ScoredChunk::getScore).reversed());
        return out;
    }

    private List<SearchResult> aggregateAndFormat(List<ScoredChunk> ranked, int size) {
        Map<Long, List<ScoredChunk>> byBlog = new LinkedHashMap<>();
        for (ScoredChunk sc : ranked) {
            byBlog.computeIfAbsent(sc.hit.getBlogId(), k -> new ArrayList<>()).add(sc);
        }

        List<SearchResult> out = new ArrayList<>();
        for (Map.Entry<Long, List<ScoredChunk>> e : byBlog.entrySet()) {
            List<ScoredChunk> list = e.getValue();
            ScoredChunk best = list.get(0); // 取该 blog 最优片段
            String preview = truncate(best.hit.getText(), 180);

            SearchResult r = new SearchResult();
            r.setBlogId(best.hit.getBlogId());
            r.setTitle(best.hit.getTitle());
            r.setPreview(preview);
            r.setScore(best.score);
            r.setCreatedAt(best.hit.getCreatedAt());

            out.add(r);
            if (out.size() >= size) break;
        }
        return out;
    }

    private String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }

    private String toTagFilter(List<Long> ids) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append('|');
            sb.append(ids.get(i));
        }
        sb.append('}');
        return sb.toString();
    }
}