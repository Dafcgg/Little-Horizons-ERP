package com.littlehorizons.erp.util;

import com.littlehorizons.erp.model.Invoice;
import java.io.IOException;
import java.nio.file.Path;

@FunctionalInterface
public interface InvoiceRenderer {
  Path render(Invoice invoice) throws IOException;
}
