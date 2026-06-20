package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.response.BulkImportImageContractResult;
import com.sep490.slms2026.dto.response.BulkImportImagesResponse;
import com.sep490.slms2026.entity.InboundContract;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.imports.ParsedZipImage;
import com.sep490.slms2026.imports.PropertyImageZipParser;
import com.sep490.slms2026.repository.InboundContractRepository;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.service.BulkPropertyImageImportService;
import com.sep490.slms2026.service.PropertyImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BulkPropertyImageImportServiceImpl implements BulkPropertyImageImportService {

    private static final String STATUS_ATTACHED = "ATTACHED";
    private static final String STATUS_PREVIEW = "PREVIEW";
    private static final String STATUS_NOT_FOUND = "NOT_FOUND";

    private final PropertyImageZipParser zipParser;
    private final PropertyImageStorage imageStorage;
    private final InboundContractRepository inboundContractRepository;
    private final PropertyRepository propertyRepository;

    @Override
    @Transactional
    public BulkImportImagesResponse importFromZip(MultipartFile zipFile, boolean dryRun) {
        if (zipFile == null || zipFile.isEmpty()) {
            throw new BusinessException("File zip không được để trống");
        }

        List<ParsedZipImage> parsedImages = zipParser.parse(zipFile);
        Map<String, List<ParsedZipImage>> imagesByContract = groupByContractCode(parsedImages);

        List<BulkImportImageContractResult> results = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int contractsMatched = 0;
        int contractsNotFound = 0;
        int totalImagesAttached = 0;

        for (Map.Entry<String, List<ParsedZipImage>> entry : imagesByContract.entrySet()) {
            String contractCode = entry.getKey();
            List<ParsedZipImage> contractImages = entry.getValue().stream()
                    .sorted(Comparator.comparing(ParsedZipImage::fileName, String.CASE_INSENSITIVE_ORDER))
                    .toList();

            Optional<InboundContract> contractOpt =
                    inboundContractRepository.findByContractCodeIgnoreCaseWithProperty(contractCode);

            if (contractOpt.isEmpty()) {
                contractsNotFound++;
                warnings.add("Mã hợp đồng \"" + contractCode + "\" có "
                        + contractImages.size() + " ảnh trong zip nhưng không tìm thấy trong DB — bỏ qua");
                results.add(BulkImportImageContractResult.builder()
                        .status(STATUS_NOT_FOUND)
                        .contractCode(contractCode)
                        .imagesAttached(0)
                        .message("Không tìm thấy căn với mã hợp đồng này (import Excel trước?)")
                        .build());
                continue;
            }

            InboundContract contract = contractOpt.get();
            Property property = contract.getProperty();
            contractsMatched++;

            if (dryRun) {
                results.add(BulkImportImageContractResult.builder()
                        .status(STATUS_PREVIEW)
                        .contractCode(contract.getContractCode())
                        .propertyId(property.getId())
                        .propertyName(property.getPropertyName())
                        .imagesAttached(contractImages.size())
                        .message("Sẽ gán " + contractImages.size() + " ảnh khi import")
                        .build());
                totalImagesAttached += contractImages.size();
                continue;
            }

            List<String> imageUrls = new ArrayList<>();
            for (ParsedZipImage image : contractImages) {
                imageUrls.add(imageStorage.store(contract.getContractCode(), image.fileName(), image.content()));
            }

            property.setImageUrls(imageUrls);
            propertyRepository.save(property);
            totalImagesAttached += imageUrls.size();

            results.add(BulkImportImageContractResult.builder()
                    .status(STATUS_ATTACHED)
                    .contractCode(contract.getContractCode())
                    .propertyId(property.getId())
                    .propertyName(property.getPropertyName())
                    .imagesAttached(imageUrls.size())
                    .build());
        }

        return BulkImportImagesResponse.builder()
                .dryRun(dryRun)
                .contractsInZip(imagesByContract.size())
                .contractsMatched(contractsMatched)
                .contractsNotFound(contractsNotFound)
                .imagesAttached(totalImagesAttached)
                .results(results)
                .warnings(warnings)
                .build();
    }

    private Map<String, List<ParsedZipImage>> groupByContractCode(List<ParsedZipImage> images) {
        Map<String, List<ParsedZipImage>> grouped = new LinkedHashMap<>();
        for (ParsedZipImage image : images) {
            String key = image.contractCode().trim();
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(image);
        }
        return grouped;
    }
}
