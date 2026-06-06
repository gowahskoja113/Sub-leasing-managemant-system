package com.sep490.slms2026.util;

import tools.jackson.databind.json.JsonMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class SmsService {

    private static final String ESMS_SEND_URL =
            "https://rest.esms.vn/MainService.svc/json/SendMultipleMessage_V4_post_json/";
    private static final String ESMS_STATUS_URL =
            "https://rest.esms.vn/MainService.svc/json/GetSendStatus?RefId={refId}&ApiKey={apiKey}&SecretKey={secretKey}";
    private static final String ESMS_RECEIVER_STATUS_URL =
            "https://rest.esms.vn/MainService.svc/json/GetSmsReceiverStatus_get?ApiKey={apiKey}&SecretKey={secretKey}&RefId={refId}";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)(?::(\\d+))?}");
    private static final Pattern NON_ASCII_SMS = Pattern.compile("[^\\x20-\\x7E]");
    private static final SecureRandom RANDOM = new SecureRandom();

    /** builtin = mau co san eSMS (Baotrixemay); custom = SmsType 8 + template tu portal */
    @Value("${esms.mode:builtin}")
    private String esmsMode;

    @Value("${esms.api-key}")
    private String apiKey;

    @Value("${esms.secret-key}")
    private String secretKey;

    @Value("${esms.sms-type:2}")
    private String smsType;

    @Value("${esms.brandname:Baotrixemay}")
    private String brandname;

    @Value("${esms.is-unicode:0}")
    private String isUnicode;

    @Value("${esms.sandbox:0}")
    private String sandbox;

    /** Chi dung khi esms.mode=custom */
    @Value("${esms.content-template:}")
    private String contentTemplate;

    /** registration | password-reset | thank-you — chi dung khi esms.mode=builtin */
    @Value("${esms.builtin-template:registration}")
    private String builtinTemplateId;

    @Value("${esms.code-length:6}")
    private int codeLength;

    @Value("${esms.callback-url:}")
    private String callbackUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final JsonMapper jsonMapper;
    private String activeContentTemplate;

    public SmsService(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @PostConstruct
    void init() {
        if (isBuiltinMode()) {
            activeContentTemplate = EsmsBuiltinTemplates.resolve(builtinTemplateId);
            smsType = EsmsBuiltinTemplates.SMS_TYPE;
            brandname = EsmsBuiltinTemplates.BRANDNAME;
            log.info(
                    "eSMS mode=builtin, brandname={}, templateId={}, contentPattern=[{}], codeLength={}",
                    brandname,
                    builtinTemplateId,
                    activeContentTemplate,
                    Math.min(codeLength, EsmsBuiltinTemplates.MAX_CODE_LENGTH)
            );
        } else {
            activeContentTemplate = contentTemplate;
            log.info("eSMS mode=custom, smsType={}, template=[{}]", smsType, activeContentTemplate);
        }
    }

    public String preparePasswordForTenantSms(String requestedPassword) {
        if (isBuiltinMode() || "otp".equalsIgnoreCase(esmsMode)) {
            return generateCode(Math.min(codeLength, EsmsBuiltinTemplates.MAX_CODE_LENGTH));
        }
        return sanitizeCode(requestedPassword, codeLength);
    }

    @Async
    public void sendCredentialsSms(String toPhoneNumber, String username, String rawPassword) {
        String phone = normalizeVietnamesePhone(toPhoneNumber);
        if (phone == null || phone.isBlank()) {
            log.warn("Bo qua gui SMS: so dien thoai trong hoac khong hop le");
            return;
        }

        try {
            String content = buildContent(activeContentTemplate, rawPassword);
            if ("0".equals(isUnicode)) {
                content = toAsciiSmsContent(content);
            }

            log.info(
                    "eSMS gui tin mode={}, smsType={}, brandname={}, phone={}, content=[{}]",
                    esmsMode,
                    smsType,
                    brandname,
                    maskPhone(phone),
                    content
            );

            String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ApiKey", apiKey);
            body.put("SecretKey", secretKey);
            body.put("Phone", phone);
            body.put("Content", content);
            body.put("SmsType", smsType);
            body.put("IsUnicode", isUnicode);
            body.put("Sandbox", sandbox);
            body.put("RequestId", requestId);

            if (requiresBrandname()) {
                body.put("Brandname", brandname);
            }
            if (callbackUrl != null && !callbackUrl.isBlank()) {
                body.put("CallbackUrl", callbackUrl.trim());
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            String rawResponse = restTemplate.postForObject(ESMS_SEND_URL, request, String.class);
            EsmsSendResponse response = jsonMapper.readValue(rawResponse, EsmsSendResponse.class);

            if (!"100".equals(response.getCodeResult())) {
                log.error(
                        "eSMS tu choi request: CodeResult={}, ErrorMessage={}",
                        response.getCodeResult(),
                        response.getErrorMessage()
                );
                return;
            }

            log.info("eSMS tiep nhan request. SMSID={}", response.getSmsId());
            logDeliveryResult(response.getSmsId());

        } catch (Exception e) {
            log.error("Loi khi goi API eSMS: {}", e.getMessage(), e);
        }
    }

    private boolean isBuiltinMode() {
        return "builtin".equalsIgnoreCase(esmsMode);
    }

    private boolean requiresBrandname() {
        return "2".equals(smsType);
    }

    String buildContent(String template, String code) {
        int maxLen = extractPlaceholderLength(template, "code");
        if (maxLen <= 0) {
            maxLen = EsmsBuiltinTemplates.MAX_CODE_LENGTH;
        }
        String codeValue = formatCode(code, maxLen);

        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1).toLowerCase();
            int len = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : maxLen;
            String value = "code".equals(name) || "p".equals(name) ? formatCode(codeValue, len) : "";
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private void logDeliveryResult(String smsId) {
        if (smsId == null || smsId.isBlank()) {
            return;
        }
        try {
            Thread.sleep(2500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        try {
            String statusRaw = restTemplate.getForObject(
                    ESMS_STATUS_URL, String.class, smsId, apiKey, secretKey);
            EsmsSendStatusResponse status = jsonMapper.readValue(statusRaw, EsmsSendStatusResponse.class);
            log.info(
                    "eSMS trang thai: SMSID={}, SendStatus={}, SendSuccess={}, SendFailed={}",
                    smsId,
                    status.getSendStatus(),
                    firstNonBlank(status.getSendSuccess(), status.getSentSuccess()),
                    firstNonBlank(status.getSendFailed(), status.getSentFailed())
            );

            String receiverRaw = restTemplate.getForObject(
                    ESMS_RECEIVER_STATUS_URL, String.class, apiKey, secretKey, smsId);
            EsmsReceiverStatusResponse receivers = jsonMapper.readValue(receiverRaw, EsmsReceiverStatusResponse.class);
            if (receivers.getReceiverList() == null) {
                return;
            }
            for (EsmsReceiverStatusResponse.Receiver receiver : receivers.getReceiverList()) {
                log.info(
                        "eSMS den {}: SentResult={}, IsSent={}",
                        maskPhone(receiver.getPhone()),
                        receiver.getSentResult(),
                        receiver.getSent()
                );
                if (Boolean.FALSE.equals(receiver.getSentResult())) {
                    log.error(
                            "eSMS THAT BAI. mode={}. Builtin: giu nguyen mau Baotrixemay, chi doi {{code}} (toi da 8 ky tu). "
                                    + "Mau: registration | password-reset | thank-you",
                            esmsMode
                    );
                }
            }
        } catch (Exception e) {
            log.warn("Khong doc duoc trang thai eSMS SMSID={}: {}", smsId, e.getMessage());
        }
    }

    static int extractPlaceholderLength(String template, String name) {
        Matcher matcher = Pattern.compile(
                "\\{" + name + ":(\\d+)\\}", Pattern.CASE_INSENSITIVE).matcher(template);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return template.toLowerCase().contains("{" + name.toLowerCase() + "}") ? EsmsBuiltinTemplates.MAX_CODE_LENGTH : 0;
    }

    static String formatCode(String rawCode, int maxLen) {
        String code = sanitizeCode(rawCode, maxLen);
        if (code.length() > maxLen) {
            code = code.substring(0, maxLen);
        }
        return code;
    }

    static String sanitizeCode(String value, int maxLen) {
        if (value == null) {
            return generateCode(maxLen);
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String alphanumeric = normalized.replaceAll("[^a-zA-Z0-9]", "");
        if (alphanumeric.isEmpty()) {
            return generateCode(maxLen);
        }
        return alphanumeric.length() > maxLen ? alphanumeric.substring(0, maxLen) : alphanumeric;
    }

    static String generateCode(int length) {
        int len = Math.min(length, EsmsBuiltinTemplates.MAX_CODE_LENGTH);
        int max = (int) Math.pow(10, len);
        int value = RANDOM.nextInt(max);
        return String.format("%0" + len + "d", value);
    }

    static String normalizeVietnamesePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String normalized = phone.replaceAll("[\\s\\-.]", "");
        if (normalized.startsWith("+")) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith("84") && normalized.length() >= 11) {
            normalized = "0" + normalized.substring(2);
        }
        if (!normalized.startsWith("0") && normalized.matches("\\d{9,10}")) {
            normalized = "0" + normalized;
        }
        return normalized.matches("0\\d{9,10}") ? normalized : null;
    }

    static String toAsciiSmsContent(String content) {
        String withoutAccents = Normalizer.normalize(content, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return NON_ASCII_SMS.matcher(withoutAccents).replaceAll("");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "****";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 2);
    }
}
