package com.sep490.slms2026.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UtilityCustomerCodeHelperTest {

    @Test
    void normalize_stripsSpacesDashesAndLowercases() {
        assertEquals("pe05000222239", UtilityCustomerCodeHelper.normalize("PE 05000222239"));
        assertEquals("pe05000222239", UtilityCustomerCodeHelper.normalize("PE05000222239"));
        assertEquals("pe05000222239", UtilityCustomerCodeHelper.normalize("PE-0500-0222239"));
        assertEquals("pe05000222239", UtilityCustomerCodeHelper.normalize("  pe 0500 0222239  "));
    }

    @Test
    void normalize_blankOrNull_returnsNull() {
        assertNull(UtilityCustomerCodeHelper.normalize(null));
        assertNull(UtilityCustomerCodeHelper.normalize(""));
        assertNull(UtilityCustomerCodeHelper.normalize("   "));
        assertNull(UtilityCustomerCodeHelper.normalize("---"));
    }
}
