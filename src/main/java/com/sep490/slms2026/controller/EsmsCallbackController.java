package com.sep490.slms2026.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook nhan callback trang thai cuoi tu eSMS (HTTP GET).
 * Cau hinh URL cong khai HTTPS trong ESMS_CALLBACK_URL (vi du qua ngrok khi dev local).
 */
@RestController
@RequestMapping("/api/v1/webhooks/esms")
@Slf4j
public class EsmsCallbackController {

    @GetMapping("/callback")
    public ResponseEntity<String> handleCallback(
            @RequestParam(value = "SMSID", required = false) String smsId,
            @RequestParam(value = "SendStatus", required = false) String sendStatus,
            @RequestParam(value = "SendSuccess", required = false) String sendSuccess,
            @RequestParam(value = "SendFailed", required = false) String sendFailed,
            @RequestParam(value = "phonenumber", required = false) String phoneNumber,
            @RequestParam(value = "RequestId", required = false) String requestId,
            @RequestParam(value = "TypeId", required = false) String typeId,
            @RequestParam(value = "telcoid", required = false) String telcoId,
            @RequestParam(value = "TotalPrice", required = false) String totalPrice,
            @RequestParam(value = "error_info", required = false) String errorInfo
    ) {
        log.info(
                "eSMS callback: SMSID={}, SendStatus={}, SendSuccess={}, SendFailed={}, phone={}, RequestId={}, TypeId={}",
                smsId,
                sendStatus,
                sendSuccess,
                sendFailed,
                maskPhone(phoneNumber),
                requestId,
                typeId
        );

        boolean deliveryOk = "1".equals(sendSuccess) && !"1".equals(sendFailed);
        if ("5".equals(sendStatus) && !deliveryOk) {
            log.error(
                    "eSMS giao tin THAT BAI (SendStatus=5 nhung SendSuccess={}, SendFailed={}). "
                            + "Kiem tra template SmsType=8 tren portal va ESMS_CONTENT_TEMPLATE. error_info={}",
                    sendSuccess,
                    sendFailed,
                    errorInfo
            );
        } else if (deliveryOk) {
            log.info("eSMS giao tin THANH CONG toi {}", maskPhone(phoneNumber));
        }

        return ResponseEntity.ok("OK");
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "****";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 2);
    }
}
