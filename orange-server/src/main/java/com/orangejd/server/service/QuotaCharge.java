package com.orangejd.server.service;

public record QuotaCharge(long balance, long amount, long priceVersion,
                          String operationNo, boolean replayed) {
}
