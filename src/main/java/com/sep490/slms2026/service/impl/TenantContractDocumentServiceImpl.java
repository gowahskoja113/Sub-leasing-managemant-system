package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.config.ContractDocumentUploadProperties;
import com.sep490.slms2026.config.ContractLessorProperties;
import com.sep490.slms2026.dto.response.TenantContractDocumentResponse;
import com.sep490.slms2026.dto.response.HouseholdMemberResponse;
import com.sep490.slms2026.dto.response.TenantContractResponse;
import com.sep490.slms2026.entity.HouseholdMember;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.entity.Tenant;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.entity.Zone;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.enums.PaymentStatus;
import com.sep490.slms2026.enums.Role;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.service.TenantContractDocumentService;
import com.sep490.slms2026.util.ContractTemplateConstants;
import com.sep490.slms2026.util.DocxTemplateRenderer;
import com.sep490.slms2026.util.TenantContractStatusHelper;
import com.sep490.slms2026.util.VietnameseNumberToWords;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import com.sep490.slms2026.repository.EquipmentRepository;
import com.sep490.slms2026.dto.response.TenantContractDetailResponse.EquipmentItem;

@Service
@RequiredArgsConstructor
public class TenantContractDocumentServiceImpl implements TenantContractDocumentService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final NumberFormat VND = NumberFormat.getInstance(new Locale("vi", "VN"));

    private final TenantContractRepository tenantContractRepository;
    private final ContractDocumentUploadProperties uploadProperties;
    private final ContractLessorProperties lessorProperties;
    private final EquipmentRepository equipmentRepository;

    @Override
    @Transactional(readOnly = true)
    public TenantContractDocumentResponse generateAndStore(Long contractId) {
        return getDocument(contractId);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] renderDraftDocument(Long contractId) {
        TenantContract contract = loadAndSync(contractId);
        assertCanGenerateDraft(contract);
        return renderDocx(contract);
    }

    @Override
    @Transactional(readOnly = true)
    public TenantContractDocumentResponse getDocument(Long contractId) {
        TenantContract contract = loadAndSync(contractId);
        if (resolveContractFileUrl(contract) == null) {
            throw new BusinessException(
                    "Hợp đồng chưa có file — gọi POST .../draft-document, upload Cloudinary, rồi PUT draftContractFileUrl");
        }
        return toDocumentResponse(contract);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] downloadContractDocument(Long contractId, UUID userId, String roleName) {
        TenantContract contract = loadAndSync(contractId);
        assertCanView(contract, userId, roleName);
        String url = resolveContractFileUrl(contract);
        if (url == null || url.isBlank()) {
            throw new BusinessException(
                    "Hợp đồng chưa có file — gọi POST .../draft-document, upload Cloudinary, rồi PUT draftContractFileUrl");
        }
        try {
            return fetchContractFileBytes(url);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Không tải được file hợp đồng: " + ex.getMessage());
        } catch (IOException ex) {
            throw new BusinessException("Không tải được file hợp đồng: " + ex.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TenantContractResponse getContractForUser(Long contractId, UUID userId, String roleName) {
        TenantContract contract = loadAndSync(contractId);
        assertCanView(contract, userId, roleName);
        return toContractResponse(contract);
    }

    @Override
    @Transactional
    public List<TenantContractResponse> listContractsForTenant(UUID tenantUserId) {
        List<TenantContract> contracts = tenantContractRepository.findByTenantId(tenantUserId);
        for (TenantContract contract : contracts) {
            if (TenantContractStatusHelper.syncExpiredIfNeeded(contract)) {
                tenantContractRepository.save(contract);
            }
        }
        return contracts.stream().map(this::toContractResponse).toList();
    }

    private TenantContract loadAndSync(Long contractId) {
        TenantContract contract = tenantContractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hợp đồng ID: " + contractId));
        if (TenantContractStatusHelper.syncExpiredIfNeeded(contract)) {
            tenantContractRepository.save(contract);
        }
        return contract;
    }

    private void assertCanGenerateDraft(TenantContract contract) {
        if (contract.getStatus() != ContractStatus.DRAFT) {
            throw new BusinessException("Chỉ xuất file nháp khi hợp đồng đang ở trạng thái DRAFT");
        }
    }

    private void assertCanView(TenantContract contract, UUID userId, String roleName) {
        Role role = Role.valueOf(roleName);
        if (role == Role.ROLE_ADMIN || role == Role.ROLE_MANAGER) {
            return;
        }
        if (role == Role.ROLE_TENANT) {
            if (contract.getTenant() == null || !contract.getTenant().getId().equals(userId)) {
                throw new BusinessException("Bạn không có quyền xem hợp đồng này");
            }
            return;
        }
        throw new BusinessException("Bạn không có quyền xem hợp đồng này");
    }

    private byte[] renderDocx(TenantContract contract) {
        Map<String, String> vars = buildVariables(contract);
        try (InputStream in = new ClassPathResource(uploadProperties.getTemplateClasspath()).getInputStream()) {
            return DocxTemplateRenderer.render(in, vars);
        } catch (IOException ex) {
            throw new BusinessException("Không đọc được template hợp đồng: " + ex.getMessage());
        }
    }

    private static String resolveContractFileUrl(TenantContract contract) {
        if (contract.getDraftContractFileUrl() != null && !contract.getDraftContractFileUrl().isBlank()) {
            return contract.getDraftContractFileUrl();
        }
        return contract.getDocumentUrl();
    }

    private byte[] fetchContractFileBytes(String url) throws IOException, InterruptedException {
        String marker = "/uploads/contracts/";
        int localIdx = url.indexOf(marker);
        if (localIdx >= 0) {
            String relative = url.substring(localIdx + marker.length());
            Path file = Path.of(uploadProperties.getDir()).toAbsolutePath().normalize().resolve(relative);
            if (Files.isRegularFile(file)) {
                return Files.readAllBytes(file);
            }
        }

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() >= 400) {
            throw new BusinessException("Không tải được file hợp đồng từ storage: HTTP " + response.statusCode());
        }
        byte[] body = response.body();
        if (body == null || body.length == 0) {
            throw new BusinessException("File hợp đồng trống hoặc không tồn tại");
        }
        return body;
    }

    /**
     * Map biến template. Phần I. BÊN CHO THUÊ in sẵn trong file Word — không map lessor* ở đây.
     * Xem docs/contract-template-content.md
     */
    private Map<String, String> buildVariables(TenantContract contract) {
        Tenant tenant = contract.getTenant();
        User tenantUser = tenant != null ? tenant.getUser() : null;
        Property property = contract.getProperty();
        Room room = contract.getRoom();
        Zone zone = property.getZone();

        LocalDate signDate = contract.getDocumentGeneratedAt() != null
                ? contract.getDocumentGeneratedAt().toLocalDate()
                : (contract.getExpectedReceptionDate() != null ? contract.getExpectedReceptionDate()
                : (contract.getPaidAt() != null ? contract.getPaidAt().toLocalDate() : LocalDate.now()));

        Map<String, String> vars = new HashMap<>();
        vars.put("contractCode", contract.getContractCode());
        vars.put("signPlace", lessorProperties.getSignPlace());
        vars.put("signDay", String.valueOf(signDate.getDayOfMonth()));
        vars.put("signMonth", String.valueOf(signDate.getMonthValue()));
        vars.put("signYear", String.valueOf(signDate.getYear()));

        vars.put("tenantFullName", tenantUser != null
                ? nullToEmpty(tenantUser.getFullName()) : nullToEmpty(contract.getDraftTenantName()));
        vars.put("tenantCccd", tenant != null
                ? nullToEmpty(tenant.getCccd()) : nullToEmpty(contract.getDraftTenantCccd()));
        vars.put("tenantPhone", tenantUser != null
                ? nullToEmpty(tenantUser.getPhoneNumber()) : nullToEmpty(contract.getDraftTenantPhone()));
        vars.put("tenantDob", formatDate(tenant != null && tenant.getDateOfBirth() != null
                ? tenant.getDateOfBirth() : contract.getDraftTenantDob()));
        vars.put("tenantAddress", "");
        vars.put("tenantEmail", "");

        vars.put("propertyName", property.getPropertyName());
        vars.put("propertyAddress", property.getAddress());
        vars.put("propertyType", Boolean.TRUE.equals(property.getWholeHouse())
                ? ContractTemplateConstants.PROPERTY_TYPE_WHOLE_HOUSE
                : ContractTemplateConstants.PROPERTY_TYPE_ROOM);
        vars.put("zoneName", zone != null ? nullToEmpty(zone.getName()) : "");
        vars.put("roomNumber", room != null ? room.getRoomNumber() : "—");
        vars.put("rentalUnit", formatRentalUnit(property, room));
        vars.put("areaSize", property.getAreaSize() != null ? String.valueOf(property.getAreaSize()) : "");
        vars.put("totalFloor", property.getTotalFloor() != null ? String.valueOf(property.getTotalFloor()) : "");
        vars.put("propertyDescription", nullToEmpty(property.getDescriptions()));

        vars.put("rentAmount", formatMoney(contract.getRentAmount()));
        vars.put("rentAmountInWords", VietnameseNumberToWords.convert(contract.getRentAmount()));
        vars.put("deposit", formatMoney(contract.getDeposit()));
        vars.put("depositInWords", VietnameseNumberToWords.convert(contract.getDeposit()));
        vars.put("depositMonths", contract.getDepositMonths() != null
                ? String.valueOf(contract.getDepositMonths()) : "");

        vars.put("serviceFee", formatMoney(property.getServiceFee()));
        vars.put("electricityUnitPrice", formatMoney(property.getElectricityUnitPrice()));
        vars.put("waterUnitPrice", formatMoney(property.getWaterUnitPrice()));
        vars.put("paymentMethod", ContractTemplateConstants.PAYMENT_METHOD);
        vars.put("paidAt", contract.getPaidAt() != null
                ? contract.getPaidAt().format(DATETIME_FMT) : "");

        vars.put("startDate", formatDate(contract.getStartDate()));
        vars.put("endDate", formatDate(contract.getEndDate()));
        vars.put("moveInDate", formatDate(contract.getMoveInDate()));
        vars.put("leaseDurationMonths", leaseMonths(contract.getStartDate(), contract.getEndDate()));

        vars.put("initialElectricReading", formatDecimal(contract.getInitialElectricReading()));
        vars.put("initialWaterReading", formatDecimal(contract.getInitialWaterReading()));
        vars.put("roomConditionNote", nullToEmpty(contract.getRoomConditionNote()));
        vars.put("equipmentSnapshot", nullToEmpty(contract.getEquipmentSnapshot()));
        vars.put("householdMembers", formatHouseholdMembers(contract.getHouseholdMembers()));

        return vars;
    }

    private static String formatHouseholdMembers(List<HouseholdMember> members) {
        if (members == null || members.isEmpty()) {
            return "Không có thành viên ở cùng.";
        }
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (HouseholdMember m : members) {
            if (index > 1) {
                sb.append("\n");
            }
            sb.append(index++).append(". ")
                    .append(nullToEmpty(m.getFullName()))
                    .append(" | Quan hệ: ").append(nullToEmpty(m.getRelation()))
                    .append(" | CCCD: ").append(nullToEmpty(m.getCccd()))
                    .append(" | SĐT: ").append(nullToEmpty(m.getPhone()))
                    .append(" | Ngày sinh: ").append(formatDate(m.getDateOfBirth()));
        }
        return sb.toString();
    }

    private TenantContractDocumentResponse toDocumentResponse(TenantContract contract) {
        return TenantContractDocumentResponse.builder()
                .contractId(contract.getId())
                .contractCode(contract.getContractCode())
                .documentUrl(resolveContractFileUrl(contract))
                .documentGeneratedAt(contract.getDocumentGeneratedAt())
                .effective(TenantContractStatusHelper.isEffective(contract.getStatus(), contract.getEndDate()))
                .effectiveLabel(TenantContractStatusHelper.effectiveLabel(
                        contract.getStatus(), contract.getEndDate()))
                .build();
    }

    private TenantContractResponse toContractResponse(TenantContract c) {
        Tenant tenant = c.getTenant();
        User tenantUser = tenant != null ? tenant.getUser() : null;
        Room room = c.getRoom();
        Property property = c.getProperty();

        String type = (room == null) ? "WHOLE_HOUSE" : "ROOM";
        String propertyNameStr = property.getPropertyName();
        
        List<EquipmentItem> equipmentList = null;
        if (room != null) {
            equipmentList = equipmentRepository.findByRoomId(room.getId()).stream()
                .map(eq -> EquipmentItem.builder()
                        .id(eq.getId())
                        .name(eq.getCatalog() != null ? eq.getCatalog().getName() : "")
                        .condition(eq.getStatus() != null ? eq.getStatus().name() : "Tốt")
                        .quantity(1)
                        .build())
                .collect(Collectors.toList());
        } else {
            // For whole house, we can query by propertyId and roomId is null
            // Actually findByPropertyId returns all equipments. Wait, whole house means all equipments in property
            equipmentList = equipmentRepository.findByPropertyId(property.getId()).stream()
                .map(eq -> EquipmentItem.builder()
                        .id(eq.getId())
                        .name(eq.getCatalog() != null ? eq.getCatalog().getName() : "")
                        .condition(eq.getStatus() != null ? eq.getStatus().name() : "Tốt")
                        .quantity(1)
                        .build())
                .collect(Collectors.toList());
        }

        return TenantContractResponse.builder()
                .id(c.getId())
                .propertyId(property.getId())
                .roomId(room != null ? room.getId() : null)
                .roomNumber(room != null ? room.getRoomNumber() : null)
                .tenantUserId(tenant != null ? tenant.getId() : null)
                .tenantFullName(tenantUser != null ? tenantUser.getFullName() : c.getDraftTenantName())
                .tenantPhone(tenantUser != null ? tenantUser.getPhoneNumber() : c.getDraftTenantPhone())
                .tenantCccd(tenant != null ? tenant.getCccd() : c.getDraftTenantCccd())
                .tenantDateOfBirth(tenant != null ? tenant.getDateOfBirth() : c.getDraftTenantDob())
                .contractCode(c.getContractCode())
                .rentAmount(c.getRentAmount())
                .deposit(c.getDeposit())
                .moveInDate(c.getMoveInDate())
                .startDate(c.getStartDate())
                .endDate(c.getEndDate())
                .status(c.getStatus())
                .effective(TenantContractStatusHelper.isEffective(c.getStatus(), c.getEndDate()))
                .effectiveLabel(TenantContractStatusHelper.effectiveLabel(c.getStatus(), c.getEndDate()))
                .equipmentSnapshot(c.getEquipmentSnapshot())
                .depositMonths(c.getDepositMonths())
                .initialElectricReading(c.getInitialElectricReading())
                .initialWaterReading(c.getInitialWaterReading())
                .electricMeterImageUrl(c.getElectricMeterImageUrl())
                .waterMeterImageUrl(c.getWaterMeterImageUrl())
                .roomConditionUrls(c.getRoomConditionUrls())
                .roomConditionNote(c.getRoomConditionNote())
                .paymentStatus(c.getPaymentStatus())
                .payosOrderCode(c.getPayosOrderCode())
                .documentUrl(resolveContractFileUrl(c))
                .documentGeneratedAt(c.getDocumentGeneratedAt())
                .type(type)
                .lessorName("Ban Quản Lý") // fallback or get from property if possible
                .lessorPhone("")
                .lesseeName(tenantUser != null ? tenantUser.getFullName() : c.getDraftTenantName())
                .lesseeCccd(tenant != null ? tenant.getCccd() : c.getDraftTenantCccd())
                .lesseePhone(tenantUser != null ? tenantUser.getPhoneNumber() : c.getDraftTenantPhone())
                .propertyName(propertyNameStr)
                .notes(c.getRoomConditionNote())
                .signedAt(c.getDocumentGeneratedAt() != null ? c.getDocumentGeneratedAt() : c.getPaidAt())
                .terminatedAt(c.getStatus() == ContractStatus.TERMINATED ? LocalDateTime.now() : null)
                .terminationReason(null)
                .pdfUrl(resolveContractFileUrl(c))
                .equipmentList(equipmentList)
                .draftContractFileUrl(c.getDraftContractFileUrl())
                .contractFileAvailable(resolveContractFileUrl(c) != null)
                .expectedReceptionDate(c.getExpectedReceptionDate())
                .assignedManagerId(c.getAssignedManager() != null ? c.getAssignedManager().getId() : null)
                .assignedManagerName(c.getAssignedManager() != null ? c.getAssignedManager().getFullName() : null)
                .householdMembers(c.getHouseholdMembers() != null ? c.getHouseholdMembers().stream()
                        .map(hm -> HouseholdMemberResponse.builder()
                                .id(hm.getId())
                                .fullName(hm.getFullName())
                                .relation(hm.getRelation())
                                .phone(hm.getPhone())
                                .dateOfBirth(hm.getDateOfBirth())
                                .cccd(hm.getCccd())
                                .build())
                        .collect(Collectors.toList()) : null)
                .build();
    }

    private static String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_FMT) : "";
    }

    private static String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return VND.format(amount.longValue());
    }

    private static String formatDecimal(BigDecimal value) {
        return value != null ? value.toPlainString() : "";
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    private static String leaseMonths(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            return "";
        }
        return String.valueOf(ChronoUnit.MONTHS.between(start, end));
    }

    private static String formatRentalUnit(Property property, Room room) {
        StringBuilder sb = new StringBuilder(nullToEmpty(property.getPropertyName()));
        if (room != null && room.getRoomNumber() != null && !room.getRoomNumber().isBlank()) {
            sb.append(" - Phòng ").append(room.getRoomNumber());
        }
        if (property.getAddress() != null && !property.getAddress().isBlank()) {
            sb.append(" (").append(property.getAddress()).append(")");
        }
        return sb.toString();
    }
}
