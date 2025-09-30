package com.hmdp.entity;

public class CacheEntry<T> {
    public T data;
    public long expireAt; // 毫秒时间戳
    public CacheEntry() {}
    public CacheEntry(T data, long expireAt) { this.data = data; this.expireAt = expireAt; }
}
