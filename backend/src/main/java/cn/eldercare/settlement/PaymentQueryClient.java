package cn.eldercare.settlement;

import java.math.BigDecimal;

interface PaymentQueryClient {
    record Remote(String status, long orderRef, int settlementVersion, BigDecimal amount) { }

    Remote query(String baseUrl, String serviceToken, String attemptRef) throws Exception;
}
