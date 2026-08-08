package com.orangejd.server.service;

public record PricingSnapshot(String serviceKey, long segmentCost, long version) {
}
