package com.hmdp.utils;

public final class TextChunker {
    private TextChunker(){}

    public static class Chunk {
        private final String text;
        private final int start;
        private final int end;
        public Chunk(String t, int s, int e){
            this.text=t; this.start=s; this.end=e;
        }
        public String getText(){
            return text;
        }
        public int getStart(){
            return start;
        }
        public int getEnd(){
            return end;
        }
    }

    public static java.util.List<Chunk> split(String text, int size, int overlap) {
        java.util.List<Chunk> list = new java.util.ArrayList<Chunk>();
        if (text == null) return list;
        int n = text.length(), i = 0;
        while (i < n) {
            int end = Math.min(i + size, n);
            list.add(new Chunk(text.substring(i, end), i, end));
            if (end == n) break;
            i = Math.max(end - overlap, i + 1);
        }
        return list;
    }

    public static String summary(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }
}
