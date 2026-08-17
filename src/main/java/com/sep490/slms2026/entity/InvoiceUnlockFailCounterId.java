package com.sep490.slms2026.entity;

import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class InvoiceUnlockFailCounterId implements Serializable {
    private UUID managerId;
    private Long invoiceId;
}
